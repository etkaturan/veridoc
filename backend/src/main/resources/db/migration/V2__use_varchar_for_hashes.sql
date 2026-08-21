-- CHAR(n) in PostgreSQL pads values with trailing spaces and offers no storage
-- benefit over VARCHAR. The padding causes subtle comparison and length bugs,
-- and Hibernate maps a String field to VARCHAR, so the original CHAR columns
-- also failed schema validation.

ALTER TABLE verification_record
    ALTER COLUMN document_hash TYPE VARCHAR(64),
    ALTER COLUMN name_hash     TYPE VARCHAR(64),
    ALTER COLUMN issuing_state TYPE VARCHAR(3);