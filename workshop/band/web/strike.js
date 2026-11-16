// Striking with a stick or a mallet: where its tip is between two hits, and the arm that holds it.
// Shared by everyone who hits something (the drummer, the marimba player, the Gummieimer).
import * as THREE from 'three';

const UP = new THREE.Vector3(0, 1, 0);
const smooth = (x) => { const c = Math.min(1, Math.max(0, x)); return c * c * (3 - 2 * c); };

/** The hit before and the one after [t] in [list] (sorted by t). */
export function around(list, t) {
  let lo = 0, hi = list.length;
  while (lo < hi) { const mid = (lo + hi) >> 1; if (list[mid].t <= t) { lo = mid + 1; } else { hi = mid; } }
  return [list[lo - 1] || null, list[lo] || null];
}

/**
 * A stick at [t], for one hand's hits [list] ({ t, at, v }, `at` in the figure's space): where its tip is and how
 * the wrist is bent. On the head at each hit; after it a small bounce, idling low; before the next the wind-up, and
 * on the way the tip travels to the next place.
 *   rest: where the tip idles when there is no hit at all
 *   home: a place in front of this arm's shoulder; the tip is pulled back toward it as it rises
 *   height: a full stroke's height; windup: how long before a hit it starts, in s; hover: idle height, as a share
 */
export function strokeAt(list, t, { rest, home, height, windup = 0.34, hover = 0.16 }) {
  const [before, after] = around(list, t);
  const from = before ? before.at : rest;
  const to = after ? after.at : from;
  const gap = before && after ? after.t - before.t : 1;
  const since = before ? t - before.t : 9;
  const until = after ? after.t - t : 9;

  const move = from === to ? 1 : smooth((gap - until) / Math.max(0.05, gap * 0.8));
  const aim = from.clone().lerp(to, move);

  const window = Math.min(gap, windup);
  const p = after && until < window ? 1 - until / window : 0;
  const strength = after ? 0.45 + 0.55 * after.v : 0;
  const idle = hover * (1 - Math.exp(-since / 0.05));
  const lift = idle * (1 - p * p * p * p) + strength * Math.min(1, window / windup) * Math.sin(Math.PI * p * p);

  const toward = new THREE.Vector3(home.x - aim.x, 0, home.z - aim.z);
  if (toward.lengthSq() < 1e-6) { toward.set(0, 0, -1); }
  toward.normalize();
  const tip = aim.clone().add(new THREE.Vector3(0, height * lift, 0)).addScaledVector(toward, 0.18 * height * lift);
  // the wrist's bend against the forearm: cocked up a little in the wind-up, snapped down a little into the hit
  const snap = Math.max(Math.exp(-since / 0.06), Math.pow(p, 8));
  const bend = 0.42 * lift - 0.14 * snap;
  return { tip, bend, lift, aim };
}

/**
 * The arm [side] of [rig] holds a stick whose tip is at [tip] (the figure's space). The hand stays in line with the
 * forearm but for the wrist's [bend]: the forearm and the stick are one long bone from the elbow to the tip, the
 * upper arm and that bone reach for the tip, and the wrist lies on that bone. [mesh] is the stick, its length
 * [length] along its own y, held at [grip] of its length from the tip.
 */
export function holdStick(rig, side, tip, bend, { length, grip = 0.72, pole, mesh }) {
  const tipWorld = rig.root.localToWorld(tip.clone());
  const held = length * grip;
  const shoulder = rig.bones['elbow' + side].getWorldPosition(new THREE.Vector3());
  const elbowNow = rig.bones['wrist' + side].getWorldPosition(new THREE.Vector3());
  const upper = shoulder.distanceTo(elbowNow);
  const fore = elbowNow.distanceTo(rig.bones['hand' + side].getWorldPosition(new THREE.Vector3()));
  const whole = Math.sqrt(fore * fore + held * held + 2 * fore * held * Math.cos(bend));
  const off = Math.asin(Math.min(1, Math.max(-1, held * Math.sin(bend) / whole)));

  const line = tipWorld.clone().sub(shoulder);
  const far = Math.min(Math.max(line.length(), Math.abs(upper - whole) + 1e-3), (upper + whole) * 0.999);
  line.normalize();
  const cos = (upper * upper + far * far - whole * whole) / (2 * upper * far);
  const out = pole.clone().addScaledVector(line, -pole.dot(line)).normalize();
  const elbow = shoulder.clone().addScaledVector(line, upper * cos).addScaledVector(out, upper * Math.sqrt(Math.max(0, 1 - cos * cos)));
  const reached = shoulder.clone().addScaledVector(line, far);

  const along = reached.clone().sub(elbow).normalize();
  const axis = new THREE.Vector3().crossVectors(along, UP);
  if (axis.lengthSq() > 1e-6) { along.applyAxisAngle(axis.normalize(), -off); }
  rig.hand(side, elbow.clone().addScaledVector(along, fore), pole);

  const hand = rig.bones['hand' + side];
  const at = hand.getWorldPosition(new THREE.Vector3());
  const d = reached.clone().sub(at).normalize();
  const points = new THREE.Vector3(0, 1, 0).applyQuaternion(hand.getWorldQuaternion(new THREE.Quaternion()));
  rig.turnWorld('hand' + side, new THREE.Quaternion().setFromUnitVectors(points, d));
  mesh.position.copy(at).addScaledVector(d, length * (grip - 0.5));
  mesh.quaternion.setFromUnitVectors(UP, d);
}

