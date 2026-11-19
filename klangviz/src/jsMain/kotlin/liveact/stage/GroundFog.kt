/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.disposeTree
import io.peekandpoke.klang.viz.liveact.lights.Lamp
import io.peekandpoke.klang.viz.liveact.toFixed
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.BlendFactor
import io.peekandpoke.klang.workshop.threejs.Blending
import io.peekandpoke.klang.workshop.threejs.Data3DTexture
import io.peekandpoke.klang.workshop.threejs.RenderTargetOptions
import io.peekandpoke.klang.workshop.threejs.ShaderMaterial
import io.peekandpoke.klang.workshop.threejs.ShaderMaterialParameters
import io.peekandpoke.klang.workshop.threejs.TextureDataType
import io.peekandpoke.klang.workshop.threejs.TextureFormat
import io.peekandpoke.klang.workshop.threejs.Uniform
import io.peekandpoke.klang.workshop.threejs.Uniforms
import io.peekandpoke.klang.workshop.threejs.WebGLRenderTarget
import io.peekandpoke.klang.workshop.threejs.createData3DTexture
import io.peekandpoke.klang.workshop.threejs.createShaderMaterial
import io.peekandpoke.klang.workshop.threejs.createWebGLRenderTarget
import io.peekandpoke.klang.workshop.threejs.drawOrder
import io.peekandpoke.klang.workshop.threejs.extensions
import io.peekandpoke.klang.workshop.threejs.forceSinglePass
import io.peekandpoke.klang.workshop.threejs.frustumCulled
import io.peekandpoke.klang.workshop.threejs.getRenderTarget
import io.peekandpoke.klang.workshop.threejs.set
import io.peekandpoke.klang.workshop.threejs.setRenderTarget
import io.peekandpoke.klang.workshop.threejs.uniform
import io.peekandpoke.klang.workshop.threejs.uniformsOf
import io.peekandpoke.kraft.addons.threejs.js.BufferGeometry
import io.peekandpoke.kraft.addons.threejs.js.Camera
import io.peekandpoke.kraft.addons.threejs.js.MaterialSide
import io.peekandpoke.kraft.addons.threejs.js.Mesh
import io.peekandpoke.kraft.addons.threejs.js.Scene
import io.peekandpoke.kraft.addons.threejs.js.TextureFilter
import io.peekandpoke.kraft.addons.threejs.js.TextureWrapping
import io.peekandpoke.kraft.addons.threejs.js.WebGLRenderer
import io.peekandpoke.kraft.utils.jsObject
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.set
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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
// The sheets cover much of the picture many times over (twelve at the quality the fog was made with, FogQuality), so
// working the fog out for every pixel of every sheet costs with the number of pixels. But what the fog is at a place
// on the stage does not depend on the camera. So it is worked out once a frame as a map of the stage for each sheet
// (a point every seven centimetres, all the maps side by side in one picture), and the sheets are drawn into the
// scene reading their maps: one reading a pixel.
// The cost of the maps is the same whatever the screen's size, and what stands in the fog cuts it exactly, at the
// picture's own fineness.
// (A first try drew the fog into a picture of half the screen's size and laid that over the finished one. Where
// bright fog lay behind a dark leg, the leg's edge showed in steps: the maintainer, 2026-11-17, "pretty heavy
// aliasing on the legs".)

/**
 * The fog on the floor of [scene], drawn by [renderer] at [quality] (its number of sheets; not [FogQuality.Off],
 * which is no fog to make). Each frame, after the lamps are set and before the scene is drawn: [update]. When the
 * stage goes, or the fog is made anew at another quality: [dispose].
 *
 * [mapped]: the fog is worked out as maps of the stage (see the top of the file), where the graphics card can hold
 * such a picture; else, and with `?fullfog` (to compare), every pixel of every sheet works it out for itself.
 */
