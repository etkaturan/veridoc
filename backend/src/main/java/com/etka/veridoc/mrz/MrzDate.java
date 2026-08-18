package com.etka.veridoc.mrz;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Year;
import java.util.Optional;

/**
 * Interprets the six-digit YYMMDD dates used throughout the MRZ.
 *
 * <p>MRZ dates carry only two year digits, so the century must be inferred.
 * The rule differs by field: a date of birth cannot be in the future, while
 * an expiry date is expected to be near the present. Both are handled by
 * sliding windows relative to the current date, which is passed in rather
 * than read from the system clock so that the behaviour is testable.
 */
public final class MrzDate {

    private MrzDate() {
        throw new AssertionError("Utility class — not meant to be instantiated");
    }

    /**
     * Parses a date of birth. The century is chosen so that the result is
     * never in the future.
     *
     * @param yymmdd six digits
     * @param today  the reference date
     * @return the parsed date, or empty if the digits are not a valid date
     */
    public static Optional<LocalDate> parseBirthDate(String yymmdd, LocalDate today) {
        return parse(yymmdd, today, true);
    }

    /**
     * Parses an expiry date. The century is chosen so that the result falls
     * within roughly the next 80 years, which covers every document validity
     * period in practical use.
     *
     * @param yymmdd six digits
     * @param today  the reference date
     * @return the parsed date, or empty if the digits are not a valid date
     */
    public static Optional<LocalDate> parseExpiryDate(String yymmdd, LocalDate today) {
        return parse(yymmdd, today, false);
    }

    private static Optional<LocalDate> parse(String yymmdd, LocalDate today, boolean mustBePast) {
        if (yymmdd == null || yymmdd.length() != 6 || !yymmdd.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }

        int twoDigitYear = Integer.parseInt(yymmdd.substring(0, 2));
        int month = Integer.parseInt(yymmdd.substring(2, 4));
        int day = Integer.parseInt(yymmdd.substring(4, 6));

        int currentCentury = (today.getYear() / 100) * 100;
        int candidate = currentCentury + twoDigitYear;

        if (mustBePast) {
            // A birth date in the future must belong to the previous century.
            if (candidate > today.getYear()) {
                candidate -= 100;
            }
        } else {
            // An expiry date more than ~20 years in the past is far more
            // likely to belong to the next century.
            if (candidate < today.getYear() - 20) {
                candidate += 100;
            }
        }

        try {
            return Optional.of(LocalDate.of(candidate, month, day));
        } catch (DateTimeException invalidDate) {
            return Optional.empty();
        }
    }

    /**
     * Calculates completed years between two dates.
     *
     * @param birthDate the date of birth
     * @param asOf      the date to calculate age at
     * @return completed years, or empty if the birth date is after {@code asOf}
     */
    public static Optional<Integer> ageInYears(LocalDate birthDate, LocalDate asOf) {
        if (birthDate == null || asOf == null || birthDate.isAfter(asOf)) {
            return Optional.empty();
        }
        return Optional.of(java.time.Period.between(birthDate, asOf).getYears());
    }
}