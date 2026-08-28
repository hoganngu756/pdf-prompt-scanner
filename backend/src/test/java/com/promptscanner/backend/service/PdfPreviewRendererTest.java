package com.promptscanner.backend.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rendering is the most memory-hungry thing this service does, on a single small
 * instance, driven entirely by a page geometry the uploader chooses. These cover
 * the ceilings that keep it bounded.
 */
class PdfPreviewRendererTest {

    private static final int MAX_RENDERED_PAGES = 5;

    private final PdfPreviewRenderer renderer = new PdfPreviewRenderer();

    private PDDocument documentOf(int pages, PDRectangle size) {
        PDDocument doc = new PDDocument();
        for (int i = 0; i < pages; i++) {
            doc.addPage(new PDPage(size));
        }
        return doc;
    }

    private Map<Integer, List<PDRectangle>> flagAll(int pages) {
        Map<Integer, List<PDRectangle>> highlights = new LinkedHashMap<>();
        for (int i = 0; i < pages; i++) {
            highlights.put(i, List.of(new PDRectangle(10, 10, 50, 12)));
        }
        return highlights;
    }

    @Test
    void rendersAtMostFivePagesHoweverManyAreFlagged() throws IOException {
        try (PDDocument doc = documentOf(12, PDRectangle.LETTER)) {
            PdfPreviewRenderer.Previews previews = renderer.render(doc, flagAll(12));

            assertEquals(MAX_RENDERED_PAGES, previews.imagesBase64().size());
            assertEquals(MAX_RENDERED_PAGES, previews.pageNumbers().size());
        }
    }

    @Test
    void reportsOneBasedSourcePageNumbersAlignedWithTheImages() throws IOException {
        Map<Integer, List<PDRectangle>> highlights = new LinkedHashMap<>();
        highlights.put(2, List.of(new PDRectangle(10, 10, 50, 12)));

        try (PDDocument doc = documentOf(4, PDRectangle.LETTER)) {
            PdfPreviewRenderer.Previews previews = renderer.render(doc, highlights);

            assertEquals(List.of(3), previews.pageNumbers());
            assertEquals(1, previews.imagesBase64().size());
        }
    }

    @Test
    void showsTheFirstPageWhenNothingWasFlagged() throws IOException {
        try (PDDocument doc = documentOf(3, PDRectangle.LETTER)) {
            PdfPreviewRenderer.Previews previews = renderer.render(doc, Map.of());

            assertEquals(List.of(1), previews.pageNumbers());
        }
    }

    @Test
    void survivesAnEnormousPageInsteadOfExhaustingMemory() throws IOException {
        // A PDF may declare any MediaBox it likes. At a fixed DPI this one would
        // rasterise to billions of pixels, so the renderer has to scale down or
        // skip rather than attempt it.
        try (PDDocument doc = documentOf(1, new PDRectangle(14_400, 14_400))) {
            PdfPreviewRenderer.Previews previews = renderer.render(doc, Map.of());

            assertNotNull(previews.imagesBase64());
            assertEquals(previews.imagesBase64().size(), previews.pageNumbers().size(),
                    "images and page numbers must stay aligned by index");
        }
    }

    @Test
    void choosesAnInBudgetDpiForEachDeclaredGeometry() {
        // Ordinary page: full quality.
        assertEquals(150f, PdfPreviewRenderer.previewDpiFor(PDRectangle.LETTER));

        // The spec's largest legal MediaBox would be 30000x30000px at 150 DPI, a
        // 3.6 GB bitmap from a tiny upload. It must scale down instead.
        float huge = PdfPreviewRenderer.previewDpiFor(new PDRectangle(14_400, 14_400));
        assertTrue(huge > 0f && huge < 150f, "expected a reduced DPI, got " + huge);

        // A geometry that overflows the area computation admits no DPI that both
        // renders something and respects the budget: report it, never clamp up.
        assertEquals(PdfPreviewRenderer.DPI_UNRENDERABLE,
                PdfPreviewRenderer.previewDpiFor(new PDRectangle(Float.MAX_VALUE, Float.MAX_VALUE)));
    }

    @Test
    void skipsAPageWhoseGeometryAdmitsNoUsableDpi() throws IOException {
        try (PDDocument doc = documentOf(1, new PDRectangle(Float.MAX_VALUE, Float.MAX_VALUE))) {
            PdfPreviewRenderer.Previews previews = renderer.render(doc, Map.of());

            assertTrue(previews.imagesBase64().isEmpty());
            assertTrue(previews.pageNumbers().isEmpty());
        }
    }
}
