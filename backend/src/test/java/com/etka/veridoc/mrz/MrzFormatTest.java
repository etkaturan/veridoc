package com.etka.veridoc.mrz;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MRZ format detection")
class MrzFormatTest {

    @ParameterizedTest(name = "{0} is {1} lines of {2}")
    @CsvSource({
            "TD1, 3, 30",
            "TD2, 2, 36",
            "TD3, 2, 44"
    })
    void hasCorrectDimensions(MrzFormat format, int lines, int length) {
        assertThat(format.lineCount()).isEqualTo(lines);
        assertThat(format.lineLength()).isEqualTo(length);
    }

    @Test
    @DisplayName("total length is lines times line length")
    void computesTotalLength() {
        assertThat(MrzFormat.TD1.totalLength()).isEqualTo(90);
        assertThat(MrzFormat.TD2.totalLength()).isEqualTo(72);
        assertThat(MrzFormat.TD3.totalLength()).isEqualTo(88);
    }

    @Test
    @DisplayName("detects TD3 from two 44-character lines")
    void detectsTd3() {
        List<String> lines = List.of("A".repeat(44), "B".repeat(44));
        assertThat(MrzFormat.detect(lines)).contains(MrzFormat.TD3);
    }

    @Test
    @DisplayName("detects TD1 from three 30-character lines")
    void detectsTd1() {
        List<String> lines = List.of("A".repeat(30), "B".repeat(30), "C".repeat(30));
        assertThat(MrzFormat.detect(lines)).contains(MrzFormat.TD1);
    }

    @Test
    @DisplayName("detects TD2 from two 36-character lines")
    void detectsTd2() {
        List<String> lines = List.of("A".repeat(36), "B".repeat(36));
        assertThat(MrzFormat.detect(lines)).contains(MrzFormat.TD2);
    }

    @Test
    @DisplayName("rejects lines of differing length")
    void rejectsRaggedLines() {
        List<String> lines = List.of("A".repeat(44), "B".repeat(43));
        assertThat(MrzFormat.detect(lines)).isEmpty();
    }

    @Test
    @DisplayName("rejects a line length matching no format")
    void rejectsUnknownLineLength() {
        List<String> lines = List.of("A".repeat(40), "B".repeat(40));
        assertThat(MrzFormat.detect(lines)).isEmpty();
    }

    @Test
    @DisplayName("rejects a line count matching no format")
    void rejectsUnknownLineCount() {
        List<String> lines = List.of("A".repeat(44));
        assertThat(MrzFormat.detect(lines)).isEmpty();
    }

    @Test
    @DisplayName("rejects empty and null input")
    void rejectsEmptyInput() {
        assertThat(MrzFormat.detect(List.of())).isEmpty();
        assertThat(MrzFormat.detect(null)).isEmpty();
    }
}