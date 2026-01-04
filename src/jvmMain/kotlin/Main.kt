package io.peekandpoke.klang

import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_fe.create
import io.peekandpoke.klang.audio_fe.samples.SampleCatalogue
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.graal.GraalStrudelCompiler
import io.peekandpoke.klang.strudel.lang.*
import io.peekandpoke.klang.strudel.makeStatic
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
//        val pattern = TestPatterns.active
//        val pattern1 = strudel.compile(pattern).await()

//        val pattern1 = stack(
//            note("c3", "e3", "g3", "b3").sound("tri", "supersaw").slow(2)
//            sound("bd", "hh", "sd", "oh").slow(1),
//        )

//        val pattern1 = stack(
//            seq(
//                note("c3 e3 g3 b3"),
//                note("c3 e3 g3 b#3"),
//            ).sound("tri supersaw").gain(0.1, 0.3, 0.5, 1.0).adsr("0.2:0.2:0.8:0.5"),
//            sound("bd [hh hh hh] sd oh").gain(1),
//        ).slow(1)

        val tetrisDslPattern = stack(
            note(
                """
                <
                    [e5 [b4 c5] d5 [c5 b4]]
                    [a4 [a4 c5] e5 [d5 c5]]
                    [b4 [~ c5] d5 e5]
                    [c5 a4 a4 ~]
                    [[~ d5] [~ f5] a5 [g5 f5]]
                    [e5 [~ c5] e5 [d5 c5]]
                    [b4 [b4 c5] d5 e5]
                    [c5 a4 a4 ~]
                >
                """.trimIndent()
            ).sound("triangle").orbit(0)
                .gain("0.3").room(0.01).rsize(3.0)
                .delay("0.25").delaytime(0.25).delayfeedback(0.75),

            note(
                """
                    <
                        [[e2 e3]*4]
                        [[a2 a3]*4]
                        [[g#2 g#3]*2 [e2 e3]*2]
                        [a2 a3 a2 a3 a2 a3 b1 c2]
                        [[d2 d3]*4]
                        [[c2 c3]*4]
                        [[b1 b2]*2 [e2 e3]*2]
                        [[a1 a2]*4]
                    >
                """.trimIndent()
            ).sound("supersaw").orbit(1)
                .pan(0.6).gain(0.6)
                .room(0.01).rsize(3.0),

            sound("bd hh sd hh").orbit(2)
                .pan(-0.7).gain(0.9)
                .room(0.01).rsize(3.0)
                .delay("0.0 0.0 0.5 0.0").delaytime(0.25).delayfeedback(0.75)
                .fast(2),
        )

        val tetrisCompiledPattern = StrudelPattern.compile(
            TestTextPatterns.tetris
        )

        val pattern = tetrisCompiledPattern

//        val pattern1 = sound("bd hh sd oh")

        println("=======================================================================")
        println(
            pattern.makeStatic(0.0, 8.0).toJson()
        )
        println("=======================================================================")

        val samples = Samples.create(catalogue = SampleCatalogue.default)
        val playerOptions = KlangPlayer.Options(
            samples = samples,
            sampleRate = 48_000,
            cyclesPerSecond = 0.5,
        )

        val audio1 = strudelPlayer(
            pattern = pattern,
            options = playerOptions,
        )

        println("start 1 ...")
        audio1.start()

        delay(600_000)
        audio1.stop()
//        audio2.stop()
        println("Done")
    }
}

