package com.etka.veridoc.ocr;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reads a line of fixed-width text one character cell at a time.
 *
 * <p>General OCR segments text by locating gaps between glyphs. That fails on
 * a machine readable zone, where a run of identical filler characters offers
 * no reliable gaps and the engine miscounts them. Because an MRZ line has a
 * known character count evenly spaced across the band, the boundaries can be
 * computed instead of detected — which makes miscounting impossible.
 */
public final class FixedWidthLineReader {

    /** Blank margin added around each extracted cell, in pixels. */
    private static final int CELL_PADDING = 12;

        /** Cells scaled below this height read poorly; upscale to reach it. */
    private static final int TARGET_CELL_HEIGHT = 64;

    /**
     * How many characters to read at once. Large enough to give the classifier
     * a baseline and relative-size reference, small enough that miscounting
     * within a group stays contained.
     */
        private static final int GROUP_SIZE = 3;

    /** Pixels darker than this count as ink. */
    private static final int DARK_PIXEL_THRESHOLD = 128;

    /**
     * The gap between the filler cluster and the character cluster must span at
     * least this fraction of the line's total density range to be treated as a
     * class boundary rather than ordinary variation between characters.
     */
    private static final double MINIMUM_CLUSTER_GAP = 0.15;

    /**
     * The filler/character boundary must fall within this fraction of the way
     * up the line's density range. Fillers are the lightest cells by a clear
     * margin, so a candidate boundary above this is a gap between two kinds of
     * character rather than a class split.
     */
    private static final double MAXIMUM_FILLER_DENSITY = 0.35;
    
    private final OcrEngine engine;

    public FixedWidthLineReader(OcrEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
    }

    /**
     * Reads a line image known to contain exactly {@code characterCount}
     * evenly spaced characters.
     *
     * @param lineImage      an image trimmed to the text
     * @param characterCount how many characters the line contains
     * @param hints          recognition constraints
     * @return the assembled line and per-character confidences
     */
    public OcrResult read(BufferedImage lineImage, int characterCount, OcrHints hints) {
        Objects.requireNonNull(lineImage, "lineImage must not be null");

        if (characterCount <= 0) {
            throw new IllegalArgumentException("characterCount must be positive");
        }

                // Reading one character at a time deprives the classifier of a baseline
        // and x-height reference, which is exactly what distinguishing 8 from 6
        // or 0 from Q depends on. Reading a small group restores that context
        // while keeping the character count fixed by construction.
        OcrHints groupHints = new OcrHints(
                hints.characterWhitelist(),
                OcrHints.PageSegmentation.SINGLE_WORD,
                hints.language());

        double cellWidth = (double) lineImage.getWidth() / characterCount;

        // Measure every cell first, then derive the filler boundary from this
        // line's own distribution. A fixed threshold only works for the exact
        // font, size and lighting it was calibrated against; the two clusters
        // stay clearly separated, but their absolute values move.
        // Fillers are identified by the vertical extent of their ink, not its
        // density. A chevron occupies a short band in the middle of the cell,
        // while every letter and digit spans most of the cap height. Density
        // alone cannot separate them: the digit '1' is a single thin stroke
        // with about as much ink as a chevron.
        double[] inkHeights = new double[characterCount];
        for (int cell = 0; cell < characterCount; cell++) {
            int from = (int) Math.round(cell * cellWidth);
            int to = Math.min((int) Math.round((cell + 1) * cellWidth), lineImage.getWidth());
            inkHeights[cell] = to > from
                    ? inkHeightFraction(
                            lineImage.getSubimage(from, 0, to - from, lineImage.getHeight()))
                    : 0.0;
        }
        double fillerThreshold = deriveFillerThreshold(inkHeights);

        StringBuilder line = new StringBuilder(characterCount);
        List<Float> confidences = new ArrayList<>(characterCount);

                int index = 0;
        while (index < characterCount) {
            // A filler cell is almost entirely white. Detecting that by ink
            // density is both more reliable and far faster than asking the
            // classifier, and it removes the long identical runs that general
            // OCR handles worst.
            int fillerStart = (int) Math.round(index * cellWidth);
            int fillerEnd = Math.min((int) Math.round((index + 1) * cellWidth),
                    lineImage.getWidth());

            if (inkHeights[index] < fillerThreshold) {
                line.append('<');
                confidences.add(100.0f);
                index++;
                continue;
            }

            int groupSize = Math.min(GROUP_SIZE, characterCount - index);

            // Do not let a group run past the next filler, or the group would
            // contain a character the classifier is not being asked to read.
            for (int lookahead = 1; lookahead < groupSize; lookahead++) {
                int peekStart = (int) Math.round((index + lookahead) * cellWidth);
                int peekEnd = Math.min((int) Math.round((index + lookahead + 1) * cellWidth),
                        lineImage.getWidth());
                if (inkHeights[index + lookahead] < fillerThreshold) {
                    groupSize = lookahead;
                    break;
                }
            }

            // Rounding both edges from the same double avoids accumulated
            // drift: the final group must still end at the image edge.
            int startX = (int) Math.round(index * cellWidth);
            int endX = (int) Math.round((index + groupSize) * cellWidth);
            endX = Math.min(endX, lineImage.getWidth());

            String text = "";
            float confidence = 0.0f;

            if (endX > startX) {
                BufferedImage group = lineImage.getSubimage(
                        startX, 0, endX - startX, lineImage.getHeight());

                OcrResult groupResult = engine.read(prepareCell(group), groupHints);
                text = groupResult.text().replaceAll("\\s", "");
                confidence = groupResult.meanConfidence();
            }
            if (text.length() == groupSize) {
                for (int offset = 0; offset < groupSize; offset++) {
                    line.append(text.charAt(offset));
                    confidences.add(confidence);
                }
            } else {
                // The group returned the wrong number of characters, so its
                // alignment cannot be trusted. Retry each cell alone: single
                // character reads are less accurate, but one uncertain
                // character is recoverable through the check digits whereas a
                // voided group of three is not.
                OcrHints singleHints = new OcrHints(
                        hints.characterWhitelist(),
                        OcrHints.PageSegmentation.SINGLE_CHAR,
                        hints.language());

                for (int offset = 0; offset < groupSize; offset++) {
                    int cellStart = (int) Math.round((index + offset) * cellWidth);
                    int cellEnd = Math.min(
                            (int) Math.round((index + offset + 1) * cellWidth),
                            lineImage.getWidth());

                    if (cellEnd <= cellStart) {
                        line.append('?');
                        confidences.add(0.0f);
                        continue;
                    }

                    OcrResult single = engine.read(
                            prepareCell(lineImage.getSubimage(
                                    cellStart, 0, cellEnd - cellStart, lineImage.getHeight())),
                            singleHints);

                    String character = single.text().replaceAll("\\s", "");
                    line.append(character.isEmpty() ? '?' : character.charAt(0));
                    confidences.add(character.isEmpty() ? 0.0f : single.meanConfidence());
                }
            }

            index += groupSize;
        }

        float mean = (float) confidences.stream()
                .mapToDouble(Float::doubleValue)
                .average()
                .orElse(0.0);

        return new OcrResult(line.toString(), mean, confidences);
    }

