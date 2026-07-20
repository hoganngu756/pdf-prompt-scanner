package com.promptscanner.backend.service;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColorSpace;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceCMYKColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceGrayColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceRGBColor;

import java.io.IOException;
import java.util.*;

public class HighlightingTextStripper extends PDFTextStripper {

    private final Set<String> highlightWords;
    private final List<String> visualObfuscationFindings = new ArrayList<>();
    private final Map<Integer, List<PDRectangle>> highlightsPerPage = new HashMap<>();
    private final Map<TextPosition, float[]> characterColors = new java.util.IdentityHashMap<>();
    private int currentPageIndex = 0;

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
    }

    @Override
    protected void processTextPosition(TextPosition text) {
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

        if (isWhiteText || hasTinyText) {
            String trimmedText = text.trim();
            if (!trimmedText.isEmpty()) {
                String finding;
                if (isWhiteText && hasTinyText) {
                    finding = String.format("Page %d: White & tiny text (size %.1fpt): '%s'", getCurrentPageNo(), minSizeFound, trimmedText);
                } else if (isWhiteText) {
                    finding = String.format("Page %d: Invisible/white text: '%s'", getCurrentPageNo(), trimmedText);
                } else {
                    finding = String.format("Page %d: Tiny text (size %.1fpt): '%s'", getCurrentPageNo(), minSizeFound, trimmedText);
                }
                visualObfuscationFindings.add(finding);

                PDRectangle rect = computeBoundingBox(textPositions);
                highlightsPerPage.computeIfAbsent(currentPageIndex, k -> new ArrayList<>()).add(rect);
            }
        }

        String lowerText = text.toLowerCase();
        for (String word : highlightWords) {
            if (lowerText.contains(word)) {
                PDRectangle rect = computeBoundingBox(textPositions);
                highlightsPerPage.computeIfAbsent(currentPageIndex, k -> new ArrayList<>()).add(rect);
                break;
            }
        }
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

    public List<String> getVisualObfuscationFindings() {
        return visualObfuscationFindings;
    }

    public Map<Integer, List<PDRectangle>> getHighlightsPerPage() {
        return highlightsPerPage;
    }
}
