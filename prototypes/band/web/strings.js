/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// The string players: a bass or a guitar on a strap. The left hand goes along the neck with the note's pitch, the
// right hand plucks (bass) or strums down and up (guitar) on every note; the strings light up when played.
// All places are in the figure's own measures; the instrument hangs from the trunk and moves with it.
import * as THREE from 'three';
import { around, Mover, showSeed } from './strike.js';

const smooth = (x) => { const c = Math.min(1, Math.max(0, x)); return c * c * (3 - 2 * c); };
const lacquer = new THREE.MeshStandardMaterial({ color: 0x0a0a0d, roughness: 0.25, metalness: 0.3 });
const neckWood = new THREE.MeshStandardMaterial({ color: 0x1c140e, roughness: 0.5 });

// Body outlines, seen from the front with the neck to the right (x along the neck, y across, in the figure's
// measure): from the neck pocket's lower corner round the body to its upper corner, as points a smooth curve goes
// through. None is a maker's model: a double cutaway with two horns, a single cutaway, a long-horned bass.
const OUTLINES = {
  double: [[0.12, -0.045], [0.15, -0.09], [0.185, -0.125], [0.14, -0.15], [0.06, -0.14], [-0.02, -0.12], [-0.13, -0.175],
    [-0.225, -0.105], [-0.24, 0.0], [-0.225, 0.10], [-0.13, 0.17], [-0.02, 0.125], [0.07, 0.145], [0.16, 0.15],
    [0.215, 0.115], [0.17, 0.085], [0.12, 0.045]],
  single: [[0.12, -0.045], [0.165, -0.075], [0.19, -0.115], [0.14, -0.148], [0.05, -0.135], [-0.02, -0.118], [-0.13, -0.172],
    [-0.22, -0.108], [-0.235, 0.0], [-0.22, 0.108], [-0.13, 0.170], [-0.02, 0.122], [0.07, 0.140], [0.13, 0.112], [0.145, 0.07],
    [0.12, 0.045]],
  bass: [[0.15, -0.05], [0.18, -0.09], [0.22, -0.12], [0.16, -0.15], [0.05, -0.135], [-0.03, -0.115], [-0.16, -0.18],
    [-0.255, -0.11], [-0.275, 0.0], [-0.255, 0.11], [-0.15, 0.172], [-0.02, 0.125], [0.08, 0.14], [0.20, 0.137],
    [0.295, 0.10], [0.225, 0.075], [0.15, 0.05]],
};
const HEADS = {
  guitar: [[0, -0.022], [0.035, -0.032], [0.16, -0.038], [0.19, -0.012], [0.172, 0.026], [0.04, 0.03], [0, 0.022]],
  bass: [[0, -0.025], [0.04, -0.04], [0.19, -0.048], [0.23, -0.015], [0.20, 0.034], [0.05, 0.036], [0, 0.025]],
};
function outline(points, s) {
  const shape = new THREE.Shape();
  shape.moveTo(points[0][0] * s, points[0][1] * s);
  shape.splineThru(points.slice(1).map(([x, y]) => new THREE.Vector2(x * s, y * s)));
  shape.closePath();
  return shape;
}
const metal = new THREE.MeshStandardMaterial({ color: 0x1a1a20, roughness: 0.25, metalness: 0.95 });

const KINDS = {
  //        scale: nut to bridge; body: half length, half width; strings; tilt of the neck; how the right hand plays
  //        (swing: how far it goes through the strings; arm: the share of that the arm does, the rest is the wrist)
  guitar: { scale: 0.64, body: [0.22, 0.165], strings: 6, neck: 0.044, tilt: 0.50, swing: 0.075, tau: 0.028, hang: -0.09, arm: 0.2, outline: 'double', pickups: [0.01, 0.075] },
  bass: { scale: 0.84, body: [0.25, 0.17], strings: 4, neck: 0.05, tilt: 0.62, swing: 0.022, tau: 0.02, hang: -0.13, arm: 1, outline: 'bass', pickups: [0.0, 0.07] },
};

