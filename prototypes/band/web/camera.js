/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// The camera's choreography. Mostly the total: the wide view from the audience, the start position. The song is cut
// into rounds of 48 cycles (bars, about 79 s): 36 on the total, then three looks in a row, three bars each, with a
// cut between them (close on one player, or the stage from another angle), then back to the total: by a plain cut
// in two rounds of three, and in the third by a slow dolly zoom over three bars (the camera travels back while its
// lens opens, so the player keeps their size for the first half of the way as the stage grows round them).
// Calm on purpose (the maintainer, 2026-11-16: fast moves "make dizzy"; "do not cut too often to the people, but
// maybe multiple in a row"; "the camera must mostly show the total"; "cut back to the total without camera
// movement, not always though").
// Everything follows from the time alone, so the same moment always has the same picture.
import * as THREE from 'three';

const smooth = (x) => { const c = Math.min(1, Math.max(0, x)); return c * c * (3 - 2 * c); };
export const HOME = { pos: new THREE.Vector3(0, 1.85, 9.4), at: new THREE.Vector3(0, 1.0, 0), fov: 35 };

// Looks at a player, in the player's own space (x to their left, y up, z ahead of them): where the camera is, what
// it looks at, the lens
const LOOKS = {
  standing: [
    { pos: [0.7, 0.75, 2.4], at: [0, 1.20, 0.1], fov: 42 },       // from below, in front
    { pos: [2.0, 1.45, 0.9], at: [0, 1.15, 0.2], fov: 38 },       // from their left
    { pos: [-0.9, 1.55, 1.7], at: [0, 1.30, 0.1], fov: 30 },      // close on head and hands
    { pos: [-1.9, 1.1, 1.3], at: [0, 1.10, 0.2], fov: 40 },       // from their right, low
  ],
  drums: [
    { pos: [1.2, 1.0, 3.1], at: [0, 0.95, 0.4], fov: 40 },        // over the kick drum
    { pos: [-1.1, 2.2, -1.5], at: [0, 0.85, 0.7], fov: 55 },      // over his shoulder, down on the kit
    { pos: [2.4, 1.25, 0.7], at: [0, 0.95, 0.4], fov: 38 },       // from his left
  ],
  marimba: [
    { pos: [0.2, 2.2, 2.3], at: [0, 0.90, 0.5], fov: 42 },        // down on the bars
    { pos: [1.9, 1.15, 1.5], at: [0, 1.05, 0.5], fov: 38 },       // along the bars
    { pos: [-0.8, 1.5, 2.2], at: [0, 1.25, 0.2], fov: 32 },
  ],
};
// The stage from elsewhere
const ANGLES = [
  { pos: [-7.2, 1.1, 5.6], at: [0.0, 1.0, 0.6], fov: 40 },        // from the left, low
  { pos: [0.0, 5.6, 7.4], at: [0.0, 0.6, 0.6], fov: 38 },         // from above
  { pos: [7.0, 1.4, 5.2], at: [0.0, 1.0, 0.6], fov: 40 },         // from the right
  { pos: [0.0, 0.45, 6.6], at: [0.0, 1.5, 0.0], fov: 44 },        // from the floor
];
const ORDER = ['drums', 'guitar1', 'eimer', 'bass', 'guitar2', 'marimba'];

export class Choreography {
  /** [rigs] by name; [activity]: name -> the times its player plays a note, sorted; [cycle]: a bar, in seconds. */
  /** [seed]: every seed gives another show: who is looked at, from where, in which order, when, and how it comes back. */
  constructor(rigs, activity, cycle, seed = 0) {
    this.seed = seed;
    this.rigs = rigs;
    this.activity = activity;
    this.cycle = cycle;
    this.round = 48 * cycle;
    // the song starts after the count-in; the rounds begin up to twelve bars later, by the seed
    this.start = (2 + Math.floor(this.chance(0, 0, 9) * 13)) * cycle;
  }

  /** A number from 0 to 1 that belongs to the round [n], the look [i] and a purpose [salt], under this seed. */
  chance(n, i, salt) {
    const x = Math.sin(n * 127.1 + i * 311.7 + salt * 74.7 + this.seed * 0.618) * 43758.5453;
    return x - Math.floor(x);
  }

  /** How many notes [name] plays between [a] and [b]. */
  plays(name, a, b) {
    const list = this.activity[name] || [];
    let n = 0;
    for (const t of list) { if (t >= a && t < b) { n++; } else if (t >= b) { break; } }
    return n;
  }

