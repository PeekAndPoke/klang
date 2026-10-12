/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_engine.KlangOfflineRenderer
import io.peekandpoke.klang.sprudel.SprudelPattern
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.math.abs

/**
 * SCAFFOLDING, removed in step 7 of `docs/tasks/in-progress/katalyst-knob-expressions.md`: the render fixture of step
 * 3, the
 * migration of every Katalyst stage knob from `IgnitorDsl` to `KatalystKnobDsl`.
 *
 * Each row renders a small synth-only song offline and compares a SHA-256 of the raw output doubles (the engine's
 * clipped stereo, before any 16-bit conversion) with the hash the UNCHANGED tree rendered. The migration is a type
 * change and must not move one sample, so the hashes are pinned from the tree before it and must hold after it.
 * The builtin songs that use `master(Katalyst(...))` are covered by the corpus check; these rows cover what the
 * corpus does not reach: the classic orbit chain driven through `katp` (handles and names), the classic chain the
 * orbit is born with driven by the sprudel bus doors, an authored chain with a `Kat.param` on every knob kind (the
 * EQ sections included), EVERY one of them moved by `katp` through two values its default is not (so a door that
 * froze a slot into its default would move the hash), and a master chain whose slots stay at their defaults.
 *
 * Synth sources only: this renderer has no sample bank.
 */
