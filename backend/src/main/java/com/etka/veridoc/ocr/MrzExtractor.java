package com.etka.veridoc.ocr;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;

/**
 * Turns an image of a machine readable zone into text lines.
 *
 * <p>Hands the whole band directly to Tesseract as one block of text, using
 * its own layout analysis rather than a custom line-splitting or
 * character-grid pipeline. Measured directly against real documents:
 * Tesseract in block mode reads a cleanly located MRZ band correctly or
 * near-correctly in a single pass, with remaining errors limited to a
 * handful of digit/letter confusions that the ICAO check digits catch.
 * A from-scratch splitting and template-matching pipeline was built and
 * tuned against this same problem and was measurably less reliable and far
 * more complex than simply trusting Tesseract with the full band.
 */
public final class MrzExtractor {

    private final OcrEngine engine;

    public MrzExtractor(OcrEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
    }

    /**
     * @param image an image cropped to the MRZ band
     * @return one raw text line per line Tesseract found
     */
    public List<String> extract(BufferedImage image) {
        Objects.requireNonNull(image, "image must not be null");

        OcrResult result = engine.read(image, OcrHints.forMrz());
        return result.text().lines()
                .filter(line -> line.length() >= 25)
                .map(MrzExtractor::padToNearestValidWidth)
                .toList();
    }

    /**
     * Pads a line that came back one or two characters short of a valid
     * ICAO width with trailing filler.
     *
     * <p>Long, uniform runs of the filler character '&lt;' are the one part of
     * an MRZ line most likely to be undercounted by whole-line OCR: with no
     * internal structure to anchor against, a run of twenty identical thin
     * marks is exactly the input general-purpose text recognition handles
     * worst. A line falling one or two characters short of 30, 36 or 44 is
     * padded back up; content is never invented for a genuinely wrong-length
     * line far from any valid width, and the check digits still reject any
     * padding that was not actually warranted.
     */
    private static String padToNearestValidWidth(String line) {
        int[] validWidths = {30, 36, 44};
        for (int width : validWidths) {
            int deficit = width - line.length();
            if (deficit > 0 && deficit <= 2) {
                return line + "<".repeat(deficit);
            }
        }
        return line;
    }
}