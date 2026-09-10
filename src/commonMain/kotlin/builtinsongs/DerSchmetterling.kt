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
let drunk         =    2    // How many beers did each band member have?
let snareHz       =  210    // Where does the snare cut through?

let guitar = (() => {

  // --- Overridable params ---------------------------------------------------------------------------------------
  let pVoices     = OscSlot.voices
  let pSpread     = OscSlot.spread
  let pAnalog     = OscSlot.analog

  // ADSR
  let pAttack     = Osc.param("attack",       0.007, "Attack")
  let pDecay      = Osc.param("decay",        1.000, "Decay")
  let pSustain    = Osc.param("sustain",      0.000, "sustain")
  let pRelease    = Osc.param("release",      0.030, "Release")

  // Amp EQ
  let pLow        = Osc.param("low",          0.000, "Low Volume")
  let pLowHz      = Osc.param("lowHz",      650.000, "Low frequency")
  let pLowQ       = Osc.param("lowQ",         0.707, "Low Q")

  let pMid        = Osc.param("mid",          0.000, "Mid Volume")
  let pMidHz      = Osc.param("midHz",     1250.000, "Mid frequency")
  let pMidQ       = Osc.param("midQ",         0.707, "Mid Q")

  let pHigh       = Osc.param("high",         0.000, "High Volume")
  let pHighHz     = Osc.param("highHz",    2500.000, "High frequency")
  let pHighQ      = Osc.param("highQ",        0.707, "High Q")

  // Low mud filter
  let pHpTrack    = Osc.param("hptrack",      1.000, "Highpass cutoff as a multiple of the note frequency")
  let pHpQ        = Osc.param("hpq",          0.707, "Highpass resonance")
  // --------------------------------------------------------------------------------------------------------------

  let saw = Osc.supersaw(x => x.voices(19).spread(0.10)
    // enable the phase-pool for consistent onsets and fundamentals
    .phasePool(on = 1, kMin = 0.60, kMax = 0.85, warmup = 0, selection = "normal")
    // character knobs, plain scalars on the supersaw builder
    .spreadPower(8.0).sideAtten(0.5).gainJitter(0.10).centerJitter(0.10)
    // analog settings
    .analog(pAnalog).analogSpread(0.5)
  )
 
  let signal = saw //.mix(saw2, 1.0)
    // Simulate plucked string
    .pitchEnvelope(0.5, 0.001, 0.02)
    //.lowpass(freq = Osc.freq().times(4).add(Osc.constant(5000).adsr(pAttack, 1.0, 0.0, 0.050)), q = 0.7)
    // noise burst
    .plus(Osc.crackle(1.25).highpass(1000).adsr(0.005, 0.1, 0.0, 0.05).mul(1.0))
    // the string - lowpass adsr for the string sound and adsr for the string
    .adsr(pAttack, pDecay, pSustain, pRelease).adsrCurves("linear", "linear", "linear")
           
  // the amp
  let amped = signal
    // pre amp
    .distort(0.40, "tube", 4).highpass(100)
    // drive amp
    .distort(0.20, "gentle", 4).highpass(100)
    .eq(e => e
      .band(freq = pLowHz,  q = pLowQ,  db = pLow)       // low
      .band(freq = pMidHz,  q = pMidQ,  db = pMid)       // mid
      .band(freq = pHighHz, q = pHighQ, db = pHigh)      // high    
    )
    // power amp
    //.distort(0.30, "gentle", 2)
    .drive(0.42)
    // cabinet
    .eq(e => e.band(freq = snareHz, q = 5.0, db = -3)) // let the snare cut through
    .lowpass(5000).lowpass(5000)                       // cabinet speaker sim  
    .highpass(freq = Osc.freq().mul(pHpTrack), q = pHpQ, analog = pAnalog)  // follow freq to avoid low mud ... again

  return amped.mul(0.30)
})()

