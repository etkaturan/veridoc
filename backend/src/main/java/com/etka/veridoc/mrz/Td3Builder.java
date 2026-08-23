package com.etka.veridoc.mrz;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Constructs a syntactically valid TD3 machine readable zone from field values,
 * computing every check digit.
 *
 * <p>Exists so that test documents can be produced for any scenario — expired,
 * future-dated, a holder under eighteen — without hand-computing checksums.
 * Hand-computed digits are error-prone in exactly the way the digits exist to
 * detect, so the same {@link MrzCheckDigit} implementation used to verify
 * documents is used to build them.
 */
public final class Td3Builder {

    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");
    private static final int NAME_FIELD_LENGTH = 39;
    private static final int DOCUMENT_NUMBER_LENGTH = 9;
    private static final int OPTIONAL_DATA_LENGTH = 14;

    private String documentCode = "P";
    private String issuingState = "UTO";
    private String surname = "SPECIMEN";
    private List<String> givenNames = List.of("TEST");
    private String documentNumber = "L898902C3";
    private String nationality = "UTO";
    private LocalDate dateOfBirth = LocalDate.of(1974, 8, 12);
    private char sex = 'F';
    private LocalDate expiryDate = LocalDate.of(2032, 4, 15);
    private String optionalData = "";

    public Td3Builder documentCode(String value) {
        this.documentCode = value;
        return this;
    }

    public Td3Builder issuingState(String value) {
        this.issuingState = value;
        return this;
    }

    public Td3Builder name(String surname, String... givenNames) {
        this.surname = surname;
        this.givenNames = List.of(givenNames);
        return this;
    }

    public Td3Builder documentNumber(String value) {
        this.documentNumber = value;
        return this;
    }

    public Td3Builder nationality(String value) {
        this.nationality = value;
        return this;
    }

    public Td3Builder dateOfBirth(LocalDate value) {
        this.dateOfBirth = value;
        return this;
    }

    public Td3Builder sex(char value) {
        this.sex = value;
        return this;
    }

    public Td3Builder expiryDate(LocalDate value) {
        this.expiryDate = value;
        return this;
    }

    public Td3Builder optionalData(String value) {
        this.optionalData = value;
        return this;
    }

    /** @return the two lines of the machine readable zone, each 44 characters */
    public List<String> build() {
        String line1 = buildLine1();
        String line2 = buildLine2();
        return List.of(line1, line2);
    }

    private String buildLine1() {
        String nameField = surname + "<<" + String.join("<", givenNames);

        if (nameField.length() > NAME_FIELD_LENGTH) {
            // ICAO truncates rather than rejecting; reproducing that keeps the
            // builder honest about what a real document would contain.
            nameField = nameField.substring(0, NAME_FIELD_LENGTH);
        }

        return pad(documentCode, 2)
                + pad(issuingState, 3)
                + pad(nameField, NAME_FIELD_LENGTH);
    }

    private String buildLine2() {
        String number = pad(documentNumber, DOCUMENT_NUMBER_LENGTH);
        char numberCheck = digit(number);

        String birth = dateOfBirth.format(YYMMDD);
        char birthCheck = digit(birth);

        String expiry = expiryDate.format(YYMMDD);
        char expiryCheck = digit(expiry);

        String optional = pad(optionalData, OPTIONAL_DATA_LENGTH);
        // ICAO permits '<' where an unused optional field's check digit would go.
        char optionalCheck = optionalData.isEmpty() ? '<' : digit(optional);

        // The composite covers the document number, birth date and expiry date
        // together with their own check digits, plus the optional data field.
        // Nationality and sex are excluded by the standard.
        String composite = number + numberCheck
                + birth + birthCheck
                + expiry + expiryCheck
                + optional + optionalCheck;

        return number + numberCheck
                + pad(nationality, 3)
                + birth + birthCheck
                + sex
                + expiry + expiryCheck
                + optional + optionalCheck
                + digit(composite);
    }

    private static char digit(String field) {
        return (char) ('0' + MrzCheckDigit.compute(field));
    }

    private static String pad(String value, int length) {
        Objects.requireNonNull(value);
        String upper = value.toUpperCase(java.util.Locale.ROOT).replace(' ', '<');
        return upper.length() >= length
                ? upper.substring(0, length)
                : upper + "<".repeat(length - upper.length());
    }
}