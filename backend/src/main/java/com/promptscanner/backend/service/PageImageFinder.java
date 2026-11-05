package com.promptscanner.backend.service;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Finds every image a page carries, for OCR.
 *
 * Listing the page's own /XObject resources missed two places an image can sit:
 * inside a form XObject (a reusable drawing, which may itself contain forms), and
 * inline in the content stream (BI ... ID ... EI), where it has no resource entry
 * at all. Either one hid an image -- and any text rendered into it -- from OCR,
 * without being reported.
 *
 * Images are found two ways and merged: by running the page's content stream,
 * which catches inline images and images drawn through forms, and by walking the
 * resource tree, which catches images that are listed but never drawn. An
 * undrawn image is invisible to a reader, but tools that extract images from a
 * PDF read resources directly, so it still reaches a model.
 */
final class PageImageFinder {

    /**
     * Ceiling on content-stream operators processed per page. A form can draw
     * another form many times over, so the operator count is not bounded by the
     * file size; this keeps a nesting bomb from costing unbounded CPU.
     */
    static final int MAX_OPERATORS_PER_PAGE = 2_000_000;

    /** Thrown when a page's operator budget is spent. */
    static final class BudgetExceeded extends IOException {
        BudgetExceeded() {
            super("operator budget exceeded");
        }
    }

    private PageImageFinder() {}

    /**
     * Hands each distinct image on the page to {@code sink}. Image XObjects are
     * reported once per document: {@code seen} is shared across pages, so a logo
     * repeated on every page counts once. Inline images have no identity and are
     * reported each time they occur.
     *
     * @throws BudgetExceeded if the page's content stream exceeds the operator
     *                        ceiling; images found before that point have
     *                        already been handed over
     */
    static void forEachImage(PDPage page, Set<COSBase> seen, Consumer<PDImage> sink) throws IOException {
        new DrawnImages(page, seen, sink).processPage(page);
        listedImages(page.getResources(), seen, sink);
    }

    /** Images reachable through the resource tree, descending into forms. */
    private static void listedImages(PDResources pageResources, Set<COSBase> seen, Consumer<PDImage> sink) {
        if (pageResources == null) {
            return;
        }
        // Forms can reference each other in a cycle; visit each resource dictionary once.
        Set<COSBase> visitedResources = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<PDResources> pending = new ArrayDeque<>();
        pending.push(pageResources);
        while (!pending.isEmpty()) {
            PDResources resources = pending.pop();
            if (!visitedResources.add(resources.getCOSObject())) {
                continue;
            }
            for (COSName name : resources.getXObjectNames()) {
                PDXObject xObject;
                try {
                    xObject = resources.getXObject(name);
                } catch (Exception e) {
                    continue;
                }
                if (xObject instanceof PDImageXObject image) {
                    if (seen.add(image.getCOSObject())) {
                        sink.accept(image);
                    }
                } else if (xObject instanceof PDFormXObject form && form.getResources() != null) {
                    pending.push(form.getResources());
                }
            }
        }
    }

    /**
     * Runs the content stream to see which images are actually painted, including
     * inline ones and those inside forms. Path construction and painting are
     * irrelevant here and left empty, and text showing is skipped outright:
     * glyph processing is the expensive part of a content stream, and the text
     * stripper has already done it once.
     */
    private static final class DrawnImages extends PDFGraphicsStreamEngine {
        private final Set<COSBase> seen;
        private final Consumer<PDImage> sink;
        private int operators;

        DrawnImages(PDPage page, Set<COSBase> seen, Consumer<PDImage> sink) {
            super(page);
            this.seen = seen;
            this.sink = sink;
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            if (++operators > MAX_OPERATORS_PER_PAGE) {
                throw new BudgetExceeded();
            }
            super.processOperator(operator, operands);
        }

        @Override
        public void drawImage(PDImage image) {
            if (image instanceof PDImageXObject xObject && !seen.add(xObject.getCOSObject())) {
                return;
            }
            sink.accept(image);
        }

        @Override
        public void showTextString(byte[] string) {
        }

        @Override
        public void showTextStrings(COSArray array) {
        }

        @Override public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {}
        @Override public void clip(int windingRule) {}
        @Override public void moveTo(float x, float y) {}
        @Override public void lineTo(float x, float y) {}
        @Override public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {}
        @Override public Point2D getCurrentPoint() { return new Point2D.Float(0, 0); }
        @Override public void closePath() {}
        @Override public void endPath() {}
        @Override public void strokePath() {}
        @Override public void fillPath(int windingRule) {}
        @Override public void fillAndStrokePath(int windingRule) {}
        @Override public void shadingFill(COSName shadingName) {}
    }
}
