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

    /**
     * A span at least this many times the height of the shortest confidently
     * single-line span is treated as two merged lines. Real MRZ typesetting is
     * often too tight for any row to register a genuine ink-density dip
     * between the two lines — confirmed by measurement on real photographs,
     * where the "gap" row still carried 89-98% of the surrounding lines' ink.
     * A density-based valley search cannot reliably distinguish that from
     * noise, so a merged pair is instead split at its geometric centre, which
     * needs no detectable gap at all.
     */
    private static final double MERGED_PAIR_HEIGHT_RATIO = 1.6;

    /** A small vertical margin excluded from each half after a centre split, so neither half keeps the other's descenders/ascenders. */
    private static final int CENTRE_SPLIT_MARGIN = 2;

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

        // A span noticeably taller than the shortest plausible single line is
        // very likely two MRZ lines that never separated, rather than one
        // unusually tall line — MRZ lines within a document are uniform
        // height. Split it at its centre; see MERGED_PAIR_HEIGHT_RATIO for why
        // this does not attempt to locate an actual gap.
        List<int[]> spans = new ArrayList<>();
        for (int[] span : rawSpans) {
            int spanHeight = span[1] - span[0];
            if (spanHeight >= minimumHeight * MERGED_PAIR_HEIGHT_RATIO) {
                int mid = (span[0] + span[1]) / 2;
                spans.add(new int[]{span[0], mid - CENTRE_SPLIT_MARGIN});
                spans.add(new int[]{mid + CENTRE_SPLIT_MARGIN, span[1]});
                if (debug) {
                    System.err.printf(
                            "[split] span y=%d-%d (height=%d) exceeds %.1fx threshold, "
                            + "centre-split at %d%n",
                            span[0], span[1], spanHeight, MERGED_PAIR_HEIGHT_RATIO, mid);
                }
                continue;
            }
            spans.add(span);
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