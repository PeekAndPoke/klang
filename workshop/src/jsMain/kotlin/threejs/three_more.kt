@file:Suppress("FunctionName", "unused")

package io.peekandpoke.klang.workshop.threejs

import io.peekandpoke.kraft.addons.threejs.ThreeJsAddon
import io.peekandpoke.kraft.addons.threejs.js.BufferGeometry
import io.peekandpoke.kraft.addons.threejs.js.Color
import io.peekandpoke.kraft.addons.threejs.js.Light
import io.peekandpoke.kraft.addons.threejs.js.Material
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import io.peekandpoke.kraft.addons.threejs.js.Object3D
import io.peekandpoke.kraft.addons.threejs.js.Scene
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import io.peekandpoke.kraft.addons.threejs.js.WebGLRenderer

/**
 * What Kraft's three.js addon does not declare yet and a lit, shaded scene needs: fog, shader materials, points,
 * geometry attributes, the hook into a built-in material's shader, and the missing members of the types it has.
 *
 * As in the addon these are types only: the library is loaded by the addon, and an instance is made through
 * [ThreeJsAddon] (the `create...` functions here), never by a Kotlin constructor.
 *
 * A member the addon's type lacks is an extension here, reading the JS object directly. When this moves into the
 * addon it becomes a plain member of the type.
 *
 * Based on: https://threejs.org/docs/ (r186, the addon's version).
 */

/** Linear fog: what is further than [near] fades into [color], all of it from [far] on. */
external class Fog {
    val color: Color
    var near: Double
    var far: Double
}

/** A geometry's attribute: one value of [itemSize] numbers for every vertex. */
external class BufferAttribute {
    val count: Int
    val itemSize: Int

    /** Set true after writing into the attribute's array, to upload it again. Write-only in three.js. */
    var needsUpdate: Boolean
}

/** What a [ShaderMaterial] is made from. A field left out keeps three.js's default. */
external interface ShaderMaterialParameters {
    /** The uniforms by name, each an object with a `value`; see [uniform]. */
    var uniforms: dynamic
    var vertexShader: String
    var fragmentShader: String
    var transparent: Boolean
    var depthWrite: Boolean
    var depthTest: Boolean

    /** A [MaterialSide][io.peekandpoke.kraft.addons.threejs.js.MaterialSide] value. */
    var side: Int

    /** A [Blending] value; with [Blending.CustomBlending], [blendSrc] and [blendDst] ([BlendFactor]) apply. */
    var blending: Int
    var blendSrc: Int
    var blendDst: Int
}

/** A material drawn by shaders of one's own. */
external class ShaderMaterial : Material {
    /** The uniforms by name, each an object with a `value`: the same objects the parameters gave. */
    val uniforms: dynamic
}

/** Points: one dot for every vertex of its geometry. */
external class Points : Object3D {
    var geometry: BufferGeometry
    var material: dynamic
}

/**
 * What a built-in material hands to [onBeforeCompile]: its shaders' sources, to be changed, and its uniforms, to
 * be added to.
 */
external interface CompiledShader {
    val uniforms: dynamic
    var vertexShader: String
    var fragmentShader: String
}

/** How a material's colour is laid over what is drawn already (values taken from three.js). */
object Blending {
    const val NoBlending: Int = 0
    const val NormalBlending: Int = 1
    const val AdditiveBlending: Int = 2
    const val CustomBlending: Int = 5
}

/** The factors of [Blending.CustomBlending] (values taken from three.js). */
object BlendFactor {
    const val ZeroFactor: Int = 200
    const val OneFactor: Int = 201
    const val SrcAlphaFactor: Int = 204
    const val OneMinusSrcAlphaFactor: Int = 205
}

/** How the renderer maps the scene's light to the screen (values taken from three.js). */
object ToneMapping {
    const val NoToneMapping: Int = 0
    const val LinearToneMapping: Int = 1
    const val ACESFilmicToneMapping: Int = 4
}

/** A shadow map's filtering (values taken from three.js; the soft PCF map went with r186). */
object ShadowMapType {
    const val BasicShadowMap: Int = 0
    const val PCFShadowMap: Int = 1
    const val VSMShadowMap: Int = 3
}

/** A uniform of a [ShaderMaterial] or a [CompiledShader]: an object holding [value]. */
fun uniform(value: Any?): dynamic {
    val holder: dynamic = js("({})")
    holder.value = value

    return holder
}

