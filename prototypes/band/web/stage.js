/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// The stage: floor, the wall behind it, the lamps, the drummer's riser. Nothing here knows the band.
import * as THREE from 'three';
import { params, seed, BPM, energyAt, times } from './show.js';
import { smooth, hash01 } from './util.js';
import { wall, dress } from './pattern.js';
import { groundFog } from './fog.js';
import { buildAudience } from './audience.js';

// The drummer sits on a riser at the back, so the front of the stage is free and he is seen over the others
export const RISER = 0.40;

/**
 * Builds the stage. Returns the renderer, the scene, the camera, the riser, and
 *   light(t, house, waiting): sets every lamp for this frame ([house]: 0 dark to 1; [waiting]: before the click)
 *   shade(kit): the faint dark on the floor under the kit and the riser
 */
export function buildStage() {
  const renderer = new THREE.WebGLRenderer({ antialias: true });
  renderer.setSize(innerWidth, innerHeight);
  renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  document.body.appendChild(renderer.domElement);

  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0x020308);
  scene.fog = new THREE.Fog(0x020308, 9, 22);
  const camera = new THREE.PerspectiveCamera(35, innerWidth / innerHeight, 0.1, 60);

  const floor = new THREE.Mesh(new THREE.PlaneGeometry(240, 240), new THREE.MeshStandardMaterial({ color: 0x0b0c10, roughness: 0.35, metalness: 0.2 }));
  floor.rotation.x = -Math.PI / 2;
  floor.receiveShadow = true;
  scene.add(floor);
  // A wall behind the stage, well clear of the riser: it takes the front lamp's light and the band's shadows. It
  // has two faces (see wall()): an ornament in its own faint glow under the back light, a wire mesh under the front
  // lamp. It takes no light from the other lamps (its colour is black unless the front lamp is on), and the fog
  // does not dim it.
  const back = new THREE.Mesh(new THREE.PlaneGeometry(60, 16), new THREE.MeshStandardMaterial({ color: 0x000000, roughness: 0.95, fog: false }));
  back.position.set(0, 8, -8);
  back.receiveShadow = true;
  const wallFront = wall(back.material, { amount: 0.014 });
  scene.add(back);
  scene.add(new THREE.AmbientLight(0x8090ff, 0.05));

  // The lamps are analog: old stage lamps on a tired supply. Each one's brightness wanders slowly on its own,
  // breathes a little, shivers faintly, and now and then sags for a moment as if something else drew current; none
  // is ever quite steady and no two do the same.
  const lamps = [];
  function spot(x, y, z, color, intensity, tx, tz, shadow) {
    const light = new THREE.SpotLight(color, intensity, 30, 0.5, 0.7, 1.2);
    light.position.set(x, y, z);
    light.target.position.set(tx, 1.0, tz);
    light.castShadow = !!shadow;
    light.shadow.mapSize.set(2048, 2048);
    light.shadow.bias = -0.0005;
    scene.add(light, light.target);
    lamps.push({ light, base: intensity, seed: lamps.length * 7.31 + 1.7, group: z < 0 ? 'back' : 'fill' });
  }
  spot(-4.5, 5.0, -4.0, 0x3070ff, 260, -2.0, 0, true);
  spot(4.5, 5.0, -4.0, 0xff3070, 260, 2.0, 0, true);
  spot(0, 5.5, -4.5, 0x70a0ff, 180, 0, -1, true);
  spot(0, 6, 8, 0xfff0e0, 25, 0, 0, false);
  // the lamp at the front: on the floor before the stage, looking up at the band and the wall, wide
  {
    const low = new THREE.SpotLight(0xff8420, 95, 40, 0.62, 0.5, 1.0);
    low.position.set(0.6, 0.25, 8.6);
    low.target.position.set(0, 2.6, -8);
    low.castShadow = true;
    low.shadow.mapSize.set(2048, 2048);
    low.shadow.bias = -0.0006;
    low.shadow.camera.near = 1;
    low.shadow.camera.far = 30;
    scene.add(low, low.target);
    lamps.push({ light: low, base: 95, seed: 31.7, group: 'front' });
  }

  const riser = new THREE.Mesh(new THREE.BoxGeometry(2.9, RISER, 2.2), new THREE.MeshStandardMaterial({ color: 0x08090c, roughness: 0.5, metalness: 0.2 }));
  riser.position.set(0.0, RISER / 2, -1.75);
  riser.castShadow = true;
  riser.receiveShadow = true;
  scene.add(riser);

  /**
   * How much the front lamp is on at [t] (0 to 1), and whether it strobes. The light changes sides now and then:
   * for eight bars in every forty the lamps behind go down and the one lamp low at the front comes up, so the band
   * throws big shadows up the wall; a second each way. Where in the song, the seed decides.
   */
  // The front lamp's colours: each time it comes up it has one of them, the seed's choice; the stroboscope is
  // always the cold blue. The amber is a deep one (a paler one looked like a building site's lamp, the maintainer,
  // 2026-11-16). ?fcolor=ff1408 sets one.
  const AMBER = 0xff8420, RED = 0xff1408, BLUE = 0x8fb8ff, VIOLET = 0xc428ff;
  const FRONT = [AMBER, RED, BLUE, RED, AMBER, VIOLET];
  // By hand, to try the colours: the key f turns the front lamp on in the first of them, f again in the next, and
  // after the last gives the lamp back to the show.
  const TRY = [AMBER, RED, BLUE, VIOLET];
  let tried = -1;
  addEventListener('keydown', (e) => {
    if (e.key.toLowerCase() === 'f' && !e.ctrlKey && !e.metaKey && !e.altKey) { tried = tried + 1 < TRY.length ? tried + 1 : -1; }
  });
  function frontColor(t) {
    if (tried >= 0) { return TRY[tried]; }
    if (params.has('fcolor')) { return parseInt(params.get('fcolor'), 16); }
    const turn = Math.floor((t / (240 / BPM) + (seed * 7) % 40) / 40);
    return FRONT[Math.floor(hash01(turn * 9.7 + seed * 1.3 + 0.4) * FRONT.length) % FRONT.length];
  }

  // Where the bass drum runs: eight hits or more in a row, each less than a quarter of a second after the one
  // before (a double sounding at once counts once). The stroboscope is on for just these stretches.
  const STROBE_LOW = 0.22, STROBE_REST = 0.35;
  const RUNS = [];
  {
    const kicks = times(['kick']).filter((k, i, all) => i === 0 || k - all[i - 1] > 0.03);
    let first = 0;
    for (let i = 1; i <= kicks.length; i++) {
      if (i === kicks.length || kicks[i] - kicks[i - 1] >= 0.25) {
        if (i - first >= 8) { RUNS.push([kicks[first], kicks[i - 1]]); }
        first = i;
      }
    }
  }

  function frontAt(t, waiting) {
    if (waiting) { return { front: 0, strobing: false }; }
    const bar = 240 / BPM;
    const x = ((t / bar + (seed * 7) % 40) % 40 + 40) % 40;
    if (tried >= 0) { return { front: 1, strobing: false }; }
    if (params.has('front')) { return { front: parseFloat(params.get('front')), strobing: false }; }
    if (t < 0) { return { front: 0, strobing: false }; }
    // and as a stroboscope, while the bass drum runs fast (see RUNS): it flashes on the sixteenths (about ten a
    // second, the maintainer's choice, 2026-11-16: the eighths were too slow). Not from black to full: between the
    // flashes the front lamp stays on a little and the other lamps stay at STROBE_REST of their light (all off and
    // full on was too hard, the maintainer, 2026-11-16). Flashing this fast over the whole picture can trigger seizures in people with
    // photosensitive epilepsy: a public showing needs a warning before it. ?nostrobe switches it off.
    if (!params.has('nostrobe') && RUNS.some(([from, to]) => t >= from - 0.02 && t < to + 0.08)) {
      const sixteenth = (t * BPM / 60 * 4) % 1;
      return { front: sixteenth < 0.28 ? 1 : STROBE_LOW, strobing: true };
    }
    return { front: smooth((x - 26) * bar / 1.0) * smooth((34 - x) * bar / 1.0), strobing: false };
  }

  // The moods: the colours of the two main lamps behind (the left one, the right one), and where each seems to
  // hang, off its first place (x, y, z in metres): as if other lamps of the rig had been turned on, so the shadows
  // lie a little differently. A mood stays for 24 bars; over the next 4 it goes over into another, the seed's
  // choice, never the same again at once. The show opens in the first.
  const MOODS = [
    { left: 0x3070ff, right: 0xff3070, l: [0, 0, 0], r: [0, 0, 0] },                  // cold against hot
    { left: 0x7a3cff, right: 0xff9030, l: [1.2, 0.5, 0.6], r: [-0.8, -0.4, 0.3] },    // violet and amber
    { left: 0x18b8c0, right: 0xe030c0, l: [-1.0, -0.5, 0.9], r: [1.1, 0.6, -0.4] },   // teal and magenta
    { left: 0x2038ff, right: 0x3c98ff, l: [0.8, 0.8, -0.5], r: [-1.3, 0.3, 0.8] },    // cold all over
    { left: 0xff2a2a, right: 0xff7a20, l: [-0.7, -0.6, 0.4], r: [0.9, -0.5, 0.7] },   // hot all over
    { left: 0xff3070, right: 0x3070ff, l: [1.4, 0.2, 0.2], r: [-1.4, 0.2, 0.2] },     // the first, the sides swapped
  ];
  const HOLD = 24, TURN = 4;
  const order = [0];
  function moodOf(k) {
    while (order.length <= k) {
      const n = order.length;
      const pick = 1 + Math.floor(hash01(n * 12.9 + seed * 0.71 + 3.3) * (MOODS.length - 1)) % (MOODS.length - 1);
      order.push(pick === order[n - 1] ? 1 + pick % (MOODS.length - 1) : pick);
    }
    return MOODS[order[Math.max(0, k)]];
  }
  const main = lamps.slice(0, 2).map((lamp) => ({ lamp, home: lamp.light.position.clone() }));
  const hue = new THREE.Color(), shift = new THREE.Vector3();
  function mood(t) {
    const x = Math.max(0, t) / (240 / BPM);
    const k = Math.floor(x / (HOLD + TURN));
    const u = smooth((x - k * (HOLD + TURN) - HOLD) / TURN);
    const a = moodOf(k), b = moodOf(k + 1);
    main.forEach(({ lamp, home }, side) => {
      lamp.light.color.setHex(side ? a.right : a.left).lerp(hue.setHex(side ? b.right : b.left), u);
      const from = side ? a.r : a.l, to = side ? b.r : b.l;
      lamp.light.position.copy(home).add(shift.set(from[0] + (to[0] - from[0]) * u, from[1] + (to[1] - from[1]) * u, from[2] + (to[2] - from[2]) * u));
    });
  }

  const audience = buildAudience(scene);
  // fog on the floor (?nofog: none)
  const fog = params.has('nofog') ? null : groundFog(scene);

  function light(t, house, waiting) {
    mood(t);
    const { front, strobing } = frontAt(t, waiting);
    back.material.color.setHex(0x8a8f9c).multiplyScalar(front);
    wallFront(Math.min(1, front));
    dress.value = Math.min(1, front);
    for (const lamp of lamps) {
      const { light, base, group } = lamp;
      if (group === 'front') { light.color.setHex(strobing ? BLUE : frontColor(t)); }
      const own = lamp.seed + (seed % 97) * 0.37;
      const wander = 0.060 * Math.sin(t * 0.23 + own) + 0.040 * Math.sin(t * 0.61 + own * 2.3) + 0.025 * Math.sin(t * 1.37 + own * 0.7);
      const shiver = 0.012 * Math.sin(t * 9.1 + own * 3.1) + 0.008 * Math.sin(t * 14.7 + own * 1.9);
      // a sag: a dip of a sixth, about every twenty seconds, at its own moment per lamp
      const phase = ((t + own * 5.3) % 19.7 + 19.7) % 19.7;
      const sag = phase < 1.2 ? 0.16 * Math.sin(Math.PI * phase / 1.2) ** 2 : 0;
      light.intensity = base * (1 + wander + shiver - sag) * house * (group === 'front' ? front : strobing ? STROBE_REST : group === 'back' ? 1 - 0.9 * front : 1 - 0.6 * front);
    }
    if (fog) { fog(t, lamps, camera); }
    // the crowd moves with the music: still while it waits, and by how loud it is once it plays
    const sound = waiting || t < 0 ? null : energyAt(t);
    audience(t, renderer.domElement.height, sound ? Math.min(1, sound.slow) : 0, sound ? Math.min(1, sound.low) : 0, t * BPM / 60);
  }

  /**
   * What stands on the floor darkens it right below itself, whatever the lamps do: the kit and the riser. None
   * under the players' feet (the maintainer, 2026-11-15): the lamps' own shadows hold them on the floor.
   */
  function shade(kit) {
    const c = document.createElement('canvas');
    c.width = c.height = 128;
    const g = c.getContext('2d');
    const fill = g.createRadialGradient(64, 64, 4, 64, 64, 64);
    fill.addColorStop(0, 'rgba(0,0,0,0.85)');
    fill.addColorStop(0.5, 'rgba(0,0,0,0.5)');
    fill.addColorStop(1, 'rgba(0,0,0,0)');
    g.fillStyle = fill;
    g.fillRect(0, 0, 128, 128);
    const texture = new THREE.CanvasTexture(c);
    const at = new THREE.Vector3();
    [[kit, 2.2, 1.9, 0.5, RISER], [riser, 3.6, 2.9, 0, 0]].forEach(([over, width, depth, forward, floorY], index) => {
      const mesh = new THREE.Mesh(new THREE.PlaneGeometry(1, 1), new THREE.MeshBasicMaterial({ map: texture, transparent: true, depthWrite: false, opacity: 0.38 }));
      mesh.rotation.x = -Math.PI / 2;
      mesh.scale.set(width, depth, 1);
      over.getWorldPosition(at);
      mesh.position.set(at.x, floorY + 0.004 + index * 0.0004, at.z + forward);
      scene.add(mesh);
    });
  }

  return { renderer, scene, camera, riser, light, shade };
}
