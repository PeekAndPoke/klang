package io.peekandpoke.klang

import io.peekandpoke.klang.audio_engine.KlangCyclicPlayback
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_engine.klangPlayer
import io.peekandpoke.klang.audio_engine.play
import io.peekandpoke.klang.audio_engine.setPlayer
import io.peekandpoke.klang.audio_fe.create
import io.peekandpoke.klang.audio_fe.samples.SampleCatalogue
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import kotlinx.coroutines.delay

/**
 * Playground entry point for testing real-time JVM audio playback.
 */
suspend fun main() {
    println("Runtime Name: ${System.getProperty("java.runtime.name")}")
    println("VM Name:      ${System.getProperty("java.vm.name")}")
    println("VM Vendor:    ${System.getProperty("java.vendor")}")
    println("VM Version:   ${System.getProperty("java.vm.version")}")

    val samples = Samples.create(catalogue = SampleCatalogue.default)
    val playerOptions = KlangPlayer.Options(
        samples = samples,
        sampleRate = 48_000,
    )

    val player = klangPlayer(options = playerOptions)

    val scripting = klangScript {
        setPlayer(player)
        registerLibrary(sprudelLib)
        registerBuiltInSongsAsModules()
    }

    val pattern = SprudelPattern.compile(scripting, BuiltInSongs.aTruthWorthLyingFor.code)!!
//    val pattern = SprudelPattern.compile(scripting, BuiltInSongs.tetris.code)!!

    println("Starting playback...")
    val playback = player.play(pattern)
    playback.start(KlangCyclicPlayback.Options(rpm = 31.0))

    delay(600_000)
    playback.stop()
    player.shutdown()
    println("Done")
}
