package io.peekandpoke.graal

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable

object GraalJsHelpers {

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
    suspend fun <T> Value.awaitPromise(map: (Value) -> T): T = this.promiseToDeferred(map).await()

    fun Value?.safeNumberOrNull(): Double? {
        if (this == null) return null

        return when {
            isNumber -> asDouble()
            hasMember("valueOf") -> invokeMember("valueOf")?.asDouble()
            else -> null
        }
    }


    fun Value?.safeNumber(default: Double): Double {
        return safeNumberOrNull() ?: default
    }

    fun Value?.safeStringOrNull(): String? {
        if (this == null) return null

        return when {
            isString -> asString()
            else -> null
        }
    }

    fun Value?.safeString(default: String): String {
        return safeStringOrNull() ?: default
    }

    fun Value?.safeToStringOrNull(): String? {
        if (this == null) return null

        return when {
            isString -> asString()
            isNumber -> asDouble().toString()
            // Convert numeric MIDI values (e.g. 40) to string "40" so parsing works
            else -> toString()
        }
    }

    fun Value?.safeToString(default: String): String {
        return safeToStringOrNull() ?: default
    }
}
