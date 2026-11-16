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

/**
 * Gives [material] (a body's) the pattern [kind]; [gloss] and [matt] are the two roughnesses.
 * [glitter]: a slight glitter in the highlights (see below).
 * [hair]: { centre (the head's middle, in the body's rest space), radius }: on the head, behind a hairline that
 * runs from the brow back over the ears, the pattern is hair combed straight back: fine glossy strands that run
 * from the front over the skull to the nape, a little uneven, as wet or oiled hair catches the light.
 */
export function pattern(material, kind, gloss = 0.12, matt = 0.85, hair = null, glitter = false) {
  material.metalness = 0;
  const combed = hair ? `
    { vec3 h = (vRest - vec3(${hair.centre.x.toFixed(4)}, ${hair.centre.y.toFixed(4)}, ${hair.centre.z.toFixed(4)})) / ${hair.radius.toFixed(4)};
      float line = h.z - 0.50 - 0.75 * h.y;                              // the hairline: further forward the higher up
      float in_hair = (1.0 - smoothstep(-0.06, 0.06, line)) * smoothstep(-0.95, -0.75, h.y) * (1.0 - smoothstep(1.25, 1.45, length(h)));
      float around = atan(h.x, h.y + 0.25);                              // which strand: round the axis from front to back
      float strand = fract(around * 9.5 + 0.35 * sin(h.z * 5.0 + around * 3.0));
      float shine = smoothstep(0.25, 0.5, strand) * (1.0 - smoothstep(0.62, 0.9, strand));
      roughnessFactor = mix(roughnessFactor, mix(0.62, 0.07, shine), in_hair); }` : '';
  // The cloth: less of a mirror, and grainy. The skin is cut into grains of about 4 mm (in its rest shape, so they
  // stay on it); each grain has its own roughness and faces a little its own way, which breaks a highlight up.
  // With [glitter], a slight one: about one grain in fourteen is a spangle, smooth and tilted more, so the highlight
  // has points in it that come and go as the figure moves.
  const GRAIN = `vec3 cell = floor(vRest * 250.0); float grain = fract(sin(dot(cell, vec3(12.9898, 78.233, 37.719))) * 43758.5453); float spangle = ${glitter ? 'step(0.93, grain)' : '0.0'};`;
  const sparkle = `\n{ ${GRAIN} roughnessFactor = clamp(roughnessFactor + 0.20 + 0.30 * (grain - 0.5), 0.05, 1.0); roughnessFactor = mix(roughnessFactor, 0.22, spangle); }`;
  material.onBeforeCompile = (shader) => {
    shader.vertexShader = shader.vertexShader
      .replace('#include <common>', '#include <common>\nvarying vec3 vRest;')
      .replace('#include <begin_vertex>', '#include <begin_vertex>\nvRest = position;');
    shader.fragmentShader = shader.fragmentShader
      .replace('#include <common>', '#include <common>\nvarying vec3 vRest;')
      .replace('#include <roughnessmap_fragment>', `#include <roughnessmap_fragment>\n{ vec3 p = vRest; float on = ${PATTERNS[kind]}; roughnessFactor = mix(${matt.toFixed(3)}, ${gloss.toFixed(3)}, on); }${sparkle}${combed}`)
      .replace('#include <normal_fragment_maps>', `#include <normal_fragment_maps>\n{ ${GRAIN} normal = normalize(normal + (vec3(grain, fract(grain * 17.3), fract(grain * 113.7)) - 0.5) * (0.10 + 0.34 * spangle)); }`);
  };
  material.customProgramCacheKey = () => 'pattern-' + kind + (hair ? '-hair' : '') + (glitter ? '-glitter' : '-grain');
  material.needsUpdate = true;
}
