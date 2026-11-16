/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// The band on stage, standalone (KLANG-00446). Step 1: the figures on a dark stage, all moving by the same motion
// code: a groove written in each figure's own measures.
//   ?t=1.25      freeze the time at 1.25 s (for stills)
//   ?view=side   front (default), side, or a figure's name for a close look
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { Rig } from './rig.js';
import { Drummer, testBeat } from './drummer.js';
import { Marimba } from './marimba.js';
import { Trumpet } from './trumpet.js';
import { StringPlayer } from './strings.js';
import { loadSong, drumHits, notesOf, strumsOf } from './song.js';
import { setEnergy } from './strike.js';
import { pattern } from './pattern.js';
import { enter, stepLegs, walkPose, capture, blend } from './entrance.js';
import { Choreography, HOME } from './camera.js';

const params = new URLSearchParams(location.search);
window.__debug = params.has('debug');
const frozen = params.has('t') ? parseFloat(params.get('t')) : null;
const view = params.get('view') || 'front';
// The song as recorded, when it is there (?test: the test beat instead)
const song = params.has('test') ? null : await loadSong('../ignored/schmetterling-events.json').catch(() => null);
const BPM = song ? song.bpm : 120;
// The recording's loudness over time (a value every 20 ms), measured once from the audio
const loudness = song ? await fetch('../ignored/schmetterling-energy.json').then((r) => (r.ok ? r.json() : null)).catch(() => null) : null;
function energyAt(t) {
  if (!loudness) { return { slow: 0.7, burst: 0, level: 0.7, low: 0.5, mid: 0.5, high: 0.5 }; }
  const i = Math.max(0, Math.min(loudness.level.length - 1, Math.round((t - loudness.offset) / loudness.step)));
  let burst = 0;
  for (let k = 0; k < 6; k++) { const j = Math.max(0, i - k); burst += Math.max(0, loudness.level[j] - loudness.slow[j]) / 6; }
  return { slow: loudness.slow[i], burst, level: loudness.level[i], low: loudness.low[i], mid: loudness.mid[i], high: loudness.high[i] };
}

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

const later = [];
const floor = new THREE.Mesh(new THREE.PlaneGeometry(40, 40), new THREE.MeshStandardMaterial({ color: 0x0b0c10, roughness: 0.35, metalness: 0.2 }));
floor.rotation.x = -Math.PI / 2;
floor.receiveShadow = true;
scene.add(floor);
// A wall behind the stage: it takes the light and the shadows. Dark and matt, so the back lamps (which shine away
// from it) leave it nearly black and a lamp from the front paints the band on it.
const back = new THREE.Mesh(new THREE.PlaneGeometry(40, 12), new THREE.MeshStandardMaterial({ color: 0x000000, roughness: 0.95 }));
back.position.set(0, 6, -5);
back.receiveShadow = true;
// the wall wears the frontwoman's pattern, the braid, laid flat and large: darker in its lines, matt all over
later.push(() => pattern(back.material, 'braidWall', 0.95, 0.95, null, 0, 0.5, { amount: 0.017, width: 5.5, color: [0.55, 0.62, 1.0] }));
scene.add(back);
scene.add(new THREE.AmbientLight(0x8090ff, 0.05));

