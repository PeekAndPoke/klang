Question:
Should the compressor better be per orbit and not per voice?

Answer:

You make a fantastic point, and you've hit on a core audio architecture concept!

**Yes, the compressor should absolutely be per-orbit (bus compression) rather than per-voice.**

Here is why:

1. **The "Glue" Effect:** Compression is most effective when it reacts to the *sum* of the audio in a bus. When your
   kick drum hits, the orbit compressor should squashing the hi-hats slightly, creating that classic pumping "glue"
   effect. If it's per-voice, the kick only compresses itself and the hi-hat only compresses itself.
2. **Performance:** Running a compressor on every single polyphonic voice is a huge waste of CPU. Running one stereo
   compressor at the end of the orbit mix is much more efficient.
3. **Strudel Parity:** In Strudel/TidalCycles, dynamics and spatial effects (like delay, room, compressor, and shape)
   apply to the Orbit bus.

**The Good News:**
Your `Orbit.kt` actually **already has the compressor logic implemented** in `processEffects()`!

However, there is a bug in `Orbit.kt` that explains why you weren't hearing it, even though it was hooked up:

In `Orbit.kt`:

```kotlin
// Compressor
voice.compressor?.let { compressorSettings ->
    compressor = Compressor(
        sampleRate = reverb.sampleRate,
        thresholdDb = compressorSettings.thresholdDb,
        ratio = compressorSettings.ratio,
        kneeDb = compressorSettings.kneeDb,
        attackSeconds = compressorSettings.attackSeconds,
        releaseSeconds = compressorSettings.releaseSeconds
    )
}
```

Because a new `Compressor` instance is created **every single time a voice plays**, the compressor's internal
`envelopeDb` state gets completely wiped and reset to `-100.0` over and over again. The envelope never has time to catch
the audio and actually apply gain reduction continuously!

### The Fix

You should **revert the changes to `AbstractVoice.kt`** (remove the `lazyCompressor` entirely), and instead, update the
compressor parameters in `Orbit.kt` without destroying the instance if one already exists:

```kotlin
// ... existing code ...
// Ducking / Sidechain
voice.ducking?.let { voiceDucking ->
    duckOrbitId = voiceDucking.orbitId
    ducking = Ducking(
        sampleRate = reverb.sampleRate,
        attackSeconds = voiceDucking.attackSeconds,
        depth = voiceDucking.depth
    )
}

// Compressor
val compSettings = voice.compressor
if (compSettings != null) {
    if (compressor == null) {
        // Initialize only once
        compressor = io.peekandpoke.klang.audio_be.effects.Compressor(
            sampleRate = sampleRate,
            thresholdDb = compSettings.thresholdDb,
            ratio = compSettings.ratio,
            kneeDb = compSettings.kneeDb,
            attackSeconds = compSettings.attackSeconds,
            releaseSeconds = compSettings.releaseSeconds
        )
    } else {
        // Update parameters without resetting the envelope follower state
        compressor?.apply {
            thresholdDb = compSettings.thresholdDb
            ratio = compSettings.ratio
            kneeDb = compSettings.kneeDb
            attackSeconds = compSettings.attackSeconds
            releaseSeconds = compSettings.releaseSeconds
        }
    }
}

}

fun clear() {
// ... existing code ...
```

This ensures the orbit compressor survives between notes and accurately tracks the combined audio level of the orbit
over time. Now, alternating settings via `<...>` in Strudel will smoothly modulate the orbit's compressor parameters
while keeping the audio envelope intact!
