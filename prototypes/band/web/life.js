/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// What makes them alive apart from playing and moving with the beat: eye contact, the eyes' wavering glow, breath.
// All three are applied after the players' own poses, in this order.
import * as THREE from 'three';
import { seed } from './show.js';
import { smooth, hash01, valueNoise } from './util.js';

// Eye contact: every now and then two of them look at each other for a few seconds, then back to their playing.
// Chance picks the moment and the pair, the same at the same place in the song. Pairs that have something to say
// to each other: the rhythm section, neighbours, the frontwoman and whoever she passes.
const PAIRS = [['bass', 'drums'], ['guitar1', 'bass'], ['eimer', 'guitar1'], ['marimba', 'guitar2'], ['drums', 'marimba'],
  ['eimer', 'marimba'], ['eimer', 'drums'], ['bass', 'eimer'], ['guitar2', 'drums'], ['guitar1', 'guitar2']];
const CONTACT = { every: 11, chance: 0.4 };
const X = new THREE.Vector3(1, 0, 0);

/** Who looks at whom at [t], and how far each head has turned; null when nobody does. [herNotes]: when the frontwoman plays. */
function contactAt(t, herNotes) {
  if (t < 1) { return null; }
  const slot = Math.floor(t / CONTACT.every);
  const roll = (n) => hash01((slot + n) * 12.9898 + 311.7 + seed * 0.6180339);
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
  if (pair.includes('eimer') && herNotes.some((x) => x > start - 3 && x < start + away + 3)) { return null; }
  const x = t - start;
  // No staring. The first one glances over, a second or so, and is half back at the playing when the other
  // notices and looks; then the first looks again and they share it briefly. A head never turns all the way: a
  // good half, as people look with their eyes; the beat's nod goes on underneath.
  const glance = 0.50 * smooth(x / 1.2) * smooth((2.0 - x) / 0.9);
  const again = 0.62 * smooth((x - late - 0.4) / 0.9) * smooth((away + 0.9 - x) / 0.9);
  const second = 0.58 * smooth((x - late) / 1.1) * smooth((away + 0.35 + 0.9 - x) / 0.9);
  // as their eyes meet, a small nod to each other, the second a moment after the first
  const greet = (at) => { const d = (x - at) / 0.28; return Math.exp(-d * d); };
  return { pair, w: [Math.max(glance, again), second], nod: [greet(meet + 0.25), greet(meet + 0.45)] };
}

/** Turns the heads of the two who look at each other at [t]. */
export function eyeContact(rigs, herNotes, t) {
  const contact = contactAt(t, herNotes);
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

/**
 * The eyes: a third less bright than their players set them, and never steady. Their glow wanders by smooth noise,
 * each pair on its own (two layers: a slow drift and a quicker waver), by about a third up and down. It scales what
 * the players set this frame, so it runs once a frame, after them.
 */
export function eyesWaver(rigs, t) {
  Object.values(rigs).forEach((rig, i) => {
    if (!rig.eyes) { return; }
    const noise = (x, own) => valueNoise(x, (k) => hash01((k + own * 57.3) * 12.9898 + seed * 0.6180339));
    const waver = 0.62 * noise(t * 0.7, i + 1) + 0.38 * noise(t * 2.9, i + 11);
    rig.eyes.emissiveIntensity *= 0.66 * (0.68 + 0.64 * waver);
  });
}

/**
 * Everyone breathes: at their own pace (14 to 19 breaths a minute), in a little faster than out, and deeper when
 * the band is loud ([slow]: the sound's energy). Last of all that moves bones: it widens them.
 */
export function breathing(rigs, t, slow) {
  Object.values(rigs).forEach((rig, i) => {
    const phase = 2 * Math.PI * (0.23 + 0.016 * ((i * 7) % 6)) * t + i * 1.9 + seed * 0.7;
    const breath = 0.5 + 0.5 * Math.sin(phase + 0.45 * Math.sin(phase));
    rig.breathe(breath, 0.045 * (0.7 + 0.6 * slow));
  });
}
