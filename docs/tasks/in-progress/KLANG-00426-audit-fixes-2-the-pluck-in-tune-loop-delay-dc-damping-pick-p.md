# KLANG-00426: Audit fixes 2: the pluck in tune (loop delay, DC, damping, pick position)

> **Status (2026-11-11): IN PROGRESS.** Decisions pass: building what the maintainer decided on 2026-11-11.
>
> **Part of:** KLANG-00353 (The Motor: correct at the edges)

## Why this exists

The engine audit (KLANG-00420) found these. Every row is traced in `docs/audio-audit/2026-11-10/audit/` (the layer
report named by the id's layer, and the `disproof-*.md` that measured it); the index is `SUMMARY.md` there. The
maintainer's decision (2026-11-10): "create tickets and fix all of the findings. Sound changes are fine, for example
for the pluck."

## The findings

| Id | Severity now | Finding | Verdict and measurement |
|---|---|---|---|
| L3-01 | critical | The pluck is flat: the loop filter's delay is not taken out of the delay line | Confirmed: Measured in the engine by two methods agreeing within 0.001 cent there: the default pluck is -15.75 cents at A4 and -36.73 at C6; brightness 1 reads 0.00 |
| L3-06 | minor | A pluck carries a random DC offset that outlives its tone | Confirmed: After 1.9 s the mean is 0.08 to 1.84 times the AC level (pluck), 0.27 to 4.26 (superpluck) |
| L3-07 | (read only) | The pluck's linear-interpolated read makes the damping of the highs depend on the note | not challenged; the auditor's reading in `layer-3-sources.md` |
| L3-08 | (read only) | `pickPosition` sets the burst's length, not a pick position, and moves the level by about 10 dB | not challenged; the auditor's reading in `layer-3-sources.md` |

## The work

Per finding: read its report and its reproduction (`src/jvmTest/kotlin/audit/` in `audio_be`, `sprudel` or `klang`,
run with `KLANG_PROBE=audit`), fix it, and turn the reproduction's row into an ordinary spec that guards the fix
(mutation-checked). A finding that turns out to need a maintainer decision is not forced: it is listed under
"Decide" below with the options, and the rest of the ticket lands without it. How the fixes are made and reviewed:
`docs/audio-audit/2026-11-10/audit/FIX-BRIEF.md`.

## Decide

### L3-07: the linear read damps the highs by an amount that depends on the note

Measured on a replica of the loop after the L3-01 fix (brightness 1, the energy above 5 kHz re the whole in the window
0.2 to 0.4 s): 438.4 Hz (read fraction 0.49) -65.8 dB, 440.4 Hz (fraction 0.99) -3.6 dB, 436.4 Hz -3.8 dB, 1046.5 Hz
-58.6 dB, 1040 Hz -64.8 dB. Two notes 2 Hz apart: one rings bright, the other loses its top within tens of milliseconds.
The tuning of L3-01 is not touched by it (the read's phase is in `loopDelayOf`), only the damping of the highs.

Options:

1. A first-order allpass (Thiran) for the fraction, the classic Karplus-Strong tuning read: lossless, so the damping is
   the one-pole's and the feedback's alone (the replica: -0.8 dB at every note above). Costs a state pair per string and
   a division per sample while the delay moves (drift, vibrato, FM). The catch: when the delay's whole part changes under
   modulation the allpass state no longer fits and clicks, which needs a state correction or a crossfade (Van Duyne,
   Jaffe, Scandalis and Stilson, 1997). A redesign of the read, not a small change.
2. A four-point (cubic Lagrange) read: stateless and safe under modulation, its gain at 6 kHz 0.99 at worst (linear:
   0.92), at 12 kHz 0.88 (linear: 0.71). The note dependence shrinks but stays; 4 reads and about 12 operations per
   sample instead of 2 and 3.
3. Keep the linear read and say it: at brightness near 1 the ring of the highs follows the note.

Recommendation: 2 now (small, safe, a clear gain), 1 only if a listening round asks for a lossless string at fixed
pitch. `loopDelayOf` then models the new read instead of the linear one.

### L3-08: `pickPosition` sets the burst's length and place, and moves the level by about 10 dB

The door texts already say what it does since KLANG-00417 ("the pluck's noise burst"); the node's KDoc in `Ignitors.kt`
said "pick position modeling" and now says what it does. What is left is the knob's meaning and the level. A shorter
burst of white noise has the same flat spectrum with less energy, so the knob is mostly a level knob.

Options:

1. A real pick position: the burst over the whole line, then the comb `x[j] - x[j - round(beta * L)]` (circular over
   the line, which doubles the power exactly at every beta, so the level is steady after a fixed `1 / sqrt(2)`), with
   `beta = 0.5 * pickPosition` floored at one sample: 0 at the bridge (thin), 1 at the middle (hollow, only odd
   harmonics). A small change in `excite`. But: the default 0.5 becomes beta 0.25 (every fourth harmonic missing), a new
   timbre for every pluck at its default; the draw count becomes the line length at every setting (it is the burst's
   length today), so every later draw of a pluck voice moves; the doors, the script knobs, the node KDoc and
   `audio/ref/voice-synthesis.md` say the new meaning. Whether the default stays 0.5 (beta 0.25) or moves (a guitar is
   picked near beta 0.1 to 0.2) is a matter of taste.
2. Keep the burst knob and hold the level: scale the burst by `sqrt(L / burstLen)`. One line; the knob then changes
   only the burst's place and length, which the ear hardly hears.
3. Keep both and document the level (the door texts already name the burst).

Recommendation: 1, with the default decided by ear (0.5 keeps every song's number but changes every pluck's colour).

### Decided (maintainer, 2026-11-11)

"All as you suggest, no listening pairs needed."

- **L3-08:** option 1, a real pick position. The default stays 0.5 (every song keeps its number; every pluck at its
  default changes colour). Owed.
- **L3-07:** the cubic read landed in KLANG-00430; the lossless allpass read is not built.

### Decided, second round (maintainer, 2026-11-11)

- **L3-08, the default:** "can we choose a different default position to not lose the harmonic? If yes please choose
  a different sensible default." Every pick position takes some harmonics out (the comb); the default moves from 0.5
  to 0.28 (beta 0.14, a seventh of the string, where a guitar is picked): the first harmonic that goes weak is the
  7th, where 0.5 took out the 4th, 8th and 12th. Owed.

## Notes (the fixer, 2026-11-10)

- L3-01 and L3-06 fixed, sound changes on purpose. The corpus before against after moves four songs, each through its
  plucks: Die Kirschblüte (`Sakura`, the koto), Greensleeves Chant (the lute), Drunken Synthlor (`DrunkenSailor`,
  `sndPluck`) and The Synthsale Piper's Farewell (`IrishLament`, the fingerpick and the contrabass). No song was tuned
  around the flat pluck (IrishLament's `detune(0.05)` is a second string for width, not a correction); none was edited.
- L3-07 and L3-08: see "Decide".

## Acceptance

- Every row above is fixed with a guarding spec, or listed under "Decide" with its reason.
- The suites are green, the corpus render is identical except where a fix changes the sound on purpose, and each such
  change is named in this ticket with the songs it moves.
- The engine health page shows the fixed items green after a new probe run.

## History

- 2026-11-10: created.
- 2026-11-10: IN PROGRESS. Started.
- 2026-11-10: PAUSED. L3-01 and L3-06 fixed and landed; L3-07 (the read's interpolation) moves to KLANG-00430 with the sample player's kernel; L3-08 (what pickPosition means, its default by ear) waits for the maintainer.
- 2026-11-11: IN PROGRESS. Decisions pass: building what the maintainer decided on 2026-11-11.
