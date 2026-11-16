/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// Where the camera is: a fixed look where the page asks for one, else the choreography, else the total.
import * as THREE from 'three';
import { params, view, song, seed, BPM, times } from './show.js';
import { Choreography } from './camera.js';

/** Returns look(t), which places [camera] for this frame. Call it after everything that moves a figure. */
export function initView(scene, camera, rigs) {
  // The camera's choreography, when the song is there (?still: the wide view only)
  const choreography = song && !params.has('still') ? new Choreography(rigs, {
    drums: times(['kick', 'snare']), bass: times(['bass']), guitar1: times(['guitar1']), guitar2: times(['guitar2', 'guitar3']),
    marimba: times(['lead']), eimer: times(['trommel']),
  }, 240 / BPM, seed) : null;
  // ?head=<player>[&from=side|front|back|threequarter]: a close look at one head, with a plain inspection light, to
  // judge a head's shape (a still for the maintainer; no part of the show)
  let inspect = null;
  if (params.has('head')) {
    scene.add(new THREE.AmbientLight(0xffffff, 0.9));
    inspect = new THREE.DirectionalLight(0xffffff, 2.2);
    scene.add(inspect, inspect.target);
  }

  return function look(t) {
    if (params.has('head') && rigs[params.get('head')]) {
      const rig = rigs[params.get('head')];
      const at = rig.bones.head.getWorldPosition(new THREE.Vector3()).add(new THREE.Vector3(0, 0.07 * rig.height / 1.6, 0));
      const from = { side: [1, 0.05, 0], front: [0, 0.05, 1], back: [0, 0.1, -1], threequarter: [0.7, 0.1, 0.7] }[params.get('from') || 'side'];
      camera.position.copy(at).addScaledVector(rig.dir(...from).normalize(), 1.2);
      camera.lookAt(at);
      if (camera.fov !== 24) { camera.fov = 24; camera.updateProjectionMatrix(); }
      inspect.position.copy(camera.position).add(new THREE.Vector3(0.4, 0.8, 0));
      inspect.target.position.copy(at);
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
      const shot = choreography.at(t);
      camera.position.copy(shot.pos);
      camera.lookAt(shot.at);
      if (Math.abs(camera.fov - shot.fov) > 1e-3) { camera.fov = shot.fov; camera.updateProjectionMatrix(); }
      return;
    }
    camera.position.set(0, 1.85, 9.4); camera.lookAt(0, 1.0, 0.0);
  };
}
