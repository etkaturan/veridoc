package com.etka.veridoc.mrz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TD3 passport parser")
class Td3ParserTest {

    /** Canonical TD3 specimen from ICAO Doc 9303. */
    private static final String LINE_1 =
            "P<UTOERIKSSON<<ANNA<MARIA" + "<".repeat(19);
    private static final String LINE_2 =
            "L898902C36UTO7408122F1204159ZE184226B<<<<<10";

    private static final List<String> SPECIMEN = List.of(LINE_1, LINE_2);

    /** Fixed reference date so every assertion is deterministic forever. */
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 18);

    private Td3Parser parser;

    @BeforeEach
    void setUp() {
        parser = new Td3Parser();
    }

    @Test
    @DisplayName("declares TD3 as its supported format")
    void declaresSupportedFormat() {
        assertThat(parser.supportedFormat()).isEqualTo(MrzFormat.TD3);
    }

    @Nested
    @DisplayName("field extraction")
    class FieldExtraction {

        @Test
        @DisplayName("extracts every field from the ICAO specimen")
        void extractsAllFields() {
            MrzData data = parser.parse(SPECIMEN, TODAY);

            assertThat(data.documentCode()).isEqualTo("P");
            assertThat(data.issuingState()).isEqualTo("UTO");
            assertThat(data.surname()).isEqualTo("ERIKSSON");
            assertThat(data.givenNames()).containsExactly("ANNA", "MARIA");
            assertThat(data.documentNumber()).isEqualTo("L898902C3");
            assertThat(data.nationality()).isEqualTo("UTO");
            assertThat(data.sex()).isEqualTo('F');
            assertThat(data.optionalData()).isEqualTo("ZE184226B");
        }

        @Test
        @DisplayName("interprets the birth date as 12 August 1974")
        void interpretsBirthDate() {
            MrzData data = parser.parse(SPECIMEN, TODAY);
            assertThat(data.dateOfBirth()).contains(LocalDate.of(1974, 8, 12));
        }

        @Test
        @DisplayName("interprets the expiry date as 15 April 2012")
        void interpretsExpiryDate() {
            MrzData data = parser.parse(SPECIMEN, TODAY);
            assertThat(data.expiryDate()).contains(LocalDate.of(2012, 4, 15));
        }

        @Test
        @DisplayName("assembles the full name in reading order")
        void assemblesFullName() {
            assertThat(parser.parse(SPECIMEN, TODAY).fullName())
                    .isEqualTo("ANNA MARIA ERIKSSON");
        }
    }

    @Nested
    @DisplayName("check digits")
    class CheckDigits {

        @Test
        @DisplayName("the ICAO specimen passes every check")
        void specimenIsFullyValid() {
            MrzVerification verification = parser.verify(SPECIMEN);

            assertThat(verification.isFullyValid()).isTrue();
            assertThat(verification.isCompositeValid()).isTrue();
            assertThat(verification.failedFields()).isEmpty();
        }

        @Test
        @DisplayName("altering the birth date fails both its own digit and the composite")
        void detectsAlteredBirthDate() {
            // Change 740812 to 640812 — making the holder appear ten years older.
            String tampered = LINE_2.substring(0, 13) + "640812" + LINE_2.substring(19);

            MrzVerification verification = parser.verify(List.of(LINE_1, tampered));

            assertThat(verification.failedFields()).containsExactlyInAnyOrder(
                    MrzVerification.MrzField.DATE_OF_BIRTH,
                    MrzVerification.MrzField.COMPOSITE);
        }

        @Test
        @DisplayName("the composite still fails when a forger fixes the local digit")
        void compositeCatchesConsistentTampering() {
            // Alter the birth date AND recompute its own check digit, the way
            // someone who understood the standard partially would.
            String newBirth = "640812";
            char newLocalDigit = (char) ('0' + MrzCheckDigit.compute(newBirth));
            String tampered = LINE_2.substring(0, 13) + newBirth + newLocalDigit
                    + LINE_2.substring(20);

            MrzVerification verification = parser.verify(List.of(LINE_1, tampered));

            assertThat(verification.failedFields())
                    .containsExactly(MrzVerification.MrzField.COMPOSITE);
            assertThat(verification.isFullyValid()).isFalse();
        }
    }

    @Nested
    @DisplayName("name splitting")
    class NameSplitting {

        @ParameterizedTest(name = "{0} -> surname {1}")
        @CsvSource({
                "'SCHMIDT<<HANS',        SCHMIDT",
                "'O<BRIEN<<SEAN',        O BRIEN",
                "'VAN<DER<BERG<<PIET',   VAN DER BERG",
                "'MUELLER<<HANS<PETER',  MUELLER"
        })
        void extractsSurname(String nameContent, String expectedSurname) {
            String nameField = nameContent + "<".repeat(39 - nameContent.length());
            List<String> lines = List.of("P<DEU" + nameField, LINE_2);
            assertThat(parser.parse(lines, TODAY).surname()).isEqualTo(expectedSurname);
        }

        @Test
        @DisplayName("splits multiple given names")
        void splitsMultipleGivenNames() {
            List<String> lines = List.of(
                    "P<DEU" + "MUELLER<<HANS<PETER" + "<".repeat(20), LINE_2);
            assertThat(parser.parse(lines, TODAY).givenNames())
                    .containsExactly("HANS", "PETER");
        }

        @Test
        @DisplayName("treats a field with no separator as a surname only")
        void handlesMissingSeparator() {
            List<String> lines = List.of("P<DEU" + "SCHMIDT" + "<".repeat(32), LINE_2);
            MrzData data = parser.parse(lines, TODAY);

            assertThat(data.surname()).isEqualTo("SCHMIDT");
            assertThat(data.givenNames()).isEmpty();
        }
    }

    @Nested
    @DisplayName("derived checks")
    class DerivedChecks {

        @Test
        @DisplayName("reports the holder as over 18")
        void reportsAgeThreshold() {
            MrzData data = parser.parse(SPECIMEN, TODAY);
            assertThat(data.isAtLeastAge(18, TODAY)).isTrue();
            assertThat(data.isAtLeastAge(60, TODAY)).isFalse();
        }

        @Test
        @DisplayName("age increments on the birthday, not before")
        void ageIncrementsOnBirthday() {
            MrzData data = parser.parse(SPECIMEN, TODAY);

            assertThat(data.ageAt(LocalDate.of(2026, 8, 11))).contains(51);
            assertThat(data.ageAt(LocalDate.of(2026, 8, 12))).contains(52);
        }

        @Test
        @DisplayName("reports the specimen document as expired")
        void reportsExpiry() {
            MrzData data = parser.parse(SPECIMEN, TODAY);
            assertThat(data.isExpiredAt(TODAY)).isTrue();
            assertThat(data.isExpiredAt(LocalDate.of(2010, 1, 1))).isFalse();
        }
    }

    @Nested
    @DisplayName("structural validation")
    class StructuralValidation {

        @Test
        @DisplayName("rejects the wrong number of lines")
        void rejectsWrongLineCount() {
            assertThatThrownBy(() -> parser.parse(List.of(LINE_1), TODAY))
                    .isInstanceOf(MrzParseException.class)
                    .hasMessageContaining("requires 2 lines");
        }

        @Test
        @DisplayName("rejects a line of the wrong length")
        void rejectsWrongLineLength() {
            assertThatThrownBy(() -> parser.parse(List.of(LINE_1, LINE_2 + "X"), TODAY))
                    .isInstanceOf(MrzParseException.class)
                    .hasMessageContaining("line 2");
        }
    }
}