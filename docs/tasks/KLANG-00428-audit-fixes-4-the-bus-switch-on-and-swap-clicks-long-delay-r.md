# KLANG-00428: Audit fixes 4: the bus (switch-on and swap clicks, long delay rings, compressor, reverb lowpass, vowel, phaser, duck order)

> **Status (2026-11-10): DESIGNED.** From the engine audit (KLANG-00420); maintainer, 2026-11-10: fix all findings, sound changes are fine.
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
| L7a-01 | major | A delay or a reverb switched on while the bus sounds feeds the unit a step | Confirmed: Delay switched on under a held tone: kink 120.6 at 250 ms, burst -22.8 dB |
| L7a-02 | major | A chain swap feeds the arriving chain's room and delay at full level from the first block | Confirmed: A swap into a delay reads exactly like switching it on; the room's first return is at 25.3 ms, inside the 60 ms ramp |
| L7a-03 | minor | A leaving chain that sounds on silence stays at full level for 20 s after the edit that removed it | Confirmed: `pow(-1)` and `plus(0.2)` hold full level 20.36 s after the edit, above 1e-6 until 24.56 s |
| L7a-04 | (read only) | With an authored lookahead the three gain-computer knobs do not glide, and the docs do not say so where the law is stated | not challenged; the auditor's reading in `layer-7a-katalyst-chain.md` |
| L7a-05 | minor | A `parallel` pad ring is not part of the tail question | Confirmed: By reading: a pad without twins never reports a tail |
| L7a-06 | (read only) | The tail scans run on every switch-off, not only in a swap's drain | not challenged; the auditor's reading in `layer-7a-katalyst-chain.md` |
| L7a-07 | (read only) | The delay ring has no size bound, and its allocation and its cleaning run in `configure` | not challenged; the auditor's reading in `layer-7a-katalyst-chain.md` |
| L7a-09 | (read only) | A switched-off lookahead compressor runs its whole detector | not challenged; the auditor's reading in `layer-7a-katalyst-chain.md` |
| L7a-10 | (read only) | The noise seeds follow the builder's line order, which no row pins | not challenged; the auditor's reading in `layer-7a-katalyst-chain.md` |
| L7b-02 | minor | The vowel tables use formant bandwidths (in Hz) as q, so every formant is about 10 to 35 Hz wide | Confirmed, smaller: Widths 10 to 22 Hz measured; the swing is 17.8 dB at wet 1 floor 0, 2.1 dB at the defaults |
| L7b-03 | minor | `reverb.lowpass` is documented as a cutoff in Hz, and it is a linear damping amount | Confirmed: 4 kHz T60: 0.79 s at lowpass 0, 0.81 at 1000, 1.22 at 20000 |
| L7b-04 | minor | The compressor smooths the LEVEL in dB before the gain computer: a dead time on the attack and a release that both hang on the -100 dB floor | Confirmed: Dead time 211 and 188 frames; release 24.5 ms to silence, 128.9 ms to -30 dBFS |
| L7b-05 | minor | The phaser's breakpoint is clamped to 18 kHz whatever the sample rate: below 36 kHz the all-pass can go unstable | Confirmed: At a 32 kHz engine: peak 8.8e307 and 49 non-finite samples; rates under 36 kHz are reachable offline |
| L7b-06 | major | `DelayLine.reset` zeroes the whole ring in one block, whatever its size | Confirmed: 10 s delay: 571 us to clear, 953 us where the drain end and the deactivation share a block; a missed block from about a 32 s delay |
| L7b-07 | note | A duck's life builds its `Ducking` on the audio thread | Confirmed, smaller: Same as L6-06 |
| L7b-08 | (read only) | A chain of ducks depends on the order the cylinders were first used | not challenged; the auditor's reading in `layer-7b-bus-stages.md` |
| L7b-09 | (read only) | `phaser(sweep = 0)` sweeps 1000 Hz, and a center at or below 0 becomes 1000 Hz | not challenged; the auditor's reading in `layer-7b-bus-stages.md` |
| L7b-11 | (read only) | The body bank and the other inserts keep computing on exact silence while a later stage's tail holds the cylinder | not challenged; the auditor's reading in `layer-7b-bus-stages.md` |
| L7b-12 | (read only) | The delay's ring passes through the subnormal range before it reaches zero | not challenged; the auditor's reading in `layer-7b-bus-stages.md` |
| L6-03 | minor | A cylinder's deactivation zeroes its delay ring and its reverb network inside the render block | Confirmed, smaller: The deactivation clear costs 32 us (0.25 s delay) and 70 us (reverb), 1 to 3 % of a block; only long delays are expensive (L7b-06) |
| L6-04 | (read only) | The duck's sidechain is another cylinder's mix before OR after that cylinder's own duck, by rent order | not challenged; the auditor's reading in `layer-6-channel-and-mix.md` |
| L6-05 | minor | With more than `maxCylinders` rented cylinders, the ones past index 254 are never visited by the cleanup | Confirmed: 256 cylinders: 1 never cleaned; 509: 254 never cleaned; negative ids reachable |
| L6-06 | note | The duck builds a new `Ducking` on the audio thread at the start of every life | Confirmed, smaller: One small `Ducking` (and its `knobArgs` array) per life |
| L6-09 | (read only) | Hard right is not exact: `cos(PI / 2)` leaves 6.1e-17 of the voice in the left channel | not challenged; the auditor's reading in `layer-6-channel-and-mix.md` |
| L6-10 | (read only) | The solo ramp's last sample is `from + (to - from)`, not `to`, and the two mix paths round differently | not challenged; the auditor's reading in `layer-6-channel-and-mix.md` |
| N-R1 | new | The first activation of a long delay allocates and zero-fills its ring inside the onset block | found in the disproof pass, `disproof-realtime.md` |
| N-R2 | new | The drain end of a long delay clears its ring twice in one block, after a switch-off that scans it | found in the disproof pass, `disproof-realtime.md` |
| S-02 | new | The compressor's detector reads a very quiet input as quieter than silence | found in the disproof pass, `disproof-sound.md` |
| S-03 | new | The vowel table names no source | found in the disproof pass, `disproof-sound.md` |

## The work

Per finding: read its report and its reproduction (`src/jvmTest/kotlin/audit/` in `audio_be`, `sprudel` or `klang`,
run with `KLANG_PROBE=audit`), fix it, and turn the reproduction's row into an ordinary spec that guards the fix
(mutation-checked). A finding that turns out to need a maintainer decision is not forced: it is listed under
"Decide" below with the options, and the rest of the ticket lands without it. How the fixes are made and reviewed:
`docs/audio-audit/2026-11-10/audit/FIX-BRIEF.md`.

## Decide

(filled while the work runs)

## Acceptance

- Every row above is fixed with a guarding spec, or listed under "Decide" with its reason.
- The suites are green, the corpus render is identical except where a fix changes the sound on purpose, and each such
  change is named in this ticket with the songs it moves.
- The engine health page shows the fixed items green after a new probe run.

## History

- 2026-11-10: created.
