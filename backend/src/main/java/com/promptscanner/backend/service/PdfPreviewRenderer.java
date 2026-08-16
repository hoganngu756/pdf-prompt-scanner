package com.promptscanner.backend.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.blend.BlendMode;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Turns flagged pages into highlighted PNG previews.
 *
 * Split out of PdfScannerService so that rendering — which is pure geometry and
 * image work — can be reasoned about and tested without text extraction, OCR, or
 * any repository standing behind it.
 */
@Service
public class PdfPreviewRenderer {

    private static final Logger log = LoggerFactory.getLogger(PdfPreviewRenderer.class);

    /** Nominal preview resolution, reduced automatically for oversized pages. */
    private static final float PREVIEW_DPI = 150f;

    /**
     * Page dimensions come from the uploaded file, and the PDF spec allows a
     * 14400x14400pt MediaBox. At 150 DPI that is 30000x30000px -- a 3.6 GB bitmap
     * from a ~600 byte upload. Capping total output pixels keeps render cost
     * bounded regardless of what the document declares.
     */
    private static final double MAX_PREVIEW_PIXELS = 4_000_000d;

    /** Returned for a page whose declared size admits no usable in-budget DPI. */
    static final float DPI_UNRENDERABLE = 0f;

    /** Cap on how many pages are rendered per scan, whatever the document size. */
    private static final int MAX_RENDERED_PAGES = 5;

    /** Rendered previews plus the 1-based source page numbers they correspond to. */
    public record Previews(List<String> imagesBase64, List<Integer> pageNumbers) {}

    /**
     * Chooses the largest DPI up to {@link #PREVIEW_DPI} that stays within the pixel
     * budget, or {@link #DPI_UNRENDERABLE} for a page whose declared geometry cannot
     * be rendered within it at all.
     */
    static float previewDpiFor(PDRectangle mediaBox) {
        float widthIn = Math.abs(mediaBox.getWidth()) / 72f;
        float heightIn = Math.abs(mediaBox.getHeight()) / 72f;
        if (widthIn <= 0 || heightIn <= 0) {
            return PREVIEW_DPI;
        }
        double pixelsAtFullDpi = (widthIn * PREVIEW_DPI) * (heightIn * PREVIEW_DPI);
        if (pixelsAtFullDpi <= MAX_PREVIEW_PIXELS) {
            return PREVIEW_DPI;
        }
        // No lower clamp: a floor here would silently re-admit the very bitmaps the
        // budget exists to reject. A 276000pt MediaBox needs 0.52 DPI to land inside
        // 4 MP; flooring that at 12 DPI asks for 46000x46000 -- an 8 GB allocation
        // from a 520 byte upload. The unclamped value is self-bounding, since
        // inches * sqrt(BUDGET / inches^2) collapses to sqrt(BUDGET) either way.
        float scaled = (float) Math.sqrt(MAX_PREVIEW_PIXELS / (widthIn * heightIn));
        // A MediaBox large enough to overflow the area computation leaves no DPI that
        // both renders a non-empty bitmap and respects the budget; such a page is
        // reported as unrenderable rather than clamped up into an allocation.
        return Float.isFinite(scaled) && scaled > 0f ? scaled : DPI_UNRENDERABLE;
    }

    /**
     * @param highlightsPerPage 0-based page index to the rectangles to highlight.
     *                          Pages absent from this map are not rendered, except
     *                          that page 1 is rendered when nothing was flagged so
     *                          the user still sees the document.
     */
    public Previews render(PDDocument document, Map<Integer, List<PDRectangle>> highlightsPerPage)
            throws IOException {

        List<Integer> pagesToRender = new ArrayList<>(highlightsPerPage.keySet());
        if (pagesToRender.isEmpty() && document.getNumberOfPages() > 0) {
            pagesToRender.add(0);
        }
        if (pagesToRender.size() > MAX_RENDERED_PAGES) {
            log.info("Limiting preview rendering to {} of {} flagged pages.",
                    MAX_RENDERED_PAGES, pagesToRender.size());
            pagesToRender = pagesToRender.subList(0, MAX_RENDERED_PAGES);
        }

        List<String> images = new ArrayList<>();
        List<Integer> pageNumbers = new ArrayList<>();
        PDFRenderer pdfRenderer = new PDFRenderer(document);

        for (int pageIndex : pagesToRender) {
            if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
                continue;
            }
            PDPage page = document.getPage(pageIndex);
            drawHighlights(document, page, highlightsPerPage.getOrDefault(pageIndex, List.of()));

            float dpi = previewDpiFor(page.getMediaBox());
            if (dpi == DPI_UNRENDERABLE) {
                log.warn("Page {} declares an unrenderable MediaBox ({}x{}pt); skipping its preview.",
                        pageIndex + 1, page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
                continue;
            }
            if (dpi < PREVIEW_DPI) {
                log.warn("Page {} is {}x{}pt; reducing preview to {} DPI to stay within the pixel budget.",
                        pageIndex + 1, page.getMediaBox().getWidth(), page.getMediaBox().getHeight(), dpi);
            }

            BufferedImage bim = pdfRenderer.renderImageWithDPI(pageIndex, dpi);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bim, "png", baos);
            images.add("data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray()));
            pageNumbers.add(pageIndex + 1);
        }

        return new Previews(images, pageNumbers);
    }

    private void drawHighlights(PDDocument document, PDPage page, List<PDRectangle> rects) throws IOException {
        if (rects.isEmpty()) {
            return;
        }
        try (PDPageContentStream cs = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
            graphicsState.setNonStrokingAlphaConstant(0.4f);
            graphicsState.setBlendMode(BlendMode.MULTIPLY);
            cs.setGraphicsStateParameters(graphicsState);
            cs.setNonStrokingColor(Color.YELLOW);

            float pageHeight = page.getMediaBox().getHeight();
            for (PDRectangle rect : rects) {
                cs.addRect(rect.getLowerLeftX(), pageHeight - rect.getUpperRightY(),
                        rect.getWidth(), rect.getHeight());
                cs.fill();
            }
        }
    }
}
