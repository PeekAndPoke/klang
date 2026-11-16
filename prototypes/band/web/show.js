/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// What the show is given: the page's switches, the seed of this performance, the recorded song and its loudness.
// Keys: space pauses, the arrows jump ten seconds; the camera by hand: see view.js (q w e r t y, 1 to 6, a s d, 0);
// f tries the front lamp's colours, one after the other (stage.js).
//   ?seed=7      this performance again (else another one each time the page opens)
//   ?t=1.25      freeze the time at 1.25 s (for stills; without a seed it takes seed 0, so stills stay comparable)
//   ?still       the camera stays on the total     ?nohud  no fps line (for stills that are compared)
//   ?view=side   or stadium, or a player's name: a fixed camera     ?cam=x,y,z&at=x,y,z  any camera
//   ?head=eimer&from=side   a close look at one head in plain light (&outline: its bare shape, black on white)
//   ?move=circle  every camera round is that kind: circle, arc or looks (to see one; else the seed decides)
//   ?front=1     the front lamp on (0: off; &fcolor=ff1408: in that colour)     ?nostrobe  no stroboscope     ?nofog  no fog on the floor     ?test  test notes, no recording
import { loadSong } from './song.js';
import { setShow } from './strike.js';

export const params = new URLSearchParams(location.search);
// Another performance every time the page is opened: one seed for all of the choreography (how each one moves with
// the music, the looks, the strolls, the lights' turns, the camera).
export const seed = params.has('seed') ? parseInt(params.get('seed'), 10)
  : params.has('t') ? 0 : Math.floor(Math.random() * 100000);
setShow(seed);
export const frozen = params.has('t') ? parseFloat(params.get('t')) : null;
export const view = params.get('view') || 'front';
// The song as recorded, when it is there (?test: the test notes instead)
export const song = params.has('test') ? null : await loadSong('../ignored/schmetterling-events.json').catch(() => null);
export const BPM = song ? song.bpm : 120;
// The recording's loudness over time (a value every 20 ms), measured once from the audio
const loudness = song ? await fetch('../ignored/schmetterling-energy.json').then((r) => (r.ok ? r.json() : null)).catch(() => null) : null;

/** The sound's energy at [t]: its level, the level over a second and a half (slow), how far above that it is (burst), and three bands. */
export function energyAt(t) {
  if (!loudness) { return { slow: 0.7, burst: 0, level: 0.7, low: 0.5, mid: 0.5, high: 0.5 }; }
  const i = Math.max(0, Math.min(loudness.level.length - 1, Math.round((t - loudness.offset) / loudness.step)));
  let burst = 0;
  for (let k = 0; k < 6; k++) { const j = Math.max(0, i - k); burst += Math.max(0, loudness.level[j] - loudness.slow[j]) / 6; }
  return { slow: loudness.slow[i], burst, level: loudness.level[i], low: loudness.low[i], mid: loudness.mid[i], high: loudness.high[i] };
}

/** When the events with [tags] start, sorted. */
export const times = (tags) => (song ? tags.flatMap((tag) => (song.byTag[tag] || []).map((e) => e.t)).sort((a, b) => a - b) : []);
