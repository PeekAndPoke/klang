/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// A pattern across a figure's whole body, in how it throws back the light and not in its colour: where the pattern
// is, the skin is glossy and gives sharp bright highlights; elsewhere it is matt and gives soft dim ones. In the dark
// the body stays black, and the pattern shows only where a lamp catches it. It is drawn from the body's own shape at
// rest (p, in metres: x to the figure's left, y up, z ahead), so it stays on the skin when the figure moves.
// How far the cloth's ground is darkened, 0 to 1: the stage sets it to how much the front lamp is on. Under the back
// light alone the whole dress reflects (it looked best so in the dark); lit from the front its ground goes dark and
// the pattern stays bright.
export const dress = { value: 0 };

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
  // long hair hanging: fine strands down its length, a little wavy
  strands: 'smoothstep(0.25, 0.5, fract(p.x * 75.0 + 0.5 * sin(p.y * 11.0))) * (1.0 - smoothstep(0.62, 0.9, fract(p.x * 75.0 + 0.5 * sin(p.y * 11.0))))',
  // the braid laid flat, for a wall (x across, y up, in metres): the same woven lines between hoops, a pace wide
  braidWall: '1.0 - smoothstep(0.06, 0.16, min(min(abs(sin(p.x * 3.0 + 1.6 * sin(p.y * 4.2))), abs(sin(p.x * 3.0 - 1.6 * sin(p.y * 4.2)))), 1.6 * abs(sin(p.y * 2.1))))',
  // even hoops
  hoops: 'smoothstep(0.42, 0.58, fract(p.y * 10.0))',
};

/**
 * Gives [material] (a body's) the pattern [kind]; [gloss] and [matt] are the two roughnesses.
 * [shade]: how much darker the pattern also is in colour (0: in the reflections only, as on the bodies).
 * [glow]: { amount, width, color }: the surface shows faintly by itself, without any lamp, its pattern darker in it,
 *   fading to the sides (x) over [width] metres.
 * [glitter]: the share of the skin's grains that are spangles (see below).
 * [hair]: { centre (the head's middle, in the body's rest space), radius }: on the head, behind a hairline that
 * runs from the brow back over the ears, the pattern is hair combed straight back: fine glossy strands that run
 * from the front over the skull to the nape, a little uneven, as wet or oiled hair catches the light.
 */
export function pattern(material, kind, gloss = 0.12, matt = 0.85, hair = null, glitter = 0.14, shade = 0, glow = null) {
  material.metalness = 0;
  const combed = hair ? `
    { vec3 h = (vRest - vec3(${hair.centre.x.toFixed(4)}, ${hair.centre.y.toFixed(4)}, ${hair.centre.z.toFixed(4)})) / ${hair.radius.toFixed(4)};
      float line = h.z - 0.50 - 0.75 * h.y;                              // the hairline: further forward the higher up
      float in_hair = (1.0 - smoothstep(-0.06, 0.06, line)) * smoothstep(-0.95, -0.75, h.y) * (1.0 - smoothstep(1.25, 1.45, length(h)));
      float around = atan(h.x, h.y + 0.25);                              // which strand: round the axis from front to back
      float strand = fract(around * 9.5 + 0.35 * sin(h.z * 5.0 + around * 3.0));
      float shine = smoothstep(0.25, 0.5, strand) * (1.0 - smoothstep(0.62, 0.9, strand));
      roughnessFactor = mix(roughnessFactor, mix(0.62, 0.07, shine), in_hair); }` : '';
  // The cloth: less of a mirror, and it glitters. The skin is cut into grains of about 4 mm (in its rest shape, so
  // they stay on it); each grain has its own roughness and faces a little its own way, and a share of them
  // ([glitter], about one in seven) is a spangle: smooth, and tilted more. So a highlight is no streak but a
  // scatter of points that come and go as the figure moves. The spangles are in the cloth's ground, hardly on its
  // pattern (a tenth of them there): the pattern stays a calm gloss and the glitter lies between its figures.
  const GRAIN = `vec3 cell = floor(vRest * 250.0); float grain = fract(sin(dot(cell, vec3(12.9898, 78.233, 37.719))) * 43758.5453); float spangle = step(${(1 - glitter).toFixed(3)}, grain) * (1.0 - 0.9 * klOn);`;
  const sparkle = `\n{ ${GRAIN} roughnessFactor = clamp(roughnessFactor + 0.20 + 0.30 * (grain - 0.5), 0.05, 1.0); roughnessFactor = mix(roughnessFactor, 0.20, spangle); }`;
  material.onBeforeCompile = (shader) => {
    shader.uniforms.uDress = dress;
    shader.vertexShader = shader.vertexShader
      .replace('#include <common>', '#include <common>\nvarying vec3 vRest;')
      .replace('#include <begin_vertex>', '#include <begin_vertex>\nvRest = position;');
    shader.fragmentShader = shader.fragmentShader
      .replace('#include <common>', '#include <common>\nvarying vec3 vRest;\nuniform float uDress;')
      .replace('#include <roughnessmap_fragment>', `#include <roughnessmap_fragment>\nfloat klOn; { vec3 p = vRest; klOn = ${PATTERNS[kind]}; roughnessFactor = mix(${matt.toFixed(3)}, ${gloss.toFixed(3)}, klOn); }${sparkle}${combed}`)
      .replace('#include <color_fragment>', `#include <color_fragment>\n{ vec3 p = vRest; float figure = ${PATTERNS[kind]}; diffuseColor.rgb *= (1.0 - ${shade.toFixed(3)} * figure) * ${shade > 0 ? '1.0' : 'mix(mix(1.0, 0.15, uDress), 1.0, figure)'}; }`)
      // the cloth's ground is dark: it throws back an eighth of the light the pattern does, so lit from the front the
      // figure stays dark and its pattern (and the spangles in the ground) stand out (the maintainer, 2026-11-16)
      .replace('#include <lights_physical_fragment>', `#include <lights_physical_fragment>\n{ ${GRAIN} float lit = mix(mix(1.0, 0.12, uDress), 1.0, max(klOn, spangle)); material.specularColor *= lit; material.specularF90 *= lit; }`)
      .replace('#include <emissivemap_fragment>', glow ? `#include <emissivemap_fragment>\n{ vec3 p = vRest; totalEmissiveRadiance += vec3(${glow.color.join(', ')}) * ${glow.amount.toFixed(4)} * (1.0 - 0.6 * (${PATTERNS[kind]})) * exp(-(p.x * p.x) / ${(glow.width * glow.width).toFixed(2)}); }` : '#include <emissivemap_fragment>')
      .replace('#include <normal_fragment_maps>', `#include <normal_fragment_maps>\n{ ${GRAIN} normal = normalize(normal + (vec3(grain, fract(grain * 17.3), fract(grain * 113.7)) - 0.5) * (0.12 + 0.50 * spangle)); }`);
  };
  material.customProgramCacheKey = () => 'pattern-' + kind + (hair ? '-hair' : '') + '-frontdark-glitter' + glitter + '-shade' + shade + (glow ? '-glow' + glow.amount + '-' + glow.width : '');
  material.needsUpdate = true;
}

