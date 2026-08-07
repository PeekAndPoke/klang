/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

/**
 * Declarative, data-driven **master bus** chain — the per-playback loudness and colour stage.
 *
 * A master is an ordered list of [MasterStageDsl] stages applied to a playback's summed bus,
 * *after* its orbits (Katalyst) and *before* the engines are mixed together.
 *
 * Mirrors [PipelineDsl] (per-voice) and [IgnitorDsl] (per-voice exciter): a `@WireFormat` root,
 * registered by name, referenced from `VoiceData.master`. One concept, one word, three hosts —
 * exciter / voice pipeline / master bus.
 *
 * Unlike the pipeline, a master rides *events*: `master(...)` stamps the reference onto a pattern
 * event, so the chain can change over musical time (fades, endings, per-section loudness).
 */
@WireFormat
data class MasterDsl(val stages: List<MasterStageDsl>) {
    companion object {
        /**
         * Unity pass-through — what a playback uses until a `master(...)` event says otherwise.
         *
         * **This MUST stay empty.** The final safety chain (`MasterStage`: brick-wall limiter, DC
         * blockers, clip + interleave) still runs on the *summed* mix, exactly as before. An empty
         * master chain is therefore byte-identical to the pre-MasterDsl engine — a song without
         * `master(...)` sounds exactly like it did. Seeding a limiter here would put two limiters
         * in series and change the sound of every song.
         */
        val default: MasterDsl = MasterDsl(emptyList())

        /** Builds a master chain from an ordered list of stages. Kotlin mirror of `Master.of(…)`. */
        fun of(vararg stages: MasterStageDsl): MasterDsl = MasterDsl(stages.toList())
    }
}

/**
 * One stage in a [MasterDsl] chain, applied to the master bus in list order.
 *
 * Stages are thin declarations — the backend maps each to a shell over the *shared* DSP classes in
 * `audio_be/effects/` (the same `Compressor` / `Reverb` / `DelayLine` the Katalyst orbit effects
 * use). No stage introduces its own DSP implementation.
 *
 * All values are tune-by-ear starting points; the engine is intentionally raw, so nothing here is
 * clamped for "safety" beyond what the underlying DSP already does.
 */
@WireFormat
sealed interface MasterStageDsl {

    /**
     * Make-up gain on the master bus.
     *
     * The structural fix for mixing a song deliberately low (to keep per-orbit compressors out of
     * plop territory) and bringing the level back up at the end of the chain.
     *
     * @param gain linear gain factor (1.0 = unity, 2.0 ≈ +6 dB).
     */
    @WireName("gain")
    data class Gain(
        val gain: Double = 1.0,
    ) : MasterStageDsl

    /**
     * Musical brick-wall limiter on the master bus.
     *
     * Opt-in and distinct from the final *safety* limiter in `MasterStage`, which always runs on
     * the summed mix.
     *
     * Threshold, ratio, knee and release mirror that safety limiter. **Attack and lookahead
     * deliberately do NOT** — this stage is per-playback, so latency here delays this playback
     * against every other one, while the house limiter runs once on the summed mix and can afford
     * it. `MasterDefaultsSyncSpec` asserts both the shared values and the divergence.
     *
     * Note the house limiter is already a brick wall on the summed mix, so this stage is usually
     * for *shaping* rather than peak-catching — which is why lookahead is opt-in here. Stages
     * stack, so three authored limiters with lookahead cost three delay lines.
     *
     * @param thresholdDb ceiling in dBFS.
     * @param ratio compression ratio (20.0 ≈ brick wall).
     * @param kneeDb soft-knee width in dB — a hard corner injects harmonics on every crossing.
     * @param attackSeconds how fast the gain closes: a one-pole attack when [lookaheadSeconds] is 0,
     *   the gain-smoothing length when it is not.
     * @param releaseSeconds envelope release.
     * @param lookaheadSeconds see the field KDoc — opt-in latency, 0 by default.
     */
    @WireName("limiter")
    data class Limiter(
        val thresholdDb: Double = -1.0,     // MasterStage limiter: thresholdDb
        val ratio: Double = 20.0,           // MasterStage limiter: ratio
        val kneeDb: Double = 2.0,           // MasterStage limiter: kneeDb
        val attackSeconds: Double = 0.001,  // MasterStage: AUTHORED_LIMITER_ATTACK_SECONDS
        val releaseSeconds: Double = 0.1,   // MasterStage limiter: releaseSeconds
        /**
         * Lookahead in seconds — how far ahead the limiter sees, and how much it delays this
         * playback. **Defaults to 0: no lookahead, no added latency.**
         *
         * Deliberately different from the house safety limiter, which runs at 5 ms. That one sits
         * on the *summed* mix, so its delay shifts everything together and nothing can desync. This
         * one sits on **one playback's** master bus — so latency here delays this playback against
         * every other one. Opt in only when you want that trade.
         *
         * With lookahead on, `attackSeconds` stops being a one-pole time constant and becomes the
         * gain-smoothing length. Same idea either way — how fast the gain closes — but widen both
         * together: low-frequency cleanliness tracks the smoothing, not the window.
         *
         * Note the master crossfade blends two chains in parallel for ~60 ms, so changing this
         * value live briefly sums the signal with a delayed copy of itself (a comb). Audible as a
         * short phasey sweep on the edit; harmless, and only reachable if you set this at all.
         */
        val lookaheadSeconds: Double = 0.0, // MasterStage: AUTHORED_LIMITER_LOOKAHEAD_SECONDS
    ) : MasterStageDsl

