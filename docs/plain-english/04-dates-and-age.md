# Dates, and working out someone's age

Passports store dates with only two digits for the year. A birth date reads
`740812` — the 12th of August, year 74.

Which 74? 1974 or 2074?

## The Y2K problem in miniature

This is the same shortcut that caused the year-2000 panic: software stored
`99` for 1999, then `00` arrived and computers couldn't tell 2000 from 1900.

Passports still do it, because there simply isn't room for more characters
in a fixed-width format. So the software has to work out the century itself.

## The rules

For a **birth date**, one rule solves it: *nobody has been born in the
future.* If reading `74` as 2074 puts the birth after today, it must be
1974.

For an **expiry date**, the logic flips. Documents expire a few years after
they're issued, not decades before. If `05` as 2005 would mean the passport
expired over twenty years ago, it more likely means 2105.

Neither rule is perfect. The birth-date rule would misjudge someone aged
over 100 — a real limitation, written down honestly rather than hidden.

## Working out age is fiddlier than it looks

Born 12 August 1974, and today is 18 August 2026. How old?

The tempting shortcut: count the days, divide by 365.25, done.

It's wrong. Not always — but wrong on and around birthdays, which is exactly
when age checks matter. Leap years, months of different lengths, and the
rounding all conspire to put someone a day either side of eighteen.

So the software counts properly: how many complete years have passed,
taking into account whether this year's birthday has happened yet.

- Born 12 August 1974, today 11 August 2026 → **51**
- Born 12 August 1974, today 12 August 2026 → **52**

One day's difference, one year's difference. Get that wrong and you turn
away an adult, or serve a minor.

## Testing something that depends on today

There's a subtle trap here. If the software asked the computer "what's
today's date?" whenever it calculated an age, we could never properly test
it — the answers would change every day, and a test that passes now might
quietly break years later.

So instead, the date is always *handed to* the calculation. That lets us
ask "what would this say on the 11th of August 2026?" and check the answer
is right, permanently. A small design choice that makes the whole thing
verifiable.