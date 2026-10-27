# KlangScript Libs — Dispatcher

The KlangScript **standard library**: everything a script sees under `import * from "stdlib"`.
`Ignitor`, `Katalyst`, `Ignitor.slot`, `Math`, `Object`, `console`, and the
String/Array/Number/Boolean extensions. Kotlin Multiplatform (JVM + JS).

The language itself lives in `:klangscript` and knows nothing about this module. This module is
to `:klangscript` what `:sprudel` is: a library on top of the language, registered by KSP from
`@KlangScript.*` annotations. It is the KlangScript door only (maintainer, 2026-10-16; in force since the
rollout S1 to S7 of KLANG-00314 (kotlin typed doors)): internal, converting once and
calling the typed Kotlin door in `audio_bridge`, where the typed `Ign` / `Kat` objects and every Ignitor and
Katalyst Kotlin door live. The one Kotlin door kept here is `Interval.ratio` (`IntervalRatio.kt`), for the
interval vocabulary of `tones`, not an audio door. Split out of `:klangscript` on 2026-09-06
(KLANG-00180 (klangscript libs split)).

## Layout

| Path                                              | Role                                                                      |
|---------------------------------------------------|---------------------------------------------------------------------------|
| `src/commonMain/kotlin/index_libs.kt`             | `stdlibLib` and `klangScript()` (engine WITH the stdlib registered)        |
| `src/commonMain/kotlin/stdlib/KlangStdLib.kt`     | Assembles the `"stdlib"` library: generated registration + console         |
| `src/commonMain/kotlin/stdlib/KlangScriptIgnitor.kt`  | The `Ignitor` doors (oscillators, noise, super-oscillators, pluck)             |
| `src/commonMain/kotlin/stdlib/KlangScriptIgnitorExtensions.kt` | Base `IgnitorDsl` wrappers (`lowpass`, `adsr`, `eq`, `phaser`, ...) and `IgnitorDslLike` |
| `src/commonMain/kotlin/stdlib/KlangScriptIgnitorSlots.kt` / `KlangScriptIgnitorClassicSlots.kt` | `Ignitor.slot` and its slot groups (`Ignitor.slot.lpf.freq`, ...): the script face of `IgnitorDsl.Slots`, the slots `x.classic()` places |
| `src/commonMain/kotlin/stdlib/KlangScriptKatalyst.kt` | `Katalyst(k => ...)` (its call form, `@KlangScript.Invoke`), `Katalyst.build`, `Katalyst.classic`, `Katalyst.param` (a `KatalystKnobDsl.Param`, the chain's own knob node), the knob modulators (`Katalyst.sine`, `saw`, `ramp`, `tri`, `square`, `perlin`, `berlin`), `Katalyst.slot`, and the short name `Kat`: the chain for an orbit (`katalyst(...)`) and for the output (`master(...)`, since phase 3 step 12) |
| `src/commonMain/kotlin/stdlib/KlangScriptKatalystSlots.kt` | `Katalyst.slot` and its stage groups (`Katalyst.slot.reverb.wet`, ...): the script face of `KatalystDsl.Slots`, the knobs `classic()` places |
| `src/commonMain/kotlin/stdlib/KatalystBuilders.kt` | The chain's script knobs (since S5 internal, renamed variants, `scriptReverb` ..., that convert and call the typed members of `KatalystBuilder`, its stage builders and the modulators' builders, all in `audio_bridge/KatalystBuilders.kt`; the knob arithmetic's variants call the typed functions in `audio_bridge/KatalystKnobDsl.kt`); `toKatalystKnob`, the one conversion to a knob, and the eq pre-check; `serial`, `parallel`, `blend`, `bands` as variants too since S6, wrapping each script stage in its check |
| `src/commonMain/kotlin/stdlib/BandsBuilders.kt` | The `bands` builders' script knobs (`band`, `cut`), internal, renamed variants since S6; `band` wraps the script's processor in its check; the builders and the crossover live in `audio_bridge/BandsBuilders.kt` |
| `src/commonMain/kotlin/stdlib/IgnitorBuilders.kt` | The oscillator builders' script knobs: internal, renamed variants (`scriptPhase`, `scriptVoices`, ...) that convert and call the typed members; the builders themselves (`OscSineBuilder`, `OscSuperSawBuilder`, ...) live in `audio_bridge/IgnitorBuilders.kt` (`OscSawBuilder` since S1, the rest since S2) |
| `src/commonMain/kotlin/stdlib/Configure.kt`     | `configuredBy`: applies a door's `configure` lambda, enforces the error contract; `runStage` / `checkedStage`: the same check for a script stage, branch or band processor |
| `src/commonMain/kotlin/stdlib/EffectBuilders.kt` | The effect builders' script knobs, all internal, renamed variants that convert and call the typed members: `AdsrBuilder` (curves, declick), `PitchEnvelopeBuilder` and `FmBuilder` (adsr) since S3b; `ModAdsrBuilder` (curves), `FilterBuilder`/`BandFilterBuilder` (the four filters) and `EqBuilder`/`KatalystEqBuilder` (band, tap) since S3a; `PhaserBuilder`/`ShimmerBuilder` (floor) and `TremoloBuilder` (range, shape) since S3c; `VibratoBuilder` (`range`, `phase`) since S1. Every class lives in `audio_bridge` (`EffectBuilders.kt`, `IgnitorBuilders.kt`) |
| `src/{jvmMain,jsMain}/kotlin/stdlib/PlatformConsole.kt` | Platform console output                                              |
| `build/generated/ksp/metadata/commonMain/kotlin/`  | KSP output, never edit: one `GeneratedStdlib<Area>Registration.kt` per source file area, the entry point `GeneratedStdlibRegistration.kt`, the docs `GeneratedStdlibDocs.kt` |

## Rules

- Every DSL surface follows `/dsl-design` (immutability, configure lambdas on builder types, two
  doors, parity, one word per concept). The builders of KLANG-00179 (dsl configure lambdas)
  moved to `audio_bridge` in the typed-doors rollout (KLANG-00314 (kotlin typed doors); `OscSawBuilder` and
  `VibratoBuilder` since spike S1, every other oscillator builder since S2, the filters' and the eq's builders since S3a, the envelopes' since S3b, the phaser's, the shimmer's and the tremolo's since S3c, the Katalyst's since S5, the bands' since S6), with typed member knobs; the Kotlin door there has the same builders and
  shapes, only the accepted types differ. What stays HERE is the script door: internal, renamed knob variants
  (`@KlangScript.Function(name = "range") internal fun VibratoBuilder.scriptRange(...)`) that convert and call
  the members, and the script doors that convert and delegate (`/dsl-design` §3, the typed-door pattern). Only the
  script-exposed functions here take `XxxLike = Any` parameters (Q50, 2026-10-16), and they are `internal`: the
  script objects (`KlangScriptIgnitor`, `KlangScriptIgnitorExtensions`, `KlangScriptKatalyst`, the script `Ign` /
  `Kat`, the slot objects), the conversions (`toIgnitorDsl`, `toKatalystKnob`) and `IgnitorDslLike` (S7). A Kotlin
  caller in another module uses the typed door in `audio_bridge`. Public by design, since other modules use them
  and they are no door: the one `Any`-taking exception, `ignitorSlotName` / `katalystSlotName` with `IgnitorSlotLike` / `KatalystSlotLike`
  (sprudel's `ignp` / `katp`), `klangScript()`, `stdlibLib`, `KlangStdLib`, `ConsoleLevel` (the app's player),
  `Interval.ratio` (the one Kotlin door kept in this module, for `tones`, not an audio door) and the script
  constants `PI` / `E` (no door, left as they were).
- KlangScript stdlib follows Kotlin conventions, not JavaScript (naming, argument style, `name = value` named args). (rule, 2026-05; moved from the root register 2026-10-23; detail: `klangscript/MEMORY.md` Design Decisions)
- Script-door parameter defaults are safe literals (number, string, boolean, null); since 2026-10-06
  the KSP processor refuses any other default with a build error naming the door and the parameter
  (the default thunk fills every script call, the pasted literal serves native callers; `/dsl-design` §3).
- Tests come in two shapes: script-vs-Kotlin equivalence specs (`KlangScriptSineSpec` is the
  template for one door, `KlangScriptSuperOscSpec` for a family of builders with the same knobs)
  and door-parity specs (`KlangScriptFilterDoorParitySpec`). Analyzer tests that need the real
  stdlib registry (`generatedStdlibDocs`) live here too (`src/jvmTest/kotlin/intel/`).
- Memory and language references stay in `klangscript/` (`MEMORY.md`, `ref/`); this module has no
  separate memory file.
- KDocs: the Lexikon's terms link themselves (no hand-written Lexikon links for them), and "per voice", not a bare
  "voice", for what belongs to one sounding note: the two KDoc rules in `sprudel/ref/dsl-conventions.md` (KDoc
  Rules), which hold here too. (rule, 2026-10-27, KLANG-00369)

## Build & Test

`:klangscript-libs:jvmTest` (stdlib, analyzer with the stdlib), `:klangscript-libs:jsTest`, `:klangscript:jvmTest` (the
language alone); through the build lock, read with test-digest (`/console-tools`).
