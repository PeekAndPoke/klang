# Request:

Hi! We need a way to clean up stale orbits. We need to detect when an orbit is no longer doing anything (contains all
zeros) then we need to disable it.

I suggest the following but maybe you have a more precise idea...

On every VoiceScheduler.process() call we pick one orbit and check if it has all zeros. We do a round-robin approach on
all the active Orbits.

Sounds plausible?

# Plan

This sounds like a solid plan. A round-robin approach is very efficient for this kind of cleanup, keeping the overhead
negligible while ensuring eventual cleanup of unused resources.

Since `VoiceScheduler` logic renders voices *before* mixing happens, putting the silence check inside
`VoiceScheduler.process` could prematurely deactivate an orbit that still has a reverb or delay tail playing out (
because the effects run during the mixing phase).

A "more precise" and safer spot is inside `Orbits.processAndMix`. This ensures we check the orbit **after** all voices
and effects have been processed for the current block.

Here are the edits.

### 1. Update `Orbit.kt` to allow checking for silence and deactivation.

We need to make `isActive` readable so `Orbits` can skip mixing inactive ones, and add the `tryDeactivate` logic.

```kotlin
// ... existing code ...
/** Compressor processor instance */
var compressor: Compressor? = null
private set

// To track if we need to update parameters this block
var isActive = false
private set

/**
 * Update orbit settings from a voice.
 */
fun updateFromVoice(voice: Voice) {
// ... existing code ...
    // Compressor active? (Insert effect on the mix bus)
    compressor?.process(mixBuffer.left, mixBuffer.right, blockFrames)
}

fun tryDeactivate() {
    if (!isActive) return

    // Check if silent
    var silent = true
    val threshold = 0.0001 // -80dB approx

    // Check left channel
    for (sample in mixBuffer.left) {
        if (sample > threshold || sample < -threshold) {
            silent = false
            break
        }
    }

    // Check right channel
    if (silent) {
        for (sample in mixBuffer.right) {
            if (sample > threshold || sample < -threshold) {
                silent = false
                break
            }
        }
    }

    if (silent) {
        isActive = false
    }
}
}
```

### 2. Update `Orbits.kt` to perform the Round-Robin check.

We'll add the `cleanupIndex` state and the check at the end of `processAndMix`. We also optimize the mix loop to skip
inactive orbits.

```kotlin
// ... existing code ...
class Orbits(
    maxOrbits: Int = 16,
    private val blockFrames: Int,
    private val sampleRate: Int,
) {
    private val maxOrbits = maxOrbits.coerceIn(1, 32)
    private val orbits = mutableMapOf<Int, Orbit>()
    private var cleanupIndex = 0

    /**
     * Clear all orbits
     */
    fun clearAll() {
// ... existing code ...
        // Step 3: Mix all orbits to output
        for (orbit in orbits.values) {
            if (!orbit.isActive) continue

            run {
                val masterLeft = masterMix.left
                val orbitLeft = orbit.mixBuffer.left
// ... existing code ...
                val orbitRight = orbit.mixBuffer.right

                for (i in 0 until blockFrames) {
                    masterRight[i] += orbitRight[i]
                }
            }
        }

        // Step 4: Cleanup stale orbits
        // We pick one orbit per block to check for silence
        if (maxOrbits > 0) {
            val orbitToCheck = orbits[cleanupIndex]
            orbitToCheck?.tryDeactivate()

            cleanupIndex = (cleanupIndex + 1) % maxOrbits
        }
    }

/**
 * Gets an orbit.
 *
 * If the orbit exists, it will be returned.
// ... existing code ...
```
