package com.etka.veridoc.ocr;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Recognises MRZ characters by comparing each cell against pre-rendered
 * templates of the 37 characters the format permits.
 *
 * <p>General OCR is built for unknown text in unknown fonts, and pays for that
 * generality with a language model and layout analysis that actively harm a
 * fixed-width machine readable zone. Here the alphabet is closed, the font is
 * standardised, and the cell geometry is known — so recognition reduces to
 * picking the closest of 37 known pictures.
 *
 * <p>Matching is scale- and position-invariant: both template and cell are
 * reduced to their ink bounding box and normalised to a fixed grid before
 * comparison, so rendering size and cell padding do not affect the result.
 */
public final class TemplateMatchingEngine implements OcrEngine {

    /** Every character legal in an ICAO 9303 machine readable zone. */
    private static final String ALPHABET = OcrHints.MRZ_CHARACTERS;

    /** Resolution of the normalised comparison grid. */
    private static final int GRID = 16;

    /** Size at which templates are rendered before normalisation. */
    private static final int RENDER_SIZE = 128;

    private static final int DARK_THRESHOLD = 128;

    /**
     * How strongly aspect ratio counts relative to a single grid cell. Set high
     * enough that a shape mismatch in proportion outweighs incidental pattern
     * similarity, low enough that it cannot override the pattern entirely.
     */
    private static final double ASPECT_WEIGHT = 4.0;

    private final Map<Character, double[]> templates;

    /**
     * @param font the font the documents are printed in; OCR-B for real MRZs
     */
    public TemplateMatchingEngine(Font font) {
        this.templates = buildTemplates(font.deriveFont(Font.PLAIN, (float) RENDER_SIZE));
    }

    @Override
    public OcrResult read(BufferedImage image, OcrHints hints) {
        double[] cell = normalise(image);

        if (cell == null) {
            // No ink at all: this is a filler cell.
            return new OcrResult("<", 100.0f, java.util.List.of());
        }

        char best = '?';
        double bestScore = Double.MAX_VALUE;
        double runnerUp = Double.MAX_VALUE;

        for (Map.Entry<Character, double[]> template : templates.entrySet()) {
            double score = distance(cell, template.getValue());
            if (score < bestScore) {
                runnerUp = bestScore;
                bestScore = score;
                best = template.getKey();
            } else if (score < runnerUp) {
                runnerUp = score;
            }
        }

        // Confidence reflects how much better the winner is than the next best.
        // A cell matching two templates almost equally is genuinely ambiguous,
        // and saying so is more useful than an unqualified guess.
        float confidence = runnerUp == 0
                ? 100.0f
                : (float) Math.min(100.0, 100.0 * (1.0 - bestScore / runnerUp));

        return new OcrResult(String.valueOf(best), confidence, java.util.List.of());
    }

    private static Map<Character, double[]> buildTemplates(Font font) {
        Map<Character, double[]> built = new LinkedHashMap<>();

        for (char character : ALPHABET.toCharArray()) {
            BufferedImage rendered = render(character, font);
            double[] normalised = normalise(rendered);
            if (normalised != null) {
                built.put(character, normalised);
            }
        }
        return built;
    }

    private static BufferedImage render(char character, Font font) {
        int size = RENDER_SIZE * 2;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_GRAY);

        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, size, size);
            graphics.setColor(Color.BLACK);
            graphics.setFont(font);

            var metrics = graphics.getFontMetrics();
            graphics.drawString(String.valueOf(character),
                    (size - metrics.charWidth(character)) / 2,
                    size / 2 + metrics.getAscent() / 2);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /**
     * Reduces an image to a fixed grid of ink coverage values, cropped to its
     * ink bounding box first so that size and position do not matter.
     *
     * @return grid values in 0–1, or null if the image contains no ink
     */
    private static double[] normalise(BufferedImage image) {
        int left = image.getWidth();
        int right = -1;
        int top = image.getHeight();
        int bottom = -1;

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (((image.getRGB(x, y) >> 8) & 0xFF) < DARK_THRESHOLD) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
        }

        if (right < 0) {
            return null;
        }

        int boxWidth = right - left + 1;
        int boxHeight = bottom - top + 1;

        // One extra slot carries the ink box's aspect ratio. Normalising the
        // box to a square grid discards it, and it is the most discriminating
        // feature in this alphabet: a narrow '1' and a wide 'M' stretch to
        // near-identical patterns without it, as do P/F, U/L and 8/R.
        double[] grid = new double[GRID * GRID + 1];
        grid[GRID * GRID] = ASPECT_WEIGHT
                * Math.min(1.0, (double) boxWidth / boxHeight);

        for (int gridY = 0; gridY < GRID; gridY++) {
            for (int gridX = 0; gridX < GRID; gridX++) {
                int fromX = left + gridX * boxWidth / GRID;
                int toX = left + (gridX + 1) * boxWidth / GRID;
                int fromY = top + gridY * boxHeight / GRID;
                int toY = top + (gridY + 1) * boxHeight / GRID;

                int dark = 0;
                int counted = 0;
                for (int y = fromY; y < Math.max(toY, fromY + 1); y++) {
                    for (int x = fromX; x < Math.max(toX, fromX + 1); x++) {
                        if (y < image.getHeight() && x < image.getWidth()) {
                            counted++;
                            if (((image.getRGB(x, y) >> 8) & 0xFF) < DARK_THRESHOLD) {
                                dark++;
                            }
                        }
                    }
                }
                grid[gridY * GRID + gridX] = counted == 0 ? 0.0 : (double) dark / counted;
            }
        }
        return grid;
    }

    /** Sum of squared differences between two normalised grids. */
    private static double distance(double[] first, double[] second) {
        double total = 0;
        for (int index = 0; index < first.length; index++) {
            double difference = first[index] - second[index];
            total += difference * difference;
        }
        return total;
    }
}