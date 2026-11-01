/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.KlangAudioRenderer
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.KatalystValue
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.uniqueId
import io.peekandpoke.klang.common.SourceLocationChain

/**
 * An offline render must know every inline DSL the song references, exactly as live playback does,
 * or a recorded WAV would not match what the song sounds like. Twin of
 * `KlangOfflineRendererMasterTest`, for the orbit chain.
 *
 * It exercises the registration sweep an engine driven in process uses,
 * `InlineDslRegistrar.intoRegistries(...).announceAll(events)`, the same sweep a playback runs with the wire as its
 * sink. The offline renderer itself goes over the wire since KLANG-00399 step 4, so its own registration is the live
 * path's, which the root render helpers (`_katalyst_render_helpers`) exercise end to end. The chain itself is silent
 * in this step (the cylinder starts reading declared chains in step 2), so what is asserted is registration.
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
        override val master: KatalystValue? = null,
    ) : KlangPatternEvent {
        override val startCycles = 0.0
        override val durationCycles = 1.0
        override val sourceLocations: SourceLocationChain? = null
        override fun toVoiceData(): VoiceData = VoiceData.empty
    }

    fun sweep(renderer: KlangAudioRenderer, events: List<KlangPatternEvent>) {
        InlineDslRegistrar.intoRegistries(
            ignitors = renderer.ignitorRegistry,
            katalysts = renderer.katalystRegistry,
        ).announceAll(events)
    }

    "a declared chain lands in the in-process engine's katalyst registry, under its synthetic name" {
        val chain = KatalystDsl.of(KatalystStageDsl.Reverb(wet = KatalystKnobDsl.Constant(0.17)))
        val renderer = renderer()

        // Before the sweep the name is unknown; the registry never invents a fallback.
        renderer.katalystRegistry.find(chain.uniqueId()).shouldBeNull()

        sweep(renderer, listOf(Event(katalyst = KatalystValue.Dsl(chain))))

        renderer.katalystRegistry.find(chain.uniqueId()) shouldBe chain
    }

    "the chain lands on the PARENT registry, so each engine fork resolves it through its parent" {
        // An engine driven in process (`intoRegistries`) registers on the parent rather than sending
        // `Cmd.RegisterKatalyst`. A fork that could not see it would leave every declared chain
        // unresolved there. (The offline renderer goes over the wire since KLANG-00399 step 4.)
        val chain = KatalystDsl.of(KatalystStageDsl.Gain(gain = KatalystKnobDsl.Constant(1.8)))
        val renderer = renderer()

        sweep(renderer, listOf(Event(katalyst = KatalystValue.Dsl(chain))))

        renderer.katalystRegistry.fork().find(chain.uniqueId()) shouldBe chain
    }

    "the in-process sweep is the shared one: a master and an orbit chain both land in the ONE registry" {
        // The point of routing every registration through `InlineDslRegistrar` is that adding a DSL
        // kind is one edit. This row fails if the in-process sweep ever grows its own copy and
        // forgets one position: the output chain (`master(...)`) is a Katalyst too since
        // phase 3 step 12 C5, and the master bus resolves it in the same registry the orbits read.
        val chain = KatalystDsl.of(KatalystStageDsl.Delay(wet = KatalystKnobDsl.Constant(0.11)))
        val master = KatalystDsl.of(KatalystStageDsl.Gain(gain = KatalystKnobDsl.Constant(1.9)))
        val renderer = renderer()

        sweep(
            renderer,
            listOf(Event(katalyst = KatalystValue.Dsl(chain)), Event(master = KatalystValue.Dsl(master))),
        )

        renderer.katalystRegistry.find(chain.uniqueId()) shouldBe chain
        renderer.katalystRegistry.find(master.uniqueId()) shouldBe master
    }
})