// Bass — sub sine + parallel saturated grind, mud band filtered out between them ----------------
let bass = (() => {

  // --- Overridable params ----------------------------------------------------------------------
  let pSub     = Osc.param("sub",         1.00, "Sub Volume")
  let pDrive   = Osc.param("drive",       0.60, "Saturation amount of the grind layer")
  let pGrindLo = Osc.param("grindlo",   100.00, "Grind highpass — where the bass starts biting")
  let pGrindHi = Osc.param("grindhi",  1100.00, "Grind lowpass — where the bass stops biting")
  let pGrind   = Osc.param("grind",       0.00, "Grind Volume")
  let pHarm    = Osc.param("harmonics",   1.00, "Harmonics Volume")
  // ----------------------------------------------------------------------------------------------

  // Sub: a bare sine. No filter — a sine has no harmonics to remove. This is the weight,
  // and it lives at 36–70 Hz where nothing else in the mix is.
  let sub = Osc.sine(x => x.analog(Osc.slot.analog)).mul(pSub)

  // Harmonics: sine partials at 2f .. 8f, gain 1/n, the fundamental left to the sub above. On the
  // low E that is 82 to 328 Hz, the band a small speaker can play and the ear folds back into 41 Hz.
  let harmonics = Osc.sine(x => x.harmonics(11, 1.2).fundamental(0).analog(Osc.slot.analog).analogSpread(0.5)).mul(pHarm)

  // Grind: "tube" is an ASYMMETRIC shape, so it generates EVEN harmonics — which is what lets
  // the ear reconstruct a 41 Hz fundamental on a speaker that cannot play 41 Hz.
  // Band-limited to land in the mid scoop and NOT in the 120–250 Hz mud band.
  // Asymmetric shapes also produce DC; the highpass removes it.
  let grind = Osc.saw()
    .pitchEnvelope(12, 0.001, 0.02)
    .distort(pDrive, "tube", 4)
    .lowpass(freq = pGrindHi, q = 0.707)
    .highpass(freq = pGrindLo, q = 0.707)
    .mul(pGrind)
     
  return sub.plus(harmonics).plus(grind)
    .eq(e => e.band(freq = snareHz, q = 5.0, db = -2)) // let the snare cut through
    .mul(0.2)
})()

export guitarDyna = "0.98 0.90!7 0.95 0.92!7".sub(perlin.range(0.00, 0.05))
export guitarClip = "<0.92!31 0.84 0.92!31 0.83 0.92!30 0.75 0.80>".sub(perlin.range(0.0, 0.02))
  .mul("<0.985!32 [1.1 0.99!7]!32 0.975!32 [1.1 0.99!7]!32>")
export guitarDecay = "<0.450!16 0.550!16 0.450!16 0.550!16>"

// Lead - Inspired by: Editors - Papillon  ---------------------------------------------------------------------------------------------------------------------
export lead_pat =
  `<[-7 0 2 4] [-7 0 4 [2 -1]|[4 2]|2|2|2|2] [-5 -1 2 4] [-6 -1 [4 3]|[5 3]|3|3|3|3 [1 -1]|1|1|1|1]>*2`

export lead_shape = x => x.gain(0.8).sound("supersaw").unison(voices = 7, spread = 0.1)
  .distort(0.75, "soft", 4).adsr(0.012, 0.5, 0.66, 0.500).coarse(2, 4)
  .clip(0.925).velocity(guitarDyna).body(material = "steel", wet = 0.7, floor = 0.5)
  .hpf(1200, 0.7)
  .lpf(freq = "2350".add(perlin.range(0, 500).slow(4)), env = 8.0, q = 0.7, attack = 0.012, decay = 0.1, sustain = 0.66, release = 0.200)
  .pan(0.2).superimpose(pan(0.8)) // . solo()

export lead_arrange = x => x.orbit(0) // .mute()
  .scale("<e4:minor!48 e5:minor!16 e4:minor!48 e3:minor!16>").postgain("<0.40!48 0.25!16 0.40!48 0.50!16>").postgain(mul(0.25))
  .shuffle("<1!80 1!1 4/8!14 1!33>")                                                                          
  .mute("<1!64 0!32 1!48 0!48>")
  .late(berlin.range(0.0005, 0.0015).mul(drunk))

export lead = n(lead_pat).apply(lead_shape).tag("lead")

