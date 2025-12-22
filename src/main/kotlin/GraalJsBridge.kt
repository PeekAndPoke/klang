package io.peekandpoke

import org.graalvm.polyglot.Value

object GraalJsBridge {

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

    fun Value?.safeTpStringOrNull(): String? {
        if (this == null) return null

        return when {
            isString -> asString()
            isNumber -> asDouble().toString()
            // Convert numeric MIDI values (e.g. 40) to string "40" so parsing works
            else -> toString()
        }
    }

    fun Value?.safeTpString(default: String): String {
        return safeTpStringOrNull() ?: default
    }
}
