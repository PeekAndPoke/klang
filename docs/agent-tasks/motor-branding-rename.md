# Motör Branding Rename

Rename the audio engine's core vocabulary to follow the combustion engine metaphor.

**Branch:** `motor-branding-rename` (create from current)  
**Slogan:** "Prepare, Ignite, Refine, Fuse"

## Naming Table

| Noun          | Verb     | Replaces                | Audio Concept                     |
|---------------|----------|-------------------------|-----------------------------------|
| **Motör**     | —        | (new concept name)      | The whole audio engine            |
| **Cylinder**  | —        | Orbit                   | Independent parallel channel      |
| **Fuel**      | Fuel     | (new concept name)      | Voices / oscillators              |
| **Injection** | Inject   | (event scheduling)      | Distributes fuel into Cylinders   |
| **Ignitor**   | Ignite   | Exciter                 | Core sound transformation         |
| **Katalyst**  | Katalyze | Bus effects             | Output refinement and polish      |
| **Fusion**    | Fuse     | Master output / mix bus | All Cylinders converge here       |
| **RPM**       | —        | CPS (cycles per second) | Tempo / speed unit (rpm = cps*60) |

## Scope

Sprudel module is **excluded** from renames. Only additions (aliases) in sprudel.

## Phase 1: Exciter → Ignitor (largest, most self-contained)

### 1a. Directory renames (git mv)

```
audio_be/src/commonMain/kotlin/exciter/        → .../ignitor/
audio_be/src/commonTest/kotlin/exciter/         → .../ignitor/
audio_be/src/commonMain/kotlin/voices/strip/excite/ → .../voices/strip/ignite/
```

### 1b. File renames

| Old                      | New                    |
|--------------------------|------------------------|
| `Exciter.kt`             | `Ignitor.kt`           |
| `Exciters.kt`            | `Ignitors.kt`          |
| `ExciteContext.kt`       | `IgniteContext.kt`     |
| `ExciterRegistry.kt`     | `IgnitorRegistry.kt`   |
| `ExciterDefaults.kt`     | `IgnitorDefaults.kt`   |
| `ExciterDslRuntime.kt`   | `IgnitorDslRuntime.kt` |
| `ExciterEffects.kt`      | `IgnitorEffects.kt`    |
| `ExciterFilters.kt`      | `IgnitorFilters.kt`    |
| `ExciterEnvelopes.kt`    | `IgnitorEnvelopes.kt`  |
| `ExciterPitchMod.kt`     | `IgnitorPitchMod.kt`   |
| `ExciterFm.kt`           | `IgnitorFm.kt`         |
| `ParamExciter.kt`        | `ParamIgnitor.kt`      |
| `SampleExciter.kt`       | `SampleIgnitor.kt`     |
| `ExciteRenderer.kt`      | `IgniteRenderer.kt`    |
| `ExciterBenchmark.kt`    | `IgnitorBenchmark.kt`  |
| All test files similarly |                        |

In `audio_bridge`:

- `ExciterDsl.kt` → `IgnitorDsl.kt`

### 1c. Content renames (search-and-replace)

| Old                  | New                  |
|----------------------|----------------------|
| `Exciter`            | `Ignitor`            |
| `ExciterDsl`         | `IgnitorDsl`         |
| `ExciteContext`      | `IgniteContext`      |
| `ExciterRegistry`    | `IgnitorRegistry`    |
| `ExciterDefaults`    | `IgnitorDefaults`    |
| `ExciteRenderer`     | `IgniteRenderer`     |
| `ExciterDslRuntime`  | `IgnitorDslRuntime`  |
| `ParamExciter`       | `ParamIgnitor`       |
| `SampleExciter`      | `SampleIgnitor`      |
| `Exciters` (object)  | `Ignitors`           |
| `ExciterBenchmark`   | `IgnitorBenchmark`   |
| `exciterRegistry`    | `ignitorRegistry`    |
| `ExciterRegistrar`   | `IgnitorRegistrar`   |
| `ExciterDslLike`     | `IgnitorDslLike`     |
| `toExciterDsl()`     | `toIgnitorDsl()`     |
| `RegisterExciter`    | `RegisterIgnitor`    |
| `"register-exciter"` | `"register-ignitor"` |

