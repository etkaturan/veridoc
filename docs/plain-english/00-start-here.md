# What this project does

Imagine a bar that needs to check IDs, or a website that has to confirm
you're old enough to sign up. Someone hands over a passport or an ID card.
Two questions follow:

1. **Is this document real, or has someone tampered with it?**
2. **What does it actually say?** Name, date of birth, when it expires.

Veridoc is software that answers both from a photograph of the document.

## How a person does it

A bartender glances at the card, looks at the photo, checks the birth date,
maybe tilts it to catch the hologram. Seconds, mostly instinct.

## How software does it

It can't rely on instinct, so it works through a checklist:

1. **Find the document** in the photo and straighten it out.
2. **Work out what it is** — passport? ID card?
3. **Read the text** on it.
4. **Check the reading makes sense** — this is the clever part, explained in
   [the self-checking number](02-the-self-checking-number.md).
5. **Work out the answer** — old enough? expired? name matches?

## Being honest about what it can and can't do

This software checks that a document is **internally consistent** — that
everything on it agrees with everything else, and that nothing appears to
have been edited.

It cannot see holograms, or the ink that only shows under ultraviolet
light, or read the security chip inside a modern passport. Those need
hardware this project doesn't use.

So think of it as a very careful, very fast first check — one that catches
bad photocopies, clumsy edits and misreadings — rather than a machine that
declares a document genuine. Knowing the difference matters.

## Where to go next

- [What is that block of text at the bottom?](01-what-is-an-mrz.md)
- [The self-checking number](02-the-self-checking-number.md)
- [Reading messy scans](03-reading-messy-scans.md)
- [Dates and working out age](04-dates-and-age.md)