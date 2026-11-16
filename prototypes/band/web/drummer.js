/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// The drummer: sits on a stool behind a kit and plays a list of hits. Every place is in the figure's own measures
// (its leg's length), so the kit fits whoever sits there.
import * as THREE from 'three';
import { around, strokeAt, holdStick, Mover } from './strike.js';


const shell = new THREE.MeshStandardMaterial({ color: 0x08080b, roughness: 0.3, metalness: 0.4 });
const skin = new THREE.MeshStandardMaterial({ color: 0x1a1a20, roughness: 0.6 });
const brass = new THREE.MeshStandardMaterial({ color: 0x4a3a18, roughness: 0.25, metalness: 0.95, side: THREE.DoubleSide });
const wood = new THREE.MeshStandardMaterial({ color: 0x2a2018, roughness: 0.6 });
const steel = new THREE.MeshStandardMaterial({ color: 0x15151a, roughness: 0.3, metalness: 0.9 });

export class Drummer {
  /** [hits]: { t, drum, limb: 'L' | 'R' | 'kick' | 'hat', v } sorted by time. */
  constructor(rig, hits, bpm, character = {}) {
    this.rig = rig;
    this.mover = new Mover(character.seed ?? 1, character);
    this.bpm = bpm;
    this.s = rig.s;
    const s = this.s;
    this.kit = new THREE.Group();
    // the kit stands where he plays, whether he sits there yet or not
    rig.root.parent.add(this.kit);
    this.kit.position.copy(rig.root.position);
    this.kit.rotation.y = rig.root.rotation.y;
    this.drums = {};
    this.byLimb = { L: [], R: [], kickL: [], kickR: [] };
    this.all = hits;

    // the stool
    this.seat = 0.50 * s;
    const stool = new THREE.Mesh(new THREE.CylinderGeometry(0.17 * s, 0.17 * s, 0.07 * s, 24), shell);
    stool.position.set(0, this.seat - 0.035 * s, -0.02 * s);
    const leg = new THREE.Mesh(new THREE.CylinderGeometry(0.03 * s, 0.03 * s, this.seat, 8), steel);
    leg.position.set(0, this.seat / 2, -0.02 * s);
    this.kit.add(stool, leg);

    // the drums: where the stick lands (the head's middle), the radius, the height of the shell, the tilt toward the player
    this.drum('snare', [0.14, 0.66, 0.58], 0.17, 0.14, 0.10);
    this.drum('tom', [-0.04, 0.84, 0.80], 0.14, 0.16, 0.45);
    this.drum('floor', [-0.50, 0.60, 0.42], 0.20, 0.40, 0.05);
    this.cymbal('hat', [0.52, 0.88, 0.56], 0.17, 0.05);
    // a second hat, standing open, for the open hat's sound: beside the first, a little further out and higher
    this.cymbal('hat2', [0.80, 0.96, 0.40], 0.16, 0.10);
    this.cymbal('crash', [0.55, 1.26, 0.90], 0.22, 0.25);
    this.cymbal('ride', [-0.80, 0.86, 0.82], 0.25, 0.30);

    // the kick: lying, its head toward the audience
    const kick = new THREE.Mesh(new THREE.CylinderGeometry(0.29 * s, 0.29 * s, 0.40 * s, 32), shell);
    kick.rotation.x = Math.PI / 2;
    kick.position.set(-0.06 * s, 0.29 * s, 1.00 * s);
    const front = new THREE.Mesh(new THREE.CircleGeometry(0.27 * s, 32), skin);
    front.position.set(-0.06 * s, 0.29 * s, 1.201 * s);
    this.kit.add(kick, front);
    this.drums.kick = { mesh: kick, at: new THREE.Vector3(), base: kick.position.clone(), hitT: -9, v: 0 };

    // the feet's places, the ankles: both on the kick's double pedal, the right one at the drum, the left one
    // beside it
    this.feet = { R: new THREE.Vector3(-0.13 * s, 0.10 * s, 0.58 * s), L: new THREE.Vector3(0.20 * s, 0.10 * s, 0.56 * s) };

    // the sticks
    this.stickLength = 0.40 * s;
    this.sticks = {};
    for (const side of ['L', 'R']) {
      const stick = new THREE.Mesh(new THREE.CylinderGeometry(0.008 * s, 0.011 * s, this.stickLength, 8), wood);
      rig.root.parent.add(stick);
      this.sticks[side] = stick;
    }
    this.kit.traverse((o) => { if (o.isMesh) { o.castShadow = true; } });
    for (const h of hits) { this.byLimb[h.limb === 'kick' ? 'kickR' : h.limb].push({ ...h, at: this.drums[h.drum].at }); }
  }

