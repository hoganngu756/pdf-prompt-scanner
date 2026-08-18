package com.promptscanner.backend.service;

import com.promptscanner.backend.dto.Finding;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import net.sourceforge.tess4j.Tesseract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extracts everything analysable from an uploaded PDF: page text, OCR of embedded
 * images, hidden-surface content, and highlighted page previews.
 *
 * Note what this class does NOT do: it holds no repository and knows nothing
 * about heuristic rules. The words to highlight are passed in by the caller, so
 * extraction can be exercised without a database behind it.
 */
@Service
public class PdfScannerService {

    private static final Logger log = LoggerFactory.getLogger(PdfScannerService.class);

    private static final int MAX_OCR_PAGES = 5;
    private static final int MAX_OCR_IMAGES = 10;

    /**
     * Ceiling on the text handed to the analysis layers.
     *
     * Page count and upload size are both capped, but neither bounds the text a
     * document yields: a 510 KB file of stacked runs produced 7.2 million
     * characters and 15.6 seconds of CPU, since deflate lets a small upload buy a
     * very large content stream. That text then reaches the regex engine, the
     * response body, and the Gemini prompt, so it is bounded once here at the
     * source. A real document that trips this has already given every layer far
     * more than it needs to reach a verdict.
     */
    private static final int MAX_EXTRACTED_CHARS = 1_000_000;

    private final PdfStructureScanner pdfStructureScanner;
    private final PdfPreviewRenderer previewRenderer;

    @Value("${ocr.tessdata.path:}")
    private String tessDataPath;

    @Value("${app.scan.max-pages:50}")
    private int maxPages;

    // Use ThreadLocal to cache Tesseract instances across requests safely
    private ThreadLocal<Tesseract> tesseractThreadLocal;

    public PdfScannerService(PdfStructureScanner pdfStructureScanner, PdfPreviewRenderer previewRenderer) {
        this.pdfStructureScanner = pdfStructureScanner;
        this.previewRenderer = previewRenderer;
    }

    @PostConstruct
    public void init() {
        tesseractThreadLocal = ThreadLocal.withInitial(() -> {
            Tesseract tesseract = new Tesseract();
            if (tessDataPath != null && !tessDataPath.trim().isEmpty()) {
                tesseract.setDatapath(tessDataPath);
            } else {
                // Fallback for local Mac development if not configured
                if (new java.io.File("/opt/homebrew/share/tessdata").exists()) {
                    tesseract.setDatapath("/opt/homebrew/share/tessdata");
                } else if (new java.io.File("/usr/local/share/tessdata").exists()) {
                    tesseract.setDatapath("/usr/local/share/tessdata");
                }
            }
            return tesseract;
        });
    }

    public record PdfData(
        String extractedText,
        List<String> previewImagesBase64,
        List<Finding> visualObfuscationFindings,
        /** Findings from document structure: metadata, annotations, active content. */
        List<Finding> structureFindings,
        /** 1-based page numbers matching previewImagesBase64 by index. Only flagged
         *  pages are rendered, so these are not contiguous and must not be inferred
         *  from list position. */
        List<Integer> previewPageNumbers
    ) {}

    /**
     * @param highlightWords lowercase words to highlight in the rendered previews,
     *                       supplied by the caller from the active rule set
     */
    public PdfData processPdf(MultipartFile file, Set<String> highlightWords) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            if (document.getNumberOfPages() > maxPages) {
                throw new IllegalArgumentException("PDF exceeds maximum allowed page count of " + maxPages
                        + " pages (found " + document.getNumberOfPages() + ").");
            }

            HighlightingTextStripper stripper = new HighlightingTextStripper(highlightWords);
            stripper.setSortByPosition(true);
            String extractedText = truncateExtracted(stripper.getText(document));
            List<Finding> visualFindings = stripper.getVisualObfuscationFindings();
            Map<Integer, List<PDRectangle>> highlightsPerPage = stripper.getHighlightsPerPage();

            // Metadata, annotations and bookmarks never appear in the page content
            // stream, so they are recovered separately and appended below.
            PdfStructureScanner.StructureData structure = pdfStructureScanner.scan(document);
            if (!structure.hiddenText().isBlank()) {
                extractedText += "\n\n--- DOCUMENT METADATA & ANNOTATIONS ---\n" + structure.hiddenText();
            }

