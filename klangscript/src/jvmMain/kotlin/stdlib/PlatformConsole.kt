package io.peekandpoke.klang.script.stdlib

/**
 * JVM implementation of console output — routes all levels to stdout.
 */
actual fun platformConsoleOutput(level: ConsoleLevel, args: List<String>) {
    val message = args.joinToString(" ")
    when (level) {
        ConsoleLevel.LOG -> println(message)
        ConsoleLevel.INFO -> println("[INFO] $message")
        ConsoleLevel.WARN -> println("[WARN] $message")
        ConsoleLevel.ERROR -> System.err.println("[ERROR] $message")
        ConsoleLevel.DEBUG -> println("[DEBUG] $message")
    }
}