export class StringPlayer {
  /**
   * [notes]: { t, midi, v } sorted by time, one per pluck or strum; [bpm] for the nod.
   * [style]: kind ('guitar' | 'bass'), lean (how the trunk leans, back is negative), bang (how much the head goes).
   */
  constructor(rig, notes, bpm, style) {
    this.rig = rig;
    this.bpm = bpm;
    this.notes = notes;
    this.style = { lean: 0.06, bang: 0.2, bounce: 0.03, ...style };
    this.mover = new Mover(this.style.character?.seed ?? 4, this.style.character);
    this.kind = KINDS[this.style.kind];
    this.s = rig.pelvis.y / 0.92;
    const s = this.s;
    const k = this.kind;

    // the instrument, in its own space: x along the neck toward the head, y across the strings (up), z out of its face
    const g = new THREE.Group();
    // the body: its outline cut from a slab, the edges rounded; pickups, a bridge and two knobs on its face
    const body = new THREE.Mesh(new THREE.ExtrudeGeometry(outline(OUTLINES[this.style.outline || k.outline], s),
      { depth: 0.036 * s, bevelEnabled: true, bevelSize: 0.010 * s, bevelThickness: 0.010 * s, bevelSegments: 3, curveSegments: 14 }), lacquer);
    body.position.z = -0.040 * s;
    this.bridge = -0.10 * s;
    this.nut = this.bridge + k.scale * s;
    const neckFrom = (k.body[0] - 0.10) * s;
    const neck = new THREE.Mesh(new THREE.BoxGeometry(this.nut - neckFrom, k.neck * s, 0.022 * s), neckWood);
    neck.position.set((this.nut + neckFrom) / 2, 0, 0.006 * s);
    const head = new THREE.Mesh(new THREE.ExtrudeGeometry(outline(HEADS[this.style.kind], s),
      { depth: 0.014 * s, bevelEnabled: true, bevelSize: 0.003 * s, bevelThickness: 0.003 * s, bevelSegments: 2, curveSegments: 10 }), lacquer);
    head.position.set(this.nut, 0, -0.012 * s);
    head.rotation.y = 0.10;
    g.add(body, neck, head);
    for (const x of k.pickups) {
      const pickup = new THREE.Mesh(new THREE.BoxGeometry(0.022 * s, k.neck * 1.25 * s, 0.008 * s), metal);
      pickup.position.set(x * s, 0, 0.012 * s);
      g.add(pickup);
    }
    const saddle = new THREE.Mesh(new THREE.BoxGeometry(0.018 * s, k.neck * 1.3 * s, 0.014 * s), metal);
    saddle.position.set(this.bridge, 0, 0.014 * s);
    g.add(saddle);
    for (const [x, y] of [[-0.15, -0.085], [-0.19, -0.05]]) {
      const knob = new THREE.Mesh(new THREE.CylinderGeometry(0.011 * s, 0.013 * s, 0.014 * s, 12), metal);
      knob.rotation.x = Math.PI / 2;
      knob.position.set(x * s, y * s, 0.014 * s);
      g.add(knob);
    }
    for (let i = 0; i < k.strings; i++) {
      const tuner = new THREE.Mesh(new THREE.CylinderGeometry(0.006 * s, 0.006 * s, 0.03 * s, 8), metal);
      const along = (i + 0.7) / (k.strings + 0.6);
      tuner.position.set(this.nut + (0.03 + 0.14 * along) * s, -0.036 * s, -0.004 * s);
      g.add(tuner);
    }
    const glow = rig.eyes ? rig.eyes.emissive.clone() : new THREE.Color(0xffffff);
    // a string each, the lowest first, lit on its own when it is the one played
    this.strings = [];
    for (let i = 0; i < k.strings; i++) {
      const lit = new THREE.MeshStandardMaterial({ color: 0x303038, roughness: 0.3, metalness: 0.9, emissive: glow, emissiveIntensity: 0 });
      this.strings.push(lit);
      const y = (0.5 - i / (k.strings - 1)) * k.neck * 0.8 * s;         // the lowest string is the upper one
      const string = new THREE.Mesh(new THREE.BoxGeometry(this.nut - this.bridge, 0.0025 * s, 0.0025 * s), lit);
      string.position.set((this.nut + this.bridge) / 2, y, 0.02 * s);
      g.add(string);
    }
    g.traverse((o) => { if (o.isMesh) { o.castShadow = true; } });
    rig.root.parent.add(g);
    this.instrument = g;

    // where the left hand is for a note. The pitches played are shared out over the strings in order, low to high,
    // each string taking its share of the notes: so a part that lives on a few low notes still goes across the strings,
    // as a player would choose positions, and not all on the lowest one. Along the neck: by how far the note is
    // above the lowest one on its string.
    const pitches = [...new Set(notes.map((n) => n.midi))].sort((a, b) => a - b);
    // by how often each is played: a string takes its share of the notes, not of the pitches
    const share = {};
    let passed = 0;
    for (const p of pitches) {
      const count = notes.filter((n) => n.midi === p).length;
      share[p] = Math.min(k.strings - 1, Math.floor((passed + count / 2) * k.strings / Math.max(1, notes.length)));
      passed += count;
    }
    const stringOf = (midi) => share[midi];
    // the hand stays in one position on the neck, as a player's does: the notes of a string lie within the four
    // frets the fingers span there, and a higher note is reached by going to a higher string, not up the neck
    const base = {}, top = {};
    for (const p of pitches) { if (base[stringOf(p)] === undefined) { base[stringOf(p)] = p; } top[stringOf(p)] = p; }
    for (const n of notes) {
      const string = stringOf(n.midi);
      const span = top[string] - base[string];
      const fret = 3 + (span > 3 ? (n.midi - base[string]) * 3 / span : n.midi - base[string]);
      n.along = k.scale * s * (1 - Math.pow(2, -fret / 12)) - 0.012 * s;
      // where the string lies across the instrument, drawn two and a half times as far apart as the strings are:
      // the hands' way from string to string is a few centimetres and would not be seen
      n.across = (0.5 - string / (k.strings - 1)) * k.neck * 0.8 * s * 2.5;
      // the strings that sound: the note's own, and for a chord one more for each further note
      n.strings = (n.all || [n.midi]).map((_, i) => Math.min(k.strings - 1, string + i));
      // the right hand plays at the middle of the strings that sound; one string is picked, several are strummed
      const mid = n.strings.reduce((a, b) => a + b, 0) / n.strings.length;
      n.pick = (0.5 - mid / (k.strings - 1)) * k.neck * 0.8 * s * 2.5;
      n.wide = n.strings.length > 1 ? 1 : 0.4;
    }
    // the right hand's way through the strings at each note: down after a pause, else down and up in turn
    let way = 1;
    notes.forEach((n, i) => {
      const gap = i > 0 ? n.t - notes[i - 1].t : 9;
      way = gap > 0.28 ? 1 : -way;
      n.way = way;
    });
  }

