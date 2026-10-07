/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_be.cylinders.Cylinder
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createVoice
import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.constants.VOICE_CULL_NEVER
import kotlin.math.abs

/**
 * Who owns an orbit's bus settings (lifecycle step 5, maintainer 2026-10-07): the orbit's bus settings are owned
 * by the newest `Sounding` voice (the latest onset; on the same onset the voice created later); a voice gives the
 * orbit up when its gate closes or it is cut. A releasing or fading voice still routes its audio and keeps the
 * orbit in use, but never owns it, and an orbit without a `Sounding` voice keeps the settings it last applied.
 *
 * The observable: two kinds of voice on orbit 0, a PLAIN one (no bus settings: the body stays off) and a BODY one
 * (`body.material` wood, `body.wet` 1: the body engages). The body is engaged exactly while the body voice's
 * settings are the ones applied. The owner is committed once per block after the voices rendered, as
 * `Cylinders.processAndMix` does; [block] does the same.
 */
class OrbitOwnershipSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val bodyParams = mapOf("body.material" to BodyMaterials.indexOf("wood"), "body.wet" to 1.0)

    /** A constant voice, never culled; [body] gives it the body settings. */
    fun voice(start: Double, gate: Double, end: Double = gate + 4800.0, body: Boolean = false): Voice = createVoice(
        startFrame = start, gateEndFrame = gate, endFrame = end,
        sampleRate = sampleRate, blockFrames = blockFrames, cull = VOICE_CULL_NEVER,
        katalystParams = if (body) bodyParams else null,
    )

    // A fresh context (and with it fresh orbits) per row.
    var ctx = createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)

    beforeTest {
        ctx = createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)
    }

    /** Renders the block at [start] for [voices] in order; returns orbit 0. */
    fun block(start: Double, vararg voices: Voice): Cylinder {
        ctx.blockStart = start

        for (v in voices) {
            v.render(ctx)
        }

        val c = ctx.cylinders.cylinders.first()

        c.commitOwner()

        return c
    }

    fun engaged(c: Cylinder): Boolean = c.body!!.isEngaged

    "the newest sounding voice owns: a newer voice takes the orbit from an older owner at its first block" {
        val older = voice(start = 0.0, gate = 1_000_000.0)
        val newer = voice(start = 512.0, gate = 1_000_000.0, body = true)

        for (b in 0 until 4) {
            withClue("block $b: the older voice owns") { engaged(block(b * 128.0, older, newer)) shouldBe false }
        }

        withClue("block 512: the newer voice's first block") { engaged(block(512.0, older, newer)) shouldBe true }
        withClue("and it keeps the orbit") { engaged(block(640.0, older, newer)) shouldBe true }
    }

    "on the same onset the voice created later owns, whatever the render order" {
        val first = voice(start = 0.0, gate = 1_000_000.0, body = true)
        val second = voice(start = 0.0, gate = 1_000_000.0)

        withClue("the later-created plain voice owns, rendered last") { engaged(block(0.0, first, second)) shouldBe false }
        withClue("and rendered first") { engaged(block(128.0, second, first)) shouldBe false }
    }

    "when the owner's gate closes, the newest remaining sounding voice claims, not the oldest" {
        val oldest = voice(start = 0.0, gate = 1_000_000.0)
        val middle = voice(start = 128.0, gate = 1_000_000.0, body = true)
        val newest = voice(start = 256.0, gate = 512.0)

        block(0.0, oldest, middle, newest)
        block(128.0, oldest, middle, newest)
        withClue("block 256: the newest owns") { engaged(block(256.0, oldest, middle, newest)) shouldBe false }
        withClue("block 384: still") { engaged(block(384.0, oldest, middle, newest)) shouldBe false }
        withClue("block 512: its gate closed, the newest remaining claims") { engaged(block(512.0, oldest, middle, newest)) shouldBe true }
    }

    "a fading voice never owns: a new voice that cuts the owner has its settings applied from its first block" {
        val owner = voice(start = 0.0, gate = 1_000_000.0)

        for (b in 0 until 8) {
            withClue("block $b: owned by the plain voice") { engaged(block(b * 128.0, owner)) shouldBe false }
        }

        val cutter = voice(start = 1024.0, gate = 1_000_000.0, body = true)

        owner.cutOff(1024.0)
        withClue("the cutter's first block") { engaged(block(1024.0, owner, cutter)) shouldBe true }
        withClue("and on, while the victim fades") { engaged(block(1152.0, owner, cutter)) shouldBe true }
    }

    "a voice gives the orbit up when it is cut: an older sounding voice owns from the cut block, with no cutter here" {
        val older = voice(start = 0.0, gate = 1_000_000.0, body = true)
        val newer = voice(start = 512.0, gate = 1_000_000.0)

        for (b in 0 until 8) {
            val expected = b < 4

            withClue("block $b: the newest sounding voice owns") { engaged(block(b * 128.0, older, newer)) shouldBe expected }
        }

        // The cutter sits on another orbit (a cut group reaches the whole playback), so nothing newer offers here:
        // only the cut itself can hand orbit 0 back to the older voice.
        newer.cutOff(1024.0)

        withClue("the cut block: the fading voice no longer owns") { engaged(block(1024.0, older, newer)) shouldBe true }
        withClue("still fading") { newer.state.shouldBeInstanceOf<Voice.State.Fading>() }
        withClue("and on") { engaged(block(1152.0, older, newer)) shouldBe true }
    }

    "a zero-length gate never owns, on a block boundary or inside a block" {
        for (onset in listOf(256.0, 300.0)) {
            val body = voice(start = 0.0, gate = 1_000_000.0, body = true)
            val tap = voice(start = onset, gate = onset)

            block(0.0, body, tap)
            block(128.0, body, tap)
            withClue("onset $onset: the newer zero-gate voice does not take the orbit") { engaged(block(256.0, body, tap)) shouldBe true }
            withClue("onset $onset: nor the block after") { engaged(block(384.0, body, tap)) shouldBe true }
            ctx = createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)
        }
    }

    "an orbit without a sounding voice keeps the settings it last applied, until the next sounding voice claims" {
        val body = voice(start = 0.0, gate = 512.0, end = 512.0 + 48_000.0, body = true)

        for (b in 0 until 4) {
            withClue("block $b: the body voice owns") { engaged(block(b * 128.0, body)) shouldBe true }
        }

        var start = 512.0

        while (start < 4096.0) {
            val c = block(start, body)

            c.tryDeactivate(start)
            withClue("block $start: ownerless, the body stays") { engaged(c) shouldBe true }
            withClue("block $start: the tail keeps the orbit in use") { c.isActive shouldBe true }
            start += blockFrames
        }

        val plain = voice(start = 4096.0, gate = 1_000_000.0)

        withClue("the next sounding voice claims and applies its own settings") { engaged(block(4096.0, body, plain)) shouldBe false }
    }

    "a held realtime voice owns while held and gives the orbit up at its note-off" {
        val body = voice(start = 0.0, gate = 1_000_000.0, body = true)
        val held = voice(start = 128.0, gate = 1_000_000.0)

        block(0.0, body, held)

        for (b in 1 until 5) {
            withClue("block $b: the held voice owns") { engaged(block(b * 128.0, body, held)) shouldBe false }
        }

        held.releaseGate(640.0)
        withClue("the block after the note-off: given up, the body voice claims") { engaged(block(640.0, body, held)) shouldBe true }
    }

    "a voice past its gate still activates a fresh orbit and routes into it, without owning it" {
        val tail = voice(start = 0.0, gate = 0.0, end = 4800.0, body = true)
        val c = block(0.0, tail)

        withClue("releasing from its first block") { tail.state.shouldBeInstanceOf<Voice.State.Releasing>() }
        withClue("the orbit is active") { c.isActive shouldBe true }
        withClue("the audio arrived") { (c.mixBuffer.left.maxOf { abs(it) } > 0.1) shouldBe true }
        withClue("but its settings were never applied") { engaged(c) shouldBe false }
    }
})
