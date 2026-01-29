# Strudel Kotlin Port - Next Implementation Tasks

This plan focuses on "High Priority" missing features from `TODOS.MD`, specifically **Pitch Envelopes**, **Sample Loop Controls**, **Distortion**, and **Advanced Sample Slicing**.

## 🎯 Goals

Implement the following sets of features:

1.  **Pitch Envelopes**: Full ADSR-style control for pitch modulation.
2.  **Sample Loop Points**: Fine-grained control over loop boundaries.
3.  **Distortion & Shaping**: Waveshaping and distortion parameters.
4.  **Splice**: Helper function combining slicing and speed adjustment.

---

## 🛠️ Implementation Steps

### 1. Data Structure Updates

You need to add new fields to `StrudelVoiceData` (the DSL data carrier) and `VoiceData` (the audio engine data carrier).

#### A. Modify `strudel/src/commonMain/kotlin/StrudelVoiceData.kt`

Add the following nullable `Double` fields to `StrudelVoiceData` and its `empty` companion object. Update the `merge` function to handle them (prefer non-null `other` value).

**Pitch Envelope:**
*   `pAttack`: Double? (Attack time)
*   `pDecay`: Double? (Decay time)
*   `pRelease`: Double? (Release time)
*   `pEnv`: Double? (Envelope amount/depth)
*   `pCurve`: Double? (Curve shape)
*   `pAnchor`: Double? (Center point of modulation, 0..1)

**Looping:**
*   `loopBegin`: Double? (Loop start point, 0..1)
*   `loopEnd`: Double? (Loop end point, 0..1)

**Distortion:**
*   `distortion`: Double? (Distortion amount)
*   `shape`: Double? (Waveshaping amount)

#### B. Modify `audio_bridge/src/commonMain/kotlin/VoiceData.kt`

Add the exact same fields to the `VoiceData` data class and its `empty` object.

#### C. Update Mapping Logic (`StrudelVoiceData.kt`)

Update the `toVoiceData()` function in `StrudelVoiceData` to map these new fields to the `VoiceData` instance.

---

### 2. DSL Implementation

Implement the functions in the appropriate files. All functions should support:
1.  **Pattern Member**: `StrudelPattern.function()`
2.  **String Extension**: `String.function()`
3.  **Standalone**: `function()` (if applicable/common)

#### A. Pitch Envelopes (`lang_effects.kt`)

Implement these DSL functions mapping to the fields above.

| Function | Alias | Field | Type | Description |
| :--- | :--- | :--- | :--- | :--- |
| `pattack` | `patt` | `pAttack` | Numerical | Attack time of pitch envelope |
| `pdecay` | `pdec` | `pDecay` | Numerical | Decay time of pitch envelope |
| `prelease` | `prel` | `pRelease` | Numerical | Release time of pitch envelope |
| `penv` | `pamt` | `pEnv` | Numerical | Pitch envelope depth (semitones) |
| `pcurve` | `pcrv` | `pCurve` | Numerical | Envelope curve shape |
| `panchor` | `panc` | `pAnchor` | Numerical | Anchor point (default 0) |

*Note: Use `applyNumericalParam` pattern used in other effects.*

#### B. Distortion & Shaping (`lang_effects.kt`)

| Function | Alias | Field | Type | Description |
| :--- | :--- | :--- | :--- | :--- |
| `distort` | `dist` | `distortion`| Numerical | Distortion amount |
| `shape` | - | `shape` | Numerical | Wave shaping amount |

#### C. Sample Loop Controls (`lang_sample.kt`)

| Function | Alias | Field | Type | Description |
| :--- | :--- | :--- | :--- | :--- |
| `loopBegin` | `loopb` | `loopBegin` | Numerical | Loop start position (0-1) |
| `loopEnd` | `loope` | `loopEnd` | Numerical | Loop end position (0-1) |

#### D. Splice (`lang_sample.kt`)

Implement `splice` as a composite function.

*   **Signature**: `splice(n: Int, indexPattern: Pattern)` (and overloads)
*   **Logic**: `splice` is effectively `slice` but with playback speed adjusted so the slice fits the step duration.
*   **Implementation**:
    ```kotlin
    // approximate logic
    fun splice(n: Int, pattern: StrudelPattern): StrudelPattern {
         // Apply slice logic, then multiply speed by n
         return pattern.slice(n).speed(n)
    }
    ```
    *Note: Check `lang_sample.kt` for existing `slice` implementation. You might need to reuse it.*

---

### 3. Verification & Testing

#### A. Create/Update Test File

Create or update `strudel/src/commonTest/kotlin/lang/LangEffectsSpec.kt` (or similar).

#### B. Test Cases

1.  **Pitch Envelope**:
    ```kotlin
    note("c").pattack(0.1).pdec(0.2).pamt(12)
    // Verify VoiceData has pAttack=0.1, pDecay=0.2, pEnv=12.0
    ```
2.  **Looping**:
    ```kotlin
    s("bd").loopb(0.1).loope(0.9)
    // Verify loopBegin=0.1, loopEnd=0.9
    ```
3.  **Distortion**:
    ```kotlin
    s("bd").dist(0.5).shape(0.8)
    // Verify distortion=0.5, shape=0.8
    ```
4.  **Splice**:
    ```kotlin
    s("bd").splice(4, "0 1")
    // Should result in slice settings AND speed set to 4.0
    ```

---

### 4. Checklist for Agent

- [ ] Update `StrudelVoiceData` (fields + merge + toVoiceData)
- [ ] Update `VoiceData` (fields)
- [ ] Implement Pitch DSL in `lang_effects.kt`
- [ ] Implement Distortion DSL in `lang_effects.kt`
- [ ] Implement Loop DSL in `lang_sample.kt`
- [ ] Implement `splice` logic in `lang_sample.kt`
- [ ] Add tests to verify fields are set correctly
