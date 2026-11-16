/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// The marimba player: stands behind a marimba and plays notes with two mallets. Which bar is hit follows the note,
// so the player moves along the instrument with the tune. All places are in the figure's own measures.
import * as THREE from 'three';
import { around, strokeAt, holdStick, Mover } from './strike.js';

const wood = new THREE.MeshStandardMaterial({ color: 0x241710, roughness: 0.5 });
const steel = new THREE.MeshStandardMaterial({ color: 0x15151a, roughness: 0.3, metalness: 0.9 });
const felt = new THREE.MeshStandardMaterial({ color: 0x30262a, roughness: 0.9 });
const NATURAL = [true, false, true, false, true, true, false, true, false, true, false, true];

export class Marimba {
  /** [notes]: { t, midi, v } sorted by time; [bpm] for the nod. */
  constructor(rig, notes, bpm, character = {}) {
    this.rig = rig;
    this.mover = new Mover(character.seed ?? 2, character);
    this.bpm = bpm;
    this.s = rig.pelvis.y / 0.92;
    const s = this.s;
    this.notes = notes;
    this.group = new THREE.Group();
    // the marimba stands on the stage where she plays, whether she is there yet or not
    rig.root.parent.add(this.group);
    this.group.position.copy(rig.root.position);
    this.group.rotation.y = rig.root.rotation.y;
    this.floor = rig.root.position.y;
    this.home = rig.root.position.clone();
    this.turn = rig.root.rotation.y;
    this.fly = 1;                                    // 1: it has landed; less: on its way in ([flyIn])

    // the bars: from the lowest note played to the highest, widened to whole naturals; low notes to the player's left
    let lo = Math.min(...notes.map((n) => n.midi), 60), hi = Math.max(...notes.map((n) => n.midi), 72);
    if (notes.length === 0) { lo = 60; hi = 84; }
    while (!NATURAL[((lo % 12) + 12) % 12]) { lo--; }
    while (!NATURAL[((hi % 12) + 12) % 12]) { hi++; }
    const naturals = [];
    for (let m = lo; m <= hi; m++) { if (NATURAL[m % 12]) { naturals.push(m); } }
    const width = Math.min(0.058 * s, 1.25 * s / naturals.length);
    const span = width * (naturals.length - 1);
    this.height = 0.86 * s;
    this.near = 0.42 * s;
    this.bars = {};
    const glow = rig.eyes ? rig.eyes.emissive.clone() : new THREE.Color(0x40ffcc);
    for (let m = lo; m <= hi; m++) {
      const natural = NATURAL[m % 12];
      const index = natural ? naturals.indexOf(m) : naturals.indexOf(m - 1) + 0.5;
      const share = naturals.length > 1 ? index / (naturals.length - 1) : 0;
      const x = span / 2 - index * width;
      const length = (0.24 - 0.08 * share) * s;
      const z = natural ? this.near + length / 2 : this.near + 0.25 * s + length / 2;
      const y = this.height + (natural ? 0 : 0.025 * s);
      const material = new THREE.MeshStandardMaterial({ color: 0x2a1a10, roughness: 0.45, emissive: glow, emissiveIntensity: 0 });
      const bar = new THREE.Mesh(new THREE.BoxGeometry(width * 0.86, 0.02 * s, length), material);
      bar.position.set(x, y, z);
      this.group.add(bar);
      // the tube below it: longer for a low note
      const tube = new THREE.Mesh(new THREE.CylinderGeometry(width * 0.36, width * 0.36, (0.55 - 0.38 * share) * s, 10), steel);
      tube.position.set(x, y - 0.03 * s - (0.55 - 0.38 * share) * s / 2, z);
      this.group.add(tube);
      // a mallet lands near the bar's end that is toward the player
      this.bars[m] = { bar, material, restY: y, at: new THREE.Vector3(x, y + 0.012 * s, z - length * (natural ? 0.15 : 0.30)), hitT: -9, v: 0 };
    }
    // the frame
    const far = this.near + 0.50 * s;
    for (const z of [this.near - 0.02 * s, this.near + 0.245 * s, far]) {
      const rail = new THREE.Mesh(new THREE.BoxGeometry(span + width * 2, 0.03 * s, 0.03 * s), wood);
      rail.position.set(0, this.height - 0.03 * s, z);
      this.group.add(rail);
    }
    // no legs: the marimba floats (the maintainer, 2026-11-15: "make the marimba fly"); short ends close the frame
    for (const x of [-span / 2 - width, span / 2 + width]) {
      const end = new THREE.Mesh(new THREE.BoxGeometry(0.03 * s, 0.05 * s, far - this.near + 0.05 * s), wood);
      end.position.set(x, this.height - 0.035 * s, (this.near + far) / 2);
      this.group.add(end);
    }
    this.group.traverse((o) => { if (o.isMesh) { o.castShadow = true; } });
    this.half = span / 2;

    // which hand plays which note: in turn, but never crossing over the other hand
    this.byHand = { L: [], R: [] };
    let last = 'R';
    const where = { L: null, R: null };
    for (const n of notes) {
      const at = (this.bars[n.midi] || this.bars[lo]).at;
      let hand = last === 'L' ? 'R' : 'L';
      const other = hand === 'L' ? 'R' : 'L';
      const recent = where[other] && n.t - where[other].t < 0.6 ? where[other].at.x : null;
      // the left hand is on the side of the low notes (x > 0): it never plays to the right of the right hand
      if (recent !== null && ((hand === 'L' && at.x < recent - width) || (hand === 'R' && at.x > recent + width))) { hand = other; }
      const hit = { t: n.t, v: n.v, at, midi: n.midi };
      this.byHand[hand].push(hit);
      where[hand] = hit;
      last = hand;
    }

    this.malletLength = 0.40 * s;
    this.mallets = {};
    for (const side of ['L', 'R']) {
      const mallet = new THREE.Group();
      mallet.add(new THREE.Mesh(new THREE.CylinderGeometry(0.005 * s, 0.006 * s, this.malletLength, 6), wood));
      const head = new THREE.Mesh(new THREE.SphereGeometry(0.022 * s, 12, 8), felt);
      head.position.y = this.malletLength / 2;
      mallet.add(head);
      rig.root.parent.add(mallet);
      this.mallets[side] = mallet;
    }
  }

