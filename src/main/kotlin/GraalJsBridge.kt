package io.peekandpoke

import org.graalvm.polyglot.Value

object GraalJsBridge {

    fun Value?.safeNumberOrNull(): Double? {
        if (this == null) return null

        return when {
            isNumber -> asDouble()
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
}
