/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.studio

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.apps.common.BuiltInSongs
import io.peekandpoke.klang.apps.common.registerBuiltInSongsAsModules
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.katalyst.KatalystChainBuilder
import io.peekandpoke.klang.audio_be.katalyst.KatalystContext
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystValue
import io.peekandpoke.klang.audio_bridge.optimize
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import kotlin.math.floor
import kotlin.random.Random

/**
 * The Katalyst chain's pass (KLANG-00468) on every chain a built-in song declares: each song is compiled, its first
 * cycles queried, and every distinct chain its events carry (the master chains, with their `bands`, and a cylinder's
 * `katalyst(...)`: Der Schmetterling's two guitar amps since KLANG-00469) is rendered as written and as the pass
 * leaves it, over the same stereo
 * input, and must give the same samples bit for bit. The twin of `OptimizerSongParitySpec`, which holds the voice's
 * pass to its margin over the songs' instruments; the chain's pass promises the same bits, so there is no margin here.
 * Only cycles 0 to 8 are queried, so a chain a song first declares later than that is not rendered.
 *
 * Kind: for the future: it guards an engine property (the pass keeps every sample) over real material, so it goes red
 * only when a song no longer compiles, no song declares a chain at all, a song's chain renders silence, or the pass
 * breaks; never because a song was edited.
 */
class KatalystOptimizerSongParitySpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 100
    val frames = blocks * blockFrames

    fun engine() = klangScript {
        registerLibrary(sprudelLib)
        registerBuiltInSongsAsModules()
    }

    /** A saw plus noise, a pitch and a seed per channel, so left and right differ. */
    fun input(freq: Double, seed: Int): DoubleArray {
        val random = Random(seed)

        return DoubleArray(frames) {
            val phase = it * freq / sampleRate

            0.3 * (2.0 * (phase - floor(phase)) - 1.0) + 0.2 * (2.0 * random.nextDouble() - 1.0)
        }
    }

    val left = input(freq = 110.0, seed = 1)
    val right = input(freq = 173.3, seed = 2)

    /** [dsl] built as the engine builds it, run over the input with no owner (the output's configuration). */
    fun render(dsl: KatalystDsl): Pair<DoubleArray, DoubleArray> {
        val chain = KatalystChainBuilder.build(
            dsl = dsl,
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        )
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))
        val outLeft = DoubleArray(frames)
        val outRight = DoubleArray(frames)
        var at = 0

        while (at < frames) {
            left.copyInto(destination = ctx.mixBuffer.left, destinationOffset = 0, startIndex = at, endIndex = at + blockFrames)
            right.copyInto(destination = ctx.mixBuffer.right, destinationOffset = 0, startIndex = at, endIndex = at + blockFrames)
            chain.applyParams(null)
            chain.process(ctx)
            ctx.mixBuffer.left.copyInto(destination = outLeft, destinationOffset = at, startIndex = 0, endIndex = blockFrames)
            ctx.mixBuffer.right.copyInto(destination = outRight, destinationOffset = at, startIndex = 0, endIndex = blockFrames)
            at += blockFrames
        }

        return outLeft to outRight
    }

    /** The same double, bit for bit: a signed zero told apart, NaN for NaN. */
    fun sameBits(a: Double, b: Double): Boolean =
        if (a.isNaN() || b.isNaN()) a.isNaN() && b.isNaN() else a == b && 1.0 / a == 1.0 / b

    fun firstDifference(a: DoubleArray, b: DoubleArray): Int = a.indices.firstOrNull { !sameBits(a[it], b[it]) } ?: -1

    "every chain a built-in song declares renders the same bits as written and after the pass" {
        var chains = 0

        for (song in BuiltInSongs.songs) {
            val ungated = Regex("""\.mute\("<[^"]*>"\)""").replace(song.code, "")
            val pattern = withClue("${song.title} compiles to a pattern") {
                SprudelPattern.compile(engine(), ungated).shouldNotBeNull()
            }
            val seen = mutableListOf<KatalystDsl>()

            for (event in pattern.queryArc(0.0, 8.0)) {
                for (value in listOf(event.data.master, event.data.katalyst)) {
                    val written = (value as? KatalystValue.Dsl)?.katalyst ?: continue

                    if (seen.any { it == written }) {
                        continue
                    }

                    seen.add(written)
                    chains++

                    val (writtenLeft, writtenRight) = render(written)
                    val (optimizedLeft, optimizedRight) = render(written.optimize())

                    withClue("${song.title}, chain ${seen.size}: the output is not silent") {
                        (writtenLeft.any { it != 0.0 } && writtenRight.any { it != 0.0 }) shouldBe true
                    }

                    withClue("${song.title}, chain ${seen.size}: the first differing frame, left") {
                        firstDifference(writtenLeft, optimizedLeft) shouldBe -1
                    }

                    withClue("${song.title}, chain ${seen.size}: the first differing frame, right") {
                        firstDifference(writtenRight, optimizedRight) shouldBe -1
                    }
                }
            }
        }

        // A floor against a vacuous pass only: how many chains the songs declare is theirs to change.
        withClue("chains found across the songs") { (chains > 0) shouldBe true }
    }
})
