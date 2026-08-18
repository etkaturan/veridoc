package com.etka.veridoc.mrz;

import java.util.EnumMap;
import java.util.Map;

/**
 * The outcome of checking every check digit in an MRZ.
 *
 * <p>A failed check digit is an ordinary result, not an error: it means the
 * MRZ was either misread by OCR or altered. Either way the caller needs the
 * detail of which field failed, so results are reported per field rather
 * than as a single boolean.
 */
public record MrzVerification(Map<MrzField, Boolean> results) {

    /** The fields of an MRZ that carry their own check digit. */
    public enum MrzField {
        DOCUMENT_NUMBER,
        DATE_OF_BIRTH,
        EXPIRY_DATE,
        OPTIONAL_DATA,
        COMPOSITE
    }

    public MrzVerification {
        results = Map.copyOf(results);
    }

    /** True only if every check digit matched. */
    public boolean isFullyValid() {
        return results.values().stream().allMatch(Boolean::booleanValue);
    }

    /** True if the composite digit matched, indicating no field was altered. */
    public boolean isCompositeValid() {
        return Boolean.TRUE.equals(results.get(MrzField.COMPOSITE));
    }

    /** The fields whose check digits did not match. */
    public java.util.Set<MrzField> failedFields() {
        return results.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toCollection(
                        () -> new java.util.TreeSet<>()));
    }
}