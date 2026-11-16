/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// A pattern across a figure's whole body, in how it throws back the light and not in its colour: where the pattern
// is, the skin is glossy and gives sharp bright highlights; elsewhere it is matt and gives soft dim ones. In the dark
// the body stays black, and the pattern shows only where a lamp catches it. It is drawn from the body's own shape at
// rest (p, in metres: x to the figure's left, y up, z ahead), so it stays on the skin when the figure moves.
const PATTERNS = {
  // broad bands round the body, waving like a tiger's
  tiger: 'smoothstep(0.42, 0.58, fract(p.y * 6.5 + 0.16 * sin(p.x * 13.0) + 0.12 * sin(p.z * 17.0)))',
  // fine upright pinstripes
  pinstripe: 'smoothstep(0.30, 0.45, abs(fract(atan(p.z, p.x) * 4.14) - 0.5) * 2.0)',
  // one stripe winding up the body
  spiral: 'smoothstep(0.40, 0.60, fract(p.y * 4.5 + atan(p.z, p.x) * 0.955))',
  // round spots, row by row offset
  spots: '1.0 - smoothstep(0.26, 0.34, length(fract(vec3(p.x * 11.0 + 0.5 * floor(p.y * 11.0), p.y * 11.0, p.z * 11.0)) - 0.5))',
  // a harlequin's diamonds
  diamonds: 'step(0.5, fract((floor(p.y * 9.0 + atan(p.z, p.x) * 1.91) + floor(p.y * 9.0 - atan(p.z, p.x) * 1.91)) * 0.5))',
  // fine lines woven round the body, crossing each other in a braid between thin hoops: the engraving on a
  // banknote or a watch's dial
  braid: '1.0 - smoothstep(0.10, 0.24, min(min(abs(sin(atan(p.z, p.x) * 5.0 + 1.6 * sin(p.y * 14.0))), abs(sin(atan(p.z, p.x) * 5.0 - 1.6 * sin(p.y * 14.0)))), 1.6 * abs(sin(p.y * 7.0))))',
  // even hoops
  hoops: 'smoothstep(0.42, 0.58, fract(p.y * 10.0))',
};

/** Gives [material] (a body's) the pattern [kind]; [gloss] and [matt] are the two roughnesses. */
export function pattern(material, kind, gloss = 0.12, matt = 0.85) {
  material.metalness = 0;
  material.onBeforeCompile = (shader) => {
    shader.vertexShader = shader.vertexShader
      .replace('#include <common>', '#include <common>\nvarying vec3 vRest;')
      .replace('#include <begin_vertex>', '#include <begin_vertex>\nvRest = position;');
    shader.fragmentShader = shader.fragmentShader
      .replace('#include <common>', '#include <common>\nvarying vec3 vRest;')
      .replace('#include <roughnessmap_fragment>', `#include <roughnessmap_fragment>\n{ vec3 p = vRest; float on = ${PATTERNS[kind]}; roughnessFactor = mix(${matt.toFixed(3)}, ${gloss.toFixed(3)}, on); }`);
  };
  material.customProgramCacheKey = () => 'pattern-' + kind;
  material.needsUpdate = true;
}
