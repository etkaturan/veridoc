package com.etka.veridoc.api;

/**
 * Answers only "has this person reached the required age".
 *
 * <p>Deliberately carries no date of birth, no name, no document number. A
 * client verifying an age threshold has no need for identity data, and every
 * field omitted here is one the client does not have to store, secure or
 * justify holding.
 *
 * @param meetsRequirement whether the holder has reached {@code requiredAge}
 * @param requiredAge      the threshold that was checked
 * @param trustworthy      whether every check digit validated
 * @param message          explanation when the check could not be performed
 */
public record AgeCheckResponse(
        Boolean meetsRequirement,
        int requiredAge,
        boolean trustworthy,
        String message
) {

    public static AgeCheckResponse of(boolean meets, int requiredAge, boolean trustworthy) {
        return new AgeCheckResponse(meets, requiredAge, trustworthy, null);
    }

    public static AgeCheckResponse failed(int requiredAge, String message) {
        return new AgeCheckResponse(null, requiredAge, false, message);
    }
}