/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.disposeTree
import io.peekandpoke.klang.viz.liveact.lights.Lamp
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.BlendFactor
import io.peekandpoke.klang.workshop.threejs.Blending
import io.peekandpoke.klang.workshop.threejs.ShaderMaterialParameters
import io.peekandpoke.klang.workshop.threejs.createColor
import io.peekandpoke.klang.workshop.threejs.createShaderMaterial
import io.peekandpoke.klang.workshop.threejs.frustumCulled
import io.peekandpoke.klang.workshop.threejs.uniform
import io.peekandpoke.kraft.addons.threejs.js.Camera
import io.peekandpoke.kraft.addons.threejs.js.MaterialSide
import io.peekandpoke.kraft.addons.threejs.js.Mesh
import io.peekandpoke.kraft.addons.threejs.js.Object3D
import io.peekandpoke.kraft.addons.threejs.js.Scene
import io.peekandpoke.kraft.addons.threejs.js.WebGLRenderer
import io.peekandpoke.kraft.utils.jsObject
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.set
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// Fog from a fog machine, lying on the floor. It is a volume drawn in slices: a stack of thin level sheets from the
// floor to knee height, each showing the fog's density at its own height. The density is a noise that drifts with a
// slight wind and is stirred by a slower one (the turbulence); it is thick at the floor and only its billows reach
// the top, thickest near the two machines behind the band. The lamps light it, each in its cone and colour, so the
// beams show in it; it throws and takes no shadows.
//
// The noise is read from a picture: a block of random numbers, made once and repeating in every direction, which the
// graphics card blends between its points. Five readings a pixel and sheet stand in place of forty computed random
// numbers, which made the fog cost more than everything else on the stage together (measured 2026-11-16).
//
// The sheets cover much of the picture twelve times over, so the fog's cost goes with the number of pixels. It is
// therefore drawn apart, into a picture of half the width and half the height (a quarter of the pixels), and that
// picture is laid over the finished one. Fog has no fine detail to lose. What stands in front of it must still hide
// it, so the small picture first takes the scene's depths (everything solid, drawn without colour), and the sheets
// are tested against them. Each sheet adds its light and veils what is behind by its share; twelve of these in a row
// are again one light and one share, so laying the small picture over the large one gives what the sheets gave one
// by one. The price: around a leg or a stand in front of the fog, the fog's edge is half as fine as the leg's.

/**
 * The fog on the floor of [scene], drawn by [renderer]. Each frame, after the lamps are set: [update]. When the
 * stage goes: [dispose].
 *
 * [half]: drawn at half size and laid over the picture (see the top of the file); it hooks into the scene for that
 * (`onAfterRender`), so whoever draws the scene draws the fog with it. Otherwise the sheets are part of the scene
 * and drawn at its size (`?fullfog`, to compare).
 */
class GroundFog(private val scene: Scene, private val renderer: WebGLRenderer, half: Boolean) {

    /** One sheet of the stack, with its own see-through share. */
    private class Layer(val mesh: Mesh, val alpha: dynamic)

    // What all the sheets share: the time, and the lamps (place, direction, colour times intensity, the cone's cosine)
    private val time = uniform(0.0)
    private val positions = Array(LAMPS) { vec() }
    private val directions = Array(LAMPS) { vec(0.0, -1.0, 0.0) }
    private val colors = Array(LAMPS) { vec() }
    private val cosines = Array(LAMPS) { 0.0 }
    private val positionsUniform = uniform(positions)
    private val directionsUniform = uniform(directions)
    private val colorsUniform = uniform(colors)
    private val cosinesUniform = uniform(cosines)

    /** The random numbers the noise is read from (see the top of the file). */
    private val noise: dynamic = noiseBlock()

    private val layers: List<Layer>
    private val target = vec()

    /** What draws the fog at half size; null where the sheets are part of the scene. */
    private val small: Small? = if (half) Small() else null

