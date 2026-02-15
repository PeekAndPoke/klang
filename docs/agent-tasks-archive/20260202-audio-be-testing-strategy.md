# Audio Backend Testing Strategy

## Overview

After unifying the voice rendering pipeline with `AbstractVoice`, we need a comprehensive testing strategy to ensure:

1. The unified pipeline works correctly for both SynthVoice and SampleVoice
2. Effects are applied in the correct order
3. FM synthesis works as expected
4. Edge cases are handled properly
5. Performance-critical paths don't regress

## Current Test Coverage

### Existing Tests

**FilterModulationTest.kt** (567 lines)

- ✅ Filter modulation with envelopes (attack, decay, sustain, release)
- ✅ Multiple modulators apply independently
- ✅ Control rate (once per render block)
- ✅ Works with both SynthVoice and SampleVoice
- ✅ Voice starting mid-block handles envelope correctly

**SampleVoiceRenderTest.kt** (169 lines)

- ✅ Sample playback with different playback rates
- ✅ Explicit looping behavior
- ⚠️ Some tests commented out (basic playback, stopFrame)

**OscillatorWarmthTest.kt** and **VoiceSchedulerWarmthTest.kt**

- These appear to be warmup/benchmark tests

### Coverage Gaps

The following areas need test coverage:

1. **AbstractVoice Pipeline Ordering** - Verify the 8-stage pipeline executes in correct order
2. **FM Synthesis** - Test frequency modulation with various parameters
3. **Effect Chain Ordering** - Verify pre-filters → main filter → envelope → post-filters
4. **Pitch Modulation** - Test vibrato, accelerate, pitch envelope
5. **ADSR Envelope** - Test all phases (Attack, Decay, Sustain, Release)
6. **Voice Lifecycle** - Test startFrame, endFrame, gateEndFrame edge cases
7. **generateSignal() Implementations** - Test SynthVoice oscillator and SampleVoice playback
8. **Lazy Effect Initialization** - Test Tremolo and Phaser lazy creation
9. **Buffer Management** - Test pitch mod buffer allocation and reuse
10. **Edge Cases** - Zero-length renders, out-of-bounds frames, null effects

---

## Testing Architecture

### Test Helper Pattern

Use shared test helpers to reduce boilerplate and ensure consistency:

```kotlin
// RenderTestHelpers.kt
object VoiceTestHelpers {
    fun createContext(
        blockStart: Long = 0,
        blockFrames: Int = 100,
        sampleRate: Int = 44100
    ): Voice.RenderContext {
        return Voice.RenderContext(
            orbits = Orbits(blockFrames, sampleRate),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voiceBuffer = DoubleArray(blockFrames),
            freqModBuffer = DoubleArray(blockFrames)
        ).apply { this.blockStart = blockStart }
    }

    fun createMinimalSynthVoice(
        startFrame: Long = 0,
        endFrame: Long = 1000,
        vararg effects: Pair<String, Any>
    ): SynthVoice {
        // Create voice with defaults, apply specified effects
    }

    fun createMinimalSampleVoice(
        sample: MonoSamplePcm,
        startFrame: Long = 0,
        endFrame: Long = 1000,
        vararg effects: Pair<String, Any>
    ): SampleVoice {
        // Create voice with defaults, apply specified effects
    }
}
```

### Spy/Mock Pattern for Effects

Create spy implementations to verify effect processing order:

```kotlin
class SpyFilter(val name: String) : AudioFilter {
    val processCalls = mutableListOf<Triple<Int, Int, Int>>() // offset, length, call order

    override fun process(buffer: DoubleArray, offset: Int, length: Int) {
        processCalls.add(Triple(offset, length, processCalls.size))
    }
}
```

---

## Priority Test Cases

### High Priority

#### 1. AbstractVoice Pipeline Order Test

**Test:** `AbstractVoicePipelineTest.kt`

