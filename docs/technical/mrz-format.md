# MrzFormat

**Package:** `com.etka.veridoc.mrz`

## Purpose

Identifies which of the three ICAO 9303 layouts a set of MRZ lines uses.
Format determines every field offset downstream, so this must be settled
before any parsing.

## The layouts

| Format | Lines | Chars/line | Total | Typical use |
|---|---|---|---|---|
| TD1 | 3 | 30 | 90 | ID cards (German Personalausweis) |
| TD2 | 2 | 36 | 72 | Older ID cards, visas, residence permits |
| TD3 | 2 | 44 | 88 | Passports |

Dimensions are unique across the three, so shape alone identifies format.

## API

| Method | Returns |
|---|---|
| `lineCount()` / `lineLength()` / `totalLength()` | `int` |
| `description()` | Human-readable document category |
| `forDimensions(int, int)` | `Optional<MrzFormat>` |
| `detect(List<String>)` | `Optional<MrzFormat>` |

## Design decisions

**Enum with fields, not bare constants.** Each constant carries its
dimensions and description, so the layout data lives with the type rather
than in a lookup table elsewhere. The three instances are created at class
load and cannot be duplicated.

**`Optional` rather than `null` or a `UNKNOWN` constant.** An unrecognised
shape is not a fourth format; it is the absence of a format. `Optional`
puts that in the type signature so callers cannot ignore it.

**Exact lengths, strictly enforced.** A 43-character line is not a
short TD3 — it is a failed OCR read. Padding or accepting near-misses would
shift every subsequent field offset by one and produce confidently wrong
data. Rejecting early is what makes downstream parsing trustworthy.

**Ragged input rejected.** All lines must be the same length. Differing
lengths mean the OCR region was wrong or lines were merged.

## Tests

`MrzFormatTest` — 11 cases covering each format's dimensions, correct
detection, and rejection of ragged lines, unknown lengths, unknown line
counts, and empty or null input.