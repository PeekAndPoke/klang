/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.KlangAudioRenderer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.KatalystValue
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl
import io.peekandpoke.klang.audio_bridge.MasterValue
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.uniqueId
import io.peekandpoke.klang.common.SourceLocationChain

/**
 * An offline render must know every inline DSL the song references, exactly as live playback does,
 * or a recorded WAV would not match what the song sounds like. Twin of
 * `KlangOfflineRendererMasterTest`, for the orbit chain.
 *
 * It exercises the renderer's actual registration step rather than a copy of it: since 2026-09-17
 * that step is `InlineDslRegistrar.intoRegistries(...).announceAll(events)`, the same sweep the
 * live playback runs with the wire as its sink. The chain itself is silent in this step (the
 * cylinder starts reading declared chains in step 2), so what is asserted is registration, which
 * is the whole of what the offline path owes it today.
 */
class KlangOfflineRendererKatalystTest : StringSpec({

    fun renderer(): KlangAudioRenderer = KlangAudioRenderer.create(
        sampleRate = 44100,
        blockFrames = 128,
        commLink = KlangCommLink().backend,
        performanceTimeMs = { 0.0 },
        phasePoolSeed = 1,
    )

    /** An event carrying only the references the sweep reads. */
    class Event(
        override val katalyst: KatalystValue? = null,
        override val master: MasterValue? = null,
    ) : KlangPatternEvent {
        override val startCycles = 0.0
        override val durationCycles = 1.0
        override val sourceLocations: SourceLocationChain? = null
        override fun toVoiceData(): VoiceData = VoiceData.empty
    }

    fun sweep(renderer: KlangAudioRenderer, events: List<KlangPatternEvent>) {
        InlineDslRegistrar.intoRegistries(
            ignitors = renderer.ignitorRegistry,
            pipelines = renderer.pipelineRegistry,
            masters = renderer.masterRegistry,
            katalysts = renderer.katalystRegistry,
        ).announceAll(events)
    }

    "a declared chain lands in the offline renderer's katalyst registry, under its synthetic name" {
        val chain = KatalystDsl.of(KatalystStageDsl.Reverb(wet = IgnitorDsl.Constant(0.17)))
        val renderer = renderer()

        // Before the sweep the name is unknown; the registry never invents a fallback.
        renderer.katalystRegistry.find(chain.uniqueId()).shouldBeNull()

        sweep(renderer, listOf(Event(katalyst = KatalystValue.Dsl(chain))))

        renderer.katalystRegistry.find(chain.uniqueId()) shouldBe chain
    }

    "the chain lands on the PARENT registry, so each engine fork resolves it through its parent" {
        // The offline path registers on the renderer's parent rather than sending
        // `Cmd.RegisterKatalyst`, which is the one way it differs from live. A fork that could not
        // see it would leave every declared chain unresolved in an offline render.
        val chain = KatalystDsl.of(KatalystStageDsl.Gain(gain = IgnitorDsl.Constant(1.8)))
        val renderer = renderer()

        sweep(renderer, listOf(Event(katalyst = KatalystValue.Dsl(chain))))

        renderer.katalystRegistry.fork().find(chain.uniqueId()) shouldBe chain
    }

    "the offline sweep is the shared one: a master and a chain on the same events both land" {
        // The point of routing the renderer through `InlineDslRegistrar` is that adding a DSL kind
        // is one edit. This is the row that fails if the offline path ever grows its own copy again
        // and forgets one kind.
        val chain = KatalystDsl.of(KatalystStageDsl.Delay(wet = IgnitorDsl.Constant(0.11)))
        val master = MasterDsl.of(MasterStageDsl.Gain(gain = 1.9))
        val renderer = renderer()

        sweep(
            renderer,
            listOf(Event(katalyst = KatalystValue.Dsl(chain)), Event(master = MasterValue.Dsl(master))),
        )

        renderer.katalystRegistry.find(chain.uniqueId()) shouldBe chain
        renderer.masterRegistry.find(master.uniqueId()) shouldBe master
    }
})
