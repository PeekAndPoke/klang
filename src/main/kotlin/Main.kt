package io.peekandpoke

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

//    complexJsStrudelTest()

    // Run the minimal audio demo using StrudelSynth
    run {
        val strudel = Strudel(Path.of("./build/strudel-bundle.mjs"))
        val synth = StrudelSynth(
            strudel = strudel,
            sampleRate = 48_000,
            oscillators = oscillators(sampleRate = 48_000),
            cps = 0.5,
        )

        try {
            val smallTownBoy = """
                note("<[c2 c3]*4 [bb1 bb2]*4 [f2 f3]*4 [eb2 eb3]*4>")
                .sound("supersaw").hpf(sine.range(800, 4000).slow(8))
            """.trimIndent()

            val crackle = """
                s("crackle*4")
                .density("<0.01 0.04 0.2 0.5>").slow(2).gain(0.1)
            """.trimIndent()

            val dust = """
                s("dust*4")
                .density("<0.01 0.04 0.2 0.5>").slow(2).gain(0.01)
            """.trimIndent()

            val pat = smallTownBoy
//            val pat = crackle
//            val pat = dust

            val compiled = strudel.compile(pat).await()!!

            println("pattern: $compiled")

            compiled.invokeMember("queryArc", 0.0, 10.0).also {
                val n = it.arraySize
                for (i in 0 until n) {
                    val ev = it.getArrayElement(i)
                    println(ev)
                }
            }

            synth.play(compiled)
            println("Done")
        } finally {
            strudel.close()
        }
    }
}

// ... existing code ...
fun complexJsStrudelTest() {

    val strudel = Strudel(Path.of("./build/strudel-bundle.mjs"))
//    println("strudel: $strudel")
//    println("strudel.core: ${strudel.core}")
//    println("strudel.mini: ${strudel.mini}")
//    println("strudel.transpiler: ${strudel.transpiler}")
//    println("strudel.compile: ${strudel.compileFn}")

//    val keys = strudel.core.memberKeys
//    println(keys.take(50))

//    val seq = strudel.core.invokeMember("sequence", "a c e")

//    run {
//        val seq = strudel.compile(
//            """
//            note("<[c2 c3]*4 [bb1 bb2]*4 [f2 f3]*4 [eb2 eb3]*4>")
//                .sound("sawtooth").lpf(800)
//        """.trimIndent()
//        )
//
//        println("seq: $seq")
//
//        println("== seq.queryArc(0.0, 10.0) ==========================================")
//
//        val queryArcResult = seq.invokeMember("queryArc", 0.0, 10.0)
//        queryArcResult.also {
//            println("num events returned: ${it.arraySize}")
//
//            for (i in 0 until it.arraySize) {
//                val ev = queryArcResult.getArrayElement(i)
//                println(ev) // prints the JS object; good enough for this check
//            }
//        }
//    }

    run {

        val noteRes = strudel.core.invokeMember("note", "a b c d")
        println("noteRes: $noteRes")
        val haps = strudel.queryPattern(noteRes, 0.0, 5.0)
        haps.also {
            println("num events returned: ${it.arraySize}")

            for (i in 0 until it.arraySize) {
                val ev = it.getArrayElement(i)
                println(ev) // prints the JS object; good enough for this check
            }
        }
    }

//    run {
//        val fromMini = strudel.mini.invokeMember("mini", "a c e")
//
//        val res = strudel.queryPattern(fromMini, 0.0, 10.0)
//
//        for (i in 0 until res.arraySize) {
//            val ev = res.getArrayElement(i)
//            println(ev)
//        }
//    }

    strudel.close()
}
