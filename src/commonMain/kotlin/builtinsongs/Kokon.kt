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
// Guitars only, and only one guitar: the instrument of Der Schmetterling, its rig stages and its makeGuitar, played
// through four rigs picked from its menu. A clean arpeggio is spun one thread at a time, a second guitar answers, a
// palm-muted heartbeat starts under the skin, volume swells stretch it from inside, it holds its breath, and the high
// gain rig splits it open. What flies out at the end is the Schmetterling's own lead, and the last chord is major.
//
// Form, in cycles of 3 s: intro 8 | answer 8 | build 8 | break 8 | coda 4 | last chord 2  = 38

let feel  = 20   // analog drift of the guitars, as in Der Schmetterling
let drunk =  1   // two guitarists, sober this time, mostly

// Rig stages (from Der Schmetterling)  ------------------------------------------------------------
let pickupNeck = x => x
  .lowpass(3500, 1.4)
  .notch(freq = Osc.freq().mul(4), q = 2.0)
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
  .highpass(120)
  .distort(0.45, "tube", 4).highpass(100)
  .distort(0.50, "softsat", 4).highpass(100)
  .distort(0.50, "soft", 4)
  .lowpass(6800)
  .mul(0.25)

let powerPushPull = x => x
  .distort(0.25, "soft", 2)
  .eq(e => e.band(freq = 4000, q = 0.7, db = 2.0))
  .mul(1.6)

let powerClassA = x => x
  .distort(0.25, "asym", 2)
  .mul(1.3)

let cab4x12 = x => x
  .eq(e => e
    .band(freq =  120, q = 1.2, db =  3.0)
    .band(freq =  380, q = 0.6, db =  7.0)
    .band(freq = 2700, q = 1.7, db =  4.0)
    .band(freq =  100, q = 0.7, db = -3.0)
  )
  .lowpass(5000, 0.707, x => x.passes(2))

let cab1x12 = x => x
  .highpass(120, 0.707, x => x.passes(2))
  .eq(e => e.band(freq = 3200, q = 1.0, db = 3.0))
  .lowpass(6500, 0.707, x => x.passes(2))

// The guitar (from Der Schmetterling)  -----------------------------------------------------------
let makeGuitar = (pickup, pedal, preamp, power, cab) => {
  let pVoices     = OscSlot.voices
  let pSpread     = OscSlot.spread
  let pAnalog     = OscSlot.analog
  let pAttack     = Osc.param("attack",       0.005, "Attack")
  let pDecay      = Osc.param("decay",        1.000, "Decay")
  let pSustain    = Osc.param("sustain",      0.000, "sustain")
  let pRelease    = Osc.param("release",      0.030, "Release")

  let saw = Osc.supersaw(x => x.voices(pVoices).spread(pSpread)
    .phasePool(on = 1, kMin = 0.60, kMax = 0.85, warmup = 0, selection = "normal")
    .spreadPower(6.0).sideAtten(0.3).gainJitter(0.05).centerJitter(0.20)
    .analog(pAnalog).analogSpread(0.5)
  )

  let signal = saw.mul(Osc.slot.pregain)
    .pitchEnvelope(0.5, x => x.adsr(0.001, 0.05, 0, 0))
    .plus(Osc.crackle(1.25).highpass(1200).adsr(0.001, 0.1, 0.0, 0.05).mul(1.5))
    .adsr(pAttack, pDecay, pSustain, pRelease, e => e.curves("linear", "linear", "linear"))

  return cab(power(preamp(pedal(pickup(signal))))).mul(0.14).classic()
}

// Four rigs, one guitar. The cocoon is clean and dark, the answer is bright, the heartbeat is the clean rig into the big
// box (the 4x12 keeps the thump the 1x12 cuts), and the wings are the Schmetterling's own rhythm rig.
let clean  = makeGuitar(pickupNeck,      pedalStock,    preampClean,    powerClassA,   cab1x12)
let bright = makeGuitar(pickupSingle,    pedalBoost,    preampCrunch,   powerPushPull, cab4x12)
let muted  = makeGuitar(pickupNeck,      pedalStock,    preampClean,    powerClassA,   cab4x12)
let heavy  = makeGuitar(pickupHumbucker, pedalScreamer, preampHighGain, powerPushPull, cab4x12)

