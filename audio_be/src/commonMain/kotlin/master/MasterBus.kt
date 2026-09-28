/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.peekandpoke.klang.audio_be.Crossfade
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystChain
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystChainBuilder
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystContext
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.master.MasterBus.Companion.MAX_CACHED_CHAINS
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.KatalystDsl

/**
 * The master chain of one [io.peekandpoke.klang.audio_be.PlaybackEngine], and the crossfade that
 * swaps it without clicks.
 *
 * **The output runs a [KatalystChain]** (phase 3 step 12): the same chain and the same stages an
 * orbit runs, built by [KatalystChainBuilder] from the [KatalystDsl] a `master(…)` names, looked up
 * in the engine's [KatalystRegistry] fork, the same fork its cylinders read (one registry, one
 * namespace for both positions, since C5). Two things make it the OUTPUT host rather than an orbit,
 * and both are this class's, not the chain's:
 *  - **nothing fills the chain's `Param` slots** (decision (b), 2026-09-27): every chain is
 *    configured with `applyParams(null)` before it processes, so a slot is its authored default;
 *  - **it never deactivates or resets on silence** (plan risk R5): a cylinder resets its chain when
 *    its orbit goes quiet, the output does not, so a limiter envelope or a room carries across a gap
 *    exactly as the old master chain did. The only resets are the swap rule below.
 *
 * A master swap is requested by the scheduler when it consumes a `master(…)` event
 * ([requestSwap]) and takes effect in [process].
 *
 * **Dual-chain crossfade.** Old and new chains run *in parallel* over a short window and their
 * outputs are blended by the shared [Crossfade] (the ramp, the law and the block-quantized start
 * are documented there). This is the one mechanism that handles any A→B pair: parameter ramping
 * only works when both chains share a topology, and limiter/reverb state cannot be interpolated
 * meaningfully. It also warms the new chain up — it processes real audio for the whole fade, so its
 * limiter envelope has settled by the time it reaches full weight. Cost is 2× master DSP for ~60 ms,
 * **per engine, not per voice**. Precedent: `KatalystFilterSwap` (the body/vowel live-change fix).
 *
 * **The outgoing chain is CUT at the end of the fade**, tail and all. The orbit bus drains its
 * outgoing chain instead (`ChainSwap`); the master's v1 cut was accepted by the maintainer with "if
 * audible, extend the old chain's life" noted, and step 12 C4 moves this host onto the orbit's swap
 * (decision (f)).
 *
 * **A chain is built once, on its first request, and cached.** Registration builds nothing: the one
 * Katalyst registry serves both positions, so a registered chain may be an orbit's that never
 * reaches the output. [requestSwap] builds on the first request for a name (inside
 * `scheduler.process`, before any orbit of the block processes) and caches it, so a repeat or a
 * swap back is a map lookup. A Katalyst stage rents its unit at its first configure, so the build
 * configures the chain at once ([buildChain]): the rent happens at the request, where the offline
 * renderer's master always rented (plan risk R1, the unit rent order the corpus render pins). Live,
 * this moved from the retired `RegisterMaster` command to the request in C5 (both on the audio thread).
 *
 * **A refused unit degrades and recovers, the orbit's rule** (maintainer, 2026-09-27). When the
 * shelf cannot serve a reverb unit or a delay ring and the allocation fails, the stage stays in the
 * chain, Off and dry, so the bus stays active and declares its tail. While the refusal is latched the
 * stage re-asks the shelf on every block, WITHOUT allocating, and takes a unit an orbit or another
 * engine hands back from that block on, starting from an empty unit (the shelf zeroes it), so
 * nothing stale is replayed; the wet enters unramped, as an orbit's first configure does (a delay's
 * first echo can land as a step on sustained material). A [KatalystChain.reset] clears the latch:
 * the first adoption and every fade into a chain that was not just audible reset their chain, so
 * the next block retries WITH allocation, on the render thread, as an orbit's stage does. Each new latch counts once in the chain's `deniedRents` (telemetry only).
 *
 * Building therefore costs on the audio thread at the first request: Freeverb buffers when the
 * shelf has no idle unit, and a delay ring only when the backend's shelf has none of that class idle
 * (resource-warehouse step 2e: master delays rent from the same class-sized shelf as the orbits, and
 * an evicted chain's rings go back to it). The first master delay of a class on a backend still
 * allocates in render, by decision (D2, 2026-09-04), as an orbit's does. Returning to a chain that
 * has since been evicted from the bounded cache (last used more than [MAX_CACHED_CHAINS] edits ago)
 * rebuilds it the same way.
 */
