# MrzData

**Package:** `com.etka.veridoc.mrz`

## Purpose

The structured contents of a parsed MRZ. Holds what the document *says*;
whether it is internally consistent is reported separately by
[MrzVerification](mrz-verification.md).

## Fields

| Field | Type | Notes |
|---|---|---|
| `format` | `MrzFormat` | Layout this was parsed from |
| `documentCode` | `String` | `P` for passport |
| `issuingState` | `String` | ISO 3166-1 alpha-3 |
| `surname` | `String` | Primary identifier |
| `givenNames` | `List<String>` | Secondary identifiers, in order |
| `documentNumber` | `String` | Filler stripped |
| `nationality` | `String` | ISO 3166-1 alpha-3 |
| `dateOfBirth` | `Optional<LocalDate>` | Empty if digits were invalid |
| `sex` | `char` | `M`, `F`, or `<` |
| `expiryDate` | `Optional<LocalDate>` | Empty if digits were invalid |
| `optionalData` | `String` | Personal number, issuer-defined |

## Derived methods

`fullName()`, `ageAt(LocalDate)`, `isAtLeastAge(int, LocalDate)`,
`isExpiredAt(LocalDate)`.

## Design decisions

**A `record`, not a class.** The compiler generates the constructor,
accessors, `equals`, `hashCode` and `toString`. Records are final with
final fields, so parsed data cannot be altered downstream — a meaningful
safety property when the data is someone's identity.

**Compact constructor performs a defensive copy.** A final reference to a
mutable `ArrayList` is still mutable through the caller's reference.
`List.copyOf` closes that hole.

**Separation of content from verification.** Deliberate: an MRZ with a
failed checksum is still worth inspecting — for diagnostics, for showing a
user which field to rescan. Merging the two would force a valid/invalid
decision at parse time.

**`Optional` fields for dates.** Some style guides discourage this. Used
here because "the digits did not parse" is a real, expected state that
callers must handle; representing it as `null` would be worse.

**Age methods take the reference date.** Same reasoning as `MrzDate`: no
hidden clocks.

## Privacy note

This record holds personal data. It should be short-lived — used to produce
a derived, minimised record and then discarded. In particular, prefer
storing "is over 18" over storing a date of birth where the use case allows.