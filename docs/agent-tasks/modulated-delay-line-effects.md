This is where things get fun. By fixing the `DelayLine` to support **fractional delays** (linear interpolation) and *
*short delay times** (<10ms), you have unlocked a whole family of effects and synthesis techniques.

All three of these rely on the same core building block: **The Modulated Delay Line**.

Here is the breakdown of each, the theory behind them, and how to implement them using your new `DelayLine`.

---

### 1. The Flanger

**"The Jet Plane Effect"**

* **What it is:** A sweeping, metallic, "whooshing" sound (think Van Halen's guitar or Daft Punk drums).
* **The Physics:** It mimics two tape machines playing the same track. If you press your thumb on the flange (rim) of
  one reel, it slows down slightly, causing a delay relative to the other.
* **The Math:**
    * **Delay Time:** Very short (1ms to 10ms).
    * **Modulation:** An LFO (Low Frequency Oscillator) slowly moves the delay time back and forth (e.g., oscillating
      between 1ms and 5ms).
    * **Feedback:** High. This creates "Comb Filtering"—constructive and destructive interference that creates deep
      notches in the frequency spectrum. As the delay moves, these notches sweep up and down, creating the "whoosh."

**How to Implement:**

You need a `DelayLine` and a simple LFO (Sine or Triangle wave).

```kotlin
class Flanger(val sampleRate: Int) {
    // 1. Very short max delay needed (e.g. 10ms)
    val delayLine = DelayLine(0.010, sampleRate)
    var lfoPhase = 0.0

    // Params
    var rate = 0.5 // Hz (Slow sweep)
    var depth = 0.002 // Seconds (Swing amount, e.g. 2ms)
    var baseDelay = 0.003 // Seconds (Center point, e.g. 3ms)
    var feedback = 0.7 // High feedback is key for Flangers!

    fun process(input: StereoBuffer, output: StereoBuffer, length: Int) {
        // Update Feedback on the delay line
        delayLine.feedback = feedback

        val angleStep = (rate * 2 * Math.PI) / sampleRate

        for (i in 0 until length) {
            // 2. Calculate LFO value (-1.0 to 1.0)
            val lfo = Math.sin(lfoPhase)
            lfoPhase += angleStep

            // 3. Modulate Delay Time: Center + (LFO * Amount)
            delayLine.delayTimeSeconds = baseDelay + (lfo * depth)

            // 4. Process normally (Output is usually 50% Dry / 50% Wet)
            // Note: You'd need to modify DelayLine to allow per-sample processing 
            // or update delayTimeSeconds every few samples (Control Rate) to save CPU.
        }

        // Actually process the audio buffer
        delayLine.process(input, output, length)
    }
}
```

---

### 2. The Chorus

**"The Thickener"**

* **What it is:** Makes a single instrument sound like multiple instruments playing together. It adds width, shimmer,
  and thickness.
* **The Physics:** When a choir sings, nobody is perfectly in time or perfectly in tune. The slight variations create a
  rich texture.
* **The Math (Doppler Effect):**
    * **Delay Time:** Medium (10ms to 30ms). Long enough that it doesn't sound like a metallic robot (Flanger), but
      short enough that it doesn't sound like an echo.
    * **Modulation:** When you change delay time *while* playing, you change the pitch (Doppler effect). As the delay
      gets shorter, pitch goes up; as it lengthens, pitch goes down.
    * **Feedback:** Very Low or None. We want richness, not robotic resonance.
    * **Stereo:** Crucial. Usually, the Left channel LFO is inverted relative to the Right channel.

**How to Implement:**

Similar to Flanger, but different settings.

```kotlin
class Chorus(val sampleRate: Int) {
    val delayLine = DelayLine(0.050, sampleRate) // 50ms buffer
    var lfoPhase = 0.0

    // Params
    var rate = 1.5 // Faster than flanger
    var depth = 0.002
    var baseDelay = 0.020 // 20ms (The "sweet spot" for Chorus)

    fun process(...) {
        delayLine.feedback = 0.0 // No feedback for Chorus

        // Calculate LFO
        val lfo = Math.sin(lfoPhase)

        // Apply LFO to delay time
        delayLine.delayTimeSeconds = baseDelay + (lfo * depth)

        // Ideally, you process Left and Right with different LFO phases 
        // to get a wide stereo image!
        delayLine.process(input, output, length)
    }
}
```

---

### 3. Karplus-Strong Synthesis

**"The Plucked String"**

* **What it is:** This is **not an effect**; it is a **voice** (Synthesizer). It physically models a guitar or harp
  string.
* **The Physics:** A string vibrates. The vibration travels down the string, hits the bridge, reflects back, and loses a
  little high-frequency energy (dampening) every time it reflects.
* **The Math:**
    1. **Excitation:** Fill the delay buffer with random noise (white noise) for a very short burst. This is the "
       pluck."
    2. **Loop:** The output of the delay is fed back into the input.
    3. **Filtering:** The feedback goes through a simple Low Pass Filter (averaging current sample with previous
       sample). This simulates the string getting duller as it decays.
    4. **Pitch:** The pitch is determined by the length of the delay buffer.
       $$DelaySamples = SampleRate / Frequency$$

**How to Implement:**

You don't use the standard `process()` method here because you are *generating* sound, not processing input.

```kotlin
class KarplusStrongVoice(val sampleRate: Int) {
    // Max delay for low notes (e.g. 50Hz = ~1000 samples)
    private val buffer = DoubleArray(2048)
    private var pos = 0
    private var lastSample = 0.0

    // Play a note
    fun pluck(frequency: Double) {
        // 1. Calculate buffer length for this pitch
        val N = (sampleRate / frequency).toInt()

        // 2. Fill the active part of the buffer with Noise
        for (i in 0 until N) {
            buffer[i] = (Math.random() * 2.0) - 1.0 // White Noise -1 to 1
        }
        pos = 0
    }

    // Generate audio
    fun process(output: DoubleArray) {
        val N = buffer.size // Effectively the pitch length calculated above

        for (i in output.indices) {
            // Read the delay line
            val current = buffer[pos]

            // Output it
            output[i] += current

            // KARPLUS-STRONG ALGORITHM:
            // Calculate feedback: Average of current and previous sample (Low Pass Filter)
            // 0.996 is the "decay" factor. If 1.0, it rings forever.
            val nextSample = (current + lastSample) * 0.5 * 0.996

            lastSample = current

            // Write back into the delay line
            buffer[pos] = nextSample

            // Advance pointer
            pos++
            if (pos >= N) pos = 0
        }
    }
}
```

### Summary of Differences

| Feature           | Flanger                | Chorus             | Karplus-Strong             |
|:------------------|:-----------------------|:-------------------|:---------------------------|
| **Delay Time**    | Very Short (1-10ms)    | Medium (15-30ms)   | Tuned to Pitch (1/Freq)    |
| **Modulation**    | Slow LFO               | Medium LFO         | None (Static)              |
| **Feedback**      | High (creates notches) | None / Low         | 100% (decay loop)          |
| **Key Component** | Comb Filtering         | Detuning / Doppler | Filtered Feedback Loop     |
| **Interpolation** | **Essential**          | **Essential**      | **Essential** (for tuning) |

Your `DelayLine` class, with the linear interpolation you just added, is now capable of doing Flanger and Chorus
directly. For Karplus-Strong, you would usually write a specialized class (like `PluckedString`) because the feedback
loop logic (the low-pass filter) is tightly coupled to the delay buffer.
