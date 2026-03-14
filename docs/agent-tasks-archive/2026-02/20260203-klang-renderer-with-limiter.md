```kotlin
package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.effects.Compressor
import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import kotlin.math.tanh

/**
 * Standard renderer that drives the DSP graph (VoiceScheduler -> Orbits -> Stereo Output).
 *
 * This class is stateless regarding the playback cursor. It simply renders "what is requested".
 */
class KlangAudioRenderer(
    sampleRate: Int,
    private val blockFrames: Int,
    private val voices: VoiceScheduler,
    private val orbits: Orbits,
) {
    private val mix = StereoBuffer(blockFrames)

    private val limiter = Compressor(
        sampleRate = sampleRate,
        thresholdDb = -1.0,
        ratio = 20.0,
        kneeDb = 0.0,
        attackSeconds = 0.001,
        releaseSeconds = 0.1,
    )

    /**
     * Renders one block of audio starting at [cursorFrame] into the [out] byte array.
     */
    fun renderBlock(
        cursorFrame: Long,
        out: ByteArray,
    ) {
        // 1. Reset Mix Buffers
        mix.clear()
        orbits.clearAll()

        // 2. Process Voices (Oscillators, Samples, Envelopes)
        voices.process(cursorFrame)

        // 3. Mix Voices into Orbits -> Main Mix
        orbits.processAndMix(mix)

        limiter.process(mix.left, mix.right, blockFrames)

        // 4. Post-Process (Limiter + Interleave to 16-bit PCM)
        val left = mix.left
        val right = mix.right

        for (i in 0 until blockFrames) {
// ... existing code ...
```

```kotlin
// ... existing code ...
// 2. Create Renderer
val renderer = KlangAudioRenderer(
    sampleRate = sampleRate,
    blockFrames = blockSize,
    voices = voices,
    orbits = orbits
)

override suspend fun run(scope: CoroutineScope) {
// ... existing code ...
```

```kotlin
// ... existing code ...
orbits = orbits,
)
)
val renderer = KlangAudioRenderer(
    sampleRate = sampleRate,
    blockFrames = blockFrames,
    voices = voices,
    orbits = orbits
)

// Buffers
val renderBuffer = ByteArray(blockFrames * 4) // 16-bit Stereo PCM (4 bytes per frame)
// ... existing code ...
```

Yes, we can definitely add a dynamic limiter!

Currently, `KlangAudioRenderer` uses `tanh` as a "soft clipper" / "hard limiter" at the very end. While this prevents
digital wrapping, it acts as a distortion effect (saturation) when the signal gets too hot, which is likely the "
overdrive" you are hearing.

To fix this, I've added a **dynamic limiter** (using the existing `Compressor` class) *before* the `tanh` stage. This
will automatically ride the volume down when it gets too loud, keeping the sound clean instead of distorting it.

Here are the changes:

1. **`KlangAudioRenderer.kt`**: Added a `Compressor` configured as a fast limiter (-1dB threshold, 20:1 ratio, 1ms
   attack).
2. **`JvmAudioBackend.kt`** & **`KlangAudioWorklet.kt`**: Updated to pass the `sampleRate` required by the new limiter.

```kotlin
package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.effects.Compressor
import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import kotlin.math.tanh

/**
 * Standard renderer that drives the DSP graph (VoiceScheduler -> Orbits -> Stereo Output).
 *
 * This class is stateless regarding the playback cursor. It simply renders "what is requested".
 */
class KlangAudioRenderer(
    sampleRate: Int,
    private val blockFrames: Int,
    private val voices: VoiceScheduler,
    private val orbits: Orbits,
) {
    private val mix = StereoBuffer(blockFrames)

    private val limiter = Compressor(
        sampleRate = sampleRate,
        thresholdDb = -1.0,
        ratio = 20.0,
        kneeDb = 0.0,
        attackSeconds = 0.001,
        releaseSeconds = 0.1,
    )

    /**
     * Renders one block of audio starting at [cursorFrame] into the [out] byte array.
     */
    fun renderBlock(
        cursorFrame: Long,
        out: ByteArray,
    ) {
        // 1. Reset Mix Buffers
        mix.clear()
        orbits.clearAll()

        // 2. Process Voices (Oscillators, Samples, Envelopes)
        voices.process(cursorFrame)

        // 3. Mix Voices into Orbits -> Main Mix
        orbits.processAndMix(mix)

        limiter.process(mix.left, mix.right, blockFrames)

        // 4. Post-Process (Limiter + Interleave to 16-bit PCM)
        val left = mix.left
        val right = mix.right

        for (i in 0 until blockFrames) {
// ... existing code ...
```

```kotlin
// ... existing code ...
// 2. Create Renderer
val renderer = KlangAudioRenderer(
    sampleRate = sampleRate,
    blockFrames = blockSize,
    voices = voices,
    orbits = orbits
)

override suspend fun run(scope: CoroutineScope) {
// ... existing code ...
```

