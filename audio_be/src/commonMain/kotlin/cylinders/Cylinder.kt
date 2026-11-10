/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.peekandpoke.klang.audio_be.ChainHost
import io.peekandpoke.klang.audio_be.ChainSwap
import io.peekandpoke.klang.audio_be.Crossfade
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.katalyst.KatalystChain
import io.peekandpoke.klang.audio_be.katalyst.KatalystChainBuilder
import io.peekandpoke.klang.audio_be.katalyst.KatalystContext
import io.peekandpoke.klang.audio_be.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.constants.SILENCE_FLOOR
import io.peekandpoke.klang.common.infra.KlangMap

/**
 * Mixing channel / effect bus: a cylinder (Strudel calls it an orbit; sprudel keeps `orbit()` as an alias of `cylinder()`).
 *
 * Each cylinder runs one [KatalystChain], the per-cylinder effect chain, built from a [KatalystDsl]:
 * **Body → Vowel → Delay → Reverb → Phaser → Compressor → Gain** for [KatalystDsl.classic], which is what
 * every cylinder is born with and what every cylinder that is handed no chain name still runs.
 *
 * A `katalyst(…)` reference on the voice stream reaches [requestChain] (Katalyst step 3a,
 * 2026-09-17), which looks the name up in this cylinder's [KatalystRegistry]. On an IDLE cylinder the
 * declared chain is installed at once; on a SOUNDING one it is faded in over [Crossfade] while the
 * outgoing chain rings out (step 3b, see [processEffects]). EVERY chain reads its knobs from the
 * cylinder's param state, the owner voice's `katalystParams`, the born-with one included (step 5b-1),
 * and a declaration whose content IS the historical chain resolves back to the born-with instance
 * rather than swapping. See `KatalystChainBuilder` and [ChainHost.chainFor].
 *
 * The chain plumbing (the chain in service and its name, the cache, the parked request, the lookup,
 * the [ChainSwap]) is the [ChainHost] this cylinder shares with `MasterBus` (KLANG-00243 (one chain
 * host)); what is the cylinder's stays here: when a request installs at once and when it fades, the
 * duck handover, the owner and the silence reset.
 *
 * The duck runs in a separate pass after all cylinders are processed (cross-cylinder dependency).
 */
