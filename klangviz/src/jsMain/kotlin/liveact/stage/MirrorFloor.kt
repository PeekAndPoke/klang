/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.mat4
import io.peekandpoke.klang.workshop.threejs.CompiledShader
import io.peekandpoke.klang.workshop.threejs.Matrix4
import io.peekandpoke.klang.workshop.threejs.RenderTargetOptions
import io.peekandpoke.klang.workshop.threejs.TextureDataType
import io.peekandpoke.klang.workshop.threejs.Uniform
import io.peekandpoke.klang.workshop.threejs.WebGLRenderTarget
import io.peekandpoke.klang.workshop.threejs.createWebGLRenderTarget
import io.peekandpoke.klang.workshop.threejs.extensions
import io.peekandpoke.klang.workshop.threejs.getRenderTarget
import io.peekandpoke.klang.workshop.threejs.matrixWorld
import io.peekandpoke.klang.workshop.threejs.onBeforeCompile
import io.peekandpoke.klang.workshop.threejs.set
import io.peekandpoke.klang.workshop.threejs.setRenderTarget
import io.peekandpoke.klang.workshop.threejs.uniform
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
import kotlin.math.max

/**
 * The floor as polished dark stone (`?floor=mirror`, [FloorLook.Mirror]): [floor] takes a material of its own, darker
 * and a little rougher than the plain floor's (the lamps' pools on it softer), that shows the stage mirrored in it,
 * dimmer than the stage itself, blurred and fading the farther what is mirrored stands from the floor, and bent a
 * little by a slow ripple of the surface, so it reads as polished stone and not as a mirror.
 *
 * How: each frame, just before the floor is drawn (three.js's `onBeforeRender`, as its own `Reflector` does it), a
 * second camera, the frame's camera mirrored in the floor, draws [scene] into a picture of a quarter of the screen's
 * pixels (half each way), with its depth. The floor reads the picture where each of its points sees its mirror image;
 * the depth says how far behind the floor that image lies, which sets the blur and the fade. The lamps' shadows are
 * the ones the frame drew just before; the mirror draws none of its own. Nothing stands below the floor, so the
 * camera needs no clipping at the floor.
 *
 * Not mirrored: the floor itself, and what [hide] hides while the mirror draws (`hide(true)`, then `hide(false)`):
 * the stage hides the fog that lies on the floor there. The rest is mirrored: the band, the riser, the instruments,
 * the wall or the fence, the haze behind it and the sun, the crowd.
 *
 * [dispose] gives the floor its own material back and frees the picture, its depth and the material; the camera holds
 * nothing on the graphics card.
 */
