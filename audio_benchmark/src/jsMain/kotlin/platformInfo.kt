package io.peekandpoke.klang.audio_benchmark

actual fun platformInfo(): String {
    return try {
        val ua = js("navigator.userAgent") as? String ?: "unknown"
        "JS / $ua"
    } catch (_: Throwable) {
        "JS / Node.js"
    }
}
