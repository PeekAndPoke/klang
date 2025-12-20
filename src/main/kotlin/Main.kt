package io.peekandpoke

import Strudel
import org.graalvm.polyglot.Context
import java.nio.file.Path

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {
    val name = "Kotlin"
    //TIP Press <shortcut actionId="ShowIntentionActions"/> with your caret at the highlighted text
    // to see how IntelliJ IDEA suggests fixing it.
    println("Hello, " + name + "!")

    for (i in 1..5) {
        //TIP Press <shortcut actionId="Debug"/> to start debugging your code. We have set one <icon src="AllIcons.Debugger.Db_set_breakpoint"/> breakpoint
        // for you, but you can always add more by pressing <shortcut actionId="ToggleLineBreakpoint"/>.
        println("i = $i")
    }

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

    complexJsStrudelTest()
}

// ... existing code ...
fun complexJsStrudelTest() {

    val strudel = Strudel(Path.of("./build/strudel-bundle.mjs"))
    println("strudel: $strudel")

    println("strudel.core: ${strudel.core}")
    println("strudel.mini: ${strudel.mini}")
    println("strudel.transpiler: ${strudel.transpiler}")
    println("strudel.compile: ${strudel.compile}")

//    val keys = strudel.core.memberKeys
//    println(keys.take(50))

//    val seq = strudel.core.invokeMember("sequence", "a c e")

    val seq = strudel.compile!!.execute(
        """
            note("<[c2 c3]*4 [bb1 bb2]*4 [f2 f3]*4 [eb2 eb3]*4>")
                .sound("sawtooth").lpf(800)
        """.trimIndent()
    )

    println("seq: $seq")

    val eventsVal = seq!!.invokeMember("queryArc", 0.0, 10.0)
    val n = eventsVal.arraySize
    println("num events returned: $n")

    for (i in 0 until n) {
        val ev = eventsVal.getArrayElement(i)
        println(ev) // prints the JS object; good enough for this check
    }

    strudel.close()

}
