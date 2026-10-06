# One `range`: the sprudel signals' shorthand, the bipolar twins and the polarity helpers out

Status: **DONE 2026-10-05, archived.** Decided by the maintainer and built the same day on branch `signals-range`: "It
reduces the surface while not costing any functionality, a total win." Commits `5f3c0278`, `82cc6024`, `f22448e9`,
`4b94bdd5`. Follow-ups: the phase knob with the vibrato/tremolo `range` knob (`docs/tasks-archive/2026-10/20261006-oscillator-phase-knob.md`)
and one name per oscillator shape (`docs/tasks/oscillator-names-across-dsls.md`).

## The decisions (maintainer, 2026-10-05)

1. **`range(from, to)` is the one word** for "this signal swings between from and to", on every DSL (the parameter
   names are decision 10 below). Sprudel's signals
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
   `cosine`, `saw`, `tri`, `square`, `perlin`, `berlin`, `rand` (`isaw` and `itri` went, decision 6). The bare signal stays a pattern
   (`perlin.slow(8)`, `.pan(perlin)`). BOTH values are required: `Ign.sine(200)` means 200 Hz on the Ignitor side,
   so a one-value `sine(200)` in sprudel would be a trap; it is a clear script error instead. Both doors (KlangScript
   `@KlangScript.Invoke`, Kotlin `operator fun invoke`), with a door-parity spec.

## Further decisions (maintainer, 2026-10-05, after steps 2 to 5)

6. **`isaw` and `itri` go** from sprudel, both doors. A falling saw is `saw.range(1, 0)`, an inverted triangle
   `tri.range(1, 0)`. A ranged one swaps the values: `isaw.range(a, b)` is `saw.range(b, a)` and `itri.range(a, b)` is
   `tri.range(b, a)`, because the innermost range wins (`isaw.range(1, 0).range(a, b)` would stay 1..0). No built-in
   or frozen song used them.
7. **`cosine` stays, as `sine.early(0.25)`**, built literally that way so `sine` is the one source of the wave. It
   keeps its shorthand (`cosine(from, to)`) and its `range`. No song used it.
8. **`sinOfDay2` and `sinOfNight2` go; `sinOfDay` / `sinOfNight` become `sineOfDay` / `sineOfNight`**, so the
   word is "sine" everywhere. History keeps its words.
9. **`choose2` goes**, with its private helper.
10. **The range values are named `from` and `to` on every surface**: sprudel's `range` and `rangex`, the signals'
    shorthand, the Ignitor `range` on both doors. `min` / `max` are clamp words in this project, and `lo` / `hi`
    are wrong because `sine(100, 50)` is legal.
11. **`from` is an ordinary name in KlangScript** ("this is a bug in the parser"): a contextual keyword as in
    JavaScript, a keyword only inside an import, so `range(from = 1, to = 2)` parses.
12. **`as` is fixed the same way** (maintainer, 2026-10-05): a contextual keyword, read as a keyword only in the
    import and export grammar (`import * as x`, `import { a as b }`, `export { a as b }`), an ordinary name elsewhere.
13. **`lo` / `hi` become `from` / `to` end to end** (2026-10-05): the wire node `IgnitorDsl.Range(inner, from, to)`, the
    engine's `Ignitor.range(from, to)` and `RangeIgnitor`. The wire ships with the frontend and worklet and nothing is
    persisted, so it is a plain rename. The clamp keeps `lo` / `hi`: a different concept.
14. **The dead range-context helpers go** (2026-10-05): `_mapRangeContext` and `ContextRangeMapPattern` with its spec,
    whose only callers were the bipolar helpers and `choose2`.
15. **`String.range` and `String.rangex` go** (maintainer, 2026-10-05), both doors. On a mini-notation string they
    did nothing useful: only `ContinuousPattern` reads the range, so `range` was a silent no-op and `rangex` applied a
    bare `exp` to the values. Discrete values already scale with `.mul(k).add(c)`: `"0 0.5 1".range(100, 1000)` meant
    `"0 0.5 1".mul(900).add(100)`. `SprudelPattern.range` / `rangex` stay; their KDoc says they shape continuous
    signals only.
16. **The Ignitor gets `rangex(from, to)`** (maintainer, 2026-10-05), both doors, with the same meaning as sprudel's
    (parameter parity): the `-1..1` swing laid onto `from..to` exponentially, `from · (to / from)^((x + 1) / 2)`, so
    `from` at -1, the geometric mean at 0, `to` at 1. A value at or below 0 is coerced to `RANGEX_FLOOR` (0.0001,
    `audio_bridge/constants/RangeDefaults.kt`, which sprudel's `rangex` now reads too). Built by COMPOSITION, no wire
    node and no engine ignitor: `exp(range(ln(max(from, floor)), ln(max(to, floor))))` with the mathematical `Max`
    node (the door named `max` is a clamp). The optimizer already classifies `Exp`, `Log`, `Max` and `Range` as
    control-rate over control-rate operands, so the chain folds like `range`; at run time the two logarithms are
    block-constant (once per block), and the cost per sample is the range's multiply-add and one `fastExp` (with
    signal bounds: also two `ln` per sample and two scratch renders).

## Order (each step green, songs bit-identical)

Status, 2026-10-05: all steps and decisions 1 to 16 are committed (`5f3c0278`, `82cc6024`, `f22448e9`, `4b94bdd5`).
Reviews: round 1 (blind pair) and round 2 (reviewer-high, clean) for the cleanup, a clean blind round 1 for the
Ignitor `rangex`.

1. **Done.** The two song lines that use a twin (`TetrisRemix.kt:41` and `StrangerThings.kt:59`, both `berlin2`) become
   `berlin.range(-1, 1)` (`x * 2 - 1` and `-1 + 2 * x` are the same IEEE number); the corpus render proves it.
2. **Done.** The twins and helpers go (code, KDoc, tests, skills, docs).
3. **Done.** The Ignitor `unipolar` / `bipolar` go (`WarmupVocabulary.kt` rewritten with `range` / `mul`).
4. **Done.** The callable shorthand, both doors, with its specs (mutation-checked).
5. **Done.** The docs of `range` gain the table "where the swing sits" (`range(0, 1)` only upward, `range(-1, 0)` only down).
6. **Done.** Decisions 6 to 16: `isaw`, `itri`, `choose2`, `sinOfDay2`, `sinOfNight2` removed, the clock pair
   renamed, `cosine` built from `sine`, `from` / `to` on every range door and in the wire node and engine, `from` and
   `as` contextual in the parser, the dead range-context helpers and the string-receiver `range` / `rangex` removed, the Ignitor `rangex` composed. The corpus render is identical except
   Der Schmetterling (the maintainer's open edit), which is proven separately from the committed text.

The phase knob and the vibrato/tremolo `range` knob are the next task (`docs/tasks-archive/2026-10/20261006-oscillator-phase-knob.md`).
