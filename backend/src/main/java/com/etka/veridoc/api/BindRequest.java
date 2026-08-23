package com.etka.veridoc.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to associate a verification with a subject.
 *
 * @param subjectReference the caller's own identifier for the person — a user
 *                         id, account number, or similar. Veridoc never
 *                         interprets it, only stores and matches on it.
 */
public record BindRequest(
        @NotBlank @Size(max = 128) String subjectReference
) {
}