// The lamps are analog: old stage lamps on a tired supply. Each one's brightness wanders slowly on its own, breathes
// a little, shivers faintly, and now and then sags for a moment as if something else drew current; none is ever
// quite steady and no two do the same.
const lamps = [];
// the house: before the click the stage lies nearly dark; on the click the lamps come up at once
let house = 1;
// The light changes sides now and then: for eight bars in every forty the lamps behind go down and one lamp low at the
// front comes up, so the band throws big shadows up the wall. A second each way.
let front = 0;
let strobing = false;
function frontAt(t) {
  if (frozen === null && audio && !playing) { return 0; }
  const bar = 240 / BPM;
  const x = ((t / bar) % 40 + 40) % 40;
  if (params.has('front')) { return parseFloat(params.get('front')); }
  strobing = false;
  if (t < 0) { return 0; }
  // and as a stroboscope: for two bars in every forty it flashes on the sixteenths (about ten a second, the
  // maintainer's choice, 2026-11-16: the eighths were too slow), all other lamps off, the band frozen on the wall
  // in each flash. Flashing this fast over the whole picture can trigger seizures in people with photosensitive
  // epilepsy: a public showing needs a warning before it. ?nostrobe switches it off.
  strobing = x >= 12 && x < 14 && !params.has('nostrobe');
  if (strobing) {
    const sixteenth = (t * BPM / 60 * 4) % 1;
    return sixteenth < 0.28 ? 1 : 0;
  }
  return smooth01((x - 26) * bar / 1.0) * smooth01((34 - x) * bar / 1.0);
}
function analog(t) {
  front = frontAt(t);
  // the wall is black unless the front lamp is on: it takes no light from the other lamps at all
  back.material.color.setHex(0x8a8f9c).multiplyScalar(front);
  // without the front lamp it is ever so slightly there by itself (its own faint glow, see pattern()): over the full
  // width, darker to the sides; it catches no other lamp
  for (const { light, base, seed, group } of lamps) {
    const wander = 0.060 * Math.sin(t * 0.23 + seed) + 0.040 * Math.sin(t * 0.61 + seed * 2.3) + 0.025 * Math.sin(t * 1.37 + seed * 0.7);
    const shiver = 0.012 * Math.sin(t * 9.1 + seed * 3.1) + 0.008 * Math.sin(t * 14.7 + seed * 1.9);
    // a sag: a dip of a sixth, about every twenty seconds, at its own moment per lamp
    const phase = ((t + seed * 5.3) % 19.7 + 19.7) % 19.7;
    const sag = phase < 1.2 ? 0.16 * Math.sin(Math.PI * phase / 1.2) ** 2 : 0;
    light.intensity = base * (1 + wander + shiver - sag) * house * (group === 'front' ? front : strobing ? 0 : group === 'back' ? 1 - 0.9 * front : 1 - 0.6 * front);
  }
}
function spot(x, y, z, color, intensity, tx, tz, shadow) {
  const light = new THREE.SpotLight(color, intensity, 30, 0.5, 0.7, 1.2);
  light.position.set(x, y, z);
  light.target.position.set(tx, 1.0, tz);
  light.castShadow = !!shadow;
  light.shadow.mapSize.set(2048, 2048);
  light.shadow.bias = -0.0005;
  scene.add(light, light.target);
  lamps.push({ light, base: intensity, seed: lamps.length * 7.31 + 1.7, group: z < 0 ? 'back' : 'fill' });
  return light;
}
spot(-4.5, 5.0, -4.0, 0x3070ff, 260, -2.0, 0, true);
spot(4.5, 5.0, -4.0, 0xff3070, 260, 2.0, 0, true);
spot(0, 5.5, -4.5, 0x70a0ff, 180, 0, -1, true);
spot(0, 6, 8, 0xfff0e0, 25, 0, 0, false);
// the lamp at the front: on the floor before the stage, looking up at the band and the wall, wide
{
  const low = new THREE.SpotLight(0xffb070, 95, 40, 0.62, 0.5, 1.0);
  low.position.set(0.6, 0.25, 8.6);
  low.target.position.set(0, 2.2, -5);
  low.castShadow = true;
  low.shadow.mapSize.set(2048, 2048);
  low.shadow.bias = -0.0006;
  low.shadow.camera.near = 1;
  low.shadow.camera.far = 30;
  scene.add(low, low.target);
  lamps.push({ light: low, base: 95, seed: 31.7, group: 'front' });
}

