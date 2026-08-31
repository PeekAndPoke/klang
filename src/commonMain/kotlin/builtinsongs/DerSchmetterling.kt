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
    rpm = 32.5,
    icon = "bug",
    code = """import * from "stdlib"
import * from "sprudel"

// Song Status: Upcoming Garage Band ...

let feel          =   20    // 0.0 .. guitar | 100.0 .. rave | 200.0 .. hyper
let transposition =   -2    // -2 .. D | 0 .. E | 2 .. F#
let drunk         =    1    // How many beers did each band member have?
let snareHz       =  210    // Where does the snare cut through?
let leadHz        = 3200    // Where does the lead sit?

let guitar = (() => {

  // --- Overridable params ---------------------------------------------------------------------------------------
  let pVoices  = OscSlot.voices
  let pSpread  = OscSlot.spread
  let pAnalog  = OscSlot.analog

  // ADSR
  let pAttack     = Osc.param("attack",        0.005, "Attack")
  let pDecay      = Osc.param("decay",         0.900, "Decay")
  let pSustain    = Osc.param("sustain",       0.100, "sustain")
  let pRelease    = Osc.param("release",       0.028, "Release")

  // amp tone
  let pMidsHz     = Osc.param("midsHz",      850.000, "Mids frequency")
  let pMidsQ      = Osc.param("midsQ",         0.707, "Mids Q")
  let pMids       = Osc.param("mids",          2.500, "Mids Volume")

  let pPresenceHz = Osc.param("presenceHz", 2500.000, "Presence frequency")
  let pPresenceQ  = Osc.param("presenceQ",     0.707, "Presence Q")
  let pPresence   = Osc.param("presence",      2.500, "Presence Volume")

  // Low mud filter
  let pHpTrack    = Osc.param("hptrack",       1.000, "Highpass cutoff as a multiple of the note frequency")
  let pHpQ        = Osc.param("hpq",           0.707, "Highpass resonance")
  // --------------------------------------------------------------------------------------------------------------
 
  let signal = Osc.supersaw(freq = Osc.freq(), voices = 17, spread = 0.11)
    // enable the phase-pool for consistent onsets and fundamentals
    .phasePool(on = 1, kMin = 0.60, kMax = 0.85, warmup = 0, selection = "normal")
    // character knobs — plain scalars, SuperSaw-typed, must precede the filter
    .analog(pAnalog).spreadPower(6.0).sideAtten(0.0).gainJitter(0.25).centerJitter(0.20)
    // Simulate plucked string
    .pitchEnvelope(0.5, 0.001, 0.005)
    // noise burst
    .plus(Osc.whitenoise().highpass(5000).adsr(pAttack, 0.05, 0.0, 0.005).mul(0.25))
    // the string - lowpass adsr for the string sound and adsr for the string
    .adsr(pAttack, pDecay, pSustain, pRelease).adsrCurves("exp", "exp", "linear")
    // follow freq to avoid low mud ... again
    .highpass(freq = Osc.freq().mul(pHpTrack), q = pHpQ)    
             
  // the amp
  let amped = signal  
    // pre amp
    .distort(0.50, "tube", 4).highpass(40).mul(0.7)
    // drive amp
    .distort(0.60, "soft", 4).highpass(40).mul(0.7)
    .eq()
      .band(freq = pMidsHz,     q = pMidsQ,     db = pMids)      // mids: parallel boost off the dry signal
      .band(freq = pPresenceHz, q = pPresenceQ, db = pPresence)  // presence: parallel boost off the dry signal
    // power amp
    .distort(0.30, "gentle", 2)
    // cabinet
    .eq()
      .band(freq = snareHz, q = 5.0, db   = -1)                   // let the snare cut through
      .lowpass(5000).lowpass(5000)                                // cabinet speaker sim    
 
  return amped.mul(0.60)
})()

// Bass — sub sine + parallel saturated grind, mud band filtered out between them ----------------
let bass = (() => {

  // --- Overridable params ----------------------------------------------------------------------
  let pSub     = Osc.param("sub",         1.00, "Sub Volume")
  let pDrive   = Osc.param("drive",       0.60, "Saturation amount of the grind layer")
  let pGrindLo = Osc.param("grindlo",  100.000, "Grind highpass — where the bass starts biting")
  let pGrindHi = Osc.param("grindhi", 1100.000, "Grind lowpass — where the bass stops biting")
  let pGrind   = Osc.param("grind",       0.55, "Grind Volume")
  // ----------------------------------------------------------------------------------------------

  // Sub: a bare sine. No filter — a sine has no harmonics to remove. This is the weight,
  // and it lives at 36–70 Hz where nothing else in the mix is.
  let sub = Osc.sine().mul(pSub)
    .distort(0.2, "gentle", 2).highpass(25)

  // Grind: "tube" is an ASYMMETRIC shape, so it generates EVEN harmonics — which is what lets
  // the ear reconstruct a 41 Hz fundamental on a speaker that cannot play 41 Hz.
  // Band-limited to land in the mid scoop and NOT in the 120–250 Hz mud band.
  // Asymmetric shapes also produce DC; the highpass removes it.
  let grind = Osc.saw()
    .pitchEnvelope(12, 0.001, 0.02)
    .distort(pDrive, "softsat", 2)
    .lowpass(freq = pGrindHi, q = 3.00)
    .highpass(freq = pGrindLo, q = 0.707)
    .mul(pGrind)
                                                                                                   
  return sub.plus(grind)
})()

export guitarDyna = "0.98 0.90!7 0.95 0.92!7".sub(perlin.range(0.00, 0.05).seg(2))
export guitarClip = "<0.83!31 0.81 0.83!31 0.79 0.83!30 0.75 0.79>".sub(perlin.range(0.0, 0.03).seg(8))

// Lead - Inspired by: Editors - Papillon  ---------------------------------------------------------------------------------------------------------------------
export lead_pat =  `<[-7 0 2 4] [-7 0 4 [2 6]|[4 2]|2|2|2|2] [-5 -1 2 4] [-4 -1 [4 3]|[5 3]|3|3|3|3 [1 -1]|1|1|1|1]>*2`

export lead_shape = x => x.gain(0.40).sound(guitar).adsrOff().unison(5).spread(0.07).oscp("decay", 0.7)
  .oscp("hptrack", Math.pow(2, 10 / 12)).oscp("hpq", 0.7)
  .oscp("mids", 3.0).oscp("midsQ", 1.0).oscp("midsHz", leadHz).oscp("presence", 0.0)
  .clip(0.87).velocity(guitarDyna).vowel("a e i o u".scramble(4)).vowelWet(0.3)
  .apply(x => x.transpose(0).pan(0.45))

export lead_arrange = x => x.orbit(0)
  .scale("<e4:minor!48 e5:minor!16 e4:minor!48 e3:minor!16>").postgain("<0.210!48 0.110!16 0.210!48 0.250!16>")  
  .shuffle("<1!80 1!1 4/8!14 1!33>")                                                                            
  .mute("<1!64 0!32 1!48 0!48>")
  .late(berlin.range(0.0005, 0.0015).mul(drunk))

export lead = n(lead_pat).apply(lead_shape).tag("lead")

// Guitar 1  --------------------------------------------------------------------------------------------------------------------------------------------------
export guitar1_pat =
  `<[[-7 -7] [[2 3] [4 2] 0] 2 <4 3 1> [-7 -7 4 3] 0 2 <[-1 1 3@2] [[3 4] 6@2 7] [[1 3] 4 3 2] [[6 10 7 5]]>]!4
    [[4 [4 4 2 0] [4 3 2 0] 0] [-1 [1 [<3 2> 1] -1 -4]] [-3!4 -3!8 4 2 4 0] [2 [2 6@3]]]!2
    [[-3,-7] [[-4,-8] [-1,-4]] [0,-3] <[[4 6],[-2 3]] [0,-1]>] [<[7,4] [[7 4 6 0  7 4 2 0]!2]> [2 0 3 0] 0 [[-5 -2 0 3] 4]]>/4`

export guitar1_shape = x => x.gain(0.8).velocity(guitarDyna.fast(2)).sound(guitar).adsrOff().unison(15).spread(0.05) // . solo()
  .oscp("hptrack", Math.pow(2, 7 / 12)).oscp("hpq", 1.3) //. mute()
  .oscp("mids", 10.0).oscp("midsHz", "1600".sub(saw.pow(0.5).mul(200).slow(4))).oscp("midsQ", 1.0)
  .oscp("presence", 8.0).oscp("presenceHz", "2800".sub(saw.pow(0.5).mul(500).slow(4))).oscp("presenceQ", 0.7)
  .clip(guitarClip.fast(2)).pan(0.55).body("maple").bodyWet(0.3)

export guitar1_arrange = x => x.orbit(1) // . solo()
  .scale("<e3:minor!48 e4:minor!16 e3:minor!48 e4:minor!16>").postgain(0.105)
  .late(berlin.range(0.0002, 0.0007).mul(drunk).seg(4))

export guitar1 = n(guitar1_pat).struct("<[x!16]!7 [x!24]!1 [x!16]!16>").apply(guitar1_shape).tag("guitar1")

// Guitar 2  --------------------------------------------------------------------------------------------------------------------------------------------------
export guitar2_pat =
  `<[11 11 9 8  7 7 9 6] [11 11 [13 11] 8  7 7 5 6] [11 11 9 11  7 7 7 8]
    [11 11 [13 9] 4  7 4 2 3]
    [4 4 6 8  4 4 5 6] [4 4 6 8  11 11 9 10] [4 4 3 6  4 4 2 3]
    [7 11 [3 7] [6 7] [4 4 6 4]!2 [0 2 4 6] 9]>/4`

export guitar2_shape = x => x.gain(0.775).velocity(guitarDyna.fast(2)).sound(guitar).adsrOff().unison(13).spread(0.05)
  .oscp("hptrack", Math.pow(2, 3 / 12)).oscp("hpq", 1.1)
  .oscp("mids", 10.0).oscp("midsHz", 1200).oscp("midsQ", 0.7)
  .oscp("presence", 8.0).oscp("presenceHz", 2400).oscp("presenceQ", 0.7)
  .clip(guitarClip.fast(2)).pan(0.66).body("cedar").bodyWet(0.3)

export guitar2_arrange = x => x.orbit(2)  // . solo()
  .scale("<e2:minor>").postgain(0.105).mute("<0!128 1!16 0!16>")
  .late(berlin.range(0.0000, 0.0005).mul(drunk).seg(4))

export guitar2 = n(guitar2_pat).struct("<[x!16]!7 [x!24]!1 [x!16]!16>").apply(guitar2_shape).tag("guitar2")

// Guitar 3  --------------------------------------------------------------------------------------------------------------------------------------------------
export guitar3_pat =
  `<[0 0 2 4 0 0 -2 -1]!4
    [0 0 2 4 0 0 -2 -1]!2 [0 0 -1 3  0 0 -2 -1]!1 [0 0 3 [0 -1]  0 0 [5 -2 0 3] 6]!1>/4`

export guitar3_shape = x => x.gain(0.75).velocity(guitarDyna.fast(2)).sound(guitar).adsrOff().unison(11).spread(0.05)
  .oscp("hptrack", Math.pow(2, 0 / 12)).oscp("hpq", 0.7)
  .oscp("mids", 10.0).oscp("midsHz", 500).oscp("midsQ", 0.7)
  .oscp("presence", 6.0).oscp("presenceHz", 2000).oscp("presenceQ", 0.7)
  .clip(guitarClip.fast(2)).pan(0.33)

export guitar3_arrange = x => x.orbit(2) // . solo()
  .scale("<e2:minor>").postgain(0.105).mute("<0!128 1!16 0!16>")
  .late(berlin.range(0.0000, 0.0004).mul(drunk).seg(4))

export guitar3 = n(guitar3_pat).struct("<[x!16]!7 [x!24]!1 [x!16]!16>").apply(guitar3_shape).tag("guitar3")

// Bass  ------------------------------------------------------------------------------------------------------------------------------------------------------
export bass_pat =
  `<[0 0 2 4 0 0 -2 -1]!3 [0 0 2 4 0 2 5 6]
    [0 0 2 4 0 0 -2 -1]!2 [0 0 -1 3  7 4 2 -1]!1 [0 0 3 [0 -1]  0 0 [0 2 4 6] 9]!1>/8`

export bass_shape = x => x.gain(1.0).velocity("0.98 0.96 0.97 0.96".fast(2)).sound(bass).postgain(0.09) //. mute()
    .oscp("drive", 0.25).oscp("grindlo", 100).oscp("grindhi", 420).oscp("grind", 0.85).oscp("sub", 0.95)  // . solo()
    .adsr(0.010, 0.6, 0.1, 0.020)

export bass_arrange = x => x.orbit(3)
  .scale("e1:minor").notchf(snareHz).notchq(1.0).mute("<0!128 1!32>").swingBy(0.05, 4)
  .pan(0.25).superimpose(pan(0.75)) // .solo()
  .clip("<[0.8 0.7 0.6 0.7]>*4".sub(perlin.range(0.0, 0.1)))  
  .late(berlin.range(0.0000, 0.0005).mul(drunk).seg(4))

export bass = n(bass_pat).struct("<[x!2]!16 [x!2 x [x x]]!16 [[x x] x!3]!28 [x!8]!2 [[x x] x!3]!2>").fast(2).apply(bass_shape).tag("bass")

// Drums  -----------------------------------------------------------------------------------------------------------------------------------------------------
export kick_pat = `<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd  ~ bd  ~]!32 [bd!4]!16 [bd ~ bd [~ bd]]!15 [bd!16]!1>`
export kick_shape = x => x.n(0).gain(0.200).velocity("0.98 0.96 0.97 0.96").pan(0.45)
  .hpf(30).hpq(1).lpf(10000).adsr(0.001, 0.050, 0.80, 0.25).distort(0.1)
  .superimpose(x => x.bpf("85").bpq(3.0).vel(0.75))
export kick_arrange = x => x.orbit(5).mute("<0!128 1!32>").late(berlin.range(0.0000, 0.0005).mul(drunk).seg(4))
export kick = sound(kick_pat).apply(kick_shape).tag("kick")

export snare_pat = `<[~!2]!2  [~!4]!2  [~!8]!2  [~!16]  [~!24]  [~  sd  ~ sd]!15 [[~ sd] sd  [[~ sd] sd] [sd!4]] [~  sd  ~ sd]!16 [~ sd ~ sd]!32>`
export snare_shape = x => x.n(5).gain(0.260).pan(0.60)
  .hpf(80).lpf(15000).lpq(0.7).adsr(0.001, 0.035, 0.80, 0.50)
  .superimpose(x => x.bpf(pure(snareHz).add(berlin.mul(10).slow(4))).bpq(3.0).vel(1.00))
export snare_arrange = x => x.orbit(5).mute("<0!128 1!32>").late(berlin.range(0.0010, 0.0015).mul(drunk).seg(4))
export snare = sound(snare_pat).apply(snare_shape).tag("snare")

export hats_pat = `<[hh hh hh hh]!16 [hh hh oh hh]!24 [cr hh cr hh]!24 [~ rd ~ rd]!32>`
export hats_shape = x => x.gain(0.26).pan(0.40)
  .hpf(700).lpf("14500".add(perlin.mul(250).fast(4))).lpq(0.7).adsr(0.003, 0.05, 0.80, 1.0)
export hats_arrange = x => x.orbit(7).mute("<0!128 1!32>").late(berlin.range(0.0015, 0.0025).mul(drunk).seg(4))
export hats = sound(hats_pat).fast(2).apply(hats_shape).velocity("<1.0 0.85 0.93 0.85>*4".sub(berlin.range(0.0, 0.05).slow(4))).tag("hats")

export clap_pat = `<~!79 [~ ~ ~ cp  cp ~ cp ~] ~!47 [~ ~ ~ cp  cp ~ cp ~]>`
export clap_shape = x => x.gain(0.062).pan(0.3).superimpose(pan(0.7))
export clap_arrange = x => x.orbit(8).mute("<0!128 1!32>")
export clap = sound(clap_pat).apply(clap_shape).tag("clap")

export shaker_pat = `<pink ~ pink pink>*16`
export shaker_shape = x => x.gain(0.135).velocity("<1.0 0.90 0.95 0.90>*16") // . mute()
  .hpf(8000).hpq(0.7).lpf(17000).lpq(1.0) //  . solo()
  .pan(sine.range(0.35, 0.65).slow(8)).adsr(0.015, 0.15, 0.0, 0.01)
export shaker_arrange = x => x.orbit(9).late(berlin.range(0.0015, 0.0025).mul(drunk))
export shaker = sound(shaker_pat).apply(shaker_shape).tag("shaker")

// Count-in  --------------------------------------------------------------------------------------------------------------------------------------------------
export countin = sound("oh!2").apply(hats_shape).velocity(0.5).tag("countin").roomWet("0.1")
export countin_arrange = x => x.orbit(7).filterWhen(t => t < 2)

// Song  ------------------------------------------------------------------------------------------------------------------------------------------------------
export song_arrange = x => x.late(2).filterWhen(t => t >= 2) // shift the song one cycle to make room for the count-in

export song_body = stack(
  stack(
    // Guitars      
    stack(
      // Lead - Inspired by: Editors - Papillon      
      lead.apply(lead_arrange) // .solo()
      , // Guitar 1
      guitar1.apply(guitar1_arrange) // .solo() .mute()
      , // Guitar 2
      guitar2.apply(guitar2_arrange) // .solo() .mute()
      , // Guitar 3
      guitar3.apply(guitar3_arrange) // .solo() .mute()
      , // Bass
      bass.apply(bass_arrange) // .solo() .mute()
    ).roomWet(0.10).rsize(3.0).compressor(-28, 2, 6, 0.005, 0.1)
  ).analog(feel).transpose(transposition)
  , // Drums
  stack(
    kick.apply(kick_arrange),     // .solo() .mute()
    snare.apply(snare_arrange),   // .solo() .mute()
    hats.apply(hats_arrange),     // .solo() .mute()
    clap.apply(clap_arrange),     // .solo() .mute()
    shaker.apply(shaker_arrange)  // .solo() .mute()
  ).analog(feel / 2).roomWet(0.20).rsize(3.0).rlp(5000) .compressor(-28, 2, 6, 0.03, 0.1) //  .mute()
).seed(timeOfDay.mul(60*60*60*24)).shuffle("<1!80 2!48 1!112 2!32>").swingBy(0.005, 4)

export song = stack(
  // Count-in: four open hats at half the song's hat speed
  countin.apply(countin_arrange)
  , // Song body
  song_body.apply(song_arrange)
  , // Master
  master(Master.of(MasterFx.reverb().wet(0.00).damp(0.2).roomSize(8), MasterFx.gain(2.6)))
)









    """,
)
