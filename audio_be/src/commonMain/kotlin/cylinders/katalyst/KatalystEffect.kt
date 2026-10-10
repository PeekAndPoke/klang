/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

/**
 * A single processing stage in the orbit bus pipeline.
 *
 * The bus signal flows through the stages of a [KatalystChain], in the order the chain's
 * `KatalystDsl` declares them. The classic chain is
 * **Body → Vowel → Delay → Reverb → Phaser → Compressor → Gain**, with the **Duck** run after
 * every orbit.
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
     * service (its orbit deactivated and will be reconfigured by whichever voice claims it next).
     */
    fun reset()

    /**
     * True while this stage still holds audible energy that would be cut if the orbit deactivated
     * now. Only the delay and the reverb can say yes; every other stage says no, and says it here.
     */
    fun hasTail(): Boolean

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
 * A stage that delays the orbit it runs on: [latencyFrames] frames, in every state, fixed when the stage is built.
 * The chain sums them (`KatalystChain.latencyFrames`), and a chain swap places two chains' weights by that sum
 * (`ChainSwap`). Three stages delay today: the compressor with a lookahead, the distort stage with oversampling, and a
 * `parallel` stage by the union of its branches' oversamplers plus their longest lookahead.
 */
interface KatalystLatentEffect {
    /** Frames this stage delays the orbit by; 0 when it does not. */
    val latencyFrames: Int

    /**
     * The oversamplers inside [latencyFrames], as their stage counts in ascending order, each counting
     * `Oversampler.latencyFrames` of it; the rest is a pure delay. Empty for a stage without one. An oversampler's IIR
     * round trip delays the top of the band more than the bass, so a `parallel` stage matches a branch that lacks one
     * with an unshaped round trip of it (a phase twin) rather than with whole frames.
     */
    val oversamplers: List<Int> get() = emptyList()
}
