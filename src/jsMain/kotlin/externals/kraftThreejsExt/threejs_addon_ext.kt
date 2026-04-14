package io.peekandpoke.kraft.addons.threejs

import io.peekandpoke.kraft.addons.threejs.js.CanvasTexture
import io.peekandpoke.kraft.addons.threejs.js.Vector2
import org.w3c.dom.HTMLCanvasElement

/**
 * Factory extensions on [ThreeJsAddon] for externals added locally in the klang repo.
 * Mirrors the upstream `threejs_addon.kt` style so these functions can be moved
 * verbatim into the ultra repo when the externals are migrated.
 */

fun ThreeJsAddon.createVector2(x: Double = 0.0, y: Double = 0.0): Vector2 {
    @Suppress("UnusedVariable", "unused", "UNUSED_VARIABLE")
    val ctor = raw.Vector2
    return js("new ctor(x, y)").unsafeCast<Vector2>()
}

fun ThreeJsAddon.createCanvasTexture(canvas: HTMLCanvasElement): CanvasTexture {
    @Suppress("UnusedVariable", "unused", "UNUSED_VARIABLE")
    val ctor = raw.CanvasTexture
    return js("new ctor(canvas)").unsafeCast<CanvasTexture>()
}
