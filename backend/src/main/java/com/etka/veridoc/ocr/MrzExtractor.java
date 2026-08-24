package com.etka.veridoc.ocr;

import java.awt.Font;
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
     * Characters per line, tried in order. TD3 is tried first because
     * passports are the common case.
     */
    private static final int[] CANDIDATE_WIDTHS = {44, 30, 36};

    /**
     * Below this mean template-match confidence, the document font likely does
     * not match the hardcoded template font, and Tesseract is tried instead.
     */
    private static final float TEMPLATE_CONFIDENCE_THRESHOLD = 55.0f;

    /**
     * A line taller than this multiple of the shortest other detected line is
     * treated as two merged MRZ lines that never separated geometrically.
     */
    private static final double MERGED_HEIGHT_RATIO = 1.6;

    /**
     * When searching for the true boundary inside a merged span, candidates
     * are tried across this fraction of the span's height, centred on the
     * midpoint — avoiding the extreme edges, which can never be the real
     * boundary between two roughly equal lines.
     */
    private static final double SPLIT_SEARCH_FRACTION = 0.5;

    private static volatile Font cachedTemplateFont;

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

        Font templateFont = loadOcrBFont();

        for (int width : CANDIDATE_WIDTHS) {
            List<BufferedImage> resolvedLines = resolveMergedLines(lineImages, width, templateFont);

            var grid = GridInference.infer(resolvedLines, width);
            if (grid.isEmpty()) {
                continue;
            }

            int left = grid.get().left();
            int gridWidth = grid.get().width();
            var reader = new TemplateLineReader(new TemplateMatchingEngine(templateFont));

            List<String> templateLines = new ArrayList<>(resolvedLines.size());
            float totalConfidence = 0;
            int cellCount = 0;
            int lineIndex = 0;

            for (BufferedImage lineImage : resolvedLines) {
                int usableWidth = Math.min(gridWidth, lineImage.getWidth() - left);
                if (usableWidth <= 0) {
                    lineIndex++;
                    continue;
                }
                var result = reader.read(
                        lineImage.getSubimage(left, 0, usableWidth, lineImage.getHeight()),
                        width, lineIndex);
                templateLines.add(result.text());
                totalConfidence += result.meanConfidence();
                cellCount++;
                lineIndex++;
            }

            float meanConfidence = cellCount == 0 ? 0 : totalConfidence / cellCount;
            if (System.getProperty("veridoc.debug.band") != null) {
                System.err.println("[extract] width=" + width
                        + " meanConfidence=" + meanConfidence + " threshold=" + TEMPLATE_CONFIDENCE_THRESHOLD);
                for (String line : templateLines) {
                    System.err.println("[extract] template line: |" + line + "|");
                }
            }

            if (meanConfidence >= TEMPLATE_CONFIDENCE_THRESHOLD) {
                return templateLines;
            }
        }

        // Nothing matched confidently with template matching at any candidate
        // width: fall back to Tesseract over each trimmed, resolved line at
        // the first candidate width.
        List<BufferedImage> resolvedLines = resolveMergedLines(lineImages, CANDIDATE_WIDTHS[0], templateFont);
        List<String> lines = new ArrayList<>();
        try {
            var fallbackReader = new FixedWidthLineReader(engine);
            for (BufferedImage lineImage : resolvedLines) {
                ImageTrimmer.trim(lineImage).ifPresent(trimmed ->
                        lines.add(fallbackReader.read(trimmed, CANDIDATE_WIDTHS[0], OcrHints.forMrz()).text()));
            }
        } catch (Exception e) {
            // Tesseract unavailable or failed; return what template matching found.
            return lines.isEmpty() ? List.of() : lines;
        }
        return lines;
    }

    /**
     * Detects a span that is really two MRZ lines which never separated
     * geometrically, and resolves it by trying several candidate split
     * heights and keeping whichever produces the most confident template
     * match on both halves.
     *
     * <p>Real MRZ typesetting is sometimes tight enough that no row is
     * genuinely ink-free between the two lines (measured directly: 89-98% of
     * the surrounding lines' own ink density), so neither a hard threshold nor
     * a geometric midpoint reliably finds the true boundary. Reading is the
     * one thing that can — the correct split is the one where both halves
     * turn into recognisable MRZ text, and template matching already gives a
     * confidence score for exactly that.
     */
    private static List<BufferedImage> resolveMergedLines(
            List<BufferedImage> lineImages, int width, Font templateFont) {

        if (lineImages.size() >= 2) {
            return lineImages;
        }
        if (lineImages.size() != 1) {
            return lineImages;
        }

        BufferedImage only = lineImages.get(0);
        // A single detected line at roughly the height a genuine two-line MRZ
        // block would have, given typical line spacing, is the signal that
        // this is a merged pair rather than one real line. There is no other
        // line in the same image to compare against here, so the threshold is
        // absolute: an MRZ line is rarely taller than about 70px even at high
        // photo resolution, and a merged pair is close to double that.
        if (only.getHeight() < 50) {
            return lineImages;
        }
        System.err.println("[merge-split] single tall line (" + only.getHeight()
                + "px) being treated as a merged pair; searching for split point");

        int height = only.getHeight();
        int searchSpan = (int) (height * SPLIT_SEARCH_FRACTION);
        int mid = height / 2;
        int halfSpan = searchSpan / 2;
        int searchStart = Math.max(1, mid - halfSpan);
        int searchEnd = Math.min(height - 1, mid + halfSpan);
        System.err.println("[merge-split] height=" + height
                + " initial range=" + searchStart + "-" + searchEnd);

        var probeEngine = new TemplateMatchingEngine(templateFont);
        var probeReader = new TemplateLineReader(probeEngine);

        int bestSplit = mid;
        float bestScore = -1;

        for (int candidate = searchStart; candidate <= searchEnd; candidate++) {
            BufferedImage top = only.getSubimage(0, 0, only.getWidth(), candidate);
            BufferedImage bottom = only.getSubimage(0, candidate, only.getWidth(), height - candidate);

            float topConf = probeReader.read(top, width).meanConfidence();
            float bottomConf = probeReader.read(bottom, width).meanConfidence();
            float combined = (topConf + bottomConf) / 2;

            if (combined > bestScore) {
                bestScore = combined;
                bestSplit = candidate;
            }
        }

        // A winning score this low means nothing in the searched range read
        // convincingly as MRZ text — the search found the least-bad option,
        // not a genuine boundary. Widen the search to the full span rather
        // than trust it, since a narrow window centred on the geometric
        // midpoint can miss the true boundary on documents whose two lines
        // are not evenly sized within the crop.
        if (bestScore < 60.0f) {
            System.err.println("[merge-split] score " + bestScore
                    + " too low in narrow range, widening search to full height");
            for (int candidate = 10; candidate < height - 10; candidate++) {
                BufferedImage top = only.getSubimage(0, 0, only.getWidth(), candidate);
                BufferedImage bottom = only.getSubimage(0, candidate, only.getWidth(), height - candidate);

                float topConf = probeReader.read(top, width).meanConfidence();
                float bottomConf = probeReader.read(bottom, width).meanConfidence();
                float combined = (topConf + bottomConf) / 2;

                if (combined > bestScore) {
                    bestScore = combined;
                    bestSplit = candidate;
                }
            }
        }

        System.err.println("[merge-split] " + only.getWidth() + "x" + only.getHeight()
                + " best split at y=" + bestSplit
                + " (score=" + bestScore + ") out of range " + searchStart + "-" + searchEnd);

        BufferedImage topHalf = only.getSubimage(0, 0, only.getWidth(), bestSplit);
        BufferedImage bottomHalf = only.getSubimage(
                0, bestSplit, only.getWidth(), height - bestSplit);

        return List.of(topHalf, bottomHalf);
    }

    /**
     * Loads the OCR-B template font from disk, once, caching the result.
     * OCR-B is the font ICAO 9303 mandates for machine readable zones; falls
     * back to Consolas if the font file is not present, so this does not
     * break environments without the OCR-B font installed.
     */
    private static Font loadOcrBFont() {
        Font cached = cachedTemplateFont;
        if (cached != null) {
            return cached;
        }
        synchronized (MrzExtractor.class) {
            if (cachedTemplateFont == null) {
                java.io.File fontFile = new java.io.File("../samples/fonts/OCRB.ttf");
                boolean debug = System.getProperty("veridoc.debug.band") != null;
                if (debug) {
                    System.err.println("[font] looking for OCR-B at absolute path: "
                            + fontFile.getAbsolutePath() + " exists=" + fontFile.exists());
                }
                try {
                    cachedTemplateFont = Font
                            .createFont(Font.TRUETYPE_FONT, fontFile)
                            .deriveFont(Font.PLAIN, 64f);
                    if (debug) {
                        System.err.println("[font] LOADED OCR-B successfully: "
                                + cachedTemplateFont.getFontName());
                    }
                } catch (Exception e) {
                    if (debug) {
                        System.err.println("[font] FAILED to load OCR-B, falling back to Consolas: " + e);
                    }
                    cachedTemplateFont = new Font("Consolas", Font.PLAIN, 64);
                }
            }
        }
        return cachedTemplateFont;
    }
}