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
 * <p>Deliberately does not attempt to split a single tall span into multiple
 * lines: on real photographs the two MRZ lines are often set tightly enough
 * that no row is genuinely ink-free, and geometric guesses at where the true
 * boundary falls (a valley search, a naive midpoint) were both measured to be
 * unreliable across different documents. Recognising and separating a merged
 * span is instead the job of the caller, which has the OCR tools available to
 * judge a candidate split by whether it actually reads correctly — this class
 * stays a simple, dependency-free geometric detector.
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

    /**
     * Lines thinner than this fraction of the tallest detected line are noise,
     * not text. An absolute pixel threshold does not generalise: it was
     * calibrated against large generated specimens and let genuinely thin
     * artefacts through on real photographs, where the whole band — and each
     * real line within it — is a fraction of that height.
     */
    private static final double MINIMUM_HEIGHT_RATIO = 0.5;

    /** Absolute floor regardless of ratio, so a single real line is never rejected. */
    private static final int MINIMUM_LINE_HEIGHT_FLOOR = 6;

    private LineSplitter() {
        throw new AssertionError("Utility class — not meant to be instantiated");
    }

    /**
     * @param image a grayscale or colour image containing horizontal text lines
     * @return one sub-image per detected line, top to bottom. A span
     *         containing two lines that never separated is returned as one
     *         (taller) image; see {@link MrzExtractor} for how that case is
     *         detected and resolved.
     */
    public static List<BufferedImage> split(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean debug = System.getProperty("veridoc.debug.band") != null;

        if (debug) {
            System.err.println("[split] split() received image: " + width + "x" + height);
        }

        boolean[] rowHasInk = new boolean[height];
        int minimumInkPixels = (int) Math.max(1, width * INK_RATIO_THRESHOLD);

        for (int y = 0; y < height; y++) {
            int count = 0;
            for (int x = 0; x < width; x++) {
                // Green channel approximates luminance closely enough here and
                // avoids a full colour-space conversion.
                int green = (image.getRGB(x, y) >> 8) & 0xFF;
                if (green < DARKNESS_THRESHOLD) {
                    count++;
                }
            }
            rowHasInk[y] = count >= minimumInkPixels;
        }

        List<int[]> spans = new ArrayList<>();
        int lineStart = -1;
        for (int y = 0; y < height; y++) {
            if (rowHasInk[y] && lineStart < 0) {
                lineStart = y;
            } else if (!rowHasInk[y] && lineStart >= 0) {
                spans.add(new int[]{lineStart, y});
                lineStart = -1;
            }
        }
        if (lineStart >= 0) {
            spans.add(new int[]{lineStart, height});
        }

        int tallest = 0;
        for (int[] span : spans) {
            tallest = Math.max(tallest, span[1] - span[0]);
        }
        int minimumHeight = Math.max(
                MINIMUM_LINE_HEIGHT_FLOOR, (int) (tallest * MINIMUM_HEIGHT_RATIO));

        if (debug) {
            System.err.println("[split] raw spans found: " + spans.size());
            for (int[] span : spans) {
                System.err.println("[split]   height=" + (span[1] - span[0])
                        + " y=" + span[0] + "-" + span[1]);
            }
            System.err.println("[split] tallest=" + tallest + " minimumHeight=" + minimumHeight);
        }

        List<BufferedImage> lines = new ArrayList<>();
        for (int[] span : spans) {
            addLine(image, lines, span[0], span[1], minimumHeight);
        }

        return lines;
    }

    private static void addLine(BufferedImage source, List<BufferedImage> target,
                                int startY, int endY, int minimumHeight) {
        if (endY - startY < minimumHeight) {
            return;
        }

        int top = Math.max(0, startY - PADDING);
        int bottom = Math.min(source.getHeight(), endY + PADDING);

        target.add(source.getSubimage(0, top, source.getWidth(), bottom - top));
    }
}