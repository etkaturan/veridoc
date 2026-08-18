# The self-checking number

Here's the problem. A computer reads a photo of a passport. Photos are
blurry, ink smudges, printing fades. What if it reads a `0` where the
document says `8`? Someone's birth date is now wrong, and nobody notices.

The people who designed passports solved this decades ago, and the solution
is genuinely elegant.

## The idea

After every important piece of information, the document prints **one extra
digit** that is calculated from that information.

Your birth date `740812` is followed by the digit `2`. That `2` isn't part
of the date — it's the result of a fixed sum performed on the six digits of
the date.

So when a scanner reads the date, it does the same sum itself and compares.
Match? The reading is almost certainly right. Different? Something is
wrong — either the scanner misread it, or someone edited the document.

## You've seen this before

- **Credit cards.** The last digit is calculated from the others. Mistype
  one number when ordering something online and the website says "invalid
  card" *before* it contacts your bank — it just did the sum.
- **Book barcodes.** The ISBN on a book's back cover works the same way.
- **IBANs.** Two digits near the start of a European bank account number
  exist purely to catch typos before money moves.

The passport version is the same trick with different arithmetic.

## What the sum actually is

Simple enough to do by hand:

1. Turn each character into a number. Digits are themselves. Letters are
   A=10, B=11, up to Z=35. Arrows count as 0.
2. Multiply them by 7, 3, 1, then 7, 3, 1 again, repeating.
3. Add it all up, and keep only the last digit.

For `740812`:

| Digit | 7 | 4 | 0 | 8 | 1 | 2 |
|---|---|---|---|---|---|---|
| times | 7 | 3 | 1 | 7 | 3 | 1 |
| gives | 49 | 12 | 0 | 56 | 3 | 2 |

Total 122. Last digit: **2**. Which is exactly what's printed. ✓

## The really clever part

There's one more of these digits at the very end of the line, and it's
calculated from *everything* — all the information **and** all the other
check digits together.

This is what makes forgery hard. Suppose you change a birth date to look
older. The check digit for the date no longer matches, so you change that
too. Now the date and its digit agree — but that final digit was calculated
from the old version, and it still doesn't match. To get away with it you'd
have to update every one, correctly, understanding the whole system.

It won't stop someone who really knows what they're doing. It reliably
stops smudges, bad photocopies, and casual edits — which is the vast
majority of what actually turns up.

## The honest limit

A passing check means **the document agrees with itself**. It does not mean
the document is real. Someone who understands the standard can invent a
person and calculate perfectly valid digits for them.

That's why this is one check among several, never the final word.