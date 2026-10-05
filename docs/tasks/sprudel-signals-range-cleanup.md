# One `range`: the sprudel signals' shorthand, the bipolar twins and the polarity helpers out

Status: **decided 2026-10-05 (maintainer), started the same day.** "It reduces the surface while not costing any
functionality, a total win."

## The decisions (maintainer, 2026-10-05)

1. **`range(lo, hi)` is the one word** for "this signal swings between lo and hi", on every DSL. Sprudel's signals
   start from 0..1, the Ignitor oscillators from -1..1; the result of `x.range(200, 400)` is the same on both, so no
   one needs to know the source's range. `rangex` (exponential, perceptually even for frequencies) stays: a different
   concept.
2. **Sprudel's bipolar relics go** (a Strudel-port relic; "if you need unipolar you have sprudel's .range(0, 1)"):
   the twins `sine2`, `cosine2`, `saw2`, `isaw2`, `tri2`, `itri2`, `square2`, `perlin2`, `berlin2`, `rand2`, and the
   helpers `toBipolar`, `fromBipolar`, `range2`, on both doors. Removed, not deprecated (`/dsl-design` section 5).
3. **The Ignitor side's `unipolar()` and `bipolar()` go** with their wire nodes (`IgnitorDsl.Unipolar` / `Bipolar`),
   runtime ignitors and script doors. `unipolar()` is `.range(0, 1)`; `bipolar()` on a 0..1 source is
   `.mul(2).minus(1)`.
4. **A callable shorthand for every sprudel signal**: `perlin(200, 400)` is `perlin.range(200, 400)`, for `sine`,
   `cosine`, `saw`, `isaw`, `tri`, `itri`, `square`, `perlin`, `berlin`, `rand`. The bare signal stays a pattern
   (`perlin.slow(8)`, `.pan(perlin)`). BOTH values are required: `Ign.sine(200)` means 200 Hz on the Ignitor side,
   so a one-value `sine(200)` in sprudel would be a trap; it is a clear script error instead. Both doors (KlangScript
   `@KlangScript.Invoke`, Kotlin `operator fun invoke`), with a door-parity spec.

## Order (each step green, songs bit-identical)

1. The two song lines that use a twin (`TetrisRemix.kt:41` and `StrangerThings.kt:59`, both `berlin2`) become
   `berlin.range(-1, 1)` (`x * 2 - 1` and `-1 + 2 * x` are the same IEEE number); the corpus render proves it.
2. The twins and helpers go (code, KDoc, tests, skills, docs).
3. The Ignitor `unipolar` / `bipolar` go (`WarmupVocabulary.kt` rewritten with `range` / `mul`).
4. The callable shorthand, both doors, with its specs (mutation-checked).
5. The docs of `range` gain the table "where the swing sits" (`range(0, 1)` only upward, `range(-1, 0)` only down).

The phase knob and the vibrato/tremolo `range` knob are the next task (`oscillator-phase-knob.md`).
