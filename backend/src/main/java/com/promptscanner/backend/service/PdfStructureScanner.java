package com.promptscanner.backend.service;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import com.promptscanner.backend.dto.Finding;
import com.promptscanner.backend.util.TextNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Looks at the parts of a PDF that a page-content text extractor never sees.
 *
 * Document pipelines routinely surface metadata, annotations and bookmarks to an
 * LLM alongside the page text, so an injection parked in any of them reaches the
 * model while remaining invisible in the rendered document. Separately, active
 * content (JavaScript, auto-run actions, embedded files) is worth reporting on
 * its own merits.
 */
@Service
public class PdfStructureScanner {

    private static final Logger log = LoggerFactory.getLogger(PdfStructureScanner.class);

    /** Cap per field so a padded document can't dominate the LLM prompt. */
    private static final int MAX_FIELD_CHARS = 2000;
    private static final int MAX_ANNOTATIONS = 200;
    private static final int MAX_OUTLINE_ITEMS = 200;
    private static final int MAX_METADATA_ENTRIES = 200;
    private static final int MAX_XMP_BYTES = 512 * 1024;

    /** Timestamps in D:YYYYMMDD form: never prose, so not worth the analysis budget. */
    private static final Set<String> DATE_KEYS = Set.of("CreationDate", "ModDate");

    /** RDF plumbing whose values are identifiers, not content. */
    private static final Set<String> XMP_STRUCTURAL_ATTRIBUTES =
            Set.of("rdf:about", "rdf:parseType", "xml:lang", "x:xmptk", "begin", "id");

    private static final Pattern XMP_ATTRIBUTE =
            Pattern.compile("\\s([\\w:.-]+)\\s*=\\s*(?:\"([^\"<]*)\"|'([^'<]*)')");
    private static final Pattern XMP_TEXT = Pattern.compile(">([^<]+)<");
    private static final Pattern XML_ENTITY = Pattern.compile("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-z]{2,4});");

    /**
     * @param hiddenText  text recovered from non-page surfaces, for the heuristic
     *                    and AI engines to analyse
     * @param findings    active-content and hidden-surface findings to show the user
     * @param limitations surfaces that were only partly read. Non-empty means the
     *                    scan cannot vouch for the whole document.
     */
    public record StructureData(String hiddenText, List<Finding> findings, List<String> limitations) {}

    public StructureData scan(PDDocument document) {
        StringBuilder recovered = new StringBuilder();
        List<Finding> findings = new ArrayList<>();
        List<String> limitations = new ArrayList<>();

        Set<String> seenMetadataValues = new HashSet<>();
        collectMetadata(document, recovered, findings, seenMetadataValues, limitations);
        collectXmpMetadata(document, recovered, findings, seenMetadataValues, limitations);
        collectActiveContent(document, findings);
        collectAnnotations(document, recovered, findings);
        collectOutline(document, recovered, findings, limitations);
        collectFormFieldValues(document, recovered, findings);

        return new StructureData(recovered.toString(), findings, limitations);
    }

    /**
     * Reads every string in the Info dictionary, not just the six standard keys.
     *
     * Any key is legal there, and tools that hand metadata to an LLM typically dump
     * the whole dictionary, so an injection under a custom key such as /Comments
     * reached the model while this scanner never looked at it.
     */
    private void collectMetadata(PDDocument document, StringBuilder recovered, List<Finding> findings,
                                 Set<String> seenValues, List<String> limitations) {
        try {
            PDDocumentInformation info = document.getDocumentInformation();
            if (info == null) {
                return;
            }
            int seen = 0;
            for (Map.Entry<COSName, COSBase> entry : info.getCOSObject().entrySet()) {
                COSBase value = entry.getValue() instanceof COSObject ref ? ref.getObject() : entry.getValue();
                String key = entry.getKey().getName();
                if (!(value instanceof COSString string) || DATE_KEYS.contains(key)) {
                    continue;
                }
                if (++seen > MAX_METADATA_ENTRIES) {
                    limitations.add(String.format(
                            "Document metadata was truncated: only the first %,d entries were analysed.",
                            MAX_METADATA_ENTRIES));
                    break;
                }
                appendField(recovered, findings, seenValues, key, string.getString());
            }
        } catch (Exception e) {
            log.warn("Could not read document metadata: {}", e.getMessage());
        }
    }

