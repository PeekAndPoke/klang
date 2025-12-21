package io.peekandpoke

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
        val strudel = Strudel(Path.of("./build/strudel-bundle.mjs"))
        val audio = StrudelAudioRenderer(
            strudel = strudel,
            sampleRate = 48_000,
            oscillators = oscillators(sampleRate = 48_000),
            cps = 0.5,
        )

        try {
            val smallTownBoyBass = """
                note("<[c2 c3]*4 [bb1 bb2]*4 [f2 f3]*4 [eb2 eb3]*4>")
                .sound("supersaw").lpf(sine.range(800, 4000).slow(8))
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
                  .sound("supersaw")
                  .detune("<.3 .3 .3 5.0>")
                  .gain(0.25)
//                  .hpf(100)
//                  .spread(".8")
//                  .unison("2 7")
            """.trimIndent()

//            val pat = smallTownBoyBass
//            val pat = crackle
//            val pat = dust
//            val pat = impulse
//            val pat = whiteNoise
//            val pat = brownNoise
//            val pat = pinkNoise
            val pat = supersaw

            val compiled = strudel.compile(pat).await()!!

            println("pattern: $compiled")

            compiled.invokeMember("queryArc", 0.0, 10.0).also {
                val n = it.arraySize
                for (i in 0 until n) {
                    val ev = it.getArrayElement(i)
                    println(ev)
                }
            }

            audio.start(compiled)
            delay(10_000)
            audio.stop()
            println("Done")
        } finally {
            strudel.close()
        }
    }
}