class GroundFog(
    private val scene: Scene,
    private val renderer: WebGLRenderer,
    private val quality: FogQuality,
    mapped: Boolean,
) {
    /** One sheet of the stack, with its material and its own see-through share. */
    private class Layer(val mesh: Mesh, val material: ShaderMaterial, val alpha: Uniform<Double>)

    // What the fog is made from: the time, and the lamps (place, direction, colour times intensity, the cone's cosine)
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
    private val noise: Data3DTexture = noiseBlock()

    /** The maps of the stage, where the fog is worked out as maps; null where every pixel works it out. */
    private val maps: Maps? = if (mapped && canHoldMaps()) Maps() else null

    /** The sheets' shape, one for all of them. */
    private val sheet: BufferGeometry = Three.addon.createPlaneGeometry(WIDE, DEEP)

    private val layers: List<Layer>
    private val target = vec()

    init {
        // a fog of two sheets at least, from the floor to the top; every sheet has its map in the picture, and the
        // picture no map to spare
        require(quality.layers >= 2) { "a fog of ${quality.layers} sheets is no fog to make" }
        require(quality.cols * quality.rows == quality.layers) {
            "${quality.layers} sheets do not fill ${quality.cols} by ${quality.rows} maps"
        }

        val count = quality.layers
        val cols = quality.cols

        layers = (0 until count).map { i ->
            val share = i.toDouble() / (count - 1)
            val alpha = uniform(0.0)
            val uniforms = uniformsOf("uAlpha" to alpha)

            if (maps != null) {
                uniforms["uMaps"] = uniform(maps.picture.texture)
                uniforms["uCell"] = uniform(Three.addon.createVector2(x = (i % cols).toDouble(), y = (i / cols).toDouble()))
            } else {
                fogUniforms(uniforms)
                uniforms["uHeight"] = uniform(share)
            }

            val material = Three.addon.createShaderMaterial(jsObject<ShaderMaterialParameters> {
                this.uniforms = uniforms
                vertexShader = if (maps != null) MAP_VERTEX else VERTEX
                fragmentShader = if (maps != null) sheetFragment(quality) else FRAGMENT
                transparent = true
                depthWrite = false
                side = MaterialSide.DoubleSide
                blending = Blending.CustomBlending
                blendSrc = BlendFactor.OneFactor
                blendDst = BlendFactor.OneMinusSrcAlphaFactor
            })
            // a level sheet shows the camera one side only: drawn once, where three.js draws what is see-through
            // and two-sided twice
            material.forceSinglePass = true
            val mesh = Three.addon.createMesh(sheet, material)
            mesh.rotation.x = -PI / 2
            mesh.position.y = BOTTOM + (TOP - BOTTOM) * share
            mesh.renderOrder = 10 + i
            scene.add(mesh)

            Layer(mesh = mesh, material = material, alpha = alpha)
        }
    }

    /** Puts what the fog is made from into [uniforms]. */
    private fun fogUniforms(uniforms: Uniforms) {
        uniforms["uTime"] = time
        uniforms["uNoise"] = uniform(noise)
        uniforms["uPos"] = positionsUniform
        uniforms["uDir"] = directionsUniform
        uniforms["uCol"] = colorsUniform
        uniforms["uCos"] = cosinesUniform
    }

    /** Sets the fog for [t], lit by the first of [lamps], seen by [camera]; and works out its maps. */
    fun update(t: Double, lamps: List<Lamp>, camera: Camera) {
        time.value = t

        for (i in 0 until min(LAMPS, lamps.size)) {
            val light = lamps[i].light
            positions[i].copy(light.position)
            directions[i].copy(target.copy(light.target.position).sub(light.position).normalize())
            colors[i].set(light.color.r, light.color.g, light.color.b).multiplyScalar(light.intensity)
            cosines[i] = cos(light.angle)
        }

        val share = quality.share

        for (layer in layers) {
            val y = layer.mesh.position.y
            // a sheet seen edge-on would show as a line: it fades out as the camera comes to its height
            val off = abs(camera.position.y - y)
            layer.alpha.value = share * min(1.0, off / 0.25)
            // drawn from the far side toward the camera: from the floor up when it looks from above
            layer.mesh.drawOrder = 10 + (if (camera.position.y > y) 1 else -1) * y * 10
        }

        maps?.draw(camera)
    }

    /**
     * Takes the sheets out of the scene and frees all the fog holds on the graphics card: the sheets' materials and
     * their shape, the noise's picture, and the maps with what draws them. The fog is gone after it; call it once.
     */
    fun dispose() {
        for (layer in layers) {
            scene.remove(layer.mesh)
            layer.material.dispose()
        }

        sheet.dispose()
        noise.dispose()
        maps?.dispose()
    }

    /**
     * Whether the graphics card can be drawn into in half-float numbers, which the maps need: the light they hold
     * is not capped at 1.
     */
    private fun canHoldMaps(): Boolean {
        val extensions = renderer.extensions

        return extensions.has("EXT_color_buffer_float") || extensions.has("EXT_color_buffer_half_float")
    }

    /**
     * The fog as maps of the stage: one [picture] with a map for each sheet, side by side, and the one sheet that
     * fills it each frame.
     */
    private inner class Maps {
        val picture: WebGLRenderTarget = Three.addon.createWebGLRenderTarget(
            width = quality.cols * MAP_WIDE,
            height = quality.rows * MAP_DEEP,
            options = jsObject<RenderTargetOptions> {
                type = TextureDataType.HalfFloatType
                depthBuffer = false
                stencilBuffer = false
            },
        )

        private val filler: Scene = Three.addon.createScene()

        init {
            val uniforms = uniformsOf()
            fogUniforms(uniforms)

            val material = Three.addon.createShaderMaterial(jsObject<ShaderMaterialParameters> {
                this.uniforms = uniforms
                vertexShader = FILL_VERTEX
                fragmentShader = fillFragment(quality)
                depthWrite = false
                depthTest = false
            })
            val mesh = Three.addon.createMesh(Three.addon.createPlaneGeometry(2.0, 2.0), material)
            mesh.frustumCulled = false
            filler.add(mesh)
        }

        /** Works the maps out for this frame. [camera]: any; the sheet fills the picture whatever it sees. */
        fun draw(camera: Camera) {
            // where the renderer draws to: the screen, or a picture of someone's own
            val drawnTo = renderer.getRenderTarget()

            try {
                renderer.setRenderTarget(picture)
                renderer.render(filler, camera)
            } finally {
                renderer.setRenderTarget(drawnTo)
            }
        }

        fun dispose() {
            picture.dispose()
            filler.disposeTree()
        }
    }

    private companion object {
        /** The noise's block has this many random numbers each way; it repeats after them. */
        const val NOISE = 64

        /**
         * A block of [NOISE] cubed random numbers as a picture in three directions, repeating in each. The numbers
         * are the same on every run, so a still shows the same fog.
         */
        fun noiseBlock(): Data3DTexture {
            val random = Random(seed = 20261116)
            val values = Uint8Array(NOISE * NOISE * NOISE)

            for (i in 0 until values.length) {
                values[i] = random.nextInt(256).toByte()
            }

            val block = Three.addon.createData3DTexture(data = values, width = NOISE, height = NOISE, depth = NOISE)

            block.format = TextureFormat.RedFormat
            block.minFilter = TextureFilter.LinearFilter
            block.magFilter = TextureFilter.LinearFilter
            block.wrapS = TextureWrapping.RepeatWrapping
            block.wrapT = TextureWrapping.RepeatWrapping
            block.wrapR = TextureWrapping.RepeatWrapping
            block.unpackAlignment = 1
            block.needsUpdate = true

            return block
        }

        const val LAMPS = 5

        /** The lowest and the highest sheet's height, in metres. */
        const val BOTTOM = 0.03
        const val TOP = 0.50

        /** A sheet's size on the floor, in metres. */
        const val WIDE = 26.0
        const val DEEP = 22.0

        /** A map has this many points each way (7 cm); the maps lie in the picture as [FogQuality] lays them out. */
        const val MAP_WIDE = 384
        const val MAP_DEEP = 324

        /**
         * The fog at a place on the stage, for both ways of drawing it: `fogAt(world, height)` gives the light it
         * sends (as the sheet adds it) and how much it veils, for a sheet at `height` (0 the lowest, 1 the highest)
         * that lets all of it through; the sheet's own share is multiplied in by whoever draws it.
         */
        private val FOG_AT = """
  uniform float uTime;
  uniform vec3 uPos[${LAMPS}], uDir[${LAMPS}], uCol[${LAMPS}];
  uniform float uCos[${LAMPS}];
  uniform highp sampler3D uNoise;

  // a random number at every whole point, blended smoothly between them: the card blends, between the points' middles
  float noise(vec3 x) {
    vec3 i = floor(x), f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return texture(uNoise, (i + f + 0.5) / ${NOISE}.0).r;
  }

  vec4 fogAt(vec3 world, float height) {
    // the wind: everything drifts slowly across the stage and a little toward the audience
    vec3 p = vec3(world.x + 0.11 * uTime, world.y * 5.0 + 0.02 * uTime, world.z + 0.05 * uTime);
    // the turbulence: a slower, larger noise pushes the fog about, so it curls instead of sliding as one sheet
    vec3 stir = vec3(noise(p * 0.45 + vec3(0.0, 0.03 * uTime, 0.0)), 0.0, noise(p * 0.45 + vec3(17.0, -0.025 * uTime, 5.0))) - 0.5;
    vec3 q = p * 1.05 + 1.7 * stir;
    float d = 0.55 * noise(q) + 0.30 * noise(q * 2.1 + 3.0) + 0.15 * noise(q * 4.3 + 7.0);
    // the machines stand behind the band, left and right: thick there, thinner toward the audience, breathing slowly
    vec2 at = world.xz;
    float near = max(exp(-dot(at - vec2(-4.5, -3.2), at - vec2(-4.5, -3.2)) / 30.0), exp(-dot(at - vec2(4.5, -3.2), at - vec2(4.5, -3.2)) / 30.0));
    float amount = (0.30 + 0.70 * near) * (0.85 + 0.15 * sin(0.05 * uTime + at.x * 0.2));
    // it settles: at the floor nearly everywhere, higher up only where it billows
    float density = smoothstep(0.36 + 0.42 * height, 0.74 + 0.16 * height, d + 0.40 * amount);
    density *= 1.0 - smoothstep(8.0, 11.0, length(at * vec2(0.8, 1.0)));
    // the lamps: each lights the fog inside its cone, weaker with the square of the distance
    vec3 light = vec3(0.004, 0.005, 0.008);
    for (int i = 0; i < ${LAMPS}; i++) {
      vec3 to = uPos[i] - world;
      float far2 = dot(to, to);
      float cone = smoothstep(uCos[i], uCos[i] + 0.12, dot(-normalize(to), uDir[i]));
      light += uCol[i] * cone / far2;
    }
    return vec4(0.035 * light * density, 0.5 * density);     // it glows where lit, and veils a little what is behind
  }"""

        // A sheet that works the fog out for each of its pixels

        val VERTEX = """
  varying vec3 vWorld;
  void main() {
    vec4 world = modelMatrix * vec4(position, 1.0);
    vWorld = world.xyz;
    gl_Position = projectionMatrix * viewMatrix * world;
  }"""

        val FRAGMENT = """
  varying vec3 vWorld;
  uniform float uHeight, uAlpha;
  $FOG_AT

  void main() {
    gl_FragColor = fogAt(vWorld, uHeight) * uAlpha;
    #include <tonemapping_fragment>
    #include <colorspace_fragment>
  }"""

        // The maps: one sheet fills the whole picture, and each of its points finds its map, and its place on the stage

        val FILL_VERTEX = """
  varying vec2 vUv;
  void main() {
    vUv = uv;
    gl_Position = vec4(position.xy, 0.0, 1.0);
  }"""

        fun fillFragment(quality: FogQuality) = """
  varying vec2 vUv;
  $FOG_AT

  void main() {
    vec2 map = vUv * vec2(${quality.cols}.0, ${quality.rows}.0);
    vec2 cell = floor(map);
    vec2 here = fract(map);
    float height = (cell.y * ${quality.cols}.0 + cell.x) / ${quality.layers - 1}.0;
    // a sheet lies flat: its upper edge is the far one, toward the wall
    vec3 world = vec3(
      (here.x - 0.5) * ${WIDE.glsl()}, ${BOTTOM.glsl()} + ${(TOP - BOTTOM).glsl()} * height, (0.5 - here.y) * ${DEEP.glsl()}
    );
    gl_FragColor = fogAt(world, height);
  }"""

        // A sheet that reads its map

        val MAP_VERTEX = """
  varying vec2 vUv;
  void main() {
    vUv = uv;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }"""

        fun sheetFragment(quality: FogQuality) = """
  varying vec2 vUv;
  uniform sampler2D uMaps;
  uniform vec2 uCell;
  uniform float uAlpha;

  void main() {
    // not from the map's outermost half points: there the card would blend in the map next to it
    vec2 edge = vec2(0.5 / ${MAP_WIDE}.0, 0.5 / ${MAP_DEEP}.0);
    vec2 here = clamp(vUv, edge, 1.0 - edge);
    gl_FragColor = texture2D(uMaps, (uCell + here) / vec2(${quality.cols}.0, ${quality.rows}.0)) * uAlpha;
    #include <tonemapping_fragment>
    #include <colorspace_fragment>
  }"""
    }
}

/** The number as a shader takes it: always with its decimals (26.0 would else be written 26, a whole number). */
private fun Double.glsl(): String = toFixed(4)
