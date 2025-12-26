package io.peekandpoke.klang.audio_be

import kotlin.math.PI

const val TWO_PI = PI * 2.0

const val ONE_OVER_TWELVE: Double = 1.0 / 12.0

@Suppress("Detekt.TooGenericExceptionCaught")
inline fun <reified T : Enum<T>> safeEnumOrNull(input: String?): T? {
    return when (input) {
        null -> null

        else -> try {
            enumValueOf(input) as T
        } catch (_: Throwable) {
            null
        }
    }
}

