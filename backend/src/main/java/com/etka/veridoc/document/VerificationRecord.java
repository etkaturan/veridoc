package com.etka.veridoc.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A stored verification result.
 *
 * <p>Holds derived facts only. There is deliberately no name, date of birth or
 * document number here: a leak of this table reveals that <em>someone</em> over
 * a given age verified a document, which is a materially different incident
 * from leaking passport numbers.
 */
@Entity
@Table(name = "verification_record")
public class VerificationRecord {

    @Id
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "document_hash", nullable = false, length = 64)
    private String documentHash;

    @Column(name = "name_hash", nullable = false, length = 64)
    private String nameHash;

    @Column(name = "document_format", nullable = false, length = 8)
    private String documentFormat;

    @Column(name = "issuing_state", nullable = false, length = 3)
    private String issuingState;

    @Column(name = "checks_passed", nullable = false)
    private boolean checksPassed;

    @Column(name = "failed_fields", length = 255)
    private String failedFields;

    @Column(name = "over_18", nullable = false)
    private boolean over18;

    @Column(name = "over_21", nullable = false)
    private boolean over21;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(name = "subject_reference", length = 128)
    private String subjectReference;

    /** Required by JPA. Not for application use. */
    protected VerificationRecord() {
    }

    private VerificationRecord(Builder builder) {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.documentHash = builder.documentHash;
        this.nameHash = builder.nameHash;
        this.documentFormat = builder.documentFormat;
        this.issuingState = builder.issuingState;
        this.checksPassed = builder.checksPassed;
        this.failedFields = builder.failedFields;
        this.over18 = builder.over18;
        this.over21 = builder.over21;
        this.expiresOn = builder.expiresOn;
        this.subjectReference = builder.subjectReference;
    }

    public static Builder builder() {
        return new Builder();
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getDocumentHash() {
        return documentHash;
    }

    public String getNameHash() {
        return nameHash;
    }

    public String getDocumentFormat() {
        return documentFormat;
    }

    public String getIssuingState() {
        return issuingState;
    }

    public boolean isChecksPassed() {
        return checksPassed;
    }

    public String getFailedFields() {
        return failedFields;
    }

    public boolean isOver18() {
        return over18;
    }

    public boolean isOver21() {
        return over21;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public String getSubjectReference() {
        return subjectReference;
    }

    /** The only mutable field: a record may be bound to a subject after creation. */
    public void bindTo(String subjectReference) {
        this.subjectReference = subjectReference;
    }

    /**
     * Fluent construction. The record has eleven fields, most of them boolean
     * or String, and a positional constructor of that shape is easy to get
     * silently wrong — two adjacent booleans transposed would compile fine and
     * store the wrong answer.
     */
    public static final class Builder {
        private String documentHash;
        private String nameHash;
        private String documentFormat;
        private String issuingState;
        private boolean checksPassed;
        private String failedFields;
        private boolean over18;
        private boolean over21;
        private LocalDate expiresOn;
        private String subjectReference;

        public Builder documentHash(String value) {
            this.documentHash = value;
            return this;
        }

        public Builder nameHash(String value) {
            this.nameHash = value;
            return this;
        }

        public Builder documentFormat(String value) {
            this.documentFormat = value;
            return this;
        }

        public Builder issuingState(String value) {
            this.issuingState = value;
            return this;
        }

        public Builder checksPassed(boolean value) {
            this.checksPassed = value;
            return this;
        }

        public Builder failedFields(String value) {
            this.failedFields = value;
            return this;
        }

        public Builder over18(boolean value) {
            this.over18 = value;
            return this;
        }

        public Builder over21(boolean value) {
            this.over21 = value;
            return this;
        }

        public Builder expiresOn(LocalDate value) {
            this.expiresOn = value;
            return this;
        }

        public Builder subjectReference(String value) {
            this.subjectReference = value;
            return this;
        }

        public VerificationRecord build() {
            return new VerificationRecord(this);
        }
    }

    /** Deliberately excludes every hash and the subject reference. */
    @Override
    public String toString() {
        return "VerificationRecord[id=%s, format=%s, state=%s, checksPassed=%s]"
                .formatted(id, documentFormat, issuingState, checksPassed);
    }
}