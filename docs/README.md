# Veridoc Documentation

Two sets of documentation, for two different readers.

## [Plain English](plain-english/) — no programming knowledge needed

What this system does and why, explained with everyday comparisons.
Start at [00-start-here](plain-english/00-start-here.md) and read in order.

## [Technical](technical/) — for developers

One document per component: what it does, why it was designed that way,
and the trade-offs involved. Mirrors the package structure of the backend.

| Component | Package | Purpose |
|---|---|---|
| [MrzCheckDigit](technical/mrz-check-digit.md) | `mrz` | ICAO 9303 checksum arithmetic |
| [MrzFormat](technical/mrz-format.md) | `mrz` | TD1/TD2/TD3 layout identification |
| [MrzNormalizer](technical/mrz-normalizer.md) | `mrz` | Cleaning raw OCR output |
| [MrzDate](technical/mrz-date.md) | `mrz` | Two-digit year interpretation, age calculation |
| [MrzData](technical/mrz-data.md) | `mrz` | Parsed MRZ contents |
| [MrzVerification](technical/mrz-verification.md) | `mrz` | Per-field checksum results |

## Keeping these honest

A document that describes code that no longer exists is worse than no
document — it actively misleads. When a component changes, its document
changes in the same commit.