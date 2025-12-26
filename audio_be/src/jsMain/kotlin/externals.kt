package io.peekandpoke.klang.audio_be

import org.khronos.webgl.Float32Array

// https://developer.mozilla.org/de/docs/Web/API/AudioWorkletProcessor

/**
 * Manually defining MessagePort if not importing from org.w3c.dom
 */
external class MessagePort {
    /** The callback when a message is received */
    var onmessage: ((MessageEvent) -> Unit)?

    /** Sends a message back to the main thread */
    fun postMessage(message: Any?)
}

/**
 * The event wrapper
 */
external class MessageEvent {
    val data: dynamic
}

/**
 * The base class for your Kotlin processor
 */
abstract external class AudioWorkletProcessor {
    val port: MessagePort

    /**
     * This is the standard Web Audio API callback signature.
     *
     * Its structure (Array of Arrays of Arrays) can be confusing at first glance.
     * Here is the breakdown of the hierarchy:
     *
     * # 1. outputs: Array<Array<Float32Array>>
     *
     * This structure represents Ports -> Channels -> Samples.
     *
     * ## Outer Array (The Ports):
     * - An AudioNode can have multiple "outputs" (cables connected to it).
     * - Usually, there is only one output port (outputs[0]).
     *
     * ## Middle Array (The Channels):
     * - That single port supports stereo, mono, 5.1, etc.
     * - outputs[0][0] is the Left channel.
     * - outputs[0][1] is the Right channel.
     *
     * ## Inner Array (Float32Array):
     * - This is the actual raw audio buffer.
     * - It is always fixed size (usually 128 samples).
     * - You write your audio data into this array.
     *
     * ## Visualization
     *
     * ```
     * outputs
     *  └─ [0] (Output Port 0)
     *      ├─ [0] (Left Channel)  -> [0.0, 0.1, -0.5, ... 128 samples]
     *      └─ [1] (Right Channel) -> [0.0, 0.1, -0.5, ... 128 samples]
     * ```
     *
     * # 2. inputs: Array<Array<Float32Array>>
     *
     * This is exactly the same structure as outputs, but for incoming audio.
     * - If your node is a synth (source), inputs will be empty or unused.
     * - If your node is an effect (like a delay), you read from inputs[0][0]
     *   and modify the data before writing to outputs.
     *
     * # 3. parameters: dynamic
     *
     * This contains AudioParams (automation data).
     * - If you defined a custom parameter like "frequency" in your static parameterDescriptors getter.
     * - The parameters object will contain arrays of values for that parameter (allowing for
     *   sample-accurate automation).
     * - Example: parameters["frequency"] might be a Float32Array of 128 different frequency values for this block.
     *
     * # 4. Return value: Boolean
     *
     * - `true`: "Keep me alive." The browser will call process() again next block.
     * - `false`: "I am finished." The browser can garbage collect this node (silence).
     */
    abstract fun process(
        inputs: Array<Array<Float32Array>>,
        outputs: Array<Array<Float32Array>>,
        parameters: dynamic,
    ): Boolean
}

/**
 * Registers an AudioWorkletProcessor
 */
external fun registerProcessor(name: String, processorCtor: Any)