// Where each player stands: x to the audience's right, z toward the audience
// The drummer sits on a riser at the back, so the front of the stage is free and he is seen over the others
const RISER = 0.40;
const PLACES = {
  drums: [0.1, -2.1, 0, RISER], bass: [-2.15, 0.4, 0.15], guitar1: [-3.0, 1.7, 0.3],
  eimer: [-0.7, 2.2, 0.1], marimba: [1.7, 0.4, -0.15], guitar2: [3.0, 1.7, -0.3],
};
const riser = new THREE.Mesh(new THREE.BoxGeometry(2.9, RISER, 2.2), new THREE.MeshStandardMaterial({ color: 0x08090c, roughness: 0.5, metalness: 0.2 }));
riser.position.set(0.0, RISER / 2, -1.75);
riser.castShadow = true;
riser.receiveShadow = true;
scene.add(riser);
// Each player's pattern, seen only in how the body throws back the light
const SKINS = { drums: 'tiger', bass: 'pinstripe', guitar1: 'spiral', guitar2: 'spots', marimba: 'diamonds', eimer: 'braid' };
// Who wears hair as a pattern in the light, combed straight back
const HAIR = { guitar1: true };
// Everyone glitters; the two women a little more (the share of spangles in the skin)
const GLITTER = { eimer: 0.21, marimba: 0.21 };
// the head's middle and size, in the body's rest space: a little above and behind where the head's bone starts
function hairOf(rig) {
  const neck = rig.root.worldToLocal(rig.rest.head.world.clone());
  const size = rig.height / 1.60;
  return { centre: new THREE.Vector3(neck.x, neck.y + 0.115 * size, neck.z - 0.012 * size), radius: 0.104 * size };
}
const rigs = {};
const loader = new GLTFLoader();
await Promise.all(Object.entries(PLACES).map(async ([name, [x, z, turn, y = 0]]) => {
  const gltf = await loader.loadAsync(`../models/${name}.glb`);
  gltf.scene.position.set(x, y, z);
  gltf.scene.rotation.y = turn;
  scene.add(gltf.scene);
  rigs[name] = new Rig(gltf.scene);
  gltf.scene.traverse((o) => {
    if (o.isSkinnedMesh) {
      for (const m of Array.isArray(o.material) ? o.material : [o.material]) {
        if (m.name.endsWith('_hair')) { pattern(m, 'strands', 0.07, 0.62, null, 0.05); }
        if (m.name.endsWith('_dark')) { pattern(m, SKINS[name], 0.12, 0.85, HAIR[name] ? hairOf(rigs[name]) : null, GLITTER[name] ?? 0.14); }
      }
    }
  });
}));

