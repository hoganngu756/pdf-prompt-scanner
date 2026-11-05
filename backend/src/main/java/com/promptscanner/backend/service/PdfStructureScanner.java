package com.promptscanner.backend.service;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.common.PDNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationFileAttachment;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import com.promptscanner.backend.dto.Finding;
import com.promptscanner.backend.util.TextNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
import java.util.Objects;
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
    /** Annotations (and form fields) with text: the analysis budget. */
    private static final int MAX_ANNOTATIONS = 200;
    /** Annotations (and form fields) walked at all, text or not: the work budget. */
    private static final int MAX_ANNOTATIONS_WALKED = 5_000;
    private static final int MAX_ATTACHMENTS = 50;
    private static final int MAX_ATTACHMENT_BYTES = 256 * 1024;
    private static final int MAX_NAME_TREE_NODES = 1_000;
    /** Characters either side of a match quoted from a long attachment. */
    private static final int EXCERPT_CONTEXT_CHARS = 150;

    /** Annotation keys that carry text a parser can extract, in reporting order. */
    private static final List<COSName> ANNOTATION_TEXT_KEYS =
            List.of(COSName.CONTENTS, COSName.T, COSName.getPDFName("Subj"), COSName.RC);
    private static final Pattern MARKUP_TAG = Pattern.compile("<[^>]*>");

    /** Action types that run code or move data in or out, with what each does. */
    private static final Map<String, String> CODE_ACTIONS = Map.of(
            "JavaScript", "runs embedded JavaScript",
            "Launch", "launches an external program",
            "SubmitForm", "submits form data to an external address",
            "ImportData", "imports data from an external file");

    /** URI schemes that execute or embed content rather than navigate to a page. */
    private static final Set<String> CODE_URI_SCHEMES = Set.of("javascript", "vbscript", "data", "file");

    /** Ceiling on actions followed through /Next chains from one trigger. */
    private static final int MAX_ACTION_CHAIN = 100;
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
        List<PDComplexFileSpecification> annotationAttachments = new ArrayList<>();
        collectAnnotations(document, recovered, findings, annotationAttachments, limitations);
        collectOutline(document, recovered, findings, limitations);
        collectFormFieldValues(document, recovered, findings, limitations);
        collectAttachments(document, recovered, findings, annotationAttachments, limitations);

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

            // Most /OpenAction entries just open the document at a page and zoom
            // (a destination array, or a GoTo action) and flagging every one made
            // ordinary PDFs read as "Injection detected". Only actions that run
            // code or reach outside the document are reported -- anywhere in the
            // /Next chain, since a GoTo can chain to JavaScript. The document's
            // /AA triggers (before print, on close, ...) run unprompted too.
            List<COSBase> automatic = new ArrayList<>();
            automatic.add(root.getDictionaryObject(COSName.getPDFName("OpenAction")));
            if (root.getDictionaryObject(COSName.AA) instanceof COSDictionary additional) {
                additional.getValues().forEach(automatic::add);
            }
            Set<String> reported = new HashSet<>();
            for (COSBase trigger : automatic) {
                for (COSDictionary action : actionChain(trigger)) {
                    String type = action.getNameAsString(COSName.S);
                    if (type == null) {
                        continue;
                    }
                    String effect = CODE_ACTIONS.get(type);
                    if (effect == null && "URI".equals(type)) {
                        effect = "opens a web address";
                    }
                    if (effect != null && reported.add(type)) {
                        findings.add(Finding.of("Document " + effect + " automatically, without the reader acting."));
                    }
                }
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

    /**
     * Recovers every text-bearing annotation key, not just /Contents. The author
     * label (/T), subject (/Subj) and rich-text body (/RC) are all extractable,
     * and /RC in particular is what many viewers and parsers show in preference
     * to /Contents.
     *
     * Two ceilings, both reported as limitations when reached: one on annotations
     * that carry text, and a much larger one on annotations walked at all. Only
     * the first is the analysis budget -- a long table of contents has hundreds
     * of bare link annotations, and counting those against it would make
     * ordinary documents inconclusive.
     */
    private void collectAnnotations(PDDocument document, StringBuilder recovered, List<Finding> findings,
                                    List<PDComplexFileSpecification> attachments, List<String> limitations) {
        int walked = 0;
        int withText = 0;
        try {
            for (PDPage page : document.getPages()) {
                List<PDAnnotation> annotations;
                try {
                    annotations = page.getAnnotations();
                } catch (Exception e) {
                    continue;
                }
                for (PDAnnotation annotation : annotations) {
                    if (++walked > MAX_ANNOTATIONS_WALKED) {
                        log.info("Stopping annotation scan at {} annotations.", MAX_ANNOTATIONS_WALKED);
                        limitations.add(String.format(
                                "Annotations were truncated: only the first %,d were examined.",
                                MAX_ANNOTATIONS_WALKED));
                        return;
                    }
                    describeAction(annotation, page, recovered, findings);
                    if (annotation instanceof PDAnnotationFileAttachment fileAttachment) {
                        findings.add(Finding.of("Annotation embeds a file attachment (/FileAttachment)."));
                        if (fileAttachment.getFile() instanceof PDComplexFileSpecification spec) {
                            attachments.add(spec);
                        }
                    }

                    List<String[]> texts = annotationTexts(annotation);
                    if (texts.isEmpty()) {
                        continue;
                    }
                    if (++withText > MAX_ANNOTATIONS) {
                        log.info("Stopping annotation text recovery at {} annotations.", MAX_ANNOTATIONS);
                        limitations.add(String.format(
                                "Annotation text was truncated: only the first %,d annotations with text were analysed.",
                                MAX_ANNOTATIONS));
                        return;
                    }
                    for (String[] text : texts) {
                        String label = text[0];
                        String trimmed = text[1];
                        // Always recovered so the heuristic and AI engines see it,
                        // but only reported when it reads as a directive -- ordinary
                        // review comments are common and shouldn't flag a document.
                        recovered.append("Annotation ").append(label).append(": ").append(trimmed).append("\n");
                        if (looksInstructional(trimmed)) {
                            findings.add(Finding.quoting(
                                    "Annotation carries instruction-like text not shown in the page body",
                                    annotation.getSubtype() + " " + label, trimmed));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not read annotations: {}", e.getMessage());
        }
    }

    /** Label/value pairs for each distinct text an annotation carries. */
    private List<String[]> annotationTexts(PDAnnotation annotation) {
        COSDictionary dict = annotation.getCOSObject();
        List<String[]> texts = new ArrayList<>();
        Set<String> distinct = new HashSet<>();
        boolean widget = PDAnnotationWidget.SUB_TYPE.equals(annotation.getSubtype());
        for (COSName key : ANNOTATION_TEXT_KEYS) {
            // On a form widget /T is the field's name, not an author label; field
            // names are reported with their values in collectFormFieldValues, and
            // counting them here would spend the budget on every blank form.
            if (widget && key.equals(COSName.T)) {
                continue;
            }
            String value;
            try {
                value = textOf(dict.getDictionaryObject(key));
            } catch (Exception e) {
                continue;
            }
            if (value == null) {
                continue;
            }
            if (key.equals(COSName.RC)) {
                // Rich text is XHTML; the markup is not content. Same linear,
                // parser-free handling as XMP, for the same reasons.
                value = decodeXmlEntities(MARKUP_TAG.matcher(value).replaceAll(" "))
                        .replaceAll("\\s+", " ");
            }
            if (value.isBlank()) {
                continue;
            }
            String trimmed = truncate(value.trim());
            // /RC usually restates /Contents with markup; recovering both would
            // double every finding.
            if (distinct.add(trimmed)) {
                texts.add(new String[] {"/" + key.getName(), trimmed});
            }
        }
        return texts;
    }

    /** A string or a (bounded) stream value as text, or null for anything else. */
    private static String textOf(COSBase value) throws IOException {
        if (value instanceof COSString string) {
            return string.getString();
        }
        if (value instanceof COSStream stream) {
            try (InputStream in = stream.createInputStream()) {
                return new String(in.readNBytes(MAX_FIELD_CHARS * 4), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /**
     * Reports what an annotation does when activated or hovered: its /A action and
     * every /AA trigger, each followed down its /Next chain.
     *
     * Ordinary links are recovered for analysis but no longer reported on their
     * own -- nearly every real document has them, and flagging each one made a
     * PDF with a hyperlink read as "Injection detected". A link is reported only
     * when it runs code (a javascript: or similar URI) or is hidden from the
     * reader, which is when it stops being an ordinary link.
     */
    private void describeAction(PDAnnotation annotation, PDPage page, StringBuilder recovered,
                                List<Finding> findings) {
        try {
            COSDictionary dict = annotation.getCOSObject();
            List<COSBase> triggers = new ArrayList<>();
            triggers.add(dict.getDictionaryObject(COSName.A));
            if (dict.getDictionaryObject(COSName.AA) instanceof COSDictionary additional) {
                additional.getValues().forEach(triggers::add);
            }
            for (COSBase trigger : triggers) {
                for (COSDictionary action : actionChain(trigger)) {
                    String type = action.getNameAsString(COSName.S);
                    if (type == null) {
                        continue;
                    }
                    if (CODE_ACTIONS.containsKey(type)) {
                        findings.add(Finding.of("Annotation " + CODE_ACTIONS.get(type) + " when activated."));
                    } else if ("URI".equals(type)) {
                        describeLink(action.getString(COSName.URI), annotation, page, recovered, findings);
                    }
                    // GoTo, Named and the other navigation actions are unremarkable
                }
            }
        } catch (Exception e) {
            log.debug("Could not read annotation action: {}", e.getMessage());
        }
    }

    private void describeLink(String uri, PDAnnotation annotation, PDPage page, StringBuilder recovered,
                              List<Finding> findings) {
        if (uri == null || uri.isBlank()) {
            return;
        }
        String trimmed = truncate(uri.trim());
        // Pipelines that convert a PDF for a model often keep link targets, so the
        // URI goes to the analysis layers either way.
        recovered.append("Link: ").append(trimmed).append("\n");

        int colon = trimmed.indexOf(':');
        String scheme = colon > 0 ? trimmed.substring(0, colon).strip().toLowerCase(java.util.Locale.ROOT) : "";
        if (CODE_URI_SCHEMES.contains(scheme)) {
            findings.add(Finding.quoting("Link runs code instead of opening a web page (" + scheme + ": URI)",
                    trimmed));
        } else if (isHiddenFromReader(annotation, page)) {
            findings.add(Finding.quoting(
                    "Hidden link: present in the file but with no visible, clickable area", trimmed));
        }
    }

    /** True when a reader could never see or click the annotation. */
    private static boolean isHiddenFromReader(PDAnnotation annotation, PDPage page) {
        if (annotation.isHidden() || annotation.isNoView()) {
            return true;
        }
        PDRectangle rect = annotation.getRectangle();
        if (rect == null || Math.abs(rect.getWidth()) < 1 || Math.abs(rect.getHeight()) < 1) {
            return true;
        }
        PDRectangle visible = page.getCropBox();
        float minX = Math.min(rect.getLowerLeftX(), rect.getUpperRightX());
        float maxX = Math.max(rect.getLowerLeftX(), rect.getUpperRightX());
        float minY = Math.min(rect.getLowerLeftY(), rect.getUpperRightY());
        float maxY = Math.max(rect.getLowerLeftY(), rect.getUpperRightY());
        return maxX <= visible.getLowerLeftX() || minX >= visible.getUpperRightX()
                || maxY <= visible.getLowerLeftY() || minY >= visible.getUpperRightY();
    }

    /**
     * The action dictionaries reachable from {@code start} through /Next, which
     * may be a single action or an array of them. An innocuous first action can
     * chain to JavaScript, so looking only at the first one is not enough. The
     * chain comes from the file, so it is walked with a cycle guard and a cap.
     */
    private static List<COSDictionary> actionChain(COSBase start) {
        List<COSDictionary> actions = new ArrayList<>();
        Set<COSDictionary> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<COSBase> pending = new ArrayDeque<>();
        if (start != null) {
            pending.push(start);
        }
        while (!pending.isEmpty() && actions.size() < MAX_ACTION_CHAIN) {
            COSBase next = pending.pop();
            if (next instanceof COSObject ref) {
                next = ref.getObject();
            }
            if (next instanceof COSArray array) {
                for (int i = array.size() - 1; i >= 0; i--) {
                    pending.push(array.getObject(i));
                }
            } else if (next instanceof COSDictionary action && visited.add(action)) {
                actions.add(action);
                COSBase chained = action.getDictionaryObject(COSName.NEXT);
                if (chained != null) {
                    pending.push(chained);
                }
            }
        }
        return actions;
    }

    /**
     * Reads the contents of attached files, from the /EmbeddedFiles name tree and
     * from /FileAttachment annotations.
     *
     * Their presence was already reported, but their text was never analysed, so
     * the report said "has an attachment" for a .txt that read "ignore previous
     * instructions". Plain-text attachments are now recovered for the analysis
     * layers; anything that cannot be read as text (a nested PDF, an office file,
     * an image) is reported as a limitation, since this scan cannot vouch for it.
     *
     * Called last, so that if the recovered text overruns its budget it is
     * attachment text that gets cut, not metadata or bookmarks.
     */
    private void collectAttachments(PDDocument document, StringBuilder recovered, List<Finding> findings,
                                    List<PDComplexFileSpecification> fromAnnotations, List<String> limitations) {
        Map<COSDictionary, PDComplexFileSpecification> specs = new IdentityHashMap<>();
        try {
            PDDocumentNameDictionary names = document.getDocumentCatalog().getNames();
            if (names != null && names.getEmbeddedFiles() != null) {
                collectNameTree(names.getEmbeddedFiles(), specs, limitations);
            }
        } catch (Exception e) {
            log.warn("Could not read embedded file name tree: {}", e.getMessage());
            limitations.add("Embedded file attachments could not be fully enumerated.");
        }
        for (PDComplexFileSpecification spec : fromAnnotations) {
            specs.putIfAbsent(spec.getCOSObject(), spec);
        }

        int seen = 0;
        for (PDComplexFileSpecification spec : specs.values()) {
            if (++seen > MAX_ATTACHMENTS) {
                limitations.add(String.format(
                        "Attachments were truncated: only the first %,d were examined.", MAX_ATTACHMENTS));
                return;
            }
            String name = Objects.requireNonNullElse(
                    spec.getFileUnicode() != null ? spec.getFileUnicode() : spec.getFilename(), "(unnamed)");
            try {
                readAttachment(name, spec, recovered, findings, limitations);
            } catch (Exception e) {
                log.warn("Could not read attachment: {}", e.getMessage());
                limitations.add(String.format("Attachment \"%s\" could not be read and was not examined.",
                        truncateName(name)));
            }
        }
    }

    private void collectNameTree(PDNameTreeNode<PDComplexFileSpecification> root,
                                 Map<COSDictionary, PDComplexFileSpecification> specs,
                                 List<String> limitations) throws IOException {
        // The tree comes from the file: guard against cycles and runaway size.
        Set<COSDictionary> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<PDNameTreeNode<PDComplexFileSpecification>> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            PDNameTreeNode<PDComplexFileSpecification> node = pending.pop();
            if (!visited.add(node.getCOSObject())) {
                continue;
            }
            if (visited.size() > MAX_NAME_TREE_NODES) {
                limitations.add("Embedded file attachments could not be fully enumerated.");
                return;
            }
            Map<String, PDComplexFileSpecification> leaves = node.getNames();
            if (leaves != null) {
                for (PDComplexFileSpecification spec : leaves.values()) {
                    if (spec != null) {
                        specs.putIfAbsent(spec.getCOSObject(), spec);
                    }
                }
            }
            List<PDNameTreeNode<PDComplexFileSpecification>> kids = node.getKids();
            if (kids != null) {
                kids.forEach(pending::push);
            }
        }
    }

    private void readAttachment(String name, PDComplexFileSpecification spec, StringBuilder recovered,
                                List<Finding> findings, List<String> limitations) throws IOException {
        PDEmbeddedFile file = spec.getEmbeddedFileUnicode();
        if (file == null) file = spec.getEmbeddedFile();
        if (file == null) file = spec.getEmbeddedFileDos();
        if (file == null) file = spec.getEmbeddedFileMac();
        if (file == null) file = spec.getEmbeddedFileUnix();
        if (file == null) {
            // A file specification with no embedded stream points outside the PDF
            return;
        }

        byte[] bytes;
        try (InputStream in = file.createInputStream()) {
            // One byte past the cap so an oversized file is detectable. The stream
            // is decoded lazily, which also bounds a decompression bomb.
            bytes = in.readNBytes(MAX_ATTACHMENT_BYTES + 1);
        }
        String shownName = truncateName(name);
        String text = attachmentText(bytes);
        if (text == null) {
            limitations.add(String.format(
                    "Attachment \"%s\" is not plain text, so its contents were not examined.", shownName));
            return;
        }
        if (bytes.length > MAX_ATTACHMENT_BYTES) {
            limitations.add(String.format(
                    "Attachment \"%s\" was truncated: only the first %,d bytes were analysed.",
                    shownName, MAX_ATTACHMENT_BYTES));
        }
        if (text.isBlank()) {
            return;
        }
        recovered.append("Attachment \"").append(shownName).append("\":\n").append(text).append("\n");
        String excerpt = instructionExcerpt(text);
        if (excerpt != null) {
            findings.add(Finding.quoting("Attached file contains instruction-like text", shownName, excerpt));
        }
    }

    /**
     * The attachment decoded as text, or null when it is not text. Judged on the
     * bytes rather than the declared type or extension, both of which the author
     * chooses.
     */
    static String attachmentText(byte[] bytes) {
        int length = Math.min(bytes.length, MAX_ATTACHMENT_BYTES);
        if (length == 0) {
            return "";
        }
        if (length >= 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F') {
            return null;
        }
        String text;
        if (length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            text = new String(bytes, 2, length - 2, StandardCharsets.UTF_16BE);
        } else if (length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            text = new String(bytes, 2, length - 2, StandardCharsets.UTF_16LE);
        } else {
            text = new String(bytes, 0, length, StandardCharsets.UTF_8);
        }
        int unreadable = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '�' || (Character.isISOControl(c) && !Character.isWhitespace(c))) {
                unreadable++;
            }
        }
        return unreadable <= text.length() * 0.02 ? text : null;
    }

    /**
     * Form field values are extractable text that never appears in the page
     * content stream, making a filled field another place to park an injection.
     */
    private void collectFormFieldValues(PDDocument document, StringBuilder recovered, List<Finding> findings,
                                        List<String> limitations) {
        try {
            PDAcroForm form = document.getDocumentCatalog().getAcroForm(null);
            if (form == null) {
                return;
            }
            // As with annotations, only filled fields count against the analysis
            // budget: a blank tax form can have hundreds of empty fields.
            int walked = 0;
            int filled = 0;
            for (PDField field : form.getFieldTree()) {
                if (++walked > MAX_ANNOTATIONS_WALKED) {
                    limitations.add(String.format(
                            "Form fields were truncated: only the first %,d were examined.", MAX_ANNOTATIONS_WALKED));
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
                if (++filled > MAX_ANNOTATIONS) {
                    limitations.add(String.format(
                            "Form field values were truncated: only the first %,d filled fields were analysed.",
                            MAX_ANNOTATIONS));
                    break;
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
    /**
     * The neighbourhood of the first instruction-like match, for quoting from text
     * too long to show whole, or null when nothing matches.
     */
    private String instructionExcerpt(String value) {
        String candidate = TextNormalizer.normalize(value);
        for (Pattern p : INSTRUCTION_PATTERNS) {
            Matcher m = p.matcher(candidate);
            if (m.find()) {
                int start = Math.max(0, m.start() - EXCERPT_CONTEXT_CHARS);
                int end = Math.min(candidate.length(), m.end() + EXCERPT_CONTEXT_CHARS);
                return (start > 0 ? "…" : "") + candidate.substring(start, end).trim()
                        + (end < candidate.length() ? "…" : "");
            }
        }
        return null;
    }

    private static String truncateName(String name) {
        return name.length() <= 120 ? name : name.substring(0, 120) + "…";
    }

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
