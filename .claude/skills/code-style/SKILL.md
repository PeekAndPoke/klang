---
name: code-style
description: Use when someone asks to apply code style rules, check code style, clean up code style, or follow project code conventions.
---

# Code style

The Klang code conventions. Apply them whenever writing or editing code. The numbers are stable because other files
cite them (`§21`): a retired rule leaves a gap, a new rule takes the next free number.

## 1. Braces always

`if`, `else`, `for`, `while` and `when` bodies get `{ }`, one-liners too.

- Exception (2026-08-28): an `if`/`else` or a `when` arm whose value is consumed may stay brace-free on one line:
  `phase = if (pm != null) phase.wrapPhase(1.0) else phase.smallNumFastMod(1.0)`.
- A statement `if` (value discarded) always gets braces. Once any branch of an expression `if` needs braces or
  several lines, all its branches get them (no `} else 0.0`).

## 2. Blank lines between steps

A blank line before and after an `if`/`for`/`when` block that is not the first or last statement of its block, so
each branch of an early-return ladder reads as its own step.

- Declarations (2026-08-28): a blank line after a group of `val`/`var`, and before a declaration that follows other
  statements. Consecutive declarations stay together.
- `return` (2026-08-31): a blank line before every `return`, unless it is the only statement in its block. A comment
  attached to the `return` stays attached; the blank goes above the comment.

```kotlin
val step = readParam(rate, freqHz, ctx) * PERLIN_STEP
val end = ctx.offset + ctx.length

if (bConst) {
    fillConst(buffer, ctx)

    return
}

for (i in ctx.offset until end) {
    buffer[i] = step
}
```

## 3. Files, and where helpers live

- Flat directories: sources sit at the module's package root (`src/commonMain/kotlin/`), never under
  `io/peekandpoke/...`. A sub-package is one flat directory (`runtime/`, `intel/`).
- A file holding a class, object or interface is named after it in PascalCase (`ShapingFuncs.kt` for `ShapingFuncs`).
- A file of functions only is `lower_case.kt`, unique and descriptive (never `_utils.kt`). Beside class files it
  takes a `_` prefix so it groups at the top (`_staff_pos_helpers.kt`); in a folder of function files only, no prefix.
- Generic helpers (maintainer, 2026-10-07): a helper that knows nothing of its module's domain (list compaction, a NaN
  guard, a numeric law, a byte conversion) lives in the module's `utils/`, package `io.peekandpoke.klang.<module>.utils`,
  never `util`, `helpers` or `common`. A helper of one feature stays beside it as `_<feature>_helpers.kt`. Each
  `utils` helper has a spec of its exact behaviour; a hot-path helper taking a lambda is `inline`.
- The `common` module is itself a module of helpers: no `utils/`, grouped by topic (`math/`, `strings/`), each helper
  with its spec (2026-10-09).

## 4. No duplication

A utility, a data class (`ResolvedShape`) or a piece of lookup logic (shape resolution) lives in one place and is
imported, never copied as a `private` twin. In `audio_be`: numeric guards in `utils/numerical_safety.kt`, phase wraps
in `utils/phase_wrap.kt`, fast math in `utils/fast_math.kt`.

## 5. No boxed types in high-performance code

Inside the performance boundary, never `Long`, `ULong`, `Byte`, `UByte`, `Short`, `UShort` or `Char`. Use `Int`
(counts, indices, char codes, bytes masked with `and 0xFF`) or `Double` (time). A `Long` from an external API is
converted where it enters.

The boundary (maintainer, rules retro 2026-10-23): `audio_be`; `audio_bridge` with the wire codecs
(`WireCodecSupport` and the code `audio-wire-codec-ksp` generates); sprudel's timing core (the query path:
`sprudel/src/commonMain/kotlin/pattern/`, `SprudelPattern`, `SprudelPatternEvent`); KlangScript's interpreter
(`klangscript/src/commonMain/kotlin/runtime/`). Outside it these types are allowed: UI and tools may keep the `Long`
or `Char` an API hands them, and a slow case found outside is fixed on its own when it shows.

Why: on Kotlin/JS `Long` is an emulated class that allocates on every operation, `Char` is a boxed wrapper, and
`Byte`/`Short` pay a range check on every operation.

## 6. No allocation in audio hot paths

Nothing allocates per sample or per block: no `FloatArray()`, `listOf()`, `toList()`, `Pair()`, string concatenation
or object creation. Preallocate at construction; a scratch buffer grows only when a block needs more
(`if (temp.size < length) { temp = FloatArray(length) }`).

## 7. No exceptions in audio hot paths

No `require()`, `check()` or `throw` in audio processing: they allocate strings and can kill the AudioWorklet thread.

## 8. Flush IIR state

Every IIR (SVF, one-pole, allpass, DC blocker) passes each state update through `flushState()` from
`utils/numerical_safety.kt`: `ic1eq = (2.0 * v1 - ic1eq).flushState()`.

Why: denormals cost 10 to 100x CPU on some platforms, and a non-finite carry latches a filter for good (one `Inf` is
enough, the next sample computes `-Inf + Inf`). The master round measured one such sample silencing the whole backend
until a page reload.

Exception: `Reverb` adds `ANTI_DENORMAL` instead (cheaper across its many combs and allpasses, documented at the
class). That covers denormals only, not a non-finite carry.

## 9. Edges are finite slopes

