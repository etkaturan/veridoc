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

    /**
     * Cells with less ink than this fraction of their area are filler.
     *
     * <p>Measured on rendered specimens: fillers cluster at 0.147–0.150,
     * letters and digits span 0.177–0.403. This threshold sits in the gap.
     * The separation is font- and size-dependent, so re-measure rather than
     * guess when moving to real document scans — run with
     * {@code -Dveridoc.ink.debug=true} to print the distribution.
     */
    private static final double FILLER_INK_RATIO = 0.16;
    
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

            if (fillerEnd > fillerStart
                    && isFiller(lineImage.getSubimage(
                            fillerStart, 0, fillerEnd - fillerStart, lineImage.getHeight()))) {
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
                if (peekEnd > peekStart
                        && isFiller(lineImage.getSubimage(
                                peekStart, 0, peekEnd - peekStart, lineImage.getHeight()))) {
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

            // The group must yield exactly groupSize characters. If it does not,
            // the read is untrustworthy and is marked unknown rather than
            // shifting every subsequent character.
            for (int offset = 0; offset < groupSize; offset++) {
                line.append(offset < text.length() && text.length() == groupSize
                        ? text.charAt(offset)
                        : '?');
                confidence = text.length() == groupSize ? confidence : 0.0f;
                confidences.add(confidence);
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
    private static boolean isFiller(BufferedImage cell) {
        int darkPixels = 0;
        int total = cell.getWidth() * cell.getHeight();

        for (int y = 0; y < cell.getHeight(); y++) {
            for (int x = 0; x < cell.getWidth(); x++) {
                if (((cell.getRGB(x, y) >> 8) & 0xFF) < 128) {
                    darkPixels++;
                }
            }
        }

                double ratio = (double) darkPixels / total;

        if (System.getProperty("veridoc.ink.debug") != null) {
            System.err.printf("[ink] %.4f%n", ratio);
        }

        return ratio < FILLER_INK_RATIO;
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