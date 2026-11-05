/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.peekandpoke.klang.audio_be.ChainHost
import io.peekandpoke.klang.audio_be.ChainSwap
import io.peekandpoke.klang.audio_be.Crossfade
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.katalyst.KatalystChain
import io.peekandpoke.klang.audio_be.katalyst.KatalystChainBuilder
import io.peekandpoke.klang.audio_be.katalyst.KatalystContext
import io.peekandpoke.klang.audio_be.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.constants.SILENCE_FLOOR

/**
 * The master chain of one [io.peekandpoke.klang.audio_be.PlaybackEngine], and the swap that
 * replaces it without clicks.
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
 *    exactly as the old master chain did. The only reset is the one a chain gets when it comes into
 *    service ([landChain]).
 *
 * A master swap is requested by the scheduler when it consumes a `master(…)` event
 * ([requestSwap]) and takes effect in [process].
 *
 * **The swap is the orbit's, [ChainSwap]** (step 12 C4, decision (f): one law at both positions).
 * The chain leaving service is fed a shrinking copy of the bus while the arriving chain's output is
 * ramped up over [Crossfade], both delayed so they meet at the output when either chain is late
 * (a lookahead limiter); then the leaving chain DRAINS: it keeps running on silence at full weight,
 * so its room and its echoes ring out, and it is retired (its units back to the shelves) on the first
 * block it reports no tail. The arriving chain is warmed up on real audio for the whole fade, so its
 * limiter envelope has settled by the time it carries full weight. Cost is 2x master DSP for the fade
 * and the leaving chain's ring-out, **per engine, not per voice**. Before C4 the master blended the
 * two chains' OUTPUTS and cut the leaving one, tail and all, at the end of the fade.
 *
 * What stays this host's (the [ChainSwap] class KDoc lists what a host keeps):
 *  - **the instant install**: a master that arrives before the engine has rendered its first block
 *    is adopted at full weight, no fade (master round M1, plan risk R4; [hasRendered] is the
 *    predicate);
 *  - **no duck**: a duck stage is inert at the output (decision (c)), so this host raises none of the
 *    swap's duck events.
 *
 * **The chain plumbing is the [ChainHost] this bus shares with every orbit** (KLANG-00243 (one chain
 * host)): the chain in service and its name, the cache, the parked request (latest wins, offered
 * again by [pollPendingSwap] every block) and the lookup.
 *
 * **A chain is built once, on the request that first lands it, and cached.** Registration builds
 * nothing: the one Katalyst registry serves both positions, so a registered chain may be an orbit's
 * that never reaches the output. A chain is built when a request for it LANDS ([landChain], on the
 * request itself when the swap is idle, inside `scheduler.process` before any orbit of the block
 * processes; or on the block's poll when it was parked), and cached, so a repeat is a map lookup. A
 * Katalyst stage rents its unit at its first configure, so the build configures the chain at once
 * ([buildChain]): the rent happens where the offline renderer's master always rented (plan risk R1,
 * the unit rent order the corpus render pins).
 *
 * **A chain out of service holds no units** (eager retire, `Cylinder.install`'s rule, step 12 C4): the
 * chain an instant install replaces is retired at once, and a swapped-away chain when its drain ends.
 * The cache keeps its stage shells; a chain that comes back is reset and re-rents at its first
 * configure, which can allocate on the audio thread when the shelf is empty and can be refused under
 * shelf pressure (then the rule below applies). Nothing ever holds a room or a ring for a chain
 * nobody plays (the allocation-cleanup rule).
 *
 * **A refused unit degrades and recovers, the orbit's rule** (maintainer, 2026-09-27). When the
 * shelf cannot serve a reverb unit or a delay ring and the allocation fails, the stage stays in the
 * chain, Off and dry, so the bus stays active and declares its tail. While the refusal is latched the
 * stage re-asks the shelf on every block, WITHOUT allocating, and takes a unit an orbit or another
 * engine hands back from that block on, starting from an empty unit (the shelf zeroes it), so
 * nothing stale is replayed; the wet enters unramped, as an orbit's first configure does (a delay's
 * first echo can land as a step on sustained material). A [KatalystChain.reset] clears the latch:
 * every chain is reset when it lands, so its next block retries WITH allocation, on the render
 * thread, as an orbit's stage does. Each new latch counts once in the chain's `deniedRents`
 * (telemetry only).
 *
 * Building therefore costs on the audio thread when a chain first lands: Freeverb buffers when the
 * shelf has no idle unit, and a delay ring only when the backend's shelf has none of that class idle
 * (resource-warehouse step 2e: master delays rent from the same class-sized shelf as the orbits). The
 * first master delay of a class on a backend still allocates in render, by decision (D2,
 * 2026-09-04), as an orbit's does. Returning to a chain that has since been evicted from the bounded
 * cache (last landed more than [ChainHost.MAX_CACHED_CHAINS] edits ago) rebuilds it the same way.
 */
