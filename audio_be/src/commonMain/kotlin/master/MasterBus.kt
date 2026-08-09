/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.master.MasterBus.Companion.MAX_CACHED_CHAINS
import io.peekandpoke.klang.audio_bridge.MasterDsl

/**
 * The master chain of one [io.peekandpoke.klang.audio_be.PlaybackEngine], and the crossfade that
 * swaps it without clicks.
 *
 * A master swap is requested by the scheduler when it consumes a `master(…)` event
 * ([requestSwap]) and takes effect in [process].
 *
 * **Dual-chain crossfade.** Old and new chains run *in parallel* over a short window and their
 * outputs are blended. This is the one mechanism that handles any A→B pair: parameter ramping only
 * works when both chains share a topology, and limiter/reverb state cannot be interpolated
 * meaningfully. It also warms the new chain up — it processes real audio for the whole fade, so its
 * limiter envelope has settled by the time it reaches full weight. Cost is 2× master DSP for ~60 ms,
 * **per engine, not per voice**. Precedent: `KatalystFilterSwap` (the body/vowel live-change fix).
 *
 * **The blend is LINEAR, not equal-power.** Both chains process the *same* input, so their outputs
 * are highly correlated; amplitude-complementary weights sum correctly, while an equal-power
 * (sin/cos) law would push correlated material up to +3 dB mid-fade. Equal-power is for
 * *uncorrelated* sources.
 *
 * **Swap start is block-quantized, the fade is per-sample.** The per-sample ramp is what prevents
 * zipper noise; the start of the fade rounds to the current block (≤ ~2.7 ms at 128 frames / 48 kHz)
 * because the shared DSP processes buffers from index 0. Inaudible against a 60 ms fade, and it
 * keeps the effect classes untouched.
 *
 * **Chains are built once, at registration.** Building allocates (Freeverb buffers, delay rings), so
 * it must not happen per swap: swaps are applied from `promoteScheduled`, inside the render
 * callback. [register] pre-builds and caches, so applying a swap is normally a map lookup.
 *
 * Two known exceptions, both bounded and documented rather than hidden: a master registered on a
 * *parent* registry (the offline renderer does this, where allocation is harmless), and returning to
 * a master that has since been evicted from the bounded cache — going back to a chain last used more
 * than [MAX_CACHED_CHAINS] edits ago rebuilds it on the audio thread.
 *
 * Note that registration is *also* handled on the audio thread (both backends drain commands there),
 * so building still costs one allocation at that moment — bounded by sizing delay rings to their
 * declared time. That residual is the same class as the known cylinder allocation spike and belongs
 * to the resource-warehouse-pool work, not here.
 */
