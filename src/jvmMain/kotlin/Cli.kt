package io.peekandpoke.klang

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_engine.cli.RenderWavCommand
import io.peekandpoke.klang.audio_fe.create
import io.peekandpoke.klang.audio_fe.samples.SampleCatalogue
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import kotlinx.coroutines.runBlocking

class KlangCli : CliktCommand(name = "klang") {
    override fun run() = Unit
}

/**
 * Creates a full KlangScript engine, executes the code, and returns the last expression as a KlangPattern.
 *
 * Inline ignitors (`note("a").sound(Osc.sine()...)`) are denormalized to synthetic names by the
 * offline renderer's internal ignitor registrar during render — no engine-level capture step.
 */
private fun compilePattern(code: String): RenderWavCommand.CompileResult? {
    val engine = klangScript {
        registerLibrary(sprudelLib)
        registerBuiltInSongsAsModules()
    }

    // Pre-import standard libraries so user code doesn't need to
    engine.execute("""import * from "stdlib"""")
    engine.execute("""import * from "sprudel"""")

    val result = engine.execute(code + "\n")
    val pattern = result.toObjectOrNull<KlangPattern>() ?: return null

    return RenderWavCommand.CompileResult(pattern = pattern)
}

fun main(args: Array<String>) {
    val samples = runBlocking { Samples.create(catalogue = SampleCatalogue.default) }

    KlangCli()
        .subcommands(
            RenderWavCommand(compilePattern = ::compilePattern, samples = samples),
        )
        .main(args)
}