```kotlin
"pipeline executes in correct order" {
    val spyPreFilter = SpyFilter("preFilter")
    val spyMainFilter = SpyFilter("mainFilter")
    val spyPostFilter = SpyFilter("postFilter")

    // Create voice with spy filters
    val voice = createSynthVoice(
        crush = Voice.Crush(4.0), // Will create preFilter
        filter = spyMainFilter,
        distort = Voice.Distort(0.5) // Will create postFilter
    )

    val ctx = createContext()
    voice.render(ctx)

    // Verify order: pre → main → post
    spyPreFilter.processCalls.size shouldBe 1
    spyMainFilter.processCalls.size shouldBe 1
    spyPostFilter.processCalls.size shouldBe 1

    spyPreFilter.processCalls[0].third shouldBe 0 // Called first
    spyMainFilter.processCalls[0].third shouldBe 1 // Called second
    spyPostFilter.processCalls[0].third shouldBe 2 // Called third
}
```

#### 2. FM Synthesis Tests

**Test:** `FmSynthesisTest.kt`

```kotlin
"FM modulates carrier frequency" {
    val carrierFreq = 440.0
    val modulatorRatio = 2.0 // Modulator at 880 Hz
    val depth = 100.0 // 100 Hz deviation

    val fm = Voice.Fm(
        ratio = modulatorRatio,
        depth = depth,
        envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0, 1.0) // Always on
    )

    val voice = createSynthVoice(
        freqHz = carrierFreq,
        fm = fm,
        osc = OscFn.sine()
    )

    val ctx = createContext()
    voice.render(ctx)

    // Verify output has frequency modulation characteristics
    // (e.g., spectral analysis shows sidebands at carrier ± modulator)
}

"FM depth of 0 produces no modulation" {
    val fm = Voice.Fm(ratio = 2.0, depth = 0.0, envelope = /* ... */)
    // Should produce same output as no FM
}

"FM envelope controls modulation intensity" {
    val fm = Voice.Fm(
        ratio = 2.0,
        depth = 100.0,
        envelope = Voice.Envelope(
            attackFrames = 100.0,
            decayFrames = 0.0,
            sustainLevel = 1.0,
            releaseFrames = 0.0
        )
    )
    // Verify modulation depth increases during attack
}
```

#### 3. ADSR Envelope Tests

**Test:** `EnvelopeTest.kt`

```kotlin
"ADSR attack phase increases linearly" {
    val envelope = Voice.Envelope(
        attackFrames = 100.0,
        decayFrames = 0.0,
        sustainLevel = 1.0,
        releaseFrames = 0.0
    )

    val voice = createSynthVoice(envelope = envelope)
    val ctx = createContext(blockFrames = 100)

    voice.render(ctx)

    // Check that envelope increases linearly
    for (i in 1 until 100) {
        val expected = i / 100.0
        ctx.voiceBuffer[i] shouldBe (expected plusOrMinus 0.01)
    }
}

"ADSR decay phase reaches sustain level" {
    val envelope = Voice.Envelope(
        attackFrames = 50.0,
        decayFrames = 50.0,
        sustainLevel = 0.5,
        releaseFrames = 0.0
    )
    // Render at frame 100 (after attack + decay)
    // Verify envelope is at 0.5
}

"ADSR release phase decays to zero" {
    // Test release after gate ends
}

"ADSR with zero attack is immediate" {
    val envelope = Voice.Envelope(
        attackFrames = 0.0,
        decayFrames = 0.0,
        sustainLevel = 1.0,
        releaseFrames = 0.0
    )
    // Verify first sample is at full amplitude
}
```

#### 4. Voice Lifecycle Tests

**Test:** `VoiceLifecycleTest.kt`

