# Open questions for the maintainer

A running log while the coordinator works in a loop (maintainer, 2026-10-07: "keep a log of the blockers, I will
answer them in one go"). Newest at the bottom of each part. When a question is answered, its answer moves into the
task file it belongs to, and the question leaves this list.

The questions are numbered, so an answer can be as short as "Q3: yes, Q7: keep".

State: `main` after v0.6.5 and the pause housekeeping (2026-10-13); where each stream picks up:
[`in-progress/NEXT-STEPS.md`](in-progress/NEXT-STEPS.md). The open engine items that are not questions:
[`engine-follow-ups.md`](in-progress/engine-follow-ups.md).

---

# Part 0: Before the refill (2026-10-13)

The calls that decide where the next work goes. All answered 2026-10-13: Q34 (the corpus re-accepted; songs move freely: `../tasks-archive/2026-10/20261015-song-checks-move-freely.md`), Q35 (no ADAA exists, the
link fixed), Q36 (the eight merged branches and the worktree deleted), Q37 (rule 1 stays provisional and is expected to go: the next review loop tries reviewers running mutants again;
rule 2 dropped, the exception documented in its KDoc, and the register says rules are guard-rails), Q38 (after the refill, finish engine
follow-ups 31 to 41, as long as each stays a purely independent performance fix that changes no API), Q39 (fade, do not cut: engine follow-up 42), Q18 (the data tables stay positional, documented at
their helpers), Q19 (the disposal order stays, documented in the dispatcher's KDoc), Q20 (fix the frozen songs'
shared orbits: `song-orbit-ownership-review.md`), Q13 (`progress()` and `elapsed()`, curves as their own future
feature: `pitch-pipeline-into-the-tree.md` 7f, `future/curves.md`), Q33 and Q32 (accelerate and the pitch envelope both stay nodes: pitch 7d, 7e), Q29 (`duty(width)`: engine follow-up 44), Q24 (typed Kotlin doors with `.ign`/`.kat`, KlangScript keeps its own
door: `kotlin-typed-doors.md`), Q30 and Q31 (confirmed as decided by default; FM gets its own round with the
maintainer: `future/fm-revisit.md`), Q28 (solo: the latest value wins at once, engine
follow-up 43).

---

# Part 0b: The C calls you asked details for (2026-10-14)

All answered 2026-10-14: Q40 (C3, NaN in a signal is the author's own; the task archived), Q41 (C5, `randL` and
`filter` cut), Q42 (C12, the next block-framing round when the nodes are stable: `../plans/block-framing-invariance.md`),
Q43 (the two laws moved to `audio/ref/`).

---

# Part 0c: Typed Kotlin doors, the design's open points (2026-10-16)

Home: [`kotlin-typed-doors.md`](kotlin-typed-doors.md), "Design". Recommended layering: option C (the script door calls
the typed Kotlin door in `audio_bridge`). Q44 is the layering itself; Q45 to Q51 follow from it.

## Q44. The layering: option C?

The KlangScript door becomes internal and calls the typed Kotlin door in `audio_bridge`, which already holds the
filters and `adsr` this way. Recommendation: yes (the only option the engine itself can use).

## Q45. The Kotlin door is flat where the script uses a configure lambda

```
x.vibrato(5, 0.5, v => v.range(0, 1))                                  // script
x.vibrato(5.ign, 0.5.ign, rangeFrom = 0.ign, rangeTo = 1.ign)         // Kotlin
```

Accept this as the general rule (today a list of recorded exceptions)? Recommendation: yes; Kotlin's named
parameters do what the lambda does in script.

## Q46. No Kotlin `Ign` object: oscillators are their node constructors

```kotlin
osc.vibrato(rate = IgnitorDsl.PerlinNoise(), semitones = 0.3.ign)     // not Ign.perlin()
```

Fine, or a thin `Ign` facade (a second word for the same concept)? Recommendation: no facade.

## Q47. The arithmetic operators with a Double operand retire too

```kotlin
saw * 0.5          // today
saw * 0.5.ign      // after
```

Or keep the two operator twins (`times`, `plus`) as a deliberate exception? Recommendation: retire them, one rule.

## Q48. `floor` on the phaser and shimmer

`door-shapes.md` says `floor` is never an Ignitor effect door parameter. On the flat Kotlin door:
`osc.phaser(0.5.ign, 0.3.ign, floor = 0.2.ign)`, or keep `.copy(floor = ...)`? Recommendation: allow it on the
Kotlin door (it is the flat form of the script's builder knob).

## Q49. The Katalyst eq section takes a leaf only

Typed `KatalystKnobDsl`, it still compiles an expression the stage refuses:
`KatalystDsl().eq(band(freq = KatalystKnobDsl.Sine(1.kat)))`. Throw `IllegalArgumentException` at construction (build
time, not the audio path), or coerce? Recommendation: throw, with the door and parameter named.

## Q50. Doors that are already one typed function for both doors

`bands`/`cut`, `serial`, `parallel`, `blend` take `Double`s and lambdas and serve both doors today:
`bands(sine(f)) { it.cut(200.0) }`. Keep them shared, or split for uniformity? Recommendation: keep them shared.

## Q51. Build-time knobs stay plain scalars

```kotlin
osc.distort(0.5.ign, shape = "hard", oversample = 2)
```

`shape: String`, `oversample: Int`, `lookahead`, the supersaw phase pool, shimmer `pitches: List<Double>`: record
them as the one exception to "every value parameter takes the DSL's own type"? Recommendation: yes (they are
fixed at build, never a signal).

---

# Part 1: Blocking (work waits on the answer)

None open.

---

# Part 2: Decided by default, please confirm

Work went ahead with the conservative choice. A "no" here means a small follow-up change.

None open.

---

# Part 3: For later (not blocking anything now)

None open.
