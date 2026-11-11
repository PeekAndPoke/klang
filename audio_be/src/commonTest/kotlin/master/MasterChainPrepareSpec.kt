/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.BackendClock
import io.peekandpoke.klang.audio_be.ChainHost
import io.peekandpoke.klang.audio_be.PlaybackEngine
import io.peekandpoke.klang.audio_be.PlaybackEngineDispatcher
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlin.math.PI
import kotlin.math.sin

/**
 * A master chain is built ahead of its request, between render blocks (KLANG-00137 (master dsl followups) §3, the
 * resource warehouse's second customer): when a schedule carrying its `master(…)` event arrives. The render block that
 * lands it then builds nothing. A build is the shells only: the units are rented at the chain's first configure, from
 * the shelf, so a chain out of service still holds none.
 */
class MasterChainPrepareSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun c(v: Double) = KatalystKnobDsl.Constant(v)

    /** A master with a unit to rent (the reverb), a delay and an insert. */
    val room = KatalystDsl.of(
        KatalystStageDsl.Reverb(wet = c(0.4), size = c(0.6)),
        KatalystStageDsl.Delay(wet = c(0.3), time = c(0.2), feedback = c(0.4)),
        KatalystStageDsl.Gain(gain = c(0.8)),
    )

    /** A master bus alone, fed a 330 Hz sine. */
    class BusRig(sampleRate: Int, private val blockFrames: Int) {
        val registry = KatalystRegistry()
        val units = ReverbUnits(sampleRate)
        val rings = SizedBuffers.forRings(sampleRate)
        val bus = MasterBus(sampleRate = sampleRate, blockFrames = blockFrames, registry = registry, rings = rings, reverbs = units)
        private val buf = StereoBuffer(blockFrames)
        private val rate = sampleRate.toDouble()
        private var n = 0

        fun block(): DoubleArray {
            for (i in 0 until blockFrames) {
                val v = 0.4 * sin(2.0 * PI * 330.0 * (n + i) / rate)
                buf.left[i] = v
                buf.right[i] = v
            }

            n += blockFrames
            bus.pollPendingSwap()
            bus.process(buf, blockFrames)
            bus.markRendered()

            return buf.left.copyOf()
        }
    }

    /** A dispatcher and its engine "song", rendered block by block. */
    class EngineRig {
        val d = PlaybackEngineDispatcher.create(
            sampleRate = 44100,
            blockFrames = 128,
            commLink = KlangCommLink(capacity = 1024).backend,
            performanceTimeMs = { 0.0 },
        ).also { it.setBackendStartTime(0.0) }

        private val clock = d.clockForTest as BackendClock
        private val buf = StereoBuffer(128)
        private var b = 0

        val engine: PlaybackEngine get() = d.engine("song").shouldNotBeNull()
        val bus: MasterBus get() = engine.masterBusForTest

        fun blocks(count: Int) {
            repeat(count) {
                clock.cursorFrame = b * 128.0
                buf.clear()
                engine.renderInto(buf, b * 128.0)
                b++
                clock.cursorFrame = b * 128.0
            }
        }
    }

    fun masterEvent(name: String, at: Double) = ScheduledVoice(
        playbackId = "song",
        startTime = at,
        gateEndTime = at + 0.1,
        data = VoiceData.empty.copy(master = name, control = true),
        playbackStartTime = 0.0,
    )

    fun tone() = ScheduledVoice(
        playbackId = "song",
        startTime = 0.0,
        gateEndTime = 1.0,
        data = VoiceData.empty.copy(sound = "sine", freqHz = 220.0, gain = 0.5),
        playbackStartTime = 0.0,
    )

    "a master built ahead is its shells: nothing rented, the landing builds nothing, and it rents at its first block" {
        val rig = BusRig(sampleRate, blockFrames)
        rig.registry.register("room", room)
        val born = rig.bus.builtChains

        rig.bus.prepare("room")

        withClue("built ahead, into the cache") {
            rig.bus.builtChains shouldBe born + 1
            rig.bus.cachedChainCount shouldBe 1
        }

        withClue("the shells only: no reverb unit and no delay ring rented") {
            (rig.units.allocations + rig.units.hits) shouldBe 0
            (rig.rings.allocations + rig.rings.hits) shouldBe 0
        }

        rig.bus.requestSwap("room")

        withClue("the landing is a cache hit") {
            rig.bus.builtChains shouldBe born + 1
            rig.bus.isActive shouldBe true
        }

        rig.block()

        withClue("its units are rented at its first configure") {
            (rig.units.allocations + rig.units.hits) shouldBe 1
            (rig.rings.allocations + rig.rings.hits) shouldBe 1
        }
    }

    "a master built ahead sounds as one built on its landing, bit for bit" {
        val ahead = BusRig(sampleRate, blockFrames)
        val onLanding = BusRig(sampleRate, blockFrames)

        for (r in listOf(ahead, onLanding)) {
            r.registry.register("room", room)
        }

        ahead.bus.prepare("room")
        ahead.bus.requestSwap("room")
        onLanding.bus.requestSwap("room")

        val dry = BusRig(sampleRate, blockFrames)
        var differsFromDry = false

        for (b in 0 until 400) {
            val a = ahead.block()
            val o = onLanding.block()

            withClue("block $b") {
                a.contentEquals(o) shouldBe true
            }

            differsFromDry = differsFromDry || !a.contentEquals(dry.block())
        }

        withClue("positive control: the master changes the sound") {
            differsFromDry shouldBe true
        }
    }

    "nothing is built ahead for the chain in service, a chain already built, or a name not registered" {
        val rig = BusRig(sampleRate, blockFrames)
        rig.registry.register("room", room)
        rig.bus.requestSwap("room")
        rig.block()
        val built = rig.bus.builtChains

        rig.bus.prepare("room")
        rig.bus.prepare("ROOM")
        rig.bus.prepare("nobody")

        rig.bus.builtChains shouldBe built
        rig.bus.cachedChainCount shouldBe 1
    }

    "chains built ahead stay within the cache's bound, the chain in service kept" {
        val rig = BusRig(sampleRate, blockFrames)
        rig.registry.register("room", room)
        rig.bus.requestSwap("room")
        rig.block()

        for (n in 0 until 2 * ChainHost.MAX_CACHED_CHAINS) {
            rig.registry.register("g$n", KatalystDsl.of(KatalystStageDsl.Gain(gain = c(0.5 + n * 0.01))))
            rig.bus.prepare("g$n")
        }

        rig.bus.cachedChainCount shouldBe ChainHost.MAX_CACHED_CHAINS

        withClue("the chain in service was never evicted: asked again, it is not rebuilt") {
            val built = rig.bus.builtChains
            rig.bus.prepare("ROOM")
            rig.bus.builtChains shouldBe built
        }
    }

    "a master scheduled ahead is built when its schedule arrives, and the render block that lands it builds nothing" {
        val rig = EngineRig()
        rig.d.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "song", name = "room", dsl = room))
        val born = rig.bus.builtChains

        rig.d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(masterEvent("room", at = 0.5), tone())))

        withClue("built on the command path") {
            rig.bus.builtChains shouldBe born + 1
            rig.bus.isActive shouldBe false
        }

        rig.blocks(sampleRate / blockFrames)

        withClue("landed at 0.5 s, and no block built it") {
            rig.bus.isActive shouldBe true
            rig.bus.builtChains shouldBe born + 1
        }
    }

    "a master in a single scheduled voice is built ahead too" {
        // A batch of one straight into the scheduler, the benchmarks' path (the single-voice `scheduleVoice` it went
        // through left with KLANG-00433 Decide 2)
        val rig = EngineRig()
        rig.d.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "song", name = "room", dsl = room))
        rig.d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(tone())))
        val born = rig.bus.builtChains

        rig.engine.scheduler.scheduleVoices(listOf(masterEvent("room", at = 0.5)))

        rig.bus.builtChains shouldBe born + 1
    }

    "building ahead while a swap drains never evicts the leaving chain: its tail rings on" {
        val rig = BusRig(sampleRate, blockFrames)
        rig.registry.register("room", room)
        rig.registry.register("dry", KatalystDsl.of(KatalystStageDsl.Gain(gain = c(0.5))))
        rig.bus.requestSwap("room")
        repeat(40) { rig.block() }
        rig.bus.requestSwap("dry")
        repeat(30) { rig.block() }

        withClue("positive control: the room is leaving, its tail still draining") {
            rig.bus.chainSwap.settled shouldBe false
        }

        // A schedule of many new masters, more than the cache holds
        for (n in 0 until 2 * ChainHost.MAX_CACHED_CHAINS) {
            rig.registry.register("g$n", KatalystDsl.of(KatalystStageDsl.Gain(gain = c(0.5 + n * 0.01))))
            rig.bus.prepare("g$n")
        }

        rig.block()

        withClue("the room still drains: not retired by an eviction") {
            rig.bus.chainSwap.settled shouldBe false
        }
    }
})
