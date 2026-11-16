/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// The trumpet player (the song's `trommel` part, the "Gummieimer", which sounds like a held melody): raises the
// trumpet to the lips before a phrase, fingers the valves on every note, points the bell higher for a high note and
// lets it sink between phrases. The bell glows while a note sounds. All places are in the figure's own measures.
import * as THREE from 'three';
import { around, Mover, showSeed } from './strike.js';

const smooth = (x) => { const c = Math.min(1, Math.max(0, x)); return c * c * (3 - 2 * c); };
const brass = new THREE.MeshStandardMaterial({ color: 0x3a2c12, roughness: 0.28, metalness: 0.95, side: THREE.DoubleSide });
/** Which of the three valves are down, by the note: not a real fingering, but it changes as one does. */
const FINGERS = [[0, 0, 0], [0, 1, 0], [1, 0, 0], [1, 1, 0], [0, 1, 1], [1, 0, 1], [1, 1, 1]];

export class Trumpet {
  /** [notes]: { t, d, midi, v } sorted by time; [bpm] for the nod. */
  constructor(rig, notes, bpm, character = {}) {
    this.rig = rig;
    this.mover = new Mover(character.seed ?? 3, character);
    this.bpm = bpm;
    this.notes = notes;
    this.s = rig.pelvis.y / 0.92;
    const s = this.s;
    this.low = notes.length ? Math.min(...notes.map((n) => n.midi)) : 60;
    this.high = notes.length ? Math.max(...notes.map((n) => n.midi)) : 72;

    // the trumpet, in its own space: x from the mouthpiece to the bell, y up
    const g = new THREE.Group();
    const tube = (from, to, r) => {
      const m = new THREE.Mesh(new THREE.CylinderGeometry(r * s, r * s, (to - from) * s, 10), brass);
      m.rotation.z = -Math.PI / 2;
      m.position.x = (from + to) / 2 * s;
      return m;
    };
    this.length = 0.50 * s;
    g.add(tube(0.0, 0.05, 0.010), tube(0.03, 0.38, 0.0065));
    const profile = [];
    for (let i = 0; i <= 10; i++) {
      const u = i / 10;
      profile.push(new THREE.Vector2((0.007 + 0.058 * Math.pow(u, 2.6)) * s, u * 0.14 * s));
    }
    const bell = new THREE.Mesh(new THREE.LatheGeometry(profile, 24), brass);
    bell.rotation.z = -Math.PI / 2;
    bell.position.x = 0.36 * s;
    g.add(bell);
    // the loop under the tube, and the three valves
    const loop = new THREE.Mesh(new THREE.TorusGeometry(0.035 * s, 0.006 * s, 8, 20), brass);
    loop.scale.set(2.6, 1, 1);
    loop.position.set(0.20 * s, -0.04 * s, 0);
    g.add(loop);
    this.valves = [];
    for (let i = 0; i < 3; i++) {
      const casing = new THREE.Mesh(new THREE.CylinderGeometry(0.011 * s, 0.011 * s, 0.085 * s, 10), brass);
      casing.position.set((0.165 + i * 0.027) * s, -0.02 * s, 0);
      const cap = new THREE.Mesh(new THREE.CylinderGeometry(0.008 * s, 0.008 * s, 0.03 * s, 8), brass);
      cap.position.set((0.165 + i * 0.027) * s, 0.035 * s, 0);
      g.add(casing, cap);
      this.valves.push(cap);
    }
    const glow = rig.eyes ? rig.eyes.emissive.clone() : new THREE.Color(0xffee88);
    this.mouth = new THREE.MeshStandardMaterial({ color: 0x000000, emissive: glow, emissiveIntensity: 0, side: THREE.DoubleSide });
    const disc = new THREE.Mesh(new THREE.CircleGeometry(0.034 * s, 24), this.mouth);
    disc.rotation.y = Math.PI / 2;
    disc.position.x = 0.465 * s;
    g.add(disc);
    g.traverse((o) => { if (o.isMesh) { o.castShadow = true; } });
    rig.root.parent.add(g);
    this.trumpet = g;

    rig.update();
    this.headRest = rig.bones.head.getWorldQuaternion(new THREE.Quaternion());

    // where he stands to play, and the stretches in which he has nothing to play and strolls about the front of the
    // stage: from a little after a phrase to well before the next, so he is back in his place in time
    this.home = rig.root.position.clone();
    this.homeTurn = rig.root.rotation.y;
    this.strolls = [];
    let free = 3;
    for (let i = 0; i <= notes.length; i++) {
      const next = i < notes.length ? notes[i].t : 1e9;
      // he stands a good while first (the maintainer, 2026-11-16): twelve seconds after his last note, or after the start
      if (next - free > 30) { this.strolls.push({ from: free + 12, to: Math.min(next - 4, free + 12 + 3600), far: null }); }
      if (i < notes.length) { free = Math.max(free, notes[i].t + notes[i].d); }
    }
  }

