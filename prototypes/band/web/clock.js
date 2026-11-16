/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// The show's clock. With the recording, the audio's time is the stage's time, and before it runs the walk-in:
// the page waits for a click, then counts INTRO seconds up to 0 and starts the audio.
import { song, frozen } from './show.js';
import { INTRO } from './band.js';

let audio = null;         // the audio of the same render as the events
let introAt = null;       // when the show was started, by the page's clock
let playing = false;      // the walk-in is over and the audio runs
let started = 0;          // when the frames began (the clock of the page without a recording)

/** Makes the audio and the click to start. Call it once the band stands: a click starts the walk-in. */
export function initClock() {
  audio = song && frozen === null ? new Audio('../ignored/schmetterling.wav') : null;
  if (!audio) { return; }
  const hint = document.createElement('div');
  hint.textContent = 'click to start';
  hint.style.cssText = 'position:fixed;left:0;right:0;bottom:18px;text-align:center;color:#9ab;font:14px monospace';
  document.body.appendChild(hint);
  const toggle = () => {
    if (!playing) { if (introAt === null) { introAt = performance.now(); } } else if (audio.paused) { audio.play(); } else { audio.pause(); }
    hint.style.opacity = 0;
  };
  addEventListener('click', toggle);
  addEventListener('keydown', (e) => {
    if (e.code === 'Space') { toggle(); e.preventDefault(); }
    // right, during the walk-in: skip it
    if (e.code === 'ArrowRight') { if (!playing) { playing = true; introAt = introAt ?? 0; audio.play(); } else { audio.currentTime += 10; } }
    if (e.code === 'ArrowLeft') { audio.currentTime = Math.max(0, audio.currentTime - 10); }
  });
}

/** Call right before the first frame. */
export function startClock() { started = performance.now(); }

/** The show's time, in seconds: negative during the walk-in, the song's time after. Starts the audio when the walk-in runs out. */
export function showTime() {
  if (frozen !== null) { return frozen; }
  if (!audio) { return (performance.now() - started) / 1000 - INTRO; }
  if (playing) { return audio.currentTime; }
  if (introAt === null) { return -INTRO; }
  const t = (performance.now() - introAt) / 1000 - INTRO;
  if (t < 0) { return t; }
  playing = true;
  audio.play();
  return 0;
}

/** Whether the show still waits for its click or walks in (the audio does not run yet). */
export const waiting = () => frozen === null && !!audio && !playing;

/** The house lights, 0 to 1: nearly dark while waiting for the click; from the click up within a second, a flash first, like lamps switched on. */
export function houseAt() {
  if (!waiting()) { return 1; }
  const since = introAt === null ? -1 : (performance.now() - introAt) / 1000;
  return since < 0 ? 0.07 : Math.min(1, 0.07 + 0.93 * since / 0.9) + 0.5 * Math.exp(-since / 0.12);
}
