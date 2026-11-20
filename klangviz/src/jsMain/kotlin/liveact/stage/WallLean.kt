/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.lights.WorldLight
import io.peekandpoke.klang.viz.liveact.lights.WorldTime
import io.peekandpoke.klang.workshop.threejs.CompiledShader
import io.peekandpoke.klang.workshop.threejs.set
import io.peekandpoke.klang.workshop.threejs.uniform

/**
 * The lamps' light on a surface behind the band (the wall, the fence's wires, the surface behind the fence) at the two
 * worlds' time of day: by night it leans toward a cold moonlit grey blue and is dimmed ([WorldLight.wallCold],
 * [WorldLight.wallLight]), so the front lamp's warm light reads as night there, and the windows into the cold world
 * glow against a darker wall. The band is not touched: its lamp moods stay. Only the light the lamps throw on the
 * surface leans; what it gives of its own (the sun's glow, the ornament, the cold world) stays as it is.
 *
 * Into a material's shader with [into] (in its `onBeforeCompile`); each frame [world]. With the time off it is the
 * surface as made (a lean of 0, a light of 1).
 */
class WallLean {
    private val cold = uniform(0.0)
    private val light = uniform(1.0)

    /**
     * The lean at the time of day [world], [share] of it (0 to 1: the fence's wires take half, they stand in front of
     * the lamp's pools on the surface and would go flat with all of it).
     */
    fun world(world: WorldTime, share: Double = 1.0) {
        cold.value = WorldLight.wallCold(world) * share
        light.value = 1.0 + (WorldLight.wallLight(world) - 1.0) * share
    }

    /** Puts the lean into a standard material's [shader]: the lamps' direct light on it, after it is summed. */
    fun into(shader: CompiledShader) {
        shader.uniforms["uWallCold"] = cold
        shader.uniforms["uWallLight"] = light
        shader.fragmentShader = shader.fragmentShader
            .replaceFirst("#include <common>", "#include <common>\n$LEAN")
            .replaceFirst(
                "#include <lights_fragment_end>",
                "#include <lights_fragment_end>\nreflectedLight.directDiffuse = wallLean(reflectedLight.directDiffuse);\n" +
                    "reflectedLight.directSpecular = wallLean(reflectedLight.directSpecular);",
            )
    }

    private companion object {
        /**
         * The lean: the light's brightness kept, its colour turned toward a cold grey blue by `uWallCold`, the whole
         * times `uWallLight`.
         */
        val LEAN = """
uniform float uWallCold;
uniform float uWallLight;
vec3 wallLean(vec3 c) {
  float lum = dot(c, vec3(0.2126, 0.7152, 0.0722));
  return mix(c, lum * vec3(0.62, 0.78, 1.25), uWallCold) * uWallLight;
}"""
    }
}
