package io.peekandpoke

import io.peekandpoke.graal.GraalStrudelCompiler
import io.peekandpoke.samples.SampleBankIndexLoader
import io.peekandpoke.samples.SampleRegistry
import io.peekandpoke.samples.decoders.WavDecoder
import io.peekandpoke.utils.AssetLoader
import io.peekandpoke.utils.withDiskCache
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
        // Song collections:
        // https://github.com/eefano/strudel-songs-collection

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
                    .sound("supersaw").unison(8)
                    .lpf(400),
                    // melody
                    arrange(
                      [8, silence],
                      [8, n("<[~ 0] 2 [0 2] [~ 2][~ 0] 1 [0 1] [~ 1][~ 0] 3 [0 3] [~ 3][~ 0] 2 [0 2] [~ 2]>*4")],
                    )
                    .scale("C4:minor")
                    .hpf(400)
                    .sound("triangle").gain(0.8),
                    // Drums
                    sound("bd hh sd hh").fast(2).gain(0.75),
                )
                
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
                    >`).sound("triangle").unison(2).detune(wchoose([0.2, 1.00], [0.4, 0.05]))
                    .hpf(800).gain("0.4"),
                    note(`<
                        [[e2 e3]*4]
                        [[a2 a3]*4]
                        [[g#2 g#3]*2 [e2 e3]*2]
                        [a2 a3 a2 a3 a2 a3 b1 c2]
                        [[d2 d3]*4]
                        [[c2 c3]*4]
                        [[b1 b2]*2 [e2 e3]*2]
                        [[a1 a2]*4]
                    >`).sound("sine").unison(4).detune(sine.range(0.3, 0.6).slow(8))
                    .lpf(800).gain(0.6),
                    sound("bd hh sd hh").fast(wchoose([2, 1.0], [1, 0.1])).gain(0.5),
                )
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

            val simpleDrums = """
                stack(
                  //n("0 1 2 3 4 5 6 7").scale("C4:minor"),
                  sound("bd hh sd oh")
//                  .lpf("100 200 300 400 500 600 700 800")
                  .fast(2)
                  .gain(1.0),
                  
                )
            """.trimIndent()

            val snareScale = """
                n("0 1 2 3 4 5 6 7").scale("c3:major").sound("sd")
            """.trimIndent()

            val strangerThings = """
                stack(
                    n("0 2 4 6 7 6 4 2")
                      .scale("<c3:major>/2")
                      .s("supersaw")
                      .distort(0.7)
                      .superimpose((x) => x.detune("<0.5>"))
                      .lpenv(perlin.slow(3).range(1, 4))
                      .lpf(perlin.slow(2).range(100, 2000))
                      .gain(0.6),
                    note("<a1 e2>/8").clip(0.8).struct("x*8").s("supersaw").gain(0.5),
                )
            """.trimIndent()

//            val pat = smallTownBoyBass
//            val pat = smallTownBoyMelody
//            val pat = smallTownBoy
//            val pat = tetris
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
//            val pat = simpleDrums
            val pat = snareScale
//            val pat = strangerThings

            val compiled = strudel.compile(pat).await()
            strudel.dumpPatternArc(compiled)

            val events = compiled.queryArc(0.0, 4.0, 44_100)
            events.forEach {
                println("${it.begin} ${it.note} ${it.sound}")
            }

            val samples = createSampleRegistry()

            val audio = StrudelAudioRenderer(
                pattern = compiled,
                options = StrudelAudioRenderer.RenderOptions(
                    sampleRate = 44_100,
                    cps = 0.2,
                    samples = samples,
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

suspend fun createSampleRegistry(): SampleRegistry {
    val samplesUrl =
        "https://raw.githubusercontent.com/felixroos/dough-samples/main/tidal-drum-machines.json"

    val aliasUrl =
        "https://raw.githubusercontent.com/todepond/samples/main/tidal-drum-machines-alias.json"

    val bankLoader = SampleBankIndexLoader(
        loader = AssetLoader.default.withDiskCache(Path.of("./cache/index")),
    )

    val index: SampleRegistry.SampleBankIndex = bankLoader.load(
        sampleMapUrl = samplesUrl,
        aliasUrl = aliasUrl,
    )

    return SampleRegistry(
        index = index,
        decoder = WavDecoder(),
        loader = AssetLoader.default.withDiskCache(Path.of("./cache/samples")),
    )
}
