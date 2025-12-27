package io.peekandpoke.klang.audio_engine

import org.w3c.dom.MessagePort
import kotlin.js.Promise

// --- External Web Audio API Definitions ---

external class AudioContext {
    val audioWorklet: AudioWorklet
    val destination: AudioNode
    val state: String
    fun resume(): Promise<Unit>
    fun close(): Promise<Unit>
}

external class AudioWorklet {
    fun addModule(moduleUrl: String): Promise<Unit>
}

open external class AudioNode {
    fun connect(destination: AudioNode)
    fun disconnect()
}

external class AudioWorkletNode(context: AudioContext, name: String) : AudioNode {
    val port: MessagePort
}
