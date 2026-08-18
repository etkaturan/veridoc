
package com.etka.veridoc.mrz;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The three machine readable zone layouts defined by ICAO Doc 9303.
 *
 * <p>Each layout has a fixed number of lines and a fixed number of
 * characters per line. These dimensions alone are enough to identify
 * which format a given MRZ uses.
 */
public enum MrzFormat {

    /** Three lines of 30 characters. Used by ID cards, including the German Personalausweis. */
    TD1(3, 30, "ID card"),

    /** Two lines of 36 characters. Used by some older ID cards, visas and residence permits. */
    TD2(2, 36, "ID card / visa"),

    /** Two lines of 44 characters. Used by passports. */
    TD3(2, 44, "Passport");

    private final int lineCount;
    private final int lineLength;
    private final String description;

    MrzFormat(int lineCount, int lineLength, String description) {
        this.lineCount = lineCount;
        this.lineLength = lineLength;
        this.description = description;
    }

    public int lineCount() {
        return lineCount;
    }

    public int lineLength() {
        return lineLength;
    }

    public String description() {
        return description;
    }

    /** Total number of characters in a complete MRZ of this format. */
    public int totalLength() {
        return lineCount * lineLength;
    }

    /**
     * Identifies the format matching the given dimensions.
     *
     * @param lineCount  number of lines
     * @param lineLength number of characters per line
     * @return the matching format, or empty if no format has these dimensions
     */
    public static Optional<MrzFormat> forDimensions(int lineCount, int lineLength) {
        return Arrays.stream(values())
                .filter(format -> format.lineCount == lineCount
                        && format.lineLength == lineLength)
                .findFirst();
    }

    /**
     * Identifies the format of a list of already-normalised lines.
     *
     * <p>All lines must be the same length, or no format matches.
     *
     * @param lines normalised MRZ lines
     * @return the matching format, or empty if the shape is not a valid MRZ
     */
    public static Optional<MrzFormat> detect(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Optional.empty();
        }

        int firstLineLength = lines.getFirst().length();

        boolean allSameLength = lines.stream()
                .allMatch(line -> line.length() == firstLineLength);

        if (!allSameLength) {
            return Optional.empty();
        }

        return forDimensions(lines.size(), firstLineLength);
    }
}