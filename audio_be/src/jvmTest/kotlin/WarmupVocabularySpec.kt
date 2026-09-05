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
import io.peekandpoke.klang.audio_bridge.optimize
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlin.math.abs
import kotlin.reflect.KClass

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
        "OptimizerHint" to "dissolves in the optimizer before anything is built — there is no runtime node to warm",
        "Variants" to "dissolves at the registry boundary (children[soundIndex]) — no runtime node; the chosen child is warmed",
    )

    /**
     * Every node in a graph, walked by reflection over the data classes' properties (an
     * `IgnitorDsl`-typed property or a list of them is a child). Classes, not names: a simple-name
     * regex over `toString()` could not tell `EqSection.Lowpass` from `IgnitorDsl.Lowpass`, and
     * certified the four standalone filters the optimizer had fused away (review round 3).
     */
    fun nodesOf(dsl: IgnitorDsl): Set<KClass<*>> {
        val out = mutableSetOf<KClass<*>>()
        fun walk(node: Any?) {
            when (node) {
                is IgnitorDsl -> {
                    out.add(node::class) // a tree: every node is visited once, whatever its class
                    // Java reflection (kotlin-reflect's `memberProperties` is not on the test
                    // classpath): every zero-arg getter that yields a node or a list of nodes.
                    for (m in node.javaClass.methods) {
                        if (m.parameterCount != 0 || !m.name.startsWith("get")) continue
                        val v = runCatching { m.invoke(node) }.getOrNull()
                        if (v is IgnitorDsl || v is List<*>) walk(v)
                    }
                }
                is IgnitorDsl.EqSection -> out.add(node::class)
                is List<*> -> node.forEach { walk(it) }
                else -> {}
            }
        }
        walk(dsl)
        return out
    }

    "every IgnitorDsl node kind is BUILT by the vocabulary after the optimizer, or is excluded with a reason" {
        // The registry optimizes every graph before building it (a plain lowpass with analog 0 is
        // fused into an Eq section), so what the warmup executes is the OPTIMIZED graph — that is
        // what is certified here (review round 3: the authored graph claimed four filter nodes the
        // optimizer had fused away; the analog = 1.0 chain now keeps them standalone).
        val allKinds = IgnitorDsl::class.sealedSubclasses.toSet()
        (allKinds.size > 70) shouldBe true // the hierarchy really was enumerated

        val present = WarmupVocabulary.sounds.flatMap { (_, dsl) -> nodesOf(dsl.optimize()) }.toSet()
        val missing = allKinds.filter { it !in present && it.simpleName !in excluded }.map { it.simpleName }

        withClue("node kinds no warmup graph executes: $missing") { missing.shouldBeEmpty() }
        val stale = excluded.keys - allKinds.mapNotNull { it.simpleName }.toSet()
        withClue("excluded kinds that are not in the hierarchy (stale exclusion): $stale") { stale.shouldBeEmpty() }
    }

    "every Eq section kind is built by the optimized filters graph" {
        val sectionKinds = IgnitorDsl.EqSection::class.sealedSubclasses.toSet()
        (sectionKinds.size >= 6) shouldBe true
        val present = nodesOf(WarmupVocabulary.filters.optimize())
        val missing = (sectionKinds - present).map { it.simpleName }
        withClue("Eq sections no warmup graph executes: $missing") { missing.shouldBeEmpty() }
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
                    val l = mix.left[i]
                    val r = mix.right[i]
                    if (l != l || r != r || abs(l) == Double.POSITIVE_INFINITY || abs(r) == Double.POSITIVE_INFINITY) finite = false // NaN-guard
                    peak = maxOf(peak, abs(l), abs(r))
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
