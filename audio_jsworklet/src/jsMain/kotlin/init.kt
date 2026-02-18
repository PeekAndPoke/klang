import io.peekandpoke.klang.audio_bridge.registerProcessor

@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("main")
fun main() {
    console.log("[WORKLET] Registering KlangAudioWorklet")

    registerProcessor(
        "klang-audio-processor",
        KlangAudioWorklet::class.js,
    )

    console.log("[WORKLET] KlangAudioWorklet registered")
}