  drum(name, at, radius, height, tilt) {
    const s = this.s;
    const group = new THREE.Group();
    group.position.set(at[0] * s, at[1] * s, at[2] * s);
    group.rotation.x = -tilt;
    const body = new THREE.Mesh(new THREE.CylinderGeometry(radius * s, radius * s, height * s, 28), shell);
    body.position.y = -height * s / 2;
    const head = new THREE.Mesh(new THREE.CircleGeometry(radius * s * 0.94, 28), skin);
    head.rotation.x = -Math.PI / 2;
    head.position.y = 0.002;
    group.add(body, head);
    if (name !== 'floor') {
      const stand = new THREE.Mesh(new THREE.CylinderGeometry(0.012 * s, 0.012 * s, at[1] * s, 6), steel);
      stand.position.set(at[0] * s, at[1] * s / 2 - height * s * 0.5, at[2] * s + 0.02);
      this.kit.add(stand);
    }
    this.kit.add(group);
    this.drums[name] = { mesh: group, at: new THREE.Vector3(at[0] * s, at[1] * s, at[2] * s), tilt, hitT: -9, v: 0 };
  }

  cymbal(name, at, radius, tilt) {
    const s = this.s;
    const group = new THREE.Group();
    group.position.set(at[0] * s, at[1] * s, at[2] * s);
    const disc = new THREE.Mesh(new THREE.ConeGeometry(radius * s, 0.025 * s, 32, 1, true), brass);
    group.add(disc);
    if (name.startsWith('hat')) {
      const lower = new THREE.Mesh(new THREE.ConeGeometry(radius * s, 0.025 * s, 32, 1, true), brass);
      lower.rotation.x = Math.PI;
      lower.position.y = (name === 'hat' ? -0.03 : -0.075) * s;        // the open one: a gap between its two cymbals
      group.add(lower);
    }
    const stand = new THREE.Mesh(new THREE.CylinderGeometry(0.01 * s, 0.01 * s, at[1] * s, 6), steel);
    stand.position.set(at[0] * s, at[1] * s / 2, at[2] * s);
    this.kit.add(group, stand);
    // a cymbal is hit near its edge, the side toward the player
    const edge = new THREE.Vector3(at[0] * s, at[1] * s, at[2] * s);
    const toward = new THREE.Vector3(Math.sign(at[0]) * 0.25 - at[0], 0, 0.1 - at[2]).normalize();
    edge.addScaledVector(toward, radius * s * 0.75);
    this.drums[name] = { mesh: group, at: edge, tilt, cymbal: true, hitT: -9, v: 0 };
  }

