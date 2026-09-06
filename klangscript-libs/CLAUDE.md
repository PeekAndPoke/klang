# KlangScript Libs — Dispatcher

The KlangScript **standard library**: everything a script sees under `import * from "stdlib"`.
`Osc`, `Master`, `Pipeline`, `Stage`, `OscSlot`, `Math`, `Object`, `console`, and the
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
| `src/commonMain/kotlin/stdlib/KlangScriptOsc.kt`  | The `Osc` doors (oscillators, noise, super-oscillators, pluck)             |
| `src/commonMain/kotlin/stdlib/KlangScriptOscExtensions.kt` | Base `IgnitorDsl` wrappers (`lowpass`, `adsr`, `eq`, `phaser`, ...) and `IgnitorDslLike` |
| `src/commonMain/kotlin/stdlib/KlangScriptMaster.kt` | `Master(m => ...)` (`invoke`), `Master.build`, `Master.default`               |
| `src/commonMain/kotlin/stdlib/MasterBuilders.kt` | `MasterBuilder` (gain, limiter, reverb, delay) and the three stage builders     |
| `src/commonMain/kotlin/stdlib/KlangScriptPipeline.kt` | `Pipeline(p => ...)` (`invoke`), `Pipeline.build`, `Pipeline.modern`, `Pipeline.pedal` |
| `src/commonMain/kotlin/stdlib/PipelineBuilders.kt` | `PipelineBuilder` (stage knobs append; `tuneVca`/`tuneFilter` configure existing stages) and the two stage builders |
| `src/commonMain/kotlin/stdlib/IgnitorBuilders.kt` | The oscillator builders (`OscSineBuilder`, `OscSuperSawBuilder`, ...) and their knobs |
| `src/commonMain/kotlin/stdlib/Configure.kt`     | `configuredBy`: applies a door's `configure` lambda, enforces the error contract |
| `src/commonMain/kotlin/stdlib/EffectBuilders.kt` | `EqBuilder` (band, tap), `PhaserBuilder`, `ShimmerBuilder` (wet, dryFloor) |
| `src/{jvmMain,jsMain}/kotlin/stdlib/PlatformConsole.kt` | Platform console output                                              |
| `build/generated/ksp/metadata/commonMain/kotlin/`  | `GeneratedStdlibRegistration.kt` (KSP output, never edit)                 |

## Rules

- Every DSL surface follows `/dsl-design` (immutability, configure lambdas on builder types, two
  doors, parity, one word per concept). The builders of `docs/tasks-archive/2026-09/20260906-dsl-configure-lambdas.md`
  land HERE, next to their doors; this module is the Kotlin door for them as well.
- Script-door parameter defaults are safe literals (number, string, boolean, null); KSP refuses a
  door that invites a trailing lambda but carries a non-literal optional default.
- Tests come in two shapes: script-vs-Kotlin equivalence specs (`KlangScriptSuperSawSpec` is the
  template) and door-parity specs (`KlangScriptFilterDoorParitySpec`). Analyzer tests that need
  the real stdlib registry (`generatedStdlibDocs`) live here too (`src/jvmTest/kotlin/intel/`).
- Memory and language references stay in `klangscript/` (`MEMORY.md`, `ref/`); this module has no
  separate memory file.

## Build & Test

```bash
./gradlew :klangscript-libs:jvmTest          # stdlib + analyzer-with-stdlib tests
./gradlew :klangscript-libs:jsTest           # JS platform
./gradlew :klangscript:jvmTest               # the language alone (no stdlib)
```
