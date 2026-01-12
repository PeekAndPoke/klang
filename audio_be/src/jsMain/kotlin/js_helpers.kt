package io.peekandpoke.klang.audio_be

fun jsObject(): dynamic = js("({})")

fun <T> jsObject(block: T.() -> Unit): T {
    val obj = jsObject() as T
    block(obj)
    return obj
}
