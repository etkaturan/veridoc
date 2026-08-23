package com.etka.veridoc.ocr;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Turns an image of a machine readable zone into text lines.
 *
 * <p>Splits the band into lines, trims each to its ink, then reads it with
 * fixed-width cell segmentation. The result is raw — normalisation and
 * parsing happen downstream.
 */
public final class MrzExtractor {

    /**
     * Characters per line, tried in order. The correct width produces lines
     * whose check digits validate; the wrong one produces noise. TD3 is tried
     * first because passports are the common case.
     */
    private static final int[] CANDIDATE_WIDTHS = {44, 30, 36};

    private final OcrEngine engine;

    public MrzExtractor(OcrEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
    }

    /**
     * @param image an image cropped to the MRZ band
     * @return one raw text line per detected line of the zone
     */
    public List<String> extract(BufferedImage image) {
        Objects.requireNonNull(image, "image must not be null");

        List<BufferedImage> lineImages = LineSplitter.split(image);
        if (lineImages.isEmpty()) {
            return List.of();
        }

        int width = widthFor(lineImages.size());

        // Derive the character grid from the spacing between glyphs rather than
        // dividing the ink span evenly. Ink bounds understate the grid — the
        // first and last cells extend past the glyphs they contain — and that
        // error compounds across every cell.
        var grid = GridInference.infer(lineImages, width);

        List<String> lines = new ArrayList<>(lineImages.size());

        if (grid.isPresent()) {
            int left = grid.get().left();
            int gridWidth = grid.get().width();
            var reader = new TemplateLineReader(
                    new TemplateMatchingEngine(new java.awt.Font("Consolas", java.awt.Font.PLAIN, 64)));

            for (BufferedImage lineImage : lineImages) {
                int usableWidth = Math.min(gridWidth, lineImage.getWidth() - left);
                if (usableWidth <= 0) {
                    continue;
                }
                lines.add(reader.read(
                        lineImage.getSubimage(left, 0, usableWidth, lineImage.getHeight()),
                        width).text());
            }
            return lines;
        }

        // No usable grid: fall back to Tesseract over the trimmed band.
        FixedWidthLineReader reader = new FixedWidthLineReader(engine);
        for (BufferedImage lineImage : lineImages) {
            ImageTrimmer.trim(lineImage).ifPresent(trimmed ->
                    lines.add(reader.read(trimmed, width, OcrHints.forMrz()).text()));
        }
        return lines;
    }

    /**
     * Infers the character width from the line count, since the three ICAO
     * layouts are distinguishable that way: three lines means TD1, two lines
     * means TD2 or TD3.
     */
    private static int widthFor(int lineCount) {
        return lineCount == 3 ? 30 : CANDIDATE_WIDTHS[0];
    }
}