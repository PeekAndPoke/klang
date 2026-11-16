/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// The audience, as far as it is seen: the small lights people hold up, nothing more. They stand before the stage in
// loose rows, below the stage's edge and a few metres off it, the ground rising toward the back as in a stadium;
// each light flickers on its own and moves as its arm does: a slow sway when the music is quiet, wider and with a
// bounce on the beat the louder it gets. They light nothing. The show's camera stands before the first row, so they are seen only
// when it looks back from behind the band, or from far away (the stadium shot).
import * as THREE from 'three';
import { hash01 } from './util.js';

// where they stand: from FIRST m before the stage's middle to LAST, WIDE to each side; from RISE on the ground climbs
// the stage stands PIT m above the ground they stand on, so a held-up light is about level with the stage's floor
const FIRST = 16, LAST = 62, WIDE = 28, RISE = 26, SLOPE = 0.25, PIT = 1.4;

const VERTEX = `
  attribute float seed, group;
  uniform float uTime, uSize, uEnergy, uLow, uBeat;
  varying float vGlow;
  void main() {
    vec3 p = position;
    // the arm. People stand in groups, and a group moves as one: the same sway, the same beat, the same
    // liveliness; each of them only a little off the others. It sways slowly when the music is quiet, wider the
    // louder it is; with the music it goes up on the beat (some groups on every second beat), and the low end's
    // level pushes it further
    float lively = 0.5 + group;
    float off = 0.5 * (seed - 0.5);
    p.x += lively * (0.03 + 0.11 * uEnergy) * sin(uTime * (0.7 + 0.5 * group) + group * 40.0 + off);
    p.y += 0.03 * sin(uTime * (0.9 + 0.4 * group) + group * 71.0 + off);
    float beat = uBeat * (fract(group * 7.0) > 0.7 ? 0.5 : 1.0) + fract(group * 13.0) + 0.06 * (seed - 0.5);
    p.y += lively * uEnergy * (0.08 + 0.10 * uLow) * abs(sin(3.14159 * beat));
    // a small flame: never steady, and now and then it nearly goes out
    float flicker = 0.75 + 0.17 * sin(uTime * (7.0 + 5.0 * seed) + seed * 90.0) + 0.08 * sin(uTime * 23.0 + seed * 13.0);
    float gust = smoothstep(0.92, 1.0, sin(uTime * (0.13 + 0.1 * seed) + seed * 55.0));
    vGlow = flicker * (1.0 - 0.8 * gust);
    vec4 mv = modelViewMatrix * vec4(p, 1.0);
    gl_Position = projectionMatrix * mv;
    gl_PointSize = max(1.5, uSize / -mv.z);
  }`;
const FRAGMENT = `
  varying float vGlow;
  void main() {
    float d = length(gl_PointCoord - 0.5) * 2.0;
    float a = (1.0 - smoothstep(0.0, 1.0, d)) * vGlow;
    gl_FragColor = vec4(vec3(1.0, 0.70, 0.32) * a * 1.6, 1.0);
    #include <tonemapping_fragment>
    #include <colorspace_fragment>
  }`;

/**
 * Adds the audience's lights to [scene]. Returns update(t, height, energy, beat): [height] is the picture's, in
 * pixels; [energy] how loud the music is (0 to 1) and [low] its low end; [beat] the time in beats.
 */
export function buildAudience(scene) {
  const places = [], seeds = [], groups = [];
  // loose rows: a place about every metre, moved about, and not everyone holds a light up
  for (let z = FIRST; z < LAST; z += 0.85) {
    for (let x = -WIDE; x < WIDE; x += 0.7) {
      const n = places.length / 3 + z * 131.0 + x * 17.0;
      if (hash01(n * 1.7 + 0.3) > 0.88) { continue; }
      const at = z + 0.6 * (hash01(n * 2.3) - 0.5);
      places.push(x + 0.6 * (hash01(n * 3.1) - 0.5), 1.95 + 0.35 * hash01(n * 4.7) - PIT + SLOPE * Math.max(0, at - RISE), at);
      seeds.push(hash01(n * 5.9));
      // the group: friends stand together, some four metres across; the groups' edges are not straight
      const gx = Math.floor((x + 1.5 * Math.sin(at * 0.9)) / 4.2), gz = Math.floor((at + 1.2 * Math.sin(x * 0.8)) / 3.4);
      groups.push(hash01(gx * 12.9898 + gz * 78.233 + 0.5));
    }
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(places, 3));
  geometry.setAttribute('seed', new THREE.Float32BufferAttribute(seeds, 1));
  geometry.setAttribute('group', new THREE.Float32BufferAttribute(groups, 1));
  const material = new THREE.ShaderMaterial({
    uniforms: { uTime: { value: 0 }, uSize: { value: 60 }, uEnergy: { value: 0 }, uLow: { value: 0 }, uBeat: { value: 0 } },
    vertexShader: VERTEX, fragmentShader: FRAGMENT,
    transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
  });
  const lights = new THREE.Points(geometry, material);
  lights.frustumCulled = false;
  scene.add(lights);
  return function update(t, height, energy, low, beat) {
    material.uniforms.uTime.value = t;
    material.uniforms.uEnergy.value = energy;
    material.uniforms.uLow.value = low;
    material.uniforms.uBeat.value = beat;
    material.uniforms.uSize.value = 0.18 * height;          // a light about a hand wide, whatever the picture's size
  };
}
