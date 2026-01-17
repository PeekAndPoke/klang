package io.peekandpoke.klang

import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_fe.create
import io.peekandpoke.klang.audio_fe.samples.SampleCatalogue
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.strudel.lang.pan
import io.peekandpoke.klang.strudel.playStrudel
import io.peekandpoke.klang.strudel.strudelPlayer
import kotlinx.browser.document
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

fun main() {
    console.log("Klang JS App Started")

    val container = document.createElement("div")
    document.body?.appendChild(container)

    val btn = document.createElement("button")
    btn.textContent = "Start Audio"
    container.appendChild(btn)

    val status = document.createElement("div")
    status.textContent = "Status: Ready"
    container.appendChild(status)

    btn.addEventListener("click", {
        status.textContent = "Status: Starting..."

        // Browsers require a user gesture to start AudioContext.
        // We launch the player inside this click handler.
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch {
            try {
                runStrudelDemo()
                status.textContent = "Status: Playing"
            } catch (e: Throwable) {
                console.error(e)
                status.textContent = "Status: Error (see console)"
            }
        }
    })
}

suspend fun runStrudelDemo() {
    console.log("Loading sample catalogue")

    // 1. Setup Samples (Web-compatible catalogue)
    val samples = Samples.create(catalogue = SampleCatalogue.default)

    console.log("Setting up options")

    // 2. Setup Player Options
    val playerOptions = KlangPlayer.Options(
        samples = samples,
        sampleRate = 48000,
        cyclesPerSecond = 0.6,
    )

    val pattern1 = TestKotlinPatterns.strangerThings.pan(-1.0)
    val pattern2 = TestKotlinPatterns.tetris.pan(1.0)

//    console.log("Compiling pattern")
//    val pattern = StrudelPattern.compile(TestTextPatterns.tetris)!!

    console.log("Running strudel player")

    // 4. Create Player
    val player = strudelPlayer(
        options = playerOptions
    )

    // 5. Start playback
    val playback1 = player.playStrudel(pattern1)
    playback1.start()

    delay(5_000.milliseconds)

    val playback2 = player.playStrudel(pattern2)
    playback2.start()

    println("Strudel Player started!")
}
