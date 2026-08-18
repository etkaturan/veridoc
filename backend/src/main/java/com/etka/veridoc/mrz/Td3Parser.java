package com.etka.veridoc.mrz;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parses the TD3 layout used by passports: two lines of 44 characters.
 *
 * <p>Field positions are defined by ICAO Doc 9303 Part 4 and are fixed —
 * every TD3 document in the world places the same field at the same offset.
 */
public final class Td3Parser implements MrzParser {

    // --- Line 1 offsets ---
    private static final int DOCUMENT_CODE_START = 0;
    private static final int DOCUMENT_CODE_END = 2;
    private static final int ISSUING_STATE_START = 2;
    private static final int ISSUING_STATE_END = 5;
    private static final int NAME_START = 5;
    private static final int NAME_END = 44;

    // --- Line 2 offsets ---
    private static final int DOCUMENT_NUMBER_START = 0;
    private static final int DOCUMENT_NUMBER_END = 9;
    private static final int DOCUMENT_NUMBER_CD = 9;
    private static final int NATIONALITY_START = 10;
    private static final int NATIONALITY_END = 13;
    private static final int BIRTH_DATE_START = 13;
    private static final int BIRTH_DATE_END = 19;
    private static final int BIRTH_DATE_CD = 19;
    private static final int SEX = 20;
    private static final int EXPIRY_DATE_START = 21;
    private static final int EXPIRY_DATE_END = 27;
    private static final int EXPIRY_DATE_CD = 27;
    private static final int OPTIONAL_DATA_START = 28;
    private static final int OPTIONAL_DATA_END = 42;
    private static final int OPTIONAL_DATA_CD = 42;
    private static final int COMPOSITE_CD = 43;

    /** Separates surname from given names within the name field. */
    private static final String NAME_SEPARATOR = "<<";

    @Override
    public MrzFormat supportedFormat() {
        return MrzFormat.TD3;
    }

    @Override
    public MrzData parse(List<String> lines, LocalDate today) {
        requireValidShape(lines);
        Objects.requireNonNull(today, "today must not be null");

        String line1 = lines.get(0);
        String line2 = lines.get(1);

        String nameField = line1.substring(NAME_START, NAME_END);
        ParsedName name = splitName(nameField);

        String birthDigits = line2.substring(BIRTH_DATE_START, BIRTH_DATE_END);
        String expiryDigits = line2.substring(EXPIRY_DATE_START, EXPIRY_DATE_END);

        return new MrzData(
                MrzFormat.TD3,
                stripFiller(line1.substring(DOCUMENT_CODE_START, DOCUMENT_CODE_END)),
                stripFiller(line1.substring(ISSUING_STATE_START, ISSUING_STATE_END)),
                name.surname(),
                name.givenNames(),
                stripFiller(line2.substring(DOCUMENT_NUMBER_START, DOCUMENT_NUMBER_END)),
                stripFiller(line2.substring(NATIONALITY_START, NATIONALITY_END)),
                MrzDate.parseBirthDate(birthDigits, today),
                line2.charAt(SEX),
                MrzDate.parseExpiryDate(expiryDigits, today),
                stripFiller(line2.substring(OPTIONAL_DATA_START, OPTIONAL_DATA_END))
        );
    }

    @Override
    public MrzVerification verify(List<String> lines) {
        requireValidShape(lines);

        String line2 = lines.get(1);
        Map<MrzVerification.MrzField, Boolean> results =
                new EnumMap<>(MrzVerification.MrzField.class);

        results.put(MrzVerification.MrzField.DOCUMENT_NUMBER,
                MrzCheckDigit.matches(
                        line2.substring(DOCUMENT_NUMBER_START, DOCUMENT_NUMBER_END),
                        line2.charAt(DOCUMENT_NUMBER_CD)));

        results.put(MrzVerification.MrzField.DATE_OF_BIRTH,
                MrzCheckDigit.matches(
                        line2.substring(BIRTH_DATE_START, BIRTH_DATE_END),
                        line2.charAt(BIRTH_DATE_CD)));

        results.put(MrzVerification.MrzField.EXPIRY_DATE,
                MrzCheckDigit.matches(
                        line2.substring(EXPIRY_DATE_START, EXPIRY_DATE_END),
                        line2.charAt(EXPIRY_DATE_CD)));

        results.put(MrzVerification.MrzField.OPTIONAL_DATA,
                MrzCheckDigit.matches(
                        line2.substring(OPTIONAL_DATA_START, OPTIONAL_DATA_END),
                        line2.charAt(OPTIONAL_DATA_CD)));

        results.put(MrzVerification.MrzField.COMPOSITE,
                MrzCheckDigit.matches(
                        compositeInput(line2),
                        line2.charAt(COMPOSITE_CD)));

        return new MrzVerification(results);
    }

    /**
     * Builds the input to the composite check digit: the document number and
     * its check digit, the birth date and its check digit, and the expiry
     * date through the optional-data check digit. Nationality and sex are
     * excluded by the standard.
     */
    private static String compositeInput(String line2) {
        return line2.substring(DOCUMENT_NUMBER_START, DOCUMENT_NUMBER_CD + 1)
                + line2.substring(BIRTH_DATE_START, BIRTH_DATE_CD + 1)
                + line2.substring(EXPIRY_DATE_START, OPTIONAL_DATA_CD + 1);
    }

    /** Splits the fixed-width name field into surname and given names. */
    private static ParsedName splitName(String nameField) {
        String trimmed = stripTrailingFiller(nameField);
        int separator = trimmed.indexOf(NAME_SEPARATOR);

        if (separator < 0) {
            // No separator present. Treat the whole field as the surname
            // rather than guessing where the split belongs.
            return new ParsedName(joinComponents(trimmed), List.of());
        }

        String surname = joinComponents(trimmed.substring(0, separator));
        String givenPart = trimmed.substring(separator + NAME_SEPARATOR.length());

        List<String> givenNames = Arrays.stream(givenPart.split("<"))
                .filter(part -> !part.isEmpty())
                .toList();

        return new ParsedName(surname, givenNames);
    }

    /**
     * Renders a single identifier whose components are filler-separated.
     *
     * <p>Within the surname, '&lt;' separates parts of one name — VAN&lt;DER&lt;BERG
     * is "VAN DER BERG" — so components are joined with spaces. Within the
     * given-name field the same character separates distinct given names, so
     * there they are split into a list instead.
     */
    private static String joinComponents(String identifier) {
        return identifier.replace(MrzCheckDigit.FILLER, ' ').trim();
    }

    private void requireValidShape(List<String> lines) {
        Objects.requireNonNull(lines, "lines must not be null");

        if (lines.size() != MrzFormat.TD3.lineCount()) {
            throw new MrzParseException(
                    "TD3 requires %d lines, received %d"
                            .formatted(MrzFormat.TD3.lineCount(), lines.size()));
        }
        for (int index = 0; index < lines.size(); index++) {
            int length = lines.get(index).length();
            if (length != MrzFormat.TD3.lineLength()) {
                throw new MrzParseException(
                        "TD3 line %d must be %d characters, received %d"
                                .formatted(index + 1, MrzFormat.TD3.lineLength(), length));
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

    /** Internal carrier for the two halves of a name field. */
    private record ParsedName(String surname, List<String> givenNames) {
    }
}