// Arp: the cocoon. Dm(add9), Bbmaj7(#11), Gm9, Asus. It is spun one thread at a time  -------------
export arp_pat = `<[0 4 7 8 9 8 7 4] [-2 2 4 8 9 8 4 2] [-4 0 2 4 5 4 2 0] [-3 1 4 7 8 7 4 1]>`

export arp = n(arp_pat).scale("d3:minor")
  .mask("<[1 0 0 0 1 0 0 0]!2 [1 0 1 0 1 0 1 0]!2 [1 0 1 1 1 0 1 1]!2 1!17 [1 1 1 1 0 0 0 0] 1!12 0!2>")
  .ply("<1!20 2!12 1!6>")
  .sound(clean).adsrOff().unison(voices = 7, spread = 0.06)
  .oscp("decay", 2.5).clip(4)
  .velocity("1.0 0.8 0.9 0.8 0.95 0.8 0.9 0.8")
  .gain("<0.25!8 0.21!8 0.20 0.21 0.22 0.23 0.24 0.25 0.26 0.27 0.16!8 0.22!6>").pan(0.4)
  .mute("<0!28 1!4 0!6>")
  .orbit(1).reverb(wet = 0.2, size = 4)

// The lift: the second half of the break climbs Bb, C, Dm and stays there
export arpLift = n(`<[-2 2 4 8 9 8 4 2] [-1 3 6 8 10 8 6 3] [0 4 7 8 9 8 7 4] [0 4 7 9 11 9 7 4]>`).scale("d3:minor")
  .ply(2)
  .sound(clean).adsrOff().unison(voices = 7, spread = 0.06)
  .oscp("decay", 2.5).clip(4)
  .velocity("1.0 0.8 0.9 0.8 0.95 0.8 0.9 0.8")
  .gain(0.16).pan(0.4)
  .mute("<1!28 0!4 1!6>")
  .orbit(1).reverb(wet = 0.2, size = 4)

// Answer: a slow melody on the bright rig  --------------------------------------------------------
export answer_pat = `<
  [4@4 3 2 1 2] [1@4 ~ 0 1 2] [4@3 5 4@2 2 0] [1@6 ~@2]
  [7@4 6 4 3 4] [8@4 ~ 7 8 9] [9@3 8 7@2 5 4] [4@6 ~@2]
>`

export answer = n(answer_pat).scale("d4:minor")
  .sound(bright).adsrOff().unison(voices = 11, spread = 0.08)
  .oscp("decay", 3.0).clip(1.5)
  .gain("<0.14!16 0.12!8 0.14!14>").pan(0.65)
  .mute("<1!8 0!16 1!14>")
  .orbit(2).reverb(wet = 0.3, size = 5)

// Swell: volume-knob swells, the thing inside stretching  ----------------------------------------
export swell_pat = `<[4,7,9] [4,8,9] [2,5,7] [1,4,8]>`

export swell = n(swell_pat).scale("d4:minor")
  .sound(bright).adsrOff().unison(voices = 11, spread = 0.12)
  .oscp("attack", 1.6).oscp("decay", 2.0).clip(1)
  .gain(0.06).pan(0.3).superimpose(x => x.pan(0.7).late(0.02))
  .mute("<1!16 0!8 1!14>")
  .orbit(7).reverb(wet = 0.5, size = 6)

// Pulse: palm mutes, 3-3-2, the heartbeat under the skin  ---------------------------------------
// One root per cycle, the whole song: Dm Bb Gm A, and in the lift Bb C Dm Dm
export roots = `<0 5 3 4  0 5 3 4  0 5 3 4  0 5 3 4  0 5 3 4  0 5 3 4  0 5 3 4  5 6 0 0  0 5 3 4  0 0>`

