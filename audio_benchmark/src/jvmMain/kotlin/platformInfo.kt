package io.peekandpoke.klang.audio_benchmark

actual fun platformInfo(): String {
    val jvm = System.getProperty("java.version") ?: "unknown"
    val jvmVendor = System.getProperty("java.vendor") ?: ""
    val os = System.getProperty("os.name") ?: "unknown"
    val arch = System.getProperty("os.arch") ?: "unknown"
    val cores = Runtime.getRuntime().availableProcessors()

    // Try to read CPU model from /proc/cpuinfo (Linux) or fall back to arch
    val cpu = try {
        java.io.File("/proc/cpuinfo").useLines { lines ->
            lines.firstOrNull { it.startsWith("model name") }
                ?.substringAfter(":")?.trim()
        }
    } catch (_: Throwable) {
        null
    } ?: arch

    return "JVM $jvm ($jvmVendor) / $os / $cpu (${cores} cores)"
}
