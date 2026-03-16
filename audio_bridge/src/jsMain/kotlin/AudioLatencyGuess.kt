package io.peekandpoke.klang.audio_bridge

import kotlinx.browser.window

/**
 * Guesses the audio output device latency in ms when the browser reports 0.
 *
 * Some browsers (notably Chrome on macOS) do not report Bluetooth or external device latency
 * through AudioContext.outputLatency. This function provides a best-effort fallback based on
 * the detected browser and OS.
 *
 * @param reportedDeviceLatencyMs The value from AudioContext.outputLatency * 1000.
 * @return The reported value if non-zero, otherwise a platform-specific guess.
 */
fun guessDeviceLatencyMs(reportedDeviceLatencyMs: Double): Double {
    if (reportedDeviceLatencyMs > 0.0) return reportedDeviceLatencyMs

    val ua = window.navigator.userAgent

    val isMac = "Macintosh" in ua || "Mac OS" in ua
    val isChrome = "Chrome" in ua && "Edg" !in ua && "OPR" !in ua

    return when {
        // Chrome on macOS reports 0 for Bluetooth; real value is typically 100-200ms
        isMac && isChrome -> 100.0
        // Other unknown cases — conservative guess
        else -> 50.0
    }
}
