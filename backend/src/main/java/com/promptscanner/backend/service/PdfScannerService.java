package com.promptscanner.backend.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class PdfScannerService {

    /**
     * Extracts all text from a PDF file.
     * Note: PDFBox's PDFTextStripper extracts text regardless of color, 
     * making it effective against "white text on white background" attacks.
     */
    public String extractText(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
             
            PDFTextStripper stripper = new PDFTextStripper();
            
            // We can optionally sort by position if needed, 
            // but default extraction is usually sufficient for finding injected strings.
            stripper.setSortByPosition(true);
            
            return stripper.getText(document);
        }
    }
}
