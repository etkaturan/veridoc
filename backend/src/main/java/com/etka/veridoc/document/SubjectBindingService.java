package com.etka.veridoc.document;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Associates verification records with subjects, and answers questions about a
 * subject from previously stored records.
 *
 * <p>This is what makes verification worth persisting: a subject verifies once,
 * and every later age check is answered from a stored boolean without the
 * document, the date of birth, or any identity data being involved again.
 */
@Service
public class SubjectBindingService {

    private final VerificationRecordRepository repository;

    public SubjectBindingService(VerificationRecordRepository repository) {
        this.repository = repository;
    }

    /**
     * Attaches a subject reference to a verification record.
     *
     * @param recordId         the record returned by a verification
     * @param subjectReference the caller's own identifier for the person
     * @return the bound record, or empty if no such record exists
     * @throws IllegalStateException if the record failed its check digits
     */
    @Transactional
    public Optional<VerificationRecord> bind(UUID recordId, String subjectReference) {
        return repository.findById(recordId).map(record -> {
            // A record whose check digits failed may contain misread data.
            // Binding it to a subject would give that data an authority it has
            // not earned, so the caller must re-verify instead.
            if (!record.isChecksPassed()) {
                throw new IllegalStateException(
                        "Cannot bind a record whose check digits failed; re-verify the document");
            }
            if (record.getSubjectReference() != null) {
                throw new IllegalStateException("Record is already bound to a subject");
            }
            record.bindTo(subjectReference);
            return record;
        });
    }

    /**
     * Answers whether a subject has a valid verification meeting an age threshold.
     *
     * @param subjectReference the caller's identifier for the person
     * @param minimumAge       18 or 21; other values are not stored
     * @param today            reference date, used to check document expiry
     */
    @Transactional(readOnly = true)
    public AgeAnswer checkAge(String subjectReference, int minimumAge, LocalDate today) {
        var records = repository.findBySubjectReferenceOrderByCreatedAtDesc(subjectReference);

        if (records.isEmpty()) {
            return AgeAnswer.noVerification();
        }

        VerificationRecord latest = records.getFirst();

        // A document that has expired since verification no longer supports a
        // claim about its holder. The age fact remains true, but the evidence
        // for it has lapsed, and the caller should know the difference.
        if (latest.getExpiresOn() != null && latest.getExpiresOn().isBefore(today)) {
            return AgeAnswer.expired(latest.getExpiresOn());
        }

        boolean meets = switch (minimumAge) {
            case 18 -> latest.isOver18();
            case 21 -> latest.isOver21();
            default -> throw new IllegalArgumentException(
                    "Only 18 and 21 are stored; requested " + minimumAge);
        };

        return AgeAnswer.answered(meets, minimumAge);
    }
}