  /** Where the stroll [k] has him at [t], off his place: a slow wandering that leaves from his place and comes back to it. */
  strollAt(k, t) {
    const ease = (x) => smooth(Math.min(1, Math.max(0, x)));
    // a stroll is rounds: out from his place, about, back to it, and there he waits a while before the next round.
    // The rounds differ in length, so it is no clockwork
    // (under the show's seed: the rounds start elsewhere in their order, and her way is another one)
    const z = showSeed();
    const ROUNDS = [[36, 50, 42, 58], [50, 42, 58, 36], [42, 58, 36, 50], [58, 36, 50, 42]][z % 4];
    let x = t - k.from, round = 0;
    while (x > ROUNDS[round % ROUNDS.length]) { x -= ROUNDS[round % ROUNDS.length]; round++; }
    const length = ROUNDS[round % ROUNDS.length];
    const wait = 9;
    const out = ease(x / 6) * ease((length - wait - x) / 6) * ease((k.to - t) / 7);
    // he never quite stops: every nine seconds or so he slows right down, to a fifth of his pace, and goes on
    const w = 2 * Math.PI / 9;
    const tau = (t - k.from) + (0.8 / w) * Math.sin(w * (t - k.from));
    const s = this.s;
    return new THREE.Vector3(
      out * (0.60 * Math.sin(0.15 * tau + z * 1.7) + 0.18 * Math.sin(0.34 * tau + 1.0 + z * 2.3) + 0.30) * s,
      0,
      // toward the audience: a little about, and in some rounds (about two in five, by the seed) she comes right
      // up to the camera in the round's middle, stays a moment and goes back
      out * (0.18 * Math.sin(0.21 * tau + 2.0 + z * 0.9) + 0.30) * s
        + (((round * 7 + z * 3) % 5) < 2 ? 3.4 * s * ease((x - 0.22 * length) / (0.2 * length)) * ease((0.72 * length - x) / (0.2 * length)) * ease((k.to - t) / 9) : 0),
    );
  }

  /** His place on the stage at [t], which way he walks, how fast, and how far he has walked in this stroll. */
  walkAt(t) {
    const k = this.strolls.find((x) => t > x.from && t < x.to);
    if (!k) { return { at: this.home, turn: this.homeTurn, speed: 0, far: 0 }; }
    const facing = (x) => {
      const ahead = this.strollAt(k, x + 0.2).sub(this.strollAt(k, x - 0.2)).multiplyScalar(1 / 0.4);
      const speed = ahead.length();
      // she looks where she goes, the more the faster she goes; standing, she faces the audience. Going away from it
      // she turns round and walks, her back to it: held sideways she slid backwards (the maintainer, 2026-11-16)
      const going = smooth(Math.min(1, speed / (0.05 * this.s)));
      let heading = Math.atan2(ahead.x, ahead.z) - this.homeTurn;
      heading = Math.atan2(Math.sin(heading), Math.cos(heading));
      return { speed, going, turn: heading * going };
    };
    // which way he faces, worked out once per stroll in tenths of a second: the direction of his way jumps where he
    // turns round or nearly stands, so he follows it with a body's inertia, never faster than a calm turn
    if (!k.turns) {
      k.turns = [];
      let now = 0;
      for (let x = k.from; x < Math.min(k.to, k.from + 1500) + 0.2; x += 0.1) {
        // by the shorter way round, and at most 57 degrees a second
        const want = facing(x).turn - now;
        now += Math.max(-0.10, Math.min(0.10, Math.atan2(Math.sin(want), Math.cos(want)) * 0.2));
        k.turns.push(now);
      }
    }
    const { speed, going } = facing(t);
    const i = Math.min(k.turns.length - 2, Math.max(0, Math.floor((t - k.from) / 0.1)));
    const u = Math.min(1, Math.max(0, (t - k.from) / 0.1 - i));
    const turn = k.turns[i] + (k.turns[i + 1] - k.turns[i]) * u;
    return { at: this.home.clone().add(this.strollAt(k, t)), turn: this.homeTurn + turn, speed, far: 0, going };
  }

  /** What sounds at [t]: the note, whether it sounds (fading shortly after it ends), how high it is, how hard it is blown. */
  soundAt(t) {
    const [before, after] = around(this.notes, t);
    const sounding = before && t < before.t + before.d ? 1 : before ? Math.exp(-(t - before.t - before.d) / 0.08) : 0;
    const note = before && (sounding > 0.05 || !after) ? before : after || before;
    const high = note ? (note.midi - this.low) / Math.max(1, this.high - this.low) : 0.5;
    return { note, sounding, high, blow: before ? before.v * sounding : 0 };
  }

