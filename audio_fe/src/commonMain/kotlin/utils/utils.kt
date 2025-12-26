package io.peekandpoke.klang.audio_fe.utils

val UrlWithProtocolRegex = Regex(
    pattern = "https?://(www\\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_+.~#?&/=]*)",
    options = setOf(RegexOption.IGNORE_CASE),
)

/**
 * Checks if the string is a url with a protocol, e.g. https://...
 */
fun String.isUrlWithProtocol(): Boolean {
    return UrlWithProtocolRegex.matches(this)
}

@Suppress("Detekt.TooGenericExceptionCaught")
inline fun <reified T : Enum<T>> safeEnumOf(input: String?, default: T): T {
    return when (input) {
        null -> default

        else -> try {
            enumValueOf(input)
        } catch (e: RuntimeException) {
            default
        }
    }
}

@Suppress("Detekt.TooGenericExceptionCaught")
inline fun <reified T : Enum<T>> safeEnumOrNull(input: String?): T? {
    return when (input) {
        null -> null

        else -> try {
            enumValueOf(input) as T
        } catch (e: RuntimeException) {
            null
        }
    }
}

