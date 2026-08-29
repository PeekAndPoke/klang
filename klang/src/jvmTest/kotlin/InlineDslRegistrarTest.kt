/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterValue
import io.peekandpoke.klang.audio_bridge.PipelineDsl
import io.peekandpoke.klang.audio_bridge.PipelineValue
import io.peekandpoke.klang.audio_bridge.SoundValue
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.uniqueId
import io.peekandpoke.klang.common.SourceLocationChain
import io.peekandpoke.klang.common.infra.KlangLock
import io.peekandpoke.klang.common.infra.withLock

/**
 * Behavioural tests for [InlineDslRegistrar] — the per-playback bookkeeping that announces inline
 * DSL trees to the backend once each. Synthetic names come from the process-wide `uniqueId()`
 * maps; this class only tracks which DSLs it has already sent a `Cmd.Register*` for.
 *
 * (Was `IgnitorRegistryTest`, against the first of three byte-identical registry classes. The
 * generic [AnnounceOnceRegistry] replaced all three, so the announce-once cases below now exercise
 * that one code path through the ignitor door, and the sweep cases pin the shared
 * [InlineDslRegistrar.announceAll] that used to be hand-written per call site.)
 */
class InlineDslRegistrarTest : StringSpec({

    val playbackId = "pb-test"

    fun newRegistrar(): Pair<InlineDslRegistrar, MutableList<KlangCommLink.Cmd>> {
        // The concurrent test exercises this from many threads; the production code path
        // serialises sendControl through the player, but the bare MutableList here would
        // lose adds without explicit synchronisation. KlangLock is KMP-portable.
        val sendLock = KlangLock()
        val sent = mutableListOf<KlangCommLink.Cmd>()
        val registrar = InlineDslRegistrar.overWire(
            playbackId = playbackId,
            sendControl = { cmd -> sendLock.withLock { sent.add(cmd) } },
        )
        return registrar to sent
    }

    /** Minimal event carrying only what the sweep reads. */
    class FakeEvent(
        override val sound: SoundValue? = null,
        override val pipeline: PipelineValue? = null,
        override val master: MasterValue? = null,
    ) : KlangPatternEvent {
        override val startCycles: Double = 0.0
        override val durationCycles: Double = 1.0
        override val sourceLocations: SourceLocationChain? = null
        override fun toVoiceData(): VoiceData = VoiceData.empty
    }

    // ── announce-once, through the ignitor door ──────────────────────────────

    "registerOrLookup returns the same name for the same DSL instance" {
        val (reg, sent) = newRegistrar()
        val dsl = IgnitorDsl.Sine()

        val name1 = reg.ignitors.registerOrLookup(dsl)
        val name2 = reg.ignitors.registerOrLookup(dsl)

        name1 shouldBe name2
        sent.size shouldBe 1 // only one RegisterIgnitor command goes to the backend
    }

    "structurally-equal but distinct DSL instances collapse to the same name" {
        val (reg, sent) = newRegistrar()

        val name1 = reg.ignitors.registerOrLookup(IgnitorDsl.Sine())
        val name2 = reg.ignitors.registerOrLookup(IgnitorDsl.Sine())

        name1 shouldBe name2
        sent.size shouldBe 1
    }

    "different DSL trees get distinct names" {
        val (reg, sent) = newRegistrar()

        val name1 = reg.ignitors.registerOrLookup(IgnitorDsl.Sine())
        val name2 = reg.ignitors.registerOrLookup(IgnitorDsl.Sawtooth())
        val name3 = reg.ignitors.registerOrLookup(IgnitorDsl.Square())

        name1 shouldNotBe name2
        name2 shouldNotBe name3
        name1 shouldNotBe name3
        sent.size shouldBe 3
    }

    "register emits a RegisterIgnitor command with the synthetic name, the DSL, and the playbackId" {
        val (reg, sent) = newRegistrar()
        val dsl = IgnitorDsl.Lowpass(inner = IgnitorDsl.Sine(), freq = IgnitorDsl.Constant(2000.0))

        val name = reg.ignitors.registerOrLookup(dsl)

        sent.size shouldBe 1
        val cmd = sent.single() as KlangCommLink.Cmd.RegisterIgnitor
        cmd.name shouldBe name
        cmd.dsl shouldBe dsl
        // Stamped with THIS playback's id, so the backend registers it on that engine's fork.
        cmd.playbackId shouldBe playbackId
    }

    "synthetic name matches IgnitorDsl.uniqueId()" {
        val (reg, _) = newRegistrar()
        val dsl = IgnitorDsl.Triangle()
        reg.ignitors.registerOrLookup(dsl) shouldBe dsl.uniqueId()
    }

    "two registrars (two playbacks) share names but each announces independently" {
        val (regA, sentA) = newRegistrar()
        val (regB, sentB) = newRegistrar()
        // A fresh, distinct DSL ensures this test is order-independent re: global counter.
        val dsl = IgnitorDsl.Lowpass(inner = IgnitorDsl.Ramp(), freq = IgnitorDsl.Constant(7777.7))

        val nameA = regA.ignitors.registerOrLookup(dsl)
        val nameB = regB.ignitors.registerOrLookup(dsl)

        nameA shouldBe nameB // global uniqueId = same name everywhere
        sentA.size shouldBe 1 // each playback announces to its own backend fork exactly once
        sentB.size shouldBe 1
    }

    // ── the generic really is shared: pipelines and masters behave identically ──

    "each DSL kind announces once and lands as its own Cmd type" {
        val (reg, sent) = newRegistrar()
        val pipeline = PipelineDsl.pedal
        val master = MasterDsl.of()

        reg.pipelines.registerOrLookup(pipeline)
        reg.pipelines.registerOrLookup(pipeline)
        reg.masters.registerOrLookup(master)
        reg.masters.registerOrLookup(master)

        sent.filterIsInstance<KlangCommLink.Cmd.RegisterPipeline>().size shouldBe 1
        sent.filterIsInstance<KlangCommLink.Cmd.RegisterMaster>().size shouldBe 1
        sent.size shouldBe 2
    }

    // ── the shared sweep (was hand-written at every call site) ───────────────

    "announceAll registers every inline DSL kind found in the events" {
        val (reg, sent) = newRegistrar()
        val osc = IgnitorDsl.Sawtooth(freq = IgnitorDsl.Constant(123.45))
        val pipeline = PipelineDsl.pedal
        val master = MasterDsl.of()

        reg.announceAll(
            listOf(
                FakeEvent(sound = SoundValue.Osc(osc)),
                FakeEvent(pipeline = PipelineValue.Dsl(pipeline)),
                FakeEvent(master = MasterValue.Dsl(master)),
            )
        )

        sent.filterIsInstance<KlangCommLink.Cmd.RegisterIgnitor>().single().dsl shouldBe osc
        sent.filterIsInstance<KlangCommLink.Cmd.RegisterPipeline>().single().dsl shouldBe pipeline
        sent.filterIsInstance<KlangCommLink.Cmd.RegisterMaster>().single().dsl shouldBe master
    }

    "announceAll ignores events that carry no inline DSL, and repeats announce nothing" {
        val (reg, sent) = newRegistrar()
        val osc = IgnitorDsl.Square(freq = IgnitorDsl.Constant(99.9))
        val events = listOf(FakeEvent(), FakeEvent(sound = SoundValue.Osc(osc)), FakeEvent())

        reg.announceAll(events)
        reg.announceAll(events) // a cyclic playback sweeps the same window repeatedly

        sent.size shouldBe 1
    }

    "concurrent registerOrLookup never collides on synthetic names" {
        val (reg, sent) = newRegistrar()
        val threads = 8
        val perThread = 200
        val results = Array(threads) { Array<String?>(perThread) { null } }

        val tasks = (0 until threads).map { t ->
            Thread {
                repeat(perThread) { i ->
                    // Mix shared + unique DSLs so the cache sees both
                    // collisions (good — same name) and new entries.
                    val dsl = if (i % 3 == 0) {
                        IgnitorDsl.Sine()
                    } else {
                        IgnitorDsl.Sawtooth(freq = IgnitorDsl.Constant(440.0 + t * 1000 + i))
                    }
                    results[t][i] = reg.ignitors.registerOrLookup(dsl)
                }
            }.also { it.start() }
        }
        tasks.forEach { it.join() }

        results.flatten().forEach { it shouldNotBe null }
        val distinctNames = sent.filterIsInstance<KlangCommLink.Cmd.RegisterIgnitor>().map { it.name }
        distinctNames.toSet().size shouldBe distinctNames.size
        reg.ignitors.size shouldBe distinctNames.size
    }
})