class MasterBus(
    private val sampleRate: Int,
    private val blockFrames: Int,
    /** The engine's chain registry fork, the one its cylinders read too. */
    registry: KatalystRegistry,
    /** The backend's ring shelf; master delays rent from it and chains leaving service return to it. */
    private val rings: SizedBuffers = SizedBuffers.forRings(sampleRate),
    /** The backend's reverb-unit shelf, same contract. */
    private val reverbs: ReverbUnits = ReverbUnits(sampleRate),
) {
    companion object {
        /**
         * Silent blocks between two tail checks on the chains in play.
         *
         * The orbit path throttles its deactivation check the same way (`Cylinder.tryDeactivate`
         * counts silent blocks before asking); this is the master-bus counterpart.
         */
        private const val TAIL_CHECK_INTERVAL_BLOCKS = 10

    }

    /**
     * The chain plumbing this bus shares with the orbits ([ChainHost]). Born with the EMPTY chain: no
     * stage, so [isActive] is false and the engine keeps its fast path. A `master(Katalyst())`
     * declares the same content and is built as a chain of its own (the output never answered a
     * declaration with its born-with chain).
     */
    private val host = ChainHost(
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        position = object : ChainHost.Position {
            override fun build(dsl: KatalystDsl): KatalystChain = buildChain(dsl)

            override fun land(key: String, rawName: String, dsl: KatalystDsl): Boolean = landChain(key = key, rawName = rawName, dsl = dsl)
        },
        bornWithDsl = KatalystDsl(emptyList()),
        bornWithAnswersItsContent = false,
        registry = registry,
    )

    /**
     * The chain swap (step 12 C4, the orbit's [ChainSwap]): the chain LEAVING service, fading out
     * and then draining. The [host] asks [ChainSwap.settled] before it lands a request and parks it
     * otherwise.
     */
    private val swap: ChainSwap = host.swap

    /** The chain in service. The empty one until a `master(…)` event says otherwise. */
    private val current: KatalystChain get() = host.current

    /**
     * True once the owning engine has rendered at least one block. Gates the first-master
     * adoption in [landChain] (master round M1): before the first block nothing audible
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

    /**
     * The context the chains run on over the engine's bus. The engine hands the same buffer every
     * block, so this is built once; a caller that hands another buffer (a spec) gets a new wrapper.
     */
    private var busContext: KatalystContext? = null

    /** Last computed answer for [isRinging] — refreshed by [updateTailState], not per read. */
    private var ringing: Boolean = false

    /**
     * Consecutive silent blocks since the last tail check. Wrap-safe by construction (audit leftovers §3): it counts
     * blocks, and every path of [updateTailState] resets it or lets it reach [TAIL_CHECK_INTERVAL_BLOCKS], where it
     * resets, so it never exceeds 10, however long the output stays silent.
     */
    private var silentBlocks: Int = 0

    /**
     * True when this bus does anything at all: a chain with stages in service, or a chain still
     * leaving it (fading or draining, even when the chain in service is the empty one). The engine
     * uses it to keep the untouched fast path (voices → cylinders → straight into the shared mix)
     * for playbacks without a master.
     */
    val isActive: Boolean get() = current.pipeline.isNotEmpty() || !swap.settled

    /**
     * True while the master output may still be ringing: it was audible in the last block, or a stage that can ring
     * ([KatalystChain.declaresTail]) may still hold energy. Read only while the bus [isActive]: a bus that went quiet
     * on the empty chain is not processed, so its last answer is stale and nothing of it rings.
     *
     * The engine must stay alive until this clears, otherwise stopping a playback chops the master tail. The orbits
     * have the same protection in their deactivation (`Cylinder.tryDeactivate`: the mix scan, then the chain's
     * `hasTail`). An insert stage (an eq bell, a phaser, a body) declares no tail but rings on for a moment; until
     * KLANG-00415 (master stop click on insert-only chains) a chain of inserts only was disposed while it still rang,
     * a click (a +24 dB eq bell at q 30 left a step of 0.3).
     */
    val isRinging: Boolean get() = ringing && isActive

    /** True while nothing is leaving the bus: no fade, drain or release of a chain swapped away runs. */
    val isSettled: Boolean get() = swap.settled

    /**
     * Frames the chain in service delays the bus by ([KatalystChain.latencyFrames]: a compressor's lookahead, an
     * oversampling region). An offline render runs this far past its end as well, or the music's last frames stay in
     * the chain. Only the chain in service counts: a chain swapped away earlier was fed silence after its fade, so
     * what it still rings is tail. The one case it misses: at `tailSec = 0`, a swap to a chain that delays less, within
     * about a fade plus the leaving chain's latency of the end, cuts the leaving chain's last frames of music. A
     * render's tail covers it.
     */
    val latencyFrames: Int get() = current.latencyFrames

    /**
     * True while the chain in service holds a tail that can never end on its own
     * ([KatalystChain.sustainsItself]; phase 3 step 12 decision (j)). The leaving chain is not
     * asked: the engine asks only once this bus [isSettled], when nothing is leaving (a leaving
     * chain's endless tail is the swap's to end, [ChainSwap] caps and releases it).
     */
    fun sustainsItself(): Boolean = current.sustainsItself()

    /** True when a chain in play declares a stage that can ring ([KatalystChain.declaresTail]): cheap, no buffer scan. */
    private fun hasTailUnits(): Boolean = current.declaresTail || swap.leaving?.declaresTail == true

    /** Number of built chains held by this bus, for tests asserting the cache stays bounded. */
    internal val cachedChainCount: Int get() = host.cachedChainCount

    /** Test seam: the swap, for the specs that name its phase or its leaving chain. */
    internal val chainSwap: ChainSwap get() = swap

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
        // A retire is idempotent (a stage hands back only the unit it still holds), so the leaving
        // chain, a cached one too, is retired twice at no cost. The cut drops the reference.
        host.retireAll()
    }

    /**
     * Requests a swap to the master registered as [name], starting with the next processed block.
     * The scheduler calls it when it consumes a `master(…)` event. The paths (already in service,
     * not registered yet, the swap busy) are the [ChainHost]'s, the orbit's own; a request that can
     * land lands in [landChain].
     *
     * Note this does *not* rescue a **recreated engine**: the backend registry is a per-engine fork
     * that dies with the engine, while the frontend's send-once set is never cleared, so the
     * registration is not re-sent. That gap is shared with the ignitor registry and the orbits' use
     * of the same Katalyst registry, and is tracked separately: it is not something this method can
     * fix.
     */
    fun requestSwap(name: String) {
        host.request(name)
    }

    /**
     * Lands the parked request once it can: its name has been registered and the swap is settled.
     * Called by the owning engine once per block, after the scheduler, whether or not this bus is
     * active (a parked name must be able to land on an engine still on its fast path).
     */
    fun pollPendingSwap() {
        host.pollPending()
    }

    /**
     * Puts the chain for [key] in service, for the [host] ([ChainHost.Position.land]); only while the
     * swap is settled.
     *
     * The arriving chain is RESET: a cached chain was retired when it left service, so it holds no
     * rented unit, but its small stages (a compressor's envelope) and a refused-unit latch are
     * cleared here, so it comes in as a clean slate. It is never the chain leaving service: a
     * request for that one parks until the drain is over.
     *
     * Before the engine's first block (master round M1): adopted at full weight, no fade, and the
     * chain it replaces retired at once. The crossfade exists to avoid a click when swapping
     * between two AUDIBLE chains; before this bus has rendered a single block there is nothing to
     * fade from, while the fade itself was audible: it ramped the song's opening 60 ms up from
     * unmastered, putting the first downbeat of every mastered song up to 8.3 dB down
     * (DerSchmetterling's gain(2.6); also Tetris, StrangerThings, ATruthWorthLyingFor,
     * IrishLamentTechno). The discriminator is "this bus has never rendered" and NOT "no master
     * yet": a mid-song first master arrives over audible signal and genuinely wants the fade.
     *
     * Returns true: the output's callers do not ask.
     */
    private fun landChain(key: String, rawName: String, dsl: KatalystDsl): Boolean {
        val next = host.chainFor(key, dsl)
        val leaving = current

        next.reset()

        host.enter(chain = next, key = key, rawName = rawName)

        if (!hasRendered) {
            swap.retire(leaving)

            return true
        }

        swap.begin(leaving = leaving, arrivingLatencyFrames = next.latencyFrames, duckingOut = null, duckFadingIn = false)

        return true
    }

    /**
     * Applies the master chain to [bus] in place, and whatever is leaving service with it.
     *
     * [frames] MUST equal [blockFrames], which is what the engine passes: the chains and the swap
     * run whole blocks of their context ([blockFrames]).
     *
     * Nothing fills a slot at the output (decision (b)): the chain in service, and the leaving one
     * while it fades, are configured from no param state, so a `Param` is its authored default.
     * After the first block the resolve is gated off and this writes numbers already in hand. A
     * draining chain is configured by nobody ([ChainSwap]): it rings out on the settings it left
     * with.
     */
    fun process(bus: StereoBuffer, frames: Int) {
        val onBus = contextOver(bus)

        current.applyParams(null)
        swap.configureLeaving(null)
        swap.process(current, onBus)

        updateTailState(bus, frames)
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
     * While the output is audible the answer is yes, for every chain. A silent output of a chain without a stage that
     * can ring ([KatalystChain.declaresTail]) asks [KatalystChain.hasTail] at once, an O(1) answer per stage: false for
     * the inserts, true for a compressor's lookahead ring that still holds audio not yet heard (at most 50 ms; the
     * review of KLANG-00415 (master stop click on insert-only chains): a last short hit after a gap was disposed
     * unplayed). Otherwise only after a run of silent blocks is the tail question asked, because a delay's output is
     * silent between echoes and an output-only test would cut the rest of them.
     */
    private fun updateTailState(bus: StereoBuffer, frames: Int) {
        if (isAudible(bus, frames)) {
            silentBlocks = 0
            ringing = true
            return
        }

        if (!hasTailUnits()) {
            ringing = current.hasTail() || swap.leaving?.hasTail() == true
            silentBlocks = 0
            return
        }

        silentBlocks++

        if (silentBlocks >= TAIL_CHECK_INTERVAL_BLOCKS) {
            silentBlocks = 0
            ringing = current.hasTail() || swap.leaving?.hasTail() == true
        }
    }

    /**
     * Cheap early-exit peak test over the block, at the engine's one floor, [SILENCE_FLOOR] (-100 dBFS). It was 1e-4
     * (-80 dBFS, decision D9 of the engine audit) until the maintainer made it the same as everywhere else
     * (2026-11-05, KLANG-00290 (engine follow-ups) item 25): a stopped engine is disposed with a step under -100 dB.
     */
    private fun isAudible(bus: StereoBuffer, frames: Int): Boolean {
        val left = bus.left
        val right = bus.right

        for (i in 0 until frames) {
            if (left[i] > SILENCE_FLOOR || left[i] < -SILENCE_FLOOR ||
                right[i] > SILENCE_FLOOR || right[i] < -SILENCE_FLOOR
            ) {
                return true
            }
        }

        return false
    }

}
