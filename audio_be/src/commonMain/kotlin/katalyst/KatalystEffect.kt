/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.katalyst

/**
 * A single processing stage in the cylinder bus pipeline.
 *
 * The bus signal flows through the stages of a [KatalystChain], in the order the chain's
 * `KatalystDsl` declares them. The classic chain is
 * **Body → Vowel → Delay → Reverb → Phaser → Compressor → Gain**, with the **Duck** run after
 * every cylinder.
 *
 * - **Delay** and **Reverb** are fed from the mix at their position (times `wet`, into a buffer of
 *   their own) and add their return into it
 * - **Phaser** and **Compressor** are insert effects (read/write mix buffer in-place)
 * - **Gain** is the group fader, one multiply of the mix; unity is bit-transparent
 * - **Duck** is a sidechain effect (reads another cylinder's mix buffer as trigger)
 *
 * Each effect checks its own activation state and short-circuits when inactive.
 *
 * Besides [process] a stage answers the lifecycle questions the chain asks of every stage it owns.
 * [reset], [hasTail] and [retire] are **abstract on purpose**: a forgotten `reset` replays a
 * previous owner's filter memory into the next one, a forgotten `hasTail` cuts a live tail, and a
 * forgotten `retire` leaves a rented ring or network inside a shelved cylinder, where nothing will
 * ever hand it back. None of the three shows up as a compile error if the interface answers for
 * you, and there is no safe default for the third in particular: `retire() = reset()` is the safe
 * direction for the SOUND and the unsafe one for the WAREHOUSE. So every stage states all three,
 * even when the answer is "nothing" (see [KatalystGainEffect], whose whole state is one factor).
 *
 * [deniedRents] keeps its default, because it is telemetry: a wrong 0 from a stage that rents
 * nothing is the truth, and a missing count costs a diagnostics number, not a resource.
 *
 * Stateful stages are **classes with named fields**, never SAM lambdas: a captured `var` becomes an
 * `ObjectRef` on Kotlin/JS and costs two indirections per access (`audio/ref/performance.md`).
 */
interface KatalystEffect {

    fun process(ctx: KatalystContext)

    /**
     * Turn off and clear every bit of internal state: the clean slate for a stage that STAYS in
     * service (its cylinder deactivated and will be reconfigured by whichever voice claims it next).
     */
    fun reset()

    /**
     * True while this stage still holds audible energy that would be cut if the cylinder deactivated
     * now. Only the delay and the reverb can say yes; every other stage says no, and says it here.
     */
    fun hasTail(): Boolean

    /**
     * The CHAIN's input is silent from now on (a chain swapped away and draining, `ChainSwap`). A
     * stage may still be fed by a stage before it (a delay's echoes into a room), which its bound
     * keeps crediting as always; a stage whose [hasTail] answers from a conservative bound may bring
     * that bound DOWN to a measurement of what it actually holds now. Never raises a bound, never touches the audio. O(unit),
     * so it is asked rarely (`ChainSwap.TAIL_MEASURE_SECONDS`), never per block. Only the reverb
     * measures; every other stage has nothing to tighten.
     */
    fun measureTail() {}

    /**
     * This stage has just been reset ([reset]) and goes into service on a bus that already SOUNDS: the arriving chain
     * of a swap (`Cylinder.beginFade`, `MasterBus.landChain` after the first block). A stage whose first switch-on
     * after a reset acts at once, because nothing has sounded yet, must not do so here: the delay and the reverb then
     * fade their feed in from 0, as on any switch-on of a bus that has run (KLANG-00428, audit finding L7a-02). Never
     * touches the audio. Only those two answer; `parallel` and a region pass it to their branches, every other stage
     * has no first switch to soften.
     */
    fun joinSoundingBus() {}

    /**
     * Whether the bus this stage runs on is SILENT now, as its host judges it with the tests it already makes (a
     * cylinder: its last silence visit and the voices of the block before, `Cylinder.judgeBus`). Told when the
     * judgement changes and when a chain enters service, before the block's writers run. The output (`MasterBus`)
     * tells nothing: its chain is configured from authored defaults only, so a stage there switches on at its first
     * block (fresh) or under a modulated knob (a one-block ramp), and keeps the fade rule it had. While it holds, the first switch-on of a delay or a reverb snaps its feed: nothing
     * sounds that a snapped wet could turn into a step, so a note that switches a delay on keeps its echo's attack
     * (KLANG-00428, maintainer 2026-11-11: "fade in only when already sounding"). Never touches the audio. A stage
     * that is never told (a chain run without a host, a spec's bare stage) keeps the rule it had: it snaps only while
     * no block has run since its build or reset. Only the delay and the reverb answer; `parallel` and a region pass it
     * to their branches.
     */
    fun busSilent(silent: Boolean) {}

    /**
     * Hand back what the warehouse lent this stage and clear the rest: the stage is going to the
     * shelf with its cylinder. For a stage that rents nothing this is [reset], stated as such; the
     * delay and the reverb release their unit DIRTY instead (see `KatalystChain.retire`).
     */
    fun retire()

    /**
     * Rents the warehouse refused this stage (out of memory). A stage that rents nothing reports 0.
     * The stage degrades rather than dying; the count is surfaced through the diagnostics feedback.
     */
    val deniedRents: Int get() = 0
}

/**
 * A stage that delays the cylinder it runs on: [latencyFrames] frames, in every state, fixed when the stage is built.
 * The chain sums them (`KatalystChain.latencyFrames`), and a chain swap places two chains' weights by that sum
 * (`ChainSwap`). Three stages delay today: the compressor with a lookahead, a
 * `parallel` stage by the union of its branches' oversamplers plus their longest lookahead, and an oversampling
 * region by its round trip plus its body's latency divided down.
 */
interface KatalystLatentEffect {
    /** Frames this stage delays the cylinder by; 0 when it does not. */
    val latencyFrames: Int

    /**
     * The oversamplers inside [latencyFrames], as their stage counts in ascending order, each counting
     * `Oversampler.latencyFrames` of it; the rest is a pure delay. Empty for a stage without one. An oversampler's IIR
     * round trip delays the top of the band more than the bass, so a `parallel` stage matches a branch that lacks one
     * with an unshaped round trip of it (a phase twin) rather than with whole frames.
     */
    val oversamplers: List<Int> get() = emptyList()

    /**
     * [latencyFrames] before it was rounded: an oversampler's group delay is a fraction of a frame. Read by an
     * oversampling region, which divides its body's latency down and rounds once, at its own level (KLANG-00214
     * (oversampling regions)); rounded first, a nested region's latency was off by over half a frame. A stage whose
     * delay is whole frames (a lookahead) keeps the default.
     */
    val exactLatency: Double get() = latencyFrames.toDouble()
}
