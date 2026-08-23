package com.etka.veridoc.api;

import java.util.UUID;

/**
 * The result of verifying a document.
 *
 * <p>Carries the record id so the caller can bind it to a subject, and the
 * trustworthiness flag so they know whether to. Deliberately no name, date of
 * birth or document number: the caller has the document in front of them and
 * does not need us to echo it back.
 */
public record VerificationResponse(
        UUID recordId,
        String format,
        String issuingState,
        boolean trustworthy,
        String failedChecks,
        String message
) {

    public static VerificationResponse failed(String message) {
        return new VerificationResponse(null, null, null, false, null, message);
    }
}