Saw, square and pulse never jump in one sample. The saw's flyback (`resetSamples`, scaled by the frequency, capped by
`shapeMax`) and the pulse's flanks (`flankSamples`) are short slopes drawn by the shared trapezoid in
`WaveVoiceState`. That keeps the aliasing down and is part of the analog character. The zawtooth resets instantly on
purpose (brighter, harsher); the triangle has no jump. No PolyBLEP (an earlier version used it; `CREDITS.MD` still
credits it).

## 10. Wrap phase

Oscillator and LFO phase accumulators wrap (`wrapPhase()`, or `if (phase >= TWO_PI) phase -= TWO_PI`). Why: an
unbounded phase loses `sin()` precision as the mantissa runs out of bits.

## 11 to 16. Deliberate, not findings

- 11. Single-letter DSP names (`a`, `k`, `q`, `g`, `v`, `p`) that follow textbook convention.
- 12. `.toFloat()` / `.toDouble()` at buffer boundaries: audio computes in `Double` and stores in `FloatArray`.
- 13. `@Suppress("NOTHING_TO_INLINE")` on audio hot-path inline functions.
- 14. `@Suppress("unused")` on a coherent API collection (`ShapingFuncs`).
- 15. `x == true` on a `Boolean?`.
- 16. Inline literals for well-known algorithm constants (Freeverb comb tunings), with a comment naming the source.

## 17. Copyright header

Every `.kt` file starts with this header, above any `@file:` annotation or `package`:

```kotlin
/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
```

- IntelliJ inserts it (the "Klang AGPL" profile in `.idea/copyright/`) and keeps the end year current on commit.
  Outside the IDE, add it by hand; never hand-edit the year.
- "Klangmotor" / "Motor" with a plain o. The umlaut spelling (retired 2026-08-25) survives only in historical records
  (`DEV-DIARY.MD`, `docs/history/`, `docs/tasks-archive/`), which are never "fixed".
- Exempt: `.kts` build scripts, and third-party files with their own notice (never overwrite it).
- `workshop/` is Apache-2.0 like Ultra, bound for Kraft (maintainer, 2026-10-25), and its files carry no header at all,
  as Ultra's do: its `LICENSE` covers them. The IDE's copyright profile maps the `workshop` scope to no copyright.
- `tones/` is MIT (a port of tonal.js) and never gets the AGPL header. The "Klang tones MIT" profile applies its own:

  ```kotlin
  /*
   * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
   * Portions derived from tonal.js — Copyright (c) 2015 danigb.
   * SPDX-License-Identifier: MIT
   * Full license: tones/LICENSE
   */
  ```

## 18. Imports, not fully qualified names

Import every referenced type and function; `io.peekandpoke.klang.audio_bridge.VoiceData` inline in code is a finding.
The only exception is a real name clash inside one file.

## 19. Exhaustive `when` as an expression

Over a sealed class or enum, `when` is an expression so the compiler checks every arm. Arms that do nothing are
written out (`is Foo -> Unit` or `{}`), never skipped by a statement `when`.

Exception (2026-10-10, provisional): inside a per-sample loop, or an inline function called in one, a `when`
producing a double may be an exhaustive statement `when` assigning an initialized `var`, because V8 boxes the
expression form's result there (`audio/ref/performance.md`, the rule on the exhaustive `when` in a sample loop).

## 20. Mark NaN guards

A self-comparison is not obvious to the next reader: `if (x != x) { // NaN-guard`.

## 21. Coerce user input, `require()` only internal invariants

Anything a user can reach (sprudel args, KlangScript DSL args, UI inputs, imports) is coerced into range
(`coerceIn`, defaults, clamping of indices and counts), never asserted. `require()` and `check()` guard internal
invariants only. This is the "coerce" half of `/dsl-design` §6; the "raw" half (no unasked safety clamp on an audio
parameter) lives there.

## 22. No em-dashes in user-facing text

Never `—` or `–` in docs, KDoc, UI strings, tutorials, commit messages or reports; use commas, colons, parentheses
or a new sentence. The A/B comment suffix is `, swap`. Why (maintainer, 2026-08): the dash reads as an AI tell.
Check: `review-pack --dash-scan` (added lines, any file), `docs-lint --forbid-dashes` (new docs); `/console-tools`.

## 23. Negated equality is `shouldNotBe`

In a spec, `a shouldNotBe b`, never `(a == b) shouldBe false`, so a failure names both values. Exception: a boxed
`NaN`, where `shouldNotBe` passes for the wrong reason; the raw comparison is then deliberate and says so in a comment.
(2026-09-18, it recurred after being retired; the row is in `/review-loop`'s `escape-ledger.md`.)

## 24. Name arguments that could be swapped

At a call site, name the arguments whenever two or more could be swapped and still compile (two `Int` ids, several
`Double` frames or seconds, two `Boolean` flags), or whenever reordering the parameters would silently shift them:
`cylinders.checkIn(orbit = voice.cylinderId, voiceId = voice.id, blockStart = ctx.blockStart)`. Positional is fine
for a single argument or for types that cannot be confused.

Exempt: standard-library calls whose order is standard or does not matter (`maxOf`, `minOf`, `coerceIn`, `fill`,
`copyOfRange`, `subList`, `Pair`, `Triple`; `copyInto` IS named, its destination offset sits next to a range), calls
of a function-typed value (Kotlin forbids names there), and Java calls. Open: whether literal data tables with their
column helper declared right above stay positional (`docs/tasks/_maintainer-questions.md`).

Why (maintainer, 2026-10-07): a review found `Cylinders.checkIn(id: Int, voiceId: Int, ...)`, whose two ids swap
silently and whose order differed from `Cylinder.checkIn`.
