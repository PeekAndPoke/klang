/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.workshop.threejs.Blending
import io.peekandpoke.klang.workshop.threejs.ShaderMaterialParameters
import io.peekandpoke.klang.workshop.threejs.createBufferGeometry
import io.peekandpoke.klang.workshop.threejs.createFloat32Attribute
import io.peekandpoke.klang.workshop.threejs.createPoints
import io.peekandpoke.klang.workshop.threejs.createShaderMaterial
import io.peekandpoke.klang.workshop.threejs.frustumCulled
import io.peekandpoke.klang.workshop.threejs.setAttribute
import io.peekandpoke.klang.workshop.threejs.uniform
import io.peekandpoke.kraft.addons.threejs.js.Scene
import io.peekandpoke.kraft.utils.jsObject

// The audience, as far as it is seen: the small lights people hold up, nothing more. They stand in half a circle
// around the stage, as in a giant open-air theatre, in loose rows below the stage's edge and a few metres off it,
// the ground rising toward the back. The lights differ: each has its own colour (amber to warm white, a few of
// them a cool white), its own size and brightness, and flickers on its own, unevenly, as a small flame or a tired
// lamp does. All of that is worked out on the graphics card from the light's own number, so it costs the page
// nothing. Each light moves as its arm does: a slow sway when the music is quiet, wider and with a soft rise on
// the beat the louder it gets. They light nothing. The show's camera stands before the first row, so those
// straight ahead of the stage are seen only when it looks back from behind the band, or from far away (the
// stadium shot); those at the stage's sides also stand behind the band when it is seen from the other side.

/** Adds the audience's lights to [scene] (where they stand: [CrowdPlaces]). Each frame: [update]. */
class Crowd(scene: Scene) {

    private val time = uniform(0.0)
    private val size = uniform(60.0)
    private val energy = uniform(0.0)
    private val low = uniform(0.0)
    private val beat = uniform(0.0)

    init {
        val lights = CrowdPlaces.all
        val geometry = Three.addon.createBufferGeometry()
        geometry.setAttribute(
            "position",
            Three.addon.createFloat32Attribute(lights.flatMap { listOf(it.x, it.y, it.z) }.toTypedArray(), 3),
        )
        geometry.setAttribute("seed", Three.addon.createFloat32Attribute(lights.map { it.seed }.toTypedArray(), 1))
        geometry.setAttribute("group", Three.addon.createFloat32Attribute(lights.map { it.group }.toTypedArray(), 1))

        val uniforms: dynamic = jsObject()
        uniforms.uTime = time
        uniforms.uSize = size
        uniforms.uEnergy = energy
        uniforms.uLow = low
        uniforms.uBeat = beat

        val material = Three.addon.createShaderMaterial(jsObject<ShaderMaterialParameters> {
            this.uniforms = uniforms
            vertexShader = VERTEX
            fragmentShader = FRAGMENT
            transparent = true
            depthWrite = false
            blending = Blending.AdditiveBlending
        })
        val points = Three.addon.createPoints(geometry, material)
        points.frustumCulled = false
        scene.add(points)
    }

    /**
     * Sets the lights for [t]: [height] is the picture's, in pixels; [energy] how loud the music is (0 to 1) and
     * [low] its low end; [beat] the time in beats.
     */
    fun update(t: Double, height: Double, energy: Double, low: Double, beat: Double) {
        time.value = t
        this.energy.value = energy
        this.low.value = low
        this.beat.value = beat
        size.value = 0.18 * height          // a light about a hand wide, whatever the picture's size
    }

    private companion object {
        val VERTEX = """
  attribute float seed, group;
  uniform float uTime, uSize, uEnergy, uLow, uBeat;
  varying float vGlow;
  varying vec3 vTint;
  void main() {
    vec3 p = position;
    // the arm. People stand in groups, and a group moves as one: the same sway, the same beat, the same
    // liveliness; each of them only a little off the others. It sways slowly when the music is quiet, wider the
    // louder it is; with the music it goes up on the beat (some groups on every second beat), and the low end's
    // level pushes it further
    // (not as one man, though: each has their own reach and pace, comes in a little early or late, and about
    // one in six goes their own way altogether)
    float g = fract(seed * 31.0) > 0.84 ? seed : group;
    float own = fract(seed * 17.0);
    float lively = (0.5 + g) * (0.6 + 0.8 * own);
    float off = 1.5 * (seed - 0.5);
    // (the sway goes along the row, which is a circle round the stage: sideways for whoever looks at the stage)
    vec2 along = normalize(vec2(position.z, -position.x));
    p.xz += along * lively * (0.03 + 0.11 * uEnergy) * sin(uTime * (0.7 + 0.5 * g) * (0.94 + 0.12 * own) + g * 40.0 + off);
    p.y += 0.03 * sin(uTime * (0.9 + 0.4 * g) + g * 71.0 + off);
    float beat = uBeat * (fract(g * 7.0) > 0.7 ? 0.5 : 1.0) + fract(g * 13.0) + 0.20 * (seed - 0.5);
    // (a hand that rises and sinks softly with the beat, by a hand's width, 12 cm at the very most: they hold a
    // light up, they do not jump; the maintainer, 2026-11-17: "too jumpy... as if they would jump 2m high")
    p.y += lively * uEnergy * (0.025 + 0.03 * uLow) * (0.5 - 0.5 * cos(6.28318 * beat));
    // a small flame, analog: its brightness wanders slowly on its own, shivers faintly on top of that, and now and
    // then it nearly goes out; some are brighter than others to begin with
    float wander = 0.5 + 0.5 * sin(uTime * (0.31 + 0.4 * fract(seed * 43.0)) + seed * 37.0)
      * sin(uTime * (0.17 + 0.2 * seed) + seed * 11.0);
    float shiver = 0.10 * sin(uTime * (7.0 + 5.0 * seed) + seed * 90.0) + 0.05 * sin(uTime * 23.0 + seed * 13.0);
    float flicker = 0.62 + 0.30 * wander + shiver;
    float gust = smoothstep(0.92, 1.0, sin(uTime * (0.13 + 0.1 * seed) + seed * 55.0));
    vGlow = (0.75 + 0.5 * fract(seed * 29.0)) * flicker * (1.0 - 0.8 * gust);
    // its colour: from amber to a warm white, and about one in twelve a cool white
    vTint = fract(seed * 97.0) > 0.92
      ? vec3(0.86, 0.92, 1.0)
      : mix(vec3(1.0, 0.58, 0.22), vec3(1.0, 0.84, 0.58), fract(seed * 71.0));
    vec4 mv = modelViewMatrix * vec4(p, 1.0);
    gl_Position = projectionMatrix * mv;
    // its size: from seven tenths to thirteen tenths of a hand's width
    gl_PointSize = max(1.5, uSize * (0.7 + 0.6 * fract(seed * 53.0)) / -mv.z);
  }"""

        val FRAGMENT = """
  varying float vGlow;
  varying vec3 vTint;
  void main() {
    float d = length(gl_PointCoord - 0.5) * 2.0;
    float a = (1.0 - smoothstep(0.0, 1.0, d)) * vGlow;
    gl_FragColor = vec4(vTint * a * 1.6, 1.0);
    #include <tonemapping_fragment>
    #include <colorspace_fragment>
  }"""
    }
}
