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
    code = """import * from "stdlib"
import * from "sprudel"

// Song Status: Upcoming Garage Band ...

let feel          =   15    // 0.0 .. guitar | 100.0 .. rave | 200.0 .. hyper
let transposition =   -0    // -2 .. D | 0 .. E | 2 .. F#
let drunk         =    1    // How many beers did each band member have?
let snareHz       =  210    // Where does the snare cut through?
let leadHz        = 3400    // Where does the lead sit?

let guitar = (() => {

  // --- Overridable params ---------------------------------------------------------------------------------------
  let pVoices  = OscSlot.voices
  let pSpread  = OscSlot.spread
  let pAnalog  = OscSlot.analog

  // ADSR
  let pAttack     = Osc.param("attack",        0.003, "Attack")
  let pDecay      = Osc.param("decay",         0.800, "Decay")
  let pSustain    = Osc.param("sustain",       0.000, "sustain")
  let pRelease    = Osc.param("release",       0.025, "Release")

  // String params — both dimensionless harmonic counts, no absolute Hz anywhere
  let pStrFloor   = Osc.param("strFloor",      6.000, "String lowpass floor, in harmonics of the note")
  let pStrSweep   = Osc.param("strSweep",     24.000, "Extra harmonics alive at the pluck")
  let pStrDecay   = Osc.param("strDecay",      0.400, "How fast the string darkens")
  let pStrQ       = Osc.param("strQ",          0.707, "String brightness")

  // amp tone
  let pMidsHz     = Osc.param("midsHz",      850.000, "Mids frequency")
  let pMidsQ      = Osc.param("midsQ",         0.707, "Mids Q")
  let pMids       = Osc.param("mids",          1.700, "Mids Volume")

  let pPresenceHz = Osc.param("presenceHz", 2500.000, "Presence frequency")
  let pPresenceQ  = Osc.param("presenceQ",     0.707, "Presence Q")
  let pPresence   = Osc.param("presence",      5.000, "Presence Volume")

  // Low mud filter
  let pHpTrack    = Osc.param("hptrack",       1.000, "Highpass cutoff as a multiple of the note frequency")
  let pHpQ        = Osc.param("hpq",           0.707, "Highpass resonance")
  // --------------------------------------------------------------------------------------------------------------
  
  let signal = Osc.supersaw(freq = Osc.freq(), voices = 31, spread = 0.10)
    // enable the phase-pool for consistent onsets and fundamentals
    .phasePool(on = 1, kMin = 0.50, kMax = 0.70, warmup = 0, selection = "normal")
    // character knobs — plain scalars, SuperSaw-typed, must precede the filter
    .analog(pAnalog).spreadPower(8.0).sideAtten(0.2).gainJitter(0.20).centerJitter(0.25)
    // Simulate plucked string
    .pitchEnvelope(0.5, 0.001, 0.005)
    // String brightness
    .lowpass(freq = Osc.freq().mul(pStrFloor.plus(pStrSweep.adsr(pAttack, pStrDecay, 0.0, pRelease))), q = pStrQ)    
    // noise burst
    .plus(Osc.whitenoise().highpass(1000).adsr(0.0, 0.05, 0.0, 0.005).mul(0.05))
    // the string - lowpass adsr for the string sound and adsr for the string
   .adsr(pAttack, pDecay, pSustain, pRelease)

  // the amp
  let amped = signal   
    // pre amp
    .distort(0.80, "tube", 4).highpass(100)
    // drive amp
    .distort(0.50, "soft", 4)  
    .eq()
      .tap(freq = pMidsHz,     q = pMidsQ,     gain = pMids)      // mids: parallel boost off the dry signal
      .tap(freq = pPresenceHz, q = pPresenceQ, gain = pPresence)  // presence: parallel boost off the dry signal
      .highpass(freq = Osc.freq().mul(pHpTrack), q = pHpQ)        // follow freq to avoid low mud ... again
    // power amp
    .distort(0.30, "gentle", 2)
    // cabinet
    .eq()
      .band(freq = snareHz, q = 5.0, db   = -3)                   // let the snare cut through
      .lowpass(4950).lowpass(4950)                                // cabinet speaker sim    
      .highpass(100)                                               // filter the low end
  
  return amped.mul(0.45)
})()

// Bass — sub sine + parallel saturated grind, mud band filtered out between them ----------------
let bass = (() => {

  // --- Overridable params ----------------------------------------------------------------------
  let pSub     = Osc.param("sub",         1.00, "Sub Volume")
  let pDrive   = Osc.param("drive",       0.60, "Saturation amount of the grind layer")
  let pGrindLo = Osc.param("grindlo",  250.000, "Grind highpass — set above the mud band")
  let pGrindHi = Osc.param("grindhi", 1100.000, "Grind lowpass — where the bass stops biting")
  let pGrind   = Osc.param("grind",       0.55, "Grind Volume")
  // ----------------------------------------------------------------------------------------------

  // Sub: a bare sine. No filter — a sine has no harmonics to remove. This is the weight,
  // and it lives at 36–70 Hz where nothing else in the mix is.
  let sub = Osc.sine().mul(pSub)

  // Grind: "tube" is an ASYMMETRIC shape, so it generates EVEN harmonics — which is what lets
  // the ear reconstruct a 41 Hz fundamental on a speaker that cannot play 41 Hz.
  // Band-limited to land in the mid scoop and NOT in the 120–250 Hz mud band.
  // Asymmetric shapes also produce DC; the highpass removes it.
  let grind = Osc.saw()
    .detune(0)
    .pitchEnvelope(12, 0.001, 0.01)
    .distort(pDrive, "tube", 4)
    .lowpass(freq = pGrindHi, q = 0.707)
    .highpass(freq = pGrindLo, q = 0.707)
    .mul(pGrind)
                                                                                                   
  return sub.plus(grind)
})()

export guitarDyna = "0.98 0.92!7 0.97 0.92!7".sub(perlin.range(0.00, 0.05).seg(2))
export guitarClip = "<0.91!31 0.89 0.91!31 0.87 0.91!30 0.85 0.88>"

// Lead - Inspired by: Editors - Papillon  ---------------------------------------------------------------------------------------------------------------------
export lead_pat =  `<[-7 0 2 4] [-7 0 4 [2 6]|[4 2]|2|2|2|2] [-5 -1 2 4] [-4 -1 [4 3]|[5 3]|3|3|3|3 [1 -1]|1|1|1|1]>*2`

export lead_shape = x => x.gain(0.35).sound(guitar).unison(5).spread(0.05).oscp("decay", 0.6).oscp("hptrack", Math.pow(2, 5/12))
  .oscp("mids", 3.0).oscp("midsQ", 2.2).oscp("midsHz", leadHz).oscp("presence", 0.0) // .bpf(leadHz).bpq(1.5)
  .clip(0.95).vowel("a e i o u".scramble(4)).vowelWet(0.3)
  .apply(x => x.transpose(0).pan(0.33).superimpose(pan(0.66))).hpf(3000)

export lead_arrange = x => x.orbit(0)
  .scale("<e4:minor!48 e5:minor!16 e4:minor!48 e3:minor!16>").postgain("<0.185!48 0.120!16 0.185!48 0.200!16>")  
  .shuffle("<1!80 1!1 4/8!14 1!33>")                                                                            
  .mute("<1!64 0!32 1!48 0!48>")
  .late(berlin.range(0.0005, 0.0015).mul(drunk))

export lead = n(lead_pat).apply(lead_shape).tag("lead")

// Guitar 1  --------------------------------------------------------------------------------------------------------------------------------------------------
export guitar1_pat =
  `<[0 [0@4 [4 9] -3] -1 <4 3 1> [0 2 4 3] 0 2 <[-1 1 3@2] [[3 4] 6@2 7] [[1 3] 4 3 2] [[6 10 7 5]]>]!4
    [[4 [4 4 2 0] [4 3 2 0] 0] [-1 [1 [<3 2> 1] -1 -4]] [-3!4 -3!8 4 2 4 0] [2 [2 6@3]]]!2
    [[-3,-7] [[-4,-8] [-1,-4]] [0,-3] <[[4 6],[-2 3]] [0,-1]>] [<[7,4] [[7 4 6 0  7 4 2 0]!2]> [2 0 3 0] 0 [[-5 -2 0 3] 4]]>/4`

export guitar1_shape = x => x.gain(0.8).velocity(guitarDyna.fast(2)).sound(guitar).unison(15).spread(0.05) // . solo()
  .oscp("hptrack", Math.pow(2, 7/12)).oscp("hpq", 1.0)
  .oscp("mids", 3.0).oscp("midsHz", 1400).oscp("midsQ", 10.0)
  .oscp("presence", 2.5).oscp("presenceHz", "3500".sub(saw.pow(2).mul(1000).slow(8))).oscp("presenceQ", 3.0)
  .clip(guitarClip.fast(2)).adsr(0, 1, 1, 0.04)
  .pan(0.75)
  .body("maple").bodyWet(0.2)

export guitar1_arrange = x => x.orbit(1)  // . solo()
  .scale("<e3:minor!48 e4:minor!16 e3:minor!48 e4:minor!16>").postgain(0.125)
  .late(berlin.range(0.0002, 0.0007).mul(drunk).seg(4))

export guitar1 = n(guitar1_pat).struct("<[x!16]!7 [x!24]!1 [x!16]!16>").apply(guitar1_shape).tag("guitar1")

// Guitar 2  --------------------------------------------------------------------------------------------------------------------------------------------------
export guitar2_pat =
  `<[11 11 9 8  7 7 9 6] [11 11 [13 11] 8  7 7 5 6] [11 11 9 11  7 7 7 8]
    [11 11 [13 9] 4  7 4 2 3]
    [4 4 6 8  4 4 5 6] [4 4 6 8  11 11 9 10] [4 4 3 6  4 4 2 3]
    [7 11 [3 7] [6 7] [4 4 6 4]!2 [0 3 4 6] 9]>/4`

export guitar2_shape = x => x.gain(0.8).velocity(guitarDyna.fast(2)).sound(guitar).unison(13).spread(0.05)
  .oscp("hptrack", Math.pow(2, 3/12)).oscp("hpq", 1.2)
  .oscp("mids", 3.0).oscp("midsHz", 1100).oscp("midsQ", 2.0)
  .oscp("presence", 2.0).oscp("presenceHz", 3000).oscp("presenceQ", 3.0)
  .clip(guitarClip.fast(2)).adsr(0, 1, 1, 0.04)
  .pan(0.25)
  .body("cedar").bodyWet(0.2)

export guitar2_arrange = x => x.orbit(2)  // . solo()
  .scale("<e2:minor>").postgain(0.125).mute("<0!128 1!16 0!16>")
  .late(berlin.range(0.0000, 0.0005).mul(drunk).seg(4))

export guitar2 = n(guitar2_pat).struct("<[x!16]!7 [x!24]!1 [x!16]!16>").apply(guitar2_shape).tag("guitar2")

// Guitar 3  --------------------------------------------------------------------------------------------------------------------------------------------------
export guitar3_pat =
  `<[0 0 2 4 0 0 -2 -1]!4
    [0 0 2 4 0 0 -2 -1]!2 [0 0 -1 3  0 0 -2 -1]!1 [0 0 3 0  0 0 [5 -2 0 3] 6]!1>/4`

export guitar3_shape = x => x.gain(0.8).velocity(guitarDyna.fast(2)).sound(guitar).unison(11).spread(0.05)
  .oscp("hptrack", 1.0).oscp("hpq", 1.4)
  .oscp("mids", 3.0).oscp("midsHz", 800).oscp("midsQ", 2.0)
  .oscp("presence", 2.0).oscp("presenceHz", 2400).oscp("presenceQ", 3.0)
  .clip(guitarClip.fast(2)).adsr(0, 1, 1, 0.04)
  .pan(0.40).superimpose(pan(0.65))

export guitar3_arrange = x => x.orbit(2)  // . solo()
  .scale("<e2:minor>").postgain(0.120).mute("<0!128 1!16 0!16>")
  .late(berlin.range(0.0000, 0.0004).mul(drunk).seg(4))

export guitar3 = n(guitar3_pat).struct("<[x!16]!7 [x!24]!1 [x!16]!16>").apply(guitar3_shape).tag("guitar3")

// Bass  ------------------------------------------------------------------------------------------------------------------------------------------------------
export bass_pat =
  `<[0 0 2 4 0 0 -2 -1]!4
    [0 0 2 4 0 0 -2 -1]!2 [0 0 -1 3  7 6 2 -1]!1 [0 0 3 0  0 0 [0 3 4 6] 9]!1>/8`

export bass_shape = x => x.gain(1.0).velocity("0.98 0.96 0.97 0.96".fast(2)).sound(bass).postgain(0.10) //. mute()
    .oscp("drive", 0.80).oscp("grindlo", 100).oscp("grindhi", 1000).oscp("grind", 1.20).oscp("sub", 0.90).hpf(25) // . solo()
    .adsr(0.005, 1.0, 0.2, 0.010)

export bass_arrange = x => x.orbit(3).clip(0.8).pan(0.55) // .solo()
  .scale("e1:minor").notchf(snareHz).notchq(1.0).mute("<0!128 1!32>")
  .late(berlin.range(0.0000, 0.0005).mul(drunk).seg(4))

export bass = n(bass_pat).struct("<[x!2]!16 [x!2 [x x] x]!16 [[x x] x!3]!28 [x!8]!2 [[x x] x!3]!2>").fast(2).apply(bass_shape).tag("bass")

// Drums  -----------------------------------------------------------------------------------------------------------------------------------------------------
export kick_pat = `<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd  ~ bd  ~]!32 [bd!4]!16 [bd ~ bd [~ bd]]!15 [bd!16]!1>`
export kick_shape = x => x.n(0).gain(0.200).velocity("0.98 0.96 0.97 0.96").pan(0.45)
  .hpf(30).hpq(1).lpf(10000).adsr(0.001, 0.03, 0.80, 0.25).distort(0.1)
  .superimpose(x => x.bpf("85").bpq(3.0).vel(0.75))
export kick_arrange = x => x.orbit(5).mute("<0!128 1!32>").late(berlin.range(0.0000, 0.0005).mul(drunk).seg(4))
export kick = sound(kick_pat).apply(kick_shape).tag("kick")

export snare_pat = `<[~!2]!2  [~!4]!2  [~!8]!2  [~!16]  [~!24]  [~  sd  ~ sd]!32 [~ sd ~ sd]!32>`
export snare_shape = x => x.n(5).gain(0.260).pan(0.60)
  .hpf(80).lpf(15000).lpq(0.6).adsr(0.001, 0.03, 0.80, 0.50)
  .superimpose(x => x.bpf(pure(snareHz).add(berlin.mul(5).fast(4))).bpq(3.0).vel(1.00))
export snare_arrange = x => x.orbit(5).mute("<0!128 1!32>").late(berlin.range(0.0010, 0.0015).mul(drunk).seg(4))
export snare = sound(snare_pat).apply(snare_shape).tag("snare")

export hats_pat = `<[hh hh hh hh]!16 [hh hh oh hh]!24 [cr hh cr hh]!24 [~ rd ~ rd]!32>`
export hats_shape = x => x.gain(0.245).pan(0.40)
  .hpf(700).lpf("14500".add(perlin.mul(250).fast(4))).lpq(0.7).adsr(0.003, 0.05, 0.80, 1.0)
export hats_arrange = x => x.orbit(7).mute("<0!128 1!32>").late(berlin.range(0.0015, 0.0025).mul(drunk).seg(4))
export hats = sound(hats_pat).fast(2).apply(hats_shape).velocity("<1.0 0.85 0.93 0.85>*4").tag("hats")

export clap_pat = `<~!79 [~ ~ ~ cp  cp ~ cp ~] ~!47 [~ ~ ~ cp  cp ~ cp ~]>`
export clap_shape = x => x.gain(0.058).pan(0.3).superimpose(pan(0.7))
export clap_arrange = x => x.orbit(8).mute("<0!128 1!32>")
export clap = sound(clap_pat).apply(clap_shape).tag("clap")

export shaker_pat = `<pink ~ pink pink>*16`
export shaker_shape = x => x.gain(0.115).velocity("<1.0 0.90 0.95 0.90>*16") // . mute()
  .hpf(5000).hpq(0.5).lpf(17000).lpq(0.7)
  .pan(sine.range(0.35, 0.65).slow(8)).adsr(0.010, 0.15, 0.0, 0.01)
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
    ).roomWet(0.20).rsize(3.0).compressor(-28, 2, 6, 0.005, 0.1)
    , // Bass
    bass.apply(bass_arrange) // .solo() .mute()
  ).analog(feel).transpose(transposition)
  , // Drums
  stack(
    kick.apply(kick_arrange),     // .solo() .mute()
    snare.apply(snare_arrange),   // .solo() .mute()
    hats.apply(hats_arrange),     // .solo() .mute()
    clap.apply(clap_arrange),     // .solo() .mute()
    shaker.apply(shaker_arrange)  // .solo() .mute()
  ).analog(feel / 2).roomWet(0.20).rsize(3.0).rlp(8000) .compressor(-28, 2, 6, 0.03, 0.1) //  .mute()
).seed(timeOfDay.mul(60*60*60*24)).shuffle("<1!80 2!48 1!112 2!32>").swingBy(0.005, 4)

export song = stack(
  // Count-in: four open hats at half the song's hat speed
  countin.apply(countin_arrange)
  , // Song body
  song_body.apply(song_arrange)
  , // Master
  master(Master.of(MasterFx.reverb().wet(0.00).damp(0.2).roomSize(8), MasterFx.gain(2.5)))
)







    
    """,
)
