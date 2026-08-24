package com.etka.veridoc.api;

import com.etka.veridoc.document.VerificationOutcome;
import com.etka.veridoc.mrz.MrzData;

import java.time.LocalDate;
import java.util.List;

/**
 * Full structured detail from a verification, for inspection/testing tools.
 *
 * <p>Unlike {@link AgeCheckResponse}, which deliberately returns only a
 * boolean, this exposes the underlying document data in full. It exists for
 * demonstrating and testing the extraction pipeline itself — an operator
 * inspecting what the system actually read — not for the privacy-preserving
 * age-verification use case, which stays on its own minimal-disclosure
 * endpoint.
 */
public record DocumentDetailResponse(
        boolean success,
        String message,
        java.util.UUID recordId,
        String format,
        boolean trustworthy,
        List<String> failedChecks,
        String documentCode,
        String issuingState,
        String surname,
        List<String> givenNames,
        String documentNumber,
        String nationality,
        String sex,
        LocalDate dateOfBirth,
        LocalDate expiryDate,
        Integer age,
        Boolean expired
) {

    public static DocumentDetailResponse failure(String message) {
        return new DocumentDetailResponse(
                false, message, null, null, false, List.of(),
                null, null, null, List.of(), null, null, null, null, null, null, null);
    }

    public static DocumentDetailResponse from(VerificationOutcome outcome, LocalDate today) {
        if (outcome.status() != VerificationOutcome.Status.PARSED) {
            return failure(outcome.message().orElse("Could not read this document"));
        }

        MrzData data = outcome.data().orElseThrow();
        var verification = outcome.verification().orElseThrow();

        return new DocumentDetailResponse(
                true,
                null,
                outcome.recordId().orElse(null),
                outcome.format().orElseThrow().name(),
                verification.isFullyValid(),
                verification.failedFields().stream().map(Enum::name).toList(),
                data.documentCode(),
                data.issuingState(),
                data.surname(),
                data.givenNames(),
                data.documentNumber(),
                data.nationality(),
                String.valueOf(data.sex()),
                data.dateOfBirth().orElse(null),
                data.expiryDate().orElse(null),
                data.ageAt(today).orElse(null),
                data.isExpiredAt(today)
        );
    }
}