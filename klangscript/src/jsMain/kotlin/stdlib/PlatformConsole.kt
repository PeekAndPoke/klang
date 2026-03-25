package io.peekandpoke.klang.script.stdlib

/**
 * JS implementation of console output — routes to the browser console.
 */
actual fun platformConsoleOutput(level: ConsoleLevel, args: List<String>) {
    val message = args.joinToString(" ")
    when (level) {
        ConsoleLevel.LOG -> console.log(message)
        ConsoleLevel.INFO -> console.info(message)
        ConsoleLevel.WARN -> console.warn(message)
        ConsoleLevel.ERROR -> console.error(message)
        ConsoleLevel.DEBUG -> console.log("[DEBUG] $message") // console.debug may be filtered
    }
}
