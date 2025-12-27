package io.peekandpoke.klang.audio_be

import org.khronos.webgl.Float32Array
import org.khronos.webgl.set
import kotlin.math.PI
import kotlin.math.sin

@JsName("KlangAudioProcessor")
class KlangAudioProcessor : AudioWorkletProcessor {

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

    @JsName("init")
    private fun init(block: Ctx.() -> Boolean): Boolean {
        console.log("init")

        return (ctx ?: Ctx()).let { ctx ->
            this.ctx = ctx

            // Listening (Receiving from Main Thread)
            port.onmessage = { e ->
                console.log("KlangAudioProcessor onmessage", e)

                val msg = e.data.toString()
                if (msg == "play") ctx.isPlaying = true
                if (msg == "stop") ctx.isPlaying = false
            }

            block(ctx)
        }
    }

    override fun process(
        inputs: Array<Array<Float32Array>>,
        outputs: Array<Array<Float32Array>>,
        parameters: dynamic,
    ): Boolean = init {
        // Port 0
        val output = outputs[0]

        // Number of channels (could be 1 for Mono, 2 for Stereo)
        val numChannels = output.size

        if (numChannels == 0 || !isPlaying) return@init true

        // We assume all channels are the same length (usually 128)
        val bufferSize = output[0].length

        // Generate samples once per frame, then write to all channels
        for (i in 0 until bufferSize) {
            val sample = (sin(phase) * 0.1).toFloat()
            phase += phaseInc
            if (phase >= 2.0 * PI) phase -= 2.0 * PI

            // Copy to all output channels (L, R, etc.)
            for (channelIdx in 0 until numChannels) {
                output[channelIdx][i] = sample
            }
        }

        // Continue to run
        true
    }
}

