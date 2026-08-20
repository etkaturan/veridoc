package com.etka.veridoc.ocr;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Determines the character cell geometry of a fixed-width text band.
 *
 * <p>Inferring the grid from the ink bounding box is systematically wrong: the
 * first and last cells extend beyond the ink of the glyphs they contain, so the
 * measured span understates the grid and the error compounds across every cell.
 * Measuring instead from the gaps <em>between</em> glyphs avoids that bias,
 * because a gap midpoint is a true cell boundary regardless of how wide the
 * adjacent characters happen to be.
 *
 * @param left  x coordinate of the first cell's left edge
 * @param pitch width of one character cell, in pixels
 * @param count number of cells
 */
public record GridInference(int left, double pitch, int count) {

    private static final int DARK_THRESHOLD = 128;

    /**
     * A blank run must be at least this fraction of the image's widest blank
     * run to count as an inter-character gap. Gaps within a glyph are far
     * narrower than the spacing between glyphs.
     */
    private static final double MINIMUM_GAP_FRACTION = 0.10;

    /** Total width spanned by the grid. */
    public int width() {
        return (int) Math.round(pitch * count);
    }

    /** Left edge of the cell at the given index. */
    public int cellStart(int index) {
        return left + (int) Math.round(index * pitch);
    }

    /**
     * Infers the grid from one or more lines known to share it.
     *
     * @param lineImages images of the text lines, all on the same grid
     * @param count      characters per line
     * @return the inferred grid, or empty if too few gaps were found
     */
    public static Optional<GridInference> infer(List<BufferedImage> lineImages, int count) {
        List<Double> boundaries = new ArrayList<>();

        for (BufferedImage lineImage : lineImages) {
            boundaries.addAll(gapMidpoints(lineImage));
        }
        if (boundaries.size() < 3) {
            return Optional.empty();
        }
        Collections.sort(boundaries);

        double pitch = medianPitch(boundaries);
        if (pitch <= 0) {
            return Optional.empty();
        }

        int left = inferOrigin(boundaries, pitch);
        return Optional.of(new GridInference(left, pitch, count));
    }

