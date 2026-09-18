/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.peekandpoke.klang.audio_be.Crossfade
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystChain
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystChainBuilder
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystContext
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystDuckEffect
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.cylinders.katalyst.VoiceLease
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.constants.ORBIT_SILENCE_FLOOR

/**
 * Mixing channel / Effect bus — called "Cylinder" in strudel.
 *
 * Each orbit runs one [KatalystChain], the per-orbit effect chain, built from a [KatalystDsl]:
 * **Body → Vowel → Delay → Reverb → Phaser → Compressor** for [KatalystDsl.classic], which is what
 * every cylinder is born with and what every cylinder that is handed no chain name still runs.
 *
 * A `katalyst(…)` reference on the voice stream reaches [requestChain] (Katalyst step 3a,
 * 2026-09-17), which looks the name up in this cylinder's [KatalystRegistry]. On an IDLE orbit the
 * declared chain is installed at once; on a SOUNDING one it is faded in over [Crossfade] while the
 * outgoing chain rings out (step 3b, see [processEffects]). The classic chain reads its knobs from
 * the orbit's OWNER voice, a declared chain from its own slots; see `KatalystChainBuilder`.
 *
 * The duck runs in a separate pass after all orbits are processed (cross-orbit dependency).
 */
// Block-framing ledger D11 (named Class 2 knob): the silence grace before the tail scan is counted
// in BLOCKS, and `Cylinders` visits ONE cylinder per block round-robin, so the wall-clock grace is
// `silentBlocksBeforeTailCheck × allocatedCylinders × blockFrames` — it shrinks with block size and
// grows with orbit count. No audio is cut (the scan itself protects tails), but everything a
// TEARDOWN does rides this schedule: the moment the ring/combs are cleared moves, and so does the
// phaser's clean-slate restart (`Phaser.resetForReuse` zeroes sweep phase AND rate) — a sparse
// pattern whose gaps straddle the grace at one block size but not another re-enters the sweep
// differently (review round 4). `PlaybackEngine` shows the seconds-derived pattern if this ever
// needs pinning to wall time.
class Cylinder(
    id: Int,
    val blockFrames: Int,
    private val sampleRate: Int,
    silentBlocksBeforeTailCheck: Int = 10,
    /** The ring shelf this orbit's delay rents from. Production passes the backend's one warehouse. */
    private val rings: SizedBuffers = SizedBuffers.forRings(sampleRate),
    /** The reverb-unit shelf this orbit's reverb rents from. Same warehouse. */
    private val reverbs: ReverbUnits = ReverbUnits(sampleRate),
    /**
     * Where a chain NAME is resolved. Production passes the renting engine's per-playback fork
     * (through `CylinderUnits.rent`), so a chain dies with the playback that declared it; the
     * default is a private, empty registry, for specs.
     */
    katalysts: KatalystRegistry = KatalystRegistry(),
) {

    companion object {
        /**
         * How many built chains one cylinder keeps, the orbit twin of `MasterBus`'s bound (8).
         *
         * Chain names are content-derived (`KatalystDsl.uniqueId()`), so every EDIT of a chain
         * while live coding mints a new one. Without a bound, each edit's stage instances would be
         * retained for the life of the cylinder. The chain in play is never evicted.
         */
        internal const val MAX_CACHED_CHAINS: Int = 8
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Bus pipeline effects
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * The registry this cylinder resolves chain names against. Re-pointed by [adopt] and DROPPED
     * by [retire]: a shelved cylinder must not hold the registry of a stopped playback, or the
     * shelf would pin every chain that playback ever registered for as long as it keeps the
     * cylinder (the allocation-cleanup rule, 2026-09-17). A cylinder without a registry resolves
     * no name, which is what an idle shelf slot should do.
     */
    private var katalysts: KatalystRegistry? = katalysts

    /**
     * The chain this cylinder is born with, and the ONE chain it can always fall back to: the
     * historical stages, driven by the orbit's owner voice. Never evicted and never cached by
     * name, so "back to classic" (a retired cylinder [adopt]ed for another orbit, or a
     * `Katalyst.classic()` on a pattern) costs no build.
     */
    private val classicChain: KatalystChain = buildChain(KatalystDsl.classic, voiceDriven = true)

    /**
     * This orbit's effect chain: the stage instances own the orbit's DSP state (delay ring, reverb
     * network, compressor envelope, phaser sweep clock), so building is not something a block or a
     * voice may do. Nothing is allocated eagerly that is lazy today: the delay's ring and the
     * reverb's network are still rented on the first activating configure.
     */
    private var chain: KatalystChain = classicChain

    /**
     * The declaration [chain] was built from, for the content test in [requestChain]: two names
     * for one content are one chain (the master's rule, content-addressed), so a repeat request
     * never rebuilds and never resets a live stage.
     */
    private var chainDsl: KatalystDsl = KatalystDsl.classic

    /**
     * The key [chain] was requested under: the LOWERCASED name, which is what
     * [KatalystRegistry.findByKey] and [chains] are keyed by. Null while the cylinder still runs
     * the classic chain.
     */
    private var chainKey: String? = null

    /**
     * The RAW spelling of the last request that landed on [chain], for [requestChain]'s fast
     * path: a re-emitted `katalyst("Bus")` compares equal here and allocates nothing, whatever
     * case it is written in. Null with [chainKey].
     */
    private var chainRawName: String? = null

    /**
     * A requested chain that could not be installed yet, as its registry KEY (normalized once, at
     * request time): the `RegisterKatalyst` command had not arrived, or a fade or drain still held
     * the one [outgoing] slot. Retried per block by [pollPendingChain] and on the next request,
     * never resolved to the classic chain instead (the master's rule: an unknown name must not
     * silently pin an orbit to the wrong chain).
     *
     * A key, not a name, because the retry runs on the render path: [KatalystRegistry.findByKey]
     * allocates nothing on a miss, so a name that never resolves costs one map probe per block
     * instead of two strings (the §7 rule, and the review finding that closed it).
     */
    private var pendingKey: String? = null

    /**
     * Built declared chains by LOWERCASED name (the registry's own key, so two spellings of one
     * chain share one entry), bounded by [MAX_CACHED_CHAINS]. A chain that is not [chain] has been
     * retired (its rented units are back on the shelves), so an idle entry holds nothing but its
     * stage shells and is ready to go straight back into service.
     *
     * "Two names for one content are one chain" holds for the chain IN SERVICE only, where
     * [chainDsl] answers it without a lookup. Two DIFFERENT names that happen to carry equal
     * content get one entry each, because the cache is keyed by name: content hashing a stage list
     * per request would cost more than the one chain it saves, and a content-derived name
     * (`KatalystDsl.uniqueId()`) is what a song normally carries anyway.
     */
    private val chains = mutableMapOf<String, KatalystChain>()

    /** Built chains held by this cylinder, for the specs asserting the cache stays bounded. */
    internal val cachedChainCount: Int get() = chains.size

    /**
     * The chain that is LEAVING service, fading out and then draining; null when neither runs.
     *
     * One slot, so a request arriving while it is taken waits in [pendingKey] instead: the
     * master's retarget policy (a), and the invariant that keeps [chain] and this from ever being
     * the same object (see [requestChain]).
     */
    private var outgoing: KatalystChain? = null

    /**
     * True once the crossfade has completed and [outgoing] is only ringing out: it processes
     * SILENT input and its output is added to the mix at FULL weight until it has no tail left
     * (see [processEffects]).
     */
    private var draining: Boolean = false

    /**
     * The leaving chain's duck, kept in service for the length of a fade because the chain fading
     * IN will not duck: dropping it at the swap would step the orbit's gain by the whole reduction
     * in force, so its EFFECT is crossfaded out instead (see [processDuck]). Null in every other
     * case, including the one where the arriving chain ducks too and simply
     * [KatalystDuckEffect.takeOver]s the envelope.
     */
    private var duckingOut: KatalystDuckEffect? = null

    /**
     * True while the chain fading IN carries a duck and nothing was ducking the orbit before the
     * swap: its effect is ramped in over the fade, the mirror of [duckingOut] (see [processDuck]).
     * False when the envelope was simply carried across ([KatalystDuckEffect.takeOver]), where the
     * reduction is already in force and must not be ramped away and back.
     */
    private var duckFadingIn: Boolean = false


    /** The ramp the two chains are blended over. One per cylinder, created once. */
    private val fade: Crossfade = Crossfade(sampleRate)

    /**
     * Test seams: which phase of a swap this cylinder is in. The audio shows the blend, but a spec
     * about the LIFECYCLE (what [tryDeactivate] refuses, when a queued name lands, when the rented
     * units go back) has to be able to name the phase.
     */
    internal val isFading: Boolean get() = outgoing != null && !draining

    internal val isDraining: Boolean get() = outgoing != null && draining

    /**
     * Rents the warehouse refused chains this cylinder has already swapped AWAY from, summed for
     * this cylinder's life.
     *
     * A stage zeroes its own count when it retires (the count is per unit life, see
     * `KatalystDelayEffect.release`), so without this the diagnostics number would drop back to
     * zero on every chain edit and a shelf that is refusing rents would look healthy again.
     */
    private var retiredDeniedRents: Int = 0

    // The chain's stages by name. Null when the chain declares no such stage, which
    // `KatalystDsl.classic` never does, so every one of them is present on every cylinder that
    // was handed no declaration. These are the CURRENT chain's instances, not the cylinder's: a
    // cylinder no longer knows what a body or a reverb IS, which is the whole point of the step.

    val body get() = chain.body

    val vowel get() = chain.vowel

    val delay get() = chain.delay

    val reverb get() = chain.reverb

    val phaser get() = chain.phaser

    val compressor get() = chain.compressor

    /**
     * The duck that governs this orbit's mix, and the one `Cylinders` resolves the sidechain orbit
     * from before it runs the duck pass.
     *
     * The duck being faded OUT comes first, for the length of the fade: it is what is audible, and
     * it must keep listening to the orbit it was pointed at. The arriving chain's own stage comes
     * second, and it may not even be configured yet (the classic chain declares a duck on every
     * cylinder, so `chain.duck` non-null says nothing about whether it ducks) - a
     * `duckCylinderId` of null is exactly how `Cylinders` skips the pass for such a stage.
     */
    val duck get() = duckingOut ?: chain.duck

    /**
     * The bus effect pipeline, in the order this orbit's chain declares its stages.
     *
     * The duck is NOT in this pipeline; it is applied separately by [Cylinders] after all orbits
     * are processed, because it needs cross-orbit access to the sidechain source.
     */
    val pipeline get() = chain.pipeline

    /**
     * Rents the warehouse refused this orbit, for the diagnostics feedback: the current chain's
     * count plus what the chains before it were refused ([retiredDeniedRents]).
     *
     * Per cylinder LIFE, like every other counter on this path: [retire] and [adopt] zero it, so
     * a shelved cylinder never carries a previous engine's number into the next one.
     */
    val deniedRents get() = retiredDeniedRents + chain.deniedRents + (outgoing?.deniedRents ?: 0)

    // ════════════════════════════════════════════════════════════════════════════
    // Buffers and context
    // ════════════════════════════════════════════════════════════════════════════

    /** Dry mix buffer — voices sum into this */
    val mixBuffer = StereoBuffer(blockFrames)

    /** Delay send buffer — voices write delay sends here */
    val delaySendBuffer = StereoBuffer(blockFrames)

    /** Reverb send buffer — voices write reverb sends here */
    val reverbSendBuffer = StereoBuffer(blockFrames)

    /**
     * Scratch mix for the chain that is leaving service: the dry mix scaled by the outgoing weight
     * during the crossfade, silence during the drain. Allocated once here, never per block: a swap
     * is applied from the render callback, and a cylinder comes off a shelf where nothing may
     * allocate.
     */
    private val fadeBuffer = StereoBuffer(blockFrames)

    /**
     * The leaving chain's own send buffers, the voices' sends scaled by the SAME outgoing weight.
     *
     * Its wet has to fade with its dry, or the two chains' rooms and echoes would both be charged
     * at full level for the length of the fade (up to +6 dB where the two returns are correlated),
     * and at the handover the leaving chain's send input would drop from full to silence in one
     * sample and its drain would then ring out material from AFTER the swap. Two more ramp passes,
     * on fading blocks only; zeroed once when the drain starts.
     */
    private val fadeDelaySendBuffer = StereoBuffer(blockFrames)

    private val fadeReverbSendBuffer = StereoBuffer(blockFrames)

    /**
     * The orbit's mix as it was BEFORE a duck that is leaving service ducked it, so the duck's
     * effect can be crossfaded out per sample (see [processDuck]). Only written while a fade is
     * taking a duck out; the same size and the same "allocated once" rule as the others.
     */
    private val duckFadeBuffer = StereoBuffer(blockFrames)

    /** Shared context for all bus effects */
    val katalystContext = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = mixBuffer,
        delaySendBuffer = delaySendBuffer,
        reverbSendBuffer = reverbSendBuffer,
    )

    /**
     * The context the leaving chain runs in: its own mix buffer and its own send buffers, so the
     * whole ramp (dry and wet) is in its input and the live buffers stay untouched for the chain
     * that is arriving.
     *
     * No send stage ever WRITES a send buffer (`DelayLine.process` and `Reverb.process` read their
     * input and ADD to their target), so the ramped copies are pure input.
     */
    private val fadeContext = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = fadeBuffer,
        delaySendBuffer = fadeDelaySendBuffer,
        reverbSendBuffer = fadeReverbSendBuffer,
    )

    // ════════════════════════════════════════════════════════════════════════════
    // State
    // ════════════════════════════════════════════════════════════════════════════

    /** The orbit this cylinder serves. Re-labelled by [adopt] when a shelved cylinder is rented for another. */
    var id: Int = id
        private set

    private var silentBlocksBeforeTailCheck: Int = silentBlocksBeforeTailCheck

    var isActive = false
        private set

    private var silentBlockCount: Int = 0

    // ONE owner per orbit: the first voice to sound owns ALL of the orbit's bus effects while it is alive
    // (first-writer-wins). Other voices on the orbit are ignored — route to a different orbit if you want
    // different bus settings. Overlapping voices with different reverb/delay/compressor make no musical
    // sense, so we don't support it. This also kills the last-writer-wins per-block flip-flop (and the
    // body-filter rebuild thrash it caused on mixed-material orbits).
    private val lease = VoiceLease()

    /**
     * Whether the voice holding the orbit's lease carries ducking, this block and the one before.
     *
     * The classic chain's duck is written from the OWNER, and its writer CLEARS the duck when the
     * owner has none, so a swap TO a voice-driven chain has to know which of the two will happen
     * before it hands that chain a live envelope ([handOverDuck], [KatalystChain.ducksWith]).
     *
     * Two blocks, because that is the lease's own liveness rule (`VoiceLease`: an owner that misses
     * a block has lapsed), and because a swap is requested at promotion, before this block's voices
     * have offered themselves. NOT the voice itself: a cylinder holding a dead voice would hold its
     * buffers with it (the allocation-cleanup rule).
     */
    private var ownerDucksThisBlock: Boolean = false

    private var ownerDucksLastBlock: Boolean = false

    /** True while a ducking owner is alive by the lease's one-block rule (see above). */
    private val ownerDucks: Boolean get() = ownerDucksThisBlock || ownerDucksLastBlock

    // ════════════════════════════════════════════════════════════════════════════
    // API
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Update orbit settings from a voice. [blockStart] is the current block's start frame, used by the
     * orbit ownership [lease] to tell voices apart across blocks (production passes it via
     * `Cylinders.getOrInit`). Only the OWNER voice's settings are applied; other voices are ignored.
     *
     * Block-framing ledger D14 (named Class 2 knob): bus params apply at BLOCK granularity, never
     * at the onset sample — a new owner's settings also govern the `ctx.offset` samples before its
     * own first sample (the previous owner's still-decaying tail), and the transition frame moves
     * with alignment. Inherent to a block-continuous bus driven by per-voice triggers.
     */
    // blockStart is an ABSOLUTE backend frame — Double, see RenderClock.cursorFrame.
    fun updateFromVoice(voice: Voice, blockStart: Double) {
        isActive = true

        if (lease.claim(voice.id, blockStart, blockFrames)) {
            ownerDucksThisBlock = voice.ducking != null

            // A ducking owner whose FIRST claim lands in the swap's own block. The swap was decided
            // at promotion, before this voice offered itself, so [handOverDuck] saw no owner and
            // chose the ramp-out path. Correct it here, before the writers run: otherwise the orbit
            // ramps the old reduction out while this voice's fresh envelope sits unprocessed behind
            // it, and drops by the whole depth the moment the ramp ends.
            //
            // ONLY in that block ([Crossfade.isAtStart]). Once the ramp has moved, the orbit's gain
            // is already `g * (1 - t) + t` and handing the envelope over would jump it back to `g`
            // (0.053 on a 0.5 probe three blocks in, 0.2 mid-fade). A claim after that keeps
            // ramping out, and the arriving chain's own fresh envelope makes its duck-down on the
            // first block past the ramp: the ducker's documented behaviour for a new owner, not a
            // step the swap put there.
            val lateDuck = duckingOut

            if (lateDuck != null && fade.isAtStart && voice.ducking != null &&
                chain.ducksWith(ownerDucks = true)
            ) {
                chain.duck?.takeOver(lateDuck)
                duckingOut = null
            }

            // The classic chain resolves every knob from the owner voice, as this method's
            // `applyBusEffects` did before the chain existed; a DECLARED chain resolved its slots
            // when it was built and ignores the voice (step 3a).
            chain.applyOwner(voice)

            // The chain FADING OUT is still audible, so it is still configured (step 3b): the
            // owner keeps steering it until the fade ends. Not while it DRAINS: a live config
            // would take its send stages out of their Draining state and point them back at the
            // live sends, which is exactly the ring-out this cylinder is holding them for.
            if (!draining) {
                outgoing?.applyOwner(voice)
            }
        }
    }

    /**
     * Requests the chain registered as [name], the orbit twin of `MasterBus.requestSwap`: the
     * scheduler calls this when it consumes a `katalyst(…)` reference, whether or not the event
     * also sounds.
     *
     * The cases, all of them cheap enough for the promotion path:
     *
     *  - **The name is already running** (the same raw spelling, or the same key): nothing to do,
     *    and a queued request is dropped, because the last expressed intent is the chain already
     *    playing. This is what makes a top-level `katalyst(…)`, which re-emits its event every
     *    cycle, free after the first application, and the RAW compare is what makes it
     *    allocation-free.
     *  - **The name is unknown here** (the `RegisterKatalyst` command has not arrived, or was
     *    dropped): remembered as [pendingKey] and retried per idle block and on the next request.
     *    Never a silent fall back to classic: the caller must be able to tell "unknown, try again"
     *    from "known" (`KatalystRegistry`).
     *  - **The name resolves to the chain already running** (a different name, the same content,
     *    which is what `Katalyst.classic()` is on a fresh cylinder): the name is adopted and
     *    nothing is rebuilt.
     *  - **The cylinder is idle**: installed now, which is the one moment no crossfade is needed.
     *  - **The cylinder is sounding**: faded in now (step 3b), over [Crossfade], while the chain
     *    it replaces fades out and then rings out (see [processEffects]).
     *  - **A fade or drain is already running**: queued in [pendingKey] and started when the one
     *    outgoing slot frees. Cutting a running fade would drop its outgoing chain at full weight,
     *    the very click the crossfade exists to prevent (the master's retarget policy (a)). A
     *    request for the chain fading IN is the first case above and costs nothing; a request for
     *    the chain fading OUT is queued like any other rather than reversing the ramp, because
     *    reversing needs the outgoing chain's name, key and declaration tracked alongside the
     *    incoming one's for a case that is one live-coding undo inside 60 ms (decided 2026-09-17,
     *    the stone rule on complexity).
     *
     * The name is normalized ONCE here, past the raw fast path, and every later hop (the registry
     * lookup, the cache, the retry) takes that key: §7's "resolve on request or registration,
     * never per block".
     */
    fun requestChain(name: String) {
        if (name == chainRawName) {
            pendingKey = null

            return
        }

        // The ONE normalization: the key the registry and the cache are both keyed by.
        val key = name.lowercase()

        if (key == chainKey) {
            // The chain already playing, under another spelling. Adopt the spelling too, so the
            // next re-emission takes the fast path above.
            chainRawName = name
            pendingKey = null

            return
        }

        val dsl = katalysts?.findByKey(key)

        if (dsl == null) {
            pendingKey = key

            return
        }

        if (dsl == chainDsl) {
            // Content-addressed, exactly as the master is: the chain playing IS this chain, so
            // adopting its name makes every repeat a single string compare.
            chainKey = key
            chainRawName = name
            pendingKey = null

            return
        }

        if (isActive && outgoing != null) {
            // The one outgoing slot is taken by a running fade or drain: wait, never cut.
            pendingKey = key

            return
        }

        // The last expressed intent wins: an earlier request that is still queued (unknown then,
        // or waiting behind a fade) must not land after this one.
        pendingKey = null

        if (isActive) {
            beginFade(key, name, dsl)

            return
        }

        install(key, name, dsl)
    }

    /**
     * Installs a chain that is queued on this orbit, polled once per block by
     * [Cylinders.processAndMix].
     *
     * This is what makes a LATE registration land: the request arrived before its
     * `RegisterKatalyst` command and no further request is coming (a one-shot `.katalyst(…)` on a
     * single note emits once), so without a poll the chain would wait for the orbit's next note.
     * It is also what starts a QUEUED swap once the fade it waited behind has finished, on a
     * sounding orbit that never goes silent. Costs one field read per cylinder per block on the
     * normal path.
     *
     * On the STUCK path (a name that never resolves, because its `RegisterKatalyst` was dropped)
     * it costs one map probe per registry in the fork chain, and still no allocation: the retry
     * takes the key [requestChain] normalized once, through [KatalystRegistry.findByKey]. A poll
     * that re-normalized the name here would be a string per cylinder per block on the audio
     * thread, which is what the delta review caught.
     */
    fun pollPendingChain() {
        if (pendingKey == null) {
            return
        }

        installPending()
    }

    fun clear() {
        if (!isActive) return

        mixBuffer.clear()
        delaySendBuffer.clear()
        reverbSendBuffer.clear()
    }

    /**
     * Processes all bus effects in the chain's order: Body → Vowel → Delay → Reverb → Phaser →
     * Compressor for the classic chain.
     *
     * The duck is NOT processed here, see [Cylinders.processAndMix].
     *
     * **While a chain swap is in flight, two chains run** (step 3b, the master's dual-chain
     * crossfade on the orbit bus):
     *
     *  - **Fading.** Both chains see the same dry mix and the same sends, the outgoing one through
     *    its own ramped copies ([fadeBuffer], [fadeDelaySendBuffer], [fadeReverbSendBuffer]) and
     *    the incoming one through the live buffers. The ramp is applied to the outgoing chain's
     *    INPUT, dry and wet alike, and to the incoming chain's OUTPUT, per sample
     *    ([Crossfade.rampDown], [Crossfade.rampUpAndAdd]), which is what makes the handover to the
     *    drain continuous. See [Crossfade] for why the master's output blend would step here. The
     *    incoming chain is warmed up on real audio for the whole fade: its compressor envelope and
     *    its room have settled by the time it carries full weight, the same reason the master bus
     *    runs both chains in parallel.
     *  - **Draining.** One block after the ramp runs out (see above) the outgoing chain leaves
     *    service, and it is NOT retired. Its send stages
     *    get their off-config ([KatalystChain.drainSends]) and it keeps processing on SILENT input
     *    with its output added at FULL weight, so the echoes and the room it had already scheduled
     *    ring out on their own timeline instead of being cut mid-tail. The existing closed-form
     *    countdowns bound it (`KatalystDelayEffect`, `KatalystReverbEffect`), and the chain retires
     *    (units back to the shelves) on the first block [KatalystChain.hasTail] is false.
     *
     *    The master bus accepted the CUT for its v1 and noted "if audible, extend the old chain's
     *    life" (decided 2026-09-17: the orbit extends it from the start, because its send effects
     *    already own the drain; nothing here invents a decay).
     */
    fun processEffects() {
        if (!isActive) return

        // The owner's check-in ages one block here, the one place that runs once per block per
        // orbit and after the voices have offered themselves (see [ownerDucksThisBlock]).
        ownerDucksLastBlock = ownerDucksThisBlock
        ownerDucksThisBlock = false

        val fading = outgoing

        if (fading != null && !draining && fade.isComplete) {
            // The ramp ran out on the PREVIOUS block and the duck pass that follows it has had its
            // last turn, so the chain leaves service now rather than in the middle of the block
            // that finished the ramp (see [processDuck]: the duck runs in a later pass than this
            // one, and a chain retired here would take the orbit's gain envelope with it).
            beginDrain(fading)
        }

        // Re-read: the chain may have retired on its way out of the line above.
        val leaving = outgoing

        if (leaving == null) {
            chain.process(katalystContext)

            return
        }

        if (draining) {
            fadeBuffer.clear()
            leaving.process(fadeContext)
            chain.process(katalystContext)
            addFadeBufferToMix()

            if (!leaving.hasTail()) {
                retireOutgoing(leaving)
            }

            return
        }

        // The two halves of one block's ramp, with both chains processed in between: the outgoing
        // chain is handed a shrinking dry mix AND shrinking sends, the incoming chain's own output
        // is ramped up, and the mix buffer is what the rest of the engine reads. Three rampDowns,
        // one weight: `rampDown` does not advance the ramp, `rampUpAndAdd` does.
        fade.rampDown(target = fadeBuffer, source = mixBuffer, frames = blockFrames)
        fade.rampDown(target = fadeDelaySendBuffer, source = delaySendBuffer, frames = blockFrames)
        fade.rampDown(target = fadeReverbSendBuffer, source = reverbSendBuffer, frames = blockFrames)
        leaving.process(fadeContext)
        chain.process(katalystContext)
        fade.rampUpAndAdd(target = mixBuffer, outgoing = fadeBuffer, frames = blockFrames)
    }

    /** The draining chain's ring-out, at full weight: it is the orbit's own tail, not a second mix. */
    private fun addFadeBufferToMix() {
        val mixLeft = mixBuffer.left
        val mixRight = mixBuffer.right
        val fadeLeft = fadeBuffer.left
        val fadeRight = fadeBuffer.right

        for (i in 0 until blockFrames) {
            mixLeft[i] = mixLeft[i] + fadeLeft[i]
            mixRight[i] = mixRight[i] + fadeRight[i]
        }
    }

    /**
     * The crossfade has finished: the outgoing chain leaves service and rings out, or retires on
     * the spot when it holds nothing that can ring (a chain without a delay and without a reverb,
     * where [KatalystChain.drainSends] has nothing to turn off).
     */
    private fun beginDrain(leaving: KatalystChain) {
        leaving.drainSends()
        // Once, not per block: a draining delay or reverb reads its own `silentInput` and never
        // these, and every other stage of a drained chain is off. Zeroing them here is what makes
        // that a property of the buffers rather than a promise about the stages.
        fadeDelaySendBuffer.clear()
        fadeReverbSendBuffer.clear()
        // The ramp is over, so no further block may blend with it: [processDuck] normally clears
        // these on the ramp's last block, but an orbit whose sidechain has meanwhile disappeared
        // never runs that pass, and `Crossfade.blendHeld` would then replay that block's weights.
        duckingOut = null
        duckFadingIn = false

        if (leaving.hasTail()) {
            draining = true

            return
        }

        retireOutgoing(leaving)
    }

    /** The outgoing chain is done: its rented units go back to the shelves and the slot frees. */
    private fun retireOutgoing(leaving: KatalystChain) {
        // Carried BEFORE the retire, which zeroes the stage's own count: the orbit's diagnostics
        // number is about this cylinder's life, not about its current chain.
        retiredDeniedRents += leaving.deniedRents
        leaving.retire()
        outgoing = null
        draining = false
        duckFadingIn = false
        // The backstop for the duck being faded out: [processDuck] drops it on the ramp's last
        // block, but an orbit whose sidechain orbit has meanwhile disappeared never runs that pass
        // at all, and a duck belonging to a retired chain must not outlive it.
        duckingOut = null
    }

    /**
     * Applies ducking using the resolved sidechain buffer.
     *
     * Called by [Cylinders] after all orbits have processed their main pipeline,
     * since ducking needs cross-orbit access.
     */
    fun processDuck(sidechainMixBuffer: StereoBuffer?) {
        if (!isActive) return

        katalystContext.sidechainBuffer = sidechainMixBuffer

        val leavingDuck = duckingOut

        if (duckFadingIn) {
            // The mirror of the branch below: the arriving chain's duck runs as it always does,
            // and the UN-ducked mix is blended back in with the weights reversed, so the orbit's
            // gain travels from "not ducked" to "ducked" across the fade instead of dropping by
            // the whole reduction on the first sample the trigger is seen.
            mixBuffer.left.copyInto(duckFadeBuffer.left, 0, 0, blockFrames)
            mixBuffer.right.copyInto(duckFadeBuffer.right, 0, 0, blockFrames)
            chain.processDuck(katalystContext)
            fade.blendHeld(
                target = mixBuffer,
                incoming = mixBuffer,
                outgoing = duckFadeBuffer,
                frames = blockFrames,
            )

            if (fade.isComplete) {
                duckFadingIn = false
            }

            katalystContext.sidechainBuffer = null

            return
        }

        if (leavingDuck != null) {
            // The chain fading in declares no duck, so the orbit's gain has to travel from
            // "ducked" to "not ducked" across the fade, per sample like every other weight in the
            // swap. The duck runs on the mix as it always does, and the UN-DUCKED mix is then
            // blended back in with the weights this block's chains were blended with.
            //
            // NOT by ramping the duck's depth: that knob can only be written per block, and a
            // per-block step in a gain that multiplies the whole orbit is a zipper (measured at
            // 0.038 on a 0.5 probe with depth 0.8, and the envelope's own release cannot be forced
            // to reach 1.0 inside the fade window, so the residue steps again when the duck goes).
            mixBuffer.left.copyInto(duckFadeBuffer.left, 0, 0, blockFrames)
            mixBuffer.right.copyInto(duckFadeBuffer.right, 0, 0, blockFrames)
            leavingDuck.process(katalystContext)
            fade.blendHeld(
                target = mixBuffer,
                incoming = duckFadeBuffer,
                outgoing = mixBuffer,
                frames = blockFrames,
            )

            if (fade.isComplete) {
                // This block's last sample was fully un-ducked, so the duck is inaudible now and
                // this is where it goes. Here and not in [beginDrain], which runs one pass EARLIER
                // in the same block: dropping it there would leave this block un-ducked and step
                // the orbit by the weight the ramp had not covered yet. The tail the chain is
                // about to ring out is not ducked either way (it is added to the mix after the
                // incoming chain's inserts, see [Crossfade]).
                duckingOut = null
            }
        } else {
            chain.processDuck(katalystContext)
        }

        katalystContext.sidechainBuffer = null
    }

    /**
     * Checks if the orbit is silent and deactivates it if so.
     *
     * Uses a two-phase approach to avoid cutting off effect tails (delay/reverb):
     * 1. When mixBuffer is silent, increment a counter instead of deactivating immediately.
     *    This grace period keeps effects processing so their tails continue to decay naturally.
     * 2. After N silent blocks, scan effect internal buffers. If they still have audio, reset
     *    the counter and keep processing. If silent, deactivate.
     */
    /**
     * Retires this cylinder for the shelf (resource warehouse, cylinders): every bus effect off and
     * cleared, the lease freed, the send buffers zeroed, and the rented units (delay ring, reverb
     * network) handed back to THEIR shelves — a shelved cylinder holds nothing. The same clean slate
     * [tryDeactivate] reaches, plus the return. Only for a cylinder that will never render again
     * on its current orbit: `CylinderUnits.giveBack` is the one caller.
     */
    fun retire() {
        startNewLife()
        // The registry goes with the engine that lent it (see [katalysts]).
        katalysts = null
    }

    /**
     * Re-labels a cylinder for orbit [id] under the renting `Cylinders`' settings, with
     * [katalysts] as its new registry: the previous engine's chains die with it, and this cylinder
     * starts over from the classic chain.
     */
    fun adopt(id: Int, silentBlocksBeforeTailCheck: Int, katalysts: KatalystRegistry) {
        this.id = id
        this.silentBlocksBeforeTailCheck = silentBlocksBeforeTailCheck
        this.katalysts = katalysts
        // The SAME clean slate [retire] reaches, not a subset: a cylinder normally arrives here
        // through the shelf, but a caller that re-labels one directly must not be the path where a
        // ring stays inside a chain nobody can reach any more.
        startNewLife()
    }

    /**
     * The clean slate a cylinder starts a life on: every chain it built goes back to the shelf it
     * rented from, the cache is dropped, the orbit runs the classic chain again with no owner, the
     * buffers are silent and the orbit is inactive. Shared by [retire] (which additionally drops
     * the registry) and [adopt], so the two can never reach different states.
     *
     * The chains' retire, NOT their reset: the rented units go back DIRTY and the warehouse's
     * housekeeping zeroes them a block at a time (see `KatalystChain.retire`). EVERY chain, not
     * just the current one: a cached chain holds no rent, but retiring it is what makes that true,
     * and it must stay true for the next engine that rents this cylinder. Retire is idempotent, so
     * the current chain being one of them costs nothing.
     */
    private fun startNewLife() {
        chain.retire()
        classicChain.retire()
        // Any fade or drain ends here, at whatever weight it had reached: this cylinder is not
        // going to render again for the orbit it was fading FOR. The chain is normally the classic
        // one or a cache entry (eviction never takes it), so the retires around this line already
        // cover it; the call is stated anyway, because retire is idempotent and a stranded ring is
        // the one mistake this method exists to make impossible.
        outgoing?.retire()
        outgoing = null
        draining = false
        duckingOut = null
        duckFadingIn = false

        for (cached in chains.values) {
            cached.retire()
        }

        chains.clear()
        retiredDeniedRents = 0
        selectClassicChain()
        lease.reset()
        ownerDucksThisBlock = false
        ownerDucksLastBlock = false
        mixBuffer.clear()
        delaySendBuffer.clear()
        reverbSendBuffer.clear()
        fadeBuffer.clear()
        fadeDelaySendBuffer.clear()
        fadeReverbSendBuffer.clear()
        duckFadeBuffer.clear()
        isActive = false
        silentBlockCount = 0
    }

    fun tryDeactivate() {
        if (!isActive) return

        if (!isMixBufferSilent()) {
            silentBlockCount = 0
            return
        }

        silentBlockCount++

        if (silentBlockCount < silentBlocksBeforeTailCheck) return

        // State-aware like the delay's: a draining reverb reports its tail BY CONSTRUCTION, so
        // the orbit stays alive until the countdown's terminal reset — the old param-gated scan
        // hid a still-charged network the moment a no-reverb owner zeroed size.
        //
        // The outgoing chain counts too, and the slot itself is the test (step 3b): while a FADE
        // runs the orbit must keep rendering until the ramp completes, whatever either chain
        // holds, and while the outgoing chain DRAINS it has a tail by construction:
        // [processEffects] frees the slot on the first block it does not.
        if (chain.hasTail() || outgoing != null) {
            silentBlockCount = 0
            return
        }

        isActive = false
        silentBlockCount = 0

        // A chain that was requested while this orbit was sounding lands HERE, the first moment
        // the swap is inaudible. The install retires the outgoing chain (units back to the
        // shelves) and frees the lease, which is the clean slate the reset below would reach, so
        // only one of the two runs: resetting first would zero a ring we are about to hand back
        // dirty on purpose (see KatalystChain.retire).
        if (installPending()) {
            return
        }

        // Free the orbit lease and reset all bus effects so a reused/reactivated orbit starts clean and
        // is reconfigured by whichever voice next claims it.
        chain.reset()
        lease.reset()
        ownerDucksThisBlock = false
        ownerDucksLastBlock = false
    }

    private fun isMixBufferSilent(): Boolean {
        // Shared with the voice cull floor (VOICE_CULL_FLOOR), so a culled voice is by definition
        // below what keeps an orbit alive.
        val threshold = ORBIT_SILENCE_FLOOR
        for (sample in mixBuffer.left) {
            if (sample > threshold || sample < -threshold) return false
        }
        for (sample in mixBuffer.right) {
            if (sample > threshold || sample < -threshold) return false
        }
        return true
    }

    /**
     * Installs the queued chain if there is one and it can be resolved by now: at once on an idle
     * orbit, through a crossfade on a sounding one. Returns true when a chain was installed or a
     * fade was started, so the caller knows the lease and the outgoing chain are dealt with.
     *
     * A name that is still unknown STAYS queued: the registration may yet arrive. So does one that
     * is waiting behind a running fade or drain.
     */
    private fun installPending(): Boolean {
        val key = pendingKey ?: return false

        if (isActive && outgoing != null) {
            // Still behind a fade or a drain, and the registry is not even asked: the one slot is
            // what this name waits for.
            return false
        }

        // The key door, never the normalizing one: this runs on the render path, every block the
        // name is still unknown.
        val dsl = katalysts?.findByKey(key) ?: return false

        pendingKey = null

        if (dsl == chainDsl) {
            chainKey = key
            chainRawName = key

            return false
        }

        if (isActive) {
            beginFade(key, key, dsl)

            return true
        }

        install(key, key, dsl)

        return true
    }

    /**
     * Swaps [chain] for the chain [name] declares, at once. Called only while this cylinder is
     * IDLE, so there is nothing audible to crossfade; a sounding orbit goes through [beginFade].
     *
     * **The outgoing chain is retired EAGERLY** (decided with the maintainer, 2026-09-17): its
     * rented ring and network go back to their shelves the moment it leaves service, and only its
     * stage shells stay in the cache. The price is that a swap BACK re-rents, which can cost a
     * synchronous unit reset (a shelved ring is zeroed on return, ~27k stores for the smallest
     * class) and can be DENIED under shelf pressure, leaving that chain's echo or room absent
     * until the next rent. That is the trade the warehouse exists for: keeping up to
     * [MAX_CACHED_CHAINS] chains' units alive per orbit would hold megabytes per orbit for chains
     * nobody is playing, and a swap is an edit-time event, not a per-block one. [beginFade] keeps
     * two chains alive for the length of the fade and the outgoing chain's ring-out, which is the
     * one case that needs both.
     *
     * The incoming chain needs no reset: nothing that is not the current chain holds state.
     *
     * The incoming chain is never the outgoing one, so retiring after the assignment cannot wipe
     * what was just installed: both callers return early when the request resolves to the content
     * already playing, and [chainDsl] tracks that content one-to-one with [chain].
     */
    private fun install(key: String, rawName: String, dsl: KatalystDsl) {
        val next = chainFor(key, dsl)
        val leaving = chain

        chain = next
        chainDsl = dsl
        chainKey = key
        chainRawName = rawName

        // Carried BEFORE the retire, which zeroes the stage's own count: the orbit's diagnostics
        // number is about this cylinder's life, not about its current chain.
        retiredDeniedRents += leaving.deniedRents
        leaving.retire()
        // The next voice on this orbit becomes the owner cleanly and writes the new chain's
        // stages, instead of the incoming chain waiting for the previous owner to die.
        lease.reset()
    }

    /**
     * Starts the crossfade from the chain in service to the one [name] declares: the swap of a
     * SOUNDING orbit (step 3b). Both chains run from the next processed block (the start is
     * block-quantized, see [Crossfade]) until the ramp completes, and the outgoing chain then
     * rings out (see [processEffects]).
     *
     * The incoming chain is RESET: a cached chain is retired when it leaves service, so it holds
     * no DSP state in practice, but it is coming back in over live audio, where a replayed tail
     * would be audible, so the clean slate is asserted here rather than assumed (the lesson
     * `MasterBus.beginFade` records).
     *
     * **The lease is NOT reset**, unlike [install]'s: the owner voice is alive and sounding, and
     * it is what configures BOTH chains for the length of the fade (see [updateFromVoice]).
     * Handing ownership to whichever voice offers itself next, mid-note, would reconfigure the
     * orbit from a different voice's fields in the middle of a swap.
     *
     * A fade started from [pollPendingChain] rather than from the promotion path begins after this
     * block's voices have already offered themselves, and may find no owner alive at all, so a
     * declared chain is configured from its own slots right here rather than waiting for a lease.
     */
    private fun beginFade(key: String, rawName: String, dsl: KatalystDsl) {
        val next = chainFor(key, dsl)
        val leaving = chain

        next.reset()

        chain = next
        chainDsl = dsl
        chainKey = key
        chainRawName = rawName

        outgoing = leaving
        draining = false
        fade.restart()

        // BEFORE the writers, not after: a carried envelope has to be updated IN PLACE by the
        // arriving chain's own writer (`writeDuck`'s `existing != null` branch), or the swap would
        // leave the orbit ducking off the leaving chain's orbit at the leaving chain's depth.
        handOverDuck(from = leaving, to = next)

        // A DECLARED chain reads its own slots, and nothing else would run its writers until a
        // voice claims the lease: this block's voices have already offered themselves when a fade
        // starts from the pending poll, and an owner may not even be alive. No-op on the classic
        // chain, which has nothing to write without a voice.
        next.applyStatic()
    }

    /**
     * Carries the orbit's duck envelope across a swap: the reduction in force is orbit state, not
     * chain state (`KatalystDuckEffect`).
     *
     * Three directions. Both chains duck: the arriving stage takes the live envelope over and its
     * own writer applies its params to it on this same block, so the reduction simply continues.
     * Only the leaving chain ducks: it stays in service for the length of the fade and its EFFECT
     * is crossfaded out ([processDuck]), since dropping it here would release the whole reduction
     * in one sample. Only the arriving chain ducks: its effect is crossfaded IN over the same ramp,
     * because `Ducking`'s duck-down is instantaneous by design and would otherwise pull the whole
     * orbit down in one sample when the trigger is already sounding.
     */
    private fun handOverDuck(from: KatalystChain, to: KatalystChain) {
        // Whether the ARRIVING chain will duck, not whether it declares a stage: the classic chain
        // declares one on every cylinder, so an orbit swapping from a ducked chain to
        // `Katalyst.classic()` under an owner that carries no ducking would hand its envelope to a
        // stage whose very next write is a `reset()`, releasing the whole reduction in one sample.
        val arrivingDucks = to.ducksWith(ownerDucks)
        val leavingDuck = from.duck

        // A duck with no envelope yet has nothing in force and nothing to carry.
        if (leavingDuck?.ducking == null) {
            duckFadingIn = arrivingDucks

            return
        }

        if (arrivingDucks) {
            to.duck?.takeOver(leavingDuck)

            return
        }

        duckingOut = leavingDuck
    }

    /**
     * The built chain for [name]: the classic instance for the historical declaration, a cache hit,
     * or a fresh build.
     *
     * **Building here is on the audio thread**, the same bounded exception `MasterBus` documents:
     * a swap is applied from the scheduler's promotion path, inside the render callback. The build
     * is small by construction (stage shells only; the delay's ring and the reverb's network are
     * still rented lazily on first activation), and the cache keeps a live-coded edit from paying
     * it twice.
     */
    private fun chainFor(key: String, dsl: KatalystDsl): KatalystChain {
        if (dsl == KatalystDsl.classic) {
            return classicChain
        }

        chains[key]?.let { return it }

        evictIfNeeded()

        return buildChain(dsl, voiceDriven = false).also { chains[key] = it }
    }

    /**
     * Drops cached chains until there is room for one more, keeping the cache bounded. The chain
     * in play is never evicted, and neither is one that is fading or draining ([outgoing]):
     * retiring a chain that is still audible would hand its ring and network to another orbit
     * mid-tail. An evicted chain is retired, which is a no-op for its rents (it had none) and the
     * clean slate for its shells.
     */
    private fun evictIfNeeded() {
        while (chains.size >= MAX_CACHED_CHAINS) {
            // Insertion order (the map's own): the oldest declaration a live-coding session has
            // moved past is the one least likely to come back.
            val victim = chains.entries.firstOrNull { (_, cached) ->
                cached !== chain && cached !== outgoing
            } ?: return

            // Copy the entry out BEFORE the removal: Kotlin/JS refuses to read a map entry once
            // the backing map has changed (the lesson MasterBus.evictIfNeeded records).
            val victimName = victim.key
            val victimChain = victim.value

            chains.remove(victimName)
            victimChain.retire()
        }
    }

    /** The classic chain is the current one again, under no name. */
    private fun selectClassicChain() {
        chain = classicChain
        chainDsl = KatalystDsl.classic
        chainKey = null
        chainRawName = null
        pendingKey = null
    }

    private fun buildChain(dsl: KatalystDsl, voiceDriven: Boolean): KatalystChain = KatalystChainBuilder.build(
        dsl = dsl,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        rings = rings,
        reverbs = reverbs,
        voiceDriven = voiceDriven,
    )
}