// Each player's character: how they move with the music apart from playing (see Mover in strike.js)
const CHARACTERS = {
  // the engine of the band: on every beat, steady, a touch ahead
  drums: { seed: 11, depth: 1.0, beats: [1, 0.7, 0.9, 0.7], lag: -0.02, soft: 0.10, chance: 0.15, look: 0.05, tilt: 0.03, lean: 0.01,
    parts: { head: 1.0, trunk: 1.3, knees: 0.8 } },                 // from the trunk: his whole upper body goes
  // the steady one: late, mostly on one and three, always the same whatever the band does; looks about slowly
  bass: { seed: 23, steady: 0.9, depth: 0.9, beats: [1, 0.2, 0.7, 0.2], lag: 0.07, soft: 0.18, chance: 0.12, skip: 0, look: 0.22, tilt: 0.06, lean: 0.02,
    parts: { head: 0.6, trunk: 0.8, knees: 1.0, sway: 1.3 } },      // from the knees and hips, the head hardly
  // loose and restless: uneven, looks round a lot, skips nods
  guitar1: { seed: 37, depth: 0.8, beats: [0.9, 0.55, 0.9, 0.55], lag: 0.02, soft: 0.13, chance: 0.6, skip: 0.18, look: 0.30, tilt: 0.10, lean: 0.03,
    parts: { head: 0.9, trunk: 1.5, knees: 0.7, sway: 1.2 } },      // leans his whole trunk into it
  // the headbanger, but only in fits: about a quarter of the time he goes all the way, else he nods like the others
  guitar2: { seed: 41, fits: { every: 12, share: 0.22, calm: 0.30 }, depth: 0.85, beats: [1, 0.85, 1, 0.85], lag: -0.03, soft: 0.11, chance: 0.25, look: 0.08, tilt: 0.05, lean: 0.02,
    parts: { head: 1.15, trunk: 0.9, knees: 0.8, sway: 0.6 } },      // all head, planted feet
  // composed: small nods, her head tilting from side to side
  marimba: { seed: 53, depth: 0.5, beats: [1, 0.25, 0.6, 0.25], lag: 0.03, soft: 0.18, chance: 0.35, look: 0.10, tilt: 0.12, lean: 0.015,
    parts: { head: 0.8, trunk: 0.6, knees: 0.5 } },                 // upright, quiet below the neck
  // the frontman: the balanced one. Every part takes the beat alike, on time, even, with little chance in it
  eimer: { seed: 67, depth: 0.9, beats: [1, 0.5, 0.8, 0.5], lag: 0.0, soft: 0.13, chance: 0.2, skip: 0, look: 0.14, tilt: 0.04, lean: 0.02,
    parts: { head: 1.0, trunk: 1.0, knees: 1.0, sway: 1.0 } },
};
const drummer = new Drummer(rigs.drums, song ? drumHits(song) : testBeat(64, BPM), BPM, CHARACTERS.drums);
const testTune = () => Array.from({ length: 256 }, (_, i) => ({ t: i * 0.25, midi: 64 + [0, 3, 7, 10, 12, 10, 7, 3][i % 8] + (i % 32 >= 16 ? 5 : 0), v: i % 4 === 0 ? 1 : 0.6 }));
const marimba = new Marimba(rigs.marimba, song ? notesOf(song, 'lead') : testTune(), BPM, CHARACTERS.marimba);
const testCall = () => Array.from({ length: 128 }, (_, i) => ({ t: 1 + i * 0.75 + (i % 8 >= 6 ? 3 : 0), d: 0.6, midi: 65 + [0, 2, 3, 7, 5, 3, 10, 12][i % 8], v: 1 })).sort((a, b) => a.t - b.t);
const eimer = new Trumpet(rigs.eimer, song ? notesOf(song, 'trommel') : testCall(), BPM, CHARACTERS.eimer);
const testRiff = (step, low) => Array.from({ length: 512 }, (_, i) => ({ t: i * step, midi: low + [0, 0, 3, 0, 5, 0, 7, 3][Math.floor(i * step * 2) % 8], v: i % 2 === 0 ? 1 : 0.7 }));
// where the camera is: who looks at the viewer looks there
const viewer = HOME.pos.clone();
const players = [
  ['bass', new StringPlayer(rigs.bass, song ? strumsOf(song, ['bass']) : testRiff(0.5, 28), BPM, { kind: 'bass', lean: 0.10, bang: 0.16, bounce: 0.045, character: CHARACTERS.bass })],
  ['guitar1', new StringPlayer(rigs.guitar1, song ? strumsOf(song, ['guitar1']) : testRiff(0.25, 52), BPM, { kind: 'guitar', lean: -0.05, bang: 0.14, bounce: 0.025, character: CHARACTERS.guitar1 })],
  ['guitar2', new StringPlayer(rigs.guitar2, song ? strumsOf(song, ['guitar2', 'guitar3']) : testRiff(0.125, 40), BPM, { kind: 'guitar', outline: 'single', lean: 0.10, bang: 0.24, bounce: 0.028, character: CHARACTERS.guitar2,
    glance: { at: viewer, every: 11, chance: 0.5 } })],
];

// The walk-in: the stage's clock starts INTRO seconds before the song. Each player comes from off the stage (x, z)
// between two times; the drummer first, from the back and up onto the riser, the frontwoman last.
const INTRO = 15;
const ENTRANCES = {
  drums: { from: [0.1, -7.5], t: [-15, -9] },
  bass: { from: [-8.5, 0.9], t: [-13.5, -7.5] },
  guitar2: { from: [8.5, 2.0], t: [-12.5, -6.5] },
  guitar1: { from: [-8.5, 2.1], t: [-11.5, -5.5] },
  marimba: { from: [8.5, 0.9], t: [-10.5, -4.8] },
  eimer: { from: [-8.5, 2.9], t: [-8.5, -2.4] },
};
let introAt = null;       // when the show was started, by the page's clock
let playing = false;      // the walk-in is over and the audio runs
function showTime() {
  if (frozen !== null) { return frozen; }
  if (!audio) { return (performance.now() - started) / 1000 - INTRO; }
  if (playing) { return audio.currentTime; }
  if (introAt === null) { return -INTRO; }
  const t = (performance.now() - introAt) / 1000 - INTRO;
  if (t < 0) { return t; }
  playing = true;
  audio.play();
  return 0;
}
const stickUp = new THREE.Vector3(0, 1, 0);

