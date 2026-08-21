package com.etka.veridoc.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Produces salted hashes of identity values so that records can be matched
 * without storing the values themselves.
 *
 * <p>The salt is per-deployment, which means hashes cannot be correlated across
 * deployments and a precomputed table built against one is useless against
 * another. Note the honest limitation: document numbers occupy a small enough
 * space that an attacker holding both the salt and the table could enumerate
 * them. The salt therefore has to be protected like a key, not treated as
 * public — hashing reduces exposure, it does not eliminate it.
 */
@Component
public class IdentityHasher {

    private final String salt;

    public IdentityHasher(@Value("${veridoc.hash.salt}") String salt) {
        this.salt = salt;
    }

    /** Hashes a document, scoped by issuing state so numbers cannot collide across countries. */
    public String hashDocument(String issuingState, String documentNumber) {
        return hash(issuingState + ":" + documentNumber);
    }

    /**
     * Hashes a name after normalising it, so that trivial formatting
     * differences do not produce different hashes.
     */
    public String hashName(String surname, java.util.List<String> givenNames) {
        String normalised = (String.join(" ", givenNames) + " " + surname)
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z ]", "")
                .replaceAll("\\s+", " ")
                .strip();
        return hash("name:" + normalised);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 is required of every Java implementation.
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}