// Guitar 1  --------------------------------------------------------------------------------------------------------------------------------------------------
export guitar1_pat =
  `<[[-7 -7] [[2 3] [4 2] 0] 2 [<4 3 1>!3 -1] [-7 -7 4 3] 0 2 <[-1 1 3@2] [[3 4] 6@2 7] [[1 3] 4 3 2] [[6 10 7 5]]>]!4
    [[4 [4 4 2 0] [4 3 2 0] 0] [-1 [1 [<3 2> 1] -1 -4]] [-3!4 -3!8 4 2 4 0] [2 [2 6@3]]]!2
    [[-3,-7] [[-4,-8] [-1,-4]] [0,-3] <[[4 6],[-2 3]] [0,-1]>] [<[7,4] [[7 4 6 0  7 4 2 0]!2]> [2 0 -1 0] 0 [[-5 -2 0 3] 4]]>/4`

export guitar1_shape = x => x.gain(0.5).velocity(guitarDyna.fast(2)).sound(guitar).adsrOff().unison(voices = 15, spread = 0.05) // . solo()
  .oscp("decay", guitarDecay).oscp("hptrack", Math.pow(2, 6 / 12)).oscp("hpq", 0.6) //. mute()
  .oscp("low", 2.5).oscp("lowHz", "1700").oscp("lowQ", 0.7)
  .oscp("mid", 2.5).oscp("midHz", "3000".sub(saw.pow(0.8).mul(200).slow(4))).oscp("midQ", 0.7)
  .oscp("high", 1.25).oscp("highHz", "3200").oscp("highQ", 0.6)
  .hpf(320)
  .clip(guitarClip.fast(2)).pan(0.5).body(material = "rosewood", wet = 0.3)

export guitar1_arrange = x => x.orbit(1)  // . solo()
  .scale("<e3:minor!48 e4:minor!16 e3:minor!48 e4:minor!16>").postgain(0.205)  // .mute()
  .late(berlin.range(0.0004, 0.0008).mul(drunk).seg(4))

export guitar1 = n(guitar1_pat).struct("<[x!16]!7 [x!24]!1 [x!16]!16>").apply(guitar1_shape).tag("guitar1")

// Guitar 2  --------------------------------------------------------------------------------------------------------------------------------------------------
export guitar2_pat =
  `<[11 11 9 8  7 7 9 6] [11 11 [13 11] 8  7 7 5 6] [11 11 9 11  7 7 7 8]
    [11 11 [13 9] 4  7 4 2 3]
    [4 4 6 8  4 4 5 6] [4 4 6 8  11 11 9 10] [4 4 3 6  4 4 2 3]
    [7 11 [3 7] [6 7] [4 4 6 4]!2 [0 2 4 6] 9]>/4`

export guitar2_shape = x => x.gain(0.5).velocity(guitarDyna.fast(2)).sound(guitar).adsrOff().unison(voices = 13, spread = 0.05)
  .oscp("decay", guitarDecay).oscp("hptrack", Math.pow(2, 0 / 12)).oscp("hpq", 1.1)
  .oscp("low", 1.5).oscp("lowHz",  850).oscp("lowQ", 0.6)
  .oscp("mid", 2.5).oscp("midHz", 1550).oscp("midQ", 0.7)
  .oscp("high", 1.25).oscp("highHz", 3000).oscp("highQ", 0.6)
  .hpf(240)
  .clip(guitarClip.fast(2)).pan(0.0).body(material = "oak", wet = 0.3)

export guitar2_arrange = x => x.orbit(2)  // . solo()
  .scale("<e2:minor>").postgain(0.170).mute("<0!128 1!16 0!16>") // .mute()
  .late(berlin.range(0.0002, 0.0006).mul(drunk).seg(4))

export guitar2 = n(guitar2_pat).struct("<[x!16]!7 [x!24]!1 [x!16]!16>").apply(guitar2_shape).tag("guitar2")

// Guitar 3  --------------------------------------------------------------------------------------------------------------------------------------------------
export guitar3_pat =
  `<[0 0 2 4 0 0 -2 -1]!4
    [0 0 2 4 0 0 -2 -1]!2 [0 0 -1 3  0 0 -2 -1]!1 [0 0 3 [0 -1]  0 0 [5 -2 0 3] 6]!1>/4`

