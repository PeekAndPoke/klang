package io.peekandpoke.graal

import io.peekandpoke.StrudelCompiler
import io.peekandpoke.StrudelPattern
import io.peekandpoke.graal.GraalJsHelpers.promiseToDeferred
import kotlinx.coroutines.Deferred
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import java.nio.file.Path

class GraalStrudelCompiler(esmBundlePath: Path) : StrudelCompiler, AutoCloseable {

    val ctx: Context = Context.newBuilder("js")
        .option("js.esm-eval-returns-exports", "true")
        .build()

    val source: Source = Source.newBuilder("js", esmBundlePath.toFile())
        .mimeType("application/javascript+module")
        .build()

    val exports: Value = ctx.eval(source)
        ?: error("Error loading Strudel bundle: $esmBundlePath")

    val performance: Value = exports.getMember("performance")
        ?: error("Error loading Strudel bundle: $esmBundlePath, missing export 'performance' (polyfill)")

    val core: Value = exports.getMember("core")
        ?: error("Error loading Strudel bundle: $esmBundlePath, missing export 'core'")

    val mini: Value = exports.getMember("mini")
        ?: error("Error loading Strudel bundle: $esmBundlePath, missing export 'mini'")

    val tonal: Value = exports.getMember("tonal")
        ?: error("Error loading Strudel bundle: $esmBundlePath, missing export 'tonal'")

    val compileFn: Value = exports.getMember("compile")
        ?: error("Error loading Strudel bundle: $esmBundlePath, missing export 'compile'")

    val queryPatternFn: Value = exports.getMember("queryPattern")
        ?: error("Error loading Strudel bundle: $esmBundlePath, missing export 'queryPattern'")

    val prettyFormatFn = exports.getMember("prettyFormat")
        ?: error("Error loading Strudel bundle: $esmBundlePath, missing export 'prettyFormat'")

    init {
        println(
            """
            Successfully loaded Strudel esm javascript bundle into Graal: 
                $esmBundlePath
            
            Successfully got polyfills:
             - performance: $performance
            
            Successfully got Strudel modules:
             - core: $core
             - mini: $mini
             - tonal: $tonal
             
            Successfully got Strudel functions:
              - compile: $compileFn
              - queryPattern: $queryPatternFn
              
            Successfully got Strudel helpers:
              - prettyFormat: $prettyFormatFn
            
            Let's make some music now ...
        """.trimIndent()
        )
    }

    /**
     * Compiles the given strudel [pattern]
     */
    override fun compile(pattern: String): Deferred<StrudelPattern> {
        val promise = compileFn.execute(pattern)

        return promise.promiseToDeferred {
            GraalStrudelPattern(value = it, graal = this)
        }
    }

    /**
     * Queries events from [pattern] between [from] and [to] cycles.
     */
    fun queryPattern(pattern: Value?, from: Double, to: Double): Value? {
        return queryPatternFn.execute(pattern, from, to)
    }

    /**
     * Formats the given [value] as a pretty string.
     */
    @Suppress("unused")
    fun prettyFormat(value: Any?): Value? {
        return prettyFormatFn.execute(value)
    }

    /**
     * Queries events from the given [pattern] between [from] and [to] cycles and debug dumps them.
     */
    @Suppress("unused")
    fun dumpPatternArc(pattern: Value?, from: Double = 0.0, to: Double = 2.0) {
        pattern?.let {
            println("pattern: $pattern")

            queryPattern(pattern, from, to)?.also {
                val n = it.arraySize
                for (i in 0 until n) {
                    val ev = it.getArrayElement(i)
                    println(ev)
                }
            }
        }
    }

    override fun close() = ctx.close()
}
