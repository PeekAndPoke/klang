package io.peekandpoke.klang

import io.peekandpoke.klang.audio_engine.KlangCyclicPlayback
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_engine.klangPlayer
import io.peekandpoke.klang.audio_engine.play
import io.peekandpoke.klang.audio_fe.create
import io.peekandpoke.klang.audio_fe.samples.SampleCatalogue
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.graal.GraalSprudelCompiler
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import kotlinx.coroutines.delay

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
suspend fun main() {
    // Print the name of the java runtime that is running
    println("Runtime Name: ${System.getProperty("java.runtime.name")}")
    println("VM Name:      ${System.getProperty("java.vm.name")}")
    println("VM Vendor:    ${System.getProperty("java.vendor")}")
    println("VM Version:   ${System.getProperty("java.vm.version")}")

    helloStrudel()
}

private suspend fun helloStrudel() {
    // Run the minimal audio demo
    // Song collections:
    // https://github.com/eefano/strudel-songs-collection

    val graal = GraalSprudelCompiler()

    graal.use { graalComp ->
        val engine = klangScript {
            registerLibrary(sprudelLib)
        }

        val compiled = graalComp.compile(
            """
                note("c").late("0.5")
            """.trimIndent()
        ).await()

        compiled.queryArc(0.0, 1.0).let { arc ->
            arc.forEach {
                println(it)
            }
        }

        val pattern1 = SprudelPattern.compile(TestTextPatterns.aTruthWorthLyingFor)!!

        val pattern2 = TestKotlinPatterns.strangerThings

        val samples = Samples.create(catalogue = SampleCatalogue.default)
        val playerOptions = KlangPlayer.Options(
            samples = samples,
            sampleRate = 48_000,
        )

        val player = klangPlayer(
            options = playerOptions,
        ) {}

        println("start 1 ...")
        val playback1 = player.play(pattern1)
        playback1.start(
            KlangCyclicPlayback.Options(
                rpm = 32.4,
            )
        )

        delay(600_000)
        playback1.stop()
//        playback2.stop()

        player.shutdown()
        println("Done")
    }
}
