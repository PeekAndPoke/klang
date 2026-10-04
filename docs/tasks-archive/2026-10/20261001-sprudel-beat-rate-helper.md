# Sprudel: `beatRate(n, base = 4)`, a tempo-following rate in Hz

> **Later (2026-10-03):** the sprudel tremolo's `sync` is `rate` on every surface (`tremolo(depth, rate, shape)`,
> the reader `tremolo.rate`, the slot key `tremolo.rate`), see
> `docs/tasks-archive/2026-10/20261003-tremolo-rate-naming-parity.md`. Sprudel no longer mirrors Strudel
> (`sprudel/README.MD`), so Strudel's `tremolosync` is no reason to bring the old name back.

Status: **DONE 2026-10-01.** The rate twin of `beats`
(`docs/tasks-archive/2026-10/20261001-sprudel-beats-helper.md`), asked for by the maintainer the same day.

## Why

`beats(n)` is a duration in seconds, right for `delay.time`. The LFO doors take a rate in Hz (`vibrato(rate)`,
`phaser(rate)`, the tremolo's `sync`), and a duration there is wrong without an error: `tremolo(0.6, beats(0.5))`
is 0.25 Hz at 120 bpm, not a wobble every half beat. Today the right spelling is `pure(1).div(beats(0.5))`.

## Name

`beatRate` (maintainer, 2026-10-01): the Hz parameters are called `rate`, so the helper's name says where it
goes. Rejected: `beatTime` (reads as a duration, the opposite), `beatsHz`, `beatFreq` (also an acoustics term
for the beating of two detuned tones), `invBeats` (names the maths, not the music).

## What

`beatRate(n, base = 4)`: one cycle every `n` beats, in Hz, as a signal that follows every tempo change.
Exactly `pure(1).div(beats(n, base))`: `base * cps / n`.

```
note("c3").s("saw").tremolo(0.6, beatRate(0.5))   // one wobble every half beat
note("c3").vibrato(beatRate(1), 0.3)              // one vibrato cycle per beat
```

- Same argument as `beats`, so a value moves between a delay and a tremolo by changing the name only.
- `base` as in `beats`: beats per cycle, default the literal `4`, coerced (0 or less, or non-finite, counts in
  fours). One implementation behind both helpers; only the last step differs.
- `n` is not coerced, as in `beats`: `beatRate(0)` is not a finite rate, and no door turns it into one.
  Read from the code (round 1): the tremolo's slot writer drops it (`_classic_slot_params.kt`), so the slot
  default 0 Hz holds; the vibrato's field crosses the wire as Infinity and the renderer's phase wrap returns 0
  (`DspUtil.wrapPhase`), so no vibrato; the phaser's `rate` setter ignores a non-finite value
  (`PhaserCore.rate`), so it keeps its last rate. Nothing crashes, no NaN reaches the output.
- `n` is patternable, the same two paths as `beats` (a number is a signal, a pattern keeps its rhythm).

## Not in this step

- The sprudel tremolo calls its rate `sync`, the one LFO door that does not say `rate`. The rename
  `sync` to `rate` is the next task (the sprudel door, the `tremolo.sync` reader, the `classic()` slot key,
  the tremolo editor tool, whose label "Rate (cycles)" is wrong too, and `DrunkenSailor.kt`).
- Feeding a sprudel signal into an Ignitor `freq` (`Osc.sine(beatRate(4))`) is a different DSL, not asked.

## Guard

`LangBeatsSpec` grows the `beatRate` rows: both doors, the default tempo, a tempo change, `base`, a pattern,
one over `beats` across several `n` and tempi, `beatRate(0)` and the tremolo slot that drops it, and
`beatRate` written into a tremolo rate.

Mutation-checked (light tier, each red): the rate returned as seconds (6 rows), the pattern path skipping the
conversion (the pattern row), the static path skipping it (5 rows), `n` coerced to a finite value (the
`beatRate(0)` row).

## Review

Round 1 (blind, one coding reviewer; no audio reviewer, the change writes no new wire field): no CRITICAL or
MAJOR, so the loop ended there. Three MINORs, all applied:

1. The KDoc said a door drops `beatRate(0)` as unset; true for the tremolo only. Rewritten from the code (the
   `n` bullet above), each claim read before it was written.
2. No row pinned `beatRate(0)`. Added: it is `+Infinity`, and the tremolo's wire slot has no `tremolo.sync`.
3. The Guard section named a test that does not exist (an identity with `pure(1).div(...)`); the spec checks
   `beatRate * beats == 1`. Wording fixed.
