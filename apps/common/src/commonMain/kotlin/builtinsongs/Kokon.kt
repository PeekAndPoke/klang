/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("unused")

package io.peekandpoke.klang.apps.common.builtinsongs

import io.peekandpoke.klang.apps.common.BuiltInSongs
import io.peekandpoke.klang.apps.common.Song

internal val kokonSong = Song(
    id = "${BuiltInSongs.PREFIX}-kokon",
    title = "Kokon",
    rpm = 19.0,
    icon = "egg",
    code = """import * from "stdlib"
import * from "sprudel"

// Kokon: before there is a butterfly, there is a cocoon.
//
// One guitar: the instrument of Der Schmetterling, its rig parts and its makeGuitar, played through four rigs picked
// from its menu, every line through an amp on its cylinder (chug and wings share one). A clean arpeggio is spun one thread at a
// time, a second guitar answers, a heartbeat starts under the skin, volume swells stretch it from inside, it holds its
// breath, and the high gain rig splits it open. The heavy block plays twice, and the second time the band of Der
// Schmetterling joins with its drums. What flies out at the end is the Schmetterling's own lead, and the last chord is
// major.
//
// How it is built, bottom up:
//   notes  the harmony and the melodies, as patterns without scale or timing
//   lines  one guitarist playing one way, a function from notes to sound
//   parts  lines played together; a part starts on its own first cycle
//   song   arrange(): which part, for how many cycles (3 s each), in which order

let feel      =  12        // analog drift of the guitars, as in Der Schmetterling
let drunk     =   1        // two guitarists, sober this time, mostly
let snareHz   = 400        // where does the snare sit?
let songScale = "c3:minor" // which scale to play the song in
let endScale  = "c3:major" // which chord the song ends on

// Guitar rig - Pickups  ---------------------------------------------------------------------------------------------------------------------------

// Simplest pickup ... direct passthrough
let pickupStock = Ign.fn(x => x)

// Single coil at the bridge: a high, sharp resonance, thin and bright, low output.
let pickupSingle = Ign.fn(x => x
  .lowpass(5000, 3.3)                              // the peak, up high and narrow
  .mul(0.8))                                       // low output, the amp stays cleaner

// Humbucker at the bridge: a lower, broader resonance, fat and dark, hot output that pushes the amp harder.
let pickupHumbucker = Ign.fn(x => x
  .lowpass(3100, 2.5)                              // the peak, low and wide
  .mul(1.3))                                       // hot output

// Humbucker at the neck: darker still, and the neck position cancels the 4th harmonic (the hollow, woody tell).
let pickupNeck = Ign.fn(x => x
  .lowpass(3500, 1.4)
  .notch(freq = Ign.freq().mul(4), q = 2.0)        // the neck sits a quarter along the string
  .mul(1.2))

// Guitar rig - Pedals  ---------------------------------------------------------------------------------------------------------------------------

// Simplest pedal ... direct passthrough
let pedalStock = Kat.fn(x => x)

// Screamer: only the mids go through the clipper, the clean signal goes around it: the mid hump over a clean bass.
let pedalScreamer = Kat.fn(x => x.parallel(
    clean => clean,
    dirt => dirt
      .highpass(220)
      .distort(0.15, "soft")
      .lowpass(6500, 1.8) // the tone knob, half way
      .mul(0.55)  // the clipper only ever sees the mids, the clean bass carries, both aligned
  )
  .mul(0.55))                                       // level

// Fuzz: everything clips, hard and lopsided, splatty.
let pedalFuzz = Kat.fn(x => x
  .distort(0.70, "asym")
  .lowpass(4500)
  .mul(0.28))                                      // level

// Treble booster: cuts the bass and pushes the rest into the preamp. The classic bright crunch.
let pedalBoost = Kat.fn(x => x.parallel(
  clean => clean, 
  dirt => dirt.highpass(600).mul(1.2)
).mul(0.65))

// Guitar rig - Pre-Amps  ---------------------------------------------------------------------------------------------------------------------------

let preampStock = Kat.fn(x => x
  .distort(0.20, "tube").highpass(100)
  .distort(0.50, "soft").highpass(100))

// Clean: one lightly driven tube stage and a bright cap that lets the top through.
let preampClean = Kat.fn(x => x
  .distort(0.12, "tube")
  .highpass(80)
  .eq(e => e.band(freq = 3500, q = 0.7, db = 2.0)) // the bright cap
  .mul(3.5))                                       // volume

// Crunch: two tube stages, coupled loosely so the bass goes along and the clipping stays round.
let preampCrunch = Kat.fn(x => x
  .highpass(120)
  .distort(0.35, "tube").highpass(120)
  .distort(0.18, "asym").highpass(100)
  .lowpass(5500)
  .mul(0.62))                                       // volume

// High gain: tighten the bass BEFORE it clips, three cascaded stages, the last one hard, then tame the fizz.
let preampHighGain = Kat.fn(x => x
  .highpass(120)                                    // tight: no bass into the gain stages
  .distort(0.10, "tube").highpass(120)
  .distort(0.15, "softsat").highpass(100)
  .distort(0.75, "soft")
  .lowpass(5200)                                   // the fizz
  .mul(0.75))                                      // volume

// Guitar rig - Power Amps  --------------------------------------------------------------------------------------------------------------------------

// Power amps: the last saturating stage. Tells: symmetric or not, and the presence bump. The last mul is the master.
let powerStock = Kat.fn(x => x.drive(0.3))

// Push-pull (class AB): symmetric clip, odd harmonics, and presence: the top opens up as it works.
let powerPushPull = Kat.fn(x => x
  .distort(0.25, "soft")
  .eq(e => e.band(freq = 4000, q = 0.7, db = 2.0)) // presence
  .mul(1.6))                                       // master

// Class A: a lopsided clip, even harmonics, the chime.
let powerClassA = Kat.fn(x => x
  .distort(0.25, "asym")
  .mul(1.3))                                       // master

// Guitar rig - Cabinets  --------------------------------------------------------------------------------------------------------------------------
// the linear half of the amp. All of these are static and linear.

// Two plain lowpasses at 5 kHz, the cab the guitar had before the cabs existed.
let cabStock = Kat.fn(x => x.lowpass(5000).lowpass(5000))

// 4x12 closed back: the air in the sealed box thumps, the speaker barks in the upper mids, and above 5 kHz there is a wall.
let cab4x12 = Kat.fn(x => x
  .eq(e => e
    .band(freq =  120, q = 0.6, db =  4.20)        // thump: closed-back box resonance
    .band(freq =  900, q = 0.7, db =  4.00)        // roar:  low mids
    .band(freq = 2700, q = 1.5, db =  1.30)        // bark:  the upper-mid speaker peak
  )
  .lowpass(5000, 0.707, x => x.passes(3))          // the wall: 36 dB/oct, the fizz is gone
  .highpass(90, 0.707, x => x.passes(2)))

// 1x12 open back: the open back cancels the bass, the top chimes and rolls off late and soft.
let cab1x12 = Kat.fn(x => x
  .highpass(120, 0.707, x => x.passes(2))          // open back: no low end below the box
  .eq(e => e
    .band(freq = 3200, q = 1.0, db = 3.0)          // chime: broad upper presence
  )
  .lowpass(6500, 0.707, x => x.passes(2)))         // soft, late roll-off, some air stays

// Small combo: a tiny speaker in a tiny box. No bass, a boxy honk, and the highs die early.
let cabCombo = Kat.fn(x => x
  .highpass(160, 0.707, x => x.passes(2))          // small speaker: nothing down low
  .eq(e => e
    .band(freq = 750, q = 1.4, db = 4.0)           // honk: the boxy midrange
  )
  .lowpass(3800, 0.707, x => x.passes(2)))         // early roll-off

// Let the snare cut through: a stage of its own, between the power amp and the cab.
let snareCut = Kat.fn(x => x
  .eq(e => e
    .band(freq = snareHz, q = 3.0, db = -4)        // notch the snare
  ))

// The Mircophone for the guitar: from rig level to mix level, the last stage of the amp. Was guitarShape.
let ampLevel = Kat.fn(x => x.mul(0.18).parallel(c => c, c => c.shape("soft")))

// Guitar  ----------------------------------------------------------------------------------------------------------------------------------------------------
// The string, the pregain and the envelope, then the pickup: the voice ends there, the amp is the guitarist's cylinder.
let makeGuitar = (pickup) => {

  // --- Overridable params ---------------------------------------------------------------------------------------
  let pVoices     = Ign.slot.voices
  let pSpread     = Ign.slot.spread
  let pAnalog     = Ign.slot.analog

  // Sound knobs
  let pBrightness = Ign.param("brightness", 5500.000, "Pre-distortion lowpass cutoff in Hz")
  
  // ADSR
  let pAttack     = Ign.param("attack",       0.005, "Attack")
  let pDecay      = Ign.param("decay",        1.000, "Decay")
  let pSustain    = Ign.param("sustain",      0.000, "sustain")
  let pRelease    = Ign.param("release",      0.012, "Release")
  // --------------------------------------------------------------------------------------------------------------

  let saw = Ign.supersaw(x => x.voices(pVoices).spread(pSpread)
    // enable the phase-pool for consistent onsets and fundamentals
    .phasePool(on = 1, kMin = 0.65, kMax = 0.95, warmup = 0, selection = "normal")
    // character knobs, plain scalars on the supersaw builder
    .spreadPower(1.0).sideAtten(0.5).gainJitter(0.25).centerJitter(0.15)
    // analog settings
    .analog(pAnalog).analogSpread(1.0)
  )
 
  let signal = saw.mul(Ign.slot.pregain)
    // Pre-distortion: sweeping lowpass adds dynamic character
    .lowpass(Ign.sine(0.50).plus(1).times(1000).plus(pBrightness), 1.50)            
    // Simulate plucked string
    .pitchEnvelope(0.2, x => x.adsr(pAttack.div(5), 0.080, 0, 0))
    //.lowpass(freq = Ign.freq().times(4).add(Ign.constant(5000).adsr(pAttack, 1.0, 0.0, 0.050)), q = 0.7)
    // noise burst
    .plus(Ign.pinknoise().adsr(pAttack, 0.3, 0.05, pRelease).highpass(Ign.perlin(1).mul(200).add(2000)).mul(0.05))
    //.plus(Ign.crackle(1.3).highpass(1200).adsr(0.003, 0.001, 0.0, 0.05).mul(1.5))
    // the string - lowpass adsr for the string sound and adsr for the string
    .adsr(pAttack, pDecay, pSustain, pRelease, e => e.curves("exp", "exp", "exp"))
           
  // the string through the pickup. No note-following highpass after the cab: the preamp tightens the bass at a fixed
  // frequency, and a filter that moves with every note gave every note the same shape, which the ear reads as
  // synthetic (2026-09-14)
  return pickup(signal)
    // sprudel classic
    .classic()
}

// Four rigs, one guitar. The cocoon is clean and dark, the answer is bright, the heartbeat is the clean rig into the big
// box (the 4x12 keeps the thump the 1x12 cuts), and the wings are the Schmetterling's own rhythm rig. A rig is a pickup
// in the voice and the stages of its amp, in signal order. A/B one stage at a time:
//   pickup: pickupStock | pickupSingle  | pickupHumbucker | pickupNeck      (in the voice: makeGuitar)
//   pedal:  pedalStock  | pedalScreamer | pedalFuzz       | pedalBoost
//   preamp: preampStock | preampClean   | preampCrunch    | preampHighGain
//   power:  powerStock  | powerPushPull | powerClassA
//   cab:    cabStock    | cab4x12       | cab1x12         | cabCombo
let cleanStages  = Kat.fn(x => x.serial(pedalStock, preampClean, powerStock, snareCut, cab1x12, ampLevel))
let brightStages = Kat.fn(x => x.serial(pedalBoost, preampCrunch, powerPushPull, snareCut, cab4x12, ampLevel))
let deepStages   = Kat.fn(x => x.serial(pedalStock, preampClean, powerClassA, snareCut, cab4x12, ampLevel))
let heavyStages  = Kat.fn(x => x.serial(pedalScreamer, preampHighGain, powerPushPull, snareCut, cab4x12, ampLevel))

let clean  = makeGuitar(pickupNeck)
let bright = makeGuitar(pickupSingle)
let deep   = makeGuitar(pickupNeck)            // the clean rig's voice: the deep rig differs in its amp, the 4x12
let heavy  = makeGuitar(pickupHumbucker)

// A line's own low cut and top cut, behind its amp. They were the voice's hpf and lpf after the rig per note; on the
// voice now they would filter what goes INTO the amp. Slots, because the parts move them: katp("amp.hp", ...) and
// katp("amp.lp", ...). A line that never cuts has no such stage.
let lowCut = hz => Kat.fn(x => x.highpass(Kat.param("amp.hp", hz)))
let topCut = hz => Kat.fn(x => x.lowpass(Kat.param("amp.lp", hz)))
let noCuts = Kat.fn(x => x)

// One amp: the stages in one 4x region, the trim the rig per note had after the microphone (Kokon's guitarShape ended
// in mul(0.35), ampLevel does not), the line's cuts, then the cylinder's usual strip (body, reverb, compressor, fader),
// so the lines' body, reverb, compressor and katp("gain.gain") still land.
let makeAmp = (stages, cuts) => Katalyst(k => k
  .oversampleAtLeast(4, r => r.serial(stages))
  .mul(0.35)
  .serial(cuts)
  .classic())

// One amp per guitar cylinder. Spin cuts only in some parts and plays open in the others (20 Hz, 20 kHz); beat's top plays open (20 kHz).
let spinAmp   = makeAmp(cleanStages,  Kat.fn(x => x.serial(lowCut(20), topCut(20000))))    // cylinder 1
let singAmp   = makeAmp(brightStages, Kat.fn(x => x.serial(
  lowCut(180),                                     // the 4x12 roar sits on the arp; the lowest note is D4 at 293 Hz
  topCut(4200))))                                  // the crunch fizz on held notes covers the arp's picks; cylinder 2
let beatAmp   = makeAmp(deepStages,   Kat.fn(x => x.serial(lowCut(80), topCut(20000))))    // cylinder 3
let heavyAmp  = makeAmp(heavyStages,  noCuts)                                              // cylinder 4: chug and wings
let strikeAmp = makeAmp(heavyStages,  noCuts)                                              // cylinder 5
let soarAmp   = makeAmp(brightStages, lowCut(240)) // two octaves up, nothing of the melody lives below; cylinder 6
let swellAmp  = makeAmp(brightStages, topCut(6500))                                        // cylinder 7
let strumAmp  = makeAmp(cleanStages,  noCuts)                                              // cylinder 8
let chimeAmp  = makeAmp(cleanStages,  noCuts)                                              // cylinder 10

let metalKick = (() => {
  let thump = Ign.sine()
    .pitchEnvelope(12, x => x.adsr(0.0001, 0.05, 0, 0))
    .adsr(0.0005, 0.25, 0.0, 0.05)
    .distort(0.3, "soft")   // the hard hit: the loud start clips, the tail stays clean
 
  let crack = Ign.whitenoise().bandpass(2800, 0.8).adsr(0.0003, 0.025, 0.0, 0.02).mul(1.8)

  return thump.plus(crack)
    .mul(0.26)                                     // level: as loud as the bd shape it replaced, at the same gain(0.25)
    .shape("soft")
    .classic()
})()

let metalSnare = (() => {
  let crack = Ign.whitenoise().highpass(1500).adsr(0.0002, 0.040, 0.0, 0.003).mul(3.0)
  // the body: the head, pushed a fifth up by the hit, a deeper sine under it, the thud of the stick driving the whole drum
  // (a dense cluster, not a tone, kept above 140 Hz and out of the mud band), and the shell around 800 Hz
  let head  = Ign.sine().pitchEnvelope(7, x => x.adsr(0.0003, 0.015, 0, 0)).adsr(0.0005, 0.15, 0.0, 0.03).mul(3.0)
  let deep  = Ign.sine(Ign.freq().mul(0.75)).adsr(0.0005, 0.080, 0.0, 0.02).mul(2.5)
  // The thud: 13 inharmonic partials from 138 to 712 Hz, a little over two semitones apart, weighted to the spectrum of
  // the pink noise band it replaces (2026-09-30). The noise band was about 230 Hz wide and 50 ms long, so every hit rolled
  // new dice: 6 dB of hit-to-hit loudness, 7.5 dB of peak. The cluster is the same on every hit. The phases of 0.5 are the
  // flipped start phases: all at 0, the partials rise together and the hit spikes 20 dB over its level; this pattern, the
  // calmest of all 8192, leaves 12 dB, less than the noise had. One sine with 13 partials since 2026-10-10: the same
  // sound as 13 sines, about a quarter of the memory per hit.
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
  let wires = Ign.whitenoise().times(Ign.sine().mul(0.8).plus(1.0))   // the buzz: noise that rises and falls with the head
    .bandpass(6000, 0.6).adsr(0.004, 0.11, 0.0, 0.03).mul(1.2)

  let drum = head.plus(deep).plus(thud).plus(shell).plus(m2).plus(m3).plus(wires)

  // The preamp: every part peaks in the same millisecond, so the hit stood 25 dB over the snare's loudness and every
  // master limiter worked on the snare alone. A soft clip rounds that first peak and leaves the body as it was: 6 dB less
  // peak at the same loudness (2026-09-30).
  return drum.plus(crack)
    .mul(0.3).oversampleAtLeast(2, y => y.shape("soft")).mul(1.4895)         // level: as loud as the snare before the clip, at the same gain
    .classic()
})()

// Bass — sub sine + parallel saturated grind, mud band filtered out between them ----------------
let bass = (() => {

  // --- Overridable params ----------------------------------------------------------------------
  let pAnalog  = Ign.slot.analog
  let pSub     = Ign.param("sub",         1.00, "Sub Volume")
  let pHarm    = Ign.param("harmonics",   1.00, "Harmonics Volume")
  // ----------------------------------------------------------------------------------------------

  // Sub: a bare sine. No filter — a sine has no harmonics to remove. This is the weight,
  // and it lives at 36–70 Hz where nothing else in the mix is.
  let sub = Ign.sine(x => x.analog(pAnalog)).mul(pSub)

  // Harmonics: sine partials at 2f .. 6f, gain 1/n, the fundamental left to the sub above. On the
  // low E that is 82 to 328 Hz, the band a small speaker can play and the ear folds back into 41 Hz.
  let harmonics = Ign.sine(x => x.harmonics(8, 0.9).fundamental(0).analog(pAnalog).analogSpread(0.02)).mul(pHarm)

  return sub.plus(harmonics)
    .eq(e => e.band(freq = snareHz, q = 3.0, db = -3)) // let the snare cut through
    .mul(0.2)
    .distort(0.1, "soft")
    .classic()
})()

// Notes  -----------------------------------------------------------------------------------------------------------
// Scale degrees in D minor, 0 is D3. No scale here: the song sets it once. A line moves its notes to its own octave.

// The cocoon: Dm(add9), Bbmaj7(#11), Gm9, Asus. One chord per cycle, four cycles a round.
export cocoonArp    = `<[0 4 7 8 9 8 7 4] [-2 2 4 8 9 8 4 2] [-4 0 2 4 5 4 2 0] [-3 1 4 7 8 7 4 1]>`
export cocoonRoots  = `<0 5 3 4>`
export cocoonPower  = `<[0, 4] [-2, 2] [-4,0] [-3,1]>`
export cocoonPower2 = `<[0,-3] [-2,-5] [-4,0] [-3,1]>`
export cocoonSwell  = `<[4,7,9] [4,8,9] [2,5,0] [1,4,8]>`

// The lift: the break climbs Bb, C, Dm, three cycles, then lands.
export liftArp   = `<[-2 2 4 8 9 8 4 2] [-1 3 6 8 10 8 6 3] [0 4 7 8 9 8 2 4]>`
export liftRoots = `<5 6 0>`
export liftPower = `<[-2,2] [-1,3] [0,4]>`

// The melody, in two phrases of one round each. The second fits the cocoon and the lift alike.
export melodyOne = `<[4@4 3 0     2 [3 7]] [3@4 ~ 4 2 1]     [0@3 4 3@2 2@2]     [1@6 ~@2]>`
export melodyTwo = `<[7@4 6 [0 1] 2 [4 6]] [8@4 ~ 4 2 [4 8]] [9@3 8 7@2 2 [2 3]] [4@6 ~@2]>`

// Out of the cocoon flies the Schmetterling's own lead.
export schmetterlingLead = `<[-7 0 2 4] [-7 0 4 [2 0]] [-5 -1 2 4|[4 4|4|-1]] [-6 -1 4 3|3|[3 3]]>`

// Lines  -----------------------------------------------------------------------------------------------------------
// One guitarist, one way of playing, each with an amp on its cylinder (chug and wings share one). A line's voice gain is how hard
// its amp is hit; its level in the mix is its cylinder's fader (katp "gain.gain"). A part may set either.

// A plucked string drops after the pick, then rings and fades. The clean lines pick a little softer (pregain), so the
// amp squeezes the drop less and each note stands out from the one still ringing.

// The trims: the fader of each line is its old voice gain times its trim, which brings the line near its old level in
// the mix (the parts, the level trim and the compressor changed with the amp on the cylinder). Measured once, each line
// alone, old against new. Rough starting values for the maintainer's fine tuning (2026-11-19).
let spinTrim   = 2.11                              // rough: +6.5 dB; the parts alone asked for +1.5 to +8 dB
let singTrim   = 1.14                              // rough: +1.1 dB
let beatTrim   = 0.86                              // rough: -1.3 dB
let heavyTrim  = 2.19                              // rough: +6.8 dB; chug and wings together, one fader
let strikeTrim = 2.26                              // rough: +7.1 dB
let soarTrim   = 1.32                              // rough: +2.4 dB
let swellTrim  = 2.32                              // rough: +7.3 dB
let strumTrim  = 2.12                              // rough: +6.5 dB
let chimeTrim  = 1.60                              // rough: +4.1 dB

// The guitar cylinders: everything a cylinder does, written once, in the function every line on it applies. The
// compressor is Der Schmetterling's guitar compressor.
let spinBus = x => x.cylinder(1).katalyst(spinAmp)
  .body(wet = 0.1, material = "oak")
  .compressor(threshold = -14.9, ratio = 4, knee = 6, attack = 0.001, release = beats(0.1))
  .katp("gain.gain", 0.17 * spinTrim)

let singBus = x => x.cylinder(2).katalyst(singAmp)
  .body(wet = 0.15, material = "rosewood")
  .compressor(threshold = -14.9, ratio = 4, knee = 6, attack = 0.001, release = beats(0.1))
  .katp("gain.gain", 0.16 * singTrim)

let beatBus = x => x.cylinder(3).katalyst(beatAmp)
  .compressor(threshold = -14.9, ratio = 4, knee = 6, attack = 0.001, release = beats(0.1))
  .katp("gain.gain", 0.25 * beatTrim)

// Chug and wings play through ONE heavy amp, as they share the cylinder, and one fader: 0.55 was the gain of wings,
// 0.40 the gain of chug. Chug keeps that ratio as how hard it hits the amp (0.73 = 0.40 / 0.55).
let heavyBus = x => x.cylinder(4).katalyst(heavyAmp)
  .compressor(threshold = -14.9, ratio = 4, knee = 6, attack = 0.001, release = beats(0.1))
  .katp("gain.gain", 0.38 * heavyTrim)

let strikeBus = x => x.cylinder(5).katalyst(strikeAmp)
  .compressor(threshold = -14.9, ratio = 4, knee = 6, attack = 0.001, release = beats(0.1))
  .katp("gain.gain", 0.35 * strikeTrim)

let soarBus = x => x.cylinder(6).katalyst(soarAmp)
  .compressor(threshold = -14.9, ratio = 4, knee = 6, attack = 0.001, release = beats(0.1))
  .katp("gain.gain", 0.42 * soarTrim)

// The swells' room comes before the compressor in the cylinder's strip, so the compressor holds the room too.
let swellBus = x => x.cylinder(7).katalyst(swellAmp)
  .reverb(wet = 0.2, size = 6)                     // a slight room of their own, inside the hall
  .compressor(threshold = -14.9, ratio = 4, knee = 6, attack = 0.001, release = beats(0.1))
  .katp("gain.gain", 0.05 * swellTrim)

let strumBus = x => x.cylinder(8).katalyst(strumAmp)
  .compressor(threshold = -14.9, ratio = 4, knee = 6, attack = 0.001, release = beats(0.1))
  .katp("gain.gain", 0.18 * strumTrim)

let chimeBus = x => x.cylinder(10).katalyst(chimeAmp)
  .compressor(threshold = -14.9, ratio = 4, knee = 6, attack = 0.001, release = beats(0.1))
  .katp("gain.gain", 0.32 * chimeTrim)

// Spin: the clean arpeggio, each note picked, then ringing under the next.
export spin = notes => n(notes)
  .sound(clean).adsrOff().unison(voices = 11, spread = 0.08).pregain(0.9)
  .ignp("decay", 2.00).ignp("sustain", 0.25).ignp("release", 1.2).clip(2.0)
  .velocity("1.0 0.8 0.9 0.8 0.95 0.8 0.9 0.8".sub(perlin(0.0, 0.05)))
  .gain(0.30)                                      // the clean amp hit softly: notes ring into each other in it
  .pan(0.5)                                        // 0.25 in the parts, before the amp: listen for a drift to the middle
  .vibrato(beatRate(0.25).mul(perlin(0.95, 1.05).slow(4)), perlin(0.05, 0.07))
  .apply(spinBus).tag("spin")

// Sing: the melody on the bright rig, an octave up.
export sing = notes => n(notes.add(7))
  .sound(bright).adsrOff().unison(voices = 11, spread = 0.04) // a narrow chorus: a held note stays one note
  .ignp("decay", 10.0).clip(0.99)
  .tremolo(rate = beatRate(0.33), depth = perlin(0.200, 0.250))  // The guitar finger
  .vibrato(rate = beatRate(0.33), semitones = perlin(0.025, 0.040))
  .gain(1.0)
  .pan(0.6)                                        // a little right of centre, before the amp: listen for a drift to the middle
  .apply(singBus).late(perlin(0.0005, 0.0015)).tag("sing")

// Soar: the melody two octaves up, wider, over the wings.
export soar = notes => n(notes.add(14))
  .sound(bright).adsrOff().unison(voices = 9, spread = 0.06)
  .ignp("decay", 3.0).clip(1.5)
  .pregain(0.6).gain(0.5).pan(0.5)
  .apply(soarBus).tag("soar")

// Swell: volume-knob swells, the thing inside stretching. Doubled on the left, a little late.
export swell = chords => n(chords.add(7))
  .sound(bright).adsrOff().unison(voices = 5, spread = 0.04)
  .ignp("attack", 1.3).ignp("decay", 1.8).clip(1)
  .late(0.030)
  .gain(0.6).pan(0.0).superimpose(x => x.pan(1.0)) // far left and far right, the right a little late
  .apply(swellBus).tag("swell")

// Beat: the heartbeat under the skin, 3-3-2 on the root, an octave down.
export beat = roots => n(roots.add(-7)).struct("x ~ ~ x ~ ~ x ~")
  .sound(deep).adsrOff().unison(voices = 11, spread = 0.06)
  .ignp("decay", 2.0).clip(2)
  .velocity("1.0 0.9 0.85")
  .gain(1.0).pan(0.5)                              // on the left, across from the melody on the right
  .apply(beatBus).tag("beat")

// Chug: the heavy rig, palm-muted on the root, an octave down.
export chug = roots => n(roots.add(-7)).struct("[x x] x@2  [x x] x@2  [x x] x")
  .sound(heavy).adsrOff().unison(voices = 7, spread = 0.07)
  .ignp("attack", perlin(0.004, 0.005).seed(1))
  .ignp("decay", "[5.0 4.8]*8").clip(1)
  .velocity("1.0 0.85 0.90 0.85  0.90 0.85 0.90 0.93".sub(perlin(0.0, 0.2).seed(2)))
  .pregain(0.6).gain(0.8).late(perlin(0.0005, 0.0010).seed(2)).apply(
    x => x.pan(0.50),
    // x => x.pan(0.10),
  ).apply(heavyBus).tag("chug")

// Wings: tremolo-picked power chords on the heavy rig, hard left and right.
export wings = chords => n(chords).ply(16)
  .sound(heavy).adsrOff().unison(voices = 7, spread = 0.07)
  .ignp("attack", perlin(0.004, 0.005).seed(1))
  .ignp("decay", "[5.0 4.8]*8").clip(1)
  .velocity("1.0 0.85 0.90 0.85  0.92 0.85 0.90 0.94".sub(perlin(0.0, 0.2).seed(3)))
  .pregain(0.6).gain(0.8).late(perlin(0.0010, 0.0015).seed(3)).apply(
    x => x.pan(0.95),
    x => x.pan(0.05),
  )
  .apply(heavyBus).tag("wings")

// Strike: one heavy chord, let ring, through a heavy amp of its own.
export strike = chords => n(chords)
  .sound(heavy).adsrOff().unison(voices = 11, spread = 0.08)
  .ignp("decay", 4.5).clip(1)
  .gain(1.0).apply(x => x.pan(0.10),x => x.pan(0.90))
  .apply(strikeBus).tag("strike")

// Chime: a melody on the clean rig, two octaves up, picked and let ring. The butterfly after the storm.
export chime = notes => n(notes.add(14))
  .sound(clean).adsrOff().unison(voices = 7, spread = 0.08).pregain(0.45)
  .ignp("decay", 0.30).ignp("sustain", 0.35).ignp("release", 1.5).clip(2)
  .velocity("1.0 0.8 0.9 0.8 0.95 0.8 0.9 0.8".sub(perlin(0.0, 0.10)))
  .gain(0.5)                                       // the clean amp hit softly: notes ring into each other in it
  .pan(0.75)                                       // before the amp: listen for a drift to the middle
  .late(perlin(0.001, 0.002))
  .tremolo(rate = beatRate(0.166), depth = perlin(0.100, 0.150))     // The guitar finger
  .vibrato(rate = beatRate(0.166), semitones = perlin(0.020, 0.030))
  .apply(chimeBus).tag("chime")

// Strum: the clean rig, one slow strum, let ring.
export strum = notes => n(notes)
  .sound(clean).adsrOff().unison(voices = 7, spread = 0.06).pregain(0.7)
  .ignp("decay", 0.30).ignp("sustain", 0.50).ignp("release", 5.0).clip(40)
  .gain(0.5)                                       // the clean amp hit softly: the strum rings into itself in it
  .pan(0.40)                                       // before the amp: listen for a drift to the middle
  .apply(strumBus).tag("strum")

// The drums, for the second run of the heavy block: the band of Der Schmetterling, heavy and half time. The kick on one,
// the snare on three, the Trommel rolling in on the off-beats of two and four, a crash on one and open hats on two to
// four. The kit shares one cylinder and one room; the Trommel keeps its own for its membrane.
let drumRoom = x => x.reverb(wet = 0.3, size = 5)

export kick = pat => sound("bd").struct(pat)
  .sound(metalKick).adsrOff().n("0").scale(songScale).transpose(-24).velocity("1.0 0.94 0.96 0.94")
  .gain(0.70).pan(0.5)
  .cylinder(11).apply(drumRoom).tag("kick")

export snare = pat => sound(pat)
  .sound(metalSnare).adsrOff().freq(snareHz)
  .gain(0.39).pan(0.575)
  .lpf(freq = "13000".add(saw(0, 1200).slow(12)), q = 0.5)
  .delay(0.33, pure(4/65).div(cps), 0.85, 24) // Snare needs it own cylinder for the dalay!
  .cylinder(12).apply(drumRoom).late(perlin(0.001, 0.0015)).tag("snare")

export hats = pat => sound(pat)
  .velocity("1.0 0.7 0.8 0.7")
  .hpf(800).lpf(freq = 14500, q = 0.5).adsr(0.005, 0.1, 0.70, 2.0) // some body, less sizzle
  .gain(0.70).pan(0.40)                            
  .cylinder(11).apply(drumRoom).late(perlin(0.002, 0.0035)).tag("hats")

// Bass: the Schmetterling's bass guitar, on every kick, on the chord's root two octaves down.
export bassGuitar = (roots, pat) => n(roots.add(-14)).struct(pat)
  .sound(bass).velocity("0.98 0.94 0.96 0.94")
  .ignp("sub", 0.92).ignp("harmonics", 1.00)
  .adsr(0.003, 5.00, 0.0, 0.040).hpf(30)
  .clip(0.90).gain(0.45).cylinder(15).tag("bass")

// Every line carries a tag with its own name (spin, sing, soar, swell, beat, chug, wings, strike, chime, strum, kick,
// snare, hats, bass): the band of the live act reads the song by them and plays what it hears (who plays which is the
// score's, in the live act). A tag changes nothing of the sound.

// Parts  -----------------------------------------------------------------------------------------------------------
// Lines played together. Every part starts on its own first cycle, so a round of the cocoon always starts on Dm.

// The cocoon is spun one thread at a time, over two rounds, in the middle; the answer and the heartbeat will stand to
// either side of it.
let spinning = stack(
  spin(cocoonArp).pan(0.25).unison(voices = 5, spread = 0.03)
    .katp("gain.gain", 0.28 * spinTrim).katp("amp.hp", 120).katp("amp.lp", 3300)
    .mask("<[1 0 0 0 1 0 0 0]!2 [1 0 1 0 1 0 1 0]!2 [1 0 1 1 1 0 1 1]!2 1!2>"),
)

// A second guitar answers.
let answering = stack(
  spin(cocoonArp).pan(0.25).unison(voices = 5, spread = 0.03)
    .katp("gain.gain", 0.28 * spinTrim).katp("amp.hp", 120).katp("amp.lp", 3300)
    .mask("<[1 1 1 0 1 0 0 0]!2 [1 0 1 1 1 0 1 0]!2>"),
  sing(melodyOne).pan(0.75).katp("gain.gain", 0.24 * singTrim).katp("amp.hp", 300),
)

// The heartbeat starts.
let quickening = stack(
  spin(cocoonArp).pan(0.25).unison(voices = 5, spread = 0.03)
    .katp("gain.gain", 0.28 * spinTrim).katp("amp.hp", 120).katp("amp.lp", 3300)
    .mask("<[1 1 1 1 1 0 1 1]!2 1!2>"),
  sing(melodyTwo).pan(0.75).katp("gain.gain", 0.24 * singTrim).katp("amp.hp", 312),
  beat(cocoonRoots).pan(0.5).katp("gain.gain", saw(0.0, 0.22).slow(4).mul(beatTrim)).katp("amp.lp", 1500),
)

// Swells stretch it from inside, the arpeggio grows.
let stretching = stack(
  spin(cocoonArp.ply(2)).pan(0.25).unison(voices = 5, spread = 0.03)
    .katp("gain.gain", "<0.28 0.29 0.30 0.31>".mul(spinTrim)).katp("amp.lp", 3300),
  sing(melodyOne.ply(2)).pan(0.75).katp("gain.gain", "<0.25 0.26 0.27 0.28>".mul(singTrim)).katp("amp.hp", 325),
  beat(cocoonRoots).pan(0.5).katp("gain.gain", 0.18 * beatTrim).katp("amp.lp", 1600),
  swell(cocoonSwell),
)

// Still growing, and in the last half cycle the arpeggio and the heartbeat hold their breath.
let breath = "<1!3 [1 0]>"

let holdingBreath = stack(
  spin(cocoonArp.ply(2)).pan(0.25).unison(voices = 5, spread = 0.03)
    .katp("gain.gain", "<0.30 0.31 0.32 0.33>".mul(spinTrim)).katp("amp.lp", 3300),
  sing(melodyTwo.struct("x!32")).pan(0.75).katp("gain.gain", "<0.22 0.25 0.28 0.31>".mul(singTrim)).katp("amp.hp", 325),
  beat(cocoonRoots).pan(0.5).katp("gain.gain", 0.20 * beatTrim).katp("amp.lp", 1600),
  swell(cocoonSwell),
)

// The high gain rig splits it open: the melody against the wall. The arpeggio waits for its own part.
let breakingOpen = stack(
  soar(melodyOne)
    .tremolo(rate = beatRate(0.25), depth = saw.slow(4).pow(3).mul(0.18).add(0.03))
    .vibrato(rate = beatRate(0.25), semitones = saw.slow(4).pow(3).mul(0.10).add(0.03)),
  wings(cocoonPower),
  chug(cocoonRoots),
  beat(cocoonRoots).pan(0.5).katp("gain.gain", 0.10 * beatTrim).katp("amp.lp", 1600),
)

// The melody steps aside and the cocoon's own thread unravels over the heavy wall: the arpeggio an octave up, leading,
// in the centre between the wings.
let unravelling = stack(
  spin(cocoonArp.add(7)).ply(2).katp("gain.gain", 0.40 * spinTrim).pregain(1.0).gain(0.8).hpf(500, 0.6).superimpose(
    pan(0.25).transpose(12),
    pan(0.75).transpose(12),
  ),
  wings(cocoonPower2),
  chug(cocoonRoots),
  beat(cocoonRoots).pan(0.5).katp("gain.gain", 0.10 * beatTrim).katp("amp.lp", 1600),
)

// The lift: Bb, C, Dm.
let lifting = stack(
  //spin(liftArp.add(14).ply(4)).katp("gain.gain", 0.05 * spinTrim).ignp("sustain", 0.0).clip(0.25).pan(0.25).superimpose(pan(0.75)),
  soar(melodyTwo)
    .tremolo(rate = beatRate(0.25), depth = saw.slow(4).pow(3).mul(0.25).add(0.05))
    .vibrato(rate = beatRate(0.25), semitones = saw.slow(4).pow(3).mul(0.10).add(0.05)),
  wings(liftPower),
  chug(liftRoots),    
  beat(cocoonRoots).pan(0.5).katp("gain.gain", 0.10 * beatTrim).katp("amp.lp", 1600),
)

// It lands on one heavy chord with the low D under it, and the melody holds its A.
let landing = stack(
  spin("[0 4 7 9 11 9 7 4]".add(7)).katp("gain.gain", 0.50 * spinTrim).ignp("sustain", 0.15).clip(0.66),
  soar("[4@6 ~@2]")
    .tremolo(rate = beatRate(0.25), depth = 0.25)
    .vibrato(rate = beatRate(0.25), semitones = 0.30),
  strike("[-7,0,4]").accelerate("0.05".add(perlin(-0.20, 0.20))).ignp("release", 3.5),
  beat("0").katp("gain.gain", 0.10 * beatTrim),
)

// The cocoon again, empty now, and the butterfly flies off.
let flyingOff = stack(
  spin(cocoonArp).pan(0.25),
  chime(schmetterlingLead).pan(0.75),
)

// The last chord is D major: this part brings its own scale, and the first scale on a note wins.
let lastChord = stack(
  strum("<[0 4 7 9 11 ~@27] ~!3>").scale(endScale).ignp("release", 5.0),
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
  [4, kit(cocoonRoots, "<[x@4  ~ ~ x@2] [x@4  ~ ~ x@2] [x x ~ ~  ~ ~ ~ [x x]] [x@2 x ~  ~ ~ [x x] ~]>")],
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
    [2, silence]
  )
    .scale(songScale)
    .analog(feel)

  , sound("pink!4").clip(3).adsr(0.5, 0.5, 0.5, 1.5).gain(perlin(0.005, 0.01))
    .pan(sine(-0.7, 0.7).slow(6)).legato(2)
    .bpf(perlin(1400, 1800).slow(17.sqrt()), 0.7)
    .apply(drumRoom)
 
  , master(Katalyst(k => k.oversampleAtLeast(4, r => r
    .reverb(0.10, 7.5, 7500)     
    .mul(0.25)
    .bands(b => b
      .band(low => low.distort(0.001, "soft").mul(1.00)).cut(150)
      .band(mid => mid.distort(0.100, "soft").mul(0.90)).cut(5100)
      .band(low => low.distort(0.001, "soft").mul(1.08))
    ) // the glue on the mids only: the kick and the hats stay clean
    .mul(4.6)
    .limiter(threshold = -3.0, ratio = 20.0, knee = 2.0, attack = 0.005, release = 0.10, lookahead = 0.005) // the ceiling: peaks only
  )))
)


// Initial foundation written by Claude (Opus 5.5) on the guitar of Der Schmetterling, which the maintainer and Claude built stage by stage.
// Melody, instruments and arrangment refined and fine-tuned by peekandpoke
//
// Inspired by: Philip Glass and Steve Reich, the additive process of minimal music. The spinning arpeggio grows thread
// by thread through its mask, and the listener hears the same notes transform instead of new ones arriving.
// Inspired by: Editors - Papillon, through the Schmetterling's lead that flies off at the end.



    """,
)
