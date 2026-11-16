/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// A figure's rig: its bones by the names of the joints they end at, its measures, and the two-bone reach.
// Nothing here knows a figure's shape: every length is read from the bones, so a reshaped figure moves the same.
import * as THREE from 'three';

const v1 = new THREE.Vector3(), v2 = new THREE.Vector3(), v3 = new THREE.Vector3();
const q1 = new THREE.Quaternion(), q2 = new THREE.Quaternion(), q3 = new THREE.Quaternion();

export class Rig {
  constructor(root) {
    this.root = root;
    this.bones = {};
    this.eyes = null;
    root.traverse((o) => {
      if (o.isBone) { this.bones[o.name] = o; }
      if (o.isSkinnedMesh) {
        o.frustumCulled = false;
        o.castShadow = true;
        const mats = Array.isArray(o.material) ? o.material : [o.material];
        for (const m of mats) { if (m.name.endsWith('_eyes')) { this.eyes = m; } }
      }
    });
    root.updateWorldMatrix(true, true);
    this.rest = {};
    for (const [name, bone] of Object.entries(this.bones)) {
      this.rest[name] = { q: bone.quaternion.clone(), p: bone.position.clone(), world: bone.getWorldPosition(new THREE.Vector3()) };
    }
    // The measures, in the figure's own space (the root at the origin): motion is written in these, not in metres
    const local = (name) => this.root.worldToLocal(this.rest[name].world.clone());
    this.pelvis = this.restTail('hips');
    // the figure's scale: its leg against the plain one's. Every player's instrument and motion is measured in it
    this.s = this.pelvis.y / 0.92;
    this.shoulder = { L: local('elbowL'), R: local('elbowR') };       // the bone to the elbow starts at the shoulder
    this.armLength = local('elbowL').distanceTo(local('wristL')) + local('wristL').distanceTo(local('handL'));
    this.ankle = { L: local('toeL'), R: local('toeR') };              // the bone to the toe starts at the ankle
    this.height = local('head').y;
    // which way the face looks, in the head bone's own space
    this.faceAhead = this.dir(0, 0, 1).applyQuaternion(this.bones.head.getWorldQuaternion(new THREE.Quaternion()).invert());
  }

  /** Where the bone [name] ends at rest, in the figure's space: its first child's start. */
  restTail(name) {
    const child = this.bones[name].children.find((c) => c.isBone);
    return this.root.worldToLocal(this.rest[child.name].world.clone());
  }

  /** Back to the rest pose: every frame starts here. */
  reset() {
    for (const [name, bone] of Object.entries(this.bones)) {
      bone.quaternion.copy(this.rest[name].q);
      bone.position.copy(this.rest[name].p);
      bone.scale.set(1, 1, 1);
    }
  }

  update() { this.root.updateWorldMatrix(true, true); }

  /** Turns the bone [name] by the world rotation [r], where it is. */
  turnWorld(name, r) {
    const bone = this.bones[name];
    bone.getWorldQuaternion(q1);
    bone.parent.getWorldQuaternion(q2).invert();
    bone.quaternion.copy(q2).multiply(q3.copy(r).multiply(q1));
    bone.updateWorldMatrix(false, true);
  }

  /** Turns the bone [name] about an axis of the figure (x to its left, y up, z ahead) by [angle]. */
  turn(name, axis, angle) {
    v1.copy(axis).transformDirection(this.root.matrixWorld);
    this.turnWorld(name, q3.setFromAxisAngle(v1, angle).clone());
  }

  /** Moves the hips by [offset], in the figure's space. */
  moveHips(offset) {
    const bone = this.bones.hips;
    const at = bone.getWorldPosition(v1).add(v2.copy(offset).transformDirection(this.root.matrixWorld).multiplyScalar(offset.length()));
    bone.parent.worldToLocal(at);
    bone.position.copy(at);
    bone.updateWorldMatrix(false, true);
  }

