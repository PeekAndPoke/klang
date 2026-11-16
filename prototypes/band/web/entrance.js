/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// The walk-in before the song: each player comes from off the stage to their place. The clock runs from -INTRO to 0
// (the song's start); a player's entrance sets where the figure stands, and the legs then step by the way made,
// whatever the rest of the body does (a guitarist walks in holding the guitar, the frontwoman with her trumpet).
import * as THREE from 'three';
import { smooth } from './util.js';

/**
 * Puts [rig] where its entrance has it at [t]: from `from` (x, z, off the stage) to its place `home` (x, z, turn, y)
 * between the times `t[0]` and `t[1]`; a place above the floor (the riser) is stepped up to near the end. Returns how
 * it walks: going (0 standing to 1 walking), far (the way made), since (seconds since it arrived, negative before).
 */
export function enter(rig, entrance, home, t) {
  const [x, z, turn, y = 0] = home;
  const [t0, t1] = entrance.t;
  const u = Math.min(1, Math.max(0, (t - t0) / (t1 - t0)));
  const k = smooth(u);
  const from = new THREE.Vector3(entrance.from[0], 0, entrance.from[1]);
  const to = new THREE.Vector3(x, 0, z);
  const way = from.distanceTo(to);
  rig.root.position.copy(from).lerp(to, k);
  rig.root.position.y = y * smooth((u - 0.70) / 0.18);
  // walking, the figure faces its way; arriving, it turns to how it stands
  const heading = Math.atan2(to.x - from.x, to.z - from.z);
  let d = turn - heading;
  d = Math.atan2(Math.sin(d), Math.cos(d));
  rig.root.rotation.y = heading + d * smooth((u - 0.80) / 0.20);
  const speed = u <= 0 || u >= 1 ? 0 : way * 6 * u * (1 - u) / (t1 - t0);
  return { going: smooth(speed / 0.45), far: way * k, since: t - t1 };
}

/** The legs step by the way made ([walk] from [enter]); the feet the pose had are kept as far as it stands. */
export function stepLegs(rig, walk) {
  if (walk.going < 0.002) { return; }
  const s = rig.s;
  const step = 0.42 * s;
  const stride = walk.far / (step / 0.6);
  rig.update();
  for (const side of ['L', 'R']) {
    const sign = side === 'L' ? 1 : -1;
    const phase = ((stride + (side === 'L' ? 0 : 0.5)) % 1 + 1) % 1;
    const air = phase < 0.6 ? null : (phase - 0.6) / 0.4;
    const foot = rig.ankle[side].clone();
    foot.z += step * (air === null ? 0.5 - phase / 0.6 : -0.5 + air);
    foot.y += air === null ? 0 : 0.08 * s * Math.sin(Math.PI * air);
    const stepping = rig.root.localToWorld(foot);
    const standing = rig.bones['toe' + side].getWorldPosition(new THREE.Vector3());
    rig.foot(side, standing.lerp(stepping, walk.going), rig.dir(0.2 * sign, 0, 1));
  }
}

/** A plain walk for a figure with nothing to hold up: upright, the arms hanging and swinging against the legs. */
export function walkPose(rig, walk) {
  const s = rig.s;
  const step = 0.42 * s;
  const stride = walk.far / (step / 0.6);
  const swing = Math.cos(2 * Math.PI * stride) * walk.going;
  rig.reset();
  rig.update();
  rig.moveHips(new THREE.Vector3(0, -0.012 * rig.pelvis.y, 0));
  rig.turn('chest', new THREE.Vector3(0, 1, 0), -0.16 * swing);
  for (const side of ['L', 'R']) {
    const sign = side === 'L' ? 1 : -1;
    const sh = rig.shoulder[side];
    const hand = new THREE.Vector3(sh.x + 0.05 * sign * s, sh.y - 0.94 * rig.armLength, 0.05 * s - 0.16 * s * swing * sign);
    rig.hand(side, rig.root.localToWorld(hand), rig.dir(0.3 * sign, -0.3, -1));
  }
  stepLegs(rig, { ...walk, going: 1 });
}

/** The pose of [rig] as it is now, to blend with another. */
export function capture(rig) {
  const pose = { hips: rig.bones.hips.position.clone(), q: {} };
  for (const [name, bone] of Object.entries(rig.bones)) { pose.q[name] = bone.quaternion.clone(); }
  return pose;
}

/** Sets [rig] between the poses [a] and [b], [w] of the way to [b]. */
export function blend(rig, a, b, w) {
  for (const [name, bone] of Object.entries(rig.bones)) { bone.quaternion.copy(a.q[name]).slerp(b.q[name], w); }
  rig.bones.hips.position.copy(a.hips).lerp(b.hips, w);
  rig.update();
}
