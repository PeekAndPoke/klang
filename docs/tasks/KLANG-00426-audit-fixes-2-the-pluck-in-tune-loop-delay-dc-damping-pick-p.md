# KLANG-00426: Audit fixes 2: the pluck in tune (loop delay, DC, damping, pick position)

> **Status (2026-11-11): PAUSED.** The decisions pass of 2026-11-11 landed; what is left is named in the ticket.
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


## Decisions pass (2026-11-11)

**L3-08 built: a real pick position, default 0.28** (option 1, the second round's default). `KarplusString.excite` fills
the whole string `L` with white noise (one `nextDouble` per sample, so a pluck now draws `L` values at every pick
position) and runs the comb `y[j] = (x[j] - x[(j - d) mod L]) / sqrt(2)`, `d = max(1, round(0.5 * pickPosition * L))`,
in place and allocation free (the circle walked in `gcd(L, d)` cycles). 0 is the bridge (thin), 1 the middle (every even
harmonic gone); 0.28 picks at about a seventh of the string. A comb of white noise doubles its power at every `d`, so
the level no longer moves with the knob (it moved by about 10 dB as a burst length). The comb's circular sum is zero, so
the excitation carries no DC (L3-06 holds by construction; the separate mean pass is gone). The tuning (L3-01) is
untouched: `PluckPitchSpec` green.

The default moved in every place that states it: `IgnitorDsl.Slots.pickPosition` (the one home; the node, the Kotlin
door, `IgnitorDefaults` and the script slot read it), the KDoc of the Kotlin and script builders, `Ignitor.slot`, the
sprudel doors (`sndPluck`, `sndSuperPluck`: the `@param` and the overview bullet) and the two sprudel editor tools'
fallback for an absent argument (0.5 to 0.28; their named presets keep their own values). `audio/ref/voice-synthesis.md`
and `audio/MEMORY.md` say the new meaning.

Guards: `KarplusStringSpec` (the draws and the comb against a plain modular formula at ten (L, pick) pairs, NaN and out
of range included; the spectrum is the noise's times `2 sin(pi k d / L) / sqrt(2)` harmonic by harmonic at four picks;
the level within 0.25 dB of plain noise at six picks; the DC row renamed), `KlangScriptIgnitorSlotTest` (the default
0.28, moved from 0.5). `PluckDcSpec`'s bound moved from 0.01 to 0.04 with its reason in the spec: the measurement's own
floor (a decaying tone's mean over 0.1 s) reaches 0.017 on one superpluck seed with the new excitation; the DC it guards
read 0.08 to 4.26. Mutants (all KILLED for the expected reason): the comb taken out, the comb's gain 1, the distance over
the whole string instead of half, the comb not circular (DC), the default back to 0.5.

Pinned renders moved by this change: `BuiltInVoiceMatrixSpec` `pluck`, `ks`, `string`, `superpluck` (both rows each)
and both stiffness pins.

Probes: the click probe's pluck rows (the onset is the excitation itself, a hard noise attack by nature) read the
level after onset 0.0100 to 0.0224 (pluck) and 0.0072 to 0.0103 (superpluck): the default pluck is louder, about +7
dB at its onset (the whole string carries noise now). Cost: pluck 5.56 to 5.05 ns, superpluck 8 strings 46.7 to 43.5
(the whole after run was 10 to 20 percent faster on unchanged rows; ratio to the sine 2.32 to 2.27, 19.5 to 19.6).

The corpus, my own before (the tree as found, `5e39f1c12`, before the first edit) against after (`runSongBenchmark
--args=corpus`, 48 kHz, a temporary RMS print in a throwaway worktree's `CorpusCheck`); no song text changed between the
runs. Each move is attributed by five more runs, each with ONE change of the pass taken out (the pick, the flank, the
perlin hash, the noise rate scaling, the jitter rule): a change is named where taking it out moves the song's hash. Songs moved by this change: Drunken Synthlor (by it alone; RMS -19.752 to -18.926 dB, peak 0.8459 to
0.8806), Die Kirschblüte (with the perlin and the noise rounding; -20.423 to -20.182, peak 0.7467 to 0.7651), Greensleeves
Chant (with the perlin and the noise rounding; -24.040 to -23.917, peak 0.4530 to 0.4758), The Synthsale Piper's Farewell
(with the perlin and the noise rounding; -27.840 to -27.582, peak 0.3498 to 0.3545). The full table is in KLANG-00430's
section of this pass.

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
- 2026-11-11: PAUSED. The decisions pass of 2026-11-11 landed; what is left is named in the ticket.