Package renames:

- `io.peekandpoke.klang.audio_be.exciter` → `io.peekandpoke.klang.audio_be.ignitor`
- `io.peekandpoke.klang.audio_be.voices.strip.excite` → `io.peekandpoke.klang.audio_be.voices.strip.ignite`

Modules affected: `audio_be`, `audio_bridge`, `klangscript`, `audio_benchmark`, `audio_jsworklet`, `klang`

### 1d. Serialization notes

- `ExciterDsl` uses `@SerialName` with descriptive wire names ("sine", "param", etc.) — NOT class names. Safe to rename.
- `RegisterExciter.SERIAL_NAME = "register-exciter"` → rename to `"register-ignitor"`. Safe: no persistence, pre-launch.
- KSP: Run clean build after rename — `@KlangScript.TypeExtensions(IgnitorDsl::class)` triggers regeneration.

### 1e. Verify

```bash
./gradlew clean compileKotlinJvm compileKotlinJs
./gradlew :audio_be:jvmTest :audio_bridge:jvmTest :audio_benchmark:jvmTest :klangscript:jvmTest
```

### 1f. Commit

---

## Phase 2: Orbit → Cylinder + Bus → Katalyst

### 2a. Directory renames

```
audio_be/src/commonMain/kotlin/orbits/     → .../cylinders/
audio_be/src/commonTest/kotlin/orbits/     → .../cylinders/
audio_be/src/commonMain/kotlin/cylinders/bus/  → .../cylinders/katalyst/
audio_be/src/commonTest/kotlin/cylinders/bus/  → .../cylinders/katalyst/
```

### 2b. File renames

| Old                      | New                           |
|--------------------------|-------------------------------|
| `Orbit.kt`               | `Cylinder.kt`                 |
| `Orbits.kt`              | `Cylinders.kt`                |
| `BusEffect.kt`           | `KatalystEffect.kt`           |
| `BusContext.kt`          | `KatalystContext.kt`          |
| `BusCompressorEffect.kt` | `KatalystCompressorEffect.kt` |
| `BusDelayEffect.kt`      | `KatalystDelayEffect.kt`      |
| `BusDuckingEffect.kt`    | `KatalystDuckingEffect.kt`    |
| `BusPhaserEffect.kt`     | `KatalystPhaserEffect.kt`     |
| `BusReverbEffect.kt`     | `KatalystReverbEffect.kt`     |
| Test files similarly     |                               |

### 2c. Content renames

| Old                   | New                        |
|-----------------------|----------------------------|
| `Orbit`               | `Cylinder`                 |
| `Orbits`              | `Cylinders`                |
| `OrbitState`          | `CylinderState`            |
| `orbitId`             | `cylinderId`               |
| `duckOrbit`           | `duckCylinder`             |
| `duckOrbitId`         | `duckCylinderId`           |
| `maxOrbits`           | `maxCylinders`             |
| `id2orbit`            | `id2cylinder`              |
| `sidechainOrbit`      | `sidechainCylinder`        |
| `BusEffect`           | `KatalystEffect`           |
| `BusContext`          | `KatalystContext`          |
| `BusCompressorEffect` | `KatalystCompressorEffect` |
| `BusDelayEffect`      | `KatalystDelayEffect`      |
| `BusDuckingEffect`    | `KatalystDuckingEffect`    |
| `BusPhaserEffect`     | `KatalystPhaserEffect`     |
| `BusReverbEffect`     | `KatalystReverbEffect`     |
| `busContext`          | `katalystContext`          |

Package renames:

- `io.peekandpoke.klang.audio_be.orbits` → `io.peekandpoke.klang.audio_be.cylinders`
- `io.peekandpoke.klang.audio_be.orbits.bus` → `io.peekandpoke.klang.audio_be.cylinders.katalyst`

### 2d. audio_bridge coordination

- `VoiceData.orbit` → `VoiceData.cylinder`
- `VoiceData.duckOrbit` → `VoiceData.duckCylinder`
- `Feedback.OrbitState` → `Feedback.CylinderState`