  /** The left hand's place along the neck at [t]: it stays on a note and changes to the next just before it sounds. */
  fretAt(t) {
    const [before, after] = around(this.notes, t);
    const from = before || after;
    const to = after || before;
    if (!from) { return { along: 0.1 * this.s, across: 0, pick: 0, wide: 1 }; }
    const gap = Math.max(0.02, to.t - from.t);
    const window = Math.min(gap * 0.9, 0.32);                             // unhurried: the hand glides to the next note
    const move = after && before ? smooth(1 - (after.t - t) / window) : 1;
    const mix = (key) => from[key] + (to[key] - from[key]) * move;
    return { along: mix('along'), across: mix('across'), pick: mix('pick'), wide: mix('wide') };
  }

  /**
   * How much the player looks at the viewer at [t], 0 to 1 (style.glance: { at: the camera's place, every: seconds
   * between chances, chance: how many of them are taken }). Chance decides, the same at the same place in the song.
   */
  glanceAt(t) {
    const g = this.style.glance;
    if (!g) { return 0; }
    const slot = Math.floor(t / g.every);
    const roll = (n) => { const x = Math.sin((slot + n) * 12.9898 + this.mover.seed * 78.233 + showSeed() * 0.6180339) * 43758.5453; return x - Math.floor(x); };
    if (roll(0) > g.chance) { return 0; }
    const start = slot * g.every + 0.8 + roll(1) * (g.every - 6);
    const hold = 2.2 + 2.0 * roll(2);
    const x = t - start;
    return smooth(x / 0.7) * smooth((hold + 0.7 - x) / 0.9);
  }

