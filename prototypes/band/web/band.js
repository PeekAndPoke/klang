/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// The band: who stands where, what each one looks like and how each one moves, the figures themselves, the players
// with their instruments, and how they come onto the stage.
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { Rig } from './rig.js';
import { Drummer, testBeat } from './drummer.js';
import { Marimba } from './marimba.js';
import { Trumpet } from './trumpet.js';
import { StringPlayer } from './strings.js';
import { drumHits, notesOf, strumsOf } from './song.js';
import { pattern } from './pattern.js';
import { enter, stepLegs, walkPose, capture, blend } from './entrance.js';
import { song, BPM } from './show.js';
import { RISER } from './stage.js';
import { smooth } from './util.js';

// Where each player stands: x to the audience's right, z toward the audience, the turn, and how high (the riser).
// The order here is the players' order wherever one is needed (it must not depend on which file loads first).
export const PLACES = {
  drums: [0.1, -2.1, 0, RISER], bass: [-2.15, 0.4, 0.15], guitar1: [-3.0, 1.7, 0.3],
  eimer: [-0.7, 2.2, 0.1], marimba: [1.7, 0.4, -0.15], guitar2: [3.0, 1.7, -0.3],
};
// Each player's pattern, seen only in how the body throws back the light
const SKINS = { drums: 'tiger', bass: 'pinstripe', guitar1: 'spiral', guitar2: 'spots', marimba: 'diamonds', eimer: 'braid' };
// Who wears hair as a pattern in the light, combed straight back
const HAIR = { guitar1: true };
// Everyone glitters; the two women a little more (the share of spangles in the skin)
const GLITTER = { eimer: 0.21, marimba: 0.21 };

/** The head's middle and size, in the body's rest space: a little above and behind where the head's bone starts. */
function hairOf(rig) {
  const neck = rig.root.worldToLocal(rig.rest.head.world.clone());
  const size = rig.height / 1.60;
  return { centre: new THREE.Vector3(neck.x, neck.y + 0.115 * size, neck.z - 0.012 * size), radius: 0.104 * size };
}

/** Loads the six figures into [scene], each at its place, and returns their rigs by name, in the order of PLACES. */
export async function loadBand(scene) {
  const rigs = {};
  const loader = new GLTFLoader();
  const loaded = await Promise.all(Object.keys(PLACES).map((name) => loader.loadAsync(`../models/${name}.glb`)));
  Object.entries(PLACES).forEach(([name, [x, z, turn, y = 0]], index) => {
    const gltf = loaded[index];
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
  });
  return rigs;
}

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
  // composed: modest nods (a little more than at first: the maintainer, 2026-11-16), her head tilting from side to side
  marimba: { seed: 53, depth: 0.68, beats: [1, 0.25, 0.6, 0.25], lag: 0.03, soft: 0.18, chance: 0.35, look: 0.10, tilt: 0.12, lean: 0.015,
    parts: { head: 1.0, trunk: 0.6, knees: 0.5 } },                 // upright, quiet below the neck
  // the frontman: the balanced one. Every part takes the beat alike, on time, even, with little chance in it
  eimer: { seed: 67, depth: 0.9, beats: [1, 0.5, 0.8, 0.5], lag: 0.0, soft: 0.13, chance: 0.2, skip: 0, look: 0.14, tilt: 0.04, lean: 0.02,
    parts: { head: 1.0, trunk: 1.0, knees: 1.0, sway: 1.0 } },
};
// Test notes, for the page without the recording (?test)
const testTune = () => Array.from({ length: 256 }, (_, i) => ({ t: i * 0.25, midi: 64 + [0, 3, 7, 10, 12, 10, 7, 3][i % 8] + (i % 32 >= 16 ? 5 : 0), v: i % 4 === 0 ? 1 : 0.6 }));
const testCall = () => Array.from({ length: 128 }, (_, i) => ({ t: 1 + i * 0.75 + (i % 8 >= 6 ? 3 : 0), d: 0.6, midi: 65 + [0, 2, 3, 7, 5, 3, 10, 12][i % 8], v: 1 })).sort((a, b) => a.t - b.t);
const testRiff = (step, low) => Array.from({ length: 512 }, (_, i) => ({ t: i * step, midi: low + [0, 0, 3, 0, 5, 0, 7, 3][Math.floor(i * step * 2) % 8], v: i % 2 === 0 ? 1 : 0.7 }));