// How much energy the sound has right now: the stage sets it every frame from the recording's measured loudness
// (`slow`: the last second and a half, 0 to about 1; `burst`: how far this moment is above that). The nod is as
// strong as the sound: nearly none in a quiet passage, deep when the band is loud, with an extra dip on a burst.
let energy = { slow: 0.7, burst: 0 };
export function setEnergy(now) { energy = now; }

const X_AXIS = new THREE.Vector3(1, 0, 0), Y_AXIS = new THREE.Vector3(0, 1, 0), Z_AXIS = new THREE.Vector3(0, 0, 1);

/** A number from 0 to 1 that belongs to [index] and [seed], always the same one. */
function own(index, seed) {
  const x = Math.sin(index * 12.9898 + seed * 78.233) * 43758.5453;
  return x - Math.floor(x);
}

/** A smooth wandering value from -1 to 1 along [x]: a new random aim at every whole number, eased between them. */
function drift(x, seed) {
  const i = Math.floor(x);
  const u = x - i;
  const ease = u * u * (3 - 2 * u);
  return (own(i, seed) + (own(i + 1, seed) - own(i, seed)) * ease) * 2 - 1;
}

/**
 * How one player moves with the music, apart from playing: the nod and a slow wandering of head and trunk. Each
 * player has a character of their own, so no two move alike, and a little chance in everything, so nobody moves
 * like a machine:
 *   depth: how deep the nod is; beats: how much each of the bar's four beats counts
 *   lag: late (lazy) or early (eager), in beats; soft: how round the nod is, in beats (more is smoother)
 *   chance: how much a nod's depth and timing vary; skip: how often a nod is left out, 0 to 1
 *   look, tilt, lean: how far the head turns and tilts and the trunk leans as they wander, in radians
 *   steady: how little the sound's energy changes the movement, 0 (follows it fully) to 1 (always the same)
 *   fits: the head goes all the way only in fits: { every: how long a mood lasts, in beats; share: how much of the
 *     time is a fit, 0 to 1; calm: the head's share between fits }
 *   parts: which parts of the body take the beat, each a factor: head (the nod itself), trunk (leaning into it),
 *     knees (giving on the beat), sway (the hips going from side to side)
 */
export class Mover {
  constructor(seed, traits = {}) {
    this.seed = seed;
    this.c = { depth: 1, beats: [1, 0.5, 0.8, 0.5], lag: 0, soft: 0.10, chance: 0.3, skip: 0, look: 0.10, tilt: 0.05, lean: 0.02, steady: 0, ...traits };
    this.parts = { head: 1, trunk: 1, knees: 1, sway: 1, ...traits.parts };
    this.head = this.parts.head;
  }

  /** One beat's nod at [beat]: round on both sides, down a little faster than up. */
  one(index, beat) {
    const c = this.c;
    const mine = own(index, this.seed);
    if (own(index, this.seed + 7) < c.skip) { return 0; }
    const weight = c.beats[((index % 4) + 4) % 4] * (1 - c.chance * 0.6 + c.chance * 1.2 * mine);
    const phase = beat - index - c.lag - (own(index, this.seed + 3) - 0.5) * 0.12 * c.chance;
    return weight * Math.exp(-(phase * phase) / (phase < 0 ? 0.030 : 0.110));
  }

  /**
   * The nod at [beat] (in beats), 0 to about 1, as strong as the sound's energy over the last second and a half:
   * it never follows a single loud moment.
   */
  nod(beat) {
    const c = this.c;
    if (c.fits) {
      // a slow mood, wandering between -1 and 1: above its threshold it is a fit, eased in and out
      const mood = drift(beat / c.fits.every, this.seed + 21);
      const fit = Math.min(1, Math.max(0, (mood - (1 - 2 * c.fits.share)) / 0.3));
      this.parts.head = this.head * (c.fits.calm + (1 - c.fits.calm) * fit * fit * (3 - 2 * fit));
    }
    // rounded: the mean over a short stretch round this moment, so nothing in it is abrupt
    let sum = 0;
    for (let k = -2; k <= 2; k++) {
      const b = beat + k * c.soft / 2;
      const index = Math.floor(b);
      sum += (3 - Math.abs(k)) * Math.max(this.one(index - 1, b), this.one(index, b), this.one(index + 1, b));
    }
    const strength = this.strength();
    return c.depth * (sum / 9) * (0.12 + 1.05 * strength);
  }

  /** How strong the movement is right now: the sound's energy, or for a steady player mostly a fixed amount. */
  strength() {
    const live = Math.min(1.25, Math.max(0, (energy.slow - 0.12) / 0.5));
    return live + (0.85 - live) * this.c.steady;
  }

  /**
   * What a sudden burst of sound adds, for the body only (trunk and knees): the head never jerks with it (the
   * maintainer, 2026-11-15: "the body can move like this but the nods are too much").
   */
  burst() {
    return this.c.depth * 0.25 * energy.burst * this.strength() * (1 - this.c.steady);
  }

  /** The slow wandering at [t] (seconds): the head looks about and tilts, the trunk leans; applied on top of the pose. */
  apply(rig, t) {
    const c = this.c;
    const alive = 0.5 + 0.5 * Math.min(1, energy.slow / 0.6);
    rig.turn('head', Y_AXIS, c.look * alive * drift(t * 0.23, this.seed + 11));
    rig.turn('head', Z_AXIS, c.tilt * alive * drift(t * 0.31, this.seed + 12));
    rig.turn('chest', Z_AXIS, c.lean * alive * drift(t * 0.13, this.seed + 13));
    rig.turn('chest', X_AXIS, c.lean * 0.6 * alive * drift(t * 0.17, this.seed + 14));
  }
}

const plain = new Mover(1);
/** The plain nod, with no character. */
export function nodAt(beat) { return plain.nod(beat); }
