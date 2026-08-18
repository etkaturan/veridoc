package com.etka.veridoc.ocr;

import java.awt.image.BufferedImage;
import java.util.Optional;

/**
 * Crops an image to the bounding box of its dark pixels.
 *
 * <p>Character cell arithmetic assumes the text spans the full width of the
 * image. Any surrounding whitespace shifts every cell boundary and corrupts
 * the whole read, so the band is trimmed to its ink before slicing.
 */
public final class ImageTrimmer {

    private static final int DARKNESS_THRESHOLD = 128;

    private ImageTrimmer() {
        throw new AssertionError("Utility class — not meant to be instantiated");
    }

    /**
     * @param image the image to trim
     * @return the ink-bounded sub-image, or empty if the image is entirely blank
     */
    public static Optional<BufferedImage> trim(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        int left = width;
        int right = -1;
        int top = height;
        int bottom = -1;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int green = (image.getRGB(x, y) >> 8) & 0xFF;
                if (green < DARKNESS_THRESHOLD) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
        }

        if (right < 0) {
            return Optional.empty();
        }

        return Optional.of(image.getSubimage(
                left, top, right - left + 1, bottom - top + 1));
    }
}