  /** [put]: where she is put from outside (her walk-in); else she is at her place, or strolling. */
  update(t, put = null) {
    const rig = this.rig;
    const s = this.s;
    const [before, after] = around(this.notes, t);

    // is a note sounding, how high is it, and is the trumpet up: up from a little before a phrase to a little after
    const now = this.soundAt(t);
    const sounding = now.sounding;
    const note = now.note;
    // the body goes with the tune as a whole, never with a single note: how high she plays and how hard she blows
    // are taken over a good half second round this moment, so a new note cannot jerk her
    let high = 0, breath = 0, weights = 0;
    for (let i = -6; i <= 6; i++) {
      const weight = 7 - Math.abs(i);
      const then = this.soundAt(t + i * 0.06);
      high += weight * then.high;
      breath += weight * then.blow;
      weights += weight;
    }
    high /= weights;
    breath /= weights;
    const until = after ? after.t - t : 99;
    const since = before ? t - before.t - before.d : 99;
    const up = Math.max(smooth(1 - (until - 0.15) / 0.7), 1 - smooth((since - 0.9) / 0.8));
    const blow = before ? before.v * sounding : 0;

    const beat = t * this.bpm / 60;
    const nod = this.mover.nod(beat) * (1 - 0.7 * up);
    const body = nod + this.mover.burst();
    const P = this.mover.parts;
    const sway = Math.sin(Math.PI * beat / 2) * P.sway;
    const back = up * (0.08 + 0.16 * high) + 0.04 * breath;

    // strolling: the whole figure goes along his way and turns into it; the legs step by the way made
    const walk = put || this.walkAt(t);
    rig.root.position.copy(walk.at);
    rig.root.rotation.y = walk.turn;
    const going = walk.going || 0;
    // he walks in time: a step every two beats, so two steps a bar; a step is as long as his pace asks for. And he
    // dances a little as he goes: the hips dip on every beat and sway onto the standing leg, the shoulders turn
    // against the hips, the arms swing wide
    const stride = beat / 4;
    const step = Math.min(0.5 * s, walk.speed * 0.6 * 4 * 60 / this.bpm);
    const swing = Math.cos(2 * Math.PI * stride);                      // 1 as the left foot lands ahead
    const dip = going * (0.5 + 0.5 * Math.cos(2 * Math.PI * beat));    // 1 on the beat

    rig.reset();
    rig.update();
    rig.moveHips(new THREE.Vector3(0.025 * sway * s * (1 - going) + 0.035 * s * going * Math.sin(2 * Math.PI * stride), -(0.010 + 0.014 * body * P.knees * (1 - 0.6 * going) + 0.008 * breath) * rig.pelvis.y - 0.010 * s * dip, -0.02 * back * s));
    rig.turn('hips', new THREE.Vector3(0, 1, 0), 0.10 * going * swing);
    rig.turn('chest', new THREE.Vector3(0, 1, 0), -0.26 * going * swing);
    rig.turn('chest', new THREE.Vector3(0, 0, 1), 0.05 * going * Math.sin(2 * Math.PI * stride));
    // the frontman stands tall: chest out, head up, hardly a bend in the knees
    rig.turn('belly', new THREE.Vector3(1, 0, 0), -0.03 - 0.5 * back + 0.025 * body * P.trunk);
    rig.turn('chest', new THREE.Vector3(1, 0, 0), -0.35 * back);
    rig.turn('chest', new THREE.Vector3(0, 0, 1), -0.04 * sway);
    // he plays across the stage, not at the audience: the bell is beside his face, never before it
    rig.turn('chest', new THREE.Vector3(0, 1, 0), 0.30 * up);
    rig.turn('head', new THREE.Vector3(0, 1, 0), 0.38 * up);
    rig.turn('head', new THREE.Vector3(1, 0, 0), -0.04 * (1 - up) + 0.09 * nod * P.head - 0.45 * back);
    this.mover.apply(rig, t);
    for (const side of ['L', 'R']) {
      const sign = side === 'L' ? 1 : -1;
      const foot = rig.ankle[side].clone();
      foot.x += 0.06 * sign * s * (1 - 0.5 * going);
      // a step: on the floor the foot goes back under him, then it lifts and swings ahead
      const phase = ((stride + (side === 'L' ? 0 : 0.5)) % 1 + 1) % 1;
      const air = phase < 0.6 ? null : (phase - 0.6) / 0.4;
      foot.z += going * step * (air === null ? 0.5 - phase / 0.6 : -0.5 + air);
      foot.y += going * (air === null ? 0 : 0.07 * s * Math.sin(Math.PI * air));
      rig.foot(side, rig.root.localToWorld(foot), rig.dir(0.3 * sign, 0, 1));
    }

    // the trumpet: at the lips, pointing where the face points; lowered before the chest between phrases
    const head = rig.bones.head;
    const turned = head.getWorldQuaternion(new THREE.Quaternion()).multiply(this.headRest.clone().invert());
    const lips = head.getWorldPosition(new THREE.Vector3()).add(rig.dir(0, 0.045, 0.100).multiplyScalar(0.110 * s).applyQuaternion(turned));
    const ahead = rig.dir(0, -0.20, 1).applyQuaternion(turned);
    // between phrases the right arm hangs loose, nearly straight, and the trumpet hangs from the hand beside his
    // thigh, the bell down: nothing is held up
    const hip = rig.bones.hips.getWorldPosition(new THREE.Vector3());
    const hang = rig.dir(-0.03, -0.97, 0.22).normalize();
    const shoulderR = rig.bones.elbowR.getWorldPosition(new THREE.Vector3());
    const loose = shoulderR.clone()
      .addScaledVector(rig.dir(1, 0, 0), -0.04 * s).addScaledVector(rig.dir(0, 1, 0), -0.95 * rig.armLength)
      .addScaledVector(rig.dir(0, 0, 1), (0.07 + 0.13 * going * swing) * s).addScaledVector(rig.dir(0, 1, 0), 0.05 * s * going * Math.abs(swing));
    const hangUp = rig.dir(0, 0.22, 0.97);
    const hangY = hangUp.addScaledVector(hang, -hangUp.dot(hang)).normalize();
    const hangZ = new THREE.Vector3().crossVectors(hang, hangY);
    const resting = loose.clone().addScaledVector(hang, -0.17 * s).addScaledVector(hangY, -0.035 * s).addScaledVector(hangZ, -0.085 * s);
    const at = resting.lerp(lips, up);
    const X = hang.clone().lerp(ahead, up).normalize();
    const upward = rig.dir(0, 1, 0);
    const Y = upward.addScaledVector(X, -upward.dot(X)).normalize();
    const Z = new THREE.Vector3().crossVectors(X, Y);
    this.trumpet.position.copy(at);
    this.trumpet.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, Y, Z));
    const place = (x, y, z) => at.clone().addScaledVector(X, x * s).addScaledVector(Y, y * s).addScaledVector(Z, z * s);
    const point = (bone, d) => {
      const now = new THREE.Vector3(0, 1, 0).applyQuaternion(rig.bones[bone].getWorldQuaternion(new THREE.Quaternion()));
      rig.turnWorld(bone, new THREE.Quaternion().setFromUnitVectors(now, d.clone().normalize()));
    };

    // the right hand on the valves from the right, the left hand round them from the left and below; elbows out.
    // Z points to the player's right when the trumpet points ahead.
    const valvesTop = place(0.19, 0.05, 0);
    const wristR = place(0.17, 0.035, 0.085);
    rig.hand('R', wristR, rig.dir(-0.9, -0.5, 0.1).lerp(rig.dir(-0.3, -0.3, -1), 1 - up));
    // playing, the hand turns onto the valves; hanging, it just follows the arm
    const forearm = wristR.clone().sub(rig.bones.wristR.getWorldPosition(new THREE.Vector3())).normalize();
    point('handR', forearm.lerp(valvesTop.clone().sub(wristR).normalize(), smooth(up)));
    const grip = place(0.20, -0.02, 0);
    // the left hand comes to the trumpet only to play; else that arm hangs
    const hanging = hip.clone()
      .addScaledVector(rig.dir(1, 0, 0), rig.shoulder.L.x + 0.05 * s).addScaledVector(rig.dir(0, 1, 0), -0.10 * s)
      .addScaledVector(rig.dir(0, 0, 1), (0.04 + 0.03 * sway * (1 - going) - 0.20 * going * swing) * s)
      .addScaledVector(rig.dir(0, 1, 0), 0.07 * s * going * Math.abs(swing)).addScaledVector(rig.dir(1, 0, 0), 0.04 * s * going);
    const wristL = hanging.clone().lerp(place(0.18, -0.05, -0.085), up);
    rig.hand('L', wristL, rig.dir(0.9, -0.6, 0.1).lerp(rig.dir(0.3, -0.3, -1), 1 - up));
    if (up > 0.5) { point('handL', grip.clone().sub(wristL)); }

    // the valves go down with the note's fingering
    const fingers = note && sounding > 0.05 ? FINGERS[((note.midi % 7) + 7) % 7] : [0, 0, 0];
    this.valves.forEach((cap, i) => { cap.position.y = (0.035 - 0.014 * fingers[i]) * s; });

    this.mouth.emissiveIntensity = 5 * blow;
    if (rig.eyes) { rig.eyes.emissiveIntensity = 2 + 14 * blow; }
  }
}
