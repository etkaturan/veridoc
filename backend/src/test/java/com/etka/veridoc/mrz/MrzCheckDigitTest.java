package com.etka.veridoc.mrz;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ICAO 9303 check digit")
class MrzCheckDigitTest {

    @Nested
    @DisplayName("character values")
    class CharacterValues {

        @ParameterizedTest(name = "''{0}'' -> {1}")
        @CsvSource({
                "0, 0",
                "5, 5",
                "9, 9",
                "A, 10",
                "C, 12",
                "L, 21",
                "Z, 35",
                "<, 0"
        })
        void mapsCharactersToIcaoValues(char character, int expected) {
            assertThat(MrzCheckDigit.characterValue(character)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "rejects ''{0}''")
        @ValueSource(chars = {'a', 'z', ' ', '-', '@', '/'})
        void rejectsInvalidCharacters(char character) {
            assertThatThrownBy(() -> MrzCheckDigit.characterValue(character))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid MRZ character");
        }
    }

    @Nested
    @DisplayName("compute")
    class Compute {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                // Canonical specimen values from ICAO Doc 9303.
                "L898902C<, 3",   // document number
                "740812,    2",   // date of birth
                "120415,    9",   // expiry date
                "123456789, 7"    // digits only, spans the weight cycle three times
        })
        void computesKnownCheckDigits(String field, int expected) {
            assertThat(MrzCheckDigit.compute(field)).isEqualTo(expected);
        }

        @Test
        @DisplayName("an all-filler field sums to zero")
        void allFillerIsZero() {
            assertThat(MrzCheckDigit.compute("<<<<<<<<<<")).isZero();
        }

        @Test
        @DisplayName("an empty field sums to zero")
        void emptyFieldIsZero() {
            assertThat(MrzCheckDigit.compute("")).isZero();
        }

        @Test
        @DisplayName("changing one character changes the result")
        void isSensitiveToSingleCharacterEdits() {
            assertThat(MrzCheckDigit.compute("740812"))
                    .isNotEqualTo(MrzCheckDigit.compute("740813"));
        }

        @Test
        @DisplayName("rejects null")
        void rejectsNull() {
            assertThatThrownBy(() -> MrzCheckDigit.compute(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("matches")
    class Matches {

        @Test
        @DisplayName("accepts a correct printed check digit")
        void acceptsCorrectDigit() {
            assertThat(MrzCheckDigit.matches("740812", '2')).isTrue();
        }

        @Test
        @DisplayName("rejects an incorrect printed check digit")
        void rejectsIncorrectDigit() {
            assertThat(MrzCheckDigit.matches("740812", '5')).isFalse();
        }

        @Test
        @DisplayName("accepts filler as the check digit of an unused field")
        void acceptsFillerForUnusedField() {
            assertThat(MrzCheckDigit.matches("<<<<<<<<<<<<<<", '<')).isTrue();
        }

        @Test
        @DisplayName("rejects filler as the check digit of a populated field")
        void rejectsFillerForPopulatedField() {
            assertThat(MrzCheckDigit.matches("740812", '<')).isFalse();
        }

        @Test
        @DisplayName("rejects a non-digit check digit")
        void rejectsLetterAsCheckDigit() {
            assertThat(MrzCheckDigit.matches("740812", 'A')).isFalse();
        }
    }
}