    /**
     * Reads the XMP metadata stream, the newer home for the same fields.
     *
     * XMP is XML from the uploaded file, so it is deliberately not handed to an XML
     * parser: that is an XXE and entity-expansion surface for no gain. Element text
     * and attribute values are pulled out with linear patterns instead, which also
     * keeps working on a malformed packet a lenient consumer would still read.
     */
    private void collectXmpMetadata(PDDocument document, StringBuilder recovered, List<Finding> findings,
                                    Set<String> seenValues, List<String> limitations) {
        try {
            PDMetadata metadata = document.getDocumentCatalog().getMetadata();
            if (metadata == null) {
                return;
            }
            byte[] bytes;
            try (InputStream in = metadata.createInputStream()) {
                // Read one byte past the cap so an oversized packet is detectable.
                // The stream is decoded lazily, so this also bounds a decompression bomb.
                bytes = in.readNBytes(MAX_XMP_BYTES + 1);
            }
            if (bytes.length > MAX_XMP_BYTES) {
                limitations.add(String.format(
                        "XMP metadata was truncated: only the first %,d bytes were analysed.", MAX_XMP_BYTES));
                bytes = Arrays.copyOf(bytes, MAX_XMP_BYTES);
            }
            String xml = new String(bytes, StandardCharsets.UTF_8);

            List<String> values = new ArrayList<>();
            Matcher attributes = XMP_ATTRIBUTE.matcher(xml);
            while (attributes.find()) {
                String name = attributes.group(1);
                if (!name.startsWith("xmlns") && !XMP_STRUCTURAL_ATTRIBUTES.contains(name)) {
                    values.add(attributes.group(2) != null ? attributes.group(2) : attributes.group(3));
                }
            }
            Matcher text = XMP_TEXT.matcher(xml);
            while (text.find()) {
                values.add(text.group(1));
            }

            int seen = 0;
            for (String value : values) {
                String decoded = decodeXmlEntities(value);
                if (decoded.isBlank()) {
                    continue;
                }
                if (++seen > MAX_METADATA_ENTRIES) {
                    limitations.add(String.format(
                            "XMP metadata was truncated: only the first %,d values were analysed.",
                            MAX_METADATA_ENTRIES));
                    break;
                }
                appendField(recovered, findings, seenValues, "XMP metadata", decoded);
            }
        } catch (Exception e) {
            log.warn("Could not read XMP metadata: {}", e.getMessage());
        }
    }

