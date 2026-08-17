package com.etka.veridoc.mrz;

import java.util.Objects;

/**
 * Implements the check digit algorithm defined in ICAO Doc 9303 Part 3,
 * used across all machine readable travel documents (passports, ID cards,
 * visas, residence permits).
 *
 * <p>The algorithm assigns a numeric value to each character, multiplies
 * those values by a repeating 7-3-1 weight pattern, and takes the sum
 * modulo 10.
 */
public final class MrzCheckDigit {

    /**
     * The filler character used throughout the MRZ to pad fields to a
     * fixed width. Contributes zero to the checksum.
     */
    public static final char FILLER = '<';

    /**
     * The repeating weight pattern. Applied cyclically, so character at
     * index 0 uses weight 7, index 1 uses 3, index 2 uses 1, index 3
     * uses 7 again, and so on.
     */
    private static final int[] WEIGHTS = {7, 3, 1};

    private MrzCheckDigit() {
        throw new AssertionError("Utility class — not meant to be instantiated");
    }

    /**
     * Returns the ICAO numeric value of a single MRZ character.
     *
     * @param character an uppercase letter, a digit, or the filler '<'
     * @return a value in the range 0–35
     * @throws IllegalArgumentException if the character is not valid in an MRZ
     */
    public static int characterValue(char character) {
        if (character == FILLER) {
            return 0;
        }
        if (character >= '0' && character <= '9') {
            return character - '0';
        }
        if (character >= 'A' && character <= 'Z') {
            return character - 'A' + 10;
        }
        throw new IllegalArgumentException(
                "Invalid MRZ character: '%c' (code point %d)"
                        .formatted(character, (int) character));
    }

    /**
     * Computes the check digit for a field.
     *
     * @param field the raw field content, excluding its check digit
     * @return the computed check digit, 0–9
     * @throws IllegalArgumentException if the field contains an invalid character
     */
    public static int compute(String field) {
        Objects.requireNonNull(field, "field must not be null");

        int sum = 0;
        for (int index = 0; index < field.length(); index++) {
            int value = characterValue(field.charAt(index));
            int weight = WEIGHTS[index % WEIGHTS.length];
            sum += value * weight;
        }
        return sum % 10;
    }

    /**
     * Verifies a field against the check digit printed on the document.
     *
     * <p>Per ICAO 9303, an unused optional data field may carry a filler
     * '<' in place of a check digit. That is accepted here only when the
     * field itself is entirely filler.
     *
     * @param field             the raw field content
     * @param printedCheckDigit the character printed immediately after the field
     * @return true if the field is internally consistent
     */
    public static boolean matches(String field, char printedCheckDigit) {
        Objects.requireNonNull(field, "field must not be null");

        if (printedCheckDigit == FILLER) {
            return isAllFiller(field);
        }
        if (printedCheckDigit < '0' || printedCheckDigit > '9') {
            return false;
        }
        return compute(field) == (printedCheckDigit - '0');
    }

    private static boolean isAllFiller(String field) {
        return field.chars().allMatch(codePoint -> codePoint == FILLER);
    }
}