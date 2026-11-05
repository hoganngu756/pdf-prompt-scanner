package com.promptscanner.backend.service;

import com.promptscanner.backend.dto.Finding;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
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
 * This class knows nothing about heuristic rules: the words to highlight are
 * passed in by the caller, so extraction can be tested in isolation.
 */
@Service
public class PdfScannerService {

    private static final Logger log = LoggerFactory.getLogger(PdfScannerService.class);

    private static final int MAX_OCR_PAGES = 5;
    private static final int MAX_OCR_IMAGES = 10;

    /**
     * Ceiling on the page text handed to the analysis layers.
     *
     * Page count and upload size are both capped, but neither bounds the text a
     * document yields: a 510 KB file of stacked runs produced 7.2 million
     * characters and 15.6 seconds of CPU, since deflate lets a small upload buy a
     * very large content stream. That text then reaches the regex engine and the
     * Gemini prompt, so it is bounded here at the source.
     */
    static final int MAX_PAGE_TEXT_CHARS = 1_000_000;

    /**
     * Separate ceilings for the metadata/annotation and OCR sections. Each section
     * is bounded on its own so that padding the page text can never push the
     * hidden surfaces out of what the analysis layers see.
     */
    static final int MAX_SURFACE_TEXT_CHARS = 200_000;

    private final PdfStructureScanner pdfStructureScanner;
    private final PdfPreviewRenderer previewRenderer;

    @Value("${ocr.tessdata.path:}")
    private String tessDataPath;

    @Value("${app.scan.max-pages:50}")
    private int maxPages;

    // Use ThreadLocal to cache Tesseract instances across requests safely
    private ThreadLocal<Tesseract> tesseractThreadLocal;

    private OcrEngine ocrEngine;

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
        ocrEngine = image -> tesseractThreadLocal.get().doOCR(image);
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
        List<Integer> previewPageNumbers,
        /** Content that was extracted but not analysed in full. Non-empty means the
         *  scan cannot vouch for the whole document. */
        List<String> limitations
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

            List<String> limitations = new ArrayList<>();

            HighlightingTextStripper stripper = new HighlightingTextStripper(highlightWords);
            stripper.setSortByPosition(true);
            String extractedText = bounded(stripper.getText(document), MAX_PAGE_TEXT_CHARS,
                    "Page text", limitations);
            List<Finding> visualFindings = stripper.getVisualObfuscationFindings();
            Map<Integer, List<PDRectangle>> highlightsPerPage = stripper.getHighlightsPerPage();

            // Metadata, annotations and bookmarks never appear in the page content
            // stream, so they are recovered separately and appended below.
            PdfStructureScanner.StructureData structure = pdfStructureScanner.scan(document);
            if (!structure.hiddenText().isBlank()) {
                extractedText += "\n\n--- DOCUMENT METADATA & ANNOTATIONS ---\n"
                        + bounded(structure.hiddenText(), MAX_SURFACE_TEXT_CHARS,
                                "Metadata and annotation text", limitations);
            }

            limitations.addAll(structure.limitations());

            OcrResult ocr = runOcr(document);
            limitations.addAll(ocr.limitations());
            String ocrText = ocr.text();
            if (!ocrText.isEmpty()) {
                log.debug("Extracted OCR text length: {}", ocrText.length());
                extractedText += "\n\n--- OCR EXTRACTED TEXT ---\n"
                        + bounded(ocrText, MAX_SURFACE_TEXT_CHARS, "OCR text", limitations);
            }

            PdfPreviewRenderer.Previews previews = previewRenderer.render(document, highlightsPerPage);

            return new PdfData(extractedText, previews.imagesBase64(), visualFindings,
                    structure.findings(), previews.pageNumbers(), limitations);
        }
    }

    /**
     * Cuts one section to its ceiling. A cut is recorded as a limitation rather
     * than only a log line: content past the ceiling is never analysed, so a scan
     * that silently dropped it could report a document as clean on the strength
     * of the part the author chose to show.
     */
    static String bounded(String text, int max, String section, List<String> limitations) {
        if (text == null || text.length() <= max) {
            return text;
        }
        log.warn("{} of {} chars exceeds the {} char limit; truncating.", section, text.length(), max);
        limitations.add(String.format("%s was truncated: only the first %,d of %,d characters were analysed.",
                section, max, text.length()));
        return text.substring(0, max) + "\n\n--- TEXT TRUNCATED AT SCAN LIMIT ---";
    }

    /** Reads text from one image. A seam so tests don't depend on a native Tesseract. */
    @FunctionalInterface
    interface OcrEngine {
        String read(BufferedImage image) throws Exception;
    }

    /** OCR output plus whatever it could not cover. */
    record OcrResult(String text, List<String> limitations) {}

    /**
     * OCR is best-effort, but its gaps are reported, not swallowed. Every image in
     * the document is counted, including those past the page and image caps, so a
     * document with a payload in an image on page 6, or on a host with no
     * Tesseract, is inconclusive rather than clean.
     *
     * When the native Tesseract library is missing or fails to initialise, tess4j
     * throws a LinkageError (ExceptionInInitializerError, UnsatisfiedLinkError) --
     * an Error, not an Exception. It is caught here so a document with images is
     * still scanned by the layers that don't depend on OCR, instead of failing
     * with a 500.
     */
    OcrResult runOcr(PDDocument document) {
        StringBuilder ocrText = new StringBuilder();
        int totalImages = 0;
        int attempted = 0;
        int failed = 0;
        boolean unavailable = false;

        try {
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                PDResources resources = document.getPage(i).getResources();
                if (resources == null) {
                    continue;
                }
                for (COSName name : resources.getXObjectNames()) {
                    try {
                        if (!resources.isImageXObject(name)) {
                            continue;
                        }
                    } catch (Exception e) {
                        continue;
                    }
                    totalImages++;
                    if (unavailable || i >= MAX_OCR_PAGES || attempted >= MAX_OCR_IMAGES) {
                        continue;
                    }
                    attempted++;
                    try {
                        var image = (PDImageXObject) resources.getXObject(name);
                        ocrText.append(ocrEngine.read(image.getImage())).append("\n");
                    } catch (LinkageError e) {
                        log.warn("OCR unavailable, continuing without it: {}", e.toString());
                        unavailable = true;
                        attempted--;
                    } catch (Exception e) {
                        // One unreadable image should not abandon the remaining ones
                        log.warn("OCR error on image {}: {}", name.getName(), e.getMessage());
                        failed++;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not enumerate images for OCR: {}", e.getMessage());
            return new OcrResult(ocrText.toString(), List.of(
                    "Images could not be fully enumerated, so text inside them may not have been examined."));
        }

        List<String> limitations = new ArrayList<>();
        if (unavailable) {
            limitations.add(String.format(
                    "OCR is unavailable on this server, so text inside %,d of %,d images was not examined.",
                    totalImages - (attempted - failed), totalImages));
        } else {
            int skipped = totalImages - attempted;
            if (skipped > 0) {
                log.info("OCR limited to {} of {} images.", attempted, totalImages);
                limitations.add(String.format(
                        "OCR covered only %,d of %,d images (the first %,d images on the first %,d pages).",
                        attempted, totalImages, MAX_OCR_IMAGES, MAX_OCR_PAGES));
            }
            if (failed > 0) {
                limitations.add(String.format(
                        "%,d of %,d images could not be read for OCR, so any text inside them was not examined.",
                        failed, totalImages));
            }
        }
        return new OcrResult(ocrText.toString(), limitations);
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
}
