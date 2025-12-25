package io.peekandpoke.klang

import io.peekandpoke.klang.samples.SampleCatalogue
import io.peekandpoke.klang.samples.Samples
import io.peekandpoke.klang.strudel.StrudelPlayer
import io.peekandpoke.klang.strudel.graal.GraalStrudelCompiler
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

    val strudel = GraalStrudelCompiler(Path.of("./build/strudel-bundle.mjs"))
    try {
        val pattern = TestPatterns.active

        val compiled = strudel.compile(pattern).await()
        strudel.dumpPatternArc(compiled)

        val events = compiled.queryArc(0.0, 4.0, 44_100)
        events.forEach {
            strudel.prettyFormat(it)
//            println("${it.begin} ${it.note} ${it.sound}")
        }

        val samples = Samples.create(
            catalogue = SampleCatalogue.default,
//            catalogue = SampleCatalogue.of(SampleCatalogue.piano),
//            catalogue = SampleCatalogue.of(SampleCatalogue.strudelDefaultDrums),
        )

        val audio = StrudelPlayer(
            pattern = compiled,
            options = StrudelPlayer.Options(
                sampleRate = 48_000,
                cps = 0.5,
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