// The audio of the same render: its time is the stage's time
const audio = song && frozen === null ? new Audio('../ignored/schmetterling.wav') : null;
if (audio) {
  const hint = document.createElement('div');
  hint.textContent = 'click to start';
  hint.style.transition = 'opacity 0.4s';
  hint.style.font = '18px monospace';
  hint.style.cssText = 'position:fixed;left:0;right:0;bottom:18px;text-align:center;color:#9ab;font:14px monospace';
  document.body.appendChild(hint);
  const toggle = () => {
    if (!playing) { if (introAt === null) { introAt = performance.now(); } } else if (audio.paused) { audio.play(); } else { audio.pause(); }
    hint.style.opacity = 0;
  };
  addEventListener('click', toggle);
  addEventListener('keydown', (e) => {
    if (e.code === 'Space') { toggle(); e.preventDefault(); }
    // right, during the walk-in: skip it
    if (e.code === 'ArrowRight') { if (!playing) { playing = true; introAt = introAt ?? 0; audio.play(); } else { audio.currentTime += 10; } }
    if (e.code === 'ArrowLeft') { audio.currentTime = Math.max(0, audio.currentTime - 10); }
  });
}

// What stands on the floor darkens it right below itself, whatever the lamps do: nobody floats
const blobTexture = (() => {
  const c = document.createElement('canvas');
  c.width = c.height = 128;
  const g = c.getContext('2d');
  const fill = g.createRadialGradient(64, 64, 4, 64, 64, 64);
  fill.addColorStop(0, 'rgba(0,0,0,0.85)');
  fill.addColorStop(0.5, 'rgba(0,0,0,0.5)');
  fill.addColorStop(1, 'rgba(0,0,0,0)');
  g.fillStyle = fill;
  g.fillRect(0, 0, 128, 128);
  return new THREE.CanvasTexture(c);
})();
const blobs = [];
function blob(follow, width, depth, forward = 0, floor = 0) {
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(1, 1), new THREE.MeshBasicMaterial({ map: blobTexture, transparent: true, depthWrite: false, opacity: 0.38 }));
  mesh.rotation.x = -Math.PI / 2;
  mesh.scale.set(width, depth, 1);
  mesh.position.y = floor + 0.004 + blobs.length * 0.0004;
  scene.add(mesh);
  blobs.push({ mesh, follow, forward });
}
// none under the players' feet (the maintainer, 2026-11-15): the lamps' own shadows hold them on the floor
blob(drummer.kit, 2.2, 1.9, 0.5, RISER); blob(riser, 3.6, 2.9);
const blobAt = new THREE.Vector3();
function placeBlobs() {
  for (const b of blobs) {
    b.follow.getWorldPosition(blobAt);
    const turn = b.forward ? rigs.drums.root.rotation.y * 0 : 0;
    b.mesh.position.x = blobAt.x + turn;
    b.mesh.position.z = blobAt.z + b.forward;
  }
}

// Eye contact: every now and then two of them look at each other for a few seconds, then back to their playing.
// Chance picks the moment and the pair, the same at the same place in the song. Pairs that have something to say
// to each other: the rhythm section, neighbours, the frontwoman and whoever she passes.
const PAIRS = [['bass', 'drums'], ['guitar1', 'bass'], ['eimer', 'guitar1'], ['marimba', 'guitar2'], ['drums', 'marimba'],
  ['eimer', 'marimba'], ['eimer', 'drums'], ['bass', 'eimer'], ['guitar2', 'drums'], ['guitar1', 'guitar2']];
