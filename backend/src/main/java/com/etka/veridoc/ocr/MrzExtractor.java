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

    /**
     * Below this mean template-match confidence, the document font likely does
     * not match the hardcoded template font, and Tesseract is tried instead.
     */
    private static final float TEMPLATE_CONFIDENCE_THRESHOLD = 55.0f;

    private static volatile java.awt.Font cachedTemplateFont;

    private static java.awt.Font loadOcrBFont() {
        java.awt.Font cached = cachedTemplateFont;
        if (cached != null) {
            return cached;
        }
        synchronized (MrzExtractor.class) {
            if (cachedTemplateFont == null) {
                java.io.File fontFile = new java.io.File("../samples/fonts/OCRB.ttf");
                System.err.println("[font] looking for OCR-B at absolute path: "
                        + fontFile.getAbsolutePath() + " exists=" + fontFile.exists());
                try {
                    cachedTemplateFont = java.awt.Font
                            .createFont(java.awt.Font.TRUETYPE_FONT, fontFile)
                            .deriveFont(java.awt.Font.PLAIN, 64f);
                    System.err.println("[font] LOADED OCR-B successfully: "
                            + cachedTemplateFont.getFontName());
                } catch (Exception e) {
                    System.err.println("[font] FAILED to load OCR-B, falling back to Consolas: " + e);
                    cachedTemplateFont = new java.awt.Font("Consolas", java.awt.Font.PLAIN, 64);
                }
            }
        }
        return cachedTemplateFont;
    }

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
        System.err.println("[extract] LineSplitter found " + lineImages.size() + " line(s)");
        if (lineImages.isEmpty()) {
            return List.of();
        }

        int width = widthFor(lineImages.size());
        System.err.println("[extract] chosen width=" + width);

        // Derive the character grid from the spacing between glyphs rather than
        // dividing the ink span evenly. Ink bounds understate the grid — the
        // first and last cells extend past the glyphs they contain — and that
        // error compounds across every cell.
        var grid = GridInference.infer(lineImages, width);

        List<String> lines = new ArrayList<>(lineImages.size());

        if (grid.isPresent()) {
            int left = grid.get().left();
            int gridWidth = grid.get().width();
            // OCR-B is the font ICAO 9303 actually mandates for machine
            // readable zones; Consolas was a development-time stand-in for
            // generated test specimens and read real OCR-B documents poorly.
            // Falls back to Consolas if the font file is not present, so this
            // does not break environments without the OCR-B font installed.
            java.awt.Font templateFont = loadOcrBFont();
            var reader = new TemplateLineReader(new TemplateMatchingEngine(templateFont));

            List<String> templateLines = new ArrayList<>(lineImages.size());
            float totalConfidence = 0;
            int cellCount = 0;

            for (BufferedImage lineImage : lineImages) {
                int usableWidth = Math.min(gridWidth, lineImage.getWidth() - left);
                if (usableWidth <= 0) {
                    continue;
                }
                var result = reader.read(
                        lineImage.getSubimage(left, 0, usableWidth, lineImage.getHeight()),
                        width);
                templateLines.add(result.text());
                totalConfidence += result.meanConfidence();
                cellCount++;
            }

            // Template matching assumes the document font matches the templates
            // (Consolas, currently). A real OCR-B document scores poorly against
            // the wrong font's templates — every cell picks the least-bad match
            // rather than the right one — which shows up as low mean confidence
            // even though a grid was found. Falling back to Tesseract in that
            // case trades a font-specific technique for a general one rather
            // than returning a confident wrong answer.
            float meanConfidence = cellCount == 0 ? 0 : totalConfidence / cellCount;
            System.err.println("[extract] template meanConfidence=" + meanConfidence
                    + " threshold=" + TEMPLATE_CONFIDENCE_THRESHOLD);
            for (String line : templateLines) {
                System.err.println("[extract] template line: |" + line + "|");
            }
            if (meanConfidence >= TEMPLATE_CONFIDENCE_THRESHOLD) {
                System.err.println("[extract] USING template result");
                return templateLines;
            }
            System.err.println("[extract] confidence too low, falling back to Tesseract");
        } else {
            System.err.println("[extract] no grid inferred, falling back to Tesseract");
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
     * Infers the character width from the line count.
     *
     * <p>In principle three lines means TD1 (30 chars) and two means TD2/TD3
     * (36/44). In practice, LineSplitter's ink-row detection occasionally
     * reports a spurious extra line on real photographs — a faint border, a
     * scan artefact, noise below the true MRZ — turning a genuine two-line
     * TD3 into an apparent three-line split. Since TD1 parsing is not yet
     * supported by the registry anyway, defaulting to it on any 3-line split
     * only makes a real TD3 document unreadable without ever gaining anything
     * in return. Always try TD3 width first; grid inference and downstream
     * check-digit validation reject it if the document genuinely is TD1.
     */
    private static int widthFor(int lineCount) {
        return CANDIDATE_WIDTHS[0];
    }
}