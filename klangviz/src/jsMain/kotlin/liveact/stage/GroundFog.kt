/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.lights.Lamp
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.BlendFactor
import io.peekandpoke.klang.workshop.threejs.Blending
import io.peekandpoke.klang.workshop.threejs.ShaderMaterialParameters
import io.peekandpoke.klang.workshop.threejs.createShaderMaterial
import io.peekandpoke.klang.workshop.threejs.uniform
import io.peekandpoke.kraft.addons.threejs.js.Camera
import io.peekandpoke.kraft.addons.threejs.js.MaterialSide
import io.peekandpoke.kraft.addons.threejs.js.Mesh
import io.peekandpoke.kraft.addons.threejs.js.Object3D
import io.peekandpoke.kraft.addons.threejs.js.Scene
import io.peekandpoke.kraft.utils.jsObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min

// Fog from a fog machine, lying on the floor. It is a volume drawn in slices: a stack of thin level sheets from the
// floor to knee height, each showing the fog's density at its own height. The density is a noise that drifts with a
// slight wind and is stirred by a slower one (the turbulence); it is thick at the floor and only its billows reach
// the top, thickest near the two machines behind the band. The lamps light it, each in its cone and colour, so the
// beams show in it; it throws and takes no shadows.

/** Adds the fog to [scene]. Each frame, after the lamps are set: [update]. */
class GroundFog(scene: Scene) {

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

    private val layers: List<Layer>
    private val target = vec()

    init {
        val sheet = Three.addon.createPlaneGeometry(26.0, 22.0)

        layers = (0 until LAYERS).map { i ->
            val share = i.toDouble() / (LAYERS - 1)
            val alpha = uniform(0.0)
            val uniforms: dynamic = jsObject()
            uniforms.uTime = time
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
            val mesh = Three.addon.createMesh(sheet, material)
            mesh.rotation.x = -PI / 2
            mesh.position.y = 0.03 + (TOP - 0.03) * share
            mesh.renderOrder = 10 + i
            scene.add(mesh)

            Layer(mesh = mesh, alpha = alpha)
        }
    }

    /** Sets the fog for [t], lit by the first of [lamps], seen by [camera]. */
    fun update(t: Double, lamps: List<Lamp>, camera: Camera) {
        time.value = t

        lamps.take(LAMPS).forEachIndexed { i, lamp ->
            val light = lamp.light
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

    private companion object {
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

        val FRAGMENT = """
  varying vec3 vWorld;
  uniform float uTime, uHeight, uAlpha;
  uniform vec3 uPos[${LAMPS}], uDir[${LAMPS}], uCol[${LAMPS}];
  uniform float uCos[${LAMPS}];

  float hash(vec3 p) { p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
  float noise(vec3 x) {
    vec3 i = floor(x), f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x), mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x), mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
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
    #include <tonemapping_fragment>
    #include <colorspace_fragment>
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
