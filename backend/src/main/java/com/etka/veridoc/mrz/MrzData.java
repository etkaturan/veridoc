package com.etka.veridoc.mrz;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The structured content of a parsed machine readable zone.
 *
 * <p>This record holds what the MRZ <em>says</em>. Whether the MRZ is
 * internally consistent is reported separately by {@link MrzVerification},
 * so that a document with a failed checksum can still be inspected.
 *
 * @param format          the MRZ layout this was parsed from
 * @param documentCode    e.g. "P" for passport
 * @param issuingState    ISO 3166-1 alpha-3 code of the issuing authority
 * @param surname         primary identifier, as printed
 * @param givenNames      secondary identifiers, in order
 * @param documentNumber  the document number, filler stripped
 * @param nationality     ISO 3166-1 alpha-3 code
 * @param dateOfBirth     interpreted date of birth, if the digits were valid
 * @param sex             'M', 'F', or '<' when unspecified
 * @param expiryDate      interpreted expiry date, if the digits were valid
 * @param optionalData    personal number or other issuer-defined data
 */
public record MrzData(
        MrzFormat format,
        String documentCode,
        String issuingState,
        String surname,
        List<String> givenNames,
        String documentNumber,
        String nationality,
        Optional<LocalDate> dateOfBirth,
        char sex,
        Optional<LocalDate> expiryDate,
        String optionalData
) {

    /** Compact constructor: defensive copy so the record is genuinely immutable. */
    public MrzData {
        givenNames = List.copyOf(givenNames);
    }

    /** The full name in reading order, e.g. "ANNA MARIA ERIKSSON". */
    public String fullName() {
        return String.join(" ", givenNames) + " " + surname;
    }

    /** Age in completed years as of the given date, if the date of birth parsed. */
    public Optional<Integer> ageAt(LocalDate asOf) {
        return dateOfBirth.flatMap(birth -> MrzDate.ageInYears(birth, asOf));
    }

    /** Whether the holder has reached the given age as of the given date. */
    public boolean isAtLeastAge(int minimumAge, LocalDate asOf) {
        return ageAt(asOf).map(age -> age >= minimumAge).orElse(false);
    }

    /** Whether the document had expired as of the given date. */
    public boolean isExpiredAt(LocalDate asOf) {
        return expiryDate.map(expiry -> expiry.isBefore(asOf)).orElse(false);
    }
}