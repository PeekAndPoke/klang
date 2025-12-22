package io.peekandpoke

import io.peekandpoke.graal.GraalStrudelCompiler
import kotlinx.coroutines.delay
import org.graalvm.polyglot.Context
import java.nio.file.Path

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
suspend fun main() {
    // Print the name of the java runtime that is running
    println("Runtime Name: ${System.getProperty("java.runtime.name")}")
    println("VM Name:      ${System.getProperty("java.vm.name")}")
    println("VM Vendor:    ${System.getProperty("java.vendor")}")
    println("VM Version:   ${System.getProperty("java.vm.version")}")

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

    // Run the minimal audio demo using StrudelSynth
    run {
        val strudel = GraalStrudelCompiler(Path.of("./build/strudel-bundle.mjs"))
        try {
            val smallTownBoyBass = """
                note("<[c2 c3]*4 [bb1 bb2]*4 [f2 f3]*4 [eb2 eb3]*4>")
                .sound("supersaw").unison(16).lpf(sine.range(400, 2000).slow(4))
            """.trimIndent()

            val smallTownBoyMelody = """
                n("<[~ 0] 2 [0 2] [~ 2][~ 0] 1 [0 1] [~ 1][~ 0] 3 [0 3] [~ 3][~ 0] 2 [0 2] [~ 2]>*4")
                .scale("C4:minor")
                .sound("saw")
            """.trimIndent()

            val smallTownBoy = """
                stack(
                    // bass
                    note("<[c2 c3]*4 [bb1 bb2]*4 [f2 f3]*4 [eb2 eb3]*4>")
                    .sound("supersaw").unison(16).lpf(sine.range(400, 2000).slow(4)),
                    // melody
                    arrange(
                      [8, silence],
                      [8, n("<[~ 0] 2 [0 2] [~ 2][~ 0] 1 [0 1] [~ 1][~ 0] 3 [0 3] [~ 3][~ 0] 2 [0 2] [~ 2]>*4")],
                    )
                    .scale("C4:minor")
                    .sound("triangle")
                ).gain(0.5)
                
            """.trimIndent()

            val tetris = """
                stack(
                    note(`<
                        [e5 [b4 c5] d5 [c5 b4]]
                        [a4 [a4 c5] e5 [d5 c5]]
                        [b4 [~ c5] d5 e5]
                        [c5 a4 a4 ~]
                        [[~ d5] [~ f5] a5 [g5 f5]]
                        [e5 [~ c5] e5 [d5 c5]]
                        [b4 [b4 c5] d5 e5]
                        [c5 a4 a4 ~]
                    >`).sound("triangle").gain(0.5),
                    note(`<
                        [[e2 e3]*4]
                        [[a2 a3]*4]
                        [[g#2 g#3]*2 [e2 e3]*2]
                        [a2 a3 a2 a3 a2 a3 b1 c2]
                        [[d2 d3]*4]
                        [[c2 c3]*4]
                        [[b1 b2]*2 [e2 e3]*2]
                        [[a1 a2]*4]
                    >`).sound("sine").unison(4).detune(sine.range(0.3, 0.6).slow(8)).gain(0.5)
                ).gain(0.5)
            """.trimIndent()

            val c4Minor = """
                n("0 1 2 3 4 5 6 7").scale("C4:minor")
            """.trimIndent()

            val numberNotes = """
                note("40 42 44 46")
            """.trimIndent()

            val crackle = """
                s("crackle*4")
                .density("<0.01 0.04 0.2 0.5>").slow(2).gain(0.1)
            """.trimIndent()

            val dust = """
                s("dust*4")
                .density("<0.01 0.04 0.2 0.5>").slow(2).gain(0.01)
            """.trimIndent()

            val impulse = """note("c6").sound("impulse").gain(0.05)""".trimIndent()

            val whiteNoise = """
                s("white").gain("<0.01 0.04 0.2 0.5>")
                """.trimIndent()

            val brownNoise = """
                s("brown").gain("<0.01 0.04 0.2 0.5>")
                """.trimIndent()

            val pinkNoise = """
                s("pink").gain("<0.01 0.04 0.2 0.5>")
                """.trimIndent()

            val supersaw = """
                note("<[c2 c3]*4 [bb1 bb2]*4 [f2 f3]*4 [eb2 eb3]*4>")
                  .sound("sine")
//                  .detune("<.3 .3 .3 1.0>")
                  .gain(0.25)
//                  .hpf(100)
//                  .spread(".8")
//                  .unison("2 7")
            """.trimIndent()

            val polyphone = """
                note("c!2 [eb,<g a bb a>]")
            """.trimIndent()

//            val pat = smallTownBoyBass
//            val pat = smallTownBoyMelody
//            val pat = smallTownBoy
            val pat = tetris
//            val pat = c4Minor
//            val pat = numberNotes
//            val pat = crackle
//            val pat = dust
//            val pat = impulse
//            val pat = whiteNoise
//            val pat = brownNoise
//            val pat = pinkNoise
//            val pat = supersaw
//            val pat = polyphone

            val sanitized = pat.lines()
                .filter { !it.trim().startsWith("//") }
                .joinToString(" ")

            val compiled = strudel.compile(sanitized).await()

//            strudel.dumpPatternArc(compiled)

            val audio = StrudelAudioRenderer(
                pattern = compiled,
                options = StrudelAudioRenderer.RenderOptions(
                    sampleRate = 44_100,
                    cps = 0.4
                ),
            )

            audio.start()
            delay(600_000)
            audio.stop()
            println("Done")

        } finally {
            strudel.close()
        }
    }
}
