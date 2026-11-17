# KLANG-00452: A scale or a transpose gave a drum sample 440 Hz: an event with no note keeps its frequency

> **Status (2026-11-17): DONE.** Both sprudel fixes committed (8622cfe4a, 420c1b682), reviewed clean and mutation-checked; the Kokon line waits in the maintainer's working copy.

## Why this exists

The maintainer (2026-11-17), on the song Kokon: "I need to specify .n(1) to get the 'normal' hats sounds which is odd
as this is not the case in any other song", and "The hats seem to be cut in the high frequencies and i do not know why."

Kokon puts one `.scale("c3:minor")` on its whole `arrange(...)`, drums included. In sprudel an event with no note
and no index got 440 Hz from the scale, because `Tones.noteToFreq("")` is 440 Hz, and the backend pitches a sample by
its frequency over the sample's own pitch (261.63 Hz for a bank sample):

- without `.n()` the hats played about a major sixth too high;
- with `.n(1)` the index became scale step 1, the note D3, and the hats played about ten semitones too low, which
  is the missing top end;
- the snare's `.freq(snareHz)` was replaced by 440 Hz.

Other songs keep their drums outside any scale, so they never met it.

## The work

1. **Done (2026-11-17, `8622cfe4a`): `.scale()` and `.note()` without arguments.** `resolveNote`
   (`lang_tonal_note.kt`) leaves the frequency as it is when there is no note to resolve: an unset one stays unset
   (a sample plays at its own pitch) and one set by `freq()` survives. Two specs in `VoiceDataSpec`; the spec that
   pinned 440 Hz for an empty voice now expects no frequency.
2. **Done (2026-11-17, `420c1b682`): `transpose()`.** An event with no note and no frequency is returned as it is
   (`lang_tonal_scale.kt`); one with only a frequency is shifted as before. `scaleTranspose()` had the issue only
   through its fallback to `transpose()` when no scale is set, and the same guard covers it. Four specs in
   `LangTransposeSpec` and `LangScaleTransposeSpec`.
3. **Kokon:** `.n(1)` removed from `hats`. The line sits in the maintainer's working copy of the song, uncommitted
   with the maintainer's own edits.

## What still stands

- `n` on a sample under a scale is a scale step, so it pitches the sample and cannot pick a variant there. Kept as it
  is; the question is on the incident line of 2026-11-17 for the meeting.
- A value that is no note name still reads as 440 Hz: KLANG-00453 (a value that is no note name).
- Kokon's hats and snare were tuned by ear against the wrong pitches and want another listen. Nobody has listened yet.

## Acceptance

- `:sprudel:jvmTest` green; `:sprudel:jsTest` not run.
- Mutation-checked: both `VoiceDataSpec` specs (2 mutants) and the four transpose specs (4 mutants), all red for the
  expected assertion, files restored byte-identical.
- Reviewed: each step one blind round, a code and an audio reviewer at rung 1, both clean. The audio reviewer read
  every `.scale(`, `.transpose(` and `.scaleTranspose(` in the built-in songs, jingles, tutorials and frozen copies:
  only Kokon changes its sound. That is from reading the code, no render was made.

## History

- 2026-11-17: created.
- 2026-11-17: DONE. Both sprudel fixes committed (8622cfe4a, 420c1b682), reviewed clean and mutation-checked; the Kokon line waits in the maintainer's working copy.