const CONTACT = { every: 11, chance: 0.4 };
let herNotes = null;
function contactAt(t) {
  if (t < 1) { return null; }
  const slot = Math.floor(t / CONTACT.every);
  const roll = (n) => { const x = Math.sin((slot + n) * 12.9898 + 311.7) * 43758.5453; return x - Math.floor(x); };
  if (roll(0) > CONTACT.chance) { return null; }
  // one of the two starts it; the other notices and answers one to three seconds later
  const drawn = PAIRS[Math.floor(roll(1) * PAIRS.length) % PAIRS.length];
  const pair = roll(4) < 0.5 ? drawn : [drawn[1], drawn[0]];
  const late = 1.0 + 2.0 * roll(5);
  const start = slot * CONTACT.every + 0.2 + roll(2) * 1.2;
  const hold = 0.7 + 0.9 * roll(3);                 // how long they really look at each other: short
  const meet = late + 1.3;                          // when both look
  const away = meet + hold;                         // when the first one looks away again
  // she does not look away while she plays: the trumpet is at her lips
  herNotes = herNotes || times(['trommel']);
  if (pair.includes('eimer') && herNotes.some((x) => x > start - 3 && x < start + away + 3)) { return null; }
  const x = t - start;
  // No staring. The first one glances over, a second or so, and is half back at the playing when the other
  // notices and looks; then the first looks again and they share it briefly. A head never turns all the way: a
  // good half, as people look with their eyes; the beat's nod goes on underneath.
  const glance = 0.50 * smooth01(x / 1.2) * smooth01((2.0 - x) / 0.9);
  const again = 0.62 * smooth01((x - late - 0.4) / 0.9) * smooth01((away + 0.9 - x) / 0.9);
  const second = 0.58 * smooth01((x - late) / 1.1) * smooth01((away + 0.35 + 0.9 - x) / 0.9);
  // as their eyes meet, a small nod to each other, the second a moment after the first
  const greet = (at) => { const d = (x - at) / 0.28; return Math.exp(-d * d); };
  return { pair, w: [Math.max(glance, again), second], nod: [greet(meet + 0.25), greet(meet + 0.45)] };
}
function eyeContact(t) {
  const contact = contactAt(t);
  if (!contact || (contact.w[0] <= 0 && contact.w[1] <= 0)) { return; }
  const [a, b] = contact.pair.map((name) => rigs[name]);
  a.update(); b.update();
  const eyes = (rig) => rig.bones.head.getWorldPosition(new THREE.Vector3()).add(new THREE.Vector3(0, 0.08, 0));
  const ea = eyes(a), eb = eyes(b);
  a.faceTo(eb, contact.w[0]);
  b.faceTo(ea, contact.w[1]);
  a.turn('head', X, 0.09 * contact.nod[0]);
  b.turn('head', X, 0.09 * contact.nod[1]);
}

const smooth01 = (x) => { const c = Math.min(1, Math.max(0, x)); return c * c * (3 - 2 * c); };
for (const run of later) { run(); }
const X = new THREE.Vector3(1, 0, 0), Y = new THREE.Vector3(0, 1, 0), Z = new THREE.Vector3(0, 0, 1);

/** A stroke's height over its beat: up slowly, down fast; 0 at the hit, at both ends. */
const stroke = (p) => Math.sin(Math.PI * p * p);

/**
 * The groove: one motion for every figure, in the figure's own measures (its hip height, its shoulders, its arm's
 * length). The knees give on the beat, the trunk leans, the head nods, the hands strike in turn at two places in
 * front; the feet stay where they stand.
 */
function groove(rig, beat, phase) {
  const b = beat + phase;
  const pulse = 0.5 - 0.5 * Math.cos(2 * Math.PI * b);              // 0 on the beat, 1 between
  const sway = Math.sin(Math.PI * b);                               // left, right, a beat each
  const leg = rig.pelvis.y;

  rig.reset();
  rig.update();
  rig.moveHips(new THREE.Vector3(sway * 0.035 * leg, -(0.05 + 0.045 * (1 - pulse)) * leg, 0.02 * leg));
  rig.turn('belly', X, 0.10 + 0.05 * (1 - pulse));
  rig.turn('chest', Z, -sway * 0.06);
  rig.turn('head', X, 0.10 + 0.16 * Math.pow(1 - pulse, 2));

  for (const side of ['L', 'R']) {
    const s = side === 'L' ? 1 : -1;
    rig.foot(side, rig.root.localToWorld(rig.ankle[side].clone()), rig.dir(0.15 * s, 0, 1));

    // the hand: strikes every second beat, the two in turn
    const mine = (b / 2 + (side === 'L' ? 0.5 : 0)) % 1;
    const sh = rig.shoulder[side];
    const reach = rig.armLength;
    const target = rig.world(sh.x + 0.10 * s * reach, sh.y - 0.78 * reach + 0.50 * reach * stroke(mine), sh.z + 0.62 * reach);
    rig.hand(side, target, rig.dir(0.5 * s, -1.0, -0.5));
  }

  // the eyes: bright at the hit, fading after
  if (rig.eyes) { rig.eyes.emissiveIntensity = 3 + 22 * Math.pow(1 - (b % 1), 3); }
}