**SprudelVoiceData:** Do NOT rename sprudel internal fields. Keep `orbit`/`duckOrbit` in `SprudelVoiceData`. In
`toVoiceData()`, map: `orbit → cylinder`, `duckOrbit → duckCylinder`.

### 2e. Tutorial strings

Tutorial files contain sprudel code strings like `.orbit(1)` — these do NOT change (sprudel DSL preserved). Only rename
Kotlin class references.

### 2f. Verify

```bash
./gradlew clean compileKotlinJvm compileKotlinJs
./gradlew :audio_be:jvmTest :audio_bridge:jvmTest :sprudel:jvmTest
```

### 2g. Commit

---

## Phase 3: Master → Fusion (~6 files)

Smallest phase. Variable/parameter renames only.

| Old             | New             |
|-----------------|-----------------|
| `masterMix`     | `fusionMix`     |
| `masterLeft`    | `fusionLeft`    |
| `masterRight`   | `fusionRight`   |
| "master output" | "fusion output" |

Files: `Cylinders.kt`, `KlangAudioRenderer.kt`, `CylindersCleanupTest.kt`, `optimizations.md`

### Verify

```bash
./gradlew :audio_be:jvmTest
```

### Commit

---

## Phase 4: CPS → RPM (engine API level)

**Important: RPM = CPS * 60. This is a unit conversion, not just a rename.**

### Strategy

- **Internal engine math stays in CPS** (cycles are the natural unit for pattern scheduling)
- **Public API accepts RPM** and converts at the boundary
- **Pattern query interface (`queryEvents`) stays in CPS** — patterns work in cycles

### Renames

| Old                                              | New                            |
|--------------------------------------------------|--------------------------------|
| `KlangCyclicPlayback.updateCyclesPerSecond(cps)` | `updateRpm(rpm)`               |
| `Options.cyclesPerSecond: Double = 0.5`          | `Options.rpm: Double = 30.0`   |
| `KlangPlaybackController.cyclesPerSecond`        | Internal: convert `rpm / 60.0` |
| `Song.cps`                                       | `Song.rpm` (values * 60)       |

### Conversion at boundary

```kotlin
fun updateRpm(rpm: Double) {
    val cps = rpm / 60.0
    // ... existing internal logic uses cps
}
```

### Verify

```bash
./gradlew :klang:jvmTest
```

### Commit

---

## Phase 5: Sprudel Additions (additive only, no renames)

### 5a. Add `rpm` continuous pattern

In `lang_continuous_addons.kt`:

```kotlin
// rpm = cps * 60
internal val _rpm by dslObject { ContinuousPattern { _, _, ctx -> ctx.getCps() * 60.0 } }
@SprudelDsl val rpm: SprudelPattern get() = _rpm
```

### 5b. Add `cylinder()` alias for `orbit()`

In `lang_dynamics.kt`, add `cylinder()` overloads that delegate to `orbit()`:

```kotlin
fun SprudelPattern.cylinder(index: PatternLike? = null): SprudelPattern = orbit(index)
// ... all overload forms (String extension, PatternMapperFn, etc.)
```

### 5c. Add tests for `rpm` and `cylinder()`

### 5d. Verify

```bash
./gradlew :sprudel:jvmTest
```

### Commit

---

## Final Verification

```bash
./gradlew clean jvmTest jsTest
./gradlew :audio_jsworklet:jsBrowserProductionWebpack
```

## Pitfalls to Watch

1. **KSP regeneration:** Run `clean` build after Phase 1 — annotation processor needs to pick up `IgnitorDsl::class`
2. **Tutorial strings:** Sprudel code in tutorials (`.orbit(1)`) must NOT change — only Kotlin class refs
3. **`voices/strip/excite/` directory:** Lives outside main exciter dir — don't forget it in Phase 1
4. **Markdown docs:** `docs/`, `audio_be/optimizations.md`, `audio/ref/` files reference old names — update for
   consistency
5. **SprudelVoiceData mapping:** Keep sprudel fields as-is, adjust `toVoiceData()` mapping only
