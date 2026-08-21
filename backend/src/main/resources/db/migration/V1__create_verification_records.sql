-- NOTE: the CHAR columns below are corrected to VARCHAR in V2. This file is
-- left unchanged because it has already been applied; Flyway records a
-- checksum per migration and editing an applied one causes a validation
-- failure on every subsequent startup.

-- Verification records deliberately hold derived facts rather than identity
-- data. A document number is stored only as a salted hash: enough to recognise
-- the same document returning, useless to an attacker who obtains the table.
-- Dates of birth are never stored; only the age thresholds that were checked.

CREATE TABLE verification_record (
    id                  UUID         PRIMARY KEY,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- Salted hash of issuing state + document number. Identifies a repeat
    -- document without revealing which document it is.
    document_hash       VARCHAR(64)  NOT NULL,

    -- Salted hash of the normalised full name, for matching against a claimed
    -- identity without storing the name itself.
    name_hash           VARCHAR(64)  NOT NULL,

    document_format     VARCHAR(8)   NOT NULL,
    issuing_state       VARCHAR(3)   NOT NULL,

    -- Whether every check digit validated at the time of verification.
    checks_passed       BOOLEAN      NOT NULL,
    failed_fields       VARCHAR(255),

    -- Derived age facts. The date of birth itself is not retained.
    over_18             BOOLEAN      NOT NULL,
    over_21             BOOLEAN      NOT NULL,

    -- Expiry is stored as a date because a client legitimately needs to know
    -- when a previously verified document stops being valid.
    expires_on          DATE,

    -- Optional link to a user account in the consuming application.
    subject_reference   VARCHAR(128)
);

CREATE INDEX idx_verification_document_hash ON verification_record (document_hash);
CREATE INDEX idx_verification_subject ON verification_record (subject_reference)
    WHERE subject_reference IS NOT NULL;

COMMENT ON TABLE verification_record IS
    'Derived results of document verification. Contains no identity data by design.';