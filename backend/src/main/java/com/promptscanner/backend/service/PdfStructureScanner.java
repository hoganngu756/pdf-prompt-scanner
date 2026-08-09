package com.promptscanner.backend.service;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import com.promptscanner.backend.util.TextNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * @param hiddenText  text recovered from non-page surfaces, for the heuristic
     *                    and AI engines to analyse
     * @param findings    active-content and hidden-surface findings to show the user
     */
    public record StructureData(String hiddenText, List<String> findings) {}

    public StructureData scan(PDDocument document) {
        StringBuilder recovered = new StringBuilder();
        List<String> findings = new ArrayList<>();

        collectMetadata(document, recovered, findings);
        collectActiveContent(document, findings);
        collectAnnotations(document, recovered, findings);
        collectOutline(document, recovered, findings);
        collectFormFieldValues(document, recovered, findings);

        return new StructureData(recovered.toString(), findings);
    }

    private void collectMetadata(PDDocument document, StringBuilder recovered, List<String> findings) {
        try {
            PDDocumentInformation info = document.getDocumentInformation();
            if (info == null) {
                return;
            }
            appendField(recovered, findings, "Title", info.getTitle());
            appendField(recovered, findings, "Author", info.getAuthor());
            appendField(recovered, findings, "Subject", info.getSubject());
            appendField(recovered, findings, "Keywords", info.getKeywords());
            appendField(recovered, findings, "Creator", info.getCreator());
            appendField(recovered, findings, "Producer", info.getProducer());
        } catch (Exception e) {
            log.warn("Could not read document metadata: {}", e.getMessage());
        }
    }

    private void appendField(StringBuilder recovered, List<String> findings, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String trimmed = truncate(value.trim());
        recovered.append(label).append(": ").append(trimmed).append("\n");
        // Only surface metadata that reads like an instruction rather than a filename
        if (looksInstructional(trimmed)) {
            findings.add(String.format("Document metadata field '%s' contains instruction-like text: '%s'",
                    label, trimmed));
        }
    }

    private void collectActiveContent(PDDocument document, List<String> findings) {
        try {
            COSDictionary root = document.getDocumentCatalog().getCOSObject();

            if (root.getDictionaryObject(COSName.getPDFName("OpenAction")) != null) {
                findings.add("Document defines an /OpenAction that runs automatically when opened.");
            }

            COSBase names = root.getDictionaryObject(COSName.NAMES);
            if (names instanceof COSDictionary namesDict) {
                if (namesDict.getDictionaryObject(COSName.JAVA_SCRIPT) != null) {
                    findings.add("Document contains embedded JavaScript (/JavaScript name tree).");
                }
                if (namesDict.getDictionaryObject(COSName.EMBEDDED_FILES) != null) {
                    findings.add("Document contains embedded file attachments (/EmbeddedFiles).");
                }
            }

            // Note: an AcroForm is deliberately NOT reported. Fillable forms are
            // entirely ordinary; it is the field *values* that are extractable
            // text, and those are recovered in collectFormFieldValues instead.
        } catch (Exception e) {
            log.warn("Could not inspect document catalog: {}", e.getMessage());
        }
    }

    private void collectAnnotations(PDDocument document, StringBuilder recovered, List<String> findings) {
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
                            findings.add(String.format("Annotation (%s) carries instruction-like text not shown in the page body: '%s'",
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

    private void describeAction(PDAnnotation annotation, List<String> findings) {
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
                case "JavaScript" -> findings.add("Annotation triggers embedded JavaScript when activated.");
                case "Launch" -> findings.add("Annotation defines a /Launch action that opens an external program.");
                case "URI" -> {
                    String uri = actionDict.getString(COSName.URI);
                    if (uri != null && !uri.isBlank()) {
                        findings.add("Annotation links to external URI: " + truncate(uri.trim()));
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
    private void collectFormFieldValues(PDDocument document, StringBuilder recovered, List<String> findings) {
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
                    findings.add(String.format("Form field '%s' contains instruction-like text: '%s'",
                            field.getPartialName(), trimmed));
                }
            }
        } catch (Exception e) {
            log.warn("Could not read form fields: {}", e.getMessage());
        }
    }

    private void collectOutline(PDDocument document, StringBuilder recovered, List<String> findings) {
        try {
            PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
            if (outline == null) {
                return;
            }
            int seen = 0;
            for (PDOutlineItem item : outline.children()) {
                if (++seen > MAX_OUTLINE_ITEMS) {
                    break;
                }
                String title = item.getTitle();
                if (title != null && !title.isBlank()) {
                    String trimmed = truncate(title.trim());
                    recovered.append("Bookmark: ").append(trimmed).append("\n");
                    if (looksInstructional(trimmed)) {
                        findings.add("Bookmark contains instruction-like text: '" + trimmed + "'");
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
