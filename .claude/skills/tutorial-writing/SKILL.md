---
name: tutorial-writing
description: Use when writing, rewriting or reviewing a Klang tutorial lesson (the `tut_*.kt` files under `src/commonMain/kotlin/pages/docs/tutorials/`, shown in the app's Learn area) - its examples, its prose, its ADSR pictures. Holds how an example is written (multi-line, one player per alternative), how the prose talks about the players, the pictures that follow them, and where the full curriculum rules and their lints live. Not for published texts (`/public-voice`) or songs (`/klang-music-writing`).
---

# Writing a tutorial lesson

A lesson is Kotlin data: a `Tutorial` of `TutorialSection`s, each a list of `Block`s (`Block.Markdown`,
`Block.Code`, `Block.Visual.Adsr`), in `src/commonMain/kotlin/pages/docs/tutorials/tut_<Name>.kt`. Each
`Block.Code` in KlangScript is drawn as a player: Play, Stop, the cycle, the RPM, an editor, Copy.

**The curriculum's rules** (the anti-slop principles: sound before vocabulary, one running example, a
"listen for" moment in every lesson, concepts before use, comments as a narration column, lesson names
instead of "next lesson", the flux ruling on engine internals) live in KLANG-00145 (tutorial curriculum),
section "Principles". Read them before writing a lesson. `src/jvmTest/kotlin/TutorialCurriculumSpec.kt` lints
what can be linted; run it after every change to a lesson.

## Examples (the maintainer, 2026-10-27)

"I would like to have all example codes multiline like the top one. If there are multiple code block add
multiple players instead of the comments."

- **Multi-line, one call per line.** The chain starts on the first line; each further call on its own line,
  indented two spaces; the narration comments in one column (lint-enforced). Only a one-call example stays on
  one line.
  ```
  note("a3 ~ c4 ~")              // the melody's opening leap, stretched long
    .sound("saw")                // the bright voice from ${Tut.theFourWaveforms}
    .adsr(0.01, 0.1, 1, 0.05)    // the default shape, written out: on, hold, off
    .gain(0.5)                   // kept modest, the stages are easier to hear
  ```
- **An alternative is a player of its own**, a `Block.Code` right after the first, never a line behind `//`
  for the reader to swap (lint-enforced: no comment line in an example starts with a call). The A/B moment of
  principle 4 is two players: the reader plays one, then the other.
- **The prose talks about the players as examples**: "**Try it:** play the first example, then the second."
  Editing a number and pressing **Update** stays a way to play with one example.
- The bold **Try it:** and **Listen for:** labels; second person, calm, concrete; no em or en dashes.

## ADSR pictures (KLANG-00371)

- A `Block.Visual.Adsr` follows the players right after it, every one of them up to the first block that is
  not a player (`TutorialSection.playersAfterPictures`): each note they play is a point of light on its curve,
  and the curve takes the shape that plays. So it goes right before the first player of its run, never
  between two.
- A picture only where its first player writes its envelope (the maintainer, 2026-10-27: no pictures before
  the lesson that teaches `adsr`, nor for examples that write none, "these do not define adsrs").
  `Block.Visual.Adsr()` without a value draws what that player writes in `.adsr(a, d, s, r)`; a value of its
  own (`"0.3:0.1:1:0.05"`) must be the same (lint-enforced).
- `envelope = Block.Visual.Adsr.Envelope.Cutoff` follows the lowpass's envelope: its value is required and must
  be the first player's `.lpf(attack = a, decay = d, sustain = s, release = r)`, that call on one line.
- Every player of Sound Design Basics that writes an envelope follows a picture (lint-enforced).
- A stack's layer can be put aside by putting `//` in front of every line of it (trailing commas are fine);
  a lesson that asks for it says "every line" (lint-enforced: each layer put aside still compiles).
