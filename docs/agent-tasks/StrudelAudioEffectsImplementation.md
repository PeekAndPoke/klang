# Implementation Plan: Audio Effects (Phaser, Tremolo) & Aliases

**Goal:** Implement missing audio effects (Phaser, Tremolo) and missing aliases (`sz`, `size`, `o`) to close ~15 items
in `TODOS.MD`.

**Scope:**

1. **Data Structures**: Update `StrudelVoiceData` (Frontend) and `VoiceData` (Bridge).
2. **DSL**: Implement functions in `lang_effects.kt` and `lang_dynamics.kt`.
3. **Tests**: Write unit tests for new functions.
4. **Verification**: Add examples to `JsCompatTestData.kt`.

---

## 1. Data Structures Update

We need to carry the new effect parameters from the DSL pattern to the audio engine bridge.

### 1.1 Update `strudel/src/commonMain/kotlin/StrudelVoiceData.kt`

Add the following fields to the `StrudelVoiceData` data class, `empty` companion object, `merge` method, and
`toVoiceData` mapping.

**Fields to add:**

```kotlin
    // Phaser
/** Phaser modulation speed */
val phaser: Double?,
/** Phaser depth (0-1) */
val phaserDepth: Double?,
/** Phaser center frequency (Hz) */
val phaserCenter: Double?,
/** Phaser sweep range (Hz) */
val phaserSweep: Double?,

// Tremolo
/** Tremolo modulation speed in cycles */
val tremoloSync: Double?,
/** Tremolo depth */
val tremoloDepth: Double?,
/** Tremolo waveform shape/skew (0-1) */
val tremoloSkew: Double?,
/** Tremolo phase offset in cycles */
val tremoloPhase: Double?,
/** Tremolo waveform type (tri, square, sine, saw, ramp) */
val tremoloShape: String?,
```

**Instruction:**

- Add these fields to the main constructor.
- Update `companion object empty` to initialize them to `null`.
- Update `fun merge(other: StrudelVoiceData)` to prefer `other`'s non-null values.
- Update `fun toVoiceData(): VoiceData` to map these fields 1:1 to the bridge `VoiceData`.

### 1.2 Update `audio_bridge/src/commonMain/kotlin/VoiceData.kt`

Add the corresponding fields to the `VoiceData` class (used by the audio engine).

**Fields to add:**

```kotlin
    // Phaser
val phaser: Double?,
val phaserDepth: Double?,
val phaserCenter: Double?,
val phaserSweep: Double?,

// Tremolo
val tremoloSync: Double?,
val tremoloDepth: Double?,
val tremoloSkew: Double?,
val tremoloPhase: Double?,
val tremoloShape: String?,
```

**Instruction:**

- Add fields to the data class.
- Update `companion object empty` to initialize them to `null`.

---

## 2. DSL Implementation

Implement the DSL functions using the standard pattern:

1. Define a `voiceModifier` lambda.
2. Define a private `applyX` function (handling control patterns if numerical).
3. Expose 3 delegates: `StrudelPattern.x`, global `x`, and `String.x`.
4. Implement aliases using simple delegation.

### 2.1 Update `strudel/src/commonMain/kotlin/lang/lang_effects.kt`

**Phaser Functions:**

* **`phaser(speed)`** (Alias: `ph`)
    * *Type:* Numerical
    * *Field:* `phaser`
* **`phaserdepth(amount)`** (Aliases: `phd`, `phasdp`)
    * *Type:* Numerical
    * *Field:* `phaserDepth`
* **`phasercenter(freq)`** (Alias: `phc`)
    * *Type:* Numerical
    * *Field:* `phaserCenter`
* **`phasersweep(freq)`** (Alias: `phs`)
    * *Type:* Numerical
    * *Field:* `phaserSweep`

**Tremolo Functions:**

* **`tremolosync(speed)`** (Alias: `tremsync`)
    * *Type:* Numerical
    * *Field:* `tremoloSync`
* **`tremolodepth(amount)`** (Alias: `tremdepth`)
    * *Type:* Numerical
    * *Field:* `tremoloDepth`
* **`tremoloskew(amount)`** (Alias: `tremskew`)
    * *Type:* Numerical
    * *Field:* `tremoloSkew`
* **`tremolophase(cycles)`** (Alias: `tremphase`)
    * *Type:* Numerical
    * *Field:* `tremoloPhase`
* **`tremoloshape(shape)`** (Alias: `tremshape`)
    * *Type:* String (Non-numerical)
    * *Field:* `tremoloShape`
    * *Note:* Use `applyParam`, NOT `applyNumericalParam`.

**Missing Aliases for Room Size:**

* Add aliases `sz` and `size` for existing `roomsize` / `applyRoomSize`.

### 2.2 Update `strudel/src/commonMain/kotlin/lang/lang_dynamics.kt`

**Missing Alias for Orbit:**

* Add alias `o` for existing `orbit` / `applyOrbit`.

---

## 3. Testing

Create new test files following the style of existing specs (e.g., `LangGainSpec.kt`).

### 3.1 Create `strudel/src/commonTest/kotlin/lang/LangPhaserSpec.kt`

Tests to write:

- `phaser()` sets `phaser` field.
- `phaser()` alias `ph` works.
- `phaserdepth()` sets `phaserDepth`.
- `phaserdepth()` aliases `phd`, `phasdp` work.
- `phasercenter()` sets `phaserCenter`.
- `phasersweep()` sets `phaserSweep`.
- Control pattern support for `phaser("1 2")`.

### 3.2 Create `strudel/src/commonTest/kotlin/lang/LangTremoloSpec.kt`

Tests to write:

- `tremolosync()` sets `tremoloSync`.
- `tremolodepth()` sets `tremoloDepth`.
- `tremoloshape()` sets `tremoloShape` (String).
- Aliases `tremsync`, `tremdepth`, `tremshape` work.

### 3.3 Create `strudel/src/commonTest/kotlin/lang/LangEffectAliasesSpec.kt`

Tests to write:

- `roomsize()` aliases: `sz`, `size`.
- `orbit()` alias: `o`.

---

## 4. JS Compatibility Data

Add examples to `strudel/src/commonTest/kotlin/JsCompatTestData.kt` to ensure we match Strudel JS behavior.

**Add to `patterns` list:**

```kotlin
            // Phaser
Example("Phaser basic", """note("c").phaser(2).phaserdepth(0.8)"""),
Example("Phaser aliases", """note("c").ph(2).phd(0.8)"""),
Example("Phaser center/sweep", """note("c").phc(500).phs(1000)"""),

// Tremolo
Example("Tremolo basic", """note("c").tremolosync(4).tremolodepth(0.5)"""),
Example("Tremolo aliases", """note("c").tremsync(4).tremdepth(0.5)"""),
Example("Tremolo shape", """note("c").tremoloshape("sine")"""),
Example("Tremolo shape alias", """note("c").tremshape("square")"""),

// Missing Aliases
Example("Room Size Alias sz", """note("c").room(0.5).sz(0.9)"""),
Example("Room Size Alias size", """note("c").room(0.5).size(0.9)"""),
Example("Orbit Alias o", """note("c").o(1)"""),
```
