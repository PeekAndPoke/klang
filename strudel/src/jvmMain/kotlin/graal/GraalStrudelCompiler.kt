package io.peekandpoke.klang.strudel.graal

import io.peekandpoke.klang.strudel.StrudelCompiler
import io.peekandpoke.klang.strudel.graal.GraalJsHelpers.promiseToDeferred
import kotlinx.coroutines.Deferred
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import java.net.URL
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class GraalStrudelCompiler(
    bundleUrl: URL = defaultBundle,
    debug: Boolean = false,
) : StrudelCompiler, AutoCloseable {

    companion object {
        val defaultBundle = GraalStrudelCompiler::class.java.classLoader.getResource("strudel-bundle.mjs")
            ?: error("Could not find strudel-entry.mjs in resources")
    }

    private val lock = ReentrantLock()

    val ctx: Context = Context.newBuilder("js")
        .option("js.esm-eval-returns-exports", "true")
        .build()

    val source: Source = run {
        // Read the content of the resource manually
        val bundleContent = bundleUrl.readText()
        val name = bundleUrl.path.substringAfterLast('/')

        // Create the source from the raw text
        Source.newBuilder("js", bundleContent, name)
            .mimeType("application/javascript+module")
            .build()
    }

    val exports: Value = ctx.eval(source)
        ?: error("Error loading Strudel bundle: $bundleUrl")

    val performance: Value = exports.getMember("performance")
        ?: error("Error loading Strudel bundle: $bundleUrl, missing export 'performance' (polyfill)")

    val core: Value = exports.getMember("utils")
        ?: error("Error loading Strudel bundle: $bundleUrl, missing export 'core'")

    val mini: Value = exports.getMember("mini")
        ?: error("Error loading Strudel bundle: $bundleUrl, missing export 'mini'")

    val tonal: Value = exports.getMember("tonal")
        ?: error("Error loading Strudel bundle: $bundleUrl, missing export 'tonal'")

    val compileFn: Value = exports.getMember("compile")
        ?: error("Error loading Strudel bundle: $bundleUrl, missing export 'compile'")

    val queryPatternFn: Value = exports.getMember("queryPattern")
        ?: error("Error loading Strudel bundle: $bundleUrl, missing export 'queryPattern'")

    val prettyFormatFn = exports.getMember("prettyFormat")
        ?: error("Error loading Strudel bundle: $bundleUrl, missing export 'prettyFormat'")

    init {
        if (debug) {
            println(
                """
                    Successfully loaded Strudel esm javascript bundle into Graal: 
                        $bundleUrl
                    
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
    }

    /**
     * Compiles the given strudel [pattern]
     */
    override fun compile(pattern: String): Deferred<GraalStrudelPattern> = lock.withLock {
        val promise = compileFn.execute(pattern)

        return promise.promiseToDeferred {
            GraalStrudelPattern(value = it, graal = this)
        }
    }

    /**
     * Queries events from [pattern] between [from] and [to] cycles.
     */
    fun queryPattern(pattern: Value?, from: Double, to: Double): Value? = lock.withLock {
        return queryPatternFn.execute(pattern, from, to)
    }

    /**
     * Formats the given [value] as a pretty string.
     */
    @Suppress("unused")
    fun prettyFormat(value: Any?): Value? = lock.withLock {
        return prettyFormatFn.execute(value)
    }

    /**
     * Queries events from the given [pattern] between [from] and [to] cycles and debug dumps them.
     */
    @Suppress("unused")
    fun dumpPatternArc(pattern: Value?, from: Double = 0.0, to: Double = 2.0) = lock.withLock {
        pattern?.let {
            println("pattern: $pattern")

            queryPattern(pattern, from, to)?.also {
                val n = it.arraySize
                for (i in 0 until n) {
                    val ev = it.getArrayElement(i)
                    println(
                        prettyFormatFn.execute(ev)
                    )
                }
            }
        }
    }

    /**
     * Queries events from the given [pattern] between [from] and [to] cycles and debug dumps them.
     */
    fun dumpPatternArc(pattern: GraalStrudelPattern, from: Double = 0.0, to: Double = 2.0) = lock.withLock {
        dumpPatternArc(pattern = pattern.value, from = from, to = to)
    }

    override fun close() = ctx.close()
}
