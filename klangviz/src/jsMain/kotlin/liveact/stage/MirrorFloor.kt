/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.disposeTree
import io.peekandpoke.klang.viz.liveact.mat4
import io.peekandpoke.klang.workshop.threejs.CompiledShader
import io.peekandpoke.klang.workshop.threejs.Matrix4
import io.peekandpoke.klang.workshop.threejs.RenderTargetOptions
import io.peekandpoke.klang.workshop.threejs.ShaderMaterialParameters
import io.peekandpoke.klang.workshop.threejs.TextureDataType
import io.peekandpoke.klang.workshop.threejs.Uniform
import io.peekandpoke.klang.workshop.threejs.WebGLRenderTarget
import io.peekandpoke.klang.workshop.threejs.createShaderMaterial
import io.peekandpoke.klang.workshop.threejs.createWebGLRenderTarget
import io.peekandpoke.klang.workshop.threejs.extensions
import io.peekandpoke.klang.workshop.threejs.frustumCulled
import io.peekandpoke.klang.workshop.threejs.getRenderTarget
import io.peekandpoke.klang.workshop.threejs.matrixWorld
import io.peekandpoke.klang.workshop.threejs.onBeforeCompile
import io.peekandpoke.klang.workshop.threejs.set
import io.peekandpoke.klang.workshop.threejs.setRenderTarget
import io.peekandpoke.klang.workshop.threejs.uniform
import io.peekandpoke.klang.workshop.threejs.uniformsOf
import io.peekandpoke.kraft.addons.threejs.js.Camera
import io.peekandpoke.kraft.addons.threejs.js.Mesh
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import io.peekandpoke.kraft.addons.threejs.js.PerspectiveCamera
import io.peekandpoke.kraft.addons.threejs.js.Scene
import io.peekandpoke.kraft.addons.threejs.js.Texture
import io.peekandpoke.kraft.addons.threejs.js.TextureFilter
import io.peekandpoke.kraft.addons.threejs.js.Vector2
import io.peekandpoke.kraft.addons.threejs.js.WebGLRenderer
import io.peekandpoke.kraft.utils.jsObject
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The floor as polished dark stone (`?floor=mirror`, [FloorLook.Mirror]): [floor] takes a material of its own, darker
 * and a little rougher than the plain floor's (the lamps' pools on it softer), that shows the stage mirrored in it,
 * dimmer than the stage itself, blurred and fading the farther what is mirrored stands from the floor, and bent a
 * little by a slow ripple of the surface, so it reads as polished stone and not as a mirror. The ripple tilts the
 * floor's own surface for its light as well, so the eye sees the unevenness that bends the reflection; how much of it
 * there is: [ripple] ([RippleLook], the key b). How blurred the reflection is: [blur] ([BlurLook], the key c); a small
 * blur stays at every level, and near the floor the reflection is crisp. The stone, plain or marble with a few soft
 * veins: [stone] ([StoneLook], the key k).
 *
 * Only the stage mirrors ([StageEdge]: from the fence to an arc a few metres before the audience, fading out over the
 * last half metre); the floor among the audience is plain (the maintainer, 2026-11-20: "The reflection are drawn far
 * into the audience ... reduce the size of what reflects by 1/4 on the whole area but increase the quality by 4x
 * instead").
 *
 * How: each frame, just before the floor is drawn (three.js's `onBeforeRender`, as its own `Reflector` does it), a
 * second camera, the frame's camera mirrored in the floor, draws [scene] into a picture of the screen's own pixels
 * (no more than the page's at a pixel ratio of 2, so not more with the oversampling, and at most [MOST_PIXELS]), with
 * its depth; but only within the box the stage's floor fills on the screen ([areaBox]) and round it as far as the blur
 * reaches, and nothing at all where the camera sees none of the stage. Where that box is large (the camera close over
 * the floor), the mirror draws a smaller share of the picture ([scale], down to half each way), so it never draws
 * more than [BUDGET] of its pixels in a frame. The floor reads the picture where each of its points sees its mirror image;
 * the depth says how far behind the floor that image lies, which sets the blur and the fade. The lamps' shadows are
 * the ones the frame drew just before; the mirror draws none of its own. Nothing stands below the floor, so the
 * camera needs no clipping at the floor.
 *
 * The blur's width and the fade are not read from the depth pixel by pixel: at the edge of a leg before the fence the
 * gap behind the floor jumps from half a metre to several, the blur's width with it, and the mirror image was cut
 * there by a hard, stepped edge that jumped as the players swayed (the maintainer, 2026-11-20: the reflections of the
 * highlights "flicker", which "reads as cheap"). So a small second drawing right after the mirror's turns its depth
 * into a picture of blur widths and fades, half the picture's size each way ([widths], [WIDTHS]), and the floor reads
 * it from its coarse copies: the width changes gradually over some 16 to 32 of the picture's pixels instead.
 *
 * Not mirrored: the floor itself, and what [hide] hides while the mirror draws (`hide(true)`, then `hide(false)`):
 * the stage hides the night sky and the fog that lies on the floor there. The rest is mirrored: the band, the riser,
 * the instruments, the wall or the fence, the haze behind it and the sun, and the crowd (from a camera low at the
 * stage's front looking out, the far, high rows' lights can land in the stage's mirror; they cost little, and
 * outside the box they are not drawn).
 *
 * The underworld (`?underworld=on`, [UnderworldLook]): [underworld] turns the mirror image, as far as its share goes,
 * into the other world: cold blue silver, darker, the ripple stiller, and what is brightest in it (the sun behind the
 * drummer) a cold white moon. A tint in the floor's own shader, no drawing of its own.
 *
 * [use] takes the mirror off the floor and puts it on again (the floor turned over while the show plays): off, the
 * floor has its own material back and the two pictures are freed, but the mirror's material and the widths' drawing
 * are kept, so putting it on again builds nothing. [dispose] gives the floor its own material back and frees the two
 * pictures, the depth, the two materials and the widths' shape; the camera holds nothing on the graphics card.
 */
class MirrorFloor(
    private val floor: Mesh,
    private val scene: Scene,
    private val renderer: WebGLRenderer,
    private val outline: Uniform<Double>,
    private val hide: (Boolean) -> Unit,
) {
    /** The floor's own material, given back by [dispose]. */
    private val plain: dynamic = floor.material

    /** The camera the mirror is drawn with: the frame's camera, mirrored in the floor. */
    private val mirrored: PerspectiveCamera = Three.addon.createPerspectiveCamera(fov = 35.0, aspect = 1.0, near = 0.1, far = 100.0)

    /** The picture's numbers: half floats where the card can draw into them (the light is not capped at 1), else bytes. */
    private val type: Int = if (renderer.extensions.has("EXT_color_buffer_float") || renderer.extensions.has("EXT_color_buffer_half_float")) {
        TextureDataType.HalfFloatType
    } else {
        TextureDataType.UnsignedByteType
    }

    /** The picture the mirror is drawn into, made at the first frame and anew where the screen's size changes. */
    private var picture: WebGLRenderTarget? = null

    /** The blur's widths and the fades ([WIDTHS]), half the picture's size each way: made and freed with [picture]. */
    private var widths: WebGLRenderTarget? = null

    // What the floor's material reads: the picture, the widths' picture, where a place of the world lands on the
    // picture, and the lens's scale across and up. What the widths' drawing reads: the picture's depth, the mirrored
    // camera's place and turn in the world, its near and far, and the lens
    private val image: Uniform<Texture?> = uniform(null)
    private val blurs: Uniform<Texture?> = uniform(null)
    private val depth: Uniform<Texture?> = uniform(null)
    private val toPicture: Uniform<Matrix4> = uniform(mat4())
    private val toWorld: Uniform<Matrix4> = uniform(mirrored.matrixWorld)
    private val range: Uniform<Vector2> = uniform(Three.addon.createVector2(x = 0.1, y = 100.0))
    private val lens: Uniform<Vector2> = uniform(Three.addon.createVector2(x = 1.0, y = 1.0))

    // How much of the picture is drawn this frame ([scale]): the share of it across and up the floor reads, and those
    // pixels, which the widths' drawing reads the depth of
    private val drawnShare: Uniform<Vector2> = uniform(Three.addon.createVector2(x = 1.0, y = 1.0))
    private val drawnPixels: Uniform<Vector2> = uniform(Three.addon.createVector2(x = 1.0, y = 1.0))

    /**
     * How much of the picture is drawn each way, 0.5 to 1: less where the mirroring area fills much of the screen, so
     * the mirror never draws more than [BUDGET] of the picture's pixels; it follows over some frames, never at once.
     */
    private var scale = 1.0

    /** The mirroring area's box on the screen this frame, -1 to 1 each way ([areaBox]): left, bottom, right, top. */
    private val box = DoubleArray(4)

    /** The area's corners, and as far as they lie before the camera, in the camera's view ([areaBox]). */
    private val corners = DoubleArray(12)
    private val clipped = DoubleArray(24)

    /** How far the mirror shows the underworld, 0 (the stage) to 1 ([underworld]). */
    private val under: Uniform<Double> = uniform(0.0)

    /** How much the stone's broad and its finer ripple bend the image and tilt the floor ([ripple]). */
    private val broadRipple: Uniform<Double> = uniform(RippleLook.Gentle.broad)
    private val fineRipple: Uniform<Double> = uniform(RippleLook.Gentle.fine)

    /** How wide the blur grows with the gap behind the floor ([blur], [BlurLook.cone]). */
    private val cone: Uniform<Double> = uniform(BlurLook.Crisp.cone)

    /** How much the floor is marble, 0 (plain) or 1 ([stone]). */
    private val marble: Uniform<Double> = uniform(0.0)

    /** Whether the mirror is on the floor now ([use]). */
    private var worn = false

    private val material: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(jsObject {
        color = 0x050609
        roughness = 0.5
        metalness = 0.15
    })

    /** The widths' drawing ([WIDTHS]): one shape over the whole picture, in a scene of its own. */
    private val widthsScene: Scene = Three.addon.createScene()

    init {
        val widthsMaterial = Three.addon.createShaderMaterial(jsObject<ShaderMaterialParameters> {
            uniforms = uniformsOf(
                "uMirrorDepth" to depth,
                "uMirrorWorld" to toWorld,
                "uMirrorRange" to range,
                "uMirrorLens" to lens,
                "uMirrorDrawn" to drawnPixels,
            )
            vertexShader = WIDTHS_VERTEX
            fragmentShader = WIDTHS
            depthTest = false
            depthWrite = false
        })
        val widthsShape = Three.addon.createMesh(Three.addon.createPlaneGeometry(2.0, 2.0), widthsMaterial)

        // the shape is placed by its shader, over the whole picture, whatever the camera
        widthsShape.frustumCulled = false
        widthsScene.add(widthsShape)

        material.onBeforeCompile("floor-mirror") { shader: CompiledShader ->
            shader.uniforms["uMirror"] = image
            shader.uniforms["uMirrorWidths"] = blurs
            shader.uniforms["uMirrorPicture"] = toPicture
            shader.uniforms["uMirrorLens"] = lens
            shader.uniforms["uMirrorScale"] = drawnShare
            shader.uniforms["uMirrorUnder"] = under
            shader.uniforms["uRippleBroad"] = broadRipple
            shader.uniforms["uRippleFine"] = fineRipple
            shader.uniforms["uMirrorCone"] = cone
            shader.uniforms["uMarble"] = marble
            shader.vertexShader = shader.vertexShader
                .replaceFirst("#include <common>", "#include <common>\nvarying vec3 vFloorWorld;")
                .replaceFirst(
                    "#include <begin_vertex>",
                    "#include <begin_vertex>\nvFloorWorld = (modelMatrix * vec4(transformed, 1.0)).xyz;",
                )
            shader.fragmentShader = shader.fragmentShader
                .replaceFirst("#include <common>", "#include <common>\n$MIRRORED")
                // the marble, worked out once here, in the stone's colour and in its sheen
                .replaceFirst(
                    "#include <color_fragment>",
                    "#include <color_fragment>\nmarbleAt(vFloorWorld);\ndiffuseColor.rgb = marbled(diffuseColor.rgb);",
                )
                .replaceFirst(
                    "#include <roughnessmap_fragment>",
                    "#include <roughnessmap_fragment>\nroughnessFactor = marbleRough(roughnessFactor);",
                )
                // the stone's own surface as uneven as the ripple that bends its mirror image
                .replaceFirst("#include <normal_fragment_maps>", "#include <normal_fragment_maps>\nnormal = floorTilted(vFloorWorld);")
                .replaceFirst(
                    "#include <opaque_fragment>",
                    // the lamps' pools softer, the mirror image over them
                    "outgoingLight = underFloor(outgoingLight * 0.3) + mirrored(vFloorWorld);\n#include <opaque_fragment>",
                )
            // the stage's edge (the mirror's reach) and its outline, over the mirror's image
            outlineOn(shader = shader, outline = outline, placed = true)
        }

        use(true)
    }

    /**
     * Puts the mirror on the floor ([on]) or takes it off: off, the floor wears its own material and draws nothing
     * before itself, and the two pictures are freed; the two materials are kept for the next time.
     */
    fun use(on: Boolean) {
        if (on == worn) {
            return
        }

        worn = on

        if (on) {
            floor.material = material
            floor.asDynamic().onBeforeRender = { _: dynamic, _: dynamic, camera: Camera -> draw(camera) }

            return
        }

        floor.material = plain
        // the floor draws nothing before itself again: three.js's own (empty) onBeforeRender is the prototype's
        val mesh: dynamic = floor

        js("delete mesh.onBeforeRender")
        free()
    }

    /** The stone made as uneven as [to]: the mirror image bent and the floor's own surface tilted by its ripple. */
    fun ripple(to: RippleLook) {
        broadRipple.value = to.broad
        fineRipple.value = to.fine
    }

    /** The reflection blurred as [to]: how wide the blur grows with the gap behind the floor. */
    fun blur(to: BlurLook) {
        cone.value = to.cone
    }

    /** The floor's stone turned to [to]: plain, or marble with a few soft veins. */
    fun stone(to: StoneLook) {
        marble.value = if (to == StoneLook.Marble) 1.0 else 0.0
    }

    /** How far the mirror shows the underworld this frame: [share] from 0 (the stage as it is) to 1. */
    fun underworld(share: Double) {
        under.value = share.coerceIn(0.0, 1.0)
    }

    /** Draws the mirror for the frame seen by [camera], before the floor is drawn. */
    private fun draw(camera: Camera) {
        val lensOf = camera.unsafeCast<PerspectiveCamera>()
        val eye = camera.matrixWorld.elements

        // a camera below the floor sees no mirror image in it
        if (eye[13] <= 0.01) {
            return
        }

        // where nothing of the mirroring area is seen, nothing is mirrored
        if (!areaBox(camera)) {
            return
        }

        val target = pictureFor(screen = renderer.domElement)
        val blurTarget = widths ?: return

        // the camera mirrored in the floor (y = 0): its place, where it looks and which way is up, each upside down
        mirrored.position.set(eye[12], -eye[13], eye[14])
        mirrored.asDynamic().up.set(eye[4], -eye[5], eye[6])
        mirrored.lookAt(eye[12] - eye[8], -eye[13] + eye[9], eye[14] - eye[10])
        mirrored.near = lensOf.near
        mirrored.far = lensOf.far
        mirrored.asDynamic().updateMatrixWorld()

        val projection: Matrix4 = mirrored.asDynamic().projectionMatrix.unsafeCast<Matrix4>()
        val inverse: Matrix4 = mirrored.asDynamic().matrixWorldInverse.unsafeCast<Matrix4>()

        projection.copy(camera.asDynamic().projectionMatrix.unsafeCast<Matrix4>())
        mirrored.asDynamic().projectionMatrixInverse.copy(camera.asDynamic().projectionMatrixInverse)

        // from the world to the picture: the camera, its lens, and from -1..1 to 0..1
        toPicture.value.asDynamic().set(
            0.5, 0.0, 0.0, 0.5,
            0.0, 0.5, 0.0, 0.5,
            0.0, 0.0, 0.5, 0.5,
            0.0, 0.0, 0.0, 1.0,
        )
        toPicture.value.multiply(projection).multiply(inverse)
        range.value.set(lensOf.near, lensOf.far)
        lens.value.set(projection.elements[0], projection.elements[5])
        drawOnly(target = target, blurTarget = blurTarget)

        val drawnTo = renderer.getRenderTarget()
        val shadows: dynamic = renderer.shadowMap
        val shadowsUpdate = shadows.autoUpdate

        floor.visible = false
        hide(true)
        // the lamps' shadows were drawn for this frame just before: the mirror reads them as they are
        shadows.autoUpdate = false

        try {
            renderer.setRenderTarget(target)
            // what was drawn see-through last may have left the depth unwritable, and the picture must be cleared
            renderer.asDynamic().state.buffers.depth.setMask(true)

            if (renderer.asDynamic().autoClear == false) {
                renderer.asDynamic().clear()
            }

            renderer.render(scene, mirrored)
            // the blur's widths from the depth just drawn (the shape covers the picture; nothing to clear)
            renderer.setRenderTarget(blurTarget)
            renderer.render(widthsScene, mirrored)
        } finally {
            renderer.setRenderTarget(drawnTo)
            shadows.autoUpdate = shadowsUpdate
            hide(false)
            floor.visible = true
        }
    }

    /**
     * Draws only what the floor can read this frame: the mirroring area's box ([box]) and round it as far as the blur
     * and the ripple reach, in [target] and (half each way) in [blurTarget]; and of the picture as much as [scale]
     * says, which falls toward 0.5 where the box is large.
     */
    private fun drawOnly(target: WebGLRenderTarget, blurTarget: WebGLRenderTarget) {
        // round the box: as far as the blur reaches at its widest (its cone at a gap as deep as the distance, its five
        // reads and the picture's smaller copy they read), and the ripple (from -1..1, twice the picture's share)
        val reach = 2.0 * min(cone.value, 0.1) + 2.0 * BEND_REACH
        val left = (box[0] - reach * lens.value.x).coerceIn(-1.0, 1.0)
        val bottom = (box[1] - reach * lens.value.y).coerceIn(-1.0, 1.0)
        val right = (box[2] + reach * lens.value.x).coerceIn(-1.0, 1.0)
        val top = (box[3] + reach * lens.value.y).coerceIn(-1.0, 1.0)
        // the share of the picture the box covers, and so how much of the picture can be drawn at most
        val share = max((right - left) * (top - bottom) / 4.0, 1e-6)

        scale += (sqrt(BUDGET / share).coerceIn(0.5, 1.0) - scale) * FOLLOW

        val wide = max(1, (target.width * scale).roundToInt())
        val tall = max(1, (target.height * scale).roundToInt())

        drawnShare.value.set(wide.toDouble() / target.width, tall.toDouble() / target.height)
        drawnPixels.value.set(wide.toDouble(), tall.toDouble())
        box[0] = left
        box[1] = bottom
        box[2] = right
        box[3] = top
        area(target = target, wide = wide, tall = tall, margin = PIXEL_MARGIN)
        area(target = blurTarget, wide = (wide + 1) / 2, tall = (tall + 1) / 2, margin = PIXEL_MARGIN / 2)
    }

    /**
     * Sets [target] to draw into its [wide] by [tall] pixels at the bottom left, and only within [box] there, [margin]
     * pixels round it (for the widths' coarse copies, the least blur and the picture's smaller copies).
     */
    private fun area(target: WebGLRenderTarget, wide: Int, tall: Int, margin: Int) {
        val x0 = max(0, floor((box[0] * 0.5 + 0.5) * wide).toInt() - margin)
        val y0 = max(0, floor((box[1] * 0.5 + 0.5) * tall).toInt() - margin)
        val x1 = min(wide, ceil((box[2] * 0.5 + 0.5) * wide).toInt() + margin)
        val y1 = min(tall, ceil((box[3] * 0.5 + 0.5) * tall).toInt() + margin)
        val t: dynamic = target

        t.viewport.set(0, 0, wide, tall)
        t.scissor.set(x0, y0, max(1, x1 - x0), max(1, y1 - y0))
        t.scissorTest = true
    }

    /**
     * The mirroring area's box on the screen of [camera] ([box], -1 to 1 each way), from its bounds on the floor: as
     * far as they lie before the camera (cut at its near plane); false where none of it does or the box lies off the
     * screen.
     */
    private fun areaBox(camera: Camera): Boolean {
        val view = camera.asDynamic().matrixWorldInverse.elements
        val lensOf = camera.asDynamic().projectionMatrix.elements
        val near = -camera.unsafeCast<PerspectiveCamera>().near

        // the four corners in the camera's view (x, y, z each)
        for (i in 0 until 4) {
            val x = if (i == 1 || i == 2) StageEdge.HALF_X else -StageEdge.HALF_X
            val z = if (i >= 2) StageEdge.HALF_Z else StageEdge.BACK

            for (k in 0 until 3) {
                corners[i * 3 + k] = view[k] * x + view[8 + k] * z + view[12 + k]
            }
        }

        // the outline cut at the near plane: what lies before it, and where an edge crosses it
        var n = 0

        for (i in 0 until 4) {
            val a = i * 3
            val b = ((i + 1) % 4) * 3
            val za = corners[a + 2]
            val zb = corners[b + 2]

            if (za < near) {
                clipped[n * 3] = corners[a]
                clipped[n * 3 + 1] = corners[a + 1]
                clipped[n * 3 + 2] = za
                n++
            }

            if ((za < near) != (zb < near)) {
                val t = (near - za) / (zb - za)

                for (k in 0 until 3) {
                    clipped[n * 3 + k] = corners[a + k] + t * (corners[b + k] - corners[a + k])
                }

                n++
            }
        }

        if (n == 0) {
            return false
        }

        box[0] = 1.0
        box[1] = 1.0
        box[2] = -1.0
        box[3] = -1.0

        for (i in 0 until n) {
            val x = clipped[i * 3]
            val y = clipped[i * 3 + 1]
            val z = clipped[i * 3 + 2]
            val w = lensOf[3] * x + lensOf[7] * y + lensOf[11] * z + lensOf[15]
            val sx = (lensOf[0] * x + lensOf[4] * y + lensOf[8] * z + lensOf[12]) / w
            val sy = (lensOf[1] * x + lensOf[5] * y + lensOf[9] * z + lensOf[13]) / w

            box[0] = min(box[0], sx)
            box[1] = min(box[1], sy)
            box[2] = max(box[2], sx)
            box[3] = max(box[3], sy)
        }

        box[0] = box[0].coerceAtLeast(-1.0)
        box[1] = box[1].coerceAtLeast(-1.0)
        box[2] = box[2].coerceAtMost(1.0)
        box[3] = box[3].coerceAtMost(1.0)

        return box[0] < box[2] && box[1] < box[3]
    }

    /**
     * The picture for [screen]: its own pixels, but no more than the page's at a pixel ratio of at most 2 (so it does
     * not grow with the oversampling) nor than [MOST_PIXELS]; made anew where that changed, and the widths' picture
     * (half each way) with it.
     */
    private fun pictureFor(screen: dynamic): WebGLRenderTarget {
        val shown: dynamic = screen
        var wide = max(1, shown.width.unsafeCast<Int>())
        var tall = max(1, shown.height.unsafeCast<Int>())
        val page = (shown.clientWidth ?: 0).unsafeCast<Double>()
        val ratio = (kotlinx.browser.window.devicePixelRatio).coerceIn(1.0, 2.0)

        // (a canvas laid out nowhere, as in a test, has no page size: its own pixels then)
        if (page > 0.0 && page * ratio < wide) {
            val k = page * ratio / wide

            wide = max(1, (wide * k).roundToInt())
            tall = max(1, (tall * k).roundToInt())
        }

        if (wide.toDouble() * tall > MOST_PIXELS) {
            val k = sqrt(MOST_PIXELS / (wide.toDouble() * tall))

            wide = max(1, (wide * k).toInt())
            tall = max(1, (tall * k).toInt())
        }

        val now = picture

        if (now != null && now.width == wide && now.height == tall) {
            return now
        }

        free()

        val options = jsObject<RenderTargetOptions> {
            type = this@MirrorFloor.type
            depthBuffer = true
            stencilBuffer = false
        }

        options.asDynamic().depthTexture = Three.addon.create<dynamic>("DepthTexture", wide, tall)
        // the blur reads the picture's smaller copies, which three.js makes after each drawing into it
        options.asDynamic().generateMipmaps = true
        options.asDynamic().minFilter = TextureFilter.LinearMipmapLinearFilter

        val made = Three.addon.createWebGLRenderTarget(width = wide, height = tall, options = options)

        picture = made
        image.value = made.texture
        depth.value = made.asDynamic().depthTexture.unsafeCast<Texture>()

        val widthsOptions = jsObject<RenderTargetOptions> {
            type = this@MirrorFloor.type
            depthBuffer = false
            stencilBuffer = false
        }

        // the floor reads the widths from their smaller copies, so they change gradually
        widthsOptions.asDynamic().generateMipmaps = true
        widthsOptions.asDynamic().minFilter = TextureFilter.LinearMipmapLinearFilter

        val blur = Three.addon.createWebGLRenderTarget(width = (wide + 1) / 2, height = (tall + 1) / 2, options = widthsOptions)

        widths = blur
        blurs.value = blur.texture

        return made
    }

    /** Frees the picture, its depth and the widths' picture, where there are. */
    private fun free() {
        widths?.dispose()
        widths = null
        blurs.value = null

        val old = picture ?: return

        picture = null
        image.value = null
        depth.value = null
        old.asDynamic().depthTexture?.dispose()
        old.dispose()
    }

    /** Gives the floor its own material back and frees what the mirror holds. The mirror is gone after it; call it once. */
    fun dispose() {
        use(false)
        material.dispose()
        widthsScene.disposeTree()
    }

    private companion object {
        /** The most pixels the picture holds (its memory): a 1920 by 1200 screen's. */
        const val MOST_PIXELS = 1920.0 * 1200.0

        /** The share of the picture's pixels the mirror draws at most in a frame ([scale]), and how fast it follows. */
        const val BUDGET = 0.5
        const val FOLLOW = 0.1

        /** How far the ripple and the marble bend the image at most, as a share of the picture. */
        const val BEND_REACH = 0.006

        /** The pixels drawn round the box ([area]): the widths' coarse copy, the least blur, the smaller copies. */
        const val PIXEL_MARGIN = 32

        /** The widths' picture's copies the floor reads the gap from: the gradual one, and the one near the floor. */
        const val WIDE_LOD = 3.5
        const val HERE_LOD = 2.0

        /** The widths' shape over the whole picture, whatever the camera. */
        val WIDTHS_VERTEX = """
void main() {
  gl_Position = vec4(position.xy, 0.0, 1.0);
}"""

        /**
         * The widths' picture: for each of its pixels, of the 2 by 2 of the mirror's picture under it, the mean of the
         * gap over the distance (0 to 1, so it fits bytes too; the floor's shader makes the blur's width of it with the
         * cone of [BlurLook]) and of the fade (`exp(-gap / 3)`, 1 at the floor). The gap is the distance along the view
         * from the floor to what the depth there shows; where the view meets the floor, from the mirrored camera's
         * place and turn in the world, so no place of the floor is needed.
         */
        val WIDTHS = """
uniform sampler2D uMirrorDepth;
uniform mat4 uMirrorWorld;
uniform vec2 uMirrorRange, uMirrorLens, uMirrorDrawn;
vec2 blurAt(ivec2 p, vec2 size) {
  float n = uMirrorRange.x, f = uMirrorRange.y;
  float hit = n * f / (f - (f - n) * texelFetch(uMirrorDepth, p, 0).x);
  // the view's ray through this pixel, at a distance of 1 along the view, and how fast it rises in the world
  vec2 ndc = (vec2(p) + 0.5) / size * 2.0 - 1.0;
  vec3 ray = vec3(ndc / uMirrorLens, -1.0);
  float rise = dot(vec3(uMirrorWorld[0].y, uMirrorWorld[1].y, uMirrorWorld[2].y), ray);
  // where it meets the floor (y = 0) from the camera below it; a ray that never rises to the floor is no part of the
  // mirror image (and is no division by nought): no gap there
  float near = rise > 1e-5 ? -uMirrorWorld[3].y / rise : hit;
  float gap = max(hit - near, 0.0);

  return vec2(min(gap / max(hit, 0.1), 1.0), exp(-gap / 3.0));
}
void main() {
  // the pixels of the depth drawn this frame (as much of the picture as its scale says)
  ivec2 size = ivec2(uMirrorDrawn);
  ivec2 last = size - 1;
  ivec2 at = ivec2(gl_FragCoord.xy) * 2;
  vec2 s = vec2(size);
  vec2 sum = blurAt(min(at, last), s) + blurAt(min(at + ivec2(1, 0), last), s)
    + blurAt(min(at + ivec2(0, 1), last), s) + blurAt(min(at + ivec2(1, 1), last), s);
  gl_FragColor = vec4(0.25 * sum, 0.0, 1.0);
}"""

        /**
         * The mirror image at a place of the floor (`world`), as the floor adds it to its own light. The picture is
         * read where the place lands on it, bent a little by the stone's ripple ([RippleLook], `mirrorBend`; by default
         * a broad one, some 2 m from crest to crest, a quarter of the strength the floor was first made with: a fence
         * post's reflection stays a straight line that sways gently; the maintainer, 2026-11-20: the finer ripple bent
         * it into zigzags that shimmered as the camera moved, "the unevenness of the floor is too much"). The same
         * ripple tilts the floor's own surface for its light (`floorTilted`: 200 times the broad ripple's bend, some
         * 9 degrees at most by default, 60 times the finer one's), so the bent reflection reads as caused by the
         * stone; how far behind the floor the image lies there (the gap, in metres along the view) sets the blur and
         * the fade, both read from the widths' picture ([WIDTHS]) at a coarse copy, so they change gradually; near the
         * floor (a gap of a few centimetres per metre of distance) the gap is read at the widths' picture's own pixels
         * instead, so what stands on the floor meets its reflection crisply. The farther, the wider the image is
         * blurred (a cone of [BlurLook.cone] of the gap, seen from the camera, read from the picture's smaller copies
         * so a thin string does not break into dots), never less than 3 of the picture's pixels (`MIRROR_LEAST`:
         * at the screen's own pixels the least at which thin things, the trumpet, the strings, the stands, crawl no
         * more for their detail than they did in the half-size picture before, and show some 30 % more of it),
         * and the more it fades (to 0.7, over some 3 m). The blur's five reads each
         * count by their brightness, less the brighter (after Karis), so a small glint or an eye does not swell and
         * pulse as one of them catches it and the next frame not. Then the stone's own
         * share: 0.45 looking down on it, up to 0.8 at a glancing angle (after Schlick's Fresnel; it was 0.6 to 1, the
         * maintainer, 2026-11-20: "a little less strong / bright"); none far out
         * among the audience. The floor's own light (the lamps' pools) is turned down to 0.3 under it.
         *
         * The marble ([StoneLook.Marble], `marbleAt`): the level lines of a warped noise, some metres long, thinned out
         * by a slow noise, are the veins; a little lighter than the stone (`marbled`), a little rougher, and standing a
         * hair proud of it, so their flanks tilt the surface and kink a reflection that crosses them, as the ripple
         * does; over a faint cloudy variation. Filtered by the pixel's footprint (`fwidth`): far off a vein grows no
         * thinner than a pixel and fades instead, so it does not shimmer as the camera moves.
         *
         * The underworld, as far as `uMirrorUnder` goes: the ripple a quarter as strong (stiller), and the image's
         * light turned to moonlight: its brightness kept, its colour a cold blue silver, at 0.75 of it (darker), over a
         * faint cold mist where nothing is mirrored; what is brightest goes toward a cold white, so the sun behind the
         * drummer is a white moon in it. The floor's own light under it leans cold and darker too (`underFloor`).
         */
        val MIRRORED = """
uniform sampler2D uMirror, uMirrorWidths;
uniform mat4 uMirrorPicture;
uniform vec2 uMirrorLens, uMirrorScale;
uniform float uMirrorUnder, uRippleBroad, uRippleFine, uMirrorCone, uMarble;
varying vec3 vFloorWorld;
const float WIDE_LOD = ${glsl(WIDE_LOD)}, HERE_LOD = ${glsl(HERE_LOD)}, STAGE_FADE = ${glsl(StageEdge.FADE)};
const float BROAD_TILT = 200.0, FINE_TILT = 60.0;
// the least blur, in the picture's pixels, at every level and also at the floor: the picture's pixels do not show on
// thin things (the trumpet, the strings, the stands, the fence's wires), nor crawl as they move (measured 2026-11-20:
// 1 to 2 pixels crawled more for their detail than the half-size picture did before; 3 no more)
const float MIRROR_LEAST = 3.0;
// the marble's veins: how wide in the field (some 3 cm on the floor), how far they stand proud (tilting their flanks by
// a degree or two), how much that bends the mirror image (a kink of about half a pixel of the picture where a
// reflection crosses a vein), and the colour they lighten the stone toward (linear; the stone itself is near 0.002)
const float MARBLE_VEIN = 0.012, MARBLE_RAISE = 0.0004, MARBLE_BEND = 0.05;
const vec3 MARBLE_LIGHT = vec3(0.010, 0.010, 0.012);
const vec2 MIRROR_TAPS[4] = vec2[4](vec2(0.7, 0.2), vec2(-0.2, 0.7), vec2(-0.7, -0.2), vec2(0.2, -0.7));
float mirrorNoise(vec2 p) {
  vec2 i = floor(p), f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  float a = fract(sin(dot(i, vec2(12.9898, 78.233))) * 43758.5453);
  float b = fract(sin(dot(i + vec2(1.0, 0.0), vec2(12.9898, 78.233))) * 43758.5453);
  float c = fract(sin(dot(i + vec2(0.0, 1.0), vec2(12.9898, 78.233))) * 43758.5453);
  float d = fract(sin(dot(i + vec2(1.0, 1.0), vec2(12.9898, 78.233))) * 43758.5453);
  return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
// the floor's own light (the lamps' pools) in the underworld: colder and darker as well
// the marble at this place of the floor, worked out once (marbleAt) for its colour, its sheen, its surface and the
// mirror: how much of a vein lies here (0 to 1), the faint cloudy variation (-1 to 1), and how the veins, standing a
// hair proud of the stone, tilt the surface here (along x and z)
float marbleVein = 0.0;
float marbleCloud = 0.0;
vec2 marbleSlope = vec2(0.0);
// a warped noise, three layers, its place pushed by another noise, so that its level lines run long and meander
float marbleField(vec2 p) {
  vec2 q = p + 1.7 * vec2(mirrorNoise(p * 0.21 + 3.1), mirrorNoise(p * 0.21 + 9.7));
  return 0.55 * mirrorNoise(q * 0.32) + 0.3 * mirrorNoise(q * 0.71 + 5.3) + 0.15 * mirrorNoise(q * 1.6 + 11.0);
}
void marbleAt(vec3 world) {
  if (uMarble <= 0.0) {
    return;
  }
  vec2 p = world.xz;
  float f = marbleField(p);
  // a vein where the field passes 0.5, some centimetres wide; never thinner than the field changes over a pixel, and
  // as much fainter as it is wider then, so far off, where a pixel covers much floor, it fades instead of shimmering
  float wide = max(MARBLE_VEIN, 1.5 * fwidth(f));
  float x = abs(f - 0.5);
  float line = 1.0 - smoothstep(0.0, wide, x);
  // only some stretches of the level line are veins: a slow noise thins them out
  float share = smoothstep(0.45, 0.75, mirrorNoise(p * 0.18 + 31.0)) * (MARBLE_VEIN / wide) * uMarble;
  marbleVein = line * share;
  marbleCloud = (2.0 * mirrorNoise(p * 0.6 + 47.0) - 1.0) * uMarble;
  // the field's slope on the floor, from how it and the place change from pixel to pixel (no second noise needed)
  vec2 dx = dFdx(p), dy = dFdy(p);
  float det = dx.x * dy.y - dx.y * dy.x;
  vec2 df = vec2(dFdx(f), dFdy(f));
  // (a pixel that sees no floor across it has no slope: no division by nought)
  vec2 grad = abs(det) > 1e-12 ? vec2(df.x * dy.y - dx.y * df.y, dx.x * df.y - df.x * dy.x) / det : vec2(0.0);
  // the vein's height falls off to both sides (as the smoothstep does), so its flanks tilt the surface
  float fall = x < wide ? 6.0 * x * (wide - x) / (wide * wide * wide) : 0.0;
  marbleSlope = -MARBLE_RAISE * fall * sign(f - 0.5) * grad * share;
}
// the stone's sheen with the marble: a little rougher along a vein, a little smoother or rougher with the clouds
float marbleRough(float r) {
  return clamp(r + 0.12 * marbleVein - 0.04 * marbleCloud, 0.04, 1.0);
}
// the stone's own colour with the marble: the veins a little lighter, the stone a little cloudy; still near black
vec3 marbled(vec3 c) {
  return mix(c * (1.0 + 0.25 * marbleCloud), MARBLE_LIGHT, 0.5 * marbleVein);
}
vec3 underFloor(vec3 c) {
  float lum = dot(c, vec3(0.2126, 0.7152, 0.0722));
  return mix(c, lum * vec3(0.6, 0.8, 1.3), 0.7 * uMirrorUnder) * (1.0 - 0.35 * uMirrorUnder);
}
// the stone is not quite flat: its ripple at a place of the floor, weighed by [broad] and [fine] (each about -0.5 to
// 0.5 times them), a broad ripple, some 2 m from crest to crest, and where asked the finer one the floor was first made
// with; the underworld stills both
vec2 mirrorRipple(vec3 world, float broad, float fine) {
  vec2 r = (vec2(mirrorNoise(world.xz * 0.45), mirrorNoise(world.xz * 0.45 + 17.0)) - 0.5) * broad;
  if (fine > 0.0) {
    vec2 f = vec2(mirrorNoise(world.xz * 1.3), mirrorNoise(world.xz * 1.3 + 17.0)) - 0.5;
    f += 0.5 * (vec2(mirrorNoise(world.xz * 4.1 + 5.0), mirrorNoise(world.xz * 4.1 + 23.0)) - 0.5);
    r += f * fine;
  }
  return r * (1.0 - 0.75 * uMirrorUnder);
}
// how far the ripple bends the image (as a share of the picture)
vec2 mirrorBend(vec3 world) {
  return mirrorRipple(world, uRippleBroad, uRippleFine) + marbleSlope * MARBLE_BEND * (1.0 - 0.75 * uMirrorUnder);
}
// the floor's own surface (its normal, as the view sees it) tilted by the same ripple, so the lamps' pools and the
// sheen show the unevenness that bends the reflection; the broad ripple tilts it more for its bend than the finer one,
// or its slow swell would not show at all
vec3 floorTilted(vec3 world) {
  vec2 tilt = mirrorRipple(world, uRippleBroad * BROAD_TILT, uRippleFine * FINE_TILT);
  tilt += marbleSlope * (1.0 - 0.75 * uMirrorUnder);
  return normalize((viewMatrix * vec4(normalize(vec3(tilt.x, 1.0, tilt.y)), 0.0)).xyz);
}
// how much one of the blur's reads counts: its share, less the brighter it is
float mirrorWeight(vec3 c, float share) {
  return share / (1.0 + dot(c, vec3(0.2126, 0.7152, 0.0722)));
}
// how much of the mirror the floor shows at a place: all of it on the stage (StageEdge), fading out over the last
// half metre to its edge, and none beyond (so a place there reads nothing of the picture, which is drawn there no more)
float mirrorArea(vec3 world) {
  return smoothstep(0.0, STAGE_FADE, stageEdge(world.xz));
}
vec3 mirrored(vec3 world) {
  float reach = mirrorArea(world);
  if (reach <= 0.0) {
    return vec3(0.0);
  }
  vec4 at = uMirrorPicture * vec4(world, 1.0);
  vec2 uv = at.xy / at.w;
  uv += mirrorBend(world);
  // the picture is drawn in its bottom left share (uMirrorScale) this frame: where the screen's place lies on it
  vec2 pic = uv * uMirrorScale;
  // the gap over the distance and the fade, from the widths' picture's coarse copy (some 11 of its pixels, 22 of the
  // picture's): they change gradually across an edge
  vec2 blurs = textureLod(uMirrorWidths, pic, WIDE_LOD).xy;
  // near the floor (the gap small over 4 by 4 of the widths' picture's pixels, 8 by 8 of the picture's) the gap as it
  // is there, so what stands on the floor meets its reflection crisply; farther from it the gradual one, and between
  // the two a soft passage (read finer, the passage showed as a small sharp block under a foot)
  float here = textureLod(uMirrorWidths, pic, HERE_LOD).x;
  float gap = mix(here, blurs.x, smoothstep(0.02, 0.1, here));
  // the blur: as wide as the cone ([BlurLook]) from the floor reaches at the gap, seen from the camera, and never less
  // than the least; read from the picture's smaller copy of about that size, and four times round the middle, each
  // read counting less the brighter
  vec2 size = vec2(textureSize(uMirror, 0)) * uMirrorScale;
  vec2 spread = max(0.5 * uMirrorLens * min(uMirrorCone * gap, 0.1), vec2(MIRROR_LEAST) / size);
  float lod = log2(max(max(spread.x * size.x, spread.y * size.y), 1.0));
  vec3 c = textureLod(uMirror, pic, lod).rgb;
  float w = mirrorWeight(c, 0.4);
  vec3 seen = c * w;
  float weights = w;
  for (int i = 0; i < 4; i++) {
    c = textureLod(uMirror, (uv + MIRROR_TAPS[i] * spread) * uMirrorScale, lod).rgb;
    w = mirrorWeight(c, 0.15);
    seen += c * w;
    weights += w;
  }
  // never 0: each weight is above 0 for any light a half float holds (at most 65504)
  seen /= weights;
  if (uMirrorUnder > 0.0) {
    float lum = dot(seen, vec3(0.2126, 0.7152, 0.0722));
    vec3 moonlit = lum * mix(vec3(0.7, 0.95, 1.6), vec3(0.95, 1.0, 1.12), smoothstep(0.08, 0.8, lum));
    // and where nothing is mirrored, the other world's night: a faint cold mist
    seen = mix(seen, 0.75 * moonlit + vec3(0.004, 0.008, 0.02), uMirrorUnder);
  }
  float keep = mix(0.7, 1.0, blurs.y);
  vec3 toEye = normalize(cameraPosition - world);
  float fresnel = 0.45 + 0.35 * pow(1.0 - clamp(toEye.y, 0.0, 1.0), 5.0);
  return seen * fresnel * keep * reach;
}"""
    }
}