  /** The right hand's place across the strings at [t], -1 (above them) to 1 (below): through them at each note. */
  strumAt(t) {
    const [before, after] = around(this.notes, t);
    const tau = this.kind.tau;
    const one = (n) => n.way * (0.4 + 0.6 * n.v) * Math.tanh((t - n.t) / tau);
    if (before && after) { return one(before) + (one(after) - one(before)) * smooth((t - before.t) / (after.t - before.t)); }
    return before ? one(before) : after ? one(after) : -0.5;
  }

  update(t) {
    const rig = this.rig;
    const s = this.s;
    const k = this.kind;
    const st = this.style;

    let energy = 0;
    const [last] = around(this.notes, t);
    // a string's light: bright at the pluck, quickly down to 30 %, held there while the note sounds (until the
    // next one at the latest), then fading out
    const lit = this.strings.map(() => 0);
    for (let i = this.notes.indexOf(last); i >= 0 && this.notes[i].t > t - 4.0; i--) {
      const n = this.notes[i];
      const age = t - n.t;
      const next = this.notes[i + 1];
      const held = Math.min(n.d ?? 0.3, next ? next.t - n.t : 9);
      const level = age < held ? 0.3 + 0.7 * Math.exp(-age / 0.045) : (0.3 + 0.7 * Math.exp(-held / 0.045)) * Math.exp(-(age - held) / 0.125);
      energy += n.v * Math.exp(-age / 0.25);
      for (const string of n.strings) { lit[string] = Math.max(lit[string], n.v * level); }
    }
    this.strings.forEach((material, i) => { material.emissiveIntensity = 3.4 * lit[i]; });
    const playing = Math.min(1, energy);

    // the body: knees giving on the beat, the trunk leaning, the head going with the beat
    const beat = t * this.bpm / 60;
    // how much he is playing drives the body, less so for a steady player
    const live = 0.35 + 0.65 * playing;
    const drive = live + (1 - live) * this.mover.c.steady;
    const nod = this.mover.nod(beat) * drive;
    const body = nod + this.mover.burst();
    const P = this.mover.parts;
    const sway = Math.sin(Math.PI * beat / 2) * drive * P.sway;
    rig.reset();
    rig.update();
    rig.moveHips(new THREE.Vector3(0.03 * sway * s, -(0.04 + st.bounce * body * P.knees) * rig.pelvis.y, 0));
    rig.turn('belly', new THREE.Vector3(1, 0, 0), st.lean + 0.04 * body * P.trunk);
    rig.turn('chest', new THREE.Vector3(0, 0, 1), -0.05 * sway);
    rig.turn('chest', new THREE.Vector3(0, 1, 0), 0.18);
    // a look at the viewer every now and then: the head comes up from the instrument and turns to the camera, holds
    // it a few seconds and goes back
    const look = this.glanceAt(t);
    let yaw = 0.35, pitch = 0;
    if (look > 0) {
      const eye = rig.root.worldToLocal(st.glance.at.clone());
      const from = rig.height + 0.10 * s;
      yaw = 0.35 + (Math.atan2(eye.x, eye.z) - 0.18 - 0.35) * look;     // the chest is turned 0.18 already
      pitch = Math.atan2(eye.y - from, Math.hypot(eye.x, eye.z)) * look;
    }
    rig.turn('neck', new THREE.Vector3(1, 0, 0), st.bang * 0.4 * nod * P.head * (1 - 0.7 * look));
    rig.turn('head', new THREE.Vector3(1, 0, 0), (0.12 + st.bang * nod * P.head) * (1 - 0.8 * look) - pitch);
    if (look < 0.5) { this.mover.apply(rig, t); }
    rig.turn('head', new THREE.Vector3(0, 1, 0), yaw);
    for (const side of ['L', 'R']) {
      const sign = side === 'L' ? 1 : -1;
      const foot = rig.ankle[side].clone();
      foot.x += 0.07 * sign * s;
      rig.foot(side, rig.root.localToWorld(foot), rig.dir(0.3 * sign, 0, 1));
    }

    // the instrument hangs from the trunk: before the right hip, its neck up and to the player's left
    const waist = rig.bones.chest.getWorldPosition(new THREE.Vector3());
    const X = rig.dir(Math.cos(k.tilt), Math.sin(k.tilt), 0.16).normalize();
    const face = rig.dir(0, 0.22, 1);
    const Z = face.addScaledVector(X, -face.dot(X)).normalize();
    const Y = new THREE.Vector3().crossVectors(Z, X);
    const at = waist.clone()
      .addScaledVector(rig.dir(1, 0, 0), -0.13 * s).addScaledVector(rig.dir(0, 1, 0), k.hang * s).addScaledVector(rig.dir(0, 0, 1), 0.17 * s);
    this.instrument.position.copy(at);
    this.instrument.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, Y, Z));
    const place = (x, y, z) => at.clone().addScaledVector(X, x).addScaledVector(Y, y).addScaledVector(Z, z);
    const point = (bone, d) => {
      const now = new THREE.Vector3(0, 1, 0).applyQuaternion(rig.bones[bone].getWorldQuaternion(new THREE.Quaternion()));
      rig.turnWorld(bone, new THREE.Quaternion().setFromUnitVectors(now, d.clone().normalize()));
    };

    // a hand points toward what it plays, but a wrist only bends so far: never more than [most] radians off the
    // line of its forearm (bent further it looked broken, the maintainer, 2026-11-16)
    const aim = (side, toward, most) => {
      const hand = rig.bones['hand' + side].getWorldPosition(new THREE.Vector3());
      const forearm = hand.sub(rig.bones['wrist' + side].getWorldPosition(new THREE.Vector3())).normalize();
      const want = toward.clone().normalize();
      const angle = forearm.angleTo(want);
      if (angle > most) {
        const axis = new THREE.Vector3().crossVectors(forearm, want).normalize();
        want.copy(forearm).applyAxisAngle(axis, most);
      }
      point('hand' + side, want);
    };

    // the left hand: under the neck at the note's place, the fingers coming up over the strings
    const fret = this.fretAt(t);
    // for an upper (lower-sounding) string the fingers reach further round the neck: the wrist comes up and out
    // the left hand goes across the neck by the strings' true distance only, and the wrist hardly at all: on a neck
    // a string is a finger's reach away, not a move of the hand (the right hand's way stays drawn larger)
    const cross = fret.across / 2.5;
    const onNeck = place(this.nut - fret.along, cross, 0.02 * s);
    const wristL = onNeck.clone().addScaledVector(Y, -0.07 * s + 0.10 * cross).addScaledVector(Z, -0.03 * s + 0.15 * cross);
    rig.hand('L', wristL, rig.dir(0.4, -1.0, -0.3));
    aim('L', onNeck.clone().sub(wristL), 0.75);

    // the right hand: over the strings between the bridge and the neck, through them on every note
    const strum = this.strumAt(t);
    const onStrings = place(this.bridge + 0.13 * s, fret.pick - strum * k.swing * fret.wide * s, 0.03 * s);
    // a guitarist strums from the wrist: the arm goes only a little of the way, the hand turns for the rest
    const wristR = place(this.bridge + 0.13 * s, fret.pick - strum * k.swing * fret.wide * k.arm * s, 0.03 * s)
      .addScaledVector(X, -0.07 * s).addScaledVector(Y, 0.05 * s).addScaledVector(Z, 0.05 * s);
    rig.hand('R', wristR, rig.dir(-0.9, -0.1, -0.7));
    aim('R', onStrings.clone().sub(wristR), 0.55);

    if (rig.eyes) { rig.eyes.emissiveIntensity = 2 + 13 * Math.min(1.6, energy); }
  }
}