    /**
     * Master reverb — the shared Freeverb `Reverb` (audio_be `effects/`) used as an *insert*: the
     * backend feeds it a copy of the bus scaled by [wet] and mixes its output back.
     *
     * **Every parameter here is the twin of a sprudel one, on the same scale** — a number means the
     * same thing whether you write it on an orbit or on the master. (It did not always: `roomSize`
     * was once raw 0..1 here while sprudel's was 0..10, so the same `3` meant a 1 s tail on an orbit
     * and a 12.5 s one on the master.)
     *
     * @param wet how much of the bus is sent into the reverb (0.0 = off). Orbit twin: `room(x)`.
     * @param roomSize tail length on the **sprudel `roomsize()` scale, ~0..10** (the backend divides
     *   by 10 — see `Reverb.normalizeRoomSize`). 3 ≈ 1 s, 5 ≈ 1.4 s, 10 ≈ 12.5 s. The shortest
     *   reachable tail is ~0.7 s. Values above 10 are bounded — past unity the comb network has no
     *   steady state and runs away (see `Reverb.normalizeRoomSize`). Orbit twin: `roomsize()`/`rsize()`.
     * @param damp Freeverb high-frequency damping, 0 = bright .. 1 = dark. **Ignored when [roomLp]
     *   is set.** No orbit twin (sprudel reaches damping through `roomlp` instead).
     * @param roomFade **overrides [roomSize]** for the tail, and is NOT on the same scale — it is
     *   the normalized 0..1 value directly, and despite the name it is not a time. Orbit twin:
     *   `roomfade()` / `rfade()`. Null = no override.
     * @param roomLp high-frequency damping as an absolute cutoff **in Hz**; overrides [damp].
     *   Orbit twin: `roomlp()` / `rlp()`. Null = no override.
     */
    @WireName("reverb")
    data class Reverb(
        val wet: Double = 0.25,
        val roomSize: Double = DEFAULT_ROOM_SIZE,
        val damp: Double = 0.5,
        val roomFade: Double? = null,
        val roomLp: Double? = null,
    ) : MasterStageDsl {
        companion object {
            /**
             * Default room size on the **authored** scale.
             *
             * `5.0 / 10 == 0.5`, the Freeverb default — so this is behaviour-preserving despite the
             * literal changing. Guarded by `MasterDefaultsSyncSpec`.
             */
            const val DEFAULT_ROOM_SIZE: Double = 5.0
        }
    }

    /**
     * Master delay — the shared `DelayLine` (audio_be `effects/`) used as an *insert* (same
     * send-copy trick as [Reverb]).
     *
     * @param wet how much of the bus is sent into the delay (0.0 = off). Orbit twin: `delay(x)`.
     * @param timeSeconds delay time in seconds. Orbit twin: `delaytime()`.
     * @param feedback feedback amount; ≥ 1.0 recirculates without loss and self-oscillates — allowed
     *   (raw engine), with [cap] deciding how loud. Orbit twin: `delayfeedback()` / `delayfb()`.
     * @param cap ceiling the feedback saturates toward (default 1.0 = unchanged). Orbit twin:
     *   `delaycap()` / `dcap()`.
     */
    @WireName("delay")
    data class Delay(
        val wet: Double = 0.25,
        val timeSeconds: Double = 0.25,
        val feedback: Double = 0.3,
        val cap: Double = 1.0,
    ) : MasterStageDsl
}
