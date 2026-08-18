package com.promptscanner.backend.service;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import com.promptscanner.backend.dto.Finding;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColorSpace;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceCMYKColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceGrayColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceRGBColor;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class HighlightingTextStripper extends PDFTextStripper {

    private static final Logger log = LoggerFactory.getLogger(HighlightingTextStripper.class);

    private final Set<String> highlightWords;
    private final List<Finding> visualObfuscationFindings = new ArrayList<>();
    private final Map<Integer, List<PDRectangle>> highlightsPerPage = new HashMap<>();
    private final Map<TextPosition, float[]> characterColors = new java.util.IdentityHashMap<>();
    /** Glyphs painted with a rendering mode that draws nothing (Tr 3 / Tr 7). */
    private final Set<TextPosition> invisibleModeGlyphs =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    /** Glyphs painted with an effectively transparent fill alpha. */
    private final Set<TextPosition> transparentGlyphs =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private int currentPageIndex = 0;

    /** Fill alpha at or below this is invisible to a reader but still extractable. */
    private static final float TRANSPARENT_ALPHA = 0.05f;
    /** Share of glyphs in a run that must be hidden before the run is reported. */
    private static final double HIDDEN_RUN_THRESHOLD = 0.8;

    /**
     * Ceiling on glyphs whose paint state is retained for one page.
     *
     * PDFTextStripper deliberately discards glyphs drawn on top of already-painted
     * ones; this subclass keeps every one it is handed, so without a ceiling a
     * single page is unbounded. Deflate compresses a repeated "draw at the same
     * coordinates" run about 330:1, which turned a 7.6 KB upload into an
     * OutOfMemoryError on a 128 MB heap -- and OOM is an Error, so it unwinds past
     * every catch in the request path and takes the instance with it.
     */
    private static final int MAX_TRACKED_GLYPHS_PER_PAGE = 200_000;

    /** Ceiling on reported hidden-text runs, mirroring PdfStructureScanner's caps. */
    private static final int MAX_VISUAL_FINDINGS = 200;
    /** Findings are display strings; the quoted run is trimmed to keep them bounded. */
    private static final int MAX_FINDING_TEXT_CHARS = 500;
    /** Ceiling on highlight rectangles per page, which also bounds the drawn overlay. */
    private static final int MAX_HIGHLIGHTS_PER_PAGE = 500;

    private int trackedGlyphs = 0;
    private boolean glyphCapReported = false;

    public HighlightingTextStripper(Set<String> highlightWords) throws IOException {
        this.highlightWords = highlightWords;
        addOperator(new SetNonStrokingColor(this));
        addOperator(new SetNonStrokingColorSpace(this));
        addOperator(new SetNonStrokingDeviceCMYKColor(this));
        addOperator(new SetNonStrokingDeviceGrayColor(this));
        addOperator(new SetNonStrokingDeviceRGBColor(this));
    }

    @Override
    protected void startPage(PDPage page) throws IOException {
        super.startPage(page);
        currentPageIndex = getCurrentPageNo() - 1;
        // Per-glyph state is only consulted within the page being written, so
        // clearing here keeps these from growing across the whole document.
        characterColors.clear();
        invisibleModeGlyphs.clear();
        transparentGlyphs.clear();
        trackedGlyphs = 0;
        glyphCapReported = false;
    }

    @Override
    protected void processTextPosition(TextPosition text) {
        // The ceiling is applied BEFORE delegating, because the parent accumulates
        // glyphs for the page in its own list: PDFTextStripper alone, with none of
        // this class involved, dies on a 510 KB page of distinct-position glyphs at
        // a 1 GB heap. Dropping the glyph here is the only way to keep it out of
        // that list. Extraction for the rest of the page is given up deliberately --
        // a partial scan beats an OutOfMemoryError, which is an Error and so unwinds
        // past every catch in the request path.
        if (trackedGlyphs >= MAX_TRACKED_GLYPHS_PER_PAGE) {
            reportGlyphCap();
            return;
        }
        trackedGlyphs++;

        super.processTextPosition(text);

        org.apache.pdfbox.pdmodel.graphics.color.PDColor color = getGraphicsState().getNonStrokingColor();
        if (color != null && color.getColorSpace() != null) {
            try {
                float[] rgb = color.getColorSpace().toRGB(color.getComponents());
                characterColors.put(text, rgb);
            } catch (Exception e) {
                // ignore
            }
        }

        // Text rendering mode 3 (and 7) paint no glyph at all. This is the standard
        // way to attach an invisible OCR layer, and equally the simplest way to hide
        // an injection: unreadable to a human, fully extractable by an LLM.
        try {
            RenderingMode mode = getGraphicsState().getTextState().getRenderingMode();
            if (mode != null && !mode.isFill() && !mode.isStroke()) {
                invisibleModeGlyphs.add(text);
            }
        } catch (Exception e) {
            // ignore - absence of a text state just means we can't judge this glyph
        }

        // Fully transparent fill hides the glyph just as effectively as white-on-white
        try {
            if (getGraphicsState().getNonStrokeAlphaConstant() <= TRANSPARENT_ALPHA) {
                transparentGlyphs.add(text);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
        super.writeString(text, textPositions);
        
        boolean isWhiteText = false;
        int whiteCount = 0;
        for (TextPosition tp : textPositions) {
            float[] rgb = characterColors.get(tp);
            if (rgb != null && rgb.length >= 3) {
                if (rgb[0] > 0.97f && rgb[1] > 0.97f && rgb[2] > 0.97f) {
                    whiteCount++;
                }
            }
        }
        if (!textPositions.isEmpty() && whiteCount >= textPositions.size() * 0.8) {
            isWhiteText = true;
        }

        boolean hasTinyText = false;
        float minSizeFound = Float.MAX_VALUE;
        for (TextPosition tp : textPositions) {
            float size = tp.getFontSizeInPt();
            if (size > 0 && size < 3.0f) {
                hasTinyText = true;
                if (size < minSizeFound) {
                    minSizeFound = size;
                }
            }
        }

        boolean isInvisibleMode = majorityOf(textPositions, invisibleModeGlyphs);
        boolean isTransparent = majorityOf(textPositions, transparentGlyphs);

        // Each mechanism is reported by name so the user learns how it was hidden
        List<String> reasons = new ArrayList<>();
        if (isInvisibleMode) {
            reasons.add("invisible text rendering mode (Tr 3)");
        }
        if (isTransparent) {
            reasons.add("fully transparent fill");
        }
        if (isWhiteText) {
            reasons.add("white/near-white text");
        }
        if (hasTinyText) {
            reasons.add(String.format("tiny text (size %.1fpt)", minSizeFound));
        }

        if (!reasons.isEmpty()) {
            String trimmedText = text.trim();
            // Both lists are serialised into the scan response, so a document made of
            // hidden runs would otherwise amplify a small upload into a multi-megabyte
            // body -- expensive to build, and enough to hang the browser rendering it.
            if (!trimmedText.isEmpty() && visualObfuscationFindings.size() < MAX_VISUAL_FINDINGS) {
                visualObfuscationFindings.add(Finding.quoting(
                        "Hidden text via " + String.join(" + ", reasons),
                        "Page " + getCurrentPageNo(),
                        truncate(trimmedText)));

                addHighlight(computeBoundingBox(textPositions));
            }
        }

        String lowerText = text.toLowerCase();
        for (String word : highlightWords) {
            if (lowerText.contains(word)) {
                addHighlight(computeBoundingBox(textPositions));
                break;
            }
        }
    }

    /**
     * Records the glyph ceiling once per page, as a finding rather than only a log
     * line. Giving up extraction silently would hand an author an evasion route --
     * pad a page past the ceiling, then hide the payload behind it -- so the
     * truncation is reported to the reader. A page carrying this many glyphs is
     * itself abnormal: a densely typeset one runs to a few thousand.
     */
    private void reportGlyphCap() {
        if (glyphCapReported) {
            return;
        }
        glyphCapReported = true;
        log.warn("Page {} exceeded {} glyphs; text extraction and visual-obfuscation "
                        + "analysis are truncated for the remainder of the page.",
                getCurrentPageNo(), MAX_TRACKED_GLYPHS_PER_PAGE);
        if (visualObfuscationFindings.size() < MAX_VISUAL_FINDINGS) {
            visualObfuscationFindings.add(Finding.at(String.format(
                    "Abnormal glyph volume — over %,d text-drawing operations on a single page. "
                            + "Analysis of this page was truncated, and content beyond that point "
                            + "was not examined.",
                    MAX_TRACKED_GLYPHS_PER_PAGE),
                    "Page " + getCurrentPageNo()));
        }
    }

    /** Bounds the rectangles held per page, and with them the overlay drawn onto it. */
    private void addHighlight(PDRectangle rect) {
        List<PDRectangle> rects = highlightsPerPage.computeIfAbsent(currentPageIndex, k -> new ArrayList<>());
        if (rects.size() < MAX_HIGHLIGHTS_PER_PAGE) {
            rects.add(rect);
        }
    }

    private static String truncate(String value) {
        return value.length() <= MAX_FINDING_TEXT_CHARS
                ? value
                : value.substring(0, MAX_FINDING_TEXT_CHARS) + "…";
    }

    /**
     * True when enough glyphs in the run carry a property that a stray character
     * or two shouldn't trigger the finding on its own.
     */
    private boolean majorityOf(List<TextPosition> textPositions, Set<TextPosition> marked) {
        if (textPositions.isEmpty() || marked.isEmpty()) {
            return false;
        }
        int count = 0;
        for (TextPosition tp : textPositions) {
            if (marked.contains(tp)) {
                count++;
            }
        }
        return count >= textPositions.size() * HIDDEN_RUN_THRESHOLD;
    }

    private PDRectangle computeBoundingBox(List<TextPosition> textPositions) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        float maxY = Float.MIN_VALUE;
        
        for (TextPosition tp : textPositions) {
            if (tp.getXDirAdj() < minX) minX = tp.getXDirAdj();
            if (tp.getYDirAdj() < minY) minY = tp.getYDirAdj() - tp.getHeightDir();
            if (tp.getXDirAdj() + tp.getWidthDirAdj() > maxX) maxX = tp.getXDirAdj() + tp.getWidthDirAdj();
            if (tp.getYDirAdj() > maxY) maxY = tp.getYDirAdj();
        }
        
        PDRectangle rect = new PDRectangle();
        rect.setLowerLeftX(minX);
        rect.setLowerLeftY(minY);
        rect.setUpperRightX(maxX);
        rect.setUpperRightY(maxY + 2);
        return rect;
    }

    public List<Finding> getVisualObfuscationFindings() {
        return visualObfuscationFindings;
    }

    public Map<Integer, List<PDRectangle>> getHighlightsPerPage() {
        return highlightsPerPage;
    }
}