  update(t) {
    const rig = this.rig;
    const s = this.s;

    // what was hit: a cymbal swings, a drum dips, and it all feeds the body
    let energy = 0, snare = 0;
    for (const drum of Object.values(this.drums)) { drum.hitT = -9; }
    const [last] = around(this.all, t);
    for (let i = this.all.indexOf(last); i >= 0 && this.all[i].t > t - 1.5; i--) {
      const h = this.all[i];
      const d = this.drums[h.drum];
      if (d.hitT < 0) { d.hitT = h.t; d.v = h.v; }
      const age = t - h.t;
      energy += h.v * Math.exp(-age / 0.35) * (h.drum === 'hat' ? 0.25 : 1);
      if (h.drum === 'snare' || h.drum === 'kick') { snare = Math.max(snare, h.v * Math.exp(-age / 0.12)); }
    }
    for (const d of Object.values(this.drums)) {
      const age = t - d.hitT;
      if (d.cymbal) {
        const swing = d.v * 0.22 * Math.exp(-age / 0.5) * Math.sin(age * 22);
        d.mesh.rotation.set(d.tilt * 0.5 + swing, 0, swing * 0.6);
      } else if (d.base) {
        d.mesh.position.z = d.base.z + d.v * 0.012 * Math.exp(-age / 0.06);
      }
    }

    const hands = {};
    for (const side of ['L', 'R']) {
      hands[side] = strokeAt(this.byLimb[side], t, {
        rest: this.drums[side === 'L' ? 'snare' : 'hat'].at,
        home: new THREE.Vector3(rig.shoulder[side].x, 0, 0.30 * s),
        height: 0.40 * s,
      });
    }

    rig.reset();
    rig.update();
    // sitting: the pelvis onto the stool, the trunk leaning in, nodding with the kick and the snare
    const nod = this.mover.nod(t * this.bpm / 60);
    const body = nod + this.mover.burst();
    const P = this.mover.parts;
    const bob = 0.012 * s * body * P.knees + 0.004 * s * snare;
    rig.moveHips(new THREE.Vector3(0, this.seat + 0.085 * s - rig.pelvis.y - bob, -0.02 * s));
    rig.turn('belly', new THREE.Vector3(1, 0, 0), 0.19 + 0.05 * body * P.trunk + 0.015 * snare);
    rig.turn('chest', new THREE.Vector3(0, 1, 0), 0.35 * (hands.L.aim.x + hands.R.aim.x) / s);
    rig.turn('chest', new THREE.Vector3(1, 0, 0), 0.06);
    rig.turn('neck', new THREE.Vector3(1, 0, 0), 0.06 * nod * P.head);
    rig.turn('head', new THREE.Vector3(1, 0, 0), -0.12 + 0.24 * nod * P.head + 0.02 * snare);
    this.mover.apply(rig, t);

    // the feet on their pedals; the right one stamps the kick
    for (const side of ['L', 'R']) {
      const sign = side === 'L' ? 1 : -1;
      const foot = this.feet[side].clone();
      // each foot stamps its own kicks: up just before, down at the hit
      const [, next] = around(this.byLimb['kick' + side], t);
      const until = next ? next.t - t : 9;
      foot.y += 0.05 * s * (until < 0.16 ? Math.sin(Math.PI * (1 - until / 0.16)) : 0);
      rig.foot(side, rig.root.localToWorld(foot), rig.dir(0.5 * sign, 0.6, 1));
    }

    // the hands, and the sticks in them
    for (const side of ['L', 'R']) {
      const sign = side === 'L' ? 1 : -1;
      holdStick(rig, side, hands[side].tip, hands[side].bend, {
        length: this.stickLength, pole: rig.dir(0.55 * sign, -1.0, -0.5), mesh: this.sticks[side],
      });
    }

    if (rig.eyes) { rig.eyes.emissiveIntensity = 2 + 14 * Math.min(1.6, energy); }
  }
}

/** A test beat, in beats: hats in eighths, kick and snare, and every fourth bar a fill round the toms into a crash. */
export function testBeat(bars, bpm) {
  const hits = [];
  const sec = 60 / bpm;
  const add = (beat, drum, limb, v) => hits.push({ t: beat * sec, drum, limb, v });
  for (let bar = 0; bar < bars; bar++) {
    const b = bar * 4;
    const fill = bar % 4 === 3;
    const afterFill = bar % 4 === 0 && bar > 0;
    for (let i = 0; i < (fill ? 4 : 8); i++) {
      if (i === 0 && afterFill) { add(b, 'crash', 'R', 1.0); } else { add(b + i * 0.5, bar % 8 >= 4 ? 'ride' : 'hat', 'R', i % 2 === 0 ? 0.8 : 0.45); }
    }
    add(b, 'kick', 'kick', 1.0);
    add(b + 1, 'snare', 'L', 0.9);
    if (!fill) {
      add(b + 2, 'kick', 'kick', 0.9); add(b + 2.5, 'kick', 'kick', 0.7); add(b + 3, 'snare', 'L', 1.0);
    } else {
      const order = ['snare', 'snare', 'tom', 'tom', 'tom', 'floor', 'floor', 'floor'];
      order.forEach((drum, i) => add(b + 2 + i * 0.25, drum, i % 2 === 0 ? 'R' : 'L', 0.6 + 0.05 * i));
      add(b + 2, 'kick', 'kick', 0.9); add(b + 3, 'kick', 'kick', 0.9);
    }
  }
  return hits.sort((a, b) => a.t - b.t);
}
