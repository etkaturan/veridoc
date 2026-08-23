package com.etka.veridoc.mrz;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TD3 builder")
class Td3BuilderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 23);

    @Test
    @DisplayName("produces two lines of 44 characters")
    void producesCorrectShape() {
        List<String> lines = new Td3Builder().build();

        assertThat(lines).hasSize(2);
        assertThat(lines).allSatisfy(line -> assertThat(line).hasSize(44));
        assertThat(MrzFormat.detect(lines)).contains(MrzFormat.TD3);
    }

    @Test
    @DisplayName("every check digit it computes passes verification")
    void checkDigitsValidate() {
        List<String> lines = new Td3Builder()
                .name("ERIKSSON", "ANNA", "MARIA")
                .dateOfBirth(LocalDate.of(1974, 8, 12))
                .expiryDate(LocalDate.of(2032, 4, 15))
                .build();

        assertThat(new Td3Parser().verify(lines).isFullyValid()).isTrue();
    }

    @Test
    @DisplayName("round-trips through the parser")
    void roundTrips() {
        // Nine characters: the TD3 document number field is exactly that wide,
        // and anything longer is truncated per ICAO 9303.
        List<String> lines = new Td3Builder()
                .name("SCHMIDT", "LUKAS")
                .documentNumber("P12345678")
                .nationality("DEU")
                .issuingState("DEU")
                .dateOfBirth(LocalDate.of(2000, 3, 15))
                .sex('M')
                .expiryDate(LocalDate.of(2030, 1, 1))
                .build();

        MrzData data = new Td3Parser().parse(lines, TODAY);

        assertThat(data.surname()).isEqualTo("SCHMIDT");
        assertThat(data.givenNames()).containsExactly("LUKAS");
        assertThat(data.documentNumber()).isEqualTo("P12345678");
        assertThat(data.nationality()).isEqualTo("DEU");
        assertThat(data.dateOfBirth()).contains(LocalDate.of(2000, 3, 15));
        assertThat(data.expiryDate()).contains(LocalDate.of(2030, 1, 1));
        assertThat(data.isAtLeastAge(18, TODAY)).isTrue();
    }

    @Test
    @DisplayName("a holder born fifteen years ago is not eighteen")
    void detectsMinor() {
        List<String> lines = new Td3Builder()
                .dateOfBirth(TODAY.minusYears(15))
                .expiryDate(TODAY.plusYears(5))
                .build();

        assertThat(new Td3Parser().parse(lines, TODAY).isAtLeastAge(18, TODAY)).isFalse();
    }


        @Test
    @DisplayName("truncates an over-long document number to the field width")
    void truncatesDocumentNumber() {
        List<String> lines = new Td3Builder()
                .documentNumber("ABCDEFGHIJKLMNOP")
                .build();

        // Truncation is ICAO behaviour, not a defect — but the check digit must
        // be computed over the truncated value, or the document would be
        // internally inconsistent.
        assertThat(new Td3Parser().parse(lines, TODAY).documentNumber())
                .isEqualTo("ABCDEFGHI");
        assertThat(new Td3Parser().verify(lines).isFullyValid()).isTrue();
    }
}