    /**
     * Finds the midpoint of every run of blank columns, which is where a cell
     * boundary falls. Runs at the very start and end are ignored: those are the
     * margins outside the grid, not gaps between characters.
     */
    private static List<Double> gapMidpoints(BufferedImage image) {
        boolean[] hasInk = new boolean[image.getWidth()];
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (((image.getRGB(x, y) >> 8) & 0xFF) < DARK_THRESHOLD) {
                    hasInk[x] = true;
                    break;
                }
            }
        }

        // Find the widest blank run first. Inter-character gaps are comparable
        // to it; gaps inside a glyph are a small fraction of it. Scaling the
        // minimum width from what this image actually contains keeps the rule
        // independent of font size and resolution.
        int widest = 0;
        int run = 0;
        for (boolean ink : hasInk) {
            run = ink ? 0 : run + 1;
            widest = Math.max(widest, run);
        }
        int minimumGap = Math.max(2, (int) (widest * MINIMUM_GAP_FRACTION));

        List<Double> midpoints = new ArrayList<>();
        int gapStart = -1;
        boolean seenInk = false;

        for (int x = 0; x < hasInk.length; x++) {
            if (hasInk[x]) {
                if (gapStart >= 0 && seenInk && x - gapStart >= minimumGap) {
                    midpoints.add((gapStart + x - 1) / 2.0);
                }
                gapStart = -1;
                seenInk = true;
            } else if (gapStart < 0) {
                gapStart = x;
            }
        }
        return midpoints;
    }

    /**
     * Takes the median spacing between consecutive boundaries.
     *
     * <p>Boundaries either side of a filler run are several cells apart, so the
     * raw gaps include multiples of the pitch. Dividing each gap by its nearest
     * whole number of cells folds those multiples back onto a single pitch
     * estimate — and the multiples are actually the most precise measurements
     * available, since any per-cell error is divided by the span.
     */
    private static double medianPitch(List<Double> boundaries) {
        List<Double> smallest = new ArrayList<>();
        for (int index = 1; index < boundaries.size(); index++) {
            double gap = boundaries.get(index) - boundaries.get(index - 1);
            if (gap > 1) {
                smallest.add(gap);
            }
        }
        if (smallest.isEmpty()) {
            return 0;
        }

        Collections.sort(smallest);

        // Many characters contain internal vertical whitespace — between the
        // bowls of an '8', inside the notches of an 'E' — so a single glyph can
        // contribute several apparent boundaries. Those intra-glyph gaps are
        // much smaller than the pitch and dominate by count, so the base
        // estimate is taken from the upper part of the distribution where real
        // inter-character spacings live.
        double base = smallest.get((int) (smallest.size() * 0.75));

        // Refine against the widest span rather than averaging single-cell
        // estimates. Every boundary carries about a pixel of measurement noise
        // from where a glyph's ink happens to begin; a span covering n cells
        // divides that noise by n, so the longest span is by far the most
        // precise measurement available. Single-cell gaps are only accurate
        // enough to identify how many cells the long spans cover.
        // Take the widest span available, then determine how many cells it
        // covers by testing candidates rather than dividing by the base
        // estimate. Single-cell gaps vary by several pixels because they
        // measure ink edges rather than cell edges, and over a long span that
        // error is enough to round to the wrong integer — which then skews the
        // pitch by exactly one cell's worth.
        double widestSpan = boundaries.get(boundaries.size() - 1) - boundaries.get(0);
        if (widestSpan <= 0) {
            return base;
        }

        long widestCells = 0;
        double bestScore = Double.MAX_VALUE;

        long lowest = Math.max(1, Math.round(widestSpan / (base * 1.15)));
        long highest = Math.round(widestSpan / (base * 0.85));

        for (long candidate = lowest; candidate <= highest; candidate++) {
            double pitch = widestSpan / candidate;

            // Score by how close every boundary sits to a multiple of this
            // pitch. The correct pitch puts all of them near a whole cell;
            // a wrong one leaves them scattered.
            double score = 0;
            for (double boundary : boundaries) {
                double cells = (boundary - boundaries.get(0)) / pitch;
                double error = Math.abs(cells - Math.round(cells));
                score += error * error;
            }

            if (score < bestScore) {
                bestScore = score;
                widestCells = candidate;
            }
        }

        if (System.getProperty("veridoc.grid.debug") != null) {
            System.err.printf("[grid] base=%.3f gaps=%d widestSpan=%.1f cells=%d pitch=%.3f%n",
                    base, smallest.size(), widestSpan, widestCells,
                    widestCells > 0 ? widestSpan / widestCells : base);
            System.err.print("[grid] gaps:");
            smallest.forEach(gap -> System.err.printf(" %.1f", gap));
            System.err.println();
        }

        return widestCells > 0 ? widestSpan / widestCells : base;
    }

    /**
     * Extrapolates the first cell's left edge from the observed boundaries.
     *
     * <p>Each boundary sits a whole number of cells from the origin, so the
     * fractional part of {@code boundary / pitch} is constant across all of
     * them. Averaging that offset over every boundary averages out per-gap
     * noise, then the earliest boundary is walked back to cell zero.
     */
    /**
     * Locates the first cell's left edge.
     *
     * <p>Every boundary sits a whole number of cells from the origin, so each
     * one's residue modulo the pitch is the same value — the position of a cell
     * edge within the pitch. Averaging those residues cancels per-boundary
     * measurement noise. Because residues wrap around zero, they are averaged
     * as unit vectors on a circle rather than as plain numbers: a set of
     * residues at 1 and at pitch-1 are one pixel apart, not pitch-2 apart, and
     * a plain average would place their mean at the opposite side of the cell.
     */
    private static int inferOrigin(List<Double> boundaries, double pitch) {
        double sumSin = 0;
        double sumCos = 0;

        for (double boundary : boundaries) {
            double angle = 2 * Math.PI * (boundary % pitch) / pitch;
            sumSin += Math.sin(angle);
            sumCos += Math.cos(angle);
        }

        double meanAngle = Math.atan2(sumSin, sumCos);
        if (meanAngle < 0) {
            meanAngle += 2 * Math.PI;
        }
        double edgeWithinCell = meanAngle * pitch / (2 * Math.PI);

        // A boundary marks the right edge of a cell, so the origin is one pitch
        // to the left of the first cell edge at or after zero.
        double origin = edgeWithinCell - pitch;
        while (origin < 0) {
            origin += pitch;
        }
        return (int) Math.round(origin);
    }
}