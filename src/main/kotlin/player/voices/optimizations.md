    // TODO: possible future performance optimization
    //  - sort activeVoices by their orbit
    //  -> why?
    //
    // From Gemini 3 Pro:
    //
    // Where it might slow down (The Constraints)
    //
    // The only real "cache thrashing" risk comes from the
    // Orbits, and only if you scale up significantly.
    // - Scenario: You have 16 Orbits active.
    // - The Issue:
    //   - Voice 1 writes to Orbit[0].mixBuffer.
    //   - Voice 2 writes to Orbit[15].mixBuffer.
    //   - Voice 3 writes to Orbit[3].mixBuffer.
    //   - Since each orbit's buffers are roughly 16KB (Dry + Send), jumping between
    //     them randomly could evict lines from L1/L2 cache.
    //
    // The Fix (Premature for now): If this ever becomes a problem, you would simply
    // sort the activeVoices list by orbitIndex before the render loop. That way, you finish
    // all work on Orbit 0 (keeping it hot in cache) before moving to Orbit 1.

    // TODO: even deeper future performance optimization
    //  - Question:
    //    We could take this even further ... currently we do:
    //    `orbits.clearAll()` ... but in theory we would only be required to clear the orbits that have current active voice, correct?
    //    The same should be true for  `orbits.processAndMix(masterMixBuffer)`, right?
    //
    // Answer:
    //
    // Yes, absolutely. You only need to touch the Orbits that actually have audio in them.
    //
    // But there is a catch: **Effects Tail**.
    // If an Orbit has a Delay (or Reverb), it continues to produce sound _even after all voices assigned to it have died_.
    // So, "active" has two meanings:
    //  1. **Voice Active:** A voice is currently writing to this Orbit.
    //  2. **Tail Active:** The effects are still ringing out.
    //
    // ### Optimization Strategy
    // We can optimize `processAndMix` and `clearAll` by tracking which orbits are truly "asleep".
    // **Rules for Sleep:** An Orbit can only be skipped if:
    //  1. **Input is silent:** No voices wrote to it this block.
    //  2. **Buffers are empty:** Its `mixBuffer` and `delaySendBuffer` are all zeros.
    //  3. **Effects are silent:** The Delay/Reverb internal buffers have decayed to silence (or below a threshold like -60dB).
    //
    // Since checking #3 is expensive (analyzing the delay line content), a simpler heuristic works well:
    // - We can't skip `processAndMix` for an Orbit just because it has no voices. It needs to run to empty out the Delay buffer.
    // - However, `clearAll` is already optimized by your use of `orbits.values`. If the map only contains active orbits, you're fine.
    //
    // **Wait, your `orbits` map logic:** Currently, `getOrInit` creates an Orbit and puts it in the map.
    // **It never removes it.**. So after 5 minutes of jamming, all 16 orbits will likely be in the map and will
    // be processed forever.
    //
    // ### The Improvement
    // You should track "active" orbits vs "allocated" orbits. Or simply rely on the fact that processing 16 delay lines
    // (even silent ones) is negligible for a modern CPU (it's just moving memory).
    //
    // **My recommendation:** Keep it simple for now. The overhead of checking "is this delay line silent?" per block
    // might be more complex code-wise than just running the loop.
    //
    // However, your logic is slightly redundant: `clearAll`
    //
    // fun clearAll() {
    //     for (orbit in orbits.values) {
    //         orbit.clear()
    //     }
    // }
    //
    // If you wanted to be super optimized, you would only clear the buffers that were _written to_.
    // But `DoubleArray.fill(0.0)` is extremely fast (memset).
    //
    // So, yes, you are right in theory, but in practice, for N <= 16, the current approach is perfectly fine.
    // Optimization matters more when you have N=100s of tracks.
