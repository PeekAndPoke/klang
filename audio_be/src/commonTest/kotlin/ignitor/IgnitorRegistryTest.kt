/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.VoiceData

class IgnitorRegistryTest : StringSpec({

    "register and get by name" {
        val registry = IgnitorRegistry()
        val sine = IgnitorDsl.Sine()
        registry.register("sine", sine)
        registry.get("sine") shouldBe sine
    }

    "names are case-insensitive" {
        val registry = IgnitorRegistry()
        registry.register("Sine", IgnitorDsl.Sine())
        registry.get("sine") shouldNotBe null
        registry.get("SINE") shouldNotBe null
        registry.contains("sInE") shouldBe true
    }

    "get returns null for unknown name" {
        val registry = IgnitorRegistry()
        registry.get("unknown") shouldBe null
    }

    "contains checks presence" {
        val registry = IgnitorRegistry()
        registry.register("sine", IgnitorDsl.Sine())
        registry.contains("sine") shouldBe true
        registry.contains("unknown") shouldBe false
    }

    "names returns all registered names" {
        val registry = IgnitorRegistry()
        registry.register("sine", IgnitorDsl.Sine())
        registry.register("saw", IgnitorDsl.Sawtooth())
        registry.names() shouldBe setOf("sine", "saw")
    }

    "fork creates independent child with inherited defs" {
        val parent = IgnitorRegistry()
        parent.register("sine", IgnitorDsl.Sine())

        val child = parent.fork()

        // Child inherits parent's defs
        child.get("sine") shouldBe IgnitorDsl.Sine()

        // Child can add new defs without affecting parent
        child.register("custom", IgnitorDsl.Square())
        child.contains("custom") shouldBe true
        parent.contains("custom") shouldBe false

        // Child can override without affecting parent
        child.register("sine", IgnitorDsl.Sine(freq = IgnitorDsl.Param("freq", 440.0)))
        child.get("sine") shouldBe IgnitorDsl.Sine(freq = IgnitorDsl.Param("freq", 440.0))
        parent.get("sine") shouldBe IgnitorDsl.Sine()
    }

    "registerDefaults registers all expected names" {
        val registry = IgnitorRegistry()
        registry.registerDefaults()

        // Basic waveforms
        registry.contains("sine") shouldBe true
        registry.contains("sin") shouldBe true
        registry.contains("sawtooth") shouldBe true
        registry.contains("saw") shouldBe true
        registry.contains("square") shouldBe true
        registry.contains("sqr") shouldBe true
        registry.contains("triangle") shouldBe true
        registry.contains("tri") shouldBe true

        // Noise
        registry.contains("whitenoise") shouldBe true
        registry.contains("white") shouldBe true
        registry.contains("brownnoise") shouldBe true
        registry.contains("brown") shouldBe true
        registry.contains("pinknoise") shouldBe true
        registry.contains("pink") shouldBe true
        registry.contains("dust") shouldBe true
        registry.contains("crackle") shouldBe true

        // Other
        registry.contains("supersaw") shouldBe true
        registry.contains("zawtooth") shouldBe true
        registry.contains("zaw") shouldBe true
        registry.contains("pulze") shouldBe true
        registry.contains("impulse") shouldBe true
        registry.contains("silence") shouldBe true

        // Compositions
        registry.contains("sgpad") shouldBe true
        registry.contains("sgbell") shouldBe true
        registry.contains("sgbuzz") shouldBe true
    }

    "createExciter returns DSL-based signal for registered name" {
        val registry = IgnitorRegistry()
        registry.register("sine", IgnitorDsl.Sine())

        val data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0)
        val signal = registry.createExciter("sine", data, 440.0)

        signal shouldNotBe null

        // Generate a block and verify non-zero output
        val blockFrames = 128
        val ctx = IgniteContext(
            sampleRate = 44100,
            voiceDurationFrames = 44100,
            gateEndFrame = 44100,
            releaseFrames = 4410,
            voiceEndFrame = 48510,
            scratchBuffers = ScratchBuffers(blockFrames),
        ).apply { offset = 0; length = blockFrames; voiceElapsedFrames = 0 }

        val buffer = AudioBuffer(blockFrames)
        signal!!.generate(buffer, 440.0, ctx)
        buffer.any { it != 0.0 } shouldBe true
    }

    "createExciter returns null for unknown name" {
        val registry = IgnitorRegistry()
        val signal = registry.createExciter("xyznotreal", VoiceData.empty, 440.0)
        signal shouldBe null
    }

    "createExciter produces independent instances per call" {
        val registry = IgnitorRegistry()
        registry.register("test", IgnitorDsl.Sine(freq = IgnitorDsl.Param("freq", 440.0)))

        val data = VoiceData.empty.copy(sound = "test", freqHz = 440.0)

        val sig1 = registry.createExciter("test", data, 440.0)
        val sig2 = registry.createExciter("test", data, 440.0)

        sig1 shouldNotBe null
        sig2 shouldNotBe null
        // Must be different instances (independent mutable state per voice)
        (sig1 !== sig2) shouldBe true
    }

    "registerDefaults compositions produce non-zero output" {
        val registry = IgnitorRegistry()
        registry.registerDefaults()

        val blockFrames = 128
        val ctx = IgniteContext(
            sampleRate = 44100,
            voiceDurationFrames = 44100,
            gateEndFrame = 44100,
            releaseFrames = 4410,
            voiceEndFrame = 48510,
            scratchBuffers = ScratchBuffers(blockFrames),
        ).apply {
            offset = 0
            length = blockFrames
            voiceElapsedFrames = 0
        }

        for (name in listOf("sgpad", "sgbell", "sgbuzz")) {
            val dsl = registry.get(name)!!
            val sig = dsl.toExciter()
            val buffer = AudioBuffer(blockFrames)
            sig.generate(buffer, 440.0, ctx)
            buffer.any { it != 0.0 } shouldBe true
        }
    }

    "createExciter dispatches Variants by VoiceData.soundIndex" {
        val registry = IgnitorRegistry()
        val variants = IgnitorDsl.Variants(
            listOf(
                IgnitorDsl.Sine(),
                IgnitorDsl.Sawtooth(),
            )
        )
        registry.register("v", variants)

        val blockFrames = 128
        fun ctx() = IgniteContext(
            sampleRate = 44100,
            voiceDurationFrames = 44100,
            gateEndFrame = 44100,
            releaseFrames = 4410,
            voiceEndFrame = 48510,
            scratchBuffers = ScratchBuffers(blockFrames),
        ).apply { offset = 0; length = blockFrames; voiceElapsedFrames = 0 }

        fun render(soundIndex: Int?): AudioBuffer {
            val data = VoiceData.empty.copy(sound = "v", freqHz = 440.0, soundIndex = soundIndex)
            val sig = registry.createExciter("v", data, 440.0)!!
            val buffer = AudioBuffer(blockFrames)
            sig.generate(buffer, 440.0, ctx())
            return buffer
        }

        fun reference(dsl: IgnitorDsl): AudioBuffer {
            val sig = dsl.toExciter()
            val buffer = AudioBuffer(blockFrames)
            sig.generate(buffer, 440.0, ctx())
            return buffer
        }

        val nullIndex = render(null)        // ?: 0 → Sine
        val zero = render(0)                // Sine
        val one = render(1)                 // Sawtooth
        val wrapped = render(2)             // 2 % 2 = 0 → Sine
        val negative = render(-1)           // (-1).mod(2) = 1 → Sawtooth

        val sineRef = reference(IgnitorDsl.Sine())
        val sawRef = reference(IgnitorDsl.Sawtooth())

        for (i in sineRef.indices) {
            nullIndex[i] shouldBe sineRef[i]
            zero[i] shouldBe sineRef[i]
            wrapped[i] shouldBe sineRef[i]
            one[i] shouldBe sawRef[i]
            negative[i] shouldBe sawRef[i]
        }
    }
})