    init {
        val sheet = Three.addon.createPlaneGeometry(26.0, 22.0)
        val into = small?.sheets ?: scene

        layers = (0 until LAYERS).map { i ->
            val share = i.toDouble() / (LAYERS - 1)
            val alpha = uniform(0.0)
            val uniforms: dynamic = jsObject()
            uniforms.uTime = time
            uniforms.uNoise = uniform(noise)
            uniforms.uPos = positionsUniform
            uniforms.uDir = directionsUniform
            uniforms.uCol = colorsUniform
            uniforms.uCos = cosinesUniform
            uniforms.uHeight = uniform(share)
            uniforms.uAlpha = alpha

            val material = Three.addon.createShaderMaterial(jsObject<ShaderMaterialParameters> {
                this.uniforms = uniforms
                vertexShader = VERTEX
                fragmentShader = FRAGMENT
                transparent = true
                depthWrite = false
                side = MaterialSide.DoubleSide
                blending = Blending.CustomBlending
                blendSrc = BlendFactor.OneFactor
                blendDst = BlendFactor.OneMinusSrcAlphaFactor
            })
            // the shader maps its light to the screen itself (see its end)
            material.asDynamic().toneMapped = false
            // a level sheet shows the camera one side only: drawn once, where three.js draws what is see-through
            // and two-sided twice
            material.asDynamic().forceSinglePass = true
            val mesh = Three.addon.createMesh(sheet, material)
            mesh.rotation.x = -PI / 2
            mesh.position.y = 0.03 + (TOP - 0.03) * share
            mesh.renderOrder = 10 + i
            into.add(mesh)

            Layer(mesh = mesh, alpha = alpha)
        }
    }

    /** Sets the fog for [t], lit by the first of [lamps], seen by [camera]. */
    fun update(t: Double, lamps: List<Lamp>, camera: Camera) {
        time.value = t

        for (i in 0 until min(LAMPS, lamps.size)) {
            val light = lamps[i].light
            positions[i].copy(light.position)
            directions[i].copy(target.copy(light.target.position).sub(light.position).normalize())
            colors[i].set(light.color.r, light.color.g, light.color.b).multiplyScalar(light.intensity)
            cosines[i] = cos(light.angle)
        }

        for (layer in layers) {
            val y = layer.mesh.position.y
            // a sheet seen edge-on would show as a line: it fades out as the camera comes to its height
            val off = abs(camera.position.y - y)
            layer.alpha.value = 0.22 * min(1.0, off / 0.25)
            // drawn from the far side toward the camera: from the floor up when it looks from above
            layer.mesh.drawOrder = 10 + (if (camera.position.y > y) 1 else -1) * y * 10
        }
    }

    /**
     * Frees the noise's picture on the graphics card, and what draws the fog at half size. Sheets that are part of
     * the scene go with the scene.
     */
    fun dispose() {
        noise.dispose()
        small?.dispose()
    }

