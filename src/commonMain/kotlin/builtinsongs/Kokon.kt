/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

internal val kokonSong = Song(
    id = "${BuiltInSongs.PREFIX}-kokon",
    title = "Kokon",
    rpm = 20.0,
    icon = "egg",
    code = """
import * from "stdlib"
import * from "sprudel"

// Kokon: before there is a butterfly, there is a cocoon.
//
// One guitar: the instrument of Der Schmetterling, its rig stages and its makeGuitar, played through four rigs picked
// from its menu. A clean arpeggio is spun one thread at a time, a second guitar answers, a heartbeat starts under the
// skin, volume swells stretch it from inside, it holds its breath, and the high gain rig splits it open. The heavy block
// plays twice, and the second time the band of Der Schmetterling joins with its drums. What flies out at the end is the
// Schmetterling's own lead, and the last chord is major.
//
// How it is built, bottom up:
//   notes  the harmony and the melodies, as patterns without scale or timing
//   lines  one guitarist playing one way, a function from notes to sound
//   parts  lines played together; a part starts on its own first cycle
//   song   arrange(): which part, for how many cycles (3 s each), in which order

let feel  = 15   // analog drift of the guitars, as in Der Schmetterling
let drunk =  1   // two guitarists, sober this time, mostly

// Rig stages (from Der Schmetterling)  ------------------------------------------------------------
let pickupNeck = x => x
  .lowpass(3400, 1.4)
  .notch(freq = Ign.freq().mul(4), q = 2.0)
  .mul(1.2)

let pickupSingle = x => x
  .lowpass(4800, 2.5)
  .mul(0.8)

let pickupHumbucker = x => x
  .lowpass(2500, 1.6)
  .mul(1.2)

let pedalStock = x => x

let pedalScreamer = x => x
  .plus(x.highpass(720).distort(0.35, "soft", 2).mul(0.6))
  .lowpass(3000)
  .mul(0.5)

let pedalBoost = x => x
  .highpass(400)
  .mul(1.2)

let preampClean = x => x
  .distort(0.12, "tube", 2).highpass(80)
  .eq(e => e.band(freq = 3500, q = 0.7, db = 2.0))
  .mul(3.5)

let preampCrunch = x => x
  .distort(0.30, "tube", 4).highpass(90)
  .distort(0.30, "tube", 4).highpass(90)
  .mul(1.9)

let preampHighGain = x => x
  .highpass(120)                                   // tight: no bass into the gain stages
  .distort(0.40, "tube", 4).highpass(110)
  .distort(0.55, "softsat", 4).highpass(100)
  .distort(0.65, "hard", 4)
  .lowpass(6200)                                   // the fizz
  .mul(0.25)                                       // volume

let powerPushPull = x => x
  .distort(0.25, "soft", 2)
  .eq(e => e.band(freq = 4000, q = 0.7, db = 2.0))
  .mul(1.6)

let powerClassA = x => x
  .distort(0.20, "asym", 2)
  .mul(1.4)

let cab4x12 = x => x
  .eq(e => e
    .band(freq =  120, q = 1.0, db =  3.0)         // thump: closed-back box resonance
    .band(freq =  400, q = 0.5, db =  7.8)         // roar:  low mids
    .band(freq = 2700, q = 1.7, db =  3.8)         // bark:  the upper-mid speaker peak
  )
  .lowpass(4980, 0.707, x => x.passes(2))          // the wall: 36 dB/oct, the fizz is gone
  .highpass(105, 0.707, x => x.passes(2))          // the low end

let cab1x12 = x => x
  .highpass(120, 0.707, x => x.passes(2))
  .eq(e => e.band(freq = 3200, q = 1.0, db = 3.0))
  .lowpass(6500, 0.707, x => x.passes(3))

// Drums and bass (from Der Schmetterling)  -------------------------------------------------------
let snareHz = 210

// The guitar (from Der Schmetterling)  -----------------------------------------------------------
// Let the snare cut through: a stage of its own, between the power amp and the cab.
let snareCut = x => x
  .eq(e => e
    .band(freq = snareHz, q = 1.5, db = -2)          // notch the snare
  )

// The string, the pregain and the envelope; the rig is everything after the string, in the order the signal takes.
let makeGuitar = (rig) => {
  let pVoices     = Ign.slot.voices
  let pSpread     = Ign.slot.spread
  let pAnalog     = Ign.slot.analog
  let pAttack     = Ign.param("attack",       0.006, "Attack")
  let pDecay      = Ign.param("decay",        1.000, "Decay")
  let pSustain    = Ign.param("sustain",      0.000, "sustain")
  let pRelease    = Ign.param("release",      0.030, "Release")

  let saw = Ign.supersaw(x => x.voices(pVoices).spread(pSpread)
    .phasePool(on = 1, kMin = 0.75, kMax = 0.90, warmup = 0, selection = "normal")
    .spreadPower(4.0).sideAtten(0.3).gainJitter(0.10).centerJitter(0.10)
    .analog(pAnalog.adsr(pAttack, pDecay, 0.05, pRelease)).analogSpread(0.5)
  )

  let signal = saw.mul(Ign.slot.pregain)
    .pitchEnvelope(0.5, x => x.adsr(0.001, 0.05, 0, 0))
    .plus(Ign.crackle(1.0).highpass(1200).adsr(0.003, 0.1, 0.0, 0.05).mul(1.0))
    .adsr(pAttack, pDecay, pSustain, pRelease, e => e.curves("linear", "linear", "linear"))

  // the string through the rig. No note-following highpass after the cab: the preamp tightens the bass at a fixed
  // frequency, and a filter that moves with every note gave every note the same shape, which the ear reads as
  // synthetic (2026-09-14)
  return rig(signal)
    .mul(0.14)
    .classic()
}

// Four rigs, one guitar. The cocoon is clean and dark, the answer is bright, the heartbeat is the clean rig into the big
// box (the 4x12 keeps the thump the 1x12 cuts), and the wings are the Schmetterling's own rhythm rig.
let cleanRig  = x => x.through(pickupNeck,      pedalStock,    preampClean,    powerClassA,   snareCut, cab1x12)
let brightRig = x => x.through(pickupSingle,    pedalBoost,    preampCrunch,   powerPushPull, snareCut, cab4x12)
let deepRig   = x => x.through(pickupNeck,      pedalStock,    preampClean,    powerClassA,   snareCut, cab4x12)
let heavyRig  = x => x.through(pickupHumbucker, pedalScreamer, preampHighGain, powerPushPull, snareCut, cab4x12)

let clean  = makeGuitar(cleanRig)
let bright = makeGuitar(brightRig)
  .eq(x => x.band(Ign.freq(), 30.0, Ign.constant(3).adsr(1.0, 1.0, 0.0, 0.1)))  // slight feedback
let deep   = makeGuitar(deepRig)
let heavy  = makeGuitar(heavyRig)

let metalKick = (() => {
  let thump = Ign.sine()
    .pitchEnvelope(36, x => x.adsr(0.0003, 0.025, 0, 0))
    .adsr(0.0005, 0.22, 0.0, 0.05)
    .distort(0.5, "soft")   // the hard hit: the loud start clips, the tail stays clean
 
  let crack = Ign.whitenoise().bandpass(3500, 0.8).adsr(0.0003, 0.025, 0.0, 0.02).mul(2.8)

  return thump.plus(crack)
    .mul(0.26)                                     // level: as loud as the bd shape it replaced, at the same gain(0.25)
    .classic()
})()

let metalSnare = (() => {
  let crack = Ign.whitenoise().highpass(1500).adsr(0.0002, 0.040, 0.0, 0.003).mul(3.0)
  // the body: the head, pushed a fifth up by the hit, a deeper sine under it, the thud of the stick driving the whole drum
  // (a dense cluster, not a tone, kept above 140 Hz and out of the mud band), and the shell around 800 Hz
  let head  = Ign.sine().pitchEnvelope(7, x => x.adsr(0.0003, 0.015, 0, 0)).adsr(0.0005, 0.15, 0.0, 0.03).mul(3.0)
  let deep  = Ign.sine(Ign.freq().mul(0.75)).adsr(0.0005, 0.080, 0.0, 0.02).mul(2.5)
  // The thud: 13 inharmonic sines from 138 to 712 Hz, a little over two semitones apart, weighted to the spectrum of the
  // pink noise band it replaces (2026-09-30). The noise band was about 230 Hz wide and 50 ms long, so every hit rolled
  // new dice: 6 dB of hit-to-hit loudness, 7.5 dB of peak. The cluster is the same on every hit. The flipped signs are the
  // start phases: all positive, the sines rise together and the hit spikes 20 dB over its level; this pattern, the
  // calmest of all 8192, leaves 12 dB, less than the noise had.
  let thud  = Ign.sine(Ign.freq().mul(0.6571)).mul(0.520)
    .plus(Ign.sine(Ign.freq().mul(0.7571)).mul(0.676))
    .plus(Ign.sine(Ign.freq().mul(0.8714)).mul(-0.652))
    .plus(Ign.sine(Ign.freq().mul(0.9476)).mul(0.826))
    .plus(Ign.sine(Ign.freq().mul(1.0857)).mul(0.938))
    .plus(Ign.sine(Ign.freq().mul(1.2524)).mul(-1.000))
    .plus(Ign.sine(Ign.freq().mul(1.4333)).mul(0.839))
    .plus(Ign.sine(Ign.freq().mul(1.6524)).mul(0.746))
    .plus(Ign.sine(Ign.freq().mul(1.8952)).mul(-0.692))
    .plus(Ign.sine(Ign.freq().mul(2.1952)).mul(-0.591))
    .plus(Ign.sine(Ign.freq().mul(2.5333)).mul(0.494))
    .plus(Ign.sine(Ign.freq().mul(2.9381)).mul(0.474))
    .plus(Ign.sine(Ign.freq().mul(3.3905)).mul(-0.358))
    .adsr(0.0005, 0.050, 0.0, 0.02).mul(1.245)
  let shell = Ign.whitenoise().bandpass(800, 0.7).adsr(0.0005, 0.050, 0.0, 0.02).mul(2.0)
  let m2    = Ign.sine(Ign.freq().mul(1.59)).adsr(0.0005, 0.035, 0.0, 0.02).mul(0.8)
  let m3    = Ign.sine(Ign.freq().mul(2.14)).adsr(0.0005, 0.020, 0.0, 0.02).mul(0.8)
  let wires = Ign.whitenoise().times(Ign.sine().mul(0.8).plus(1.0))   // the buzz: noise that rises and falls with the head
    .bandpass(6000, 0.6).adsr(0.004, 0.11, 0.0, 0.03).mul(1.2)

  let drum = head.plus(deep).plus(thud).plus(shell).plus(m2).plus(m3).plus(wires)

  // The preamp: every part peaks in the same millisecond, so the hit stood 25 dB over the snare's loudness and every
  // master limiter worked on the snare alone. A soft clip rounds that first peak and leaves the body as it was: 6 dB less
  // peak at the same loudness (2026-09-30).
  return drum.plus(crack)
    .mul(0.3).shape("soft", 2).mul(1.4895)         // level: as loud as the snare before the clip, at the same gain
    .classic()
})()

let bass = (() => {

  // --- Overridable params ----------------------------------------------------------------------
  let pAnalog  = Ign.slot.analog
  let pSub     = Ign.param("sub",         1.00, "Sub Volume")
  let pHarm    = Ign.param("harmonics",   1.00, "Harmonics Volume")
  // ----------------------------------------------------------------------------------------------

  // Sub: a bare sine. No filter: a sine has no harmonics to remove. This is the weight,
  // and it lives at 36 to 70 Hz where nothing else in the mix is.
  let sub = Ign.sine(x => x.analog(pAnalog)).mul(pSub)

  // Harmonics: sine partials at 2f .. 8f, gain 1/n, the fundamental left to the sub above. On the
  // low E that is 82 to 328 Hz, the band a small speaker can play and the ear folds back into 41 Hz.
  let harmonics = Ign.sine(x => x.harmonics(10, 1.0).fundamental(0).analog(pAnalog).analogSpread(0.5)).mul(pHarm)

  return sub.plus(harmonics)
    .eq(e => e.band(freq = snareHz, q = 3.0, db = -2)) // let the snare cut through
    .mul(0.2)
    .classic()
})()

// Notes  -----------------------------------------------------------------------------------------------------------
// Scale degrees in D minor, 0 is D3. No scale here: the song sets it once. A line moves its notes to its own octave.

// The cocoon: Dm(add9), Bbmaj7(#11), Gm9, Asus. One chord per cycle, four cycles a round.
export cocoonArp    = `<[0 4 7 8 9 8 7 4] [-2 2 4 8 9 8 4 2] [-4 0 2 4 5 4 2 0] [-3 1 4 7 8 7 4 1]>`
export cocoonRoots  = `<0 5 3 4>`
export cocoonPower  = `<[0, 4] [-2, 2] [-4,0] [-3,1]>`
export cocoonPower2 = `<[0,-3] [-2,-5] [-4,0] [-3,1]>`
export cocoonSwell  = `<[4,7,9] [4,8,9] [2,5,7] [1,4,8]>`

// The lift: the break climbs Bb, C, Dm, three cycles, then lands.
export liftArp   = `<[-2 2 4 8 9 8 4 2] [-1 3 6 8 10 8 6 3] [0 4 7 8 9 8 2 4]>`
export liftRoots = `<5 6 0>`
export liftPower = `<[-2,2] [-1,3] [0,4]>`

// The melody, in two phrases of one round each. The second fits the cocoon and the lift alike.
export melodyOne = `<[4@4 3 2 1 2] [1@4 ~ 0 1 2] [4@3 5 4@2 2 0] [1@6 ~@2]>`
export melodyTwo = `<[7@4 6 4 3 4] [8@4 ~ 7 8 9] [9@3 8 7@2 2 4] [4@6 ~@2]>`

// Out of the cocoon flies the Schmetterling's own lead.
export schmetterlingLead = `<[-7 0 2 4] [-7 0 4 2] [-5 -1 2 4] [-6 -1 4 3]>`

// Lines  -----------------------------------------------------------------------------------------------------------
// One guitarist, one way of playing. Each line sets its own level; a part may set another.

// A plucked string drops after the pick, then rings and fades. The clean lines pick a little softer (pregain), so the
// amp squeezes the drop less and each note stands out from the one still ringing.

// Spin: the clean arpeggio, each note picked, then ringing under the next.
export spin = notes => n(notes)
  .sound(clean).adsrOff().unison(voices = 7, spread = 0.05).pregain(0.7)
  .ignp("decay", 1.00).ignp("sustain", 0.25).ignp("release", 0.8).clip(2.0)
  .velocity("1.0 0.8 0.9 0.8 0.95 0.8 0.9 0.8")
  .gain(0.22).pan(0.5)                             // the arp guitarist stands dead centre
  .orbit(1).body(wet = 0.1, material = "oak")

// Sing: the melody on the bright rig, an octave up.
export sing = notes => n(notes.add(7))
  .sound(bright).adsrOff().unison(voices = 11, spread = 0.04) // a narrow chorus: a held note stays one note
  .ignp("decay", 3.0).clip(0.99)
  .tremolo(rate = 4, depth = perlin.range(0.325, 0.375)).accelerate(perlin.range(0.01, 0.02).slow(8).seg(8))
  .hpf(200)                                        // the 4x12 roar sits on the arp; the lowest note is D4 at 293 Hz
  .lpf(3800)                                       // the crunch fizz on held notes covers the arp's picks
  .gain(0.14).pan(0.6)                             // the melody stands near the centre, a little right
  .orbit(2).body(wet = 0.2, material = "rosewood").late(perlin.range(0.000, 0.001))

// Soar: the melody two octaves up, wider, over the wings.
export soar = notes => n(notes.add(14))
  .sound(bright).adsrOff().unison(voices = 15, spread = 0.05)
  .ignp("decay", 3.0).clip(1.5)
  .hpf(400)                                        // two octaves up, nothing of the melody lives below
  .lpf(4450)                                       // less fizz, the wall keeps its own
  .gain(0.39).pan(0.5)
  .orbit(6)

// Swell: volume-knob swells, the thing inside stretching. Doubled on the left, a little late.
export swell = chords => n(chords.add(7))
  .sound(bright).adsrOff().unison(voices = 11, spread = 0.06)
  .ignp("attack", 2.4).ignp("decay", 1.0).clip(1)
  .lpf(3500)
  .gain(0.07).pan(0.05).superimpose(x => x.pan(0.95).late(0.01)) // far left and far right, the right a little late
  .orbit(7).reverb(wet = 0.2, size = 6)       // a slight room of their own, inside the hall

// Beat: the heartbeat under the skin, 3-3-2 on the root, an octave down.
export beat = roots => n(roots.add(-7)).struct("x ~ ~ x ~ ~ x ~")
  .sound(deep).adsrOff().unison(voices = 7, spread = 0.06)
  .ignp("decay", 2.0).clip(2).hpf(80)
  .velocity("1.0 0.9 0.85")
  .gain(0.25).pan(0.3)                             // on the left, across from the melody on the right
  .orbit(3)

// Chug: the heavy rig, palm-muted on the root, an octave down.
export chug = roots => n(roots.add(-7)).struct("x x@2  x x@2  x x")
  .sound(heavy).adsrOff().unison(voices = 15, spread = 0.09)
  .ignp("decay", 0.8).clip(1)
  .velocity("1.0 0.75 0.9 0.80 0.95 0.80")
  .gain(1.00).pan(0.5).late(perlin.range(0.0000, 0.0008))
  .orbit(4)

// Wings: tremolo-picked power chords on the heavy rig, hard left and right.
export wings = chords => n(chords).ply(16)
  .sound(heavy).adsrOff().unison(voices = 11, spread = 0.09)
  .ignp("decay", 0.5).clip(1)
  .velocity("1.0 0.92!2 0.96 0.90 0.92 0.94 0.96")
  .gain(0.48).apply(
    x => x.pan(0.00).late(perlin.range(0.0005, 0.0013)),
    x => x.pan(1.00).late(perlin.range(0.0010, 0.0018)),
  )
  .orbit(4)

// Strike: one heavy chord, let ring. It shares the wings' orbit and room.
export strike = chords => n(chords)
  .sound(heavy).adsrOff().unison(voices = 11, spread = 0.09)
  .ignp("decay", 3.5).clip(1)
  .gain(0.7)
  .pan(0.1).superimpose(x => x.pan(0.9).late(0.000))
  .orbit(5)

// Chime: a melody on the clean rig, two octaves up, picked and let ring. The butterfly after the storm.
export chime = notes => n(notes.add(14))
  .sound(clean).adsrOff().unison(voices = 7, spread = 0.08).pregain(0.7)
  .ignp("decay", 0.30).ignp("sustain", 0.35).ignp("release", 1.2).clip(2)
  .gain(0.33).pan(0.75).late(perlin.range(0.001, 0.002))
  .orbit(10)

// Strum: the clean rig, one slow strum, let ring.
export strum = notes => n(notes)
  .sound(clean).adsrOff().unison(voices = 7, spread = 0.06).pregain(0.7)
  .ignp("decay", 0.30).ignp("sustain", 0.50).ignp("release", 5.0).clip(40)
  .gain(0.20).pan(0.45)
  .orbit(8)

// The drums, for the second run of the heavy block: the band of Der Schmetterling, heavy and half time. The kick on one,
// the snare on three, the Trommel rolling in on the off-beats of two and four, a crash on one and open hats on two to
// four. The kit shares one orbit and one room; the Trommel keeps its own for its membrane.
let drumRoom = x => x.reverb(wet = 0.2, size = 5)

export kick = pat => sound("bd").struct(pat)
  .sound(metalKick).adsrOff().note("a1").velocity("1.0 0.94 0.96 0.94")
  .gain(1.1).pan(0.5)
  .orbit(11).apply(drumRoom)

export snare = pat => sound(pat)
  .sound(metalSnare).adsrOff().freq(snareHz)
  .gain(0.48).pan(0.55)
  .lpf(freq = 11000, q = 0.5)
  .delay(0.33, pure(1/16).div(cps), 0.82, 16) // Snare needs it own orbit for the dalay!
  .orbit(12).apply(drumRoom).late(perlin.range(0.001, 0.0015))

export hats = pat => sound(pat).n(0)
  .velocity("1.0 0.7 0.85 0.7")
  .hpf(800).lpf(freq = 14500, q = 0.5).adsr(0.005, 0.1, 0.70, 2.0) // some body, less sizzle
  .gain(0.75).pan(0.4)                            
  .orbit(11).apply(drumRoom).late(perlin.range(0.002, 0.0035))

// Bass: the Schmetterling's bass guitar, on every kick, on the chord's root two octaves down.
export bassGuitar = (roots, pat) => n(roots.add(-14)).struct(pat)
  .sound(bass).velocity("0.98 0.96 0.97 0.96")
  .ignp("sub", 1.00).ignp("harmonics", 1.00)
  .adsr(0.003, 0.3, 0.5, 0.040).hpf(30).notch(freq = snareHz, q = 1.0)
  .clip(0.90)
  .gain(1.2).pan(0.5)
  .orbit(15)

// Parts  -----------------------------------------------------------------------------------------------------------
// Lines played together. Every part starts on its own first cycle, so a round of the cocoon always starts on Dm.

// The cocoon is spun one thread at a time, over two rounds, in the middle; the answer and the heartbeat will stand to
// either side of it.
let spinning = stack(
  spin(cocoonArp).gain(0.28).hpf(120).lpf(3300).unison(voices = 5, spread = 0.02).mask("<[1 0 0 0 1 0 0 0]!2 [1 0 1 0 1 0 1 0]!2 [1 0 1 1 1 0 1 1]!2 1!2>"),
)

// A second guitar answers.
let answering = stack(
  spin(cocoonArp).gain(0.28).hpf(120).lpf(3300).unison(voices = 5, spread = 0.02),
  sing(melodyOne).gain(0.28).hpf(300),
)

// The heartbeat starts.
let quickening = stack(
  spin(cocoonArp).gain(0.28).hpf(120).lpf(3300).unison(voices = 5, spread = 0.02),
  sing(melodyTwo).gain(0.28).hpf(312),
  beat(cocoonRoots).gain(saw.range(0.0, 0.22).slow(4)).lpf(1500),
)

// Swells stretch it from inside, the arpeggio grows.
let stretching = stack(
  spin(cocoonArp.ply(2)).gain("<0.28 0.29 0.30 0.31>").lpf(3300).unison(voices = 5, spread = 0.02),
  sing(melodyOne.ply(2)).gain("<0.28 0.29 0.30 0.31>").hpf(325),
  swell(cocoonSwell),
  beat(cocoonRoots).gain(0.18).lpf(1500),
)

// Still growing, and in the last half cycle the arpeggio and the heartbeat hold their breath.
let breath = "<1!3 [1 0]>"

let holdingBreath = stack(
  spin(cocoonArp.ply(2)).gain("<0.30 0.31 0.32 0.33>").lpf(3300).unison(voices = 5, spread = 0.02),
  sing(melodyTwo.struct("x!32")).gain("<0.30 0.31 0.32 0.33>").hpf(325),
  swell(cocoonSwell),
  beat(cocoonRoots).gain(0.20).lpf(1500),
)

// The high gain rig splits it open: the melody against the wall. The arpeggio waits for its own part.
let breakingOpen = stack(
  soar(melodyOne).vibrato(beatRate(0.5), 0.02),
  wings(cocoonPower),
  chug(cocoonRoots),
)

// The melody steps aside and the cocoon's own thread unravels over the heavy wall: the arpeggio an octave up, leading,
// in the centre between the wings.
let unravelling = stack(
  spin(cocoonArp.add(7)).ply(2).gain(0.58).pan(0.5),
  wings(cocoonPower2),
  chug(cocoonRoots),
)

// The lift: Bb, C, Dm.
let lifting = stack(
  spin(liftArp.add(7)).gain(0.15).ignp("sustain", 0.35).clip(0.66).pan(0.2).superimpose(pan(0.8)),
  soar(melodyTwo).vibrato(beatRate(0.5), saw.pow(4).mul(0.2).add(0.02).slow(4)),
  wings(liftPower),
  chug(liftRoots),
)

// It lands on one heavy chord with the low D under it, and the melody holds its A.
let landing = stack(
  spin("[0 4 7 9 11 9 7 4]".add(7)).gain(0.35).ignp("sustain", 0.15).clip(0.66),
  soar("[4@6 ~@2]"),
  strike("[-7,0,4]").accelerate("0.05".add(perlin.range(-0.20, 0.20))).ignp("release", 2.0),
  beat("0").gain(0.45),
)

// The cocoon again, empty now, and the butterfly flies off.
let flyingOff = stack(
  spin(cocoonArp),
  chime(schmetterlingLead),
)

// The last chord is D major: this part brings its own scale, and the first scale on a note wins.
let lastChord = stack(
  strum("<[0 4 7 9 11 ~@27] ~!3>").scale("d3:major").ignp("release", 6.0),
)

// The heavy block: the cocoon breaks open, the arpeggio unravels, the lift, the landing. Played twice, the second time
// with the band of Der Schmetterling behind it.
let heavyBlock = arrange(
  [4, breakingOpen],
  [4, unravelling],
  [3, lifting],
  [1, landing],
)

// The kit for the second run, for the chord roots and a kick pattern: the bass plays on every kick.
let fullKit  = (roots, kicks) => stack(
  kick(kicks),
  bassGuitar(roots, kicks),
  snare("~ ~ sd ~"),
  hats("[cr oh oh oh]"),
)

let kit = fullKit

let crash = hats("cr").gain(1.0)

// The kicks speed up through the run: one per cycle while it breaks open, two and four while it unravels, eights up
// the lift and sixteenths on its last cycle. The landing is one hit, let ring.
let heavyDrums = arrange(
  [4, kit(cocoonRoots, "x")],
  [4, kit(cocoonRoots, "<[x [~ x]] [x [~ x]] [x!2 ~!5 [x x]] [[x x] [~ [~ x x@2]]]>")],
  [3, kit(liftRoots, "<[x!7 [x!2]] [x!7 ~] [x!16]>")],
  [1, stack(crash, kick("x"), bassGuitar("0", "x"))],
)

// Song  ------------------------------------------------------------------------------------------------------------
export song = stack(
  arrange(
    [8, spinning],
    [4, answering],
    [4, quickening],
    [4, stretching],
    [4, holdingBreath],
    [12, heavyBlock],
    [12, stack(heavyBlock, heavyDrums)],
    [12, flyingOff],
    [4, lastChord],
  )
    .scale("d3:minor")
    .analog(feel)
   
  , master(Katalyst(k => k
    .reverb(0.20, 7, 7000)                         // the hall: one room for the whole band, about 2 s, warm
    .gain(1.15)                                    // the house level, -14 LUFS
    .limiter(threshold = -3.0, ratio = 20.0, knee = 2.0, attack = 0.005, release = 0.10, lookahead = 0.005) // the ceiling: peaks only
  ))
)



// Written by Claude (Opus 5.5) on the guitar of Der Schmetterling, which the maintainer and Claude built stage by stage.
// Fine-tuned and arranged with peekandpoke
//
// Inspired by: Philip Glass and Steve Reich, the additive process of minimal music. The spinning arpeggio grows thread
// by thread through its mask, and the listener hears the same notes transform instead of new ones arriving.
// Inspired by: Editors - Papillon, through the Schmetterling's lead that flies off at the end.







    """,
)
