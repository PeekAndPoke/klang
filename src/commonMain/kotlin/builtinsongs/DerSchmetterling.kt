/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

internal val derSchmetterlingSong = Song(
    id = "${BuiltInSongs.PREFIX}-der-schmetterling",
    title = "Der Schmetterling",
    rpm = 36.0,
    icon = "bug",
    code = """import * from "stdlib"
import * from "sprudel"

let feel = 15.0    // 0.0 .. guitar | 100.0 .. rave

let supersawHp = (() => {

  // --- overridable slots -------------------------------------------------
  let pVoices  = OscSlot.voices
  let pSpread  = OscSlot.spread
  let pAnalog  = OscSlot.analog

  // --- pitch-tracking highpass -------------------------------------------
  let pPresenceHz = Osc.param("presenceHz", 2500.0,   "Presence frequency")
  let pPresence   = Osc.param("presence",      4.2,   "Presence amount")
  let pHpTrack    = Osc.param("hptrack",       1.4,   "Highpass cutoff as a multiple of the note frequency")
  let pHpQ        = Osc.param("hpq",           0.507, "Highpass resonance")

  let signal = Osc.supersaw(freq = Osc.freq(), voices = pVoices, spread = pSpread)
    // enable the phase-pool for consistent onsets and fundamentals
    .phasePool(on = 1, kMin = 0.65, kMax = 0.90)
    // character knobs — plain scalars, SuperSaw-typed, must precede the filter
    .analog(pAnalog).spreadPower(1.2).sideAtten(0.2).gainJitter(0.20).centerJitter(0.20)
    // Simulate plucked string, add noise burst
    .pitchEnvelope(0.2, 0.001, 0.03)
    .plus(Osc.whitenoise().highpass(2000).adsr(0.001, 0.03, 0.0, 0.005).mul(0.14))
    // Distort
    .distort(0.3, "hard", 4)
   
  return signal
    //.add(signal.bandpass(800, 0.500).mul(1.0))           // mids
    .add(signal.bandpass(pPresenceHz, 0.5).mul(pPresence)) // presence
    .lowpass(5800).lowpass(5800)                           // Double lowpass to remove the fizz in the high freqs
    // Follow the frequency to avoid low mud
    .highpass(Osc.freq().mul(pHpTrack), pHpQ)
})()
                                                                                                                       
stack(    
  stack(
    // Lead                                                                                                              
    n(`<[-7 0 2 4] [-7 0 4 [2 6]|[4 2]|2|2|2] [-5 -1 2 4] [-6 -1 [4 3]|[5 3]|3|3|3 [1 -1]|1|1|1|1]>*2`)
      .orbit(0).scale("<e4:minor!48 e5:minor!16 e4:minor!48 e3:minor!16>")
      .gain(0.40).postgain("<0.210!48 0.080!16 0.210!48 0.250!16>")  // . solo()                  
      .sound(supersawHp).unison(21).spread(0.08).oscp("presenceHz", 2900)    
      .lpf(5200).lpe(perlin.range(0.5, 0.6).fast(2)).lpq(1.5).lpadsr("0.000:0.05:0.2:0.03")                      
      .adsr("0.010:4.0:0.3:0.02").clip(0.95).release("<0.05!16 0.075!16>") // . mute()            
      .shuffle("<1!64 0!16 1!1 4/8!14 1!33>")                                                                            
      .superimpose(x => x.mute("<1!16 0!16>").transpose(12).spread(0.12).velocity(0.2).pan(0.1).superimpose(pan(0.9)))
      .mute("<1!64 0!32 1!32 0!16>").room("0.3:5:0.1").body("maple").bodyMix(0.2).compressor("-15:2:6:0.01:0.2")                                                                            
    , // Guitar 1                                                                                                        
    n(`<[0 [0@4 [4 7] -3] -1 <1 -3> [0 2 4 3] 0 2 <[-1 1 3@2] [[3 4] 6@2 7] [[1 3] 4 3 2] [[6 10 7 5]]>]!4
        [[4 [4 4 2 0] [4 3 2 0] 0] [-1 [1 2 -1 -4]] [-3!4 -3!8 4 2 4 0] [2 [2 6@3]]]!2
        [[-3,-7] [[-4,-8] [-1,-4]] [0,-3] <[[4 3],[-2 -1]] [0,-1]>] [<[7,4] [[7 4 6 0  7 4 2 0]!2]> [-8 -6] [-7] [-2 <-1 -4 2 1>]]>/4`)
      .orbit(1).scale("<e3:minor!48 e4:minor!16 e3:minor!48 e4:minor!16>").struct("<[x!16]!7 [x!24]!1 [x!16]!16>") //  .mute()
      .gain(0.5).postgain(0.145).velocity("0.98 0.95!7 0.97 0.95!7".fast(2))  // . solo()
      .sound(supersawHp).unison(15).spread(0.07).oscp("presenceHz", 2700)  
      .clip("<0.95!31 0.85 0.95!31 0.87 0.95!30 0.85 0.89>".fast(2)).adsr("0.007:4.5:0.0:0.021")    
      //.hpf(800).lpf(5500).lpe(0.5).lpq(0.707)
      .pan(0.15).superimpose(pan(0.85)).body("violin").bodyMix(0.5).compressor("-15:2:6:0.01:0.2")
    , // Guitar 2
    n(`<[11 11 9 8  7 7 9 6] [11 11 [13 11] 8   7 7 5 6] [11 11 9 8  7 7 7 6] [11 11 [13 11] 8  7  4        2         3]
        [ 4  4 6 8  4 4 5 6] [ 4  4 6       8  11 7 9 8] [ 4  4 3 6  4 4 2 3] [ 7 11 9       8  [4 4 6 4]!2 [4 2 6 2] 3]>/4`)
      .orbit(2).scale("<e2:minor>").struct("<[x!16]!7 [x!24]!1 [x!16]!16>") // . mute()
      .gain(0.5).postgain(0.13).velocity("0.98 0.95!7 0.97 0.95!7".fast(2)) // . solo()
      .sound(supersawHp).unison(11).spread(0.09).oscp("presenceHz", 2450)
      .clip("<0.95!31 0.85 0.95!31 0.90 0.95!30 0.85 0.89>".fast(2)).adsr("0.005:4.5:0.0:0.020")  
      // .hpf(400).lpf(5200).lpe(0.5).lpq(0.707)
      .pan(0.25).superimpose(pan(0.75)).mute("<0!128 1!16 0!16>")
      .coarse(2).body("cedar").bodyMix(0.5)
    , // Guitar 3
    n(`<[0 0 2 4 0 0 -2 -1]!4
        [0 0 2 4 0 0 -2 -1]!2 [0 0 -1 3  0 0 -2 -1]!1 [0 0 2 4 0  0 -2 -1]!1>/4`)  //  . solo()
      .orbit(2).scale("<e2:minor>").struct("<[x!16]!7 [x!24]!1 [x!16]!16>") // . mute()
      .gain(0.5).postgain(0.135).velocity("0.98 0.95!7 0.97 0.95!7".fast(2))
      .sound(supersawHp).unison(11).spread(0.10).oscp("presenceHz", 2300)
      .clip("<0.95!31 0.85 0.95!31 0.93 0.95!30 0.85 0.89>".fast(2)).adsr("0.003:4.5:0.0:0.019")    
      // .hpf(100).lpf(5000).lpe(0.5).lpq(0.707)
      .pan(0.70).superimpose(pan(0.30)).compressor("-15:2:6:0.01:0.2")
      .mute("<0!128 1!16 0!16>")
    , // Bass
    n(`<[0 0 2 4 0 0 -2 -1]!4
        [0 0 2 4 0 0 -2 -1]!2 [0 0 -1 3  0 0 -2 -1]!1 [0 0 2 4 0  0 -2 -1]!1>/8`).struct("<[x!1]!16 [x@3 x]!48 [x!4]!80>").fast(2).velocity("0.98 0.94 0.96 0.94".fast(2))  // . mute()
      .orbit(3).scale("e1:minor").sound("sine").gain(1.0).distort("0.10:gentle:2").postgain(0.055).clip(0.80)
      .adsr("0.003:4.0:0.5:0.010").lpadsr("0.000:0.03:0.0:0.01").hpf(25).hpq(0.5).lpf(500).lpe(16).lpq(0.707)  //  .solo()
      .superimpose(transpose(12).hpf(60).postgain(0.02))
      .pan(0.35).superimpose(pan(0.75)).mute("<0!128 1!32>").compressor("-15:2:6:0.01:0.2")
  ).transpose(-2)
  , // Drums
  stack(  
    sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd  ~ bd  ~]!32 [bd!4]!16 [bd ~ bd [~ bd]]!15 [bd!]!1>").mute("<0!128 1!32>")  // . solo()
      .pan(0.5).orbit(5).gain(0.19).hpf(25).lpf(13000).adsr("0.000:0.05:0.95:0.2").distort(0.1),
    sound("<[~!2]!2  [~!4]!2  [~!8]!2  [~!16]  [~!24]  [~  sd  ~ sd]!32 [~ sd ~ sd]!32>").mute("<0!128 1!32>") // . solo()
      .pan(0.5).late(0.0015).orbit(5).pan(0.45).gain(0.155).hpf(80).lpf(15000).lpq(0.5).adsr("0.000:0.05:0.95:0.2")
      .superimpose(x => x.bandf("205".add(berlin.mul(20).fast(4))).bandq(4).vel(0.70).hpf(180).lpf(450)),
    sound("<[hh hh hh hh]!16 [hh hh oh hh]!24 [cr hh cr hh]!24 [~ rd ~ rd]!32>").fast(2).mute("<0!128 1!32>") // . solo()
      .pan(0.55).late(0.0030).orbit(5).gain(0.21).hpf(800).lpf("14500".add(perlin.mul(500).fast(4))).lpq(0.5).adsr("0.004:0.05:0.95:0.5"), // . mute()
    sound("<~!79 [~ ~ ~ cp  cp ~ cp ~] ~!47 [~ ~ ~ cp  cp ~ cp ~]>").orbit(5).gain(0.085).mute("<0!128 1!32>"),
    sound("<pink ~ pink pink>*16").orbit(5).gain(0.04).hpf(5000).lpf(perlin.range(15000, 16000)).lpq(1.2)
      .pan(sine.range(0.4, 0.6).slow(11)).adsr("0.002:0.12:0.0:0.01") //  .solo()
  ).compressor("-15:2:6:0.01:0.2")
  // Master
  ,master(Master.of(MasterFx.reverb().wet(0.03).damp(0.7).roomSize(7), MasterFx.gain(2.2)))
).analog(feel).seed(timeOfDay.mul(10*60*60*24)).shuffle("<1!80 2!48 1!128 2!32>").early(0)



// Inspired by: Editors - Papillon
// https://open.spotify.com/intl-de/track/7hYiX6LMP8w8d0kEc4KWuW




// Written by: peekandpoke

// Epilepsy Warning: Do not click the oscilloscope!














    """,
)