export guitar3_shape = x => x.gain(0.5).velocity(guitarDyna.fast(2)).sound(guitar).adsrOff().unison(voices = 11, spread = 0.05)
  .oscp("decay", guitarDecay).oscp("hptrack", Math.pow(2, -3 / 12)).oscp("hpq", 1.1)
  .oscp("low", 1.5).oscp("lowHz", 750).oscp("lowQ", 0.6)
  .oscp("mid", 2.5).oscp("midHz", 1350).oscp("midQ", 0.7)
  .oscp("high", 1.25).oscp("highHz", 2800).oscp("highQ", 0.6)
  .hpf(180)
  .clip(guitarClip.fast(2)).pan(1.0).body(material = "rosewood", wet = 0.3)

export guitar3_arrange = x => x.orbit(3)  // . solo()
  .scale("<e2:minor>").postgain(0.170).mute("<0!128 1!16 0!16>") //.mute()
  .late(berlin.range(0.0000, 0.0004).mul(drunk).seg(4))

export guitar3 = n(guitar3_pat).struct("<[x!16]!7 [x!24]!1 [x!16]!16>").apply(guitar3_shape).tag("guitar3")

// Bass  ------------------------------------------------------------------------------------------------------------------------------------------------------
export bass_pat =
  `<[0 0 2 4 0 0 -2 -1]!3 [0 0 2 4 0 0 5 6]
    [0 0 2 4 0 0 -2 -1]!2 [0 0 -1 3  7 0 -2 -1]!1 [0 0 3 [0 -1]  0 0 [0 2 4 6] 9]!1>/8`

// TODO: remove grind from the bass and add eq
export bass_shape = x => x.gain(1.0).velocity("0.98 0.96 0.97 0.96".fast(2)).sound(bass).postgain(0.37) // . mute()
    .oscp("sub", 1.0).oscp("harmonics", 1.00)  // . solo()
    .adsr(0.003, 0.3, 0.5, 0.020).hpf(25)

export bass_arrange = x => x.orbit(3) // . mute()
  .scale("e1:minor").notch(freq = snareHz, q = 1.0).mute("<0!128 1!32>")
  .pan(0.5).clip("<[0.85 0.75 0.65 0.75]>*4".sub(perlin.range(0.0, 0.05)))
  .late(berlin.range(0.0002, 0.0005).mul(drunk).seg(4))

export bass = n(bass_pat).struct("<[x!2]!16 [x@2 x@2]!16 [x x@2 x]!16 [x!4]!12 [x!8]!2 [[x x] x!3]!2>").fast(2).apply(bass_shape).tag("bass")

// Drums  -----------------------------------------------------------------------------------------------------------------------------------------------------
export kick_pat = `<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd  ~ bd  ~]!32 [bd!4]!16 [bd ~ bd [~ bd]]!15 [bd!16]!1>`
export kick_shape = x => x.n(0).gain(0.30).velocity("0.98 0.94 0.96 0.94").pan(0.5)
  .hpf(freq = 35).lpf(9000).adsr(0.001, 0.035, 0.50, 0.25).distort(0.02)
  .superimpose(x => x.bpf(freq = "90", q = 1.0).vel(0.5))
export kick_arrange = x => x.orbit(5).mute("<0!128 1!32>").late(berlin.range(0.0000, 0.0005).mul(drunk).seg(4)) // .mute()
export kick = sound(kick_pat).apply(kick_shape).tag("kick")  //. solo()

export snare_pat = `<[~!2]!2  [~!4]!2  [~!8]!2  [~!16]  [~!24]  [~  sd  ~ sd]!15 [[~ sd] sd  [[~ sd] sd] [sd!4]] [~  sd  ~ sd]!16 [~ sd ~ sd]!32>`
export snare_shape = x => x.n(5).gain(0.40).pan(0.65)
  .hpf(200).lpf(freq = 14500, q = 0.6).adsr(0.001, 0.050, 0.50, 0.50) //c. mute()
  .superimpose(x => x.bpf(freq = pure(snareHz).add(berlin.mul(10).slow(4)), q = 2.0).vel(0.25))
export snare_arrange = x => x.orbit(5).mute("<0!128 1!32>").late(berlin.range(0.0010, 0.0015).mul(drunk).seg(4))
export snare = sound(snare_pat).apply(snare_shape).tag("snare") //.solo()

