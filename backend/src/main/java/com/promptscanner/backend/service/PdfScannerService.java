package com.promptscanner.backend.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.blend.BlendMode;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class PdfScannerService {

    public static class PdfData {
        public String extractedText;
        public String previewImageBase64;
    }

    // A simple list of words we want to highlight if they appear
    private static final List<String> HIGHLIGHT_WORDS = List.of(
            "ignore", "previous", "instructions", "system", "message", "prompt", "bypass"
    );

    public PdfData processPdf(MultipartFile file) throws IOException {
        PdfData data = new PdfData();

        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            
            // 1. Extract Text and find positions of suspicious words
            List<PDRectangle> highlightRects = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
                    super.writeString(text, textPositions);
                    
                    String lowerText = text.toLowerCase();
                    for (String word : HIGHLIGHT_WORDS) {
                        if (lowerText.contains(word)) {
                            // Find bounding box for the entire matched string in this chunk
                            float minX = Float.MAX_VALUE;
                            float minY = Float.MAX_VALUE;
                            float maxX = Float.MIN_VALUE;
                            float maxY = Float.MIN_VALUE;
                            
                            for (TextPosition tp : textPositions) {
                                if (tp.getXDirAdj() < minX) minX = tp.getXDirAdj();
                                if (tp.getYDirAdj() < minY) minY = tp.getYDirAdj() - tp.getHeightDir(); // PDF Y is inverted sometimes, or we use standard page coords
                                if (tp.getXDirAdj() + tp.getWidthDirAdj() > maxX) maxX = tp.getXDirAdj() + tp.getWidthDirAdj();
                                if (tp.getYDirAdj() > maxY) maxY = tp.getYDirAdj();
                            }
                            
                            // Adjust Y coordinates because TextPosition Y is baseline
                            // The actual bounds calculation can be tricky in PDFBox, this is a reasonable approximation
                            PDRectangle rect = new PDRectangle();
                            rect.setLowerLeftX(minX);
                            rect.setLowerLeftY(minY);
                            rect.setUpperRightX(maxX);
                            rect.setUpperRightY(maxY + 2); // add a little padding
                            highlightRects.add(rect);
                            break; // just highlight the whole line chunk if it matches any word
                        }
                    }
                }
            };
            stripper.setSortByPosition(true);
            data.extractedText = stripper.getText(document);

            // 2. Draw Highlights on the first page
            if (document.getNumberOfPages() > 0 && !highlightRects.isEmpty()) {
                PDPage firstPage = document.getPage(0);
                
                // We use APPEND so we draw over existing content
                try (PDPageContentStream cs = new PDPageContentStream(document, firstPage, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    
                    // Setup transparency so text shows through the highlight
                    PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
                    graphicsState.setNonStrokingAlphaConstant(0.4f);
                    graphicsState.setBlendMode(BlendMode.MULTIPLY);
                    cs.setGraphicsStateParameters(graphicsState);
                    
                    cs.setNonStrokingColor(Color.YELLOW);
                    
                    for (PDRectangle rect : highlightRects) {
                        // PDF coordinates start from bottom-left by default, but TextPosition Y might be from top depending on transforms.
                        // However, TextPosition getXDirAdj() and getYDirAdj() return coordinates relative to the page lower-left.
                        float x = rect.getLowerLeftX();
                        // TextPosition Y is usually the baseline from the top. We need to convert it to bottom-up PDF coordinates.
                        float pageHeight = firstPage.getMediaBox().getHeight();
                        float y = pageHeight - rect.getUpperRightY(); 
                        float width = rect.getWidth();
                        float height = rect.getHeight();
                        
                        cs.addRect(x, y, width, height);
                        cs.fill();
                    }
                }
            }

            // 3. Render the first page to an image
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            if (document.getNumberOfPages() > 0) {
                // Render at 150 DPI for a decent preview size
                BufferedImage bim = pdfRenderer.renderImageWithDPI(0, 150);
                
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bim, "png", baos);
                byte[] imageBytes = baos.toByteArray();
                data.previewImageBase64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
            }
        }

        return data;
    }
}
