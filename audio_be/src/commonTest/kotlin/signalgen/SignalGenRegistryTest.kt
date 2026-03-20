package io.peekandpoke.klang.audio_be.signalgen

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.osci.oscillators
import io.peekandpoke.klang.audio_bridge.SignalGenDsl
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.random.Random

class SignalGenRegistryTest : StringSpec({

    "register and get by name" {
        val registry = SignalGenRegistry()
        val sine = SignalGenDsl.Sine()
        registry.register("sine", sine)
        registry.get("sine") shouldBe sine
    }

    "names are case-insensitive" {
        val registry = SignalGenRegistry()
        registry.register("Sine", SignalGenDsl.Sine())
        registry.get("sine") shouldNotBe null
        registry.get("SINE") shouldNotBe null
        registry.contains("sInE") shouldBe true
    }

    "get returns null for unknown name" {
        val registry = SignalGenRegistry()
        registry.get("unknown") shouldBe null
    }

    "contains checks presence" {
        val registry = SignalGenRegistry()
        registry.register("sine", SignalGenDsl.Sine())
        registry.contains("sine") shouldBe true
        registry.contains("unknown") shouldBe false
    }

    "names returns all registered names" {
        val registry = SignalGenRegistry()
        registry.register("sine", SignalGenDsl.Sine())
        registry.register("saw", SignalGenDsl.Sawtooth())
        registry.names() shouldBe setOf("sine", "saw")
    }

    "fork creates independent child with inherited defs" {
        val parent = SignalGenRegistry()
        parent.register("sine", SignalGenDsl.Sine())

        val child = parent.fork()

        // Child inherits parent's defs
        child.get("sine") shouldBe SignalGenDsl.Sine()

        // Child can add new defs without affecting parent
        child.register("custom", SignalGenDsl.Square())
        child.contains("custom") shouldBe true
        parent.contains("custom") shouldBe false

        // Child can override without affecting parent
        child.register("sine", SignalGenDsl.Sine(0.5))
        child.get("sine") shouldBe SignalGenDsl.Sine(0.5)
        parent.get("sine") shouldBe SignalGenDsl.Sine()
    }

    "registerDefaults registers all expected names" {
        val registry = SignalGenRegistry()
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

        // Compositions
        registry.contains("sgpad") shouldBe true
        registry.contains("sgbell") shouldBe true
        registry.contains("sgbuzz") shouldBe true
    }

    "contains checks legacy fallback" {
        val registry = SignalGenRegistry(
            legacyOscillators = oscillators(44100) { rng(Random(42)) },
        )

        // Legacy oscillator names should be found
        registry.contains("supersaw") shouldBe true
        registry.contains("dust") shouldBe true

        // Unknown names should not
        registry.contains("xyznotreal") shouldBe false
    }

    "createSignalGen returns DSL-based signal for registered name" {
        val registry = SignalGenRegistry()
        registry.register("sine", SignalGenDsl.Sine())

        val data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0)
        val signal = registry.createSignalGen("sine", data, 440.0)

        signal shouldNotBe null

        // Generate a block and verify non-zero output
        val blockFrames = 128
        val ctx = SignalContext(
            sampleRate = 44100,
            voiceDurationFrames = 44100,
            gateEndFrame = 44100,
            releaseFrames = 4410,
            voiceEndFrame = 48510,
            scratchBuffers = ScratchBuffers(blockFrames),
        ).apply { offset = 0; length = blockFrames; voiceElapsedFrames = 0 }

        val buffer = FloatArray(blockFrames)
        signal!!.generate(buffer, 440.0, ctx)
        buffer.any { it != 0.0f } shouldBe true
    }

    "createSignalGen falls back to legacy oscillators" {
        val registry = SignalGenRegistry(
            legacyOscillators = oscillators(44100) { rng(Random(42)) },
        )

        val data = VoiceData.empty.copy(sound = "supersaw", freqHz = 440.0, voices = 5.0)
        val signal = registry.createSignalGen("supersaw", data, 440.0)

        signal shouldNotBe null

        val blockFrames = 128
        val ctx = SignalContext(
            sampleRate = 44100,
            voiceDurationFrames = 44100,
            gateEndFrame = 44100,
            releaseFrames = 4410,
            voiceEndFrame = 48510,
            scratchBuffers = ScratchBuffers(blockFrames),
        ).apply { offset = 0; length = blockFrames; voiceElapsedFrames = 0 }

        val buffer = FloatArray(blockFrames)
        signal!!.generate(buffer, 440.0, ctx)
        buffer.any { it != 0.0f } shouldBe true
    }

    "createSignalGen returns null for unknown name" {
        val registry = SignalGenRegistry()
        val signal = registry.createSignalGen("xyznotreal", VoiceData.empty, 440.0)
        signal shouldBe null
    }

    "createSignalGen produces independent instances per call" {
        val registry = SignalGenRegistry()
        registry.register("test", SignalGenDsl.Sine(0.5))

        val data = VoiceData.empty.copy(sound = "test", freqHz = 440.0)

        val sig1 = registry.createSignalGen("test", data, 440.0)
        val sig2 = registry.createSignalGen("test", data, 440.0)

        sig1 shouldNotBe null
        sig2 shouldNotBe null
        // Must be different instances (independent mutable state per voice)
        (sig1 !== sig2) shouldBe true
    }

    "fork inherits legacy fallback" {
        val parent = SignalGenRegistry(
            legacyOscillators = oscillators(44100) { rng(Random(42)) },
        )
        parent.register("custom", SignalGenDsl.Sine())

        val child = parent.fork()

        // Child should have DSL entry
        child.contains("custom") shouldBe true
        // Child should also have legacy fallback
        child.contains("supersaw") shouldBe true
    }

    "registerDefaults compositions produce non-zero output" {
        val registry = SignalGenRegistry()
        registry.registerDefaults()

        val blockFrames = 128
        val ctx = SignalContext(
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
            val sig = dsl.toSignalGen()
            val buffer = FloatArray(blockFrames)
            sig.generate(buffer, 440.0, ctx)
            buffer.any { it != 0.0f } shouldBe true
        }
    }
})
