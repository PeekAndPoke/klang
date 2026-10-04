# Sprudel: `beats(n)`, a tempo-following duration in seconds

> **Later (2026-10-03):** the sprudel tremolo's `sync` is `rate` on every surface (`tremolo(depth, rate, shape)`,
> the reader `tremolo.rate`, the slot key `tremolo.rate`), see
> `docs/tasks-archive/2026-10/20261003-tremolo-rate-naming-parity.md`. Sprudel no longer mirrors Strudel
> (`sprudel/README.MD`), so Strudel's `tremolosync` is no reason to bring the old name back.

Status: **DONE 2026-10-01.** Asked for by the maintainer after `delay("1/8".div(cps))` turned out to
be a slow-down by 8 in mini-notation, not a fraction.

## Why

The sound doors take a time in **seconds** (`delay.time`, the envelope stages, `duck`'s attack; `late` and `early` take cycles), and
seconds stay the unit (maintainer, 2026-10-01; the backend never learns about cycles). A delay that follows
the tempo is written today as `pure(1/8).div(cps)` or `pure(60).div(bpm)`. That works, but it reads like
arithmetic, and the obvious string spelling `"1/8".div(cps)` is silently wrong: in mini-notation `/` is
slow-down, so `"1/8"` is the value 1 stretched over 8 cycles.

## What

`beats(n, base = 4)`: the length of `n` beats in seconds, as a **signal** like `cps`, so it follows every
tempo change while playing. `base` is how many beats make a cycle (maintainer, 2026-10-01: a parameter, not a
constant, so other meters work too, `beats(1, 3)` in three).

```
s("sd").delay(0.3, beats(0.5), 0.4)            // an eighth-note echo at any tempo
s("sd").delay(0.3, beats("<0.5 0.75>"), 0.4)   // patterned: eighth, then dotted eighth
s("bd sd sd").delay(0.3, beats(1, 3), 0.4)     // three beats to the cycle
```

- Value: `n / (base * cps)`. The default 4 is the same 4/4 that `bpm` assumes; it lives once in
  `lang_continuous_clock.kt` (`DEFAULT_BEATS_PER_CYCLE`) and `bpm` reads it too. The script door's default is
  the literal `4` (`/dsl-design` §3: a script-door default must be a safe literal).
- `base` is a plain number, not a pattern. Coerced, not asserted: 0 or less, or non-finite, counts in fours.
- `n` is patternable. A plain number takes the static path (one `ContinuousPattern`); a pattern keeps its own
  rhythm and rests, and each value is multiplied by the seconds per beat of the query's tempo.
- `n` is not coerced: `beats(-1)` is a negative duration, as `pure(-1).div(cps)` is today.
- One implementation is both doors: the sprudel function is `@KlangScript.Function`, KSP registers it.
  KlangScript does not mix positional and named arguments: `beats(1, 3)` or `beats(n = 1, base = 3)`.

## Not in this step

- **A rate twin for the LFO doors.** `beats` is a duration. `tremolo`'s `sync` is a rate in Hz, so
  `tremolo(0.6, beats(0.5))` gives 0.25 Hz at 120 bpm, not a wobble every half beat; that needs
  `pure(1).div(beats(0.5))`. A rate helper (name open) is the next step if the maintainer wants it.
- `cycles(n)` (`n / cps`): not built (maintainer, 2026-10-03). It is `beats(n, 1)`, one beat to the cycle, and
  `late` / `early` take cycles already; a second name for the same thing waits until someone misses it.

## Also in this change

- The `cps` and `bpm` KDoc examples said "Dalay".

## Guard

`LangBeatsSpec`: both doors (Kotlin and script, positional and named), the default tempo, a tempo change
between two queries, `beats(1)` against `bpm`, a patterned `n` across cycles and with a rest, `base` and its
coercion, and `beats` as a delay time written into `delay.time` over 12 cycles at two tempos.
Mutation-checked (light tier, five mutations, each red): tempo ignored, the base factor dropped, the
pattern combiner a no-op, `base` ignored, the coercion removed.

## Review

Round 1 (blind, one coding reviewer; no audio reviewer, no voice data, DSP or wire path is touched): no
CRITICAL or MAJOR, so the loop ended there. Three MINORs:

1. The KDoc said every door that takes a time takes seconds; `late` and `early` take cycles. Fixed, here and
   in the KDoc.
2. `rangeMin` / `rangeMax` from an enclosing `range` reach the two paths differently (the static path maps
   `n * secondsPerBeat`, the pattern path only the seconds per beat). Not fixed: `cps` and `bpm` share the
   same mapping, and range-mapping a duration is not a use anyone has.
3. No row for a named call that leaves `base` out. Added (`beats(n = 0.5)`). The reviewer's failure scenario
   (a non-literal default makes that call fail) does not happen here: with `KSP` re-run on a
   `DEFAULT_BEATS_PER_CYCLE` default the thunk is gone and the call still works, because a TRAILING omitted
   parameter falls back to the Kotlin default. The guardrail bites on a skipped parameter in the middle. The
   row stays as plain coverage. Lesson for mutation runs on a KSP default: KSP is incremental, so force it
   (`:sprudel:kspCommonMainKotlinMetadata --rerun`) or the mutant is never generated.
