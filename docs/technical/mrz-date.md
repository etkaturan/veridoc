# MrzDate

**Package:** `com.etka.veridoc.mrz`

## Purpose

Interprets the six-digit `YYMMDD` dates in an MRZ and calculates age.

## The two-digit year problem

An MRZ stores only two year digits, so `74` is ambiguous between 1974 and
2074. The correct century depends on which field it is:

**Date of birth** — cannot be in the future. If the current-century
interpretation would place the birth after today, subtract 100 years.

**Expiry date** — is near the present. Document validity periods are
typically 5–10 years, so an expiry more than about 20 years in the past
almost certainly belongs to the next century.

These windows are heuristics, not certainties. They are correct for every
realistic document but would misjudge, for example, a holder over 100 years
old. Documented here so the limitation is visible rather than surprising.

## API

| Method | Returns |
|---|---|
| `parseBirthDate(String, LocalDate today)` | `Optional<LocalDate>` |
| `parseExpiryDate(String, LocalDate today)` | `Optional<LocalDate>` |
| `ageInYears(LocalDate, LocalDate asOf)` | `Optional<Integer>` |

## Design decisions

**The reference date is a parameter, never `LocalDate.now()`.** This is the
most important decision in the file. A class that reads the system clock
internally cannot be tested deterministically, and its behaviour changes
silently over time — a test passing today might fail in five years. Passing
the date in makes every century rule explicit and verifiable.

**`java.time` only.** `java.util.Date` and `Calendar` are legacy: mutable,
with zero-indexed months and timestamp-versus-date confusion. `LocalDate`
is immutable and means precisely a date with no time and no zone.

**`Period.between(...).getYears()` for age.** Handles leap years and
varying month lengths correctly. Dividing elapsed days by 365.25 is wrong
around birthdays — exactly when age verification matters most.

**Invalid dates return empty, not an exception.** `19740230` is bad data,
not a structural failure. The caller decides how to treat it.

## Tests

To be added alongside the parser in Step 1c, covering century boundaries,
leap-year birthdays, invalid dates, and age calculation on and around a
birthday.