/**
 * Gives every figure its instrument and its part of the song. [viewer]: where the camera is (who looks at the
 * viewer looks there). Build this before anyone walks in: a player takes the place its figure stands at as home.
 */
export function castBand(rigs, viewer) {
  const drummer = new Drummer(rigs.drums, song ? drumHits(song) : testBeat(64, BPM), BPM, CHARACTERS.drums);
  const marimba = new Marimba(rigs.marimba, song ? notesOf(song, 'lead') : testTune(), BPM, CHARACTERS.marimba);
  const eimer = new Trumpet(rigs.eimer, song ? notesOf(song, 'trommel') : testCall(), BPM, CHARACTERS.eimer);
  const strings = [
    ['bass', new StringPlayer(rigs.bass, song ? strumsOf(song, ['bass']) : testRiff(0.5, 28), BPM, { kind: 'bass', lean: 0.10, bang: 0.16, bounce: 0.045, character: CHARACTERS.bass })],
    ['guitar1', new StringPlayer(rigs.guitar1, song ? strumsOf(song, ['guitar1']) : testRiff(0.25, 52), BPM, { kind: 'guitar', lean: -0.05, bang: 0.14, bounce: 0.025, character: CHARACTERS.guitar1 })],
    ['guitar2', new StringPlayer(rigs.guitar2, song ? strumsOf(song, ['guitar2', 'guitar3']) : testRiff(0.125, 40), BPM, { kind: 'guitar', outline: 'single', lean: 0.10, bang: 0.24, bounce: 0.028, character: CHARACTERS.guitar2,
      glance: { at: viewer, every: 11, chance: 0.5 } })],
  ];
  return { drummer, marimba, eimer, strings };
}

// The walk-in: the stage's clock starts INTRO seconds before the song. Each player comes from off the stage (x, z)
// between two times; the drummer first, from the back and up onto the riser, the frontwoman last.
export const INTRO = 15;
const ENTRANCES = {
  drums: { from: [0.1, -7.5], t: [-15, -9] },
  bass: { from: [-8.5, 0.9], t: [-13.5, -7.5] },
  guitar2: { from: [8.5, 2.0], t: [-12.5, -6.5] },
  guitar1: { from: [-8.5, 2.1], t: [-11.5, -5.5] },
  marimba: { from: [8.5, 0.9], t: [-10.5, -4.8] },
  eimer: { from: [-8.5, 2.9], t: [-8.5, -2.4] },
};
const UP = new THREE.Vector3(0, 1, 0);

/** The band at [t]: everyone where their walk-in has them, playing; the legs of those who walk step. */
export function playBand(rigs, band, t) {
  const walks = {};
  for (const name of Object.keys(ENTRANCES)) { walks[name] = enter(rigs[name], ENTRANCES[name], PLACES[name], t); }
  // the string players and the marimba player walk as they stand, the legs stepping
  for (const [name, player] of band.strings) { player.update(t); stepLegs(rigs[name], walks[name]); }
  band.marimba.update(t);
  stepLegs(rigs.marimba, walks.marimba);
  // the frontwoman: put where her walk-in has her; once there she is her own (her place, her strolls)
  const her = walks.eimer.since < 0 ? { at: rigs.eimer.root.position.clone(), turn: rigs.eimer.root.rotation.y, going: 0, speed: 0 } : null;
  band.eimer.update(t, her);
  stepLegs(rigs.eimer, walks.eimer);
  // the drummer walks in with the sticks in his hands, and sits down once he is at the stool
  band.drummer.update(t);
  const sit = smooth(walks.drums.since / 1.6);
  if (sit < 1) {
    const seated = capture(rigs.drums);
    walkPose(rigs.drums, walks.drums);
    blend(rigs.drums, capture(rigs.drums), seated, sit);
    for (const side of ['L', 'R']) {
      const d = rigs.drums.axisOf('hand' + side);
      const stick = band.drummer.sticks[side];
      stick.position.copy(rigs.drums.bones['hand' + side].getWorldPosition(new THREE.Vector3())).addScaledVector(d, band.drummer.stickLength * 0.22);
      stick.quaternion.setFromUnitVectors(UP, d);
    }
  }
}
