# KlangScript Libs — Dispatcher

The KlangScript **standard library**: everything a script sees under `import * from "stdlib"`.
`Ignitor`, `Katalyst`, `Ignitor.slot`, `Math`, `Object`, `console`, and the
String/Array/Number/Boolean extensions. Kotlin Multiplatform (JVM + JS).

The language itself lives in `:klangscript` and knows nothing about this module. This module is
to `:klangscript` what `:sprudel` is: a library on top of the language, registered by KSP from
`@KlangScript.*` annotations, with BOTH doors of every DSL in one place (the Kotlin API and its
script registration). Split out of `:klangscript` on 2026-09-06
(`docs/tasks-archive/2026-09/20260906-klangscript-libs-split.md`).

## Layout

| Path                                              | Role                                                                      |
|---------------------------------------------------|---------------------------------------------------------------------------|
| `src/commonMain/kotlin/index_libs.kt`             | `stdlibLib` and `klangScript()` (engine WITH the stdlib registered)        |
| `src/commonMain/kotlin/stdlib/KlangStdLib.kt`     | Assembles the `"stdlib"` library: generated registration + console         |
| `src/commonMain/kotlin/stdlib/KlangScriptIgnitor.kt`  | The `Ignitor` doors (oscillators, noise, super-oscillators, pluck)             |
| `src/commonMain/kotlin/stdlib/KlangScriptIgnitorExtensions.kt` | Base `IgnitorDsl` wrappers (`lowpass`, `adsr`, `eq`, `phaser`, ...) and `IgnitorDslLike` |
| `src/commonMain/kotlin/stdlib/KlangScriptIgnitorSlots.kt` / `KlangScriptIgnitorClassicSlots.kt` | `Ignitor.slot` and its slot groups (`Ignitor.slot.lpf.freq`, ...): the script face of `IgnitorDsl.Slots`, the slots `x.classic()` places |
| `src/commonMain/kotlin/stdlib/KlangScriptKatalyst.kt` | `Katalyst(k => ...)` (its call form, `@KlangScript.Invoke`), `Katalyst.build`, `Katalyst.classic`, `Katalyst.param` (a `KatalystParam`), `Katalyst.slot`, and the short name `Kat`: the chain for an orbit (`katalyst(...)`) and for the output (`master(...)`, since phase 3 step 12) |
| `src/commonMain/kotlin/stdlib/KlangScriptKatalystSlots.kt` | `Katalyst.slot` and its stage groups (`Katalyst.slot.reverb.wet`, ...): the script face of `KatalystDsl.Slots`, the knobs `classic()` places |
| `src/commonMain/kotlin/stdlib/KatalystBuilders.kt` | `KatalystBuilder` (each knob appends one stage, in written order; `limiter` is a compressor preset) and its stage builders |
| `src/commonMain/kotlin/stdlib/IgnitorBuilders.kt` | The oscillator builders (`OscSineBuilder`, `OscSuperSawBuilder`, ...) and their knobs |
| `src/commonMain/kotlin/stdlib/Configure.kt`     | `configuredBy`: applies a door's `configure` lambda, enforces the error contract |
| `src/commonMain/kotlin/stdlib/EffectBuilders.kt` | `AdsrBuilder` (curves, declick) and `ModAdsrBuilder` (curves) for the envelopes, `FilterBuilder`/`BandFilterBuilder` (the four filters), `EqBuilder` (band, tap), `PitchEnvelopeBuilder`, `FmBuilder`, `PhaserBuilder`/`ShimmerBuilder` (floor), `TremoloBuilder` (shape) |
| `src/{jvmMain,jsMain}/kotlin/stdlib/PlatformConsole.kt` | Platform console output                                              |
| `build/generated/ksp/metadata/commonMain/kotlin/`  | KSP output, never edit: one `GeneratedStdlib<Area>Registration.kt` per source file area, the entry point `GeneratedStdlibRegistration.kt`, the docs `GeneratedStdlibDocs.kt` |

## Rules

- Every DSL surface follows `/dsl-design` (immutability, configure lambdas on builder types, two
  doors, parity, one word per concept). The builders of `docs/tasks-archive/2026-09/20260906-dsl-configure-lambdas.md`
  land HERE, next to their doors; this module is the Kotlin door for them as well.
- Script-door parameter defaults are safe literals (number, string, boolean, null); since 2026-10-06
  the KSP processor refuses any other default with a build error naming the door and the parameter
  (the default thunk fills every script call, the pasted literal serves native callers; `/dsl-design` §3).
- Tests come in two shapes: script-vs-Kotlin equivalence specs (`KlangScriptSineSpec` is the
  template for one door, `KlangScriptSuperOscSpec` for a family of builders with the same knobs)
  and door-parity specs (`KlangScriptFilterDoorParitySpec`). Analyzer tests that need the real
  stdlib registry (`generatedStdlibDocs`) live here too (`src/jvmTest/kotlin/intel/`).
- Memory and language references stay in `klangscript/` (`MEMORY.md`, `ref/`); this module has no
  separate memory file.

## Build & Test

```bash
./gradlew :klangscript-libs:jvmTest          # stdlib + analyzer-with-stdlib tests
./gradlew :klangscript-libs:jsTest           # JS platform
./gradlew :klangscript:jvmTest               # the language alone (no stdlib)
```