    /**
     * Decodes the five predefined entities and numeric character references.
     * Leaving "&#105;gnore" encoded would let a payload slip past every rule.
     */
    static String decodeXmlEntities(String value) {
        if (value.indexOf('&') < 0) {
            return value;
        }
        Matcher m = XML_ENTITY.matcher(value);
        StringBuilder out = new StringBuilder(value.length());
        while (m.find()) {
            String entity = m.group(1);
            String replacement = switch (entity) {
                case "lt" -> "<";
                case "gt" -> ">";
                case "amp" -> "&";
                case "quot" -> "\"";
                case "apos" -> "'";
                default -> {
                    try {
                        int codePoint = entity.startsWith("#x") || entity.startsWith("#X")
                                ? Integer.parseInt(entity.substring(2), 16)
                                : Integer.parseInt(entity.substring(1));
                        yield Character.isValidCodePoint(codePoint)
                                ? new String(Character.toChars(codePoint))
                                : m.group();
                    } catch (NumberFormatException e) {
                        yield m.group();
                    }
                }
            };
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    private void appendField(StringBuilder recovered, List<Finding> findings, Set<String> seenValues,
                             String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String trimmed = truncate(value.trim());
        // XMP normally mirrors the Info dictionary; reporting both would double
        // every finding and the text sent to the analysis layers.
        if (!seenValues.add(trimmed)) {
            return;
        }
        recovered.append(label).append(": ").append(trimmed).append("\n");
        // Only surface metadata that reads like an instruction rather than a filename
        if (looksInstructional(trimmed)) {
            findings.add(Finding.quoting(
                    "Document metadata field contains instruction-like text", label, trimmed));
        }
    }

    private void collectActiveContent(PDDocument document, List<Finding> findings) {
        try {
            COSDictionary root = document.getDocumentCatalog().getCOSObject();

            if (root.getDictionaryObject(COSName.getPDFName("OpenAction")) != null) {
                findings.add(Finding.of("Document defines an /OpenAction that runs automatically when opened."));
            }

            COSBase names = root.getDictionaryObject(COSName.NAMES);
            if (names instanceof COSDictionary namesDict) {
                if (namesDict.getDictionaryObject(COSName.JAVA_SCRIPT) != null) {
                    findings.add(Finding.of("Document contains embedded JavaScript (/JavaScript name tree)."));
                }
                if (namesDict.getDictionaryObject(COSName.EMBEDDED_FILES) != null) {
                    findings.add(Finding.of("Document contains embedded file attachments (/EmbeddedFiles)."));
                }
            }

            // Note: an AcroForm is deliberately NOT reported. Fillable forms are
            // entirely ordinary; it is the field *values* that are extractable
            // text, and those are recovered in collectFormFieldValues instead.
        } catch (Exception e) {
            log.warn("Could not inspect document catalog: {}", e.getMessage());
        }
    }

    private void collectAnnotations(PDDocument document, StringBuilder recovered, List<Finding> findings) {
        int seen = 0;
        try {
            for (PDPage page : document.getPages()) {
                List<PDAnnotation> annotations;
                try {
                    annotations = page.getAnnotations();
                } catch (Exception e) {
                    continue;
                }
                for (PDAnnotation annotation : annotations) {
                    if (++seen > MAX_ANNOTATIONS) {
                        log.info("Stopping annotation scan at {} annotations.", MAX_ANNOTATIONS);
                        return;
                    }
                    String contents = annotation.getContents();
                    if (contents != null && !contents.isBlank()) {
                        String trimmed = truncate(contents.trim());
                        // Always recovered so the heuristic and AI engines see it,
                        // but only reported when it reads as a directive -- ordinary
                        // review comments are common and shouldn't flag a document.
                        recovered.append("Annotation: ").append(trimmed).append("\n");
                        if (looksInstructional(trimmed)) {
                            findings.add(Finding.quoting(
                                    "Annotation carries instruction-like text not shown in the page body",
                                    annotation.getSubtype(), trimmed));
                        }
                    }
                    describeAction(annotation, findings);
                }
            }
        } catch (Exception e) {
            log.warn("Could not read annotations: {}", e.getMessage());
        }
    }

    private void describeAction(PDAnnotation annotation, List<Finding> findings) {
        try {
            COSBase actionBase = annotation.getCOSObject().getDictionaryObject(COSName.A);
            if (!(actionBase instanceof COSDictionary actionDict)) {
                return;
            }
            String subtype = actionDict.getNameAsString(COSName.S);
            if (subtype == null) {
                return;
            }
            switch (subtype) {
                case "JavaScript" -> findings.add(
                        Finding.of("Annotation triggers embedded JavaScript when activated."));
                case "Launch" -> findings.add(
                        Finding.of("Annotation defines a /Launch action that opens an external program."));
                case "URI" -> {
                    String uri = actionDict.getString(COSName.URI);
                    if (uri != null && !uri.isBlank()) {
                        findings.add(Finding.quoting(
                                "Annotation links to an external URI", truncate(uri.trim())));
                    }
                }
                default -> { /* ordinary navigation actions are unremarkable */ }
            }
        } catch (Exception e) {
            log.debug("Could not read annotation action: {}", e.getMessage());
        }
    }

    /**
     * Form field values are extractable text that never appears in the page
     * content stream, making a filled field another place to park an injection.
     */
    private void collectFormFieldValues(PDDocument document, StringBuilder recovered, List<Finding> findings) {
        try {
            PDAcroForm form = document.getDocumentCatalog().getAcroForm(null);
            if (form == null) {
                return;
            }
            int seen = 0;
            for (PDField field : form.getFieldTree()) {
                if (++seen > MAX_ANNOTATIONS) {
                    break;
                }
                String value;
                try {
                    value = field.getValueAsString();
                } catch (Exception e) {
                    continue;
                }
                if (value == null || value.isBlank()) {
                    continue;
                }
                String trimmed = truncate(value.trim());
                recovered.append("Form field '").append(field.getPartialName()).append("': ").append(trimmed).append("\n");
                if (looksInstructional(trimmed)) {
                    findings.add(Finding.quoting("Form field contains instruction-like text",
                            field.getPartialName(), trimmed));
                }
            }
        } catch (Exception e) {
            log.warn("Could not read form fields: {}", e.getMessage());
        }
    }

    /**
     * Walks the whole bookmark tree, not just the top level. A child bookmark is
     * as visible to an LLM as a top-level one, and reading only outline.children()
     * let an injection hide one level down under an innocuous chapter title.
     *
     * The tree comes from the uploaded file, so its /First and /Next links can be
     * made to loop. The traversal is iterative (no recursion depth to exhaust) and
     * remembers every node it has visited, so a cycle ends the walk instead of
     * spinning until the item cap.
     *
     * Reaching the cap is recorded as a limitation, not just logged: otherwise a
     * file could park its payload behind enough innocuous bookmarks and still be
     * reported clean.
     */
    private void collectOutline(PDDocument document, StringBuilder recovered, List<Finding> findings,
                                List<String> limitations) {
        try {
            PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
            if (outline == null) {
                return;
            }
            Set<COSDictionary> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            Deque<PDOutlineItem> pending = new ArrayDeque<>();
            if (outline.getFirstChild() != null) {
                pending.push(outline.getFirstChild());
            }
            int seen = 0;
            while (!pending.isEmpty()) {
                PDOutlineItem item = pending.pop();
                if (!visited.add(item.getCOSObject())) {
                    continue;
                }
                if (++seen > MAX_OUTLINE_ITEMS) {
                    log.info("Stopping outline scan at {} bookmarks.", MAX_OUTLINE_ITEMS);
                    limitations.add(String.format("Bookmarks were truncated: only the first %,d were analysed.",
                            MAX_OUTLINE_ITEMS));
                    break;
                }
                // Sibling first so the child is popped next: document order.
                if (item.getNextSibling() != null) {
                    pending.push(item.getNextSibling());
                }
                if (item.getFirstChild() != null) {
                    pending.push(item.getFirstChild());
                }

                String title = item.getTitle();
                if (title != null && !title.isBlank()) {
                    String trimmed = truncate(title.trim());
                    recovered.append("Bookmark: ").append(trimmed).append("\n");
                    if (looksInstructional(trimmed)) {
                        findings.add(Finding.quoting("Bookmark contains instruction-like text", trimmed));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not read document outline: {}", e.getMessage());
        }
    }

    /**
     * Phrase-level patterns that read as a directive aimed at a model.
     *
     * Bare keywords are unusable here: "Installation Instructions" and "AI Systems
     * Inc." are ordinary document titles, and matching on "instruction", "ai" or
     * "output" flags them. Each pattern below therefore requires the surrounding
     * grammar of an instruction, not just a topic word.
     */
    private static final List<Pattern> INSTRUCTION_PATTERNS = List.of(
            Pattern.compile("\\b(ignore|disregard|forget)\\b.{0,20}\\b(previous|prior|above|earlier|all|any|everything)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\byou are (now|to act|an? )\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bact as an? \\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bsystem (prompt|message)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bnew instructions\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(must|should|always|only)\\s+(output|respond|reply|say|state|rate|classify|answer|return)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\boutput only\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bdo not (mention|report|reveal|tell|disclose|include)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(jailbreak|developer mode)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bregardless of\\b.{0,30}\\b(content|instruction|rest)\\b", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Metadata and annotations legitimately hold titles, tool names and review
     * comments, so only values that read as an address to a model are reported.
     * The recovered text is handed to the heuristic and AI engines either way --
     * this only controls whether the structure check raises a finding itself.
     */
    private boolean looksInstructional(String value) {
        // Normalised so a directive spelled with lookalike characters is still seen
        String candidate = TextNormalizer.normalize(value);
        for (Pattern p : INSTRUCTION_PATTERNS) {
            if (p.matcher(candidate).find()) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String value) {
        return value.length() <= MAX_FIELD_CHARS ? value : value.substring(0, MAX_FIELD_CHARS) + "…";
    }
}
