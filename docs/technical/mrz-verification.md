# MrzVerification

**Package:** `com.etka.veridoc.mrz`

## Purpose

Reports the outcome of every check digit in an MRZ, per field.

## Fields checked

| Field | Covers |
|---|---|
| `DOCUMENT_NUMBER` | Positions 0–8 of line 2 |
| `DATE_OF_BIRTH` | Positions 13–18 |
| `EXPIRY_DATE` | Positions 21–26 |
| `OPTIONAL_DATA` | Positions 28–41 |
| `COMPOSITE` | 0–9, 13–19 and 21–42 concatenated |

The composite is the tamper detector. It covers each data field *together
with* its own check digit, so altering a field and recomputing its local
digit still fails the composite.

## API

| Method | Returns |
|---|---|
| `isFullyValid()` | `boolean` — every digit matched |
| `isCompositeValid()` | `boolean` |
| `failedFields()` | `Set<MrzField>` |

## Design decisions

**Per-field results, not one boolean.** Which field failed is actionable
information. A failed date-of-birth digit with everything else passing
suggests OCR trouble on a specific line; a failed composite with all local
digits passing suggests deliberate alteration.

**A failed check digit is a result, not an exception.** Structural failures
— wrong line count, invalid characters — throw `MrzParseException`. A
checksum mismatch is an ordinary, expected outcome of verification. Keeping
this boundary clean is what stops error handling becoming unmanageable.

**Nested enum.** `MrzField` has no meaning outside verification, so it
lives with it rather than in its own file.

**Immutable map.** `Map.copyOf` in the compact constructor.

## Interpreting results

| Result | Likely meaning |
|---|---|
| All pass | Consistent read; document not proven genuine |
| One field fails, composite fails | OCR misread that field |
| All fields pass, composite fails | Possible alteration |
| Most fail | Wrong region cropped, or not an MRZ |