export pulseSoft = n(roots).struct("x ~ ~ x ~ ~ x ~").scale("d2:minor")
  .sound(muted).adsrOff().unison(voices = 7, spread = 0.06)
  .oscp("decay", 0.12).clip(1)
  .velocity("1.0 0.8 0.9")
  .gain("<0.45!24 0.8!8 0.45!6>").pan(0.5)
  .mute("<1!12 0!11 [0 1] 0!8 1!6>")
  .orbit(3)

export pulseHeavy = n(roots).struct("<[x x ~ x x ~ x x]!31 [x ~!7] [x x ~ x x ~ x x]!6>").scale("d2:minor")
  .sound(heavy).adsrOff().unison(voices = 19, spread = 0.10)
  .oscp("decay", "<0.15!31 3.0 0.15!6>").clip("<1!31 8 1!6>")
  .velocity("1.0 0.75 0.9 0.75 0.95 0.75")
  .gain(1.2).pan(0.5)
  .mute("<1!24 0!8 1!6>")
  .orbit(4)

// Wings: the break, the cocoon splits. Tremolo-picked power chords, hard left and right  -------
export wings_pat = `<[0,4] [-2,2] [-4,0] [-3,1] [-2,2] [-1,3] [0,4] [0,4]>`

export wings = n(wings_pat).struct("<[x!16]!7 [x ~!15]>").scale("d3:minor")
  .sound(heavy).adsrOff().unison(voices = 11, spread = 0.10)
  .oscp("decay", "<0.4!7 3.5>").clip("<1!7 16>")
  .velocity("1.0 0.85 0.9 0.85")
  .gain(0.8)
  .pan(0.1).superimpose(x => x.pan(0.9).late(0.004))
  .mute("<1!24 0!8 1!6>")
  .orbit(5).reverb(wet = 0.15, size = 3)

// Flight: the answer melody an octave up, over the wings  ----------------------------------------
export flight = n(answer_pat).scale("d5:minor")
  .sound(bright).adsrOff().unison(voices = 15, spread = 0.10)
  .oscp("decay", 3.0).clip(1.5)
  .gain(0.50).pan(0.5)
  .mute("<1!24 0!8 1!6>")
  .orbit(6).reverb(wet = 0.25, size = 5)

// Motif: out of the cocoon flies the Schmetterling's own lead  -----------------------------------
export motif = n(`<[-7 0 2 4] [-7 0 4 2] [-5 -1 2 4] [-6 -1 4 3]>`).scale("d5:minor")
  .sound(bright).adsrOff().unison(voices = 11, spread = 0.08)
  .oscp("decay", 2.0).clip(2)
  .gain(0.26).pan(0.6)
  .mute("<1!32 0!4 1!2>")
  .orbit(2).reverb(wet = 0.3, size = 5)

// Strum: the last chord is D major. It rings  ---------------------------------------------------
export strum = n("<[0 4 7 9 11 ~@27] ~>").scale("d3:major")
  .sound(clean).adsrOff().unison(voices = 7, spread = 0.06)
  .oscp("decay", 6.0).clip(60)
  .gain(0.3).pan(0.45)
  .mute("<1!36 0!2>")
  .orbit(8).reverb(wet = 0.3, size = 6)

// Song  ------------------------------------------------------------------------------------------------
export song = stack(
  stack(arp, arpLift, answer, swell, pulseSoft, pulseHeavy, wings, flight, motif, strum)
    .analog(feel)
    .late(berlin.range(0.0, 0.002).mul(drunk).seg(8)),
  master(Katalyst(k => k
    .gain(1.6).limiter(threshold = -8.0, ratio = 2.0, attack = 0.015, release = 0.25)
    .gain(1.5).limiter(threshold = -4.0, ratio = 4.0, attack = 0.008, release = 0.15)
    .gain(1.3)
  ))
)

// Written by Claude (Opus 5.5) on the guitar of Der Schmetterling, which the maintainer and Claude built stage by stage.
    """,
)
