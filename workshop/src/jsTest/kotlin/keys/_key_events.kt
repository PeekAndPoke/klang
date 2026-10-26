package io.peekandpoke.klang.workshop.keys

import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit

/** A key event as the browser sends it: [key] is the typed character or the key's name, [code] its position. */
fun keyEvent(
    key: String,
    code: String = "",
    ctrl: Boolean = false,
    alt: Boolean = false,
    shift: Boolean = false,
    meta: Boolean = false,
    repeat: Boolean = false,
    altGraph: Boolean = false,
    composing: Boolean = false,
    keyCode: Int = 0,
    type: String = "keydown",
): KeyboardEvent = KeyboardEvent(
    type,
    KeyboardEventInit(
        isComposing = composing,
        key = key,
        code = code,
        ctrlKey = ctrl,
        altKey = alt,
        shiftKey = shift,
        metaKey = meta,
        repeat = repeat,
        modifierAltGraph = altGraph,
        bubbles = true,
        cancelable = true,
    ),
).also { event ->
    // Kotlin's KeyboardEventInit has no keyCode (a legacy field), so it is set on the event itself
    if (keyCode != 0) {
        val descriptor: dynamic = js("({})")
        descriptor.value = keyCode
        js("Object").defineProperty(event, "keyCode", descriptor)
    }
}