  update(t) {
    const rig = this.rig;
    const s = this.s;

    // the bars just hit glow and fade; how much is played feeds the eyes
    let energy = 0;
    const [last] = around(this.notes, t);
    for (const b of Object.values(this.bars)) { b.hitT = -9; }
    for (let i = this.notes.indexOf(last); i >= 0 && this.notes[i].t > t - 1.2; i--) {
      const n = this.notes[i];
      const b = this.bars[n.midi];
      if (b && b.hitT < 0) { b.hitT = n.t; b.v = n.v; }
      energy += n.v * Math.exp(-(t - n.t) / 0.3);
    }
    for (const b of Object.values(this.bars)) {
      const age = t - b.hitT;
      b.material.emissiveIntensity = b.hitT < 0 ? 0 : 1.6 * b.v * Math.exp(-age / 0.22);
      b.bar.position.y = b.restY - (b.hitT < 0 ? 0 : 0.004 * s * b.v * Math.exp(-age / 0.05));
    }

    // it hovers, a slow breath up and down; the mallets follow it
    const hover = 0.012 * s * Math.sin(t * 1.3);
    // lowered from the ceiling (the maintainer, 2026-11-16): straight down onto its place, out of sight above the
    // picture until then, swinging a little as it comes and settling
    const left = 1 - this.fly;
    this.group.position.set(this.home.x, this.floor + hover + 7.5 * left, this.home.z);
    this.group.rotation.set(0.05 * left * Math.sin(t * 2.1), this.turn, 0.07 * left * Math.sin(t * 1.7));

    // is she playing: up from well before a phrase to a little after it; between phrases she relaxes, the
    // arms hanging with the mallets down beside her, standing upright
    const [, coming] = around(this.notes, t);
    const until = coming ? coming.t - t : 99;
    const since = last ? t - last.t : 99;
    const ease = (x) => { const c = Math.min(1, Math.max(0, x)); return c * c * (3 - 2 * c); };
    // into position a few cycles ahead: the arms start to rise three cycles before her first note and she stands
    // ready, mallets over the bars, a good cycle before it
    const cycle = 240 / this.bpm;
    const up = Math.max(ease(1 - (until - 1.2 * cycle) / (1.8 * cycle)), 1 - ease((since - 1.4) / 1.2));

    const hands = {};
    this.rests = this.rests || { L: new THREE.Vector3(0.12 * s, this.height + 0.012 * s, this.near + 0.08 * s), R: new THREE.Vector3(-0.12 * s, this.height + 0.012 * s, this.near + 0.08 * s) };
    for (const side of ['L', 'R']) {
      const sign = side === 'L' ? 1 : -1;
      hands[side] = strokeAt(this.byHand[side], t, {
        rest: this.rests[side],
        home: new THREE.Vector3(rig.shoulder[side].x, 0, 0.22 * s),
        height: 0.20 * s, windup: 0.26, hover: 0.25,
      });
      hands[side].tip.y += hover;
      const down = new THREE.Vector3(rig.shoulder[side].x + 0.05 * sign * s, rig.pelvis.y - 0.34 * s, 0.14 * s);
      hands[side].tip.lerpVectors(down, hands[side].tip, up);
      hands[side].bend *= up;
      hands[side].aim.x *= up;
    }

    // the body and the arms are two things: the arms reach for the notes, and the body stays where it is as long
    // as they can. Only where the playing moves out of easy reach does the body follow, by what is beyond it, and
    // slowly: it goes by where the hands play over a few seconds, not by each note.
    const middle = (hands.L.aim.x + hands.R.aim.x) / 2;
    let region = 0;
    for (const dt of [-1.0, -0.5, 0, 0.5, 1.0]) {
      for (const side of ['L', 'R']) {
        region += strokeAt(this.byHand[side], t + dt, { rest: this.rests[side], home: this.rests[side], height: 0 }).aim.x / 10;
      }
    }
    region *= up;
    const easy = 0.20 * s;
    const beyond = Math.sign(region) * Math.max(0, Math.abs(region) - easy);
    const stance = (0.07 + 0.10 * up) * s;
    const reach = stance + rig.ankle.L.x;
    const shift = Math.max(-1, Math.min(1, beyond / reach)) * reach;
    const weight = shift / (stance + rig.ankle.L.x);                    // -1 on the right leg, 1 on the left
    const nod = this.mover.nod(t * this.bpm / 60);
    const body = nod + this.mover.burst();
    rig.reset();
    rig.update();
    rig.moveHips(new THREE.Vector3(shift, -(0.015 + 0.03 * up + 0.010 * body * this.mover.parts.knees - 0.02 * Math.abs(weight)) * rig.pelvis.y, 0.0));
    rig.turn('hips', new THREE.Vector3(0, 0, 1), -0.05 * weight);
    rig.turn('belly', new THREE.Vector3(1, 0, 0), 0.02 + 0.11 * up + 0.02 * body * this.mover.parts.trunk);
    rig.turn('belly', new THREE.Vector3(0, 0, 1), 0.04 * weight);
    rig.turn('chest', new THREE.Vector3(0, 1, 0), 0.22 * (middle - shift) / s);
    rig.turn('head', new THREE.Vector3(1, 0, 0), 0.04 + 0.18 * up + 0.08 * nod * this.mover.parts.head);
    this.mover.apply(rig, t);
    rig.turn('head', new THREE.Vector3(0, 1, 0), 0.5 * (middle - shift) / s);
    for (const side of ['L', 'R']) {
      const sign = side === 'L' ? 1 : -1;
      const foot = rig.ankle[side].clone();
      foot.x += stance * sign;
      // the leg without the weight rests on its toes a little
      foot.y += 0.025 * s * Math.max(0, -weight * sign);
      rig.foot(side, rig.root.localToWorld(foot), rig.dir(0.25 * sign, 0, 1));
    }
    for (const side of ['L', 'R']) {
      const sign = side === 'L' ? 1 : -1;
      holdStick(rig, side, hands[side].tip, hands[side].bend, {
        length: this.malletLength, grip: 0.80, pole: rig.dir(0.6 * sign, -1.0, -0.5), mesh: this.mallets[side],
      });
    }
    if (rig.eyes) { rig.eyes.emissiveIntensity = 2 + 12 * Math.min(1.6, energy); }
    if (window.__debug) {
      const f = (v) => `${v.x.toFixed(2)},${v.y.toFixed(2)},${v.z.toFixed(2)}`;
      const sh = rig.root.worldToLocal(rig.bones.elbowL.getWorldPosition(new THREE.Vector3()));
      const hl = rig.root.worldToLocal(rig.bones.handL.getWorldPosition(new THREE.Vector3()));
      window.__dbg = `s ${s.toFixed(2)} shift ${shift.toFixed(2)} tipL ${f(hands.L.tip)} aimL ${f(hands.L.aim)} tipR ${f(hands.R.tip)} shoulderL ${f(sh)} handL ${f(hl)} nL ${this.byHand.L.length} nR ${this.byHand.R.length}`;
    }
  }
}