    /**
     * The fog at half size: the [sheets] in a scene of their own, the small picture they are drawn into, and the
     * sheet that lays it over the finished picture. It draws whenever the stage's scene was drawn.
     */
    private inner class Small {
        val sheets: Scene = Three.addon.createScene()

        private val three: dynamic = Three.addon.raw
        private val picture: dynamic = js(
            "(function (three) { return new three.WebGLRenderTarget(1, 1, { depthBuffer: true, stencilBuffer: false }) })"
        )(three)

        /** Everything solid is drawn with this into the small picture: its depth only, from both sides. */
        private val depthOnly: dynamic = Three.addon.createMeshBasicMaterial(jsObject {}).also {
            it.asDynamic().colorWrite = false
            it.asDynamic().side = MaterialSide.DoubleSide
        }

        private val overlay: Scene = Three.addon.createScene()

        /** What hides nothing (see [unsolid]), looked up once the scene is first seen; and whether each was seen. */
        private var unsolid: Array<dynamic>? = null
        private var seen: Array<Boolean> = emptyArray()

        private val clearColor = Three.addon.createColor(0x000000)

        /** True while the fog draws: its own drawing of the scene must not start it again. */
        private var drawing = false

        init {
            val uniforms: dynamic = jsObject()
            uniforms.uFog = uniform(picture.texture)

            val material = Three.addon.createShaderMaterial(jsObject<ShaderMaterialParameters> {
                this.uniforms = uniforms
                vertexShader = OVER_VERTEX
                fragmentShader = OVER_FRAGMENT
                transparent = true
                depthWrite = false
                depthTest = false
                blending = Blending.CustomBlending
                blendSrc = BlendFactor.OneFactor
                blendDst = BlendFactor.OneMinusSrcAlphaFactor
            })
            // the small picture holds colours as the screen takes them
            material.asDynamic().toneMapped = false
            val mesh = Three.addon.createMesh(Three.addon.createPlaneGeometry(2.0, 2.0), material)
            mesh.frustumCulled = false
            overlay.add(mesh)

            scene.asDynamic().onAfterRender = { _: dynamic, _: dynamic, camera: Camera -> draw(camera) }
        }

        /** Draws the fog into the small picture and lays it over what was just drawn, as seen by [camera]. */
        private fun draw(camera: Camera) {
            if (drawing || !scene.visible) {
                return
            }

            val r: dynamic = renderer
            val width = max(1, renderer.domElement.width / 2)
            val height = max(1, renderer.domElement.height / 2)

            if (picture.width != width || picture.height != height) {
                picture.setSize(width, height)
            }

            // what does not hide the fog: looked up once, as the scene is complete when it is first shown
            val out = unsolid ?: unsolid().also {
                unsolid = it
                seen = Array(it.size) { true }
            }

            // what is changed on the renderer here is put back below
            val autoClear: Boolean = r.autoClear
            val shadows: Boolean = r.shadowMap.autoUpdate
            val shadowsDue: Boolean = r.shadowMap.needsUpdate
            val clearAlpha: Double = r.getClearAlpha()
            val background: dynamic = scene.asDynamic().background
            // where the scene was drawn to: the screen, or a picture of someone's own
            val drawnTo: dynamic = r.getRenderTarget()
            val moves: Boolean = scene.asDynamic().matrixWorldAutoUpdate
            var hidden = false

            r.getClearColor(clearColor)
            drawing = true

            try {
                r.autoClear = false
                // the shadows are this frame's already
                r.shadowMap.autoUpdate = false
                r.shadowMap.needsUpdate = false
                r.setRenderTarget(picture)
                r.setClearColor(0x000000, 0.0)
                // colour and depth, no stencil
                r.clear(true, true, false)

                // the depths of everything solid
                for (i in out.indices) {
                    seen[i] = out[i].visible
                    out[i].visible = false
                }

                hidden = true
                // nothing has moved since the scene was drawn: its places are not worked out again
                scene.asDynamic().matrixWorldAutoUpdate = false

                // without its background: three.js clears to a scene's background colour, fully opaque, whatever
                // autoClear says, and the small picture must stay see-through where there is no fog
                scene.asDynamic().background = null
                scene.asDynamic().overrideMaterial = depthOnly
                r.render(scene, camera)
                scene.asDynamic().overrideMaterial = null
                scene.asDynamic().background = background

                // the fog, behind them; then all of it over the picture
                r.render(sheets, camera)
                r.setRenderTarget(drawnTo)
                r.render(overlay, camera)
            } finally {
                if (hidden) {
                    for (i in out.indices) {
                        out[i].visible = seen[i]
                    }
                }

                scene.asDynamic().matrixWorldAutoUpdate = moves
                scene.asDynamic().overrideMaterial = null
                scene.asDynamic().background = background
                r.setRenderTarget(drawnTo)
                r.setClearColor(clearColor, clearAlpha)
                r.shadowMap.autoUpdate = shadows
                r.shadowMap.needsUpdate = shadowsDue
                r.autoClear = autoClear
                drawing = false
            }
        }

        /**
         * What in the scene hides nothing behind it: points (the crowd's lights), and what does not write its depth
         * (the dark laid under the kit and the riser).
         */
        private fun unsolid(): Array<dynamic> {
            val found = mutableListOf<dynamic>()

            scene.traverse { node ->
                val o: dynamic = node
                val material: dynamic = o.material

                if (o.isPoints == true || (material != null && material.depthWrite == false)) {
                    found.add(o)
                }
            }

            return found.toTypedArray()
        }

        fun dispose() {
            // the scene's own again, which does nothing: ours lay over it
            val hooked: dynamic = scene
            js("delete hooked.onAfterRender")
            picture.dispose()
            depthOnly.dispose()
            sheets.disposeTree()
            overlay.disposeTree()
        }
    }