export hats_pat = `<[hh hh hh hh]!16 [hh hh oh hh]!24 [cr hh cr hh]!24 [~ rd ~ rd]!32>`
export hats_shape = x => x.gain(0.27).pan(0.35)
  .hpf(600).lpf(freq = "13500".add(perlin.mul(50).fast(4)), q = 0.5).adsr(0.003, 0.05, 0.80, 1.0)
export hats_arrange = x => x.orbit(7).mute("<0!128 1!32>").late(berlin.range(0.0015, 0.0025).mul(drunk).seg(4))
export hats = sound(hats_pat).fast(2).apply(hats_shape).velocity("<1.0 0.85 0.93 0.85>*4".sub(berlin.range(0.0, 0.05).slow(4))).tag("hats")

export clap_pat = `<[rim rim ~ ~  ~ ~ ~ rim] [rim rim ~ ~  rim ~ ~ rim] [rim [rim!2]  rim [rim!2]]>`
export clap_shape = x => x.gain(0.10).pan(0.3).superimpose(pan(0.7)) // . mute()
  .hpf("400".add(perlin.range(0, 100).slow(4))).lpf("6500")
export clap_arrange = x => x.orbit(8).mute("<0!128 1!32>")
export clap = sound(clap_pat).apply(clap_shape).tag("clap")

export shaker_pat = `<pink ~ pink ~ pink ~ pink>*8`
export shaker_shape = x => x.gain(0.08).velocity("<1.0 0.90 0.95 0.90>*16")  // . mute()
  .hpf(freq = 3000, q = 0.7) //  . solo()
  .pan(0.5).adsr(0.010, 0.08, 0.0, 0.01)
export shaker_arrange = x => x.orbit(9).late(berlin.range(0.0010, 0.0020).mul(drunk))
export shaker = sound(shaker_pat).apply(shaker_shape).tag("shaker")

// Count-in  --------------------------------------------------------------------------------------------------------------------------------------------------
export countin = sound("oh!2").apply(hats_shape).velocity(0.5).tag("countin").room("0.1")
export countin_arrange = x => x.orbit(7).filterWhen(t => t < 2)

// Song  ------------------------------------------------------------------------------------------------------------------------------------------------------
export song_arrange = x => x.late(2).filterWhen(t => t >= 2) // shift the song one cycle to make room for the count-in

export song_body = stack(
  stack(
    // Lead - Inspired by: Editors - Papillon    
    lead.apply(lead_arrange).room(wet = 0.10, size = 1.0) // . mute() // .solo()
    // Guitars    
    , stack(
      // Guitar 1
      guitar1.apply(guitar1_arrange) // .solo() .mute()
      , // Guitar 2
      guitar2.apply(guitar2_arrange) // .solo() .mute()
      , // Guitar 3
      guitar3.apply(guitar3_arrange) // .solo() .mute()
    ).room(wet = 0.20, size = 2.0).compressor(-21, 3, 6, 0.005, 0.12)
    , // Bass
    bass.apply(bass_arrange) // .solo() // .mute()
  ).analog(feel).transpose(transposition).compressor(-21, 3, 6, 0.005, 0.12)
  , // Drums
  stack(
    kick.apply(kick_arrange),     // .solo() .mute()
    snare.apply(snare_arrange),   // .solo() .mute()
    hats.apply(hats_arrange),     // .solo() .mute()
    clap.apply(clap_arrange),     // .solo() .mute()
    shaker.apply(shaker_arrange)  // .solo() .mute()
  ).analog(feel / 2).room(wet = 0.20, size = 3.0).compressor(-21, 4, 6, 0.005, 0.12)  //. solo() //  .mute()
).seed(timeOfDay.mul(60*60*60*24)).shuffle("<1!80 2!48 1!112 2!32>").swingBy(0.005, 4)

export song = stack(
  // Count-in: four open hats at half the song's hat speed
  countin.apply(countin_arrange)
  , // Song body
  song_body.apply(song_arrange)
  , // Master
  master(Master(m =>
    m.reverb(r => r.wet(0.2).damp(0.8).roomSize(7).roomLp(1500)).gain(2.6)
  ))
)









    """,
)
