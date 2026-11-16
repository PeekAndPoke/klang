/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// The audience, as far as it is seen: the small lights people hold up, nothing more. They stand before the stage in
// loose rows, the ground rising toward the back as in a stadium; each light flickers on its own and sways a
// little, as an arm does. They light nothing. The show's camera stands before the first row, so they are seen only
// when it looks back from behind the band, or from far away (the stadium shot).
import * as THREE from 'three';
import { hash01 } from './util.js';

// where they stand: from FIRST m before the stage's middle to LAST, WIDE to each side; from RISE on the ground climbs
const FIRST = 12, LAST = 60, WIDE = 26, RISE = 24, SLOPE = 0.25;

const VERTEX = `
  attribute float seed;
  uniform float uTime, uSize;
  varying float vGlow;
  void main() {
    vec3 p = position;
    p.x += 0.10 * sin(uTime * (0.7 + 0.5 * seed) + seed * 40.0);
    p.y += 0.04 * sin(uTime * (0.9 + 0.4 * seed) + seed * 71.0);
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

/** Adds the audience's lights to [scene]. Returns update(t, height): [height] is the picture's, in pixels. */
export function buildAudience(scene) {
  const places = [], seeds = [];
  // loose rows: a place about every metre, moved about, and not everyone holds a light up
  for (let z = FIRST; z < LAST; z += 1.1) {
    for (let x = -WIDE; x < WIDE; x += 0.95) {
      const n = places.length / 3 + z * 131.0 + x * 17.0;
      if (hash01(n * 1.7 + 0.3) > 0.72) { continue; }
      const at = z + 0.8 * (hash01(n * 2.3) - 0.5);
      places.push(x + 0.8 * (hash01(n * 3.1) - 0.5), 1.95 + 0.35 * hash01(n * 4.7) + SLOPE * Math.max(0, at - RISE), at);
      seeds.push(hash01(n * 5.9));
    }
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(places, 3));
  geometry.setAttribute('seed', new THREE.Float32BufferAttribute(seeds, 1));
  const material = new THREE.ShaderMaterial({
    uniforms: { uTime: { value: 0 }, uSize: { value: 60 } },
    vertexShader: VERTEX, fragmentShader: FRAGMENT,
    transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
  });
  const lights = new THREE.Points(geometry, material);
  lights.frustumCulled = false;
  scene.add(lights);
  return function update(t, height) {
    material.uniforms.uTime.value = t;
    material.uniforms.uSize.value = 0.30 * height;          // a light about a hand wide, whatever the picture's size
  };
}
