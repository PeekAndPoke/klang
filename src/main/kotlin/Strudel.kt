package io.peekandpoke

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import java.nio.file.Path

class Strudel(private val bundlePath: Path) : AutoCloseable {
    val ctx: Context = Context.newBuilder("js")
//        .allowIO(IOAccess.ALL)
        .option("js.esm-eval-returns-exports", "true")
        .build()

    val source = Source.newBuilder("js", bundlePath.toFile())
        .mimeType("application/javascript+module")
        .build()

    val exports = ctx.eval(source)           // module namespace object :contentReference[oaicite:6]{index=6}

    val core = exports.getMember("core")
    val mini = exports.getMember("mini")
    val transpiler = exports.getMember("transpiler")

    val compileFn = exports.getMember("compile")
    val queryPatternFn = exports.getMember("queryPattern")

    val prettyFormatFn = exports.getMember("prettyFormat")

    /**
     * Convert a JavaScript Promise (as Polyglot Value) into a Kotlin Deferred.
     *
     * - If the Promise resolves, the Deferred completes with the mapped result.
     * - If the Promise rejects, the Deferred completes exceptionally.
     */
    fun <T> Value.promiseToDeferred(
        map: (Value) -> T,
    ): Deferred<T> {
        require(this.hasMember("then")) {
            "Value does not look like a Promise/thenable (missing member 'then'): $this"
        }

        val deferred = CompletableDeferred<T>()

        val onFulfilled = ProxyExecutable { args ->
            try {
                val resolved = args.getOrNull(0) ?: throw IllegalStateException("Promise resolved with no value")
                deferred.complete(map(resolved))
            } catch (t: Throwable) {
                deferred.completeExceptionally(t)
            }
            null
        }

        val onRejected = ProxyExecutable { args ->
            val reason = args.getOrNull(0)
            deferred.completeExceptionally(
                RuntimeException(
                    buildString {
                        append("JS Promise rejected")
                        if (reason != null) append(": ").append(reason.toString())
                    }
                )
            )
            null
        }

        // Register callbacks: promise.then(onFulfilled, onRejected)
        this.invokeMember("then", onFulfilled, onRejected)
        return deferred
    }

    /**
     * Convenience: await the JS Promise from a suspending Kotlin context.
     */
    suspend fun <T> Value.awaitPromise(map: (Value) -> T): T =
        this.promiseToDeferred(map).await()

    fun coreHasExecutableMember(name: String): Boolean = core.hasMember(name) && core.getMember(name).canExecute()

    fun compile(code: String): Deferred<Value?> {
        val promise = compileFn.execute(code)

        return promise.promiseToDeferred { it }
    }

    fun queryPattern(pattern: Value?, from: Double, to: Double): Value? {
        return queryPatternFn.execute(pattern, from, to)
    }

    fun prettyFormat(value: Any?): Value? {
        return prettyFormatFn.execute(value)
    }

    fun dumpPatternArc(value: Value?, from: Double = 0.0, to: Double = 2.0) {
        value?.let {
            println("pattern: $value")

            queryPattern(value, from, to)?.also {
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
