package com.etka.veridoc.mrz;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MRZ normalisation")
class MrzNormalizerTest {

    @ParameterizedTest(name = "''{0}'' -> ''{1}''")
    @CsvSource({
            "'  ABC  ',     ABC",
            "'A B C',       ABC",
            "'abc',         ABC",
            "'AbC<<dEf',    ABC<<DEF",
            "'A\tB',        AB"
    })
    void cleansRepresentation(String input, String expected) {
        assertThat(MrzNormalizer.normalizeLine(input)).isEqualTo(expected);
    }

    /**
     * Canonical TD3 specimen from ICAO Doc 9303. Built by concatenation so
     * the filler run cannot be miscounted: 5 chars of prefix + 39 chars of
     * name field = 44.
     */
    private static final String TD3_LINE_1 =
            "P<UTOERIKSSON<<ANNA<MARIA" + "<".repeat(19);

    private static final String TD3_LINE_2 =
            "L898902C36UTO7408122F1204159ZE184226B<<<<<10";
    @Test
    @DisplayName("maps guillemet variants to the canonical filler")
    void mapsFillerVariants() {
        assertThat(MrzNormalizer.normalizeLine("A\u00ABB\u226AC")).isEqualTo("A<B<C");
    }

    @Test
    @DisplayName("does not correct ambiguous characters")
    void preservesAmbiguousCharacters() {
        // O and 0 must remain distinct — substituting would let a misread
        // document produce a valid checksum.
        assertThat(MrzNormalizer.normalizeLine("O0I1")).isEqualTo("O0I1");
    }

    @Test
    @DisplayName("splits on Windows line endings")
    void handlesCrLf() {
        assertThat(MrzNormalizer.normalize("ABC\r\nDEF")).containsExactly("ABC", "DEF");
    }

    @Test
    @DisplayName("splits on Unix line endings")
    void handlesLf() {
        assertThat(MrzNormalizer.normalize("ABC\nDEF")).containsExactly("ABC", "DEF");
    }

    @Test
    @DisplayName("discards blank lines")
    void discardsBlankLines() {
        assertThat(MrzNormalizer.normalize("ABC\n\n   \nDEF")).containsExactly("ABC", "DEF");
    }

    @Test
    @DisplayName("returns an empty list for input that is entirely blank")
    void handlesEntirelyBlankInput() {
        assertThat(MrzNormalizer.normalize("   \n\n  ")).isEmpty();
    }

    @Test
    @DisplayName("normalised OCR output feeds straight into format detection")
    void integratesWithFormatDetection() {
        // Dirty the canonical specimen the way a real OCR pass would:
        // lowercase output, stray leading whitespace, Windows line endings.
        String messyOcr = "  " + TD3_LINE_1.toLowerCase(Locale.ROOT) + "\r\n"
                        + " "  + TD3_LINE_2.toLowerCase(Locale.ROOT) + "\r\n";

        List<String> lines = MrzNormalizer.normalize(messyOcr);

        assertThat(lines).hasSize(2);
        assertThat(lines).allSatisfy(line -> assertThat(line).hasSize(44));
        assertThat(lines).containsExactly(TD3_LINE_1, TD3_LINE_2);
        assertThat(MrzFormat.detect(lines)).contains(MrzFormat.TD3);
    }
}