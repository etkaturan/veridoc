# The strange text at the bottom of your passport

Open a passport to the photo page. At the bottom you'll see two lines of
capital letters, numbers, and rows of `<<<<<` arrows:

"P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<L898902C<3UTO7408122F1204159ZE184226B<<<<<10"

It looks like nonsense. It isn't. It's called the **machine readable
zone**, and it's the same information printed above it — name, nationality,
document number, birth date, expiry date — written in a format designed for
machines rather than people.

## Why it exists

Before it, a border officer typed your details in by hand. Slow, and
riddled with typos. Every country agreed on one standard layout so any
scanner anywhere can read any passport.

## What the arrows are for

The `<` characters are just padding, like pressing space to fill a form
field. Every part of the zone has a **fixed** number of character slots. The
name field always has exactly 39, whether your name is short or long. Any
unused slot gets an arrow.

That fixed width is what makes it machine-readable. The computer doesn't
search for the birth date — it knows the birth date is always characters 14
to 19 of the second line. Always. On every passport in the world.

Think of a paper form where every box is exactly the same size, so a
scanner can be told "the date of birth is the fourth box from the left" and
never has to guess.

## Reading the example

- `P` — this is a passport
- `UTO` — the issuing country (Utopia, a fictional code used in examples)
- `ERIKSSON<<ANNA<MARIA` — surname, then a double arrow, then given names
- `L898902C` — the passport number
- `740812` — born on 12 August 1974
- `F` — sex
- `120415` — expired on 15 April 2012

The double arrow `<<` separates surname from first names; a single `<`
separates one first name from the next. So the computer knows the surname
is Eriksson and the given names are Anna and Maria — without needing to
understand names at all.

## And there's a trick hidden in it

Look at the numbers scattered through the second line. Some of them aren't
data — they're built-in error detectors. That's the
[next document](02-the-self-checking-number.md), and it's the cleverest
part of the whole system.