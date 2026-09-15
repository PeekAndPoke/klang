/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.toExciter
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.OPTIMIZER_PARITY
import io.peekandpoke.klang.audio_bridge.SoundValue
import io.peekandpoke.klang.audio_bridge.optimize
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import kotlin.math.abs
import kotlin.random.Random

/**
 * The optimizer on every instrument every builtin song actually plays: each song is compiled,
 * its first cycles queried, every distinct inlined ignitor graph rendered authored and optimized
 * with same-seeded streams and held to `OPTIMIZER_PARITY`. The shapes people wrote, with their
 * shared intermediates, params and rigs, are the corpus the hand-written rows cannot enumerate.
 */
class OptimizerSongParitySpec : StringSpec({

    val blockFrames = 128
    val blocks = 3
    val sampleRate = 48000

    val songs = listOf(
        BuiltInSongs.derSchmetterling, BuiltInSongs.tetris, BuiltInSongs.tetrisRemix, BuiltInSongs.soundOfTheSea,
        BuiltInSongs.sakura, BuiltInSongs.aTruthWorthLyingFor, BuiltInSongs.strangerThings,
        BuiltInSongs.irishLamentTechno, BuiltInSongs.irishLament, BuiltInSongs.finalFantasy7Prelude,
        BuiltInSongs.smallTownBoy, BuiltInSongs.drunkenSailor,
    )

    fun engine() = klangScript {
        registerLibrary(sprudelLib)
        registerBuiltInSongsAsModules()
    }

    fun withinParity(a: Double, b: Double, scale: Double = 0.0): Boolean = when {
        a.isNaN() || b.isNaN() -> a.isNaN() && b.isNaN()
        a.isInfinite() || b.isInfinite() -> a == b
        else -> abs(a - b) <= OPTIMIZER_PARITY * maxOf(abs(a), abs(b), scale, 1e-300)
    }

    /**
     * The block's scale: its loudest finite sample on either side, at most full scale (1.0), what
     * the margin is relative to. The cap keeps the law in a saturated block: one sample at
     * SAFE_MAX must not buy the musical samples next to it a tolerance of 1e3.
     */
    fun scaleOf(bufA: AudioBuffer, bufB: AudioBuffer, from: Int, until: Int): Double {
        var peak = 0.0

        for (i in from until until) {
            val a = abs(bufA[i])
            val b = abs(bufB[i])

            if (a.isFinite() && a > peak) {
                peak = a
            }

            if (b.isFinite() && b > peak) {
                peak = b
            }
        }

        return if (peak > 1.0) 1.0 else peak
    }

    fun ctx(random: Random): IgniteContext = IgniteContext(
        sampleRate = sampleRate, voiceDurationFrames = sampleRate, gateEndFrame = sampleRate, releaseFrames = 0,
        scratchBuffers = ScratchBuffers(blockFrames), random = random,
    )

    fun assertParity(clue: String, authored: IgnitorDsl, oscParams: Map<String, Double>?) {
        val optimized = authored.optimize()
        val rngA = Random(11)
        val rngB = Random(11)
        val a = authored.toExciter(oscParams, random = rngA)
        val b = optimized.toExciter(oscParams, random = rngB)
        val ca = ctx(rngA)
        val cb = ctx(rngB)
        val bufA = AudioBuffer(blockFrames)
        val bufB = AudioBuffer(blockFrames)

        for (f in listOf(110.0, 440.0)) {
            repeat(blocks) { block ->
                val offset = if (block == 0) 37 else 0

                ca.updateOffsetAndLength(offset, blockFrames - offset)
                cb.updateOffsetAndLength(offset, blockFrames - offset)
                a.generate(bufA, f, ca)
                b.generate(bufB, f, cb)

                val scale = scaleOf(bufA, bufB, offset, blockFrames)

                for (i in offset until blockFrames) {
                    withClue("$clue freq $f block $block sample $i: ${bufA[i]} vs ${bufB[i]} (scale $scale)") {
                        withinParity(bufA[i], bufB[i], scale) shouldBe true
                    }
                }

                ca.voiceElapsedFrames += blockFrames
                cb.voiceElapsedFrames += blockFrames
            }
        }
    }

    "every inlined instrument of every builtin song renders within the margin under the pass" {
        var graphs = 0

        for (song in songs) {
            val ungated = Regex("""\.mute\("<[^"]*>"\)""").replace(song.code, "")
            val pattern = SprudelPattern.compile(engine(), ungated) ?: continue
            val seen = mutableListOf<IgnitorDsl>()

            for (event in pattern.queryArc(0.0, 8.0)) {
                val sound = event.data.sound as? SoundValue.Osc ?: continue

                if (seen.any { it == sound.osc }) {
                    continue
                }

                seen.add(sound.osc)
                graphs++
                assertParity("${song.title}, orbit ${event.data.cylinder}:", sound.osc, event.data.oscParams)
            }
        }

        withClue("instrument graphs found across the songs") { (graphs > 10) shouldBe true }
    }
})