class KatalystKnobMigrationFixtureSpec : StringSpec({

    suspend fun renderHash(code: String): Pair<String, Double> {
        val pattern = SprudelPattern.compile(code) ?: error("the song did not compile: $code")
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = ByteBuffer.allocate(16)
        var peak = 0.0

        KlangOfflineRenderer(sampleRate = 48_000).render(
            pattern = pattern,
            cycles = 8,
            cyclesPerSecond = 0.5,
            tailSec = 1.0,
            onBlock = { out, frames ->
                for (i in 0 until frames) {
                    bytes.clear()
                    bytes.putDouble(out.left[i])
                    bytes.putDouble(out.right[i])
                    digest.update(bytes.array())

                    peak = maxOf(peak, abs(out.left[i]), abs(out.right[i]))
                }
            },
        )

        return HexFormat.of().formatHex(digest.digest()).take(16) to peak
    }

    val classicKatp = """
        stack(
          note("c2*4").s("triangle").orbit(0),
          note("c3 e3 g3 b3").s("supersaw").orbit(1)
            .katalyst(Katalyst(k => k.classic()))
            .katp(Kat.slot.reverb.wet, "0.3 0.5").katp("reverb.size", 6).katp(Kat.slot.reverb.lowpass, 3000)
            .katp(Kat.slot.delay.wet, 0.3).katp("delay.time", 0.18).katp(Kat.slot.delay.feedback, 0.45).katp("delay.cap", 0.8)
            .katp(Kat.slot.phaser.wet, 0.5).katp("phaser.rate", 1.5).katp(Kat.slot.phaser.center, 1200)
            .katp("phaser.sweep", 800).katp(Kat.slot.phaser.floor, 0.8)
            .katp(Kat.slot.body.material, 2).katp("body.wet", 0.4).katp(Kat.slot.body.floor, 0.3)
            .katp(Kat.slot.compressor.threshold, -18).katp("compressor.ratio", 3).katp(Kat.slot.compressor.knee, 4)
            .katp("compressor.attack", 0.01).katp(Kat.slot.compressor.release, 0.2)
            .katp(Kat.slot.gain.gain, "<0.8 1.2>")
            .katp(Kat.slot.duck.orbit, 0).katp("duck.depth", 0.6).katp(Kat.slot.duck.attack, 0.05),
          note("e4 g4").s("sawtooth").orbit(2)
            .katalyst(Katalyst(k => k.classic()))
            .katp(Kat.slot.vowel.vowel, 3).katp("vowel.wet", 0.6).katp(Kat.slot.vowel.floor, 0.25)
        )
    """.trimIndent()

    val classicDoors = """
        stack(
          note("c3 e3 g3 b3").s("supersaw").orbit(1)
            .reverb(wet = 0.3, size = 5).delay(wet = 0.25, time = 0.2, feedback = 0.4)
            .phaser(wet = 0.5, rate = 2).body(material = "wood").compressor(threshold = -20),
          note("e4 g4").s("sawtooth").orbit(2).vowel(vowel = "o")
        )
    """.trimIndent()

    val authored = """
        note("c3 e3 g3 b3").s("supersaw").orbit(2).katalyst(Katalyst(k => k
          .eq(e => e.band(freq = Kat.param("eqf", 900), q = 0.8, db = Kat.param("eqdb", 3)).tap(2500, 0.7, Kat.param("tapg", 0.5)))
          .distort(Kat.param("drive", 0.2), "tube")
          .delay(0.3, Kat.param("dt", 0.2), 0.4, d => d.cap(Kat.param("dcap", 0.9)))
          .reverb(Kat.param("rw", 0.3), Kat.param("rs", 4), Kat.param("rl", 5000))
          .phaser(0.4, Kat.param("pr", 1), 1000, 600, p => p.floor(Kat.param("pf", 0.7)))
          .body(0.3, Kat.param("mat", 2), b => b.floor(Kat.param("bf", 0.4)))
          .vowel(Kat.param("vw", 0.3), "a", v => v.floor(0.2))
          .compressor(threshold = Kat.param("ct", -18), ratio = 3)
          .times(Kat.param("trim", 0.9)).plus(0).pow(1).max(Kat.param("cap", 0.9)).min(-0.9)
          .gain(Kat.param("fader", 1.1))
        ))
          .katp("eqf", "<700 1400>").katp("eqdb", "<-4 6>").katp("tapg", "<0.2 0.8>").katp("drive", "0.1 0.3")
          .katp("dt", "<0.15 0.3>").katp("dcap", "<0.6 0.8>").katp("rw", "<0.2 0.45>").katp("rs", "<6 3>")
          .katp("rl", "<3000 8000>").katp("pr", "<0.5 2>").katp("pf", "<0.5 0.85>").katp("mat", "<3 1>")
          .katp("bf", "<0.25 0.6>").katp("vw", "<0.2 0.5>").katp("ct", "<-24 -12>").katp("trim", "<0.7 1.1>")
          .katp("cap", "<0.7 0.95>").katp("fader", "<0.9 1.2>")
    """.trimIndent()

    val masterDefaults = """
        stack(
          note("c3 e3 g3 b3").s("supersaw"),
          master(Katalyst(k => k.reverb(0.05, Kat.param("msize", 7)).gain(Kat.param("mgain", 1.3)).limiter()))
        )
    """.trimIndent()

    // The hashes the unchanged tree rendered (eb39b385), twice each, before the migration began; the authored row was
    // rewritten in step 3's review round 1 and pinned the same way, in a scratch worktree at eb39b385. Step 5c re-pinned
    // the authored row on purpose (Q8 (a), the maintainer's): its `katp("vw", ...)` moves the vowel's wet alone, which
    // now glides in place instead of installing a fresh bank (was `6a69ecdeb84e8574`; with the mix back in the bank's
    // identity compare the row renders the old hash, so the whole move is the in-place blend).
    val pinned = listOf(
        Triple("classic chain through katp", classicKatp, "afce19df06d968f4"),
        Triple("classic chain through the bus doors", classicDoors, "f64d42e5c1e288b1"),
        Triple("authored chain, a Kat.param on every knob kind, every one moved", authored, "2949f0d641d984ec"),
        Triple("master chain, slots at their defaults", masterDefaults, "de98222378e41b4d"),
    )

    for ((title, code, hash) in pinned) {
        "render identical: $title" {
            val (actual, peak) = renderHash(code)

            withClue("the song made sound, peak $peak") { (peak > 0.05) shouldBe true }
            withClue("hash of $title") { actual shouldBe hash }
        }
    }
})
