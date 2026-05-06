package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer

class MemoizingIgnitorSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun createCtx(): IgniteContext = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = blockFrames * 16,
        gateEndFrame = blockFrames * 16,
        releaseFrames = 0,
        voiceEndFrame = blockFrames * 16,
        scratchBuffers = ScratchBuffers(blockFrames),
    ).apply {
        offset = 0
        length = blockFrames
        voiceElapsedFrames = 0
    }

    /**
     * Counting probe — increments [calls] on every generate() and writes a
     * distinct sample per call so we can detect whether the caller got the
     * cached or freshly-generated output.
     */
    class CountingIgnitor : Ignitor {
        var calls = 0
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            calls++
            val stamp = calls.toDouble()
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                buffer[i] = stamp
            }
        }
    }

    "generates fresh on first call" {
        val probe = CountingIgnitor()
        val memo = MemoizingIgnitor(probe)
        val ctx = createCtx()
        val out = AudioBuffer(blockFrames)

        memo.generate(out, 440.0, ctx)

        probe.calls shouldBeExactly 1
        out[0] shouldBe 1.0
    }

    "single consumer: generates directly into buffer (no cache overhead)" {
        val probe = CountingIgnitor()
        val memo = MemoizingIgnitor(probe) // consumers=1 by default
        val ctx = createCtx()
        val out = AudioBuffer(blockFrames)

        memo.generate(out, 440.0, ctx)
        probe.calls shouldBeExactly 1
        out[0] shouldBe 1.0
    }

    "multi-consumer: second call within same block uses cache (probe not re-run)" {
        val probe = CountingIgnitor()
        val memo = MemoizingIgnitor(probe)
        memo.incConsumers() // simulate shared node (2 consumers)
        val ctx = createCtx()
        val out1 = AudioBuffer(blockFrames)
        val out2 = AudioBuffer(blockFrames)

        memo.generate(out1, 440.0, ctx)
        memo.generate(out2, 440.0, ctx)

        probe.calls shouldBeExactly 1
        out2[0] shouldBe 1.0
        for (i in 0 until blockFrames) out2[i] shouldBe out1[i]
    }

    "advancing voiceElapsedFrames invalidates cache" {
        val probe = CountingIgnitor()
        val memo = MemoizingIgnitor(probe)
        memo.incConsumers()
        val ctx = createCtx()
        val out = AudioBuffer(blockFrames)

        memo.generate(out, 440.0, ctx)
        probe.calls shouldBeExactly 1

        ctx.voiceElapsedFrames = blockFrames
        memo.generate(out, 440.0, ctx)
        probe.calls shouldBeExactly 2
        out[0] shouldBe 2.0
    }

    "changing freqHz invalidates cache (e.g. detune path)" {
        val probe = CountingIgnitor()
        val memo = MemoizingIgnitor(probe)
        memo.incConsumers()
        val ctx = createCtx()
        val out = AudioBuffer(blockFrames)

        memo.generate(out, 440.0, ctx)
        probe.calls shouldBeExactly 1

        memo.generate(out, 880.0, ctx)
        probe.calls shouldBeExactly 2
    }

    "changing offset invalidates cache (sub-block render)" {
        val probe = CountingIgnitor()
        val memo = MemoizingIgnitor(probe)
        memo.incConsumers()
        val ctx = createCtx()
        val out = AudioBuffer(blockFrames * 2)

        ctx.offset = 0
        ctx.length = blockFrames
        memo.generate(out, 440.0, ctx)
        probe.calls shouldBeExactly 1

        ctx.offset = blockFrames
        memo.generate(out, 440.0, ctx)
        probe.calls shouldBeExactly 2
    }

    "three readers within same block trigger one generate call" {
        val probe = CountingIgnitor()
        val memo = MemoizingIgnitor(probe)
        memo.incConsumers() // 2
        memo.incConsumers() // 3
        val ctx = createCtx()
        val a = AudioBuffer(blockFrames)
        val b = AudioBuffer(blockFrames)
        val c = AudioBuffer(blockFrames)

        memo.generate(a, 440.0, ctx)
        memo.generate(b, 440.0, ctx)
        memo.generate(c, 440.0, ctx)

        probe.calls shouldBeExactly 1
        a[0] shouldBe 1.0
        b[0] shouldBe 1.0
        c[0] shouldBe 1.0
    }
})