  /**
   * Two bones reach for [target] (world): [upper] starts at the first joint (shoulder, hip), [lower] at the middle one
   * (elbow, knee), [end] at the last (wrist, ankle). [pole] (world direction) is where the middle joint bends to.
   */
  reach(upper, lower, end, target, pole) {
    const a = this.bones[upper].getWorldPosition(new THREE.Vector3());
    const b = this.bones[lower].getWorldPosition(new THREE.Vector3());
    const c = this.bones[end].getWorldPosition(new THREE.Vector3());
    const lab = a.distanceTo(b), lbc = b.distanceTo(c);
    const dir = target.clone().sub(a);
    const lat = Math.min(Math.max(dir.length(), Math.abs(lab - lbc) + 1e-4), (lab + lbc) * 0.999);
    dir.normalize();
    const cosA = (lab * lab + lat * lat - lbc * lbc) / (2 * lab * lat);
    const sinA = Math.sqrt(Math.max(0, 1 - cosA * cosA));
    const perp = pole.clone().addScaledVector(dir, -pole.dot(dir));
    if (perp.lengthSq() < 1e-8) { perp.set(0, -1, 0).addScaledVector(dir, dir.y); }
    perp.normalize();
    const elbow = a.clone().addScaledVector(dir, lab * cosA).addScaledVector(perp, lab * sinA);
    const reached = a.clone().addScaledVector(dir, lat);

    this.turnWorld(upper, new THREE.Quaternion().setFromUnitVectors(b.clone().sub(a).normalize(), elbow.clone().sub(a).normalize()));
    const b2 = this.bones[lower].getWorldPosition(new THREE.Vector3());
    const c2 = this.bones[end].getWorldPosition(new THREE.Vector3());
    this.turnWorld(lower, new THREE.Quaternion().setFromUnitVectors(c2.sub(b2).normalize(), reached.sub(b2).normalize()));
  }

  /**
   * Turns the face toward [target] (world), [w] of the way (0 to 1): the neck a little, the head the rest. Never
   * further than a head can go: about 70 degrees to a side, 30 up or down from straight ahead.
   */
  faceTo(target, w) {
    if (w <= 0) { return; }
    const head = this.bones.head;
    const from = head.getWorldPosition(new THREE.Vector3());
    const local = this.root.worldToLocal(target.clone()).sub(this.root.worldToLocal(from.clone()));
    const yaw = Math.max(-1.25, Math.min(1.25, Math.atan2(local.x, local.z)));
    const pitch = Math.max(-0.5, Math.min(0.5, Math.atan2(local.y, Math.hypot(local.x, local.z))));
    const want = this.dir(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    for (const [bone, share] of [['neck', 0.3 * w], ['head', w]]) {
      const now = this.faceAhead.clone().applyQuaternion(head.getWorldQuaternion(new THREE.Quaternion()));
      const turn = new THREE.Quaternion().setFromUnitVectors(now, want);
      this.turnWorld(bone, new THREE.Quaternion().slerp(turn, share));
    }
  }

  /**
   * Breathing: the belly goes out and in, the chest a little with it ([breath] 0 breathed out to 1 breathed in,
   * [depth] how far the belly goes, as a share of its girth). A bone is made wider across (its own y runs along it),
   * and the bones that hang on it are made narrower by as much, so only this part of the body swells. Call it last
   * in a frame: the other moves read the bones' turns, which a width would blur.
   */
  breathe(breath, depth) {
    const belly = 1 + depth * breath, chest = 1 + 0.4 * depth * breath;
    this.bones.belly.scale.set(belly, 1, belly);
    this.bones.chest.scale.set(chest / belly, 1, chest / belly);
    for (const child of this.bones.chest.children) {
      if (child.isBone) { child.scale.set(1 / chest, 1, 1 / chest); }
    }
  }

  /** Which way the bone [name] points (its own y axis), in the world. */
  axisOf(name) {
    return new THREE.Vector3(0, 1, 0).applyQuaternion(this.bones[name].getWorldQuaternion(new THREE.Quaternion()));
  }

  /** Points the bone [name] along [d] (world). */
  point(name, d) {
    this.turnWorld(name, new THREE.Quaternion().setFromUnitVectors(this.axisOf(name), d.clone().normalize()));
  }

  /** A direction of the figure's space in the world. */
  dir(x, y, z) { return new THREE.Vector3(x, y, z).transformDirection(this.root.matrixWorld); }

  hand(side, target, pole) { this.reach('elbow' + side, 'wrist' + side, 'hand' + side, target, pole); }

  foot(side, target, pole) { this.reach('knee' + side, 'ankle' + side, 'toe' + side, target, pole); }
}