```kotlin
// ... existing code ...
orbits = orbits,
)
)
val renderer = KlangAudioRenderer(
    sampleRate = sampleRate,
    blockFrames = blockFrames,
    voices = voices,
    orbits = orbits
)

// Buffers
val renderBuffer = ByteArray(blockFrames * 4) // 16-bit Stereo PCM (4 bytes per frame)
// ... existing code ...
```

Can we optimize this loop then?

```kotlin
for (i in 0 until blockFrames) {
    // Hard Limiter (tanh)
    val l = (tanh(left[i]).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt()
    val r = (tanh(right[i]).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt()

    // Interleave L/R into Byte Array (Little Endian)
    val idx = i * 4
    out[idx] = (l and 0xff).toByte()
    out[idx + 1] = ((l ushr 8) and 0xff).toByte()
    out[idx + 2] = (r and 0xff).toByte()
    out[idx + 3] = ((r ushr 8) and 0xff).toByte()
}
```

Is there something better than ByteArray for this case? Can we use a ShortArray to avoid the bit-operations

    Yes, using `ShortArray` is a great optimization here! It completely eliminates the bit-shifting overhead in `KlangAudioRenderer` and simplifies the data flow, especially for the AudioWorklet (JS) which can process numbers more naturally than raw bytes.

Here is the plan:

1. **`KlangAudioRenderer`**: Change output to `ShortArray`.
2. **`JvmAudioBackend`**: Adapt to convert `ShortArray` → `ByteArray` (efficiently via `ByteBuffer`).
3. **`KlangAudioWorklet`**: Adapt to use `ShortArray` directly (removing the complex byte unpacking).

```kotlin
// ... existing code ...
/**
 * Renders one block of audio starting at [cursorFrame] into the [out] byte array.
 */
fun renderBlock(
    cursorFrame: Long,
    out: ShortArray,
) {
    // 1. Reset Mix Buffers
    mix.clear()
// ... existing code ...
    limiter.process(mix.left, mix.right, blockFrames)

    // 4. Post-Process (Limiter + Interleave to 16-bit PCM)
    val left = mix.left
    val right = mix.right

    for (i in 0 until blockFrames) {
        // Hard Limiter (tanh)
        val l = (tanh(left[i]).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        val r = (tanh(right[i]).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()

        // Interleave L/R
        val idx = i * 2
        out[idx] = l
        out[idx + 1] = r
    }
}
}
```

```kotlin
package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.osci.oscillators
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import io.peekandpoke.klang.audio_bridge.KlangTime
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.time.Duration.Companion.milliseconds

class JvmAudioBackend(
    config: AudioBackend.Config,
// ... existing code ...
    line.open (format, bufferBytes
)
line.start()

// Pre-allocate the output buffer for one block
val outBytes = ByteArray(blockSize * 4)
val outShorts = ShortArray(blockSize * 2)
val outBuffer = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

try {
    while (scope.isActive) {
        // Get events
// ... existing code ...
    }

    // rendering ///////////////////////////////////////////////////////////////////////////////////////
    // Render into buffer (State Read)
    renderer.renderBlock(cursorFrame = currentFrame, out = outShorts)

    // Convert ShortArray to ByteArray via ByteBuffer view
    outBuffer.clear()
    outBuffer.put(outShorts)

    // Advance Cursor (State Write)
    currentFrame += blockSize

    // 3. Write to Hardware
    // This call blocks if the hardware buffer is full, pacing the loop
    line.write(outBytes, 0, outBytes.size)

    // 60 FPS
    delay(10.milliseconds)
}
} finally {
// ... existing code ...
```

```kotlin
// ... existing code ...
val renderer = KlangAudioRenderer(
    sampleRate = sampleRate,
    blockFrames = blockFrames,
    voices = voices,
    orbits = orbits
)

// Buffers
val renderBuffer = ShortArray(blockFrames * 2) // 16-bit Stereo PCM (2 shorts per frame)
var cursorFrame = 0L

var isPlaying = true
}

private var ctx: Ctx? = null
// ... existing code ...
// Port 0
val output = outputs[0]
val numChannels = output.size
if (numChannels == 0) return@init true

// 1. Render the block into our intermediate ShortArray
renderer.renderBlock(cursorFrame, renderBuffer)

// 2. Convert PCM 16-bit back to Float32 for Web Audio
// renderer.renderBlock interleaves L/R: [L, R, L, R, ...]
for (i in 0 until blockFrames) {
val idx = i * 2

// Read Short and normalize to -1.0..1.0
val lSample = renderBuffer[idx].toFloat() / Short.MAX_VALUE
val rSample = renderBuffer[idx + 1].toFloat() / Short.MAX_VALUE

// Write to output channels
output[0][i] = lSample
if (numChannels > 1) {
output[1][i] = rSample
}
}

cursorFrame += blockFrames

// Forward all feedback messages
// ... existing code ...
```

