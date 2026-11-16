package com.promptscanner.backend.service;

import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Decides whether an embedded image is small enough to decode.
 *
 * An image's dimensions come from the uploaded file, and decoding allocates in
 * proportion to them, not to the upload: a 12000x12000 RGB image of one colour
 * deflates to 420 KB and decodes to well over 400 MB, which was an
 * OutOfMemoryError from a single request. Both places that decode images -- OCR
 * and the page preview -- ask here first and leave an oversized image alone.
 */
final class ImageBudget {

    /**
     * Ceiling on pixels in one decoded image. An A4 page scanned at 300 DPI is
     * about 8.7 megapixels, so ordinary scans fit; much beyond this a single
     * image costs more heap than a small instance has to give.
     */
    static final long MAX_IMAGE_PIXELS = 10_000_000L;

    private ImageBudget() {}

    /**
     * True when decoding the image, or a mask decoded along with it, would
     * exceed the budget. An image whose masks cannot even be looked up is
     * treated as over budget: it is not safe to hand to a decoder.
     */
    static boolean exceeds(PDImage image) {
        if (pixels(image) > MAX_IMAGE_PIXELS) {
            return true;
        }
        if (image instanceof PDImageXObject xObject) {
            try {
                // getImage() decodes these too, at their own declared size
                PDImageXObject softMask = xObject.getSoftMask();
                PDImageXObject mask = xObject.getMask();
                return (softMask != null && pixels(softMask) > MAX_IMAGE_PIXELS)
                        || (mask != null && pixels(mask) > MAX_IMAGE_PIXELS);
            } catch (Exception e) {
                return true;
            }
        }
        return false;
    }

    private static long pixels(PDImage image) {
        return (long) Math.abs(image.getWidth()) * Math.abs(image.getHeight());
    }
}
