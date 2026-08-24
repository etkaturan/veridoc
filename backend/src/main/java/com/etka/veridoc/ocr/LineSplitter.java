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
 * <p>Two lines set close together — common on real, tightly-typeset documents
 * — can leave no row that is genuinely ink-free: antialiasing, print bleed
 * and JPEG compression all keep a handful of dark pixels in the gap. Rather
 * than require zero ink, a second pass looks for a relative valley — a row
 * markedly lighter than the lines either side of it — inside any span tall
 * enough to plausibly be two merged lines.
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

    /** A span must be at least this many times the minimum line height before a valley split is attempted. */
    private static final double TALL_SPAN_RATIO = 1.6;

    /** Fraction of the span's height, at each edge, excluded from the valley search — ascenders and descenders skew the true edges. */
    private static final double VALLEY_EDGE_MARGIN_RATIO = 0.2;

    /** A row counts as a valley only if its ink is below this fraction of the span's average. */
    private static final double VALLEY_DEPTH_RATIO = 0.55;

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
        boolean debug = System.getProperty("veridoc.debug.band") != null;

        if (debug) {
            System.err.println("[split] split() received image: " + width + "x" + height);
        }

        int[] inkCount = new int[height];
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
            inkCount[y] = count;
            rowHasInk[y] = count >= minimumInkPixels;
        }

        List<int[]> rawSpans = new ArrayList<>();
        int lineStart = -1;
        for (int y = 0; y < height; y++) {
            if (rowHasInk[y] && lineStart < 0) {
                lineStart = y;
            } else if (!rowHasInk[y] && lineStart >= 0) {
                rawSpans.add(new int[]{lineStart, y});
                lineStart = -1;
            }
        }
        if (lineStart >= 0) {
            rawSpans.add(new int[]{lineStart, height});
        }

        int tallest = 0;
        for (int[] span : rawSpans) {
            tallest = Math.max(tallest, span[1] - span[0]);
        }
        int minimumHeight = Math.max(
                MINIMUM_LINE_HEIGHT_FLOOR, (int) (tallest * MINIMUM_HEIGHT_RATIO));

        if (debug) {
            System.err.println("[split] raw spans found: " + rawSpans.size());
            for (int[] span : rawSpans) {
                System.err.println("[split]   height=" + (span[1] - span[0])
                        + " y=" + span[0] + "-" + span[1]);
            }
            System.err.println("[split] tallest=" + tallest + " minimumHeight=" + minimumHeight);
        }

        // A span noticeably taller than the others may be two lines that
        // never separated because the gap between them was never fully
        // ink-free. Try a relative valley split before accepting it as one.
        List<int[]> spans = new ArrayList<>();
        for (int[] span : rawSpans) {
            int spanHeight = span[1] - span[0];
            if (spanHeight >= minimumHeight * TALL_SPAN_RATIO) {
                int[] gap = findValleySplit(inkCount, span, debug);
                if (gap != null) {
                    spans.add(new int[]{span[0], gap[0]});
                    spans.add(new int[]{gap[1], span[1]});
                    continue;
                }
            }
            spans.add(span);
        }

        List<BufferedImage> lines = new ArrayList<>();
        for (int[] span : spans) {
            addLine(image, lines, span[0], span[1], minimumHeight);
        }

        return lines;
    }

    /**
     * Looks for a relative ink-density valley within a span tall enough to
     * plausibly be two merged lines.
     *
     * <p>A fixed near-zero threshold cannot find a gap where every row still
     * carries some ink from antialiasing, print bleed or compression. This
     * instead finds the row with the least ink relative to the span's own
     * average: a genuine gap between two lines is markedly lower than either
     * line's own density, even when it is not ink-free.
     *
     * @return {@code [gapStart, gapEnd)} to exclude from both halves, or null
     *         if nothing convincing enough was found
     */
    private static int[] findValleySplit(int[] inkCount, int[] span, boolean debug) {
        int start = span[0];
        int end = span[1];
        int spanHeight = end - start;

        int edgeMargin = (int) (spanHeight * VALLEY_EDGE_MARGIN_RATIO);
        int searchStart = start + edgeMargin;
        int searchEnd = end - edgeMargin;

        if (searchEnd <= searchStart) {
            return null;
        }

        double sum = 0;
        for (int y = start; y < end; y++) {
            sum += inkCount[y];
        }
        double average = sum / spanHeight;

        int valleyRow = -1;
        int valleyCount = Integer.MAX_VALUE;
        for (int y = searchStart; y < searchEnd; y++) {
            if (inkCount[y] < valleyCount) {
                valleyCount = inkCount[y];
                valleyRow = y;
            }
        }

        if (valleyRow < 0 || valleyCount >= average * VALLEY_DEPTH_RATIO) {
            if (debug) {
                System.err.printf(
                        "[split] no valley in span y=%d-%d (best row=%d count=%d avg=%.1f)%n",
                        start, end, valleyRow, valleyCount, average);
            }
            return null;
        }

        // Widen the excluded gap past the single deepest row so a genuinely
        // blurred transition — not just one favourable pixel row — is fully
        // excluded from both halves.
        int gapStart = valleyRow;
        int gapEnd = valleyRow + 1;
        while (gapStart > start && inkCount[gapStart - 1] <= valleyCount * 1.3) {
            gapStart--;
        }
        while (gapEnd < end && inkCount[gapEnd] <= valleyCount * 1.3) {
            gapEnd++;
        }

        if (debug) {
            System.err.printf(
                    "[split] valley found in span y=%d-%d at row=%d (count=%d avg=%.1f), gap=%d-%d%n",
                    start, end, valleyRow, valleyCount, average, gapStart, gapEnd);
        }

        return new int[]{gapStart, gapEnd};
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