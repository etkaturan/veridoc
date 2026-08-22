package com.etka.veridoc.document;

import java.time.LocalDate;

/**
 * The result of asking whether a subject meets an age requirement.
 *
 * @param status      why the question could or could not be answered
 * @param meets       the answer, when there is one
 * @param minimumAge  the threshold that was checked
 * @param expiredOn   when the supporting document expired, if it has
 */
public record AgeAnswer(
        Status status,
        Boolean meets,
        Integer minimumAge,
        LocalDate expiredOn
) {

    public enum Status {
        /** A valid verification exists and the question is answered. */
        ANSWERED,
        /** No verification is bound to this subject. */
        NO_VERIFICATION,
        /** A verification exists but the document has since expired. */
        DOCUMENT_EXPIRED
    }

    public static AgeAnswer answered(boolean meets, int minimumAge) {
        return new AgeAnswer(Status.ANSWERED, meets, minimumAge, null);
    }

    public static AgeAnswer noVerification() {
        return new AgeAnswer(Status.NO_VERIFICATION, null, null, null);
    }

    public static AgeAnswer expired(LocalDate expiredOn) {
        return new AgeAnswer(Status.DOCUMENT_EXPIRED, null, null, expiredOn);
    }
}