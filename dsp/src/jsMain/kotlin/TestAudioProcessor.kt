package io.peekandpoke.klang.dsp

import org.khronos.webgl.Float32Array
import org.khronos.webgl.set
import kotlin.math.PI
import kotlin.math.sin

class TestAudioProcessor : AudioWorkletProcessor {

    class Ctx {
        // State
        var isPlaying = false
        var phase = 0.0

        // Constants
        val sampleRate = 44100.0
        val frequency = 440.0 // A4 note
        val phaseInc = (2.0 * PI * frequency) / sampleRate
    }

    private var ctx: Ctx? = null

    private fun init(): Ctx {
        if (ctx != null) return ctx!!

        ctx = Ctx()

        console.log("TestAudioProcessor init2", this)

        // Listening (Receiving from Main Thread)
        port.onmessage = { e ->
            console.log("TestAudioProcessor onmessage", e)

            ctx?.let { ctx ->
                val msg = e.data.toString()
                if (msg == "play") ctx.isPlaying = true
                if (msg == "stop") ctx.isPlaying = false
            }
        }

        return ctx!!
    }

    override fun process(
        inputs: Array<Array<Float32Array>>,
        outputs: Array<Array<Float32Array>>,
        parameters: dynamic,
    ): Boolean {
        // Init hack
        if (ctx == null) init()
        // We now have the ctx
        val ctx = ctx!!

        // Port 0
        val output = outputs[0]

        // Number of channels (could be 1 for Mono, 2 for Stereo)
        val numChannels = output.size


        if (numChannels == 0) return true

        // We assume all channels are the same length (usually 128)
        val bufferSize = output[0].length

        // Generate samples once per frame, then write to all channels
        for (i in 0 until bufferSize) {
            var sample = 0f

            if (ctx.isPlaying) {
                sample = (sin(ctx.phase) * 0.1).toFloat()
                ctx.phase += ctx.phaseInc
                if (ctx.phase >= 2.0 * PI) ctx.phase -= 2.0 * PI
            }

            // Copy to all output channels (L, R, etc.)
            for (channelIdx in 0 until numChannels) {
                output[channelIdx][i] = sample
            }
        }

        return true
    }
}

