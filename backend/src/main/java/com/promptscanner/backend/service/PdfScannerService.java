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
import net.sourceforge.tess4j.Tesseract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import com.promptscanner.backend.entity.HeuristicRule;
import com.promptscanner.backend.repository.HeuristicRuleRepository;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColorSpace;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceCMYKColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceGrayColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceRGBColor;

@Service
public class PdfScannerService {

    private static final Logger log = LoggerFactory.getLogger(PdfScannerService.class);

    private final HeuristicRuleRepository heuristicRuleRepository;

    @Value("${ocr.tessdata.path:}")
    private String tessDataPath;

    @Value("${app.scan.max-pages:50}")
    private int maxPages;

    // Use ThreadLocal to cache Tesseract instances across requests safely
    private ThreadLocal<Tesseract> tesseractThreadLocal;

    public PdfScannerService(HeuristicRuleRepository heuristicRuleRepository) {
        this.heuristicRuleRepository = heuristicRuleRepository;
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

    public static class PdfData {
        public String extractedText;
        public List<String> previewImagesBase64 = new ArrayList<>();
        public List<String> visualObfuscationFindings = new ArrayList<>();
    }

    public PdfData processPdf(MultipartFile file) throws IOException {
        PdfData data = new PdfData();

        // Build active highlight words dynamically
        List<HeuristicRule> activeRules = heuristicRuleRepository.findByIsActiveTrue();
        Set<String> highlightWords = new HashSet<>();
        for (HeuristicRule rule : activeRules) {
            if (!rule.isRegex()) {
                String[] words = rule.getPhrase().toLowerCase().split("[\\W_]+");
                for (String w : words) {
                    if (w.length() > 3) {
                        highlightWords.add(w);
                    }
                }
            }
        }
        if (highlightWords.isEmpty()) {
            highlightWords.addAll(List.of("ignore", "previous", "instructions", "system", "message", "prompt", "bypass"));
        }

        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            if (document.getNumberOfPages() > maxPages) {
                throw new IllegalArgumentException("PDF exceeds maximum allowed page count of " + maxPages + " pages (found " + document.getNumberOfPages() + ").");
            }
            
            // Map of pageIndex -> list of rectangles to highlight
            Map<Integer, List<PDRectangle>> highlightsPerPage = new HashMap<>();

            PDFTextStripper stripper = new PDFTextStripper() {
                private int currentPageIndex = 0;
                private final Map<TextPosition, float[]> characterColors = new java.util.IdentityHashMap<>();

                {
                    addOperator(new SetNonStrokingColor(this));
                    addOperator(new SetNonStrokingColorSpace(this));
                    addOperator(new SetNonStrokingDeviceCMYKColor(this));
                    addOperator(new SetNonStrokingDeviceGrayColor(this));
                    addOperator(new SetNonStrokingDeviceRGBColor(this));
                }

                @Override
                protected void startPage(PDPage page) throws IOException {
                    super.startPage(page);
                    // PDFBox pages are 1-indexed in PDFTextStripper getCurrentPageNo()
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
                            String finding = "";
                            if (isWhiteText && hasTinyText) {
                                finding = String.format("Page %d: White & tiny text (size %.1fpt): '%s'", getCurrentPageNo(), minSizeFound, trimmedText);
                            } else if (isWhiteText) {
                                finding = String.format("Page %d: Invisible/white text: '%s'", getCurrentPageNo(), trimmedText);
                            } else {
                                finding = String.format("Page %d: Tiny text (size %.1fpt): '%s'", getCurrentPageNo(), minSizeFound, trimmedText);
                            }
                            data.visualObfuscationFindings.add(finding);

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
                            
                            highlightsPerPage.computeIfAbsent(currentPageIndex, k -> new ArrayList<>()).add(rect);
                        }
                    }

                    String lowerText = text.toLowerCase();
                    for (String word : highlightWords) {
                        if (lowerText.contains(word)) {
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
                            
                            highlightsPerPage.computeIfAbsent(currentPageIndex, k -> new ArrayList<>()).add(rect);
                            break;
                        }
                    }
                }
            };
            stripper.setSortByPosition(true);
            data.extractedText = stripper.getText(document);

            StringBuilder ocrText = new StringBuilder();
            Tesseract tesseract = tesseractThreadLocal.get();
            
            int ocrImageCount = 0;
            int maxOcrPages = Math.min(document.getNumberOfPages(), 5); // limit OCR to first 5 pages
            for (int i = 0; i < maxOcrPages; i++) {
                if (ocrImageCount >= 10) {
                    log.info("Reached maximum OCR image limit (10). Skipping remaining images.");
                    break;
                }
                PDPage page = document.getPage(i);
                org.apache.pdfbox.pdmodel.PDResources resources = page.getResources();
                if (resources != null) {
                    for (org.apache.pdfbox.cos.COSName name : resources.getXObjectNames()) {
                        try {
                            if (resources.isImageXObject(name)) {
                                if (ocrImageCount >= 10) break;
                                ocrImageCount++;
                                org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject image = (org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) resources.getXObject(name);
                                BufferedImage bim = image.getImage();
                                String result = tesseract.doOCR(bim);
                                ocrText.append(result).append("\n");
                            }
                        } catch (Exception e) {
                            log.warn("OCR Error on image {}: {}", name.getName(), e.getMessage());
                        }
                    }
                }
            }

            if (ocrText.length() > 0) {
                log.debug("Extracted OCR Text length: {}", ocrText.length());
                data.extractedText += "\n\n--- OCR EXTRACTED TEXT ---\n" + ocrText.toString();
            }

            // Determine which pages to render (pages with highlights, or just the first page if none)
            List<Integer> pagesToRender = new ArrayList<>(highlightsPerPage.keySet());
            if (pagesToRender.isEmpty() && document.getNumberOfPages() > 0) {
                pagesToRender.add(0); // Render first page by default so the UI isn't empty
            }
            if (pagesToRender.size() > 5) {
                log.info("Limiting page rendering preview to first 5 flagged pages out of {}", pagesToRender.size());
                pagesToRender = pagesToRender.subList(0, 5);
            }

            PDFRenderer pdfRenderer = new PDFRenderer(document);

            for (int pageIndex : pagesToRender) {
                if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) continue;
                
                PDPage page = document.getPage(pageIndex);
                List<PDRectangle> rects = highlightsPerPage.getOrDefault(pageIndex, new ArrayList<>());

                // Draw Highlights for this page
                if (!rects.isEmpty()) {
                    try (PDPageContentStream cs = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                        PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
                        graphicsState.setNonStrokingAlphaConstant(0.4f);
                        graphicsState.setBlendMode(BlendMode.MULTIPLY);
                        cs.setGraphicsStateParameters(graphicsState);
                        cs.setNonStrokingColor(Color.YELLOW);
                        
                        for (PDRectangle rect : rects) {
                            float x = rect.getLowerLeftX();
                            float pageHeight = page.getMediaBox().getHeight();
                            float y = pageHeight - rect.getUpperRightY(); 
                            float width = rect.getWidth();
                            float height = rect.getHeight();
                            
                            cs.addRect(x, y, width, height);
                            cs.fill();
                        }
                    }
                }

                // Render the page to image
                BufferedImage bim = pdfRenderer.renderImageWithDPI(pageIndex, 150);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bim, "png", baos);
                byte[] imageBytes = baos.toByteArray();
                data.previewImagesBase64.add("data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes));
            }
        }

        return data;
    }
}
