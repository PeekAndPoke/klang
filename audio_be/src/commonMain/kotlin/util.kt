package io.peekandpoke.klang.audio_be

// DSP constants (TWO_PI, ONE_OVER_TWELVE) moved to DspUtil.kt

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
