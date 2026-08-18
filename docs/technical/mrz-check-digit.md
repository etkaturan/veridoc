# MrzCheckDigit

**Package:** `com.etka.veridoc.mrz`
**Standard:** ICAO Doc 9303, Part 3

## Purpose

Implements the checksum arithmetic used by every machine readable travel
document. This is the mathematical foundation of the entire verification
pipeline: it allows the system to detect misread or altered fields with no
network access and no external service.

## The algorithm

1. **Character to value.** Digits map to themselves (`0`–`9`), letters to
   10–35 (`A`=10 … `Z`=35), and the filler `<` to 0.
2. **Weighted sum.** Multiply each value by a repeating 7-3-1 weight,
   cycling every three characters regardless of field length.
3. **Modulo 10.** The remainder is the check digit.

### Worked example — date of birth `740812`

| Position | Char | Value | Weight | Product |
|---|---|---|---|---|
| 0 | 7 | 7 | 7 | 49 |
| 1 | 4 | 4 | 3 | 12 |
| 2 | 0 | 0 | 1 | 0 |
| 3 | 8 | 8 | 7 | 56 |
| 4 | 1 | 1 | 3 | 3 |
| 5 | 2 | 2 | 1 | 2 |

Sum 122 → `122 mod 10` = **2**, which is the digit printed on the document.

## API

| Method | Returns | Notes |
|---|---|---|
| `characterValue(char)` | `int` 0–35 | Throws `IllegalArgumentException` on invalid input |
| `compute(String)` | `int` 0–9 | The check digit for a field |
| `matches(String, char)` | `boolean` | Field against its printed digit |

## Design decisions

**Utility class, not an instance.** The algorithm is a pure function of its
input. `final class` plus a private constructor that throws prevents both
subclassing and reflective instantiation.

**Filler accepted as a check digit only for an all-filler field.** ICAO
permits an unused optional-data field to carry `<` where a digit would go.
Accepting `<` for a populated field would create a bypass: pad the digit
position with a chevron and skip verification entirely.

**Invalid characters throw rather than returning a sentinel.** A character
outside `[0-9A-Z<]` means the input is not an MRZ at all. Returning 0 would
let malformed input produce a plausible-looking checksum.

## What this does and does not prove

A matching check digit proves a field is **internally consistent** — the
data and its digit agree. It does not prove the document is genuine. A
forger who understands the standard can compute correct digits for
fabricated data. Checksums catch OCR errors and careless alterations; they
are one signal among several, not a verdict.

## Limitations

- Detects most single-character errors, but not all transpositions. Because
  weights repeat every three positions, swapping two characters exactly
  three apart leaves the sum unchanged.
- Modulo 10 means roughly a 1-in-10 chance that a random corruption still
  produces a matching digit. This is why the composite digit and
  cross-field checks matter.

## Tests

`MrzCheckDigitTest` — 27 cases including the canonical ICAO specimen values
`L898902C<`→3, `740812`→2, `120415`→9. Reference values come from the
published standard, not from this implementation, so the tests verify
correctness rather than self-consistency.