// Block-framing ledger D11 (named Class 2 knob): the silence grace before the tail scan is counted
// in BLOCKS, and `Cylinders` visits ONE cylinder per block round-robin, so the wall-clock grace is
// `silentBlocksBeforeTailCheck × allocatedCylinders × blockFrames` — it shrinks with block size and
// grows with cylinder count. No audio is cut (the scan itself protects tails), but everything a
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
    /** The ring shelf this cylinder's delay rents from. Production passes the backend's one warehouse. */
    private val rings: SizedBuffers = SizedBuffers.forRings(sampleRate),
    /** The reverb-unit shelf this cylinder's reverb rents from. Same warehouse. */
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
         * How many blocks the cylinder's param state survives its owner's last commit ([commitOwner]): the
         * block it was written on plus one. Past that the state is dropped and a chain arriving on the
         * cylinder's tail resolves from what it authored.
         */
        private const val OWNER_STATE_BLOCKS: Int = 2
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Bus pipeline effects
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * The chain plumbing this cylinder shares with the output ([ChainHost]): the chain in service, the
     * cache, the parked request and the [swap].
     *
     * Born with the CLASSIC chain, the historical stages, the one chain this cylinder can always fall
     * back to: never evicted and never cached by name, so a retired cylinder [adopt]ed for another
     * cylinder costs no build. Slot-driven like every other chain since step 5b-1 (2026-09-19), so a bus
     * door or a `katp` reaches it through the cylinder's param state and nothing else. A pattern writing
     * `Katalyst.classic()` declares the same stages and resolves to THIS instance
     * ([ChainHost.chainFor]), which makes that declaration a no-op instead of a crossfade.
     *
     * Its registry, where a chain NAME is resolved, is re-pointed by [adopt] and DROPPED by
     * [retire]: a shelved cylinder must not hold the registry of a stopped playback, or the shelf
     * would pin every chain that playback ever registered for as long as it keeps the cylinder (the
     * allocation-cleanup rule, 2026-09-17). A cylinder without a registry resolves no name, which is
     * what an idle shelf slot should do.
     */
    private val host = ChainHost(
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        position = object : ChainHost.Position {
            override fun build(dsl: KatalystDsl): KatalystChain = buildChain(dsl)

            override fun land(key: String, rawName: String, dsl: KatalystDsl): Boolean = landChain(key = key, rawName = rawName, dsl = dsl)
        },
        bornWithDsl = KatalystDsl.classic,
        bornWithAnswersItsContent = true,
        registry = katalysts,
    )

    /**
     * This cylinder's effect chain: the stage instances own the cylinder's DSP state (delay ring, reverb
     * network, compressor envelope, phaser sweep clock), so building is not something a block or a
     * voice may do. Nothing is allocated eagerly that is lazy today: the delay's ring and the
     * reverb's network are still rented on the first activating configure.
     */
    private val chain: KatalystChain get() = host.current

    /** Built chains held by this cylinder, for the specs asserting the cache stays bounded. */
    internal val cachedChainCount: Int get() = host.cachedChainCount

    /**
     * The chain swap of a SOUNDING cylinder (phase 3 step 12 C1): the chain LEAVING service, fading
     * out over [Crossfade] and then draining, and the duck handover of the fade (see [ChainSwap],
     * whose table is authoritative for the edges). The [host] asks [ChainSwap.settled] before it
     * lands a request and parks the key otherwise, which is what keeps [chain] and the leaving chain
     * from ever being the same object.
     *
     * It also carries the rents the warehouse refused the chains this cylinder has swapped AWAY
     * from ([ChainSwap.retire]), because a stage zeroes its own count when it retires.
     */
    private val swap: ChainSwap = host.swap

    /**
     * Test seams: which phase of a swap this cylinder is in. The audio shows the blend, but a spec
     * about the LIFECYCLE (what [tryDeactivate] refuses, when a queued name lands, when the rented
     * units go back) has to be able to name the phase.
     */
    internal val isFading: Boolean get() = swap.isFading

    internal val isDraining: Boolean get() = swap.isDraining

    /** Test seam: the swap itself, for the specs that inspect its states and references. */
    internal val chainSwap: ChainSwap get() = swap

    /**
     * Test seam: the chain in service. A cylinder no longer knows what a body or a reverb IS, so it names no stage;
     * the specs that ask about one find it on this chain through their own helpers (`_katalyst_test_helpers.kt`,
     * engine tidy-up step 13).
     */
    internal val currentChain: KatalystChain get() = chain

    /**
     * True while this cylinder rings with a tail that can never end on its own, in the chain in
     * service or the one leaving it while it fades or drains ([KatalystChain.sustainsItself];
     * phase 3 step 12 decision (j)). NOT the leaving chain once its swap RELEASES or CUTS it: that one
     * ends within the release or the cut by construction ([ChainSwap.leavingIsEnding]), and counting it would make a stopped engine release
     * its whole output, finite tails on other cylinders included, for a tail already on its way out.
     */
    fun sustainsItself(): Boolean =
        isActive && (chain.sustainsItself() || (!swap.leavingIsEnding && swap.leaving?.sustainsItself() == true))

    /**
     * The duck that governs this cylinder's mix, and the one `Cylinders` resolves the sidechain cylinder
     * from before it runs the duck pass.
     *
     * The duck being faded OUT comes first, for the length of the fade: it is what is audible, and
     * it must keep listening to the cylinder it was pointed at. The arriving chain's own stage comes
     * second, and it may not even be configured yet (the classic chain declares a duck on every
     * cylinder, so `chain.duck` non-null says nothing about whether it ducks) - a
     * `duckCylinderId` of null is exactly how `Cylinders` skips the pass for such a stage.
     */
    val duck get() = swap.duckingOut ?: chain.duck

    /**
     * Rents the warehouse refused this cylinder, for the diagnostics feedback: the current chain's
     * count plus what the chain leaving now and the chains before it were refused
     * ([ChainSwap.deniedRents]).
     *
     * Per cylinder LIFE, like every other counter on this path: [retire] and [adopt] zero it, so
     * a shelved cylinder never carries a previous engine's number into the next one.
     */
    val deniedRents get() = swap.deniedRents + chain.deniedRents

    // ════════════════════════════════════════════════════════════════════════════
    // Buffers and context
    // ════════════════════════════════════════════════════════════════════════════

    /** The cylinder mix: voices sum into this, and the chain processes it in place. */
    val mixBuffer = StereoBuffer(blockFrames)

    /**
     * Shared context for all bus effects. The chain leaving service runs in the [swap]'s own
     * context, so the live mix stays untouched for the chain that is arriving.
     */
    val katalystContext = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = mixBuffer,
    )

    // ════════════════════════════════════════════════════════════════════════════
    // State
    // ════════════════════════════════════════════════════════════════════════════

    /** The cylinder number this instance serves. Re-labelled by [adopt] when a shelved instance is rented for another. */
    var id: Int = id
        private set

    private var silentBlocksBeforeTailCheck: Int = silentBlocksBeforeTailCheck

    /** Whether the cylinder runs (its mix is cleared, its chain processes): every state of [activity] but [Activity.Inactive]. */
    val isActive: Boolean get() = activity !== Activity.Inactive

    /**
     * The cylinder's own activity as a state machine (KLANG-00290 (engine follow ups) item 27, C4.1, maintainer
     * 2026-11-06), the four situations that decided it as a flag and a counter before. A sealed type with an
     * exhaustive `when` ([tryDeactivate]): the states without data are `data object`s, [Activity.SilentGrace]
     * (the count) a class with one instance, [silentGrace], created with the cylinder, so no transition allocates
     * (`audio/ref/effect-state-machines.md` §1).
     *
     * | state \ event | [checkIn] | visit, mix sounds | visit, silent, inside the grace | visit, silent, grace over: tail or swap | ...: a voice checked in | ...: neither | [startNewLife] |
     * |---|---|---|---|---|---|---|---|
     * | **Inactive** | **Sounding** | (no visit) | | | | | Inactive |
     * | **Sounding** | Sounding | Sounding | **SilentGrace** | Sounding | **Held** | **Inactive** | **Inactive** |
     * | **SilentGrace** | SilentGrace | **Sounding** | SilentGrace (counts on) | **Sounding** | **Held** | **Inactive** | **Inactive** |
     * | **Held** | Held | **Sounding** | (never: the grace is over) | **Sounding** | Held | **Inactive** | **Inactive** |
     *
     * A visit is [tryDeactivate], once per block. "Grace over" means the silent visits, this one included, reach
     * [silentBlocksBeforeTailCheck]; a grace of 1 or less is over at the first silent visit. A tail or a running
     * swap restarts the grace (Sounding); a voice still playing holds it at its end (Held), so the cylinder goes at the
     * first silent visit after the voice's last check-in grace. Wrap-safe by construction (audit leftovers §3): the
     * count lives only in SilentGrace and never passes the grace, so a cylinder that stays silent forever, a muted
     * one with notes included, never counts past it.
     */
    private sealed class Activity {
        /** Not in use: no mix to clear, no chain to run, until a voice checks in. */
        data object Inactive : Activity()

        /** In use, and the mix sounded at the last visit (or a tail or a swap restarted the grace). */
        data object Sounding : Activity()

        /** In use and silent, inside the grace: the stages keep processing, so their tails decay on their own. */
        class SilentGrace : Activity() {
            /** Silent visits in a row, 1 up to the grace (exclusive). */
            var count: Int = 0
                private set

            /** Entered on a silent visit: [visits] silent visits so far. Returns this state, for `activity = ...`. */
            fun enter(visits: Int): SilentGrace {
                count = visits

                return this
            }
        }

        /** In use and silent past the grace, nothing rings, but a voice still checks in: every visit asks again. */
        data object Held : Activity()
    }

    private var activity: Activity = Activity.Inactive

    /** The one [Activity.SilentGrace] instance (see [Activity]). */
    private val silentGrace = Activity.SilentGrace()

    // ONE owner per cylinder, chosen once per block: the cylinder's bus settings are owned by the newest `Sounding`
    // voice; a voice gives the cylinder up when its gate closes or it is cut ([offer], [commitOwner]). Other voices
    // on the cylinder are ignored; route to a different cylinder for different bus settings. One owner per block
    // also rules out the per-block flip-flop (and the body-filter rebuild thrash it caused on mixed-material
    // cylinders). The newest offer of the current block, cleared by [commitOwner].
    private var candidate: Voice? = null

    /**
     * The cylinder's param state, as its owner last handed it over: the map
     * `.katp` and the bus doors wrote (`Voice.katalystParams`), by reference.
     *
     * Kept for the ONE moment a swap needs it. [beginFade] runs before this block's voices have
     * offered themselves (the promotion path) or after they have (the pending poll), and in both
     * cases it has to resolve the ARRIVING chain from the live state before [handOverDuck] asks
     * whether that chain ducks. Without it a `.katp("duck.cylinder", n)` reads as "no duck" at the
     * handover, the reduction is ramped out, and the arriving chain's own writer then drops a fresh
     * one on the cylinder a block later (review round 1).
     *
     * **Aged** (review round 2): a block without an owner ([commitOwner] found no offer) ages the state, and a
     * chain arriving while the cylinder merely rings out its tail resolves from the authored defaults, not from a
     * voice that gave the cylinder up. The reference is dropped after [OWNER_STATE_BLOCKS], so nothing here
     * outlives its owner by more than that (the allocation-cleanup rule).
     */
    private var ownerParams: KlangMap<Double>? = null

    /**
     * Blocks since the cylinder's owner last handed [ownerParams] over, capped at
     * [OWNER_STATE_BLOCKS], where the state is dropped. Counted in blocks: an owner is chosen once per block.
     */
    private var ownerParamsAge: Int = OWNER_STATE_BLOCKS

    /**
     * Whether any voice has checked in since the cylinder's last reset, and the block it last did ([checkIn]): the
     * cylinder is in use while a voice renders on it, owner or not (tails, fading and culled voices included).
     * [tryDeactivate] reads it. Separate from ownership, which only `Sounding` voices take ([offer]).
     */
    private var checkedIn: Boolean = false

    // Absolute backend frame, Double, see RenderClock.cursorFrame.
    private var lastCheckInFrame: Double = 0.0

    // ════════════════════════════════════════════════════════════════════════════
    // API
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * [voice], `Sounding` this block, offers itself as the owner of the cylinder's bus settings: the cylinder's bus
     * settings are owned by the newest `Sounding` voice; a voice gives the cylinder up when its gate closes or it is
     * cut (lifecycle step 5, maintainer 2026-10-07). The offer only records the newest offer of the block ([isNewer]);
     * [commitOwner] applies it once, after every voice has rendered, so the outcome does not depend on the order
     * the voices render in, and a block applies one owner's settings, not each offerer's in turn. Allocation-free.
     */
    // blockStart is an ABSOLUTE backend frame, Double, see RenderClock.cursorFrame.
    fun offer(voice: Voice, blockStart: Double) {
        checkIn(blockStart)

        val current = candidate

        if (current == null || isNewer(aStart = voice.startFrame, aId = voice.id, bStart = current.startFrame, bId = current.id)) {
            candidate = voice
        }
    }

    /**
     * Applies this block's newest offer ([offer]) as the cylinder's owner: production calls it once per block from
     * `Cylinders.processAndMix`, after the voices rendered and before the cylinder's own processing. No offer this
     * block: the cylinder has no owner and keeps the settings it last applied. Re-applying the same owner is cheap
     * (the chain re-resolves only when the owner's map instance changes).
     *
     * Block-framing ledger D14 (named Class 2 knob): bus params apply at BLOCK granularity, never at the onset
     * sample: a new owner's settings also govern the `ctx.offset` samples before its own first sample (the
     * previous owner's still-decaying tail), and the transition frame moves with alignment. Inherent to a
     * block-continuous bus driven by per-voice triggers.
     */
    fun commitOwner() {
        val voice = candidate ?: return

        candidate = null
        ownerParams = voice.katalystParams
        ownerParamsAge = 0

        // BEFORE the late-duck question below, and before any writer runs: `ducksWith` asks
        // the chain what its duck stage RESOLVES to, and until this owner's state has been read
        // that answer is the chain's authored default. Resolving does not write, so the
        // handover order the carried envelope depends on is untouched, and the `applyParams`
        // further down re-uses this resolve (same map instance, gated).
        chain.resolveParams(voice.katalystParams)

        // A ducking owner whose FIRST claim lands in the swap's own block. The swap was decided
        // at promotion, before this voice offered itself, so [handOverDuck] saw no owner and
        // chose the ramp-out path. Corrected here ([ChainSwap.ownerClaimed]), before the writers
        // run: otherwise the cylinder ramps the old reduction out while this voice's fresh envelope
        // sits unprocessed behind it, and drops by the whole depth the moment the ramp ends.
        //
        // ONLY in that block ([Crossfade.isAtStart]). Once the ramp has moved, the cylinder's gain
        // is already `g * (1 - t) + t` and handing the envelope over would jump it back to `g`
        // (0.053 on a 0.5 probe three blocks in, 0.2 mid-fade). A claim after that keeps
        // ramping out, and the arriving chain's own fresh envelope makes its duck-down on the
        // first block past the ramp: the ducker's documented behaviour for a new owner, not a
        // step the swap put there.
        //
        // NOT gated on the voice's own duck settings (dropped in review round 3; the voice
        // has none since step 5b-3): a duck named through `.katp("duck.cylinder", n)` alone left
        // the voice's then `duckCylinder` field null, so the correction skipped it
        // and the arriving chain's fresh envelope then pulled the cylinder down by the full depth
        // one block past the ramp. What the arriving chain will do is `ducksWith`'s answer, and
        // the resolve above is what makes it current.
        swap.ownerClaimed(chain)

        // EVERY chain resolves EVERY knob from the owner's param state, the born-with one
        // included (step 5b-1): one way for a bus knob to reach a stage (the voice's bus
        // fields left the wire in step 5b-3). The rule's one home is the `katalystParam` door's KDoc in
        // `sprudel/lang/lang_katalyst.kt`.
        //
        // The state is READ FROM THE OWNER and never copied into this cylinder: it is the owner's
        // map, by reference. A cylinder without an owner keeps the stages as they were last written
        // (only a chain arriving meanwhile resolves its authored defaults). The chain re-resolves only when the map
        // INSTANCE changes (`KatalystChain.applyParams`), so a live owner costs one reference
        // compare per block and no lookup.
        chain.applyParams(voice.katalystParams)

        // The chain FADING OUT is still audible, so it is still configured (step 3b): the
        // owner keeps steering it until the fade ends. Not while it DRAINS: the ring-out runs
        // on the settings the chain had when it left service, and a new owner's settings (a
        // different time or room, or an off-config that would fade out the room's feed of echoes)
        // are the arriving chain's business.
        swap.configureLeaving(voice.katalystParams)
    }

    /**
     * A voice renders on this cylinder this block without offering itself as the owner (a voice past its gate, or
     * a fading one): it activates the cylinder and keeps it in use ([tryDeactivate]). It does not offer, so it gives
     * the cylinder up simply by not offering. Allocation-free.
     */
    // blockStart is an ABSOLUTE backend frame, Double, see RenderClock.cursorFrame.
    fun checkIn(blockStart: Double) {
        if (activity === Activity.Inactive) {
            activity = Activity.Sounding
        }

        checkedIn = true
        lastCheckInFrame = blockStart
    }

    /**
     * The order of the ownership rule ([offer]): the later onset wins; on the same onset the voice created later
     * (the higher [Voice.id]; ids grow monotonically and wrap only after 2^31 voices).
     */
    private fun isNewer(aStart: Double, aId: Int, bStart: Double, bId: Int): Boolean =
        aStart > bStart || (aStart == bStart && aId > bId)

    /** True while a voice checked in during this block or the one before (a one-block grace). */
    private fun voiceCheckedIn(blockStart: Double): Boolean = checkedIn && (blockStart - lastCheckInFrame) <= blockFrames

    /**
     * Requests the chain registered as [name]: the scheduler calls this when it consumes a
     * `katalyst(…)` reference, whether or not the event also sounds. The paths (already in service,
     * not registered yet, the swap busy) are the [ChainHost]'s; a request that can land lands here
     * ([landChain]):
     *
     *  - **The cylinder is idle**: installed now, which is the one moment no crossfade is needed.
     *    A name whose CONTENT equals the historical chain resolves to the chain the cylinder was
     *    born with, so the install is the name and nothing else ([ChainHost.chainFor]).
     *  - **The cylinder is sounding**: faded in now (step 3b), over [Crossfade], while the chain
     *    it replaces fades out and then rings out (see [processEffects]).
     *
     * A request for the chain fading OUT is parked like any other rather than reversing the ramp,
     * because reversing needs the outgoing chain's name, key and declaration tracked alongside the
     * incoming one's for a case that is one live-coding undo inside 60 ms (decided 2026-09-17, the
     * stone rule on complexity).
     */
    fun requestChain(name: String) {
        host.request(name)
    }

    /**
     * Lands a chain that is parked on this cylinder, polled once per block by
     * [Cylinders.processAndMix] ([ChainHost.pollPending]).
     *
     * This is what makes a LATE registration land: the request arrived before its
     * `RegisterKatalyst` command and no further request is coming (a one-shot `.katalyst(…)` on a
     * single note emits once), so without a poll the chain would wait for the cylinder's next note.
     * It is also what starts a PARKED swap once the fade it waited behind has finished, on a
     * sounding cylinder that never goes silent. One field read per cylinder per block on the normal
     * path; on a name that never resolves, one map probe per registry in the fork chain, and still
     * no allocation.
     */
    fun pollPendingChain() {
        host.pollPending()
    }

    fun clear() {
        if (!isActive) return

        mixBuffer.clear()
        katalystContext.clearBuses()
    }

    /**
     * The mix at [steps] 2x steps above the base rate, for a voice that hands its output over at its region's rate
     * (KLANG-00214 (oversampling regions) step 3b): [mixBuffer] at 0, else the rate bus, made the first time a voice
     * lands there. The chain brings every bus to its own rate, once per cylinder ([KatalystContext.buses]).
     */
    fun mixBufferAt(steps: Int): StereoBuffer = if (steps == 0) mixBuffer else katalystContext.busAt(steps)

    /**
     * Processes all bus effects in the chain's order: Body → Vowel → Delay → Reverb → Phaser →
     * Compressor → Gain for the classic chain.
     *
     * The duck is NOT processed here, see [Cylinders.processAndMix].
     *
     * **While a chain swap is in flight, two chains run** (step 3b, the master's dual-chain
     * crossfade on the cylinder bus):
     *
     *  - **Fading.** Both chains see the same mix, the outgoing one through its own ramped copy
     *    (the [swap]'s leaving mix) and the incoming one through the live buffer. The ramp is applied to the
     *    outgoing chain's INPUT (its delay and reverb take their feed from it too) and to the
     *    incoming chain's OUTPUT, per sample
     *    ([Crossfade.rampDown], [Crossfade.rampUpAndAdd]), which is what makes the handover to the
     *    drain continuous. See [Crossfade] for why an output blend would step here. The
     *    incoming chain is warmed up on real audio for the whole fade: its compressor envelope and
     *    its room have settled by the time it carries full weight.
     *  - **Draining.** One block after the ramp runs out (see above) the outgoing chain leaves
     *    service, and it is NOT retired. It keeps processing, its stages still Active, on SILENT
     *    input with its output added at FULL weight, so the echoes and the room it had already
     *    scheduled ring out on their own timeline instead of being cut mid-tail, and the room keeps
     *    hearing the delay's ring-out (a stage switched off would read its own silent input and fade
     *    that feed out within 50 ms; before engine follow-up 42 it cut it in one sample, measured in
     *    step 5b-2). The stages' tail ceilings bound it
     *    (`KatalystDelayEffect`, `KatalystReverbEffect`), and the chain retires (units back to the
     *    shelves) on the first block [KatalystChain.hasTail] is false and the chain's own output is under
     *    `SILENCE_FLOOR` (the output gate). A drain still reporting a
     *    tail after [ChainSwap.MAX_DRAIN_SECONDS] (a self-oscillating delay never stops) has its
     *    output released exponentially and retires about 4.5 s later (step 12 decision (i)). While
     *    it drains it measures its tails now and then, so a room's ceiling follows what the room
     *    really holds ([ChainSwap.TAIL_MEASURE_SECONDS]). No owner configures it any more (see
     *    [commitOwner]).
     *  - **Cutting.** A request queued behind a drain or a release does not wait for it
     *    (maintainer, 2026-10-17): the leaving chain's output is faded out over
     *    [ChainSwap.CUT_SECONDS] and the request lands on the next block's poll, about a fifth of a
     *    second after it was made. A request during the fade cuts where the fade ends.
     *
     *    The cylinder drained from the start (decided 2026-09-17: its delay and reverb already own the
     *    drain; nothing here invents a decay); the master bus cut its leaving chain until step 12
     *    C4 put it on the same [ChainSwap].
     */
    fun processEffects() {
        if (!isActive) return

        // The owner's committed state ages one block here, the one place that runs once per block per
        // cylinder and after the voices have offered themselves (see [ownerParams]).
        if (ownerParamsAge < OWNER_STATE_BLOCKS) {
            ownerParamsAge++

            if (ownerParamsAge == OWNER_STATE_BLOCKS) {
                // The owner has lapsed: its state goes with it, and a chain arriving from here on
                // resolves from the authored defaults.
                ownerParams = null
            }
        }

        // Idle, Fading or Draining: the [swap]'s table. The fade-to-drain edge fires at the start
        // of the block after the ramp's last one, inside this call (see [ChainSwap]).
        swap.process(chain, katalystContext)
    }

    /**
     * Applies ducking using the resolved sidechain buffer.
     *
     * Called by [Cylinders] after all cylinders have processed their main pipeline,
     * since ducking needs cross-cylinder access.
     */
    fun processDuck(sidechainMixBuffer: StereoBuffer?) {
        if (!isActive) return

        katalystContext.sidechainBuffer = sidechainMixBuffer

        // While a fade carries a duck, its effect is ramped in or out with the weights this
        // block's chains were blended with; otherwise the chain in service ducks as it always does
        // (see [ChainSwap.processDuck]).
        swap.processDuck(chain, katalystContext)

        katalystContext.sidechainBuffer = null
    }

    /**
     * Retires this cylinder for the shelf (resource warehouse, cylinders): every bus effect off and
     * cleared, the owner forgotten, the buffers zeroed, and the rented units (delay ring, reverb
     * network) handed back to THEIR shelves — a shelved cylinder holds nothing. The same clean slate
     * [tryDeactivate] reaches, plus the return. Only for a cylinder that will never render again
     * on its current cylinder: `CylinderUnits.giveBack` is the one caller.
     */
    fun retire() {
        startNewLife()
        // The registry goes with the engine that lent it (see [host]).
        host.registry = null
    }

    /**
     * Re-labels this instance for cylinder number [id] under the renting `Cylinders`' settings, with
     * [katalysts] as its new registry: the previous engine's chains die with it, and this cylinder
     * starts over from the classic chain.
     */
    fun adopt(id: Int, silentBlocksBeforeTailCheck: Int, katalysts: KatalystRegistry) {
        this.id = id
        this.silentBlocksBeforeTailCheck = silentBlocksBeforeTailCheck
        host.registry = katalysts
        // The SAME clean slate [retire] reaches, not a subset: a cylinder normally arrives here
        // through the shelf, but a caller that re-labels one directly must not be the path where a
        // ring stays inside a chain nobody can reach any more.
        startNewLife()
    }

    /**
     * The clean slate a cylinder starts a life on: every chain it built goes back to the shelf it
     * rented from, the cache is dropped, the cylinder runs the classic chain again with no owner, the
     * buffers are silent and the cylinder is inactive. Shared by [retire] (which additionally drops
     * the registry) and [adopt], so the two can never reach different states.
     *
     * The chains' retire, NOT their reset: the rented units go back DIRTY and the warehouse's
     * housekeeping zeroes them a block at a time (see `KatalystChain.retire`). EVERY chain, not
     * just the current one: a cached chain holds no rent, but retiring it is what makes that true,
     * and it must stay true for the next engine that rents this cylinder. Retire is idempotent, so
     * the current chain being one of them costs nothing.
     */
    private fun startNewLife() {
        // Any fade or drain ends here, at whatever weight it had reached: this cylinder is not
        // going to render again for the cylinder it was fading FOR. The leaving chain is normally the
        // classic one or a cache entry (eviction never takes it), so the chains' retires already
        // cover it; the swap's hard cut retires it anyway, because retire is idempotent and a
        // stranded ring is the one mistake this method exists to make impossible.
        host.retireAll()
        candidate = null
        checkedIn = false
        ownerParams = null
        ownerParamsAge = OWNER_STATE_BLOCKS
        mixBuffer.clear()
        katalystContext.clearBuses()
        activity = Activity.Inactive
    }

    /**
     * Deactivates the cylinder once its mix has been silent for the grace, nothing rings in its chain,
     * no swap runs, AND no voice plays on it. [blockStart] is the start frame of the block just
     * rendered (production: `Cylinders.processAndMix`, fed the engine's cursor), which the check-in
     * test needs.
     *
     * Two phases, so a tail is never cut: while the mix is silent a counter runs instead of
     * deactivating at once, and the stages keep processing so their tails decay naturally; after
     * the grace the chain is asked for a tail, and one resets the counter.
     *
     * **A cylinder never deactivates while a voice plays on it** (decided 2026-09-19 with the
     * maintainer, Katalyst 5c-8). The silence test reads the POST-fader mix, so a group fader at 0
     * used to make a playing cylinder look dead: it was reset every tenth block (measured with one
     * cylinder allocated; the grace is counted in cleanup visits), the owner was
     * re-dealt, and the fader came back either as a one-sample jump (a fresh stage snaps) or as a
     * one-block ramp mid-note, depending on which voice claimed first. The cylinder counts as in use
     * while any voice on it checked in this block or the one before ([checkIn], owner or not,
     * separate from ownership), so a muted cylinder with notes keeps running at 0 and its fader glides
     * back from where it stands. Judging silence BEFORE the fader was rejected: in a user chain the
     * gain stage can sit anywhere.
     *
     * The check-in test does NOT restart the silence grace, unlike a tail: silence already counted
     * stays counted, and a cylinder whose notes have all ended goes at the first visit two blocks
     * after the last check-in (the one-block grace covers the block after it). Every voice checks in while it
     * renders, its release included, audible or not (a culled voice has ended and checks in no more). That is NOT a new
     * processing cost: before 5c-8 every check-in reactivated the cylinder (the owner claim set
     * `isActive`), so the same cylinder was reactivated the block after each deactivation and its
     * chain ran every block anyway, to the same final deactivation block. What the refusal
     * removes is the repeated reset of the chain and the re-dealing of the owner in between.
     */
    fun tryDeactivate(blockStart: Double) {
        // The silent visits so far, this one included, from the state (see [Activity]'s table)
        val silentVisits = when (val a = activity) {
            Activity.Inactive -> return
            Activity.Sounding -> 1
            is Activity.SilentGrace -> a.count + 1
            Activity.Held -> silentBlocksBeforeTailCheck + 1
        }

        if (!isMixBufferSilent()) {
            activity = Activity.Sounding
            return
        }

        if (silentVisits < silentBlocksBeforeTailCheck) {
            activity = silentGrace.enter(visits = silentVisits)
            return
        }

        // State-aware like the delay's: a draining reverb reports its tail BY CONSTRUCTION, so
        // the cylinder stays alive until the countdown's terminal reset: the old param-gated scan
        // hid a still-charged network the moment a no-reverb owner zeroed size.
        //
        // The outgoing chain counts too, and the swap's state is the test (step 3b): while a FADE
        // runs the cylinder must keep rendering until the ramp completes, whatever either chain
        // holds, and while the outgoing chain DRAINS it has a tail by construction: the swap
        // returns to Idle on the first block it does not, or when the release at the drain's cap ends.
        if (chain.hasTail() || !swap.settled) {
            activity = Activity.Sounding
            return
        }

        // A voice still plays here (see the KDoc). The grace is held at its end rather than
        // restarted, so the cylinder goes at the first visit after the last check-in's grace.
        if (voiceCheckedIn(blockStart)) {
            activity = Activity.Held
            return
        }

        activity = Activity.Inactive
        // The mix buffer is not cleared while the cylinder is inactive (see [clear]), so the last
        // block's sub-floor output would otherwise still be in it when a voice reactivates the
        // cylinder: summed into that block's mix and, since step 5b-2, fed into its delay and room.
        // The rate buses likewise: the chain's steps down may have rung out on one this block.
        mixBuffer.clear()
        katalystContext.clearBuses()

        // A chain that was requested while this cylinder was sounding lands HERE, the first moment
        // the swap is inaudible. An install that SWAPPED retires the outgoing chain (units back to
        // the shelves) and forgets the owner, which is the clean slate the reset below would reach,
        // so only one of the two runs: resetting first would zero a ring we are about to hand back
        // dirty on purpose (see KatalystChain.retire). A pending key that resolved to the chain
        // already in service installs nothing, and then the reset below is still owed: it is what
        // keeps the next life's first owner from inheriting this life's DSP state.
        if (host.pollPending()) {
            return
        }

        // Forget the owner and reset all bus effects so a reused/reactivated cylinder starts clean and
        // is reconfigured by whichever voice next owns it.
        chain.reset()
        candidate = null
        checkedIn = false
        ownerParams = null
        ownerParamsAge = OWNER_STATE_BLOCKS
    }

    private fun isMixBufferSilent(): Boolean {
        // Shared with the voice cull floor (VOICE_CULL_FLOOR), so a culled voice is by definition
        // below what keeps a cylinder alive.
        val threshold = SILENCE_FLOOR
        for (sample in mixBuffer.left) {
            if (sample > threshold || sample < -threshold) return false
        }
        for (sample in mixBuffer.right) {
            if (sample > threshold || sample < -threshold) return false
        }
        return true
    }

    /**
     * Lands a request the [host] resolved, while the swap is settled ([ChainHost.Position.land]): at
     * once on an idle cylinder ([install]), through a crossfade on a sounding one ([beginFade]).
     *
     * **What the return value means, per branch**, because the two are not quite the same question:
     * the IDLE branch reports whether the chain actually CHANGED, which is what [tryDeactivate]
     * needs (a content-classic key lands on the chain already in service and installs nothing, and
     * the reset is then still owed); the SOUNDING branch reports that the key was CONSUMED, and
     * says true even for a content-classic key on which [beginFade] no-ops. That asymmetry is
     * harmless and deliberate: the only caller that reads the value is [tryDeactivate], which runs
     * on a cylinder that has just gone silent, so the sounding branch is unreachable from it. Giving
     * [beginFade] a Boolean of its own would buy nothing today and one more thing to keep true.
     */
    private fun landChain(key: String, rawName: String, dsl: KatalystDsl): Boolean {
        if (isActive) {
            beginFade(key = key, rawName = rawName, dsl = dsl)

            return true
        }

        return install(key = key, rawName = rawName, dsl = dsl)
    }

    /**
     * Swaps [chain] for the chain [name] declares, at once. Called only while this cylinder is
     * IDLE, so there is nothing audible to crossfade; a sounding cylinder goes through [beginFade].
     *
     * **The outgoing chain is retired EAGERLY** (decided with the maintainer, 2026-09-17): its
     * rented ring and network go back to their shelves the moment it leaves service, and only its
     * stage shells stay in the cache. The price is that a swap BACK re-rents, which can cost a
     * synchronous unit reset (a shelved ring is zeroed on return, ~27k stores for the smallest
     * class) and can be DENIED under shelf pressure, leaving that chain's echo or room absent
     * until the next rent. That is the trade the warehouse exists for: keeping up to
     * [ChainHost.MAX_CACHED_CHAINS] chains' units alive per cylinder would hold megabytes per cylinder
     * for chains nobody is playing, and a swap is an edit-time event, not a per-block one. [beginFade] keeps
     * two chains alive for the length of the fade and the outgoing chain's ring-out, which is the
     * one case that needs both.
     *
     * The incoming chain needs no reset: nothing that is not the current chain holds state.
     *
     * The incoming chain is never the outgoing one, so retiring after the assignment cannot wipe
     * what was just installed: the [host] lands nothing when the requested KEY is the one already
     * playing, and [chain] is either the born-with classic chain (never cached by name) or the
     * cache entry for its key.
     */
    private fun install(key: String, rawName: String, dsl: KatalystDsl): Boolean {
        val next = host.chainFor(key, dsl)
        val leaving = chain

        host.enter(chain = next, key = key, rawName = rawName)

        // The requested chain is the one already in service, under a name it did not carry before:
        // a content-classic declaration on a cylinder still running the chain it was born with (see
        // [ChainHost.chainFor]). Adopting the name is the whole of the install; retiring `leaving`
        // below would hand back the ring and the network of the chain we just "installed", and
        // forgetting the owner would re-deal the cylinder for nothing. FALSE, not true: the caller has
        // to know that nothing was installed, or [tryDeactivate] would skip the reset that is its
        // own job.
        if (next === leaving) {
            return false
        }

        // The denied rents are carried BEFORE the retire, which zeroes the stage's own count: the
        // cylinder's diagnostics number is about this cylinder's life, not about its current chain.
        swap.retire(leaving)
        // The next commit writes the new chain's stages from whichever voice owns the cylinder then.
        candidate = null
        checkedIn = false
        ownerParams = null
        ownerParamsAge = OWNER_STATE_BLOCKS

        return true
    }

    /**
     * Starts the crossfade from the chain in service to the one [name] declares: the swap of a
     * SOUNDING cylinder (step 3b). Both chains run from the next processed block (the start is
     * block-quantized, see [Crossfade]) until the ramp completes, and the outgoing chain then
     * rings out (see [processEffects]).
     *
     * The incoming chain is RESET: a cached chain is retired when it leaves service, so it holds
     * no DSP state in practice, but it is coming back in over live audio, where a replayed tail
     * would be audible, so the clean slate is asserted here rather than assumed (`MasterBus.landChain`
     * does the same).
     *
     * **The owner is NOT forgotten**, unlike in [install]: the owner voice is alive and sounding, and
     * it is what configures BOTH chains for the length of the fade (see [commitOwner]).
     *
     * A fade started from [pollPendingChain] rather than from the promotion path begins after this
     * block's voices have already offered themselves, and may find no owner alive at all, so a
     * declared chain is configured from its own slots right here rather than waiting for an owner.
     */
    private fun beginFade(key: String, rawName: String, dsl: KatalystDsl) {
        val next = host.chainFor(key, dsl)
        val leaving = chain

        host.enter(chain = next, key = key, rawName = rawName)

        // Already in service (see [install]): there is nothing to fade, and a fade would RESET the
        // running chain, cutting the cylinder's own tail on a declaration that changes nothing.
        if (next === leaving) {
            return
        }

        next.reset()
        // It arrives over sound: a delay or a reverb it switches on fades its feed in rather than snapping it, or its
        // unit would be fed a step of the live mix that comes back after the ramp (KLANG-00428, L7a-02).
        next.joinSoundingBus()

        // The arriving chain reads the cylinder's live param state FIRST, without writing anything:
        // `handOverDuck` below asks whether it will duck, and a duck whose cylinder or depth comes
        // from `.katp` answers that question only once the state has been read (review round 1).
        next.resolveParams(ownerParams)

        // BEFORE the writers, not after: a carried envelope has to be updated IN PLACE by the
        // arriving chain's own writer (`KatalystDuckEffect.configure`'s reuse branch), or the swap would
        // leave the cylinder ducking off the leaving chain's cylinder at the leaving chain's depth.
        //
        // THIS CALL STARTS THE SWAP: `handOverDuck` ends in [ChainSwap.begin] (one call per duck
        // direction), which puts `leaving` on its way out and restarts the ramp. Both callers of
        // this function asked [ChainSwap.settled] first, so the swap does not refuse it.
        handOverDuck(from = leaving, to = next)

        // The arriving chain reads the cylinder's slot state here, because nothing else would run its
        // writers until the next commit: this block's owner has already been committed when a fade
        // starts from the pending poll, and an owner may not even be alive.
        // Gated on the same map instance the resolve above took, so it only writes. With no state
        // at all every knob lands on what the chain authored, which is the clean slate a chain
        // entering service wants.
        next.applyParams(ownerParams)
    }

    /**
     * Carries the cylinder's duck envelope across a swap: the reduction in force is cylinder state, not
     * chain state (`KatalystDuckEffect`).
     *
     * Three directions. Both chains duck: the arriving stage takes the live envelope over and its
     * own writer applies its params to it on this same block, so the reduction simply continues.
     * Only the leaving chain ducks: it stays in service for the length of the fade and its EFFECT
     * is crossfaded out ([processDuck]), since dropping it here would release the whole reduction
     * in one sample. Only the arriving chain ducks: its effect is crossfaded IN over the same ramp,
     * because `Ducking`'s duck-down is instantaneous by design and would otherwise pull the whole
     * cylinder down in one sample when the trigger is already sounding.
     *
     * The decision is the host's (the master has no duck pass); the data it decides is the
     * [swap]'s Fading state's, handed over in [ChainSwap.begin], which is where the fade starts.
     */
    private fun handOverDuck(from: KatalystChain, to: KatalystChain) {
        // Whether the ARRIVING chain will duck, not whether it declares a stage: a chain built from
        // `Katalyst.classic()` declares a duck that names no source, and handing it the envelope
        // would give it to a stage whose very next write is a `reset()`, releasing the whole
        // reduction in one sample.
        val arrivingDucks = to.ducksWith()
        val leavingDuck = from.duck

        // A duck with no envelope yet has nothing in force and nothing to carry.
        if (leavingDuck?.ducking == null) {
            swap.begin(leaving = from, arrivingLatencyFrames = to.latencyFrames, duckingOut = null, duckFadingIn = arrivingDucks)

            return
        }

        if (arrivingDucks) {
            to.duck?.takeOver(leavingDuck)
            swap.begin(leaving = from, arrivingLatencyFrames = to.latencyFrames, duckingOut = null, duckFadingIn = false)

            return
        }

        swap.begin(leaving = from, arrivingLatencyFrames = to.latencyFrames, duckingOut = leavingDuck, duckFadingIn = false)
    }

    /**
     * Builds a chain for this cylinder. **On the audio thread**, the same bounded exception `MasterBus`
     * documents: a swap is applied from the scheduler's promotion path, inside the render callback.
     * The build is small by construction (stage shells only; the delay's ring and the reverb's
     * network are still rented lazily on first activation), and the [host]'s cache keeps a
     * live-coded edit from paying it twice.
     */
    private fun buildChain(dsl: KatalystDsl): KatalystChain = KatalystChainBuilder.build(
        dsl = dsl,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        rings = rings,
        reverbs = reverbs,
    )
}