class MasterBus(
    private val sampleRate: Int,
    private val blockFrames: Int,
    private val registry: MasterRegistry,
) {
    companion object {
        /** Crossfade length for a master swap. Tune by ear. */
        const val MASTER_XFADE_SECONDS: Double = 0.06

        /**
         * How many built chains one bus keeps.
         *
         * Names are content-derived (`MasterDsl.uniqueId()`), so every *edit* of a master while live
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

    private val fadeFrames: Int = (MASTER_XFADE_SECONDS * sampleRate).toInt().coerceAtLeast(1)

    /** Built chains by (lowercased) name — allocation happens here, never on the swap path. */
    private val chains = mutableMapOf<String, MasterChain>()

    private val unity: MasterChain = MasterChain.build(
        dsl = MasterDsl.default,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
    )

    /** The active chain. Unity until a `master(…)` event says otherwise. */
    private var current: MasterChain = unity

    /** The outgoing chain during a crossfade; null when no fade is running. */
    private var previous: MasterChain? = null

    /** Frames already elapsed in the running crossfade. */
    private var fadePos: Int = 0

    /** Name of the currently active master — a repeat request for the same name is a no-op. */
    private var currentName: String? = null

    /** At most one queued swap: a request arriving mid-fade waits instead of cutting the fade. */
    private var pendingName: String? = null

    /** Scratch for the parallel chain during a crossfade; allocated on first swap. */
    private var scratch: StereoBuffer? = null

    /** Last computed answer for [isRinging] — refreshed by [updateTailState], not per read. */
    private var ringing: Boolean = false

    /** Consecutive silent blocks since the last expensive tail check. */
    private var silentBlocks: Int = 0

    /**
     * True when this bus does anything at all. The engine uses it to keep the untouched fast path
     * (voices → cylinders → straight into the shared mix) for playbacks without a master.
     */
    val isActive: Boolean get() = current.isActive || previous != null

    /**
     * True while a reverb/delay in the master chain may still be ringing.
     *
     * The engine must stay alive until this clears, otherwise stopping a playback chops the master
     * tail — the orbit buses have the same protection via `Cylinder.reverbHasTail()`.
     */
    val isRinging: Boolean get() = ringing && hasTailUnits()

    /** True when either live chain owns a reverb/delay at all — cheap, no buffer scan. */
    private fun hasTailUnits(): Boolean = current.hasTail || previous?.hasTail == true

    /** Number of built chains held by this bus — for tests asserting the cache stays bounded. */
    internal val cachedChainCount: Int get() = chains.size

    /**
     * Registers a custom master chain for this playback **and builds it** (allocating), so applying
     * it later is allocation-free.
     */
    fun register(name: String, dsl: MasterDsl) {
        val key = name.lowercase()

        registry.register(name, dsl)

        // Names are content-derived (`MasterDsl.uniqueId()`), so a name we already hold is already
        // the right chain: rebuilding would burn an allocation and orphan a live instance mid-fade.
        if (chains.containsKey(key)) {
            return
        }

        evictIfNeeded()
        chains[key] = MasterChain.build(dsl, sampleRate, blockFrames)
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

            chains.remove(victim.key)
        }
    }

    /**
     * Requests a swap to the master registered as [name], starting with the next processed block.
     *
     * Repeat requests for the already-active (or already-queued) name are ignored — that is what
     * makes a top-level `master(…)`, which re-emits its event every cycle, free after the first
     * application.
     *
     * **An unknown name is NOT latched.** If the `RegisterMaster` command has not arrived yet, the
     * request is simply dropped, so a later re-emission of the same event still applies it. Latching
     * here would silently pin the playback to unity for good.
     *
     * Note this does *not* rescue a **recreated engine**: the backend registry is a per-engine fork
     * that dies with the engine, while the frontend's send-once set is never cleared, so the
     * registration is not re-sent. That gap is shared with the ignitor and pipeline registries and
     * is tracked separately — it is not something this method can fix.
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

        beginFade(key, chain, outgoing = current)
    }

    /**
     * The built chain for [name], or null if no such master is known here.
     *
     * Normally a cache hit ([register] pre-builds). The lazy branch covers a master registered
     * directly on a parent registry — the offline renderer does that, where allocation is harmless.
     */
    private fun chainFor(key: String): MasterChain? {
        chains[key]?.let { return it }

        val dsl = registry.find(key) ?: return null

        evictIfNeeded()

        return MasterChain.build(dsl, sampleRate, blockFrames).also { chains[key] = it }
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
    private fun beginFade(name: String, chain: MasterChain, outgoing: MasterChain?) {
        if (chain !== current && chain !== outgoing) {
            chain.reset()
        }

        previous = current
        current = chain
        currentName = name
        fadePos = 0

        if (scratch == null) {
            scratch = StereoBuffer(blockFrames)
        }
    }

    /** Applies the master chain to [bus] in place, running a crossfade if one is pending. */
    fun process(bus: StereoBuffer, frames: Int) {
        val outgoing = previous

        if (outgoing == null) {
            current.process(bus, frames)
            updateTailState(bus, frames)
            return
        }

        // Crossfade: both chains must see the SAME input, so snapshot the dry bus first.
        val wet = scratch ?: StereoBuffer(blockFrames).also { scratch = it }
        bus.left.copyInto(wet.left, 0, 0, frames)
        bus.right.copyInto(wet.right, 0, 0, frames)

        outgoing.process(bus, frames)   // bus  = outgoing output
        current.process(wet, frames)    // wet  = incoming output

        blendInto(bus, wet, frames)
        updateTailState(bus, frames)

        if (fadePos >= fadeFrames) {
            // Fade complete: the outgoing chain (and its reverb/delay tail) is dropped.
            previous = null
            fadePos = 0

            // A swap that arrived mid-fade waited for exactly this moment.
            pendingName?.let { queued ->
                pendingName = null
                chainFor(queued)?.let { chain -> beginFade(queued, chain, outgoing = outgoing) }
            }
        }
    }

    /**
     * Refreshes [isRinging] — cheaply on most blocks, thoroughly now and then.
     *
     * A chain without time-based units can never ring. While the output is audible the answer is
     * trivially yes. Only after a run of silent blocks is the *expensive* question asked (does the
     * reverb/delay still hold energy?), because a delay's output is silent between echoes and an
     * output-only test would cut the rest of them.
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
            ringing = current.hasActiveTail() || previous?.hasActiveTail() == true
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

    /** Linear per-sample blend of the outgoing (already in [bus]) and incoming ([wet]) chains. */
    private fun blendInto(bus: StereoBuffer, wet: StereoBuffer, frames: Int) {
        val busL = bus.left
        val busR = bus.right
        val wetL = wet.left
        val wetR = wet.right
        val total = fadeFrames.toDouble()
        var pos = fadePos

        for (i in 0 until frames) {
            val t = if (pos >= fadeFrames) 1.0 else pos / total
            val u = 1.0 - t

            busL[i] = busL[i] * u + wetL[i] * t
            busR[i] = busR[i] * u + wetR[i] * t

            pos++
        }

        fadePos = pos
    }

}
