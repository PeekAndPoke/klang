/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_engine.KlangOfflineRenderer
import io.peekandpoke.klang.script.stdlib.KlangScriptIgnitor
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.sound
import kotlin.math.abs

/**
 * An empty `Ignitor.variants()` is SILENCE on both doors (`/code-style` §21: user input is coerced, never asserted).
 * Until 2026-10-07 the engine's Variants pick ran `require(children.isNotEmpty())` at note-on, on the audio thread,
 * where nothing catches it (`docs/tasks-archive/2026-10/20261009-engine-tidy-up.md`, "First, a bug").
 *
 * Each row renders a song through the real voice path (`KlangOfflineRenderer`: the inline-DSL registration, the
 * scheduler, `VoiceFactory`, the build), so a throw anywhere on that path fails the row. Here because sprudel is the
 * module that has both doors and the renderer, so the spec runs on the JVM and on JS.
 */
class EmptyVariantsDoorRenderSpec : StringSpec({

    class Render(val frames: Int, val peak: Double)

    suspend fun render(pattern: SprudelPattern): Render {
        var frames = 0
        var peak = 0.0

        KlangOfflineRenderer(sampleRate = 48_000).render(
            pattern = pattern,
            cycles = 1,
            cyclesPerSecond = 1.0,
            tailSec = 0.25,
            onBlock = { out, n ->
                frames += n

                for (i in 0 until n) {
                    peak = maxOf(peak, abs(out.left[i]), abs(out.right[i]))
                }
            },
        )

        return Render(frames = frames, peak = peak)
    }

    fun script(code: String): SprudelPattern = SprudelPattern.compile(code) ?: error("the song did not compile: $code")

    "the harness hears sound: a one-child variants() is not silent, on either door" {
        withClue("KlangScript door") {
            render(script("""note("c3 e3").sound(Ignitor.variants(Ignitor.sine()))""")).peak shouldBeGreaterThan 0.01
        }
        withClue("Kotlin door") {
            render(note("c3 e3").sound(KlangScriptIgnitor.variants(KlangScriptIgnitor.sine()))).peak shouldBeGreaterThan 0.01
        }
    }

    "KlangScript door: an empty Ignitor.variants() renders silence and does not throw" {
        val out = render(script("""note("c3 e3").sound(Ignitor.variants())"""))

        out.frames shouldBeGreaterThan 0
        out.peak shouldBe 0.0
    }

    "Kotlin door: an empty KlangScriptIgnitor.variants() renders silence and does not throw" {
        val out = render(note("c3 e3").sound(KlangScriptIgnitor.variants()))

        out.frames shouldBeGreaterThan 0
        out.peak shouldBe 0.0
    }
})
