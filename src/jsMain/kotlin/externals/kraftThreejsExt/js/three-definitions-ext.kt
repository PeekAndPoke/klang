@file:Suppress("FunctionName", "unused")

package io.peekandpoke.kraft.addons.threejs.js

import org.w3c.dom.HTMLCanvasElement

/**
 * Extensions to the kraft-threejs externals.
 *
 * This file lives in the klang repo temporarily. It mirrors the upstream
 * package `io.peekandpoke.kraft.addons.threejs.js` so it can be migrated
 * into `/opt/dev/peekandpoke/ultra/kraft/addons/threejs/src/jsMain/kotlin/js/`
 * as a straight file move — no renames or imports changes required.
 */

/** 2D vector with x, y. Used for `normalScale`, etc. */
external class Vector2 {
    var x: Double
    var y: Double

    fun set(x: Double, y: Double): Vector2
    fun copy(v: Vector2): Vector2
}

/** Base texture. */
open external class Texture {
    var wrapS: Int
    var wrapT: Int
    var magFilter: Int
    var minFilter: Int
    var needsUpdate: Boolean
    var anisotropy: Double

    fun dispose()
}

/** Texture backed by an HTMLCanvasElement (ImageBitmap/OffscreenCanvas work too via dynamic). */
external class CanvasTexture(canvas: HTMLCanvasElement) : Texture

/** Texture wrapping constants (values taken from three.js). */
object TextureWrapping {
    const val RepeatWrapping: Int = 1000
    const val ClampToEdgeWrapping: Int = 1001
    const val MirroredRepeatWrapping: Int = 1002
}

/** Texture filter constants (values taken from three.js). */
object TextureFilter {
    const val NearestFilter: Int = 1003
    const val NearestMipMapNearestFilter: Int = 1004
    const val NearestMipMapLinearFilter: Int = 1005
    const val LinearFilter: Int = 1006
    const val LinearMipMapNearestFilter: Int = 1007
    const val LinearMipMapLinearFilter: Int = 1008
}