////  MAKING  /////////////////////////////////////////////////////////////////////////////////////////////////////////

fun ThreeJsAddon.createFog(color: Int, near: Double, far: Double): Fog =
    create("Fog", color, near, far)

fun ThreeJsAddon.createColor(hex: Int): Color =
    create("Color", hex)

fun ThreeJsAddon.createShaderMaterial(parameters: ShaderMaterialParameters): ShaderMaterial =
    create("ShaderMaterial", parameters)

fun ThreeJsAddon.createBufferGeometry(): BufferGeometry =
    create("BufferGeometry")

/** An attribute of [itemSize] numbers a vertex, from [values] in a row. */
fun ThreeJsAddon.createFloat32Attribute(values: Array<Double>, itemSize: Int): BufferAttribute =
    create("Float32BufferAttribute", values, itemSize)

fun ThreeJsAddon.createPoints(geometry: BufferGeometry, material: Material): Points =
    create("Points", geometry, material)

fun ThreeJsAddon.createCircleGeometry(radius: Double, segments: Int = 32): BufferGeometry =
    create("CircleGeometry", radius, segments)

////  MEMBERS THE ADDON'S TYPES LACK  /////////////////////////////////////////////////////////////////////////////////

/** The scene's fog, or null. */
var Scene.fog: Fog?
    get() = asDynamic().fog.unsafeCast<Fog?>()
    set(value) {
        asDynamic().fog = value
    }

/** Whether the node is left out when it lies outside the camera's view: false where a shader moves its vertices. */
var Object3D.frustumCulled: Boolean
    get() = asDynamic().frustumCulled.unsafeCast<Boolean>()
    set(value) {
        asDynamic().frustumCulled = value
    }

fun BufferGeometry.setAttribute(name: String, attribute: BufferAttribute): BufferGeometry {
    asDynamic().setAttribute(name, attribute)

    return this
}

/** Whether the material writes into the depth buffer: false for what is see-through and drawn in layers. */
var Material.depthWrite: Boolean
    get() = asDynamic().depthWrite.unsafeCast<Boolean>()
    set(value) {
        asDynamic().depthWrite = value
    }

/** Whether the scene's fog dims what this material draws. */
var Material.fog: Boolean
    get() = asDynamic().fog.unsafeCast<Boolean>()
    set(value) {
        asDynamic().fog = value
    }

/**
 * Changes the material's shader before it is built: [change] gets the sources and the uniforms. [cacheKey] names
 * the changed program, so three.js does not share it with an unchanged material of the same kind.
 */
fun Material.onBeforeCompile(cacheKey: String, change: (CompiledShader) -> Unit) {
    asDynamic().onBeforeCompile = change
    asDynamic().customProgramCacheKey = { cacheKey }
    needsUpdate = true
}

/** The light the material gives off by itself. */
val MeshStandardMaterial.emissive: Color
    get() = asDynamic().emissive.unsafeCast<Color>()

var MeshStandardMaterial.emissiveIntensity: Double
    get() = asDynamic().emissiveIntensity.unsafeCast<Double>()
    set(value) {
        asDynamic().emissiveIntensity = value
    }

/** A [ToneMapping] value. */
var WebGLRenderer.toneMapping: Int
    get() = asDynamic().toneMapping.unsafeCast<Int>()
    set(value) {
        asDynamic().toneMapping = value
    }

/**
 * A light's shadow, tuned: [bias] moves the shadow off the surface that throws it (against stripes on it), [near]
 * and [far] bound what its camera sees, [mapSize] is the map's width and height in texels.
 */
fun Light.tuneShadow(mapSize: Int? = null, bias: Double? = null, near: Double? = null, far: Double? = null) {
    val shadow: dynamic = asDynamic().shadow ?: return

    mapSize?.let { shadow.mapSize.set(it, it) }
    bias?.let { shadow.bias = it }
    near?.let { shadow.camera.near = it }
    far?.let { shadow.camera.far = it }
}

fun Color.lerp(to: Color, share: Double): Color {
    asDynamic().lerp(to, share)

    return this
}

fun Color.scale(by: Double): Color {
    asDynamic().multiplyScalar(by)

    return this
}

fun Vector3.distanceTo(other: Vector3): Double =
    asDynamic().distanceTo(other).unsafeCast<Double>()

fun Vector3.dot(other: Vector3): Double =
    asDynamic().dot(other).unsafeCast<Double>()
