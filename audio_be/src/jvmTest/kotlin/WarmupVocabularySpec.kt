/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlin.math.abs

/**
 * The warmup vocabulary must (1) execute EVERY `IgnitorDsl` node kind, so no song's instrument
 * hits a cold kind in a live frame, and (2) actually sound, finitely, through the real engine —
 * a graph that builds but renders silence (or NaN) warms nothing (or poisons the post chain).
 *
 * JVM-only because (1) enumerates the sealed hierarchy by reflection: a kind added to the DSL
 * without a decision here fails this spec, which is the point.
 */
class WarmupVocabularySpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = AudioBackendContext.RENDER_QUANTUM_FRAMES

    /**
     * Kinds the vocabulary deliberately does NOT play, each with the reason. Everything else in
     * the sealed hierarchy must appear in a vocabulary graph.
     */
    val excluded: Map<String, String> = mapOf(
        // (none today — add a kind here only with a reason a reviewer would accept)
    )

    /** Simple class names of every node in a graph, off the data-class `toString` — a JVM guard. */
    fun kindsIn(dsl: IgnitorDsl): Set<String> =
        Regex("""\b([A-Z][A-Za-z]+)(?=\(|,|\)|$)""").findAll(dsl.toString()).map { it.groupValues[1] }.toSet()

    "every IgnitorDsl node kind appears in the vocabulary, or is excluded with a reason" {
        val allKinds = IgnitorDsl::class.sealedSubclasses.mapNotNull { it.simpleName }.toSet()
        (allKinds.size > 70) shouldBe true // the hierarchy really was enumerated

        val present = WarmupVocabulary.sounds.flatMap { (_, dsl) -> kindsIn(dsl) }.toSet()
        val missing = allKinds.filter { it !in present && it !in excluded }

        withClue("node kinds no warmup graph executes: $missing") { missing.shouldBeEmpty() }
        withClue("excluded kinds that are not in the hierarchy (stale exclusion): ${excluded.keys - allKinds}") {
            (excluded.keys - allKinds).shouldBeEmpty()
        }
    }

    "every Eq section kind appears in the vocabulary" {
        val sectionKinds = IgnitorDsl.EqSection::class.sealedSubclasses.mapNotNull { it.simpleName }.toSet()
        (sectionKinds.size >= 6) shouldBe true
        val present = kindsIn(WarmupVocabulary.filters)
        withClue("Eq sections no warmup graph executes") { sectionKinds.filter { it !in present }.shouldBeEmpty() }
    }

    "every vocabulary sound renders audible, FINITE audio through the real engine" {
        val mix = StereoBuffer(blockFrames)
        // One fresh engine per sound: a shared one would let an earlier sound's tail make a silent
        // graph look audible (a mutation that muted a whole graph survived that version).
        for ((name, dsl) in WarmupVocabulary.sounds) {
            val clock = BackendClock(sampleRate)
            val context = AudioBackendContext.create(
                sampleRate = sampleRate, blockFrames = blockFrames,
                commLink = KlangCommLink(capacity = 1024).backend, clock = clock,
            )
            val engine = PlaybackEngine.create(context)
            engine.scheduler.registerIgnitor(name, dsl)
            val start = 0.5 * blockFrames / sampleRate
            engine.scheduler.scheduleVoice(
                ScheduledVoice(
                    playbackId = "warm", startTime = start, gateEndTime = start + 0.1,
                    data = VoiceData.empty.copy(sound = name, freqHz = 220.0, cylinder = 0),
                    playbackStartTime = 0.0,
                )
            )
            var peak = 0.0
            var finite = true
            repeat(40) {
                mix.clear()
                engine.renderInto(mix, clock.cursorFrame)
                for (i in 0 until blockFrames) {
                    val v = mix.left[i]
                    if (v != v || v == Double.POSITIVE_INFINITY || v == Double.NEGATIVE_INFINITY) finite = false // NaN-guard
                    peak = maxOf(peak, abs(v))
                }
                clock.cursorFrame += blockFrames
            }
            withClue("$name: peak $peak, finite $finite") {
                finite shouldBe true
                peak shouldBeGreaterThan 1e-4
                // Bounded too: a graph driving 1e13-class samples through the combs and the post
                // chain is only harmless by three unrelated facts (silenced output, the post-chain
                // reset at disposal, the shelf zeroing a dirty unit). Keep it a signal.
                peak shouldBeLessThan 10.0
            }
            engine.scheduler.droppedVoiceCount("warm") shouldBe 0
        }
    }
})
