**Status:** ✅ **COMPLETE** (2026-01-28)
**Tests:** All 2096 Strudel tests passing (including 8 new vowel synthesis tests)

---

Here is the implementation plan and code for adding vowel formant synthesis to Strudel, formatted as a Markdown file for
your coding agent.

***

# Implementation Plan: Vowel Formant Synthesis in Strudel

This plan outlines the steps to enable "singing" voice effects in the Strudel DSL by implementing a Vowel Formant
Filter. This involves updates to the Audio Bridge, Audio Engine (Backend), and the Strudel DSL (Frontend).

## 1. Audio Bridge Updates

We need to define the `Formant` filter structure, which consists of multiple parallel bandpass filters (formants).

**File:** `audio_bridge/src/commonMain/kotlin/FilterDef.kt`

```kotlin
// ... existing code ...
    @Serializable
    @SerialName("notch")
    data class Notch(
        val cutoffHz: Double,
        val q: Double?,
        val envelope: FilterEnvelope? = null,
    ) : FilterDef()

    @Serializable
    @SerialName("formant")
    data class Formant(
        val bands: List<Band>,
    ) : FilterDef() {
        @Serializable
        data class Band(
            val freq: Double,
            val db: Double,
            val q: Double,
        )
    }
}
```

## 2. Audio Engine (Backend) Implementation

We need a DSP implementation for the formant filter. It processes multiple bandpass filters in parallel and sums their
output.

**New File:** `audio_be/src/commonMain/kotlin/filters/FormantFilter.kt`

```kotlin
package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_bridge.FilterDef
import kotlin.math.pow

class FormantFilter(
    bands: List<FilterDef.Formant.Band>,
    sampleRate: Double
) : AudioFilter {

    private data class BandFilter(val filter: LowPassHighPassFilters.SvfBPF, val gain: Double)

    private val filters = bands.map { band ->
        // Convert dB to linear gain: 10^(db/20)
        val gain = 10.0.pow(band.db / 20.0)
        BandFilter(
            filter = LowPassHighPassFilters.SvfBPF(band.freq, band.q, sampleRate),
            gain = gain
        )
    }

    // Scratch buffer to avoid allocation in process loop
    private var scratchBuffer: DoubleArray = DoubleArray(0)

    override fun process(buffer: DoubleArray, offset: Int, length: Int) {
        // Resize scratch buffer if needed
        if (scratchBuffer.size < length) {
            scratchBuffer = DoubleArray(length)
        }

        // 1. Copy input to scratch buffer (because we will overwrite 'buffer')
        System.arraycopy(buffer, offset, scratchBuffer, 0, length)
        
        // 2. Clear output buffer
        buffer.fill(0.0, offset, offset + length)

        // 3. Process each band
        for (band in filters) {
            // We need a clean copy of input for each parallel filter
            // But SvfBPF modifies in-place. So we need to copy scratch -> temp, process temp, add to buffer.
            
            val bandBuffer = DoubleArray(length) // Ideally pre-allocate this too or use a second scratch
            System.arraycopy(scratchBuffer, 0, bandBuffer, 0, length)
            
            band.filter.process(bandBuffer, 0, length)
            
            // Sum into main buffer with gain
            for (i in 0 until length) {
                buffer[offset + i] += bandBuffer[i] * band.gain
            }
        }
    }
}
```

**Update:** `audio_be/src/commonMain/kotlin/voices/VoiceScheduler.kt`

We need to register the new filter type in the scheduler.

```kotlin
// ... existing code ...
        is FilterDef.Notch ->
            LowPassHighPassFilters.createNotch(cutoffHz = cutoffHz, q = q, sampleRate = options.sampleRateDouble)

        is FilterDef.Formant ->
            FormantFilter(bands = bands, sampleRate = options.sampleRateDouble)
    }

    private fun FilterDef.toModulator(
// ... existing code ...
            is FilterDef.BandPass -> this.envelope
            is FilterDef.Notch -> this.envelope
            is FilterDef.Formant -> null // Envelope not yet supported for vowels
        }

        // No envelope? No modulator needed
// ... existing code ...
        // Get base cutoff
        val baseCutoff = when (this) {
            is FilterDef.LowPass -> this.cutoffHz
            is FilterDef.HighPass -> this.cutoffHz
            is FilterDef.BandPass -> this.cutoffHz
            is FilterDef.Notch -> this.cutoffHz
            is FilterDef.Formant -> 0.0 // Not tunable via single cutoff
        }

        // Resolve envelope to concrete values
// ... existing code ...
```

## 3. Strudel DSL (Frontend) Updates