// The camera's choreography, when the song is there (?still: the wide view only)
const times = (tags) => (song ? tags.flatMap((tag) => (song.byTag[tag] || []).map((e) => e.t)).sort((a, b) => a - b) : []);
const choreography = song && !params.has('still') ? new Choreography(rigs, {
  drums: times(['kick', 'snare']), bass: times(['bass']), guitar1: times(['guitar1']), guitar2: times(['guitar2', 'guitar3']),
  marimba: times(['lead']), eimer: times(['trommel']),
}, 240 / BPM) : null;
let showT = 0;
// ?head=<player>[&from=side|front|back|threequarter]: a close look at one head, with a plain inspection light, to
// judge a head's shape (a still for the maintainer; no part of the show)
if (params.has('head')) {
  scene.add(new THREE.AmbientLight(0xffffff, 0.9));
  const lamp = new THREE.DirectionalLight(0xffffff, 2.2);
  scene.add(lamp, lamp.target);
  window.__inspect = lamp;
}
function look() {
  if (params.has('head') && rigs[params.get('head')]) {
    const rig = rigs[params.get('head')];
    const at = rig.bones.head.getWorldPosition(new THREE.Vector3()).add(new THREE.Vector3(0, 0.07 * rig.height / 1.6, 0));
    const from = { side: [1, 0.05, 0], front: [0, 0.05, 1], back: [0, 0.1, -1], threequarter: [0.7, 0.1, 0.7] }[params.get('from') || 'side'];
    camera.position.copy(at).addScaledVector(rig.dir(...from).normalize(), 1.7);
    camera.lookAt(at);
    if (camera.fov !== 24) { camera.fov = 24; camera.updateProjectionMatrix(); }
    window.__inspect.position.copy(camera.position).add(new THREE.Vector3(0.4, 0.8, 0));
    window.__inspect.target.position.copy(at);
    return;
  }
  if (params.has('cam')) {
    const c = params.get('cam').split(',').map(Number), a = (params.get('at') || '0,1,0').split(',').map(Number);
    camera.position.set(c[0], c[1], c[2]); camera.lookAt(a[0], a[1], a[2]); return;
  }
  if (view === 'side') { camera.position.set(8.5, 2.2, 5.5); camera.lookAt(0, 1.0, -0.4); return; }
  if (rigs[view]) {
    const p = rigs[view].root.position;
    camera.position.set(p.x + 1.6, 1.5, p.z + 4.2); camera.lookAt(p.x, 1.0, p.z); return;
  }
  if (choreography) {
    const shot = choreography.at(showT);
    camera.position.copy(shot.pos);
    camera.lookAt(shot.at);
    if (Math.abs(camera.fov - shot.fov) > 1e-3) { camera.fov = shot.fov; camera.updateProjectionMatrix(); }
    return;
  }
  camera.position.set(0, 1.85, 9.4); camera.lookAt(0, 1.0, 0.0);
}

addEventListener('resize', () => {
  renderer.setSize(innerWidth, innerHeight);
  camera.aspect = innerWidth / innerHeight;
  camera.updateProjectionMatrix();
});

