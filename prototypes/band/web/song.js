/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// The song as recorded: the events of one render of Der Schmetterling (prototypes/band/ignored/schmetterling-events.json) beside
// its audio. Each player takes the events with its tags.
export async function loadSong(url) {
  const response = await fetch(url);
  if (!response.ok) { return null; }
  const song = await response.json();
  const byTag = {};
  for (const e of song.events) {
    for (const tag of e.tags) { (byTag[tag] ||= []).push(e); }
  }
  for (const list of Object.values(byTag)) {
    list.sort((a, b) => a.t - b.t);
    const loudest = Math.max(...list.map((e) => e.gain ?? 1), 1e-9);
    for (const e of list) { e.v = Math.min(1, Math.max(0.25, (e.gain ?? loudest) / loudest)); }
  }
  song.byTag = byTag;
  song.bpm = song.rpm * 4;                                            // a cycle is a bar of four beats
  return song;
}

const NAMES = { c: 0, d: 2, e: 4, f: 5, g: 7, a: 9, b: 11 };

/** A note's midi number, from its frequency, else from its name ("f#5", "eb4"). */
export function midiOf(e) {
  if (e.freq) { return Math.round(69 + 12 * Math.log2(e.freq / 440)); }
  const m = /^([a-g])([#sb]*)(-?\d+)$/i.exec(e.note || '');
  if (!m) { return null; }
  let n = NAMES[m[1].toLowerCase()] + 12 * (parseInt(m[3], 10) + 1);
  for (const c of m[2]) { n += c === 'b' ? -1 : 1; }
  return n;
}

/** One hit where several events sound at once (a part doubled left and right). */
export function merged(list, key) {
  const out = [];
  const last = {};
  for (const e of [...list].sort((a, b) => a.t - b.t)) {
    const k = key(e);
    // the doubles are a few thousandths apart (each has its own small delay), never further
    if (last[k] === undefined || e.t - last[k] > 0.03) { out.push(e); }
    last[k] = e.t;
  }
  return out;
}

/** The drummer's hits from the song: the kick by the foot, the snare by the left hand, the cymbals by the right. */
export function drumHits(song) {
  const hits = [];
  // hh: the closed hat; oh: the second hat, standing open; cr: the crash; rd: the ride
  const cymbal = (e) => {
    const s = (e.sound || '').toLowerCase();
    return s.startsWith('cr') ? 'crash' : s.startsWith('oh') ? 'hat2' : s.startsWith('rd') ? 'ride' : 'hat';
  };
  // the kick with both feet: the right one leads, and in a run the two take turns
  let foot = null;
  for (const e of merged(song.byTag.kick || [], () => 'k')) {
    const run = foot && e.t - foot.t < 0.45;
    foot = { t: e.t, drum: 'kick', limb: run && foot.limb === 'kickR' ? 'kickL' : 'kickR', v: e.v };
    hits.push(foot);
  }
  const snares = merged(song.byTag.snare || [], () => 's');
  let previous = null;
  for (const e of snares) {
    // a fast run on the snare is played by both hands in turn
    const fast = previous && e.t - previous.t < 0.22;
    const limb = fast && previous.limb === 'L' ? 'R' : 'L';
    previous = { t: e.t, drum: 'snare', limb, v: e.v };
    hits.push(previous);
  }
  // the right hand keeps the hat and the ride going; a crash or the open hat between them is the left hand's when it is free
  // (no snare just then), so no hand has to jump between two cymbals
  const busy = (limb, t, within) => hits.some((h) => h.limb === limb && Math.abs(h.t - t) < within);
  for (const e of merged([...(song.byTag.hats || []), ...(song.byTag.countin || [])].sort((a, b) => a.t - b.t), (x) => x.sound)) {
    const drum = cymbal(e);
    const limb = (drum === 'crash' || drum === 'hat2') && !busy('L', e.t, 0.19) ? 'L' : 'R';
    if (busy(limb, e.t, 0.12)) { continue; }
    hits.push({ t: e.t, drum, limb, v: e.v });
  }
  return hits.sort((a, b) => a.t - b.t);
}

/** A player's notes from the song's events with [tag]. */
export function notesOf(song, tag) {
  return merged(song.byTag[tag] || [], (e) => midiOf(e)).map((e) => ({ t: e.t, midi: midiOf(e), v: e.v, d: e.d })).filter((n) => n.midi !== null);
}

/** A string player's plucks and strums from the events with any of [tags]: what sounds together is one, its lowest note leading. */
export function strumsOf(song, tags) {
  const all = tags.flatMap((tag) => song.byTag[tag] || []).map((e) => ({ t: e.t, d: e.d, midi: midiOf(e), v: e.v })).filter((n) => n.midi !== null);
  all.sort((a, b) => a.t - b.t);
  const out = [];
  for (const n of all) {
    const last = out[out.length - 1];
    if (last && n.t - last.t < 0.03) {
      if (!last.all.includes(n.midi)) { last.all.push(n.midi); }
      last.midi = Math.min(last.midi, n.midi); last.v = Math.max(last.v, n.v); last.d = Math.max(last.d, n.d);
    } else { out.push({ ...n, all: [n.midi] }); }
  }
  return out;
}
