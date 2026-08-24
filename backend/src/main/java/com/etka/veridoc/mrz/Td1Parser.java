package com.etka.veridoc.mrz;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parses the TD1 layout used by ID cards: three lines of 30 characters.
 *
 * <p>Field positions are defined by ICAO Doc 9303 Part 5. Unlike TD3, TD1 has
 * no separate check digit for its optional data field — only document
 * number, date of birth, expiry date, and the composite are checked.
 */
public final class Td1Parser implements MrzParser {

    // --- Line 1 offsets ---
    private static final int DOCUMENT_CODE_START = 0;
    private static final int DOCUMENT_CODE_END = 2;
    private static final int ISSUING_STATE_START = 2;
    private static final int ISSUING_STATE_END = 5;
    private static final int DOCUMENT_NUMBER_START = 5;
    private static final int DOCUMENT_NUMBER_END = 14;
    private static final int DOCUMENT_NUMBER_CD = 14;
    private static final int OPTIONAL_DATA_1_START = 15;
    private static final int OPTIONAL_DATA_1_END = 30;

    // --- Line 2 offsets ---
    private static final int BIRTH_DATE_START = 0;
    private static final int BIRTH_DATE_END = 6;
    private static final int BIRTH_DATE_CD = 6;
    private static final int SEX = 7;
    private static final int EXPIRY_DATE_START = 8;
    private static final int EXPIRY_DATE_END = 14;
    private static final int EXPIRY_DATE_CD = 14;
    private static final int NATIONALITY_START = 15;
    private static final int NATIONALITY_END = 18;
    private static final int OPTIONAL_DATA_2_START = 18;
    private static final int OPTIONAL_DATA_2_END = 29;
    private static final int COMPOSITE_CD = 29;

    /** Separates surname from given names within the line-3 name field. */
    private static final String NAME_SEPARATOR = "<<";

    @Override
    public MrzFormat supportedFormat() {
        return MrzFormat.TD1;
    }

    @Override
    public MrzData parse(List<String> lines, LocalDate today) {
        requireValidShape(lines);
        Objects.requireNonNull(today, "today must not be null");

        String line1 = lines.get(0);
        String line2 = lines.get(1);
        String line3 = lines.get(2);

        ParsedName name = splitName(line3);

        String birthDigits = line2.substring(BIRTH_DATE_START, BIRTH_DATE_END);
        String expiryDigits = line2.substring(EXPIRY_DATE_START, EXPIRY_DATE_END);

        String optionalData = stripFiller(
                line1.substring(OPTIONAL_DATA_1_START, OPTIONAL_DATA_1_END)
                        + line2.substring(OPTIONAL_DATA_2_START, OPTIONAL_DATA_2_END));

        return new MrzData(
                MrzFormat.TD1,
                stripFiller(line1.substring(DOCUMENT_CODE_START, DOCUMENT_CODE_END)),
                stripFiller(line1.substring(ISSUING_STATE_START, ISSUING_STATE_END)),
                name.surname(),
                name.givenNames(),
                stripFiller(line1.substring(DOCUMENT_NUMBER_START, DOCUMENT_NUMBER_END)),
                stripFiller(line2.substring(NATIONALITY_START, NATIONALITY_END)),
                MrzDate.parseBirthDate(birthDigits, today),
                line2.charAt(SEX),
                MrzDate.parseExpiryDate(expiryDigits, today),
                optionalData
        );
    }

    @Override
    public MrzVerification verify(List<String> lines) {
        requireValidShape(lines);

        String line1 = lines.get(0);
        String line2 = lines.get(1);

        Map<MrzVerification.MrzField, Boolean> results =
                new EnumMap<>(MrzVerification.MrzField.class);

        results.put(MrzVerification.MrzField.DOCUMENT_NUMBER,
                MrzCheckDigit.matches(
                        line1.substring(DOCUMENT_NUMBER_START, DOCUMENT_NUMBER_END),
                        line1.charAt(DOCUMENT_NUMBER_CD)));

        results.put(MrzVerification.MrzField.DATE_OF_BIRTH,
                MrzCheckDigit.matches(
                        line2.substring(BIRTH_DATE_START, BIRTH_DATE_END),
                        line2.charAt(BIRTH_DATE_CD)));

        results.put(MrzVerification.MrzField.EXPIRY_DATE,
                MrzCheckDigit.matches(
                        line2.substring(EXPIRY_DATE_START, EXPIRY_DATE_END),
                        line2.charAt(EXPIRY_DATE_CD)));

        // TD1 has no separate optional-data check digit — omitted here rather
        // than reported as failed, since it was never present to check.

        results.put(MrzVerification.MrzField.COMPOSITE,
                MrzCheckDigit.matches(compositeInput(line1, line2), line2.charAt(COMPOSITE_CD)));

        return new MrzVerification(results);
    }

    /**
     * Builds the input to the composite check digit: document number and its
     * check digit and optional data from line 1, then birth date through
     * expiry date (each with their own check digits) and the second optional
     * data block from line 2. Nationality and sex are excluded, matching the
     * standard's TD3 composite rule.
     */
    private static String compositeInput(String line1, String line2) {
        return line1.substring(DOCUMENT_NUMBER_START, DOCUMENT_NUMBER_END + 1)
                + line1.substring(OPTIONAL_DATA_1_START, OPTIONAL_DATA_1_END)
                + line2.substring(BIRTH_DATE_START, BIRTH_DATE_CD + 1)
                + line2.substring(EXPIRY_DATE_START, EXPIRY_DATE_CD + 1)
                + line2.substring(OPTIONAL_DATA_2_START, OPTIONAL_DATA_2_END);
    }

    private static ParsedName splitName(String nameField) {
        String trimmed = stripTrailingFiller(nameField);
        int separator = trimmed.indexOf(NAME_SEPARATOR);

        if (separator < 0) {
            return new ParsedName(joinComponents(trimmed), List.of());
        }

        String surname = joinComponents(trimmed.substring(0, separator));
        String givenPart = trimmed.substring(separator + NAME_SEPARATOR.length());

        List<String> givenNames = new ArrayList<>();
        for (String part : givenPart.split("<")) {
            if (!part.isEmpty()) {
                givenNames.add(part);
            }
        }

        return new ParsedName(surname, givenNames);
    }

    private static String joinComponents(String identifier) {
        return identifier.replace(MrzCheckDigit.FILLER, ' ').trim();
    }

    private void requireValidShape(List<String> lines) {
        Objects.requireNonNull(lines, "lines must not be null");

        if (lines.size() != MrzFormat.TD1.lineCount()) {
            throw new MrzParseException(
                    "TD1 requires %d lines, received %d"
                            .formatted(MrzFormat.TD1.lineCount(), lines.size()));
        }
        for (int index = 0; index < lines.size(); index++) {
            int length = lines.get(index).length();
            if (length != MrzFormat.TD1.lineLength()) {
                throw new MrzParseException(
                        "TD1 line %d must be %d characters, received %d"
                                .formatted(index + 1, MrzFormat.TD1.lineLength(), length));
            }
        }
    }

    private static String stripFiller(String field) {
        return field.replace(String.valueOf(MrzCheckDigit.FILLER), "");
    }

    private static String stripTrailingFiller(String field) {
        int end = field.length();
        while (end > 0 && field.charAt(end - 1) == MrzCheckDigit.FILLER) {
            end--;
        }
        return field.substring(0, end);
    }

    private record ParsedName(String surname, List<String> givenNames) {
    }
}