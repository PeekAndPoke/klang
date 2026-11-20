/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.workshop.threejs.CompiledShader
import io.peekandpoke.klang.workshop.threejs.Uniform
import io.peekandpoke.klang.workshop.threejs.set

// The stage's edge (StageEdge) in the floor's shaders: where the mirror ends, and the outline that shows it.

/** [value] as a shader's number: with a point always (Kotlin/JS writes 7.0 as 7, a whole number to a shader). */
internal fun glsl(value: Double): String = value.toString().let { if ('.' in it || 'e' in it) it else "$it.0" }

/**
 * The stage's edge for a floor's fragment shader, as [StageEdge.distance] has it: `stageEdge(p)`, how far the floor's
 * point `p` (x, z) lies inside it, in metres; and `stageOutlined(c, p)`, the light `c` with the outline drawn over it
 * where `uStageOutline` is on ([OutlineLook]): a line some 4 cm wide, never thinner than a pixel and smooth at its
 * sides (by how fast the distance changes from pixel to pixel), a clear green-blue that reads on the dark floor.
 */
internal val STAGE_EDGE_GLSL = """
uniform float uStageOutline;
float stageEdge(vec2 p) {
  float back = p.y - ${glsl(StageEdge.BACK)};
  vec2 axes = vec2(${glsl(StageEdge.HALF_X)}, ${glsl(StageEdge.HALF_Z)});
  vec2 q = p / axes;
  vec2 g = p / (axes * axes);
  float slope = 2.0 * length(g);
  // at the very middle the oval has no slope: as far inside as it gets (no division by nought)
  float arc = slope > 1e-9 ? min((1.0 - dot(q, q)) / slope, axes.x) : axes.x;
  return min(back, arc);
}
vec3 stageOutlined(vec3 c, vec2 p) {
  if (uStageOutline <= 0.0) {
    return c;
  }
  float d = stageEdge(p);
  float w = max(0.02, fwidth(d));
  float line = 1.0 - smoothstep(w, w + fwidth(d), abs(d));
  return mix(c, vec3(0.15, 1.6, 0.9), line * uStageOutline);
}"""

/**
 * Draws the stage's outline over [shader]'s floor, as [outline] says (0 off, 1 on): the floor's place passed on to
 * the fragments as `vFloorWorld` (unless [placed] says it is already), [STAGE_EDGE_GLSL] in, and the outline over the
 * floor's light just before it is written (three.js's `#include <opaque_fragment>`), so after the mirror's image.
 */
internal fun outlineOn(shader: CompiledShader, outline: Uniform<Double>, placed: Boolean) {
    shader.uniforms["uStageOutline"] = outline

    if (!placed) {
        shader.vertexShader = shader.vertexShader
            .replaceFirst("#include <common>", "#include <common>\nvarying vec3 vFloorWorld;")
            .replaceFirst("#include <begin_vertex>", "#include <begin_vertex>\nvFloorWorld = (modelMatrix * vec4(transformed, 1.0)).xyz;")
        shader.fragmentShader = shader.fragmentShader.replaceFirst("#include <common>", "#include <common>\nvarying vec3 vFloorWorld;")
    }

    shader.fragmentShader = shader.fragmentShader
        .replaceFirst("#include <common>", "#include <common>\n$STAGE_EDGE_GLSL")
        .replaceFirst(
            "#include <opaque_fragment>",
            "outgoingLight = stageOutlined(outgoingLight, vFloorWorld.xz);\n#include <opaque_fragment>",
        )
}