    private companion object {
        /** The noise's block has this many random numbers each way; it repeats after them. */
        const val NOISE = 64

        /**
         * A block of [NOISE] cubed random numbers as a picture in three directions, repeating in each. The numbers
         * are the same on every run, so a still shows the same fog.
         */
        fun noiseBlock(): dynamic {
            val random = Random(seed = 20261116)
            val values = Uint8Array(NOISE * NOISE * NOISE)

            for (i in 0 until values.length) {
                values[i] = random.nextInt(256).toByte()
            }

            val three: dynamic = Three.addon.raw
            val create = js("(function (three, values, n) { return new three.Data3DTexture(values, n, n, n) })")
            val block: dynamic = create(three, values, NOISE)

            block.format = three.RedFormat
            block.minFilter = three.LinearFilter
            block.magFilter = three.LinearFilter
            block.wrapS = three.RepeatWrapping
            block.wrapT = three.RepeatWrapping
            block.wrapR = three.RepeatWrapping
            block.unpackAlignment = 1
            block.needsUpdate = true

            return block
        }

        const val LAYERS = 12
        const val TOP = 0.50
        const val LAMPS = 5

        val VERTEX = """
  varying vec3 vWorld;
  void main() {
    vec4 world = modelMatrix * vec4(position, 1.0);
    vWorld = world.xyz;
    gl_Position = projectionMatrix * viewMatrix * world;
  }"""

        val OVER_VERTEX = """
  varying vec2 vUv;
  void main() {
    vUv = uv;
    gl_Position = vec4(position.xy, 0.0, 1.0);
  }"""

        val OVER_FRAGMENT = """
  varying vec2 vUv;
  uniform sampler2D uFog;
  void main() {
    gl_FragColor = texture2D(uFog, vUv);
  }"""

        // The light is mapped to the screen here, by the renderer's own functions called by name (the ACES mapping
        // the stage sets, then sRGB), where a shader usually leaves that to the renderer: the renderer does it only
        // for what is drawn onto the screen, and the sheets are drawn into the small picture. Each sheet is mapped
        // by itself, as it was when the sheets were drawn onto the screen, so the fog is as bright as before.
        val FRAGMENT = """
  #include <tonemapping_pars_fragment>
  varying vec3 vWorld;
  uniform float uTime, uHeight, uAlpha;
  uniform vec3 uPos[${LAMPS}], uDir[${LAMPS}], uCol[${LAMPS}];
  uniform float uCos[${LAMPS}];
  uniform highp sampler3D uNoise;

  // a random number at every whole point, blended smoothly between them: the card blends, between the points' middles
  float noise(vec3 x) {
    vec3 i = floor(x), f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return texture(uNoise, (i + f + 0.5) / ${NOISE}.0).r;
  }

  void main() {
    // the wind: everything drifts slowly across the stage and a little toward the audience
    vec3 p = vec3(vWorld.x + 0.11 * uTime, vWorld.y * 5.0 + 0.02 * uTime, vWorld.z + 0.05 * uTime);
    // the turbulence: a slower, larger noise pushes the fog about, so it curls instead of sliding as one sheet
    vec3 stir = vec3(noise(p * 0.45 + vec3(0.0, 0.03 * uTime, 0.0)), 0.0, noise(p * 0.45 + vec3(17.0, -0.025 * uTime, 5.0))) - 0.5;
    vec3 q = p * 1.05 + 1.7 * stir;
    float d = 0.55 * noise(q) + 0.30 * noise(q * 2.1 + 3.0) + 0.15 * noise(q * 4.3 + 7.0);
    // the machines stand behind the band, left and right: thick there, thinner toward the audience, breathing slowly
    vec2 at = vWorld.xz;
    float near = max(exp(-dot(at - vec2(-4.5, -3.2), at - vec2(-4.5, -3.2)) / 30.0), exp(-dot(at - vec2(4.5, -3.2), at - vec2(4.5, -3.2)) / 30.0));
    float amount = (0.30 + 0.70 * near) * (0.85 + 0.15 * sin(0.05 * uTime + at.x * 0.2));
    // it settles: at the floor nearly everywhere, higher up only where it billows
    float density = smoothstep(0.36 + 0.42 * uHeight, 0.74 + 0.16 * uHeight, d + 0.40 * amount);
    density *= 1.0 - smoothstep(8.0, 11.0, length(at * vec2(0.8, 1.0)));
    // the lamps: each lights the fog inside its cone, weaker with the square of the distance
    vec3 light = vec3(0.004, 0.005, 0.008);
    for (int i = 0; i < ${LAMPS}; i++) {
      vec3 to = uPos[i] - vWorld;
      float far2 = dot(to, to);
      float cone = smoothstep(uCos[i], uCos[i] + 0.12, dot(-normalize(to), uDir[i]));
      light += uCol[i] * cone / far2;
    }
    float a = density * uAlpha;
    gl_FragColor = vec4(0.035 * light * a, 0.5 * a);       // it glows where lit, and veils a little what is behind
    gl_FragColor.rgb = ACESFilmicToneMapping(gl_FragColor.rgb);
    gl_FragColor = sRGBTransferOETF(gl_FragColor);
  }"""
    }
}

// TYPING: belongs in the workshop
/**
 * The draw order as three.js takes it: any number, not only a whole one (the addon's [Object3D.renderOrder] is an
 * Int). The fog's sheets are ordered by their height.
 */
private var Object3D.drawOrder: Double
    get() = asDynamic().renderOrder.unsafeCast<Double>()
    set(value) {
        asDynamic().renderOrder = value
    }