            String ocrText = runOcr(document);
            if (!ocrText.isEmpty()) {
                log.debug("Extracted OCR text length: {}", ocrText.length());
                extractedText += "\n\n--- OCR EXTRACTED TEXT ---\n" + ocrText;
            }

            PdfPreviewRenderer.Previews previews = previewRenderer.render(document, highlightsPerPage);

            return new PdfData(truncateExtracted(extractedText), previews.imagesBase64(), visualFindings,
                    structure.findings(), previews.pageNumbers());
        }
    }

    /**
     * Applied to the page text before the metadata and OCR sections are appended,
     * and again to the result, so neither the page content nor the total can run
     * away. The marker is left in place so a truncated scan is visible rather than
     * silently partial.
     */
    private static String truncateExtracted(String text) {
        if (text == null || text.length() <= MAX_EXTRACTED_CHARS) {
            return text;
        }
        log.warn("Extracted text of {} chars exceeds the {} char limit; truncating.",
                text.length(), MAX_EXTRACTED_CHARS);
        return text.substring(0, MAX_EXTRACTED_CHARS) + "\n\n--- TEXT TRUNCATED AT SCAN LIMIT ---";
    }

    /**
     * OCR is best-effort. When the native Tesseract library is missing or fails
     * to initialise, tess4j throws {@link ExceptionInInitializerError} — an Error,
     * not an Exception — which sailed past the per-image catch below and failed
     * the entire scan with a 500. A document containing any image was
     * unscannable on a host without libtesseract. The text, structure and visual
     * layers do not depend on OCR, so a failure here degrades the scan rather
     * than ending it.
     */
    private String runOcr(PDDocument document) {
        try {
            return runOcrInternal(document);
        } catch (Throwable t) {
            log.warn("OCR unavailable, continuing without it: {}", t.toString());
            return "";
        }
    }

    private String runOcrInternal(PDDocument document) {
        StringBuilder ocrText = new StringBuilder();
        Tesseract tesseract = tesseractThreadLocal.get();
        int imageCount = 0;
        int pageLimit = Math.min(document.getNumberOfPages(), MAX_OCR_PAGES);

        for (int i = 0; i < pageLimit && imageCount < MAX_OCR_IMAGES; i++) {
            PDPage page = document.getPage(i);
            org.apache.pdfbox.pdmodel.PDResources resources = page.getResources();
            if (resources == null) {
                continue;
            }
            for (org.apache.pdfbox.cos.COSName name : resources.getXObjectNames()) {
                if (imageCount >= MAX_OCR_IMAGES) {
                    log.info("Reached maximum OCR image limit ({}). Skipping remaining images.", MAX_OCR_IMAGES);
                    break;
                }
                try {
                    if (!resources.isImageXObject(name)) {
                        continue;
                    }
                    imageCount++;
                    var image = (org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) resources.getXObject(name);
                    BufferedImage bim = image.getImage();
                    ocrText.append(tesseract.doOCR(bim)).append("\n");
                } catch (Exception e) {
                    // One unreadable image should not abandon the remaining ones
                    log.warn("OCR error on image {}: {}", name.getName(), e.getMessage());
                }
            }
        }
        return ocrText.toString();
    }

    /** Derives the words worth highlighting in previews from the active literal rules. */
    public static Set<String> highlightWordsFrom(List<String> literalPhrases) {
        Set<String> words = new java.util.HashSet<>();
        for (String phrase : literalPhrases) {
            for (String w : phrase.toLowerCase().split("[\\W_]+")) {
                if (w.length() > 3) {
                    words.add(w);
                }
            }
        }
        if (words.isEmpty()) {
            words.addAll(List.of("ignore", "previous", "instructions", "system", "message", "prompt", "bypass"));
        }
        return words;
    }

    /** Kept for callers that have no rule context. */
    public PdfData processPdf(MultipartFile file) throws IOException {
        return processPdf(file, highlightWordsFrom(new ArrayList<>()));
    }
}