```kotlin
"voice does not render before startFrame" {
    val voice = createSynthVoice(startFrame = 100, endFrame = 200)
    val ctx = createContext(blockStart = 0)

    voice.render(ctx)

    // Buffer should be empty (voice hasn't started)
    ctx.voiceBuffer.all { it == 0.0 } shouldBe true
}

"voice does not render after endFrame" {
    val voice = createSynthVoice(startFrame = 0, endFrame = 100)
    val ctx = createContext(blockStart = 100)

    val result = voice.render(ctx)

    result shouldBe false // Voice is done
}

"voice starting mid-block renders partial buffer" {
    val voice = createSynthVoice(startFrame = 50, endFrame = 150)
    val ctx = createContext(blockStart = 0, blockFrames = 100)

    voice.render(ctx)

    // First 50 samples should be 0, next 50 should have audio
    ctx.voiceBuffer.slice(0 until 50).all { it == 0.0 } shouldBe true
    ctx.voiceBuffer.slice(50 until 100).any { it != 0.0 } shouldBe true
}

"voice ending mid-block renders partial buffer" {
    val voice = createSynthVoice(startFrame = 0, endFrame = 50)
    val ctx = createContext(blockStart = 0, blockFrames = 100)

    voice.render(ctx)

    // First 50 samples should have audio, next 50 should be 0
    ctx.voiceBuffer.slice(0 until 50).any { it != 0.0 } shouldBe true
    ctx.voiceBuffer.slice(50 until 100).all { it == 0.0 } shouldBe true
}

"gateEndFrame triggers release phase" {
    val voice = createSynthVoice(
        startFrame = 0,
        gateEndFrame = 100, // Gate ends here
        endFrame = 200,
        envelope = Voice.Envelope(0.0, 0.0, 1.0, 100.0) // Release
    )

    // Render at frame 100, verify release starts
    // Render at frame 150, verify envelope is decreasing
}
```

### Medium Priority

#### 5. Effect Chain Tests

**Test:** `EffectChainTest.kt`

```kotlin
"bit crush applied before main filter" {
    // Create voice with crush and main filter
    // Verify crush happens first
}

"distortion applied after envelope" {
    // Create voice with distortion
    // Verify distortion happens after VCA
}

"tremolo lazy initialization" {
    val voice = createSynthVoice(tremolo = Voice.Tremolo(5.0, 0.5, 0.0, 0.0, null))

    // First render should initialize tremolo
    voice.render(createContext())

    // Second render should reuse same instance
}

"phaser lazy initialization with defaults" {
    val voice = createSynthVoice(
        phaser = Voice.Phaser(
            rate = 1.0,
            depth = 0.5,
            center = 0.0, // Should default to 1000
            sweep = 0.0   // Should default to 1000
        )
    )
    // Verify phaser uses defaults
}
```

#### 6. Pitch Modulation Tests

**Test:** `PitchModulationTest.kt`

```kotlin
"vibrato modulates pitch" {
    val voice = createSynthVoice(
        vibrato = Voice.Vibrato(rate = 5.0, depth = 0.1)
    )
    // Verify output has vibrato characteristics
}

"accelerate changes pitch over time" {
    val voice = createSynthVoice(
        accelerate = Voice.Accelerate(rate = 1.0) // Doubles pitch per second
    )
    // Verify pitch increases
}

"pitch envelope modulates pitch" {
    val pitchEnv = Voice.PitchEnvelope(
        attackAmount = 2.0, // Start 2 octaves higher
        attackFrames = 100.0,
        decayAmount = 0.0,
        decayFrames = 0.0
    )
    // Verify pitch starts high and descends
}

"FM and vibrato combine correctly" {
    // Test that FM + vibrato both apply
}
```

#### 7. SampleVoice Specific Tests

**Test:** `SampleVoicePlaybackTest.kt`

```kotlin
"sample playback with interpolation" {
    val sample = createSample(size = 10)
    val voice = createSampleVoice(sample, rate = 1.5) // Between samples

    // Verify linear interpolation
}

"sample looping wraps correctly" {
    // Test loopStart/loopEnd behavior
}

"sample respects stopFrame" {
    // Test .end() slicing
}

"sample playback rate respects pitch modulation" {
    val voice = createSampleVoice(
        sample = createSample(100),
        rate = 1.0,
        vibrato = Voice.Vibrato(5.0, 0.1)
    )
    // Verify vibrato affects playback speed
}
```

### Low Priority

#### 8. Integration Tests

**Test:** `VoiceIntegrationTest.kt`

- Test full voice with all effects enabled
- Test realistic scenarios (bass note with filter sweep, hi-hat with crush)
- Test multiple voices mixing to same orbit
- Test voice pool management

#### 9. Performance Tests

**Test:** `VoicePerformanceTest.kt`

- Benchmark render() with different configurations
- Verify no allocations in hot path
- Measure effect of lazy initialization
- Profile memory usage

---

## Test Data Strategies

### Sample Data

Create reusable test samples:

```kotlin
object TestSamples {
    val silence = createSample(size = 100) { 0.0f }
    val impulse = createSample(size = 100) { if (it == 0) 1.0f else 0.0f }
    val ramp = createSample(size = 100) { it.toFloat() / 100f }
    val sine = createSample(size = 100) { sin(TWO_PI * it / 100).toFloat() }
}
```

