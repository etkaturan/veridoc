package com.etka.veridoc.api;

import com.etka.veridoc.document.AgeAnswer;

/**
 * Answers an age question about a subject from stored verification data.
 *
 * @param status the outcome — answered, no verification on file, or the
 *               supporting document has expired
 * @param meets  the answer, present only when status is ANSWERED
 */
public record SubjectAgeResponse(
        String status,
        Boolean meets,
        Integer minimumAge,
        String detail
) {

    public static SubjectAgeResponse from(AgeAnswer answer) {
        return switch (answer.status()) {
            case ANSWERED -> new SubjectAgeResponse(
                    "ANSWERED", answer.meets(), answer.minimumAge(), null);
            case NO_VERIFICATION -> new SubjectAgeResponse(
                    "NO_VERIFICATION", null, null,
                    "No verified document is on file for this subject");
            case DOCUMENT_EXPIRED -> new SubjectAgeResponse(
                    "DOCUMENT_EXPIRED", null, null,
                    "The verified document expired on " + answer.expiredOn());
        };
    }
}