/**
 * The wall behind the stage (a plane, x across and y up in metres) has two faces (the maintainer, 2026-11-16: "first
 * wealthy ... then prison"):
 * - by itself, under the back light: an ornament, a damask of round medallions with a flower in each, ringed, small
 *   studs between them, seen ever so slightly in a faint glow of its own that fades to the sides;
 * - under the front lamp ([front] 0 to 1, set every frame through the returned function): a wire mesh, the braid
 *   laid flat (the maintainer: "like perfect like wire mesh"), dark on the lit wall. The ornament's glow goes as the lamp comes.
 */
export function wall(material, { amount = 0.017, width = 5.5, color = [0.55, 0.62, 1.0] } = {}) {
  const uniforms = { uFront: { value: 0 } };
  const ORNAMENT = `
    float u = p.x * 2.4, v = p.y * 2.4;
    float field = cos(u) * cos(v);
    float ring = 1.0 - smoothstep(0.0, 0.07, abs(abs(field) - 0.38));
    float second = 1.0 - smoothstep(0.0, 0.035, abs(abs(field) - 0.62));
    float flower = smoothstep(0.70, 0.86, abs(field)) * (0.45 + 0.55 * cos(6.0 * atan(sin(u), sin(v))));
    float stud = smoothstep(0.90, 0.97, abs(cos(u + 1.5708) * cos(v + 1.5708)));
    float ornament = clamp(max(max(ring, 0.6 * second), max(flower, stud)), 0.0, 1.0);`;
  // the mesh is the braid laid flat (PATTERNS.braidWall): woven wires between hoops, as wire fencing is woven
  const MESH = `float mesh = ${PATTERNS.braidWall};`;
  material.onBeforeCompile = (shader) => {
    shader.uniforms.uFront = uniforms.uFront;
    shader.vertexShader = shader.vertexShader
      .replace('#include <common>', '#include <common>\nvarying vec3 vRest;')
      .replace('#include <begin_vertex>', '#include <begin_vertex>\nvRest = position;');
    shader.fragmentShader = shader.fragmentShader
      .replace('#include <common>', '#include <common>\nvarying vec3 vRest;\nuniform float uFront;')
      .replace('#include <color_fragment>', `#include <color_fragment>\n{ vec3 p = vRest; ${MESH} diffuseColor.rgb *= 1.0 - 0.55 * mesh; }`)
      .replace('#include <emissivemap_fragment>', `#include <emissivemap_fragment>\n{ vec3 p = vRest; ${ORNAMENT} totalEmissiveRadiance += vec3(${color.join(', ')}) * ${amount.toFixed(4)} * (0.28 + 1.5 * ornament) * exp(-(p.x * p.x) / ${(width * width).toFixed(2)}) * (1.0 - uFront); }`);
  };
  material.customProgramCacheKey = () => 'wall-two-faces-braid';
  material.needsUpdate = true;
  return (front) => { uniforms.uFront.value = front; };
}
