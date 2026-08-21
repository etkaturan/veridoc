package com.etka.veridoc.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for verification records.
 *
 * <p>Spring Data derives the implementation from the method names, so there is
 * no SQL to write or maintain here.
 */
public interface VerificationRecordRepository extends JpaRepository<VerificationRecord, UUID> {

    /** Most recent verification of a given document, if any. */
    Optional<VerificationRecord> findFirstByDocumentHashOrderByCreatedAtDesc(String documentHash);

    /** Every verification bound to a subject, newest first. */
    List<VerificationRecord> findBySubjectReferenceOrderByCreatedAtDesc(String subjectReference);

    /** Whether a document has ever been verified with all checks passing. */
    boolean existsByDocumentHashAndChecksPassedTrue(String documentHash);
}