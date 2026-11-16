/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// Fog from a fog machine, lying on the floor. It is a volume drawn in slices: a stack of thin level sheets from the
// floor to knee height, each showing the fog's density at its own height. The density is a noise that drifts with a
// slight wind and is stirred by a slower one (the turbulence); it is thick at the floor and only its billows reach
// the top, thickest near the two machines behind the band. The lamps light it, each in its cone and colour, so the
// beams show in it; it throws and takes no shadows.
import * as THREE from 'three';

const LAYERS = 12, TOP = 0.50, LAMPS = 5;

const VERTEX = `
  varying vec3 vWorld;
  void main() {
    vec4 world = modelMatrix * vec4(position, 1.0);
    vWorld = world.xyz;
    gl_Position = projectionMatrix * viewMatrix * world;
  }`;

const FRAGMENT = `
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
  }`;

/** Adds the fog to [scene]. Returns update(t, lamps, camera): call it each frame, after the lamps are set. */
export function groundFog(scene) {
  const shared = {
    uTime: { value: 0 },
    uPos: { value: Array.from({ length: LAMPS }, () => new THREE.Vector3()) },
    uDir: { value: Array.from({ length: LAMPS }, () => new THREE.Vector3(0, -1, 0)) },
    uCol: { value: Array.from({ length: LAMPS }, () => new THREE.Vector3()) },
    uCos: { value: new Array(LAMPS).fill(0) },
  };
  const sheet = new THREE.PlaneGeometry(26, 22);
  const layers = [];
  for (let i = 0; i < LAYERS; i++) {
    const share = i / (LAYERS - 1);
    const material = new THREE.ShaderMaterial({
      uniforms: { ...shared, uHeight: { value: share }, uAlpha: { value: 0 } },
      vertexShader: VERTEX, fragmentShader: FRAGMENT,
      transparent: true, depthWrite: false, side: THREE.DoubleSide,
      blending: THREE.CustomBlending, blendSrc: THREE.OneFactor, blendDst: THREE.OneMinusSrcAlphaFactor,
    });
    const mesh = new THREE.Mesh(sheet, material);
    mesh.rotation.x = -Math.PI / 2;
    mesh.position.y = 0.03 + (TOP - 0.03) * share;
    mesh.renderOrder = 10 + i;
    scene.add(mesh);
    layers.push(mesh);
  }
  const target = new THREE.Vector3();

  return function update(t, lamps, camera) {
    shared.uTime.value = t;
    lamps.slice(0, LAMPS).forEach(({ light }, i) => {
      shared.uPos.value[i].copy(light.position);
      shared.uDir.value[i].copy(target.copy(light.target.position).sub(light.position).normalize());
      shared.uCol.value[i].set(light.color.r, light.color.g, light.color.b).multiplyScalar(light.intensity);
      shared.uCos.value[i] = Math.cos(light.angle);
    });
    for (const layer of layers) {
      // a sheet seen edge-on would show as a line: it fades out as the camera comes to its height
      const off = Math.abs(camera.position.y - layer.position.y);
      layer.material.uniforms.uAlpha.value = 0.22 * Math.min(1, off / 0.25);
      // drawn from the far side toward the camera: from the floor up when it looks from above
      layer.renderOrder = 10 + (camera.position.y > layer.position.y ? 1 : -1) * layer.position.y * 10;
    }
  };
}
