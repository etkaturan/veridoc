# MrzNormalizer

**Package:** `com.etka.veridoc.mrz`

## Purpose

Converts raw OCR output into canonical MRZ text so that everything
downstream sees a consistent representation.

## The core rule

**Normalisation corrects representation, never content.**

| Fixed | Left alone |
|---|---|
| Whitespace, tabs, stray indentation | `O` vs `0` |
| Lowercase letters | `I` vs `1` |
| `«` `»` `≪` `≫` mapped to `<` | `S` vs `5` |
| Line-ending variants | `B` vs `8` |

Substituting ambiguous characters would be guessing at data. Worse, it
could turn a misread document into one whose check digits pass — defeating
the entire verification mechanism. Ambiguous characters must remain wrong
so the checksum can catch them.

## API

| Method | Returns |
|---|---|
| `normalizeLine(String)` | Cleaned single line |
| `normalize(String)` | `List<String>` of cleaned, non-empty lines |

## Design decisions

**`\R` for line splitting.** Matches any Unicode line break — `\n`,
`\r\n`, `\r` and others. Splitting on `\n` alone leaves a trailing `\r` on
Windows-authored input, which then fails the length check by one with a
confusing error. Relevant here because development is on Windows while CI
may run on Linux.

**`StringBuilder` over string concatenation.** Java strings are immutable;
`+=` in a loop allocates a new string per iteration. Negligible at 44
characters, but the habit matters.

**Blank lines discarded.** Scan regions routinely include empty leading or
trailing lines. Removing them lets format detection see the real shape.

**Returns an immutable list.** `.toList()` (Java 16+) rather than
`Collectors.toList()`, so callers cannot modify the result.

**`Locale.ROOT` for case conversion in tests and callers.** Default-locale
`toLowerCase()`/`toUpperCase()` is locale-sensitive: in a Turkish locale
`"I".toLowerCase()` yields `ı`, not `i`. For machine data, always pin the
locale.

## Tests

`MrzNormalizerTest` — 12 cases including an end-to-end check that
deliberately dirtied OCR output normalises to the exact canonical ICAO
specimen and is then correctly detected as TD3.