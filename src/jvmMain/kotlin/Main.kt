package io.peekandpoke.klang

import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_fe.create
import io.peekandpoke.klang.audio_fe.samples.SampleCatalogue
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.strudel.StrudelPlayback
import io.peekandpoke.klang.strudel.graal.GraalStrudelCompiler
import io.peekandpoke.klang.strudel.lang.*
import io.peekandpoke.klang.strudel.playStrudel
import io.peekandpoke.klang.strudel.strudelPlayer
import kotlinx.coroutines.delay
import org.graalvm.polyglot.Context

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
suspend fun main() {
    // Print the name of the java runtime that is running
    println("Runtime Name: ${System.getProperty("java.runtime.name")}")
    println("VM Name:      ${System.getProperty("java.vm.name")}")
    println("VM Vendor:    ${System.getProperty("java.vendor")}")
    println("VM Version:   ${System.getProperty("java.vm.version")}")

    helloJs()
    helloStrudel()
}

private fun helloJs() {

    // Create a context for JavaScript
    Context.create("js").use { context ->
        // Evaluate a simple JS snippet
        val result = context.eval("js", "const x = 10; const y = 20; x + y")

        println("JS Result: ${result.asInt()}") // Should print 30

        // You can also run more complex logic
        context.eval(
            "js", """
            function greet(name) {
                return 'Hello from JS, ' + name + '!';
            }
            console.log(greet('Kotlin User'));
        """.trimIndent()
        )
    }
}

private suspend fun helloStrudel() {
    // Run the minimal audio demo using StrudelSynth
    // Song collections:
    // https://github.com/eefano/strudel-songs-collection

    val strudel = GraalStrudelCompiler(
//        Path.of("./build/strudel-bundle.mjs").toFile()
    )

    strudel.use { strudel ->
//        val code = TestTextPatterns.smallTownBoy
//        val pattern = strudel.compile(code).await()

        val engine = klangScript {
            registerLibrary(strudelLib)
        }

//        val compiled = strudel.compile(
//            """
//                note("c").iresponse("hall")
//            """.trimIndent()
//        ).await()
//
//        compiled.queryArc(0.0, 1.0).let { arc ->
//            arc.forEach {
//                println(it)
//            }
//        }
//
//        exitProcess(1)


//        val result = engine.execute(
//            """
//            import * from "stdlib"
//            import * from "strudel"
//
//            note("a b").filter(x => x + 1)
//            note("a b").filter(x => x.data.note == "a")
//            note("a b").filter((x) => {
//                let note = x.data.note
//                return note == "a"
//            })
//        """.trimIndent()
//        )
//
//        println(result)
//
//        val code = """
//                sound("a")
//                  .loop()
//                  .begin(0.5)
//                  .end(0.5)
//                  .speed(-1)
//                  .loopAt(2)
//                  .cut(1)
//                  .slice(4, 1)
//        """.trimIndent()

//        val pattern = strudel.compile(code).await()

//        val pattern = StrudelPattern.compile(code)!!

//        val pattern = sound("bd").fast(2).pan(sine.range(-1.0, 1.0).slow(8))

//        val pattern = sound("bd").loop()

//
//        println("=======================================================================")
//        println(
//            pattern1.makeStatic(0.0, 8.0).toJson()
//        )
//        println("=======================================================================")

//        val pattern1 = TestKotlinPatterns.tetris // .pan(-1.0)

//        val pattern1 = StrudelPattern.compile(TestTextPatterns.strangerThingsNetflix)!!
        val pattern1 = s("bd hh sd oh").chunk(2, { x -> x.fast(2) }).slow(2)

//        val pattern1 = StrudelPattern.compile(
//            """
//            sound("bd hh sd oh").fast(2)
//        """.trimIndent()
//        )!!

        val pattern2 = TestKotlinPatterns.strangerThings.pan(1.0)

        val samples = Samples.create(catalogue = SampleCatalogue.default)
        val playerOptions = KlangPlayer.Options(
            samples = samples,
            sampleRate = 48_000,
        )

        val player = strudelPlayer(
            options = playerOptions,
        )

        println("start 1 ...")
        val playback1 = player.playStrudel(pattern1)
        playback1.start(
            StrudelPlayback.Options(
                cyclesPerSecond = 0.5,
            )
        )

//        delay(10_000)
//        val playback2 = player.playStrudel(pattern2)
//        playback2.start(
//            KlangPlayback.Options(
//                cyclesPerSecond = 0.6,
//            )
//        )

        delay(600_000)
        playback1.stop()
//        playback2.stop()

        player.shutdown()
        println("Done")
    }
}

