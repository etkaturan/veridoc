package com.etka.veridoc.mrz;

/**
 * The legal character set at each position of a TD3 machine readable zone.
 *
 * <p>ICAO 9303 fixes not just the width of each field but its alphabet: dates
 * and the document number are digits only (plus filler); the name field
 * allows letters and filler only; nationality and issuing state are
 * three-letter country codes. Knowing this at read time resolves visually
 * close characters — 0 vs O being the standard example — using a guarantee
 * the format makes, rather than relying on pixel shape alone.
 */
public final class Td3FieldAlphabet {

    private static final String DIGITS_AND_FILLER = "0123456789<";
    private static final String LETTERS_AND_FILLER =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ<";
    private static final String ALL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<";

    private Td3FieldAlphabet() {
        throw new AssertionError("Utility class — not meant to be instantiated");
    }

    /**
     * @param lineIndex 0 for the first MRZ line, 1 for the second
     * @param column    0-based character position within that line, 0-43
     * @return the characters legal at this exact position
     */
    public static String forPosition(int lineIndex, int column) {
        if (lineIndex == 0) {
            // Document code (0-1), issuing state (2-4), then the name field
            // through the end of the line — all letters and filler.
            return LETTERS_AND_FILLER;
        }

        // Line 2. Offsets match Td3Parser's own field layout exactly.
        if (column <= 8) return DIGITS_AND_FILLER;              // document number
        if (column == 9) return DIGITS_AND_FILLER;               // its check digit
        if (column >= 10 && column <= 12) return LETTERS_AND_FILLER; // nationality
        if (column >= 13 && column <= 18) return DIGITS_AND_FILLER;  // date of birth
        if (column == 19) return DIGITS_AND_FILLER;              // its check digit
        if (column == 20) return "MF<";                          // sex
        if (column >= 21 && column <= 26) return DIGITS_AND_FILLER;  // expiry date
        if (column == 27) return DIGITS_AND_FILLER;              // its check digit
        if (column >= 28 && column <= 41) return ALL;             // optional data: issuer-defined
        if (column == 42) return DIGITS_AND_FILLER;              // optional data check digit
        return DIGITS_AND_FILLER;                                 // 43: composite check digit
    }
}