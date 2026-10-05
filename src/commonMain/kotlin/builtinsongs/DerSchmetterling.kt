/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

internal val derSchmetterlingSong = Song(
    id = "${BuiltInSongs.PREFIX}-der-schmetterling",
    title = "Der Schmetterling",
    rpm = 33.0,
    icon = "bug",
    code = """
import * from "stdlib"
import * from "sprudel"

// Song Status: Upcoming Garage Band ...

let feel          =   15    // 0.0 .. guitar | 100.0 .. rave | 200.0 .. hyper
let transposition =   -3    // -2 .. D | 0 .. E | 2 .. F#
let drunk         =    3    // How many beers did each band member have?
let snareHz       =  210    // Where does the snare cut through?

// Guitar rig  ------------------------------------------------------------------------------------------------------------------------------------------------
// string -> pickup -> pedal -> preamp -> tone stack -> power amp -> cab. Every stage is a function of the signal, and every
// stage has a menu of caricatures with 2 to 4 tells each, tuned by ear. The *Stock preset of every stage is the sound
// before that stage existed, so a rig of all-stock presets is the A/B reference. Pick presets one stage at a time, in
// signal order, with the rest held where they are. A preset you cannot name in an A/B gets deleted, not tuned.

// Pickups: the coil's resonance sets the colour, its output sets how hard the amp is pushed, its position on the string
// cancels one harmonic.
let pickupStock = x => x

// Single coil at the bridge: a high, sharp resonance, thin and bright, low output.
let pickupSingle = x => x
  .lowpass(4800, 2.5)                              // the peak, up high and narrow
  .mul(0.8)                                        // low output, the amp stays cleaner

// Humbucker at the bridge: a lower, broader resonance, fat and dark, hot output that pushes the amp harder.
let pickupHumbucker = x => x
  .lowpass(2200, 1.6)                              // the peak, low and wide
  .mul(1.2)                                        // hot output

// Humbucker at the neck: darker still, and the neck position cancels the 4th harmonic (the hollow, woody tell).
let pickupNeck = x => x
  .lowpass(3500, 1.4)
  .notch(freq = Ign.freq().mul(4), q = 2.0)        // the neck sits a quarter along the string
  .mul(1.2)

// Pedals: a box in front of the amp. What it filters BEFORE clipping is the tell, not the clipping itself. The last
// mul is the pedal's level knob, set so the three guitars land within 2 dB of the stock rig (a louder A/B always wins).
let pedalStock = x => x

// Screamer: only the mids go through the clipper, the clean signal goes around it: the mid hump over a clean bass.
let pedalScreamer = x => x
  .plus(x.highpass(720).distort(0.35, "soft", 2).mul(0.6)) // the clipper only ever sees the mids, and the clean bass carries
  .lowpass(3000)                                   // the tone knob, half way
  .mul(0.5)                                        // level

// Fuzz: everything clips, hard and lopsided, splatty.
let pedalFuzz = x => x
  .distort(0.70, "asym", 4)
  .lowpass(4500)
  .mul(0.28)                                       // level

// Treble booster: cuts the bass and pushes the rest into the preamp. The classic bright crunch.
let pedalBoost = x => x
  .highpass(400)
  .mul(1.2)                                        // level

// Preamps: the amp's gain stages. Tells: how many stages, what each coupling cap lets through, and how tight the bass
// is BEFORE it clips. The last mul is the preamp's volume, set so the power amp is pushed about as hard as stock.
let preampStock = x => x
  .distort(0.20, "tube", 4).highpass(100)
  .distort(0.50, "soft", 4).highpass(100)

// Clean: one lightly driven tube stage and a bright cap that lets the top through.
let preampClean = x => x
  .distort(0.12, "tube", 2).highpass(80)
  .eq(e => e.band(freq = 3500, q = 0.7, db = 2.0)) // the bright cap
  .mul(3.5)                                        // volume

// Crunch: two tube stages, coupled loosely so the bass goes along and the clipping stays round.
let preampCrunch = x => x
  .distort(0.30, "tube", 4).highpass(90)
  .distort(0.30, "tube", 4).highpass(90)
  .mul(1.9)                                        // volume

// High gain: tighten the bass BEFORE it clips, three cascaded stages, the last one hard, then tame the fizz.
let preampHighGain = x => x
  .highpass(120)                                   // tight: no bass into the gain stages
  .distort(0.40, "tube", 4).highpass(110)
  .distort(0.55, "softsat", 4).highpass(100)
  .distort(0.65, "hard", 4)
  .lowpass(6200)                                   // the fizz
  .mul(0.25)    

// Power amps: the last saturating stage. Tells: symmetric or not, and the presence bump. The last mul is the master.
let powerStock = x => x.drive(0.3)

// Push-pull (class AB): symmetric clip, odd harmonics, and presence: the top opens up as it works.
let powerPushPull = x => x
  .distort(0.25, "soft", 2)
  .eq(e => e.band(freq = 4000, q = 0.7, db = 2.0)) // presence
  .mul(1.6)                                        // master

// Class A: a lopsided clip, even harmonics, the chime.
let powerClassA = x => x
  .distort(0.25, "asym", 2)
  .mul(1.3)                                        // master

// Cabinets: the linear half of the amp. All of these are static and linear, so they move to the orbit unchanged once
// the Katalyst DSL lands.

// Two plain lowpasses at 5 kHz, the cab the guitar had before the cabs existed.
let cabStock = x => x.lowpass(5000).lowpass(5000)

// 4x12 closed back: the air in the sealed box thumps, the speaker barks in the upper mids, and above 5 kHz there is a wall.
let cab4x12 = x => x
  .eq(e => e
    .band(freq =  120, q = 1.0, db =  3.1)         // thump: closed-back box resonance
    .band(freq =  400, q = 0.5, db =  7.9)         // roar:  low mids
    .band(freq = 2700, q = 1.7, db =  3.8)         // bark:  the upper-mid speaker peak
  )
  .lowpass(5000, 0.707, x => x.passes(2))          // the wall: 36 dB/oct, the fizz is gone
  .highpass(105, 0.707, x => x.passes(2))          // the low end

// 1x12 open back: the open back cancels the bass, the top chimes and rolls off late and soft.
let cab1x12 = x => x
  .highpass(120, 0.707, x => x.passes(2))          // open back: no low end below the box
  .eq(e => e
    .band(freq = 3200, q = 1.0, db = 3.0)          // chime: broad upper presence
  )
  .lowpass(6500, 0.707, x => x.passes(2))          // soft, late roll-off, some air stays

// Small combo: a tiny speaker in a tiny box. No bass, a boxy honk, and the highs die early.
let cabCombo = x => x
  .highpass(160, 0.707, x => x.passes(2))          // small speaker: nothing down low
  .eq(e => e
    .band(freq = 750, q = 1.4, db = 4.0)           // honk: the boxy midrange
  )
  .lowpass(3800, 0.707, x => x.passes(2))          // early roll-off

// Let the snare cut through: a stage of its own, between the power amp and the cab.
let snareCut = x => x
  .eq(e => e
    .band(freq = snareHz, q = 1.5, db = -1)          // notch the snare
  )

// Guitar  ----------------------------------------------------------------------------------------------------------------------------------------------------
// The string, the pregain and the envelope; the rig is everything after the string, in the order the signal takes.
let makeGuitar = (rig) => {

  // --- Overridable params ---------------------------------------------------------------------------------------
  let pVoices     = Ign.slot.voices
  let pSpread     = Ign.slot.spread
  let pAnalog     = Ign.slot.analog

  // ADSR
  let pAttack     = Ign.param("attack",       0.005, "Attack")
  let pDecay      = Ign.param("decay",        1.000, "Decay")
  let pSustain    = Ign.param("sustain",      0.000, "sustain")
  let pRelease    = Ign.param("release",      0.030, "Release")
  // --------------------------------------------------------------------------------------------------------------

  let saw = Ign.supersaw(x => x.voices(pVoices).spread(pSpread)
    // enable the phase-pool for consistent onsets and fundamentals
    .phasePool(on = 1, kMin = 0.70, kMax = 0.99, warmup = 0, selection = "normal")
    // character knobs, plain scalars on the supersaw builder
    .spreadPower(4.0).sideAtten(0.2).gainJitter(0.10).centerJitter(0.10)
    // analog settings
    .analog(pAnalog.adsr(pAttack, pDecay, 0.05, pRelease)).analogSpread(0.5)
  )
 
  let signal = saw.mul(Ign.slot.pregain)
    // Simulate plucked string
    .pitchEnvelope(0.5, x => x.adsr(0.001, 0.07, 0, 0))
    //.lowpass(freq = Ign.freq().times(4).add(Ign.constant(5000).adsr(pAttack, 1.0, 0.0, 0.050)), q = 0.7)
    // noise burst
    .plus(Ign.crackle(1.1).highpass(1200).adsr(0.003, 0.05, 0.0, 0.05).mul(1.0))
    // the string - lowpass adsr for the string sound and adsr for the string
    .adsr(pAttack, pDecay, pSustain, pRelease, e => e.curves("linear", "linear", "linear"))
           
  // the string through the rig. No note-following highpass after the cab: the preamp tightens the bass at a fixed
  // frequency, and a filter that moves with every note gave every note the same shape, which the ear reads as
  // synthetic (2026-09-14)
  return rig(signal)
    .mul(0.15)
    .classic()
}

// The rigs: the signal path after the string, stage by stage. A/B one stage at a time:
//   pickup: pickupStock | pickupSingle  | pickupHumbucker | pickupNeck
//   pedal:  pedalStock  | pedalScreamer | pedalFuzz       | pedalBoost
//   preamp: preampStock | preampClean   | preampCrunch    | preampHighGain
//   power:  powerStock  | powerPushPull | powerClassA
//   cab:    cabStock    | cab4x12       | cab1x12         | cabCombo
// Two guitarists, two rigs: the rhythm rig on guitars 2 and 3 (hard left and right), the melody rig on guitar 1 in the
// centre. On one rig the humbucker, the screamer and the cab all peak near 2.6 kHz; three guitars on it pile up there.
let rhythmRig = x => x.through(pickupHumbucker, pedalScreamer, preampHighGain, powerPushPull, snareCut, cab4x12)
let melodyRig = x => x.through(pickupSingle,    pedalBoost,    preampCrunch,   powerPushPull, snareCut, cab4x12)

let guitar       = makeGuitar(rhythmRig)
let guitarMelody = makeGuitar(melodyRig)

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

  // Harmonics: sine partials at 2f .. 8f, gain 1/n, the fundamental left to the sub above. On the
  // low E that is 82 to 328 Hz, the band a small speaker can play and the ear folds back into 41 Hz.
  let harmonics = Ign.sine(x => x.harmonics(11, 1.0).fundamental(0).analog(pAnalog).analogSpread(0.5)).mul(pHarm)

  return sub.plus(harmonics)
    .eq(e => e.band(freq = snareHz, q = 3.0, db = -2)) // let the snare cut through
    .mul(0.2)
    .classic()
})()

export guitarDyna = "0.98 0.88!7 0.95 0.88!7".sub(perlin.range(0.00, 0.03).slow(2))
export guitarClip = "<0.92!31 0.81 0.92!31 0.80 0.92!30 0.75 0.79>".sub(perlin.range(0.0, 0.02))
  .mul("<0.985!32 [1.1 0.99!7]!32 0.975!32 [1.1 0.99!7]!32>")
export guitarDecay = "<0.425!16 0.440!16 0.425!16 0.440!16>"

// Lead - Inspired by: Editors - Papillon  ---------------------------------------------------------------------------------------------------------------------
export lead_pat =
  `<[-7 0 2 4] [-7 0 4 [2 -1]|[4 2]|[4 6]|2|2|2|2] [-5 -1 2 4] [-6 -1 [4 3]|[5 3]|3|3|3|3 [1 -1]|1|1|1|1]>*2`

// Marimba: a wooden bar with three tuned modes (1 : 4 : 10). The upper two are gone within 100 ms, so the pitch is
// stated at the hit and only the fundamental rings on, longer on low bars. A mallet thump, and the resonator tube
// under the bar in which the thump rings at the fundamental. Its energy sits at 250 to 650 Hz, under the guitar wall.
let marimba = (() => {
  let pAnalog = Ign.slot.analog
  let ring = Ign.constant(400).div(Ign.freq())                                                            // seconds: 1.2 s on e4, 0.6 s on e5
  let f1  = Ign.sine(x => x.analog(pAnalog)).adsr(0.001, ring, 0.0, 0.5)
  let f4  = Ign.sine(Ign.freq().mul(4.05), x => x.analog(pAnalog)).adsr(0.001, 0.12, 0.0, 0.10).mul(0.35)
  let f10 = Ign.sine(Ign.freq().mul(10.075), x => x.analog(pAnalog)).adsr(0.001, 0.05, 0.0, 0.05).mul(0.25)
  let mallet = Ign.pinknoise().adsr(0.0005, 0.010, 0.0, 0.010).lowpass(2500).mul(9.0)                     // yarn head: a thump, not a click                    
 
  return f1.plus(f4).plus(f10).plus(mallet)
    .distort(0.3, "softsat")
    .eq(e => e
      .band(freq = Ign.freq(), q = 30.0, db = 6) // the resonator tube
    )
    .adsr(0.010, 1.0, 0.0, 0.5).mul(0.1)
    .classic()
})()

export lead_shape = x => x.sound(marimba).adsrOff()
  .velocity(guitarDyna).body(material = "steel", wet = 0.2)
  .hpf(600, 0.7).lpf(4500, 1.2)
  .pan(0.33).superimpose(pan(0.66)) // . solo()
  .clip(0.80)

export lead_arrange = x => x.orbit(0) //  .mute()
  .scale("<e5:minor!48 e6:minor!16>").gain("<0.50!48 0.18!16>").gain(mul(0.38))
  .velocity()
  .shuffle("<1!80 1!1 4/8!14 1!33>")
  .mute("<1!64 0!32 1!32 0!16>")
  .late(berlin.range(0.0005, 0.0015).mul(drunk))

export lead = n(lead_pat).apply(lead_shape).tag("lead")

// Guitar 1  --------------------------------------------------------------------------------------------------------------------------------------------------
export guitar1_pat =
  `<[[-7 -7] [[2 3] [4 2] 0] 2 [<4 3 1>!3 -1] [-7 -7 4 3] 0 2 <[-1 1 3@2] [[3 4] 6@2 7] [[1 3] 4 3 2] [[6 10 7 5]]>]!4
    [[4 [4 4 2 0] [4 3 2 0] 0] [-1 [1 [<3 2> 1] -1 -4]] [-3!4 -3!8 4 2 4 0] [2 [2 6@3]]]!2
    [[-3,-7] [[-4,-8] [-1,-4]] [0,-3] <[[4 6],[-2 3]] [0,-1]>] [<[7,4] [[7 4 6 0  7 4 2 0]!2]> [2 0 -1 0] 0 [[-3 -1 0 3] 2]]>/4`

export guitar1_shape = x => x.pregain(guitarDyna.fast(2).mul(0.8)).sound(guitarMelody).adsrOff().unison(voices = 17, spread = 0.08) // . solo()
  .ignp("decay", guitarDecay) //. mute()
  .clip(guitarClip.fast(2)).pan(0.5).body(material = "rosewood", wet = 0.20)

export guitar1_arrange = x => x.orbit(1)  // . solo()
  .scale("<e3:minor!48 e4:minor!16 e3:minor!48 e4:minor!16>").gain(0.220)  // .mute()
  .late(berlin.range(0.0002, 0.0006).mul(drunk).seg(4))

export guitar1 = n(guitar1_pat).struct("<[x!16]!7 [x!24]!1 [x!16]!16>").apply(guitar1_shape).tag("guitar1")

// Guitar 2  --------------------------------------------------------------------------------------------------------------------------------------------------
export guitar2_pat =
  `<[11 11 9 8  7 7 9 6] [11 11 [13 11] 8  7 7 5 6] [11 11 9 11  7 7 7 8]
    [11 11 [13 9] 4  7 4 2 3]
    [4 4 6 8  4 4 5 6] [4 4 6 8  11 11 9 10] [4 4 3 6  4 4 2 3]
    [7 11 [3 7] [6 7] [4 4 6 4]!2 [3 3 0 3] -2]>/4`

export guitar2_shape = x => x.pregain(guitarDyna.fast(2)).sound(guitar).adsrOff().unison(voices = 15, spread = 0.09)
  .ignp("decay", guitarDecay)
  .clip(guitarClip.fast(2)).pan(0.25).body(material = "oak", wet = 0.20) // .superimpose(pan(0.95))

export guitar2_arrange = x => x.orbit(2)  // . solo()
  .scale("<e2:minor>").gain(0.130).mute("<0!128 1!16 0!16>") // .mute()
  .late(berlin.range(0.0002, 0.0006).mul(drunk).seg(4))

export guitar2 = n(guitar2_pat).struct("<[x!16]!7 [x!24]!1 [x!16]!16>").apply(guitar2_shape).tag("guitar2")

// Guitar 3  --------------------------------------------------------------------------------------------------------------------------------------------------
export guitar3_pat =
  `<[0 0 2 4 0 0 -2 -1]!4
    [0 0 2 4 0 0 -2 -1]!2 [0 0 -1 3  0 0 -2 -1]!1 [0 0 3 [0 -1]  0 0 [0 0 -2 0] -2]!1>/4`

export guitar3_shape = x => x.pregain(guitarDyna.fast(2)).sound(guitar).adsrOff().unison(voices = 15, spread = 0.09)
  .ignp("decay", guitarDecay)
  .clip(guitarClip.fast(2)).pan(0.75).body(material = "maple", wet = 0.2) // .superimpose(pan(0.05))

export guitar3_arrange = x => x.orbit(3) //  . solo()
  .scale("<e2:minor>").gain(0.133).mute("<0!128 1!16 0!16>") //.mute()
  .late(berlin.range(0.0000, 0.0004).mul(drunk).seg(4))

export guitar3 = n(guitar3_pat).struct("<[x!16]!7 [x!24]!1 [x!16]!16>").apply(guitar3_shape).tag("guitar3")

// Bass  ------------------------------------------------------------------------------------------------------------------------------------------------------
export bass_pat =
  `<[0 0 2 4 0 0 -2 -1]!3 [0 0 2 4 0 0 5 6]
    [0 0 2 4 0 0 -2 -1]!2 [0 0 -1 3  7 0 -2 -1]!1 [0 0 3 [0 -1]  0 0 [0 2 3 6] 5]!1>/8`

export bass_shape = x => x.velocity("0.98 0.96 0.97 0.96".fast(2)).sound(bass).gain(0.41) // . mute()
    .ignp("sub", 0.90).ignp("harmonics", 1.00)  // . solo()
    .adsr(0.003, 0.3, 0.33, 0.015).hpf(30)

export bass_arrange = x => x.orbit(4) // . mute()
  .scale("e1:minor").notch(freq = snareHz, q = 1.0).mute("<0!128 1!32>")
  .pan(0.5).clip("<[0.80 0.70 0.65 0.70]>*4".sub(perlin.range(0.0, 0.1).slow(8)))
  .late(berlin.range(0.0002, 0.0005).mul(drunk).seg(4))

export bass = n(bass_pat).struct(
  `< x x x [x x]  x x [x x] x    x x x [x x]  x x [x x] x
    [x x] [x [x x]] [x x] [x x]  [[x x] [~ x]] [x x] [[x x] x] [x [x x]]    [x x] [x [x x]] [x x] [x x]  [[x x] [x x?]] [x x] [[x x] x] [x x]
    [x x@2 x]!16
    [x!4]!12 [x!8]!2 [[x x] x!3]!2>`
).fast(2).apply(bass_shape).tag("bass")

// Gummieimer  ------------------------------------------------------------------------------------------------------------------------------------------
// A traditional Rubber-bucket, similar in design to a Orchestertrommel
let gummiEimer = (() => {
  let pAnalog = Ign.slot.analog
 
  let ring = Ign.constant(140).div(Ign.freq()).mul(0.85)  
 
  let head  = Ign.sine(x => x.analog(pAnalog)).pitchEnvelope(15, x => x.adsr(0.010, 0.025, 0, 0)).adsr(0.002, ring, 0.0, 2.0).mul(0.4)
  // the harmonics 2f..8f, fundamental left out: the ear rebuilds it, so the drum sits low in the mix and keeps its pitch,
  // and the pitch drop is heard up here, not felt at 70 Hz. They die well before the head does.
  let harms = Ign.sine(x => x.harmonics(10, 1.1).fundamental(0).analog(pAnalog).analogSpread(1.0))
    .pitchEnvelope(12, x => x.adsr(0.001, 0.05, 0, 0)).adsr(0.002, 0.35, 0.0, 0.40).mul(1.2)
 
  let m2 = Ign.sine(Ign.freq().mul(1.59), x => x.analog(pAnalog)).adsr(0.002, 0.10, 0.0, 0.20).mul(0.60)
  let m3 = Ign.sine(Ign.freq().mul(2.14), x => x.analog(pAnalog)).adsr(0.002, 0.06, 0.0, 0.10).mul(0.25)
  // wood core: a crack, the force of the hit the skin gives, and the hit reads as hard
  let beater = Ign.whitenoise().adsr(0.0005, 0.035, 0.0, 0.015).lowpass(1500).mul(3.00)    
 
  return head.plus(harms).plus(m2).plus(m3).plus(beater)
    .distort(0.40, "softsat", 2)
    .mul(0.3)
    .classic()
})()

// A slow tuned pulse under the band: root, root, root ... then the step the bass takes. 3-3-2 like a march.
export trommel_pat = `<[0 ~ 1 0 ~ ~ 2 ~] [0 ~ 0 -4 -2 ~ -1 ~] [0 ~ -1 0 ~ ~ ~ ~] [0 0 ~ 2 2 ~ 1 ~]>`

export trommel_shape = x => x.sound(gummiEimer).adsrOff() // .solo()
  .velocity("1.0 0.8 0.9 0.8".sub(perlin.range(0.0, 0.02))).body(material = "membrane", wet = 0.4).notch(100, 1.2)
  .hpf(360).lpf("2400".add(perlin.range(-100, 100).slow(16).early(8)))
  .pan(0.25).superimpose(pan(0.75))

export trommel_arrange = x => x.orbit(5) // .solo(0.5)
  .scale("e4:minor").gain(0.07)
  .mute("<1!32 0!32 1!32>")                          
  .late(berlin.range(0.0035, 0.0045).mul(drunk))

export trommel = n(trommel_pat).apply(trommel_shape).tag("trommel")

// Metal kick  ------------------------------------------------------------------------------------------------------------------------------------------------
// Thump and crack, and nothing in between. The "bd" sample rings a partial at 115 Hz, 11 dB under its 68 Hz body, for
// its whole 250 ms: right where the guitars and the bass want to be. This kick is three tells:
//   - the thump: a pure sine, no overtones, so once the sweep is through there is nothing above it but the crack
//   - the punch: a fast three-octave drop that crosses the mud band in a few milliseconds instead of dwelling there
//   - the crack: the beater on the batter head, a short noise tick around 3.5 kHz
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

// Metal snare  -----------------------------------------------------------------------------------------------------------------------------------------------
// Force first, then the crack. The mix is flat from 125 Hz to 4 kHz and 10 to 20 dB quieter above 5 kHz, where the guitar
// cab's wall holds. The "sd" sample had no crack (its wires came in after 15 ms) and its body sat at 159 Hz, on the
// guitars' thump. Measured against the mix at the hits, this snare is 8 to 20 dB on top from 160 to 630 Hz in the first
// 15 ms, and 9 to 23 dB on top above 4 kHz in the first 3 ms. Three tells:
//   - the crack: the stick on the batter head, broadband noise above 1.5 kHz
//   - the body: the head at snareHz (played with .freq), a fifth up at the hit and settling, a deeper sine at 0.75 times
//     it, the thud (a cluster of sines from 140 to 700 Hz) and the shell (noise around 800 Hz), and the two membrane modes at 1.59 and
//     2.14 times the head. A lone sine sat in one third-octave band and sounded thin, a "pick" (2026-09-28); the "sd"
//     sample spread its body over 160 to 250 Hz and had 5 to 10 dB more between 400 Hz and 1 kHz
//   - the wires: noise that buzzes at the head's frequency, because the wires slap the bottom head on every cycle; they
//     swell in over 4 ms, once the head moves
// A sine and clean noise alone is the 80s drum machine snare (too clean, 2026-09-28). No ring after 150 ms: the sample's
// tail was under the mix in every band, level that nobody heard as snare.
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

// Drums  -----------------------------------------------------------------------------------------------------------------------------------------------------
export kick_pat = `<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd  ~ bd  ~]!32 [bd!4]!16 [bd ~ bd [~ bd]]!15 [bd!16]!1>`
export kick_shape = x => x.sound(metalKick).adsrOff().note("b1").gain(0.25).velocity("0.98 0.94 0.96 0.94").pan(0.5)
export kick_arrange = x => x.orbit(6).mute("<0!128 1!32>").late(berlin.range(0.0000, 0.0005).mul(drunk).seg(4)) // .mute()
export kick = sound(kick_pat).apply(kick_shape).tag("kick")  // . solo()

export snare_pat = `<[~!2]!2  [~!4]!2  [~!8]!2  [~!16]  [~!24]  [~  sd  ~ sd]!15 [[~ sd] sd  [[sd? sd?] sd] [sd!4]] [~  sd  ~ sd]!16 [~ sd ~ sd]!32>`
export snare_shape = x => x.sound(metalSnare).adsrOff().freq(snareHz).gain(0.13).pan(0.575)
export snare_arrange = x => x.orbit(7).mute("<0!128 1!32>").late(berlin.range(0.0010, 0.0015).mul(drunk).seg(4))
export snare = sound(snare_pat).apply(snare_shape).tag("snare") // .solo()

export hats_pat = `<[hh hh hh hh]!16 [hh hh oh hh]!24 [cr hh cr hh]!24 [~ rd ~ rd]!32>`
export hats_shape = x => x.gain(0.20).pan(0.425)
  .hpf(1200).lpf(freq = "13000".add(perlin.mul(50).fast(4)), q = 0.5).adsr(perlin.range(0.001, 0.003), 0.1, 0.70, 2.0)
export hats_arrange = x => x.orbit(8).mute("<0!128 1!32>").late(berlin.range(0.0015, 0.0025).mul(drunk).seg(4))
export hats = sound(hats_pat).fast(2).apply(hats_shape).velocity("<1.0 0.85 0.93 0.85>*4".sub(berlin.range(0.0, 0.05).slow(4))).tag("hats")

export shaker_pat = `<pink ~ pink ~ pink ~ pink ~ pink>*8`
export shaker_shape = x => x.gain(0.09).velocity("<1.0 0.90 0.95 0.90>*16") //  . mute()
  .hpf(freq = 6000, q = 0.7) //  . solo()
  .pan(0.35).adsr(0.010, 0.10, 0.0, 0.01)
export shaker_arrange = x => x.orbit(10).late(berlin.range(0.0010, 0.0020).mul(drunk))
export shaker = sound(shaker_pat).apply(shaker_shape).tag("shaker")

// Count-in  --------------------------------------------------------------------------------------------------------------------------------------------------
export countin = sound("oh!2").apply(hats_shape).velocity(0.5).tag("countin").reverb(wet = "0.1", size = 0)
export countin_arrange = x => x.orbit(0).filterWhen(t => t < 2)

// Song  ------------------------------------------------------------------------------------------------------------------------------------------------------
export song_arrange = x => x.late(2).filterWhen(t => t >= 2) // shift the song one cycle to make room for the count-in

export song_body = stack(
  stack(
    // Lead - Inspired by: Editors - Papillon    
    lead.apply(lead_arrange).reverb(wet = 0.10, size = 1.0) // . mute() // .solo()
    // Guitars    
    , stack(
      // Guitar 1
      guitar1.apply(guitar1_arrange) // .solo() .mute()
      , // Guitar 2
      guitar2.apply(guitar2_arrange) // .solo() .mute()
      , // Guitar 3
      guitar3.apply(guitar3_arrange) // .solo() .mute()
    ).reverb(wet = 0.15, size = 3.0) // . solo()
    , // Bass
    bass.apply(bass_arrange) // .solo() // .mute()
    , // Orchestertrommel
    trommel.apply(trommel_arrange).reverb(wet = 0.20, size = 6.0) // .solo() .mute()
  ).analog(feel).transpose(transposition)
  , // Drums
  stack(
    kick.apply(kick_arrange),     // .solo() .mute()
    snare.apply(snare_arrange),   // .solo() .mute()
    hats.apply(hats_arrange),     // .solo() .mute()
    shaker.apply(shaker_arrange)  // .solo() .mute()
  ).analog(feel / 2).reverb(wet = 0.30, size = 4.0) //. solo() //  .mute()
).seed(timeOfDay.mul(60*60*60*24)).shuffle("<1!80 2!48 1!112 2!32>")

export song = stack(
  // Count-in: four open hats at half the song's hat speed
  countin.apply(countin_arrange)
  , // Song body
  song_body.apply(song_arrange)
  , // Master: the hall, the house level (-14 LUFS), and a ceiling that only trims the peaks. No glue compressor: it
    // pulled the groove towards the break (6.3 LU apart without it, 4.4 at 1.4 dB of glue). The ceiling sits at -3 dB
    // because the snare's crack overshoots between the samples: true peak -1.2 dBFS, and the house limiter stays idle.
  master(Katalyst(k => k
    .reverb(0.2, 5, 7000)
    .gain(4.4)
    .limiter(threshold = -3.0, ratio = 20.0, knee = 2.0, attack = 0.005, release = 0.10, lookahead = 0.005)
  ))
)









    """,
)