const hud = document.getElementById('hud');
const started = performance.now();
// frames a second, counted over each half second, and the longest single frame in it (a stutter shows there)
let fps = 0, worst = 0, counted = 0, since = performance.now(), before = performance.now();
let frames = 0;
function frame() {
  const t = showTime();
  const beat = t * BPM / 60;
  setEnergy(energyAt(t));
  const walks = {};
  for (const name of Object.keys(ENTRANCES)) { walks[name] = enter(rigs[name], ENTRANCES[name], PLACES[name], t); }
  // the string players and the marimba player walk as they stand, the legs stepping
  for (const [name, player] of players) { player.update(t); stepLegs(rigs[name], walks[name]); }
  marimba.update(t);
  stepLegs(rigs.marimba, walks.marimba);
  // the frontwoman: put where her walk-in has her; once there she is her own (her place, her strolls)
  const her = walks.eimer.since < 0 ? { at: rigs.eimer.root.position.clone(), turn: rigs.eimer.root.rotation.y, going: 0, speed: 0, far: 0 } : null;
  eimer.update(t, her);
  stepLegs(rigs.eimer, walks.eimer);
  // the drummer walks in with the sticks in his hands, and sits down once he is at the stool
  drummer.update(t);
  const sit = smooth01(walks.drums.since / 1.6);
  if (sit < 1) {
    const seated = capture(rigs.drums);
    walkPose(rigs.drums, walks.drums);
    blend(rigs.drums, capture(rigs.drums), seated, sit);
    for (const side of ['L', 'R']) {
      const hand = rigs.drums.bones['hand' + side];
      const d = new THREE.Vector3(0, 1, 0).applyQuaternion(hand.getWorldQuaternion(new THREE.Quaternion()));
      const stick = drummer.sticks[side];
      stick.position.copy(hand.getWorldPosition(new THREE.Vector3())).addScaledVector(d, drummer.stickLength * 0.22);
      stick.quaternion.setFromUnitVectors(stickUp, d);
    }
  }
  eyeContact(t);
  // the eyes: a third less bright, and never steady. Their glow wanders by smooth noise, each pair on its own (two
  // layers: a slow drift and a quicker waver), by about a third up and down
  Object.values(rigs).forEach((rig, i) => {
    if (!rig.eyes) { return; }
    const noise = (x, seed) => {
      const n = Math.floor(x), u = x - n, e = u * u * (3 - 2 * u);
      const at = (k) => { const v = Math.sin((k + seed * 57.3) * 12.9898) * 43758.5453; return v - Math.floor(v); };
      return at(n) + (at(n + 1) - at(n)) * e;
    };
    const waver = 0.62 * noise(t * 0.7, i + 1) + 0.38 * noise(t * 2.9, i + 11);
    rig.eyes.emissiveIntensity *= 0.66 * (0.68 + 0.64 * waver);
  });
  // everyone breathes: at their own pace (14 to 19 breaths a minute), in a little faster than out, and deeper when
  // the band is loud
  Object.values(rigs).forEach((rig, i) => {
    const phase = 2 * Math.PI * (0.23 + 0.016 * ((i * 7) % 6)) * t + i * 1.9;
    const breath = 0.5 + 0.5 * Math.sin(phase + 0.45 * Math.sin(phase));
    rig.breathe(breath, 0.045 * (0.7 + 0.6 * energyAt(t).slow));
  });
  // waiting for the click: nearly dark. From the click: up within a second, a flash first, like lamps switched on
  if (frozen === null && audio && !playing) {
    const since = introAt === null ? -1 : (performance.now() - introAt) / 1000;
    house = since < 0 ? 0.07 : Math.min(1, 0.07 + 0.93 * since / 0.9) + 0.5 * Math.exp(-since / 0.12);
  } else { house = 1; }
  analog(t);
  placeBlobs();
  showT = t;
  look();
  viewer.copy(camera.position);
  renderer.render(scene, camera);
  const nowMs = performance.now();
  worst = Math.max(worst, nowMs - before);
  before = nowMs;
  counted++;
  if (nowMs - since >= 500) { fps = counted * 1000 / (nowMs - since); hud.dataset.worst = worst.toFixed(0); counted = 0; worst = 0; since = nowMs; }
  hud.textContent = `${fps.toFixed(0)} fps  (slowest frame ${hud.dataset.worst || '-'} ms)   t ${t.toFixed(2)} s  beat ${beat.toFixed(2)}  ${window.__dbg || ''}`;
  frames++;
  if (frames > 2) { window.__ready = true; }
  requestAnimationFrame(frame);
}
frame();
