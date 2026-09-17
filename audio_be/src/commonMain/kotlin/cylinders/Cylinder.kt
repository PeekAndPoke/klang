/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystChain
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystChainBuilder
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystContext
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
 * 2026-09-17), which looks the name up in this cylinder's [KatalystRegistry] and installs the
 * declared chain the next time this cylinder is idle. The classic chain reads its knobs from the
 * orbit's OWNER voice, a declared chain from its own slots; see `KatalystChainBuilder`.
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
     * request time): the cylinder was still sounding, or the `RegisterKatalyst` command had not
     * arrived. Retried per block while the orbit is idle ([pollPendingChain]) and on the next
     * request, never resolved to the classic chain instead (the master's rule: an unknown name
     * must not silently pin an orbit to the wrong chain).
     *
     * A key, not a name, because the retry runs on the render path: [KatalystRegistry.findByKey]
     * allocates nothing on a miss, so a name that never resolves costs one map probe per block
     * instead of two strings (the §7 rule, and the review finding that closed it).
     *
     * ⚠️ Until step 3b brings the crossfade, a chain edit on an orbit that NEVER goes silent
     * waits here: there is no click-free way to swap a running chain yet, and cutting one is worse
     * than waiting a bar.
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

    val duck get() = chain.duck

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
    val deniedRents get() = retiredDeniedRents + chain.deniedRents

    // ════════════════════════════════════════════════════════════════════════════
    // Buffers and context
    // ════════════════════════════════════════════════════════════════════════════

    /** Dry mix buffer — voices sum into this */
    val mixBuffer = StereoBuffer(blockFrames)

    /** Delay send buffer — voices write delay sends here */
    val delaySendBuffer = StereoBuffer(blockFrames)

    /** Reverb send buffer — voices write reverb sends here */
    val reverbSendBuffer = StereoBuffer(blockFrames)

    /** Shared context for all bus effects */
    val katalystContext = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = mixBuffer,
        delaySendBuffer = delaySendBuffer,
        reverbSendBuffer = reverbSendBuffer,
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
            // The classic chain resolves every knob from the owner voice, as this method's
            // `applyBusEffects` did before the chain existed; a DECLARED chain resolved its slots
            // when it was built and ignores the voice (step 3a).
            chain.applyOwner(voice)
        }
    }

    /**
     * Requests the chain registered as [name], the orbit twin of `MasterBus.requestSwap`: the
     * scheduler calls this when it consumes a `katalyst(…)` reference, whether or not the event
     * also sounds.
     *
     * The five cases, all of them cheap enough for the promotion path:
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
     *  - **The cylinder is sounding**: queued, and installed at the next deactivation (see
     *    [pendingKey]).
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

        if (isActive) {
            pendingKey = key

            return
        }

        // The last expressed intent wins: an earlier request that is still queued (unknown then,
        // or arriving while the orbit sounded) must not land after this one.
        pendingKey = null

        install(key, name, dsl)
    }

    /**
     * Installs a chain that is queued on an IDLE orbit, polled once per block by
     * [Cylinders.processAndMix].
     *
     * This is what makes a LATE registration land: the request arrived before its
     * `RegisterKatalyst` command, the orbit is silent and no further request is coming (a one-shot
     * `.katalyst(…)` on a single note emits once), so without a poll the chain would wait for the
     * orbit's next note. Costs two field reads per cylinder per block on the normal path.
     *
     * On the STUCK path (a name that never resolves, because its `RegisterKatalyst` was dropped)
     * it costs one map probe per registry in the fork chain, and still no allocation: the retry
     * takes the key [requestChain] normalized once, through [KatalystRegistry.findByKey]. A poll
     * that re-normalized the name here would be a string per cylinder per block on the audio
     * thread, which is what the delta review caught.
     */
    fun pollPendingChain() {
        if (isActive || pendingKey == null) {
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
     */
    fun processEffects() {
        if (!isActive) return

        chain.process(katalystContext)
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
        chain.processDuck(katalystContext)
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

        for (cached in chains.values) {
            cached.retire()
        }

        chains.clear()
        retiredDeniedRents = 0
        selectClassicChain()
        lease.reset()
        mixBuffer.clear()
        delaySendBuffer.clear()
        reverbSendBuffer.clear()
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
        if (chain.hasTail()) {
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
     * Installs the queued chain if there is one and it can be resolved by now. Returns true when
     * a chain was installed, so the caller knows the lease and the outgoing chain are dealt with.
     *
     * A name that is still unknown STAYS queued: the registration may yet arrive.
     */
    private fun installPending(): Boolean {
        val key = pendingKey ?: return false
        // The key door, never the normalizing one: this runs on the render path, every block the
        // orbit is idle and the name is still unknown.
        val dsl = katalysts?.findByKey(key) ?: return false

        pendingKey = null

        if (dsl == chainDsl) {
            chainKey = key
            chainRawName = key

            return false
        }

        install(key, key, dsl)

        return true
    }

    /**
     * Swaps [chain] for the chain [name] declares. Called only while this cylinder is idle, so
     * there is nothing audible to crossfade (step 3b brings the crossfade and with it the swap of
     * a SOUNDING orbit).
     *
     * **The outgoing chain is retired EAGERLY** (decided with the maintainer, 2026-09-17): its
     * rented ring and network go back to their shelves the moment it leaves service, and only its
     * stage shells stay in the cache. The price is that a swap BACK re-rents, which can cost a
     * synchronous unit reset (a shelved ring is zeroed on return, ~27k stores for the smallest
     * class) and can be DENIED under shelf pressure, leaving that chain's echo or room absent
     * until the next rent. That is the trade the warehouse exists for: keeping up to
     * [MAX_CACHED_CHAINS] chains' units alive per orbit would hold megabytes per orbit for chains
     * nobody is playing, and a swap is an edit-time event, not a per-block one. Step 3b keeps two
     * chains alive only for the length of the fade, which is the one case that needs both.
     *
     * The incoming chain needs no reset: nothing that is not the current chain holds state.
     *
     * The incoming chain is never the outgoing one, so retiring after the assignment cannot wipe
     * what was just installed: both callers return early when the request resolves to the content
     * already playing, and [chainDsl] tracks that content one-to-one with [chain].
     */
    private fun install(key: String, rawName: String, dsl: KatalystDsl) {
        val next = chainFor(key, dsl)
        val outgoing = chain

        chain = next
        chainDsl = dsl
        chainKey = key
        chainRawName = rawName

        // Carried BEFORE the retire, which zeroes the stage's own count: the orbit's diagnostics
        // number is about this cylinder's life, not about its current chain.
        retiredDeniedRents += outgoing.deniedRents
        outgoing.retire()
        // The next voice on this orbit becomes the owner cleanly and writes the new chain's
        // stages, instead of the incoming chain waiting for the previous owner to die.
        lease.reset()
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
     * in play is never evicted; an evicted one is retired, which is a no-op for its rents (it had
     * none) and the clean slate for its shells.
     */
    private fun evictIfNeeded() {
        while (chains.size >= MAX_CACHED_CHAINS) {
            // Insertion order (the map's own): the oldest declaration a live-coding session has
            // moved past is the one least likely to come back.
            val victim = chains.entries.firstOrNull { (_, cached) -> cached !== chain } ?: return

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