Here is the implementation plan to switch from `ByteArray` to `ShortArray` in the audio pipeline, optimizing performance
and simplifying data flow.

### 1. `KlangAudioRenderer.kt` (Core Renderer)

Change the `renderBlock` method to output `ShortArray` instead of `ByteArray`.

* **Update Signature**: Change `renderBlock(cursorFrame: Long, out: ByteArray)` to
  `renderBlock(cursorFrame: Long, out: ShortArray)`.
* **Update Logic**:
    * Remove the byte-interleaving loop.
    * Directly assign `Short` values to the `ShortArray`.
    * Use `i * 2` for indexing (left/right pairs) instead of `i * 4`.
    * Retain the `tanh` limiter logic (though it's now applied *after* the compressor/limiter added in the previous
      step, acting as a final safety clipper).

### 2. `JvmAudioBackend.kt` (JVM Backend)

The JVM `SourceDataLine` expects bytes, so we need to bridge the gap efficiently.

* **Update Buffer Allocation**:
    * Create `outShorts = ShortArray(blockSize * 2)`.
    * Keep `outBytes = ByteArray(blockSize * 4)` for the hardware line.
    * Create a `ByteBuffer` wrapping `outBytes` (using `ByteBuffer.wrap(outBytes)`).
    * Set `ByteBuffer` order to `ByteOrder.LITTLE_ENDIAN`.
    * Create a `ShortBuffer` view via `.asShortBuffer()`.
* **Update Render Loop**:
    * Call `renderer.renderBlock(..., outShorts)`.
    * Use `shortBuffer.clear()` and `shortBuffer.put(outShorts)` to efficiently bulk-copy shorts into the byte array
      with correct endianness.
    * Write `outBytes` to the `SourceDataLine` as before.

### 3. `KlangAudioWorklet.kt` (JS Audio Worklet)

The AudioWorklet deals with floats, but our renderer now gives shorts. This is a cleaner conversion than from bytes.

* **Update Buffer**: Change `renderBuffer` from `ByteArray` to `ShortArray(blockFrames * 2)`.
* **Update Render Call**: Pass `renderBuffer` (ShortArray) to `renderer.renderBlock`.
* **Update Conversion Loop**:
    * Iterate `0 until blockFrames`.
    * Read directly from `renderBuffer` as `Short`.
    * Convert to float: `shortValue.toFloat() / Short.MAX_VALUE`.
    * Assign to `outputs` channels.
    * Remove all bit-shifting/byte-unpacking logic.

### 4. `KlangAudioRenderer.kt` (Verification of Limiter)

* Ensure the dynamic `Compressor` (limiter) added in the previous step is still correctly placed *before* the `tanh`
  conversion to `Short`.
* The signal flow should be: `Mix (Double)` -> `Compressor (Double)` -> `tanh + conversion (Short)`.

### Summary of Data Flow Changes

* **Old**: Double (Mix) -> `tanh` -> Int -> Byte shifting -> `ByteArray` -> (JVM: direct write / JS: byte unpacking ->
  Float)
* **New**: Double (Mix) -> `tanh` -> Int -> `Short` -> `ShortArray` -> (JVM: Bulk Copy to ByteBuffer -> Write / JS:
  `Short` to Float cast)

This removes significant bitwise arithmetic overhead in the render loop, especially for the JS/Wasm target where such
operations can be slower than simple array access.

`tanh` is relatively expensive. **Optimization**: Use a **Lookup Table (LUT)** or a **Polynomial Approximation** (like a
Padé approximant) for the soft clipper.
A cubic approximation `x - x^3/3` is valid for small `x` but diverges. A better fast sigmoid/tanh approximation for
audio clipping is: `x / (1 + |x|)` (soft knee) or similar algebraic curves which are much faster than
`java.lang.Math.tanh`.
**Proposed Optimization: Fast Tanh Approximation**
Replace with a fast algebraic sigmoid. `kotlin.math.tanh```` kotlin
// Fast approximation of soft clipping
// x / (1 + abs(x)) is a common one, but goes to +/- 1 asymptotically.
// For hard limiting, we want it to look like tanh.
//
// Common fast tanh approx:
// x * ( 27 + x * x ) / ( 27 + 9 * x * x )
// ... or just use the rational function:
fun fastSoftClip(x: Double): Double {
if (x < -3.0) return -1.0
if (x > 3.0) return 1.0
return x * (27.0 + x * x) / (27.0 + 9.0 * x * x)
}

```
Note: The exact formula depends on desired harmonic distortion characteristics.
However, given you just added a Compressor before this stage, the tanh is now acting as a final safety clipper for peaks that slip through.
