/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// The band on stage, standalone (KLANG-00446): this file puts the pieces together and runs the frames.
// What each piece is: README.md. The page's switches: show.js.
import { params, seed, BPM, energyAt, times } from './show.js';
import { buildStage } from './stage.js';
import { loadBand, castBand, playBand } from './band.js';
import { eyeContact, eyesWaver, breathing } from './life.js';
import { initClock, startClock, showTime, waiting, houseAt } from './clock.js';
import { initView } from './view.js';
import { setEnergy } from './strike.js';
import { HOME } from './camera.js';

// Set up, in this order (what is made first is drawn first, and the players take their figures' places as home)
const stage = buildStage();
const rigs = await loadBand(stage.scene);
const viewer = HOME.pos.clone();              // where the camera is: who looks at the viewer looks there
const band = castBand(rigs, viewer);
const herNotes = times(['trommel']);          // when the frontwoman plays: she looks at nobody then
initClock();
stage.shade(band.drummer.kit);
const look = initView(stage.scene, stage.camera, rigs);

// Oversampling: the picture is drawn with more pixels than the screen shows and scaled down, which smooths edges
// and the glitter. 2x and 4x are the number of pixels drawn (so 1.41 and 2 times as many each way); the cost in
// frames grows about as much. The key o steps through them; ?oversample=2 starts with one.
const SAMPLES = [1, 2, 4];
let over = Math.max(0, SAMPLES.indexOf(parseInt(params.get('oversample') || '1', 10)));
const sample = () => {
  stage.renderer.setPixelRatio(Math.min(devicePixelRatio, 2) * Math.sqrt(SAMPLES[over]));
  stage.renderer.setSize(innerWidth, innerHeight);
};
if (over > 0) { sample(); }
addEventListener('keydown', (e) => {
  if (e.key.toLowerCase() === 'o' && !e.ctrlKey && !e.metaKey && !e.altKey) { over = (over + 1) % SAMPLES.length; sample(); }
});

addEventListener('resize', () => {
  stage.renderer.setSize(innerWidth, innerHeight);
  stage.camera.aspect = innerWidth / innerHeight;
  stage.camera.updateProjectionMatrix();
});

const hud = document.getElementById('hud');
if (params.has('nohud')) { hud.style.display = 'none'; }      // for stills that are compared with each other
startClock();
// frames a second, counted over each half second, and the longest single frame in it (a stutter shows there)
let fps = 0, worst = 0, counted = 0, since = performance.now(), before = performance.now();
let frames = 0;

/**
 * One frame. The order matters: the sound's energy first (the nod reads it); then everyone's place and pose; then
 * what is laid over the poses (eye contact, the eyes' glow, breath, breath last because it widens bones); then the
 * lights; then the camera, which follows the figures; and the viewer's place for the next frame's looks.
 */
function frame() {
  const t = showTime();
  const energy = energyAt(t);
  setEnergy(energy);
  playBand(rigs, band, t);
  eyeContact(rigs, herNotes, t);
  eyesWaver(rigs, t);
  breathing(rigs, t, energy.slow);
  stage.light(t, houseAt(), waiting());
  look(t);
  viewer.copy(stage.camera.position);
  stage.renderer.render(stage.scene, stage.camera);

  const nowMs = performance.now();
  worst = Math.max(worst, nowMs - before);
  before = nowMs;
  counted++;
  if (nowMs - since >= 500) { fps = counted * 1000 / (nowMs - since); hud.dataset.worst = worst.toFixed(0); counted = 0; worst = 0; since = nowMs; }
  hud.textContent = `camera: ${look.what()}  |  seed ${seed}  |  oversampling ${SAMPLES[over]}x (key o)  |  ${fps.toFixed(0)} fps  |  slowest frame ${hud.dataset.worst || '-'} ms  |  t ${t.toFixed(2)} s  |  beat ${(t * BPM / 60).toFixed(2)}`;
  frames++;
  if (frames > 2) { window.__ready = true; }
  requestAnimationFrame(frame);
}
frame();
