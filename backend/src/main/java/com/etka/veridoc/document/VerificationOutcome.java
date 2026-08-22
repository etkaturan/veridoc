package com.etka.veridoc.document;

import com.etka.veridoc.mrz.MrzData;
import com.etka.veridoc.mrz.MrzFormat;
import com.etka.veridoc.mrz.MrzVerification;

import java.util.Optional;

/**
 * The result of a verification attempt, including the ways it can fail
 * without being exceptional.
 */
public record VerificationOutcome(
        Status status,
        Optional<MrzFormat> format,
        Optional<MrzData> data,
        Optional<MrzVerification> verification,
        Optional<String> message,
        Optional<java.util.UUID> recordId
) {

    /** Returns a copy carrying the id of the persisted record. */
    public VerificationOutcome withRecordId(java.util.UUID id) {
        return new VerificationOutcome(status, format, data, verification, message,
                Optional.of(id));
    }

    public enum Status {
        /** Parsed successfully; consult the check digits for trustworthiness. */
        PARSED,
        /** No usable machine readable zone was found in the image. */
        UNREADABLE,
        /** A valid layout was recognised but no parser supports it yet. */
        UNSUPPORTED_FORMAT
    }

    public static VerificationOutcome parsed(
            MrzFormat format, MrzData data, MrzVerification verification) {
        return new VerificationOutcome(Status.PARSED,
                Optional.of(format), Optional.of(data),
                Optional.of(verification), Optional.empty(), Optional.empty());
    }

    public static VerificationOutcome unreadable(String message) {
        return new VerificationOutcome(Status.UNREADABLE,
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(message), Optional.empty());
    }

    public static VerificationOutcome unsupported(MrzFormat format) {
        return new VerificationOutcome(Status.UNSUPPORTED_FORMAT,
                Optional.of(format), Optional.empty(), Optional.empty(),
                Optional.of("Layout %s (%s) is not yet supported"
                        .formatted(format, format.description())),
                Optional.empty());
    }

    public boolean isTrustworthy() {
        return verification.map(MrzVerification::isFullyValid).orElse(false);
    }
}