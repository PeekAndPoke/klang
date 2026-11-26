# KLANG-00491: notstrom a morse key for Rune

> **Status (2026-11-26): IDEA.** The maintainer's wish from the rewrite of Seltsamere Dinge for the band, 2026-11-26.

## Why this exists

The maintainer (2026-11-26), of Seltsamere Dinge played by the band: "Rune would play the 'morse' ... so we would
need some kind of 'morse'-instrument."

## What is there today

The song "Noch seltsamere Dinge" has the morse as its own line (tag `morse`) on its own Ignitor instrument, the
`morseKey`: a buzzer behind a narrow band, crushed to 5 bits. Its score (`NOCH_SELTSAMERE_DINGE`) seats Rune at
the marimba for it (`NoteSeat`, "Morse, on the marimba"), so he strikes a bar on every dot and dash, until the key
exists.

## Decide before implementing

- What the key looks like: a straight key (a lever pressed down, the classic telegraph key) on a small stand or on
  his knee, maybe with a lamp or a sounder beside it that lights or clicks with the tone.
- What the player does: one hand on the key, down for as long as the note sounds (the dots short, the dashes long),
  the other hand resting. The note's length matters here, where the marimba only reads the onset.
- The seat: a third kind of `NoteSeat` beside the marimba and the trumpet, or the seat restructure first
  (KLANG-00490: a seat owns its instrument, the cast is a list).
- Whether the sound gets a tell of the key itself (the click of the contact), tuned by ear in the song.

## The work

## Acceptance

## History

- 2026-11-26: created.