        /**
     * Decides whether a cell contains the filler character '&lt;'.
     *
     * <p>In OCR-B and most monospace fonts the chevron is a small mark low in
     * the cell, so a filler cell has markedly less ink than any letter or
     * digit. Comparing ink density against a threshold separates them without
     * involving the classifier at all.
     */
    /**
     * The vertical extent of a cell's ink, as a fraction of the cell height.
     *
     * <p>Returns 0 for a cell containing no ink at all.
     */
    private static double inkHeightFraction(BufferedImage cell) {
        int top = -1;
        int bottom = -1;

        for (int y = 0; y < cell.getHeight(); y++) {
            for (int x = 0; x < cell.getWidth(); x++) {
                if (((cell.getRGB(x, y) >> 8) & 0xFF) < DARK_PIXEL_THRESHOLD) {
                    if (top < 0) {
                        top = y;
                    }
                    bottom = y;
                    break;
                }
            }
        }

        return top < 0 ? 0.0 : (double) (bottom - top + 1) / cell.getHeight();
    }

    /**
     * Finds the ink-density boundary separating filler cells from characters.
     *
     * <p>Sorts the line's cell densities and looks for the widest gap between
     * consecutive values. Filler cells cluster tightly at a low density and
     * every letter or digit sits well above, so that gap is the natural
     * boundary. Requiring it to span a meaningful share of the observed range
     * prevents a line containing no fillers from being split arbitrarily.
     *
     * @return a threshold below which a cell is filler, or 0 if the line has none
     */
    private static double deriveFillerThreshold(double[] inkRatios) {
        double[] sorted = inkRatios.clone();
        java.util.Arrays.sort(sorted);

        double spread = sorted[sorted.length - 1] - sorted[0];
        if (spread <= 0) {
            return 0.0;
        }

        double widestGap = 0;
        int gapIndex = -1;

        // Fillers are always the least-inked cells, but their count varies
        // widely — 25 of 44 on a passport's name line, 6 on the data line. So
        // the search covers every gap and is constrained by density rather than
        // position: the boundary must fall in the lower part of the observed
        // range. Limiting by index instead would examine mostly real characters
        // on a filler-sparse line and split the character cluster in two.
        double densityLimit = sorted[0] + spread * MAXIMUM_FILLER_DENSITY;

        for (int index = 0; index < sorted.length - 1; index++) {
            if (sorted[index] > densityLimit) {
                break;
            }
            double gap = sorted[index + 1] - sorted[index];
            if (gap > widestGap) {
                widestGap = gap;
                gapIndex = index;
            }
        }

        if (gapIndex < 0 || widestGap < spread * MINIMUM_CLUSTER_GAP) {
            return 0.0;
        }

        double threshold = (sorted[gapIndex] + sorted[gapIndex + 1]) / 2.0;

        if (System.getProperty("veridoc.ink.debug") != null) {
            System.err.printf("[height] threshold %.4f (gap %.4f, spread %.4f)%n",
                    threshold, widestGap, spread);
        }
        return threshold;
    }

    /**
     * Pads and upscales a single cell.
     *
     * <p>Tesseract's classifier expects a character surrounded by whitespace at
     * a reasonable size. A bare 40x70 pixel crop touching all four edges is
     * unlike anything it was trained on, so the cell is centred on a white
     * background and enlarged.
     */
    private static BufferedImage prepareCell(BufferedImage cell) {
        double scale = Math.max(1.0, (double) TARGET_CELL_HEIGHT / cell.getHeight());

        int scaledWidth = (int) Math.round(cell.getWidth() * scale);
        int scaledHeight = (int) Math.round(cell.getHeight() * scale);

        int canvasWidth = scaledWidth + 2 * CELL_PADDING;
        int canvasHeight = scaledHeight + 2 * CELL_PADDING;

        BufferedImage canvas = new BufferedImage(
                canvasWidth, canvasHeight, BufferedImage.TYPE_BYTE_GRAY);

        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, canvasWidth, canvasHeight);
            graphics.drawImage(cell, CELL_PADDING, CELL_PADDING,
                    scaledWidth, scaledHeight, null);
        } finally {
            graphics.dispose();
        }

        return canvas;
    }
}