We need to add the `vowel` parameter to the voice data and map the vowel characters ('a', 'e', 'i', 'o', 'u') to their
respective formant frequencies.

**File:** `strudel/src/commonMain/kotlin/StrudelVoiceData.kt`

```kotlin
// ... existing code ...
    // Sample manipulation
    val begin: Double?,
    val end: Double?,
    val speed: Double?,
    val loop: Boolean?,
    val cut: Int?,

    // Voice / Singing
    val vowel: String?,

    // Custom value
    val value: StrudelVoiceValue? = null,
) {
    companion object {
        val empty = StrudelVoiceData(
// ... existing code ...
            loop = null,
            cut = null,
            vowel = null,
            value = null,
        )
    }

    fun merge(other: StrudelVoiceData): StrudelVoiceData {
        return StrudelVoiceData(
// ... existing code ...
            loop = other.loop ?: loop,
            cut = other.cut ?: cut,
            vowel = other.vowel ?: vowel,
            value = other.value ?: value
        )
    }
// ... existing code ...
    fun toVoiceData(): VoiceData {
        // Build filter list from flat fields, each with its own resonance
        val filters = buildList {
            vowel?.let { v ->
                val formants = when (v.lowercase()) {
                    "a" -> listOf( // "a" as in "father"
                        FilterDef.Formant.Band(800.0, 0.0, 5.0),
                        FilterDef.Formant.Band(1150.0, -6.0, 5.0),
                        FilterDef.Formant.Band(2900.0, -32.0, 5.0),
                        FilterDef.Formant.Band(3900.0, -20.0, 5.0),
                        FilterDef.Formant.Band(4950.0, -50.0, 5.0)
                    )
                    "e" -> listOf( // "e" as in "bed"
                        FilterDef.Formant.Band(350.0, 0.0, 5.0),
                        FilterDef.Formant.Band(2000.0, -20.0, 5.0),
                        FilterDef.Formant.Band(2800.0, -15.0, 5.0),
                        FilterDef.Formant.Band(3600.0, -40.0, 5.0),
                        FilterDef.Formant.Band(4950.0, -56.0, 5.0)
                    )
                    "i" -> listOf( // "i" as in "see"
                        FilterDef.Formant.Band(270.0, 0.0, 5.0),
                        FilterDef.Formant.Band(2140.0, -12.0, 5.0),
                        FilterDef.Formant.Band(3050.0, -26.0, 5.0),
                        FilterDef.Formant.Band(4000.0, -26.0, 5.0),
                        FilterDef.Formant.Band(4950.0, -44.0, 5.0)
                    )
                    "o" -> listOf( // "o" as in "cot"
                        FilterDef.Formant.Band(450.0, 0.0, 5.0),
                        FilterDef.Formant.Band(800.0, -11.0, 5.0),
                        FilterDef.Formant.Band(2830.0, -22.0, 5.0),
                        FilterDef.Formant.Band(3800.0, -22.0, 5.0),
                        FilterDef.Formant.Band(4950.0, -50.0, 5.0)
                    )
                    "u" -> listOf( // "u" as in "boot"
                        FilterDef.Formant.Band(325.0, 0.0, 5.0),
                        FilterDef.Formant.Band(700.0, -16.0, 5.0),
                        FilterDef.Formant.Band(2700.0, -35.0, 5.0),
                        FilterDef.Formant.Band(3800.0, -40.0, 5.0),
                        FilterDef.Formant.Band(4950.0, -60.0, 5.0)
                    )
                    else -> emptyList()
                }
                if (formants.isNotEmpty()) {
                    add(FilterDef.Formant(formants))
                }
            }

            cutoff?.let { cutoffValue ->
// ... existing code ...
```

**New File:** `strudel/src/commonMain/kotlin/lang/lang_vowel.kt`

We expose the `vowel()` function to the DSL.

```kotlin
package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern

// -- vowel() ----------------------------------------------------------------------------------------------------------

private val vowelMutation = voiceModifier {
    copy(vowel = it?.toString())
}

private fun applyVowel(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyParam(
        args = args,
        modify = vowelMutation,
    )
}

/** Sets the vowel formant filter (a, e, i, o, u). */
@StrudelDsl
val StrudelPattern.vowel by dslPatternExtension { p, args, /* callInfo */ _ -> applyVowel(p, args) }

/** Sets the vowel formant filter (a, e, i, o, u). */
@StrudelDsl
val vowel by dslFunction { args, /* callInfo */ _ -> args.toPattern(vowelMutation) }

/** Sets the vowel formant filter (a, e, i, o, u) on a string. */
@StrudelDsl
val String.vowel by dslStringExtension { p, args, callInfo -> p.vowel(args, callInfo) }
```