class MasterBus(
    private val sampleRate: Int,
    private val blockFrames: Int,
    /** The engine's chain registry fork, the one its cylinders read too. */
    private val registry: KatalystRegistry,
    /** The backend's ring shelf; master delays rent from it and evicted chains return to it. */
    private val rings: SizedBuffers = SizedBuffers.forRings(sampleRate),
    /** The backend's reverb-unit shelf, same contract. */
    private val reverbs: ReverbUnits = ReverbUnits(sampleRate),
) {
    companion object {
        /**
         * How many built chains one bus keeps.
         *
         * Names are content-derived (`KatalystDsl.uniqueId()`), so every *edit* of a master while live
         * coding mints a new one. Without a bound, each edit's Freeverb buffers and delay ring would
         * be retained for the life of the engine. The chains still in play (current / outgoing /
         * queued) are never evicted.
         */
        private const val MAX_CACHED_CHAINS = 8

        /** Peak below which the master output counts as silent for the cheap tail pre-check. */
        private const val TAIL_SILENCE_THRESHOLD = 1e-4

        /**
         * Silent blocks between two *expensive* tail checks.
         *
         * `Reverb.hasTail()` / `DelayLine.hasTail()` scan whole buffers and document themselves as
         * "not intended for per-block use". The orbit path throttles them the same way
         * (`Cylinder.tryDeactivate` counts silent blocks before scanning); this is the master-bus
         * counterpart.
         */
        private const val TAIL_CHECK_INTERVAL_BLOCKS = 10

    }

    /** The ramp both chains are blended over. One per bus, created once. */
    private val fade: Crossfade = Crossfade(sampleRate)

    /** Built chains by (lowercased) name: built on the first request, then a map lookup. */
    private val chains = mutableMapOf<String, KatalystChain>()

    /** The empty chain: no stage, so [isActive] is false and the engine keeps its fast path. */
    private val unity: KatalystChain = buildChain(KatalystDsl(emptyList()))

    /** The active chain. Unity until a `master(…)` event says otherwise. */
    private var current: KatalystChain = unity

    /** The outgoing chain during a crossfade; null when no fade is running. */
    private var previous: KatalystChain? = null

    /** Name of the currently active master — a repeat request for the same name is a no-op. */
    private var currentName: String? = null

    /**
     * True once the owning engine has rendered at least one block. Gates the first-master
     * adoption in [requestSwap] (master round M1): before the first block nothing audible
     * exists, so a chain is adopted at full weight; afterwards a swap must crossfade.
     *
     * Set by [markRendered] rather than inside [process], because [process] is exactly what
     * does NOT run in the unmastered case — `PlaybackEngine.renderInto` takes a fast path
     * while `isActive` is false, so a flag maintained here would still read false when a
     * MID-SONG first master arrived and would wrongly hard-cut it over live audio.
     */
    private var hasRendered: Boolean = false

    /**
     * Called once per rendered block by the owning engine, after the scheduler has run and
     * before any audio is produced — so a master arriving in the engine's FIRST block still
     * sees `false`, and one arriving later sees `true`.
     */
    fun markRendered() {
        hasRendered = true
    }

    /** At most one queued swap: a request arriving mid-fade waits instead of cutting the fade. */
    private var pendingName: String? = null

    /** Scratch for the parallel chain during a crossfade, and its context; allocated on first swap. */
    private var scratch: KatalystContext? = null

    /**
     * The context the chains run on over the engine's bus. The engine hands the same buffer every
     * block, so this is built once; a caller that hands another buffer (a spec) gets a new wrapper.
     */
    private var busContext: KatalystContext? = null

    /** Last computed answer for [isRinging] — refreshed by [updateTailState], not per read. */
    private var ringing: Boolean = false

    /** Consecutive silent blocks since the last expensive tail check. */
    private var silentBlocks: Int = 0

    /**
     * True when this bus does anything at all. The engine uses it to keep the untouched fast path
     * (voices → cylinders → straight into the shared mix) for playbacks without a master.
     */
    val isActive: Boolean get() = current.pipeline.isNotEmpty() || previous != null

    /**
     * True while a reverb/delay in the master chain may still be ringing.
     *
     * The engine must stay alive until this clears, otherwise stopping a playback chops the master
     * tail — the orbit buses have the same protection via `Cylinder.reverbHasTail()`.
     */
    val isRinging: Boolean get() = ringing && hasTailUnits()

    /** True when either live chain declares a reverb/delay at all: cheap, no buffer scan. */
    private fun hasTailUnits(): Boolean = current.declaresTail() || previous?.declaresTail() == true

    private fun KatalystChain.declaresTail(): Boolean = reverb != null || delay != null

    /** Number of built chains held by this bus — for tests asserting the cache stays bounded. */
    internal val cachedChainCount: Int get() = chains.size

    /**
     * Builds the chain for [dsl] and configures it at once (see the class KDoc: the first configure
     * is where a stage rents its unit, so it has to happen here and not in the first [process]).
     * The configure writes the chain's own numbers; [process] writes them again every block.
     */
    private fun buildChain(dsl: KatalystDsl): KatalystChain = KatalystChainBuilder.build(
        dsl = dsl,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        rings = rings,
        reverbs = reverbs,
    ).also { it.applyParams(null) }

    /**
     * Returns every cached chain's units to the warehouse and drops the cache — the owning engine
     * is being disposed (resource warehouse, 2f). The bus must not process afterwards.
     */
    fun releaseAll() {
        for (chain in chains.values) {
            chain.retire()
        }
        chains.clear()
        current = unity
        previous = null
    }

    /**
     * Drops cached chains that are not in play, keeping the cache bounded.
     *
     * "In play" includes the **queued** chain: evicting it would make the fade-completion path
     * rebuild it — allocating inside the render callback, the very thing caching exists to avoid.
     */
    private fun evictIfNeeded() {
        while (chains.size >= MAX_CACHED_CHAINS) {
            val queued = pendingName?.let { chains[it] }
            val victim = chains.entries.firstOrNull { (_, chain) ->
                chain !== current && chain !== previous && chain !== queued
            } ?: return

            // Copy the entry out BEFORE the removal: Kotlin/JS refuses to read a map entry once the
            // backing map has changed (ConcurrentModificationException, found by the JS suite).
            val victimName = victim.key
            val victimChain = victim.value

            chains.remove(victimName)
            // The chain is out of play (not current, not outgoing, not queued): its rings go back
            // to the shelf, where the next master delay of that class finds them without allocating.
            victimChain.retire()
        }
    }

    /**
     * Requests a swap to the master registered as [name], starting with the next processed block.
     *
     * Repeat requests for the already-active (or already-queued) name are ignored — that is what
     * makes a top-level `master(…)`, which re-emits its event every cycle, free after the first
     * application.
     *
     * **An unknown name is NOT latched.** If the `RegisterKatalyst` command has not arrived yet, the
     * request is simply dropped, so a later re-emission of the same event still applies it. Latching
     * here would silently pin the playback to unity for good.
     *
     * Note this does *not* rescue a **recreated engine**: the backend registry is a per-engine fork
     * that dies with the engine, while the frontend's send-once set is never cleared, so the
     * registration is not re-sent. That gap is shared with the ignitor registry and the orbits' use
     * of the same Katalyst registry, and is tracked separately: it is not something this method can
     * fix.
     *
     * **A request arriving mid-fade is queued** (at most one, last writer wins) and starts when the
     * running fade completes. Cutting a fade short would drop the outgoing chain at full weight —
     * exactly the click the crossfade exists to prevent.
     */
    fun requestSwap(name: String) {
        val key = name.lowercase()

        if (key == currentName) {
            // The last expressed intent is the master already playing — drop any queued swap, or a
            // fade that started before this request would still land on the superseded chain.
            pendingName = null
            return
        }

        if (key == pendingName) {
            return
        }

        val chain = chainFor(key) ?: return

        if (previous != null) {
            pendingName = key
            return
        }

        // Ledger master round M1: adopt the FIRST master at full weight instead of fading up
        // from unity. The crossfade exists to avoid a click when swapping between two AUDIBLE
        // chains; before this bus has rendered a single block there is nothing to fade from
        // and nothing that could click, while the fade itself was audible — it ramped the
        // song's opening 60 ms up from unmastered, putting the first downbeat of every
        // mastered song up to 8.3 dB down and swelling (DerSchmetterling's gain(2.6); also
        // Tetris, StrangerThings, ATruthWorthLyingFor, IrishLamentTechno). MasterBusTest had
        // already loosened an assertion to accommodate it.
        //
        // The discriminator is "this bus has never rendered" and NOT "no master yet": a
        // mid-song first master arrives over audible signal and genuinely wants the fade.
        if (!hasRendered) {
            if (chain !== current) {
                chain.reset()
            }

            current = chain
            currentName = key
            return
        }

        beginFade(key, chain, outgoing = current)
    }

    /**
     * The built chain for [key] (already lowercased), or null if no such chain is registered here
     * or on a parent registry.
     *
     * A cache hit after the first request; the first request builds (see the class KDoc). The
     * lookup takes the key as it is ([KatalystRegistry.findByKey], no normalizing allocation).
     */
    private fun chainFor(key: String): KatalystChain? {
        chains[key]?.let { return it }

        val dsl = registry.findByKey(key) ?: return null

        evictIfNeeded()

        return buildChain(dsl).also { chains[key] = it }
    }

    /**
     * Starts a fade to [chain].
     *
     * [outgoing] is the chain that was audible immediately before (normally [current]; on the
     * fade-completion path the one that just finished fading out). A cached chain keeps its buffers
     * frozen while inactive, so re-adopting one would dump an earlier section's reverb tail — or
     * verbatim delay echoes — into the bus. It is therefore reset, EXCEPT when it is the chain that
     * was just audible: an A→B→A inside one fade window should carry A's tail through, not wipe it.
     */
    private fun beginFade(name: String, chain: KatalystChain, outgoing: KatalystChain?) {
        if (chain !== current && chain !== outgoing) {
            chain.reset()
        }

        previous = current
        current = chain
        currentName = name
        // The master blends the two chains' OUTPUTS, so the arriving chain's latency does not
        // delay this ramp; an authored lookahead limiter here keeps the comb its KDoc records.
        fade.restart(incomingDelayFrames = 0, outgoingInputDelayFrames = 0)

        if (scratch == null) {
            scratch = KatalystContext(blockFrames, StereoBuffer(blockFrames))
        }
    }

    /**
     * Applies the master chain to [bus] in place, running a crossfade if one is pending.
     *
     * [frames] MUST equal [blockFrames], which is what the engine passes. The chain's stages run a
     * whole block of their context ([blockFrames]), while the fade copies and blends [frames]: with a
     * shorter [frames] the incoming chain would process scratch frames the copy never wrote.
     */
    fun process(bus: StereoBuffer, frames: Int) {
        val outgoing = previous
        val onBus = contextOver(bus)

        if (outgoing == null) {
            run(current, onBus)
            updateTailState(bus, frames)
            return
        }

        // Crossfade: both chains must see the SAME input, so snapshot the dry bus first.
        val incoming = scratch ?: KatalystContext(blockFrames, StereoBuffer(blockFrames)).also { scratch = it }
        val wet = incoming.mixBuffer
        bus.left.copyInto(wet.left, 0, 0, frames)
        bus.right.copyInto(wet.right, 0, 0, frames)

        run(outgoing, onBus)        // bus  = outgoing output
        run(current, incoming)      // wet  = incoming output

        fade.blend(target = bus, incoming = wet, outgoing = bus, frames = frames)
        updateTailState(bus, frames)

        if (fade.isComplete) {
            // Fade complete: the outgoing chain (and its reverb/delay tail) is dropped.
            previous = null

            // A swap that arrived mid-fade waited for exactly this moment.
            pendingName?.let { queued ->
                pendingName = null
                chainFor(queued)?.let { chain -> beginFade(queued, chain, outgoing = outgoing) }
            }
        }
    }

    /**
     * One block of [chain] on [ctx]. Nothing fills a slot at the output (decision (b)): the chain is
     * configured from no param state, so a `Param` is its authored default. After the first block the
     * resolve is gated off and this writes numbers already in hand.
     */
    private fun run(chain: KatalystChain, ctx: KatalystContext) {
        chain.applyParams(null)
        chain.process(ctx)
    }

    /** The context over [bus]: the one built for it, or a new wrapper when a caller hands another buffer. */
    private fun contextOver(bus: StereoBuffer): KatalystContext {
        val known = busContext

        if (known != null && known.mixBuffer === bus) {
            return known
        }

        return KatalystContext(blockFrames, bus).also { busContext = it }
    }

    /**
     * Refreshes [isRinging] — cheaply on most blocks, thoroughly now and then.
     *
     * A chain without time-based units can never ring. While the output is audible the answer is
     * trivially yes. Only after a run of silent blocks is the *expensive* question asked (does the
     * reverb/delay still hold energy?), because a delay's output is silent between echoes and an
     * output-only test would cut the rest of them. That question is [KatalystChain.hasTail], so it
     * also counts a compressor's lookahead ring (at most 50 ms of audio not yet heard) in a chain
     * that has a reverb or a delay; a limiter-only chain still never rings. Nothing is cut either way.
     */
    private fun updateTailState(bus: StereoBuffer, frames: Int) {
        if (!hasTailUnits()) {
            ringing = false
            silentBlocks = 0
            return
        }

        if (isAudible(bus, frames)) {
            silentBlocks = 0
            ringing = true
            return
        }

        silentBlocks++

        if (silentBlocks >= TAIL_CHECK_INTERVAL_BLOCKS) {
            silentBlocks = 0
            ringing = current.hasTail() || previous?.hasTail() == true
        }
    }

    /** Cheap early-exit peak test over the block. */
    private fun isAudible(bus: StereoBuffer, frames: Int): Boolean {
        val left = bus.left
        val right = bus.right

        for (i in 0 until frames) {
            if (left[i] > TAIL_SILENCE_THRESHOLD || left[i] < -TAIL_SILENCE_THRESHOLD ||
                right[i] > TAIL_SILENCE_THRESHOLD || right[i] < -TAIL_SILENCE_THRESHOLD
            ) {
                return true
            }
        }

        return false
    }

}
