package com.etka.veridoc.mrz;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Cleans raw OCR output into canonical MRZ text.
 *
 * <p>Normalisation corrects <em>representation</em> only — whitespace,
 * letter case, and alternative renderings of the filler character. It
 * deliberately does not attempt to correct ambiguous characters such as
 * O/0 or I/1. Substituting those would risk producing a checksum-valid
 * result from a document that was actually misread, defeating the entire
 * purpose of the check digits.
 */
public final class MrzNormalizer {

    /**
     * Alternative renderings of the filler character '<' that OCR engines
     * commonly produce, mapped to the canonical form.
     */
    private static final char[] FILLER_VARIANTS = {
            '\u00AB',  // « left-pointing double angle quotation mark
            '\u00BB',  // » right-pointing double angle quotation mark
            '\u226A',  // ≪ much less-than
            '\u226B',  // ≫ much greater-than
            '\u003C'   // < the canonical form itself
    };

    private MrzNormalizer() {
        throw new AssertionError("Utility class — not meant to be instantiated");
    }

    /**
     * Normalises a single line: removes all whitespace, uppercases, and
     * maps filler variants to '<'.
     *
     * @param line a raw line of OCR output
     * @return the normalised line, possibly empty
     */
    public static String normalizeLine(String line) {
        Objects.requireNonNull(line, "line must not be null");

        StringBuilder result = new StringBuilder(line.length());

        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);

            if (Character.isWhitespace(character)) {
                continue;
            }
            if (isFillerVariant(character)) {
                result.append(MrzCheckDigit.FILLER);
                continue;
            }
            result.append(Character.toUpperCase(character));
        }

        return result.toString();
    }

    /**
     * Normalises a complete MRZ block, splitting on line breaks and
     * discarding lines that are empty after cleaning.
     *
     * @param rawText raw OCR output, potentially multi-line
     * @return normalised, non-empty lines in their original order
     */
    public static List<String> normalize(String rawText) {
        Objects.requireNonNull(rawText, "rawText must not be null");

        return Arrays.stream(rawText.split("\\R"))
                .map(MrzNormalizer::normalizeLine)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    private static boolean isFillerVariant(char character) {
        for (char variant : FILLER_VARIANTS) {
            if (character == variant) {
                return true;
            }
        }
        return false;
    }
}