class MirrorFloor(
    private val floor: Mesh,
    private val scene: Scene,
    private val renderer: WebGLRenderer,
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

    // What the floor's material reads: the picture and its depth, where a place of the world lands on the picture,
    // where it is seen from the mirrored camera, the camera's near and far, and its lens's scale across and up
    private val image: Uniform<Texture?> = uniform(null)
    private val depth: Uniform<Texture?> = uniform(null)
    private val toPicture: Uniform<Matrix4> = uniform(mat4())
    private val toCamera: Uniform<Matrix4> = uniform(mat4())
    private val range: Uniform<Vector2> = uniform(Three.addon.createVector2(x = 0.1, y = 100.0))
    private val lens: Uniform<Vector2> = uniform(Three.addon.createVector2(x = 1.0, y = 1.0))

    private val material: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(jsObject {
        color = 0x050609
        roughness = 0.5
        metalness = 0.15
    })

    init {
        material.onBeforeCompile("floor-mirror") { shader: CompiledShader ->
            shader.uniforms["uMirror"] = image
            shader.uniforms["uMirrorDepth"] = depth
            shader.uniforms["uMirrorPicture"] = toPicture
            shader.uniforms["uMirrorCamera"] = toCamera
            shader.uniforms["uMirrorRange"] = range
            shader.uniforms["uMirrorLens"] = lens
            shader.vertexShader = shader.vertexShader
                .replaceFirst("#include <common>", "#include <common>\nvarying vec3 vFloorWorld;")
                .replaceFirst(
                    "#include <begin_vertex>",
                    "#include <begin_vertex>\nvFloorWorld = (modelMatrix * vec4(transformed, 1.0)).xyz;",
                )
            shader.fragmentShader = shader.fragmentShader
                .replaceFirst("#include <common>", "#include <common>\n$MIRRORED")
                .replaceFirst(
                    "#include <opaque_fragment>",
                    // the lamps' pools softer, the mirror image over them
                    "outgoingLight = outgoingLight * 0.3 + mirrored(vFloorWorld);\n#include <opaque_fragment>",
                )
        }

        floor.material = material
        floor.asDynamic().onBeforeRender = { _: dynamic, _: dynamic, camera: Camera -> draw(camera) }
    }

    /** Draws the mirror for the frame seen by [camera], before the floor is drawn. */
    private fun draw(camera: Camera) {
        val lensOf = camera.unsafeCast<PerspectiveCamera>()
        val eye = camera.matrixWorld.elements

        // a camera below the floor sees no mirror image in it
        if (eye[13] <= 0.01) {
            return
        }

        val target = pictureFor(width = renderer.domElement.width, height = renderer.domElement.height)

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
        toCamera.value.copy(inverse)
        range.value.set(lensOf.near, lensOf.far)
        lens.value.set(projection.elements[0], projection.elements[5])

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
        } finally {
            renderer.setRenderTarget(drawnTo)
            shadows.autoUpdate = shadowsUpdate
            hide(false)
            floor.visible = true
        }
    }

    /** The picture for a screen [width] by [height] pixels: half each way; made anew where that changed. */
    private fun pictureFor(width: Int, height: Int): WebGLRenderTarget {
        val wide = max(1, width / 2)
        val tall = max(1, height / 2)
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

        return made
    }

    /** Frees the picture and its depth, where there is one. */
    private fun free() {
        val old = picture ?: return

        picture = null
        image.value = null
        depth.value = null
        old.asDynamic().depthTexture?.dispose()
        old.dispose()
    }

    /** Gives the floor its own material back and frees what the mirror holds. The mirror is gone after it; call it once. */
    fun dispose() {
        floor.material = plain
        // the floor draws nothing before itself again: three.js's own (empty) onBeforeRender is the prototype's
        val mesh: dynamic = floor

        js("delete mesh.onBeforeRender")
        free()
        material.dispose()
    }

    private companion object {
        /**
         * The mirror image at a place of the floor (`world`), as the floor adds it to its own light. The picture is
         * read where the place lands on it, bent a little by a slow ripple; the depth there says how far behind the
         * floor the image lies (`gap`, in metres along the view). The farther, the wider the image is blurred (a
         * spread of about 0.03 of the gap, seen from the camera, read from the picture's smaller copies so a thin
         * string does not break into dots) and the more it fades (to 0.7, over some 3 m). Then the stone's own
         * share: 0.6 looking down on it, more at a glancing angle (after Schlick's Fresnel); none far out
         * among the audience. The floor's own light (the lamps' pools) is turned down to 0.3 under it.
         */
        val MIRRORED = """
uniform sampler2D uMirror, uMirrorDepth;
uniform mat4 uMirrorPicture, uMirrorCamera;
uniform vec2 uMirrorRange, uMirrorLens;
varying vec3 vFloorWorld;
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
vec3 mirrored(vec3 world) {
  float near = -(uMirrorCamera * vec4(world, 1.0)).z;
  vec4 at = uMirrorPicture * vec4(world, 1.0);
  vec2 uv = at.xy / at.w;
  // the stone is not quite flat: a slow ripple, larger and finer, bends the image a little
  vec2 ripple = vec2(mirrorNoise(world.xz * 1.3), mirrorNoise(world.xz * 1.3 + 17.0)) - 0.5;
  ripple += 0.5 * (vec2(mirrorNoise(world.xz * 4.1 + 5.0), mirrorNoise(world.xz * 4.1 + 23.0)) - 0.5);
  uv += ripple * 0.006;
  float d = texture2D(uMirrorDepth, uv).x;
  float n = uMirrorRange.x, f = uMirrorRange.y;
  float hit = n * f / (f - (f - n) * d);
  float gap = max(hit - near, 0.0);
  // the blur: as wide as a cone of about 0.03 from the floor reaches at the gap, seen from the camera; read from the
  // picture's smaller copy of about that size, and four times round the middle
  vec2 spread = 0.5 * uMirrorLens * min(0.03 * gap / max(hit, 0.1), 0.1);
  vec2 size = vec2(textureSize(uMirror, 0));
  float lod = log2(max(max(spread.x * size.x, spread.y * size.y), 1.0));
  vec3 seen = textureLod(uMirror, uv, lod).rgb * 0.4;
  for (int i = 0; i < 4; i++) {
    seen += textureLod(uMirror, uv + MIRROR_TAPS[i] * spread, lod).rgb * 0.15;
  }
  float keep = mix(0.7, 1.0, exp(-gap / 3.0));
  vec3 toEye = normalize(cameraPosition - world);
  float fresnel = 0.6 + 0.4 * pow(1.0 - clamp(toEye.y, 0.0, 1.0), 5.0);
  float reach = 1.0 - smoothstep(14.0, 30.0, length(world.xz));
  return seen * fresnel * keep * reach;
}"""
    }
}