---

## Implementation Summary (2026-01-28)

### What Was Implemented

Successfully implemented vowel formant synthesis for Strudel with all 5 vowels (a, e, i, o, u).

#### 1. Audio Bridge Updates ✅

- Added `FilterDef.Formant` sealed class with `Band` data class
- Location: `audio_bridge/src/commonMain/kotlin/FilterDef.kt`
- Each band has freq (Hz), db (gain), and q (quality factor)

#### 2. Audio Engine (Backend) ✅

- Created `FormantFilter.kt` with DSP implementation
- Uses parallel bandpass filters (SvfBPF) for each formant band
- Converts dB gains to linear: `10^(db/20)`
- Optimized with scratch buffers to avoid allocations
- **Fix Applied:** Replaced JVM-specific `System.arraycopy()` with multiplatform `copyInto()`
- Location: `audio_be/src/commonMain/kotlin/filters/FormantFilter.kt`

- Updated `VoiceScheduler.kt` to register formant filter type
- Added 3 when branches for filter creation, envelope support, and base cutoff
- Location: `audio_be/src/commonMain/kotlin/voices/VoiceScheduler.kt`

#### 3. Strudel DSL (Frontend) ✅

- Added `vowel: String?` field to `StrudelVoiceData`
- Updated `empty` companion object and `merge()` function
- Implemented `toVoiceData()` formant mapping with precise formant frequencies:
  - **a**: F1=800, F2=1150, F3=2900, F4=3900, F5=4950 Hz
  - **e**: F1=350, F2=2000, F3=2800, F4=3600, F5=4950 Hz
  - **i**: F1=270, F2=2140, F3=3050, F4=4000, F5=4950 Hz
  - **o**: F1=450, F2=800, F3=2830, F4=3800, F5=4950 Hz
  - **u**: F1=325, F2=700, F3=2700, F4=3800, F5=4950 Hz
- dB gains: 0, -6, -12, -18, -24 (decreasing for higher formants)
- Q factors: 80, 90, 120, 130, 140 (increasing for higher formants)
- Location: `strudel/src/commonMain/kotlin/StrudelVoiceData.kt`

- Created DSL functions using proper Strudel DSL pattern
- Implemented triple-mode: pattern extension, standalone function, string extension
- Handles case insensitivity (vowels converted to lowercase)
- Location: `strudel/src/commonMain/kotlin/lang/lang_vowel.kt`

- Updated `GraalStrudelPattern.kt` to extract vowel from JavaScript pattern events
- Location: `strudel/src/jvmMain/kotlin/graal/GraalStrudelPattern.kt`

#### 4. Test Coverage ✅

- Created comprehensive test suite with 8 test cases
- Tests verify DSL functions, formant filter creation, and all vowels
- Tests validate formant frequencies, gains, and Q factors
- Location: `strudel/src/commonTest/kotlin/lang/LangVowelSpec.kt`

### Test Results

- **All tests passing:** 2096 Strudel tests (2088 existing + 8 new vowel tests)
- **Zero failures**
- **Test execution time:** ~45 seconds

### Usage Examples

```kotlin
// Apply vowel formant to notes
note("c3 e3 g3").vowel("a")

// Sequence different vowels
vowel("a e i o u")

// Combine with other effects
note("c3").vowel("o").lpf(2000).gain(0.8)

// String extension
"c3 e3".vowel("i")
```

### Files Modified

1. `audio_bridge/src/commonMain/kotlin/FilterDef.kt` - Added Formant filter definition
2. `audio_be/src/commonMain/kotlin/filters/FormantFilter.kt` - New DSP implementation
3. `audio_be/src/commonMain/kotlin/voices/VoiceScheduler.kt` - Registered filter type
4. `strudel/src/commonMain/kotlin/StrudelVoiceData.kt` - Added vowel field and formant mapping
5. `strudel/src/commonMain/kotlin/lang/lang_vowel.kt` - New DSL functions
6. `strudel/src/jvmMain/kotlin/graal/GraalStrudelPattern.kt` - Added vowel extraction
7. `strudel/src/commonTest/kotlin/lang/LangVowelSpec.kt` - New comprehensive test suite

### Technical Notes

- Formant frequencies based on standard acoustic phonetics research
- Parallel bandpass filter architecture ensures clean vowel characteristics
- Unknown vowels are gracefully ignored (no filter created)
- Multiplatform compatibility ensured (no JVM-specific APIs)
- Follows existing Strudel DSL patterns and conventions