### Oscillator Stubs

Create predictable oscillators for testing:

```kotlin
val constantOsc: OscFn = { buffer, offset, length, phase, _, _ ->
    for (i in 0 until length) buffer[offset + i] = 1.0
    phase
}

val rampOsc: OscFn = { buffer, offset, length, phase, _, _ ->
    for (i in 0 until length) buffer[offset + i] = i.toDouble() / length
    phase
}
```

---

## Testing Best Practices

### 1. Test One Thing at a Time

Each test should verify a single behavior. Use minimal voice configurations.

**Good:**

```kotlin
"attack phase increases linearly" {
    val voice = createSynthVoice(
        envelope = Voice.Envelope(100.0, 0.0, 1.0, 0.0)
        // All other params at defaults
    )
}
```

**Bad:**

```kotlin
"voice works" {
    val voice = createSynthVoice(
        envelope = /* complex */,
        fm = /* complex */,
        vibrato = /* complex */,
        // Too many variables!
    )
}
```

### 2. Use Descriptive Test Names

Test names should describe the scenario and expected outcome:

- "FM modulates carrier frequency"
- "ADSR attack phase increases linearly"
- "voice does not render before startFrame"

### 3. Use Tolerances for Floating Point

Always use `plusOrMinus` for floating point comparisons:

```kotlin
result shouldBe (expected plusOrMinus 0.0001)
```

### 4. Test Edge Cases

- Zero values (attack = 0, depth = 0)
- Maximum values (rate = 10.0, depth = 1.0)
- Null/empty (fm = null, filterModulators = emptyList())
- Boundary conditions (startFrame = endFrame, loopStart = loopEnd)

### 5. Use Parameterized Tests for Similar Cases

When testing similar scenarios with different parameters:

```kotlin
"envelope phases" - {
    listOf(
        Triple("attack", 0.0, 50.0),
        Triple("decay", 50.0, 100.0),
        Triple("sustain", 100.0, 200.0),
        Triple("release", 200.0, 300.0)
    ).forEach { (phase, startFrame, endFrame) ->
        "envelope in $phase phase" {
            // Test each phase
        }
    }
}
```

---

## Implementation Plan

### Phase 1: Core Pipeline (Week 1)

1. Create VoiceTestHelpers
2. Write AbstractVoice pipeline order tests
3. Write ADSR envelope tests
4. Write voice lifecycle tests

### Phase 2: Effects & Modulation (Week 2)

5. Write FM synthesis tests
6. Write pitch modulation tests
7. Write effect chain tests
8. Write lazy initialization tests

### Phase 3: Voice Implementations (Week 3)

9. Write SynthVoice specific tests
10. Write SampleVoice specific tests
11. Uncomment and fix existing SampleVoiceRenderTest tests

### Phase 4: Integration & Performance (Week 4)

12. Write integration tests
13. Write performance benchmarks
14. Measure test coverage
15. Document any remaining gaps

---

## Success Criteria

- **Line Coverage:** >80% for voices package
- **Branch Coverage:** >70% for voices package
- **All Edge Cases:** Tested and documented
- **Performance:** No regressions in render() benchmarks
- **Maintainability:** New effects can be tested using existing patterns

---

## Tools & Frameworks

- **Test Framework:** Kotest (StringSpec style, already in use)
- **Assertions:** Kotest matchers (shouldBe, plusOrMinus, etc.)
- **Test Samples:** MonoSamplePcm with predictable data
- **Spy Pattern:** Custom AudioFilter implementations
- **Coverage:** Built-in Kotlin/JVM coverage tools

---

## Notes

1. **No Mocking Library Needed:** The AudioFilter interface is simple enough to implement spy versions directly.
2. **Focus on Correctness Over Coverage:** Better to have fewer high-quality tests than many weak tests.
3. **Document Assumptions:** If a test makes assumptions (e.g., "oscillator produces sine wave"), document it.
4. **Keep Tests Fast:** Each test should run in <100ms. Use small buffer sizes and short durations.
5. **Refactor Helpers as Needed:** If tests become too verbose, extract more helper functions.
