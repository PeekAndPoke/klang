package io.peekandpoke.klang

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_engine.cli.RenderWavCommand
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull
import io.peekandpoke.klang.sprudel.lang.sprudelLib

class KlangCli : CliktCommand(name = "klang") {
    override fun run() = Unit
}

/**
 * Creates a full KlangScript engine, executes the code, and returns the last expression as a KlangPattern.
 */
private fun compilePattern(code: String): KlangPattern? {
    val engine = klangScript {
        registerLibrary(sprudelLib)
    }

    // Pre-import standard libraries so user code doesn't need to
    engine.execute("""import * from "stdlib"""")
    engine.execute("""import * from "sprudel"""")

    val result = engine.execute(code + "\n")
    return result.toObjectOrNull<KlangPattern>()
}

fun main(args: Array<String>) {
    KlangCli()
        .subcommands(
            RenderWavCommand(compilePattern = ::compilePattern),
        )
        .main(args)
}
