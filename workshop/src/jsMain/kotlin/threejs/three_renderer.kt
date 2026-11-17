@file:Suppress("FunctionName", "unused")

package io.peekandpoke.klang.workshop.threejs

import io.peekandpoke.kraft.addons.threejs.ThreeJsAddon
import io.peekandpoke.kraft.addons.threejs.js.Texture
import io.peekandpoke.kraft.addons.threejs.js.WebGLRenderer
import org.khronos.webgl.Uint8Array

/**
 * The renderer's members the addon's [WebGLRenderer] lacks (its pixel ratio, what it counts, what the graphics card
 * can do, a picture to draw into), and textures of numbers. Types only, as in the addon: an instance is made
 * through [ThreeJsAddon] (the `create...` functions here).
 */

/** What the renderer counts while it draws: see [WebGLRenderer.info]. */
external interface RendererInfo {
    /** What was drawn since the count was last set back. */
    val render: RenderInfo

    /** Whether the count is set back before each drawing (three.js's default); false leaves that to [reset]. */
    var autoReset: Boolean

    /** Sets the count back to nothing. */
    fun reset()
}

/** What was drawn: how many draw calls, and how many triangles. */
external interface RenderInfo {
    val calls: Int
    val triangles: Int
}

/** The WebGL extensions the graphics card offers. */
external interface WebGLExtensions {
    /** Whether the card offers the extension [name] (`"EXT_color_buffer_float"`). */
    fun has(name: String): Boolean
}

/** What a [WebGLRenderTarget] is made with. A field left out keeps three.js's default. */
external interface RenderTargetOptions {
    /** The numbers its picture holds: a [TextureDataType] value. */
    var type: Int

    /** Whether it has a depth buffer, and a stencil buffer. */
    var depthBuffer: Boolean
    var stencilBuffer: Boolean
}

/** A picture on the graphics card the renderer can draw into, in place of the screen; its [texture] can be read. */
external class WebGLRenderTarget {
    val width: Int
    val height: Int

    /** The picture, as a texture a material can read. */
    val texture: Texture

    /** Frees the picture on the graphics card. */
    fun dispose()
}

/** A block of numbers in three directions, as a texture: [width] by [height] by [depth] of them. */
external class Data3DTexture : Texture {
    /** How the texture repeats along its third direction: a texture wrapping value, as [wrapS] and [wrapT]. */
    var wrapR: Int

    /** What one point of it holds: a [TextureFormat] value. */
    var format: Int

    /** How the rows of its data are aligned, in bytes: 1 (a block's default; a plain picture's is 4), 2, 4 or 8. */
    var unpackAlignment: Int
}

/** The numbers a texture holds (values taken from three.js). */
object TextureDataType {
    const val UnsignedByteType: Int = 1009
    const val HalfFloatType: Int = 1016
}

/** What one point of a texture holds (values taken from three.js). */
object TextureFormat {
    /** One number, red. */
    const val RedFormat: Int = 1028
}

////  MAKING  /////////////////////////////////////////////////////////////////////////////////////////////////////////

/** A picture [width] by [height] points large to draw into. */
fun ThreeJsAddon.createWebGLRenderTarget(width: Int, height: Int, options: RenderTargetOptions): WebGLRenderTarget =
    create("WebGLRenderTarget", width, height, options)

/** A block of numbers from [data], [width] by [height] by [depth] of them; one byte each as made here. */
fun ThreeJsAddon.createData3DTexture(data: Uint8Array, width: Int, height: Int, depth: Int): Data3DTexture =
    create("Data3DTexture", data, width, height, depth)

////  MEMBERS THE ADDON'S RENDERER LACKS  /////////////////////////////////////////////////////////////////////////////

/** How many of the screen's points make one of the page's, as last set with `setPixelRatio`. */
fun WebGLRenderer.getPixelRatio(): Double = asDynamic().getPixelRatio().unsafeCast<Double>()

/** What the renderer counts while it draws. */
val WebGLRenderer.info: RendererInfo
    get() = asDynamic().info.unsafeCast<RendererInfo>()

/** The WebGL extensions the graphics card offers. */
val WebGLRenderer.extensions: WebGLExtensions
    get() = asDynamic().extensions.unsafeCast<WebGLExtensions>()

/** Where the renderer draws to: a picture of one's own, or null for the screen. */
fun WebGLRenderer.getRenderTarget(): WebGLRenderTarget? = asDynamic().getRenderTarget().unsafeCast<WebGLRenderTarget?>()

/** Draws into [target] from now on, or onto the screen where it is null. */
fun WebGLRenderer.setRenderTarget(target: WebGLRenderTarget?) {
    asDynamic().setRenderTarget(target)
}
