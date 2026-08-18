package com.etka.veridoc.ocr;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits an image band into horizontal strips, one per text line.
 *
 * <p>Uses a horizontal projection profile: the count of dark pixels in each
 * row. Rows containing text have a high count; the gaps between lines have a
 * count near zero. The boundaries between those regions are the line breaks.
 *
 * <p>This is deliberately simple and assumes the text is already horizontal.
 * Deskewing belongs upstream, in the preprocessing stage.
 */
public final class LineSplitter {

    /** A pixel darker than this counts as ink. */
    private static final int DARKNESS_THRESHOLD = 128;

    /** Rows with fewer dark pixels than this fraction of the width are gaps. */
    private static final double INK_RATIO_THRESHOLD = 0.005;

    /** Vertical padding added around each detected line, in pixels. */
    private static final int PADDING = 8;

    /** Lines thinner than this are noise, not text. */
    private static final int MINIMUM_LINE_HEIGHT = 8;

    private LineSplitter() {
        throw new AssertionError("Utility class — not meant to be instantiated");
    }

    /**
     * @param image a grayscale or colour image containing horizontal text lines
     * @return one sub-image per detected line, top to bottom
     */
    public static List<BufferedImage> split(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        boolean[] rowHasInk = new boolean[height];
        int minimumInkPixels = (int) Math.max(1, width * INK_RATIO_THRESHOLD);

        for (int y = 0; y < height; y++) {
            int inkCount = 0;
            for (int x = 0; x < width; x++) {
                // Green channel approximates luminance closely enough here and
                // avoids a full colour-space conversion.
                int green = (image.getRGB(x, y) >> 8) & 0xFF;
                if (green < DARKNESS_THRESHOLD) {
                    inkCount++;
                }
            }
            rowHasInk[y] = inkCount >= minimumInkPixels;
        }

        List<BufferedImage> lines = new ArrayList<>();
        int lineStart = -1;

        for (int y = 0; y < height; y++) {
            if (rowHasInk[y] && lineStart < 0) {
                lineStart = y;
            } else if (!rowHasInk[y] && lineStart >= 0) {
                addLine(image, lines, lineStart, y);
                lineStart = -1;
            }
        }
        if (lineStart >= 0) {
            addLine(image, lines, lineStart, height);
        }

        return lines;
    }

    private static void addLine(BufferedImage source, List<BufferedImage> target,
                                int startY, int endY) {
        if (endY - startY < MINIMUM_LINE_HEIGHT) {
            return;
        }

        int top = Math.max(0, startY - PADDING);
        int bottom = Math.min(source.getHeight(), endY + PADDING);

        target.add(source.getSubimage(0, top, source.getWidth(), bottom - top));
    }
}