  /** What the look number [i] (0, 1, 2) of round [n] shows: a player who plays then, or the stage from another angle. */
  subject(n, i) {
    const a = this.start + n * this.round + (36 + 3 * i) * this.cycle, b = a + 3 * this.cycle;
    const pick = (list, salt) => list[Math.floor(this.chance(n, i, salt) * list.length) % list.length];
    // about one look in five is the stage from another angle
    if (this.chance(n, i, 1) < 0.2) { return { angle: pick(ANGLES, 2) }; }
    // the marimba and the trumpet play seldom: when one of them does, the round's first look is theirs
    const rare = ['marimba', 'eimer'].filter((name) => this.plays(name, a, b) > 2);
    let name = rare.length && i === 0 ? pick(rare, 3) : null;
    // else someone who plays just then, and not the one the look before showed
    const before = i > 0 ? this.subject(n, i - 1).name : null;
    const first = Math.floor(this.chance(n, i, 4) * ORDER.length);
    for (let k = 0; k < ORDER.length && !name; k++) {
      const candidate = ORDER[(first + k) % ORDER.length];
      if (candidate !== before && this.plays(candidate, a, b) > 3) { name = candidate; }
    }
    if (!name) { return { angle: pick(ANGLES, 2) }; }
    return { name, look: pick(LOOKS[name] || LOOKS.standing, 5) };
  }

  /** A look as the camera's place, target and lens, in the world. */
  shot(subject) {
    if (subject.angle) {
      return { pos: new THREE.Vector3(...subject.angle.pos), at: new THREE.Vector3(...subject.angle.at), fov: subject.angle.fov };
    }
    const root = this.rigs[subject.name].root;
    root.updateWorldMatrix(true, false);
    return {
      pos: root.localToWorld(new THREE.Vector3(...subject.look.pos)),
      at: root.localToWorld(new THREE.Vector3(...subject.look.at)),
      fov: subject.look.fov,
    };
  }

  /** The camera at [t]: { pos, at, fov }. */
  at(t) {
    // the wide view breathes: a slow drift, never still
    const home = {
      pos: HOME.pos.clone().add(new THREE.Vector3(0.15 * Math.sin(t * 0.07), 0.04 * Math.sin(t * 0.05 + 1), 0.12 * Math.sin(t * 0.04))),
      at: HOME.at.clone(), fov: HOME.fov,
    };
    if (t < this.start) { return home; }
    const n = Math.floor((t - this.start) / this.round);
    const x = (t - this.start - n * this.round) / this.cycle;           // 0 to 48, in bars
    const dolly = this.chance(n, 0, 6) < 0.34;
    if (x < 36 || (x >= 45 && !dolly)) { return home; }
    const i = Math.min(2, Math.floor((x - 36) / 3));
    const shot = this.shot(this.subject(n, i));
    if (x < 45) {
      // the look itself is never quite still: a very slow push in, a slow slide to one side (the other side on the
      // next look) of about a twentieth of its distance, and a faint float up and down
      const u = (x - 36 - 3 * i) / 3;
      const far = shot.pos.distanceTo(shot.at);
      const side = new THREE.Vector3().subVectors(shot.at, shot.pos).cross(new THREE.Vector3(0, 1, 0)).normalize();
      shot.pos.lerp(shot.at, 0.03 * u);
      shot.pos.addScaledVector(side, (i % 2 === 0 ? 1 : -1) * 0.05 * far * (u - 0.5));
      shot.pos.y += 0.008 * far * Math.sin(t * 0.8 + i);
      return shot;
    }
    // back by a dolly zoom over three bars: the camera travels to the start; the lens keeps the subject's size for
    // the first half of the way, then opens to the wide view, and the look turns from the subject to the stage
    // the dolly zoom starts where the last look's slide ended
    {
      const far = shot.pos.distanceTo(shot.at);
      const side = new THREE.Vector3().subVectors(shot.at, shot.pos).cross(new THREE.Vector3(0, 1, 0)).normalize();
      shot.pos.lerp(shot.at, 0.03);
      shot.pos.addScaledVector(side, (i % 2 === 0 ? 1 : -1) * 0.05 * far * 0.5);
    }
    const u = smooth((x - 45) / 3);
    const pos = shot.pos.clone().lerp(home.pos, u);
    const held = 2 * Math.atan(shot.pos.distanceTo(shot.at) * Math.tan(shot.fov * Math.PI / 360) / pos.distanceTo(shot.at)) * 180 / Math.PI;
    const open = smooth((u - 0.45) / 0.55);
    return { pos, at: shot.at.clone().lerp(home.at, smooth((u - 0.2) / 0.8)), fov: held + (home.fov - held) * open };
  }
}
