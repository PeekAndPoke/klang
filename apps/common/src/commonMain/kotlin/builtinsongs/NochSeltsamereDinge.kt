/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("unused")

package io.peekandpoke.klang.apps.common.builtinsongs

import io.peekandpoke.klang.apps.common.BuiltInSongs
import io.peekandpoke.klang.apps.common.Song

internal val nochSeltsamereDingeSong = Song(
    id = "${BuiltInSongs.PREFIX}-noch-seltsamere-dinge",
    title = "Noch seltsamere Dinge",
    rpm = 31.0,
    icon = "film",
    code = """import * from "stdlib"
import * from "sprudel"

// Song status: WIP
//
// Seltsamere Dinge, for the band. The same song, every part now one player's: the glass guitar split in two halves on
// one amp (the roots and the leaps), the arpeggio, the morse, a bass guitar under it all, and the kick and the snare of
// Der Schmetterling in place of the samples. The original stays as it was, as "Seltsamere Dinge".

let wait = 16
let keep = 32 * 6
let notchFreq = sine.range(320, 520).slow(20)
let snareHz   = 220                                // a3: the snare sits on the key

// INSTRUMENTS: the glass guitar, the morse key, the bass, the drums  -----------------------------------------------------

// Glass guitar rig  ------------------------------------------------------------------------------------------------------
// The guitar of Seltsamere Dinge as a rig, in the parts the guitars of Der Schmetterling have: the string and the pickup
// in the voice, the pedals and the room on the guitarists' cylinder. Its tells:
//   - the string: a supersaw that swells and holds, no pick, at half the sample rate: the grain of an old sampler
//   - the pickup: a low, resonant lowpass, darker than the neck humbucker's
//   - the grit: a twin of the string through a 2 bit crusher, before the envelope, so its tail fades crushed and never
//     gates; it comes in over the song and stands wide on either side of the clean one
//   - the notch: a wide notch that steps between 320 and 520 Hz with every chord, a slow, stepped phaser
//   - the glass: a glass body, and a vowel filter talking "e o e i a u" through the song

// The string, then the pickup, then the envelope: the order the voice strip had, so the crusher sees the string at its
// full level and the release of every note stays smooth.
let makeGlass = (pickup) => {

  // --- Overridable params ---------------------------------------------------------------------------------------
  // The supersaw's voices, spread and analog are its slots: the pattern's unison(...) and analog(...) set them.
  let pAttack  = Ign.param("attack",  0.01, "Attack")
  let pDecay   = Ign.param("decay",   1.00, "Decay")
  let pSustain = Ign.param("sustain", 0.66, "Sustain")
  let pRelease = Ign.param("release", 2.75, "Release")
  // --------------------------------------------------------------------------------------------------------------

  // The old line's onepole(23846) is left out: at 48 kHz it took next to nothing off the top.
  let string = Ign.supersaw()
    .coarse(2)                                     // half the sample rate: the grain

  return pickup(string)
    .adsr(pAttack, pDecay, pSustain, pRelease)
    .classic()
}

// The glass pickup: a low resonant peak, and the rumble below 60 Hz cut.
let pickupGlass = Ign.fn(x => x
  .highpass(60)
  .lowpass(1980, 2.5))

// The grit: a 2 bit crusher where the pickup would be, brighter and thinner than the clean string. Its bits are a param:
// the line moves them a little.
let pickupGrit = Ign.fn(x => x
  .crush(Ign.param("bits", 2.0, "Crusher bits"))
  .highpass(120)
  .lowpass(3410, 2.5))

let glassClean = makeGlass(pickupGlass)
let glassGrit  = makeGlass(pickupGrit)

// Morse key  -------------------------------------------------------------------------------------------------------------

// The morse key: a buzzer behind a narrow band, crushed to 5 bits, the sound of a radio far away. The same chain the
// pulse had, now an instrument of its own.
let morseKey = (() => {
  return Ign.square()
    .crush(5)
    .highpass(1000)
    .bandpass(2000, 7.0)
    .lowpass(3500)
    .adsr(0.03, 0.08, 0.2, 0.2)
    .classic()
})()

// Bass  ------------------------------------------------------------------------------------------------------------------

// Der Schmetterling's bass guitar: a sub sine and the harmonics 2f to 6f over it, the snare's frequency cut out.
let bassSnd = (() => {

  // --- Overridable params ----------------------------------------------------------------------
  let pAnalog  = Ign.slot.analog
  let pSub     = Ign.param("sub",         1.00, "Sub Volume")
  let pHarm    = Ign.param("harmonics",   1.00, "Harmonics Volume")
  // ----------------------------------------------------------------------------------------------

  let sub = Ign.sine(x => x.analog(pAnalog)).mul(pSub)
  let harmonics = Ign.sine(x => x.harmonics(6, 1.10).fundamental(0).analog(pAnalog).analogSpread(0.05)).mul(pHarm)

  return sub.plus(harmonics)
    .eq(e => e.band(freq = snareHz, q = 3.0, db = -3)) // let the snare cut through
    .mul(0.2)
    .distort(0.1, "soft")
    .classic()
})()

// Metal kick  ------------------------------------------------------------------------------------------------------------

// Der Schmetterling's kick (thump and crack) and snare (crack, body, wires).
let metalKick = (() => {
  let thump = Ign.sine()
    .pitchEnvelope(12, x => x.adsr(0.0001, 0.05, 0, 0))
    .adsr(0.0005, 0.35, 0.0, 0.05)
    .distort(0.2, "soft")   // the hard hit: the loud start clips, the tail stays clean

  let crack = Ign.whitenoise().bandpass(2600, 0.8).adsr(0.0003, 0.035, 0.0, 0.02).mul(1.8)

  return thump.plus(crack)
    .mul(0.25)
    .classic()
})()

// Metal snare  -----------------------------------------------------------------------------------------------------------

let metalSnare = (() => {
  let crack = Ign.whitenoise().highpass(1500).adsr(0.0002, 0.040, 0.0, 0.003).mul(3.0)
  let head  = Ign.sine().pitchEnvelope(7, x => x.adsr(0.0003, 0.015, 0, 0)).adsr(0.0005, 0.15, 0.0, 0.03).mul(3.0)
  let deep  = Ign.sine(Ign.freq().mul(0.75)).adsr(0.0005, 0.080, 0.0, 0.02).mul(2.5)
  let thud  = Ign.sine(x => x.fundamental(0)
      .partial(0.6571, 0.520)
      .partial(0.7571, 0.676)
      .partial(0.8714, 0.652, 0.5)
      .partial(0.9476, 0.826)
      .partial(1.0857, 0.938)
      .partial(1.2524, 1.000, 0.5)
      .partial(1.4333, 0.839)
      .partial(1.6524, 0.746)
      .partial(1.8952, 0.692, 0.5)
      .partial(2.1952, 0.591, 0.5)
      .partial(2.5333, 0.494)
      .partial(2.9381, 0.474)
      .partial(3.3905, 0.358, 0.5)
    ).adsr(0.0005, 0.050, 0.0, 0.02).mul(1.245)
  let shell = Ign.whitenoise().bandpass(800, 0.7).adsr(0.0005, 0.050, 0.0, 0.02).mul(2.0)
  let m2    = Ign.sine(Ign.freq().mul(1.59)).adsr(0.0005, 0.035, 0.0, 0.02).mul(0.8)
  let m3    = Ign.sine(Ign.freq().mul(2.14)).adsr(0.0005, 0.020, 0.0, 0.02).mul(0.8)
  let wires = Ign.whitenoise().times(Ign.sine().mul(0.8).plus(1.0))
    .bandpass(6000, 0.6).adsr(0.004, 0.11, 0.0, 0.03).mul(1.2)

  let drum = head.plus(deep).plus(thud).plus(shell).plus(m2).plus(m3).plus(wires)

  return drum.plus(crack)
    .mul(0.3)
    .shape("soft")
    .mul(1.4895)
    .classic()
})()

// KATALYSTS: the glass amp, and the cylinder it sits on  -----------------------------------------------------------------

// Amps  ------------------------------------------------------------------------------------------------------------------

// The notch pedal: a wide notch on the amp. The line sets it with every note (katp "notch.freq"), so it steps with the
// chords, as it did when it was each note's own.
let pedalNotch = Kat.fn(x => x.notch(Kat.param("notch.freq", 420), 0.5))

// One amp for both halves: the pedal, then the cylinder's strip, so the body and the vowel land.
let glassAmp = Katalyst(k => k.serial(pedalNotch).classic())

// Cylinders  -------------------------------------------------------------------------------------------------------------

// The glass guitarists' cylinder: everything it does, written once.
let glassBus = x => x.cylinder(3).katalyst(glassAmp)
  .body(material = "glass")
  .vowel(vowel = "e o e i a u".slow(24), wet = 0.40)

// THE SONG: the parts, then the arrangement  -----------------------------------------------------------------------------

// Glass guitar, the playing  ---------------------------------------------------------------------------------------------

// How the glass guitar is played: the clean string in the middle, its crushed twin coming in over 64 cycles and
// spreading out to both sides, a slow wobble on it. The swell over the first 32 cycles is the guitar's own.
let glassSwell = cat(saw.range(0.25, 1.0).pow(1.5).slow(32), pure(1).slow(256)).mul("1 0.95 0.975 0.95".fast(2))

let glass = x => x
  .unison(voices = 9, spread = saw.range(0.05, 0.45).slow(64)).analog(10)
  .gain(0.50).katp("notch.freq", notchFreq)
  .apply(
    clean => clean.sound(glassClean).adsrOff(),
    grit => grit.sound(glassGrit).adsrOff()
      .ignp("bits", "2.0".add(berlin.range(-1, 1).mul(0.75).slow(4)))
      .gain(saw.range(0.2, 1.0).slow(64).pow(1.1).mul(1.2))
      .vibrato(rate = "0.51".add(perlin.div(20)), semitones = 0.06)
      .pan(saw.range(0.5, 0.1).slow(64)).superimpose(pan(saw.range(0.5, 0.9).slow(64)))
  )
  .velocity(glassSwell)
  .apply(glassBus)

// Glass guitar, the roots  -----------------------------------------------------------------------------------------------

// One chord every four cycles, mostly the root alone.
export roots_pat =
  `<a1 [f1 c2 e1 [f1 c2]] [a1 [c2 f1] a1 [f1@3 e1]] [a1@2 [c2@3] [d1,d2] [c1,c2,c3] [d1,d1,d2,a1,a2]]>/4`

let rootsTime = note(roots_pat).struct("x!4").slow(16)

export roots = rootsTime.clip(0.7).apply(glass).tag("roots")

// Glass guitar, the leaps  -----------------------------------------------------------------------------------------------

// The same chords, leaping an octave up and down from them, and a fifth.
export leaps_pat = `<[12 12 7 12 12 [12 12] 0 -12] [12 12 0 12 12 [0 12] 0 -12]>/32`

export leaps = rootsTime.clip(0.825).scaleTranspose(leaps_pat)
  .pan(sine.range(0.3, 0.7).slow(20)).apply(glass).tag("leaps")

// Arpeggio  --------------------------------------------------------------------------------------------------------------

export arp_pat = `<[0 2 4 6 7 6 4 2]!14 [2 0 2 4 6 7 4 1] [-2 -1 0 2 7 4 -1 -3]>`

export arp = n(arp_pat)
  .scale("[c3:major c3:pentatonic c3:major c3:major]/16")
  .cylinder(2).s("supersaw").unison(voices = 11, spread = saw.range(0.05, 0.35).slow(16))
  .gain(0.6 * 0.11).distort(0.7).adsr(0.008, 3.0, 0.5, 0.2).lpf(attack = 0.008, decay = 5.0, sustain = 0.2, release = 0.1).clip(1.05)
  .hpf(800).lpf(freq = 1400, env = perlin.range(21.7, 27.9).slow(8), q = 1.5).analog(15).body(material = "violin", wet = 0.5)
  .pan(0.5).superimpose(
    x => x.hpf(800).lpf(freq = 1500, q = 5).bpf(freq = notchFreq, q = 1.0).transpose(12).gain(0.6 * 0.08).pan(0.3).superimpose(pan(0.7)),
    x => x.hpf(800).lpf(freq = 1500, q = 5).bpf(freq = notchFreq, q = 1.0).transpose(24).gain(0.6 * 0.04).pan(0.1).superimpose(pan(0.9))
  ).tag("arp")

// Morse  -----------------------------------------------------------------------------------------------------------------

export morse = n("0").morse("Schön ist es auf der Welt zu sein!")
  .scale("C5:major").scaleTranspose("0 -2 2 2".slow(32))
  .sound(morseKey).adsrOff().analog(2)
  .gain(0.35).clip(0.35).pan(berlin.slow(2))
  .cylinder(1).body(material = "membrane").tag("morse")

// Bass line  -------------------------------------------------------------------------------------------------------------

// The guitar's roots without their chords, on the same four cycle grid, played in eighths: the root, and an octave or
// a fifth up towards the end of each cycle.
export bass_pat = `<a1 [f1 c2 e1 [f1 c2]] [a1 [c2 f1] a1 [f1@3 e1]] [a1@2 c2 d1 c1 d1]>/4`

export bass = note(bass_pat).slow(16)
  .ply("<2 4 8 16>")
  .transpose("<[0 0 0 0 0 0 12 0] [0 0 0 0 0 7 12 0]>")
  .sound(bassSnd).velocity("1.0 0.80 0.90 0.80 0.95 0.80 0.90 0.85")
  .adsr(0.003, 5.00, 0.00, 0.015).clip(0.80).hpf(35)
  .gain(0.18).pan(0.5)
  .cylinder(4).tag("bass")

// Drums  -----------------------------------------------------------------------------------------------------------------

// The kick and the snare keep the old drum line: one bar of sixteen steps, played slower and faster through every 32
// cycles. One line, split in two: where the old one chose "bd|sd:5", the kick's "bd|~" and the snare's "~|sd" roll the
// same dice, so one of them plays.
let drumTime = x => x.slow("[8 8 8 8 8 8 4 [2 4]]/32").fast(2)

export kick_pat  = `[bd bd bd ~  bd ~ bd ~] [bd bd ~  ~  bd ~ bd|~  ~]`
export snare_pat = `[~  ~  ~  ~  ~  ~ ~  ~] [~  ~  sd ~  ~  ~ ~|sd ~]`

export kick = sound(kick_pat).apply(drumTime)
  .sound(metalKick).adsrOff().note("a1").gain(0.85).velocity("0.98 0.94 0.96 0.94").pan(0.5)
  .degrade(0.01).hpf(35)
  .cylinder(6).tag("kick")

export snare = sound(snare_pat).apply(drumTime)
  .sound(metalSnare).adsrOff().freq(snareHz).gain(0.50).pan(0.55)
  .degrade(0.01).lpf(13500)
  .cylinder(6).tag("snare")

export hats = sound("<[hh hh oh hh] [hh hh ~ hh] [hh hh oh hh] [hh hh ~ <cr!7 rd>]>")
  .cylinder(5).gain(0.5).pan(0.40).adsr(0.01, 0.15, 0.8, 2.0).fast(2).degrade(0.1).lpf(15000).late(0.001)
  .tag("hats")

// Claps  -----------------------------------------------------------------------------------------------------------------

export claps = sound("cp ~ cp ~ ~ cp cp ~  cp ~ ~ ~ cp cp ~ ~").slow(4).cylinder(0).gain(0.20).legato(2.0)
  .bpf(sine.range(2000, 2200).fast(3.14)).hpf(600)
  .tag("claps")

// Shore  -----------------------------------------------------------------------------------------------------------------

export shore = note("c").fast(4).sound("brown").legato(2)
  .cylinder(7).gain(0.08).pan(perlin.early(1.7).range(0.3, 0.7).slow(7)).adsr(0.5, 1.0, 1.0, 2.5)
  .bpf(freq = perlin.range(440, 440 * 4).segment(16).slow(6), q = sine.range(0.25, 5.0).slow(48).early(12))
  .tag("shore")

// Every line carries a tag with its own name (roots, leaps, arp, morse, bass, kick, snare, hats, claps, shore): the
// band of the live act reads the song by them. A tag changes nothing of the sound.

// Song  ------------------------------------------------------------------------------------------------------------------
// When each line plays, in cycles, as in Seltsamere Dinge: the claps go on after the morse, the shore never stops.

export song = stack(
  // Glass guitar: Skalpell the roots, Klaue the leaps, one amp
  roots.filterWhen(x => x < (wait * 4 + keep))                         // . solo() . mute()
  , leaps.filterWhen(x => x < (wait * 4 + keep))                       // . solo() . mute()
  , // Arpeggio
  arp.filterWhen(x => x >= wait * 4 && x < (wait * 4 + keep))          // . solo() . mute()
  , // Morse
  morse.filterWhen(x => x >= wait * 12 && x < (wait * 6 + keep))   // . solo() . mute()
  , // Bass
  bass.filterWhen(x => x >= wait * 1.75 && x < (wait * 1 + keep))      // . solo() . mute()
  , // Drums
  stack(
    kick.filterWhen(x => x >= wait * 1.75 && x < (wait * 1 + keep)),   // . solo() . mute()
    snare.filterWhen(x => x >= wait * 1.75 && x < (wait * 1 + keep)),  // . solo() . mute()
    hats.filterWhen(x => x >= wait * 1 && x < (wait * 2 + keep))       // . solo() . mute()
  ).reverb(wet = 0.30, size = 4.0, lowpass = 10000)
  , // Claps
  claps.filterWhen(x => x >= wait * 8 && x < (wait * 12 + keep))  // . solo() . mute()
  , // Shore
  shore
  , // Master
  master(Katalyst(k => k.oversampleAtLeast(4, r => r
    .reverb(0.10, 9, 6500)
    .mul(0.80)
    .bands(b => b
      .band(low => low.mul(1.00)).cut(150)
      .band(mid => mid.distort(0.15, "soft").mul(0.85)).cut(5000)
      .band(low => low.mul(1.00))
    ) // the glue on the mids only: the kick and the hats stay clean
    .mul(3.2)
    .limiter(threshold = -3.0, ratio = 20.0, knee = 2.0, attack = 0.005, release = 0.10, lookahead = 0.005)
  )))
)

// Rewritten for the band by Claude (Opus 5.5) from peekandpoke's Seltsamere Dinge: its guitar taken apart into a rig, its
// parts given to the players, the bass line and the drums of Der Schmetterling added.
//
// Inspired by: Kyle Dixon and Michael Stein's title music for Stranger Things, through Seltsamere Dinge.


    """,
)
