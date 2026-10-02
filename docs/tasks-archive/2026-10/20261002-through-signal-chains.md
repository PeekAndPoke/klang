# `through()`: a signal chain written as the list it is

Status: **DONE 2026-10-02** (step 5 on 2026-10-03). Opened 2026-10-02 by the maintainer. Small DSL door on two DSLs, no engine or wire change.

## Why

Der Schmetterling's and Kokon's `makeGuitar(pickup, pedal, preamp, power, cab)` takes five stages only to call them
nested, `cab(power(preamp(pedal(pickup(signal)))))`. A sixth stage (the snare cut EQ) does not fit the five slots
and gets written into the instrument by hand. The maintainer's idea: one call that runs a signal through any number of
stages, in the order written:

```javascript
signal.through(pickupNeck, pedalStock, preampClean, powerClassA, snareCut, cab1x12)
```

A rig becomes a value (`let cleanRig = x => x.through(...)`), which is also the shape the instrument extraction needs
(stages as small reusable functions, combined freely).

## Decided (2026-10-02, in conversation)

- **The word is `through`.** It reads as the signal path and says that the order matters. Rejected:
  - `apply`: sprudel's `apply(f, g)` is an alias of `layer` and STACKS the results in parallel; one word per concept;
  - `pipe`: the Pipeline DSL was retired 2026-09-27 and is on the do-not-restore list;
  - `chain`: already means the Katalyst chain (orbit chain, master chain).
- **On `Osc` and on the Katalyst builder** (`Katalyst(k => k.through(...))`), both doors each (KlangScript and Kotlin).
  Sprudel patterns are not in scope.
- **A native vararg, not a script function.** Script-defined functions have no rest parameters (the parser takes plain
  names only), so a script-level `through` needs an array: `through(x, [a, b])` (tested 2026-10-02, byte-identical
  to the nested calls). Kotlin functions can be varargs (sprudel's `layer` is one), so the door reads without brackets.

## The contract

- `x.through(a, b, c)` is exactly `c(b(a(x)))`: the same node (Osc) or the same builder (Katalyst) as the hand-nested
  calls. Construction-time only: no new node kind, no wire change, nothing at render time.
- `x.through()` with no stage is `x` itself.
- A stage is any function from the type to the same type: `(IgnitorDsl) -> IgnitorDsl` on Osc,
  `(KatalystBuilder) -> KatalystBuilder` on the Katalyst. The receiver is never changed (immutability, `/dsl-design` §1).

## Where it goes (read from the code 2026-10-02)

- **Osc, Kotlin door:** `fun IgnitorDsl.through(vararg stages)` in `audio_bridge/src/commonMain/kotlin/IgnitorDsl.kt`,
  in its own "Composition" section after the arithmetic group (the Osc Kotlin door lives there).
- **Osc, script door:** `@KlangScript.Method through(self, vararg stages)` on `KlangScriptOscExtensions`. The KSP
  object-method vararg path with a FUNCTION element type had no precedent; verified 2026-10-02: KSP emits
  `registerVarargMethod<((IgnitorDsl) -> IgnitorDsl), IgnitorDsl>("through")`, and the parity spec is green on the JVM
  and in the JS browser runner. The fallback (a file-level `@KlangScript.Function` on `IgnitorDsl`) was not needed.
- **Every stage is checked on the script doors** (review round 1): a null stage, a stage returning nothing (a block
  body without `return`) or the wrong type is a script error naming the stage (`runThroughStages` in `Configure.kt`,
  the sibling of `configuredBy`). A non-function stage (a number, a boolean, an array) is caught one level down, in
  the language: `convertToKotlin` now refuses a non-callable value on any `FunctionN` slot (the mirror of the existing
  "expected Double, got a function"), which closes the same hole for every function-typed parameter, configure
  lambdas included.
- **Katalyst, both doors:** `@KlangScript.Function fun KatalystBuilder.through(vararg stages)` in
  `klangscript-libs/.../stdlib/KatalystBuilders.kt`, one implementation for both doors, like `reverb` and `gain`
  (file-level vararg path, as sprudel's `layer`).

## Steps

1. The three functions with KDoc (the editor popup reads it), the Katalyst builder's knob list updated.
2. A door-parity spec in `klangscript-libs` (pattern: `KlangScriptFilterDoorParitySpec`,
   `KlangScriptKatalystDoorParitySpec`): per DSL, script door == Kotlin door == the hand-nested calls; the order
   (stages that do not commute, so a reversed fold is red); with no stage, `through()` is the receiver; a rig stored in a `let` and
   used as a stage. Mutation-checked (light tier, surface tests): one reversed fold, one dropped stage.
3. The references: `ignitor-reference.md` and the Katalyst part of `sprudel-reference.md`, with the `apply` contrast.
4. Review: one coding reviewer (no DSP, no wire), loop until clean.
5. `makeGuitar` in Der Schmetterling and Kokon takes one rig built with `through`, by byte-identical renders.

## What we built (2026-10-02)

The maintainer had the idea and chose to build it natively for the nicer surface; Claude built it, two review rounds
on `opus` (round 2 clean, at high effort).

- `through(...)` on `Osc` (Kotlin door in `audio_bridge`, script door on `KlangScriptOscExtensions`) and on the
  Katalyst builder (one `@KlangScript.Function` for both doors), with KDoc and both music-writing references.
- The script doors check every stage (`runThroughStages`, next to `configuredBy`): a null stage, a stage returning
  nothing, a stage returning the wrong type are script errors naming the stage.
- A language fix found on the way (round 1, MAJOR): a non-callable value on any function-typed native parameter was
  passed through unconverted (a cast failure on the JVM, a silent wrong value on JS); `convertToKotlin` now refuses it
  with "expected a function, got a number". Full suites green: klangscript 1126, klangscript-libs 761, sprudel 3470
  (JVM); the new specs also in the JS browser runner.
- A JS-only bug caught by running the spec in the browser: a null check on an element of a non-null declared type was
  removed by the JS compiler; the stages are read as `Any?` so the check stands on both platforms.
- Mutation checks, all red: reversed folds on both DSLs, the script door ignoring its stages, a dropped last stage,
  the language guard removed, each stage check removed (the null-stage one on JVM and JS). One mutant did not compile
  and was replaced by one that does; a mutant that does not compile proves nothing.
- Step 5, done 2026-10-03: `makeGuitar` in Der Schmetterling and Kokon takes ONE rig, the signal path after the
  string written with `through` (`let rhythmRig = x => x.through(pickupHumbucker, pedalScreamer, preampHighGain,
  powerPushPull, snareCut, cab4x12)`); the snare cut that sat welded between the power amp and the cab is a stage of its
  own (`snareCut`). Both songs render byte-identical to before (full renders, Der Schmetterling 162 cycles with a fixed
  seed, Kokon 60 cycles, on the same build). The rig-ablation benchmark (`src/jvmMain/kotlin/SongBenchmarkCases.kt`)
  follows the new rig line; on the way it caught up with the live song where it had fallen behind: the string extras
  are matched without their envelope and burst values (the pitch envelope keeps its amount `0.5`, which tells it from
  the drums' envelopes), the Trommel's analog anchor without its ring constant, the drums lost the clap (the frozen ledger
  rows keep it), and the "song: no compressors" row is gone with the compressors. `SongBenchmarkCasesCompileSpec`
  green.
