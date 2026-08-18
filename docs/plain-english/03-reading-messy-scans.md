# Reading messy scans

Text-recognition software is good, not perfect. Feed it a photo taken in a
dim room at a slight angle, and you get back something like:

p< utoeriksson<<anna<maria<
l898902c«3uto7408122f1204159...

Lowercase where it should be capitals. Stray spaces. A `«` instead of a
`<`. Before anything else can happen, this has to be tidied up.

## The rule we follow, and why it matters

**We fix how things are written. We never fix what they say.**

Safe to correct — none of these change the information:

- Remove spaces and tabs
- Make everything uppercase
- Turn `«` and `≪` into `<`

Never corrected, even though we could guess:

- `O` versus `0`
- `I` versus `1`
- `S` versus `5`

That second list looks like a missed opportunity. It's the opposite.

Say the software sees `O` where the document really has `0`. If we "helpfully"
correct it, we've now invented data. Worse — remember the
[self-checking number](02-the-self-checking-number.md)? Our correction
might make the sum come out right, so the error passes every check and
nobody ever finds out.

By leaving the wrong character alone, the sum fails, and the system reports
honestly: *I couldn't read this properly, take another photo.*

**Being unhelpful in the right place is what makes the system trustworthy.**

## Working out what kind of document it is

Once cleaned, the shape of the text identifies the document — no
understanding required, just measuring:

| Shape | What it is |
|---|---|
| 3 lines, 30 characters each | An ID card |
| 2 lines, 36 characters each | An older ID card or visa |
| 2 lines, 44 characters each | A passport |

Like telling A4 from A5 paper with a ruler. You don't need to read a word.

## Why we're strict about the counting

If a line comes out at 43 characters instead of 44, the software rejects
it outright rather than trying to cope.

That sounds harsh, but consider what "coping" would mean. The whole system
depends on knowing that the birth date sits at positions 14 to 19. If one
character went missing early in the line, everything after it shifts by
one — and the software would confidently read the wrong six characters as
the birth date.

Better to say "I couldn't read this, please try again" than to give a
confident wrong answer. In a system that checks identity, a clear failure
is always safer than a quiet mistake.