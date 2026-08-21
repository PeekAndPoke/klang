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
    rpm = 34.5,
    icon = "bug",
    code = """import * from "stdlib"
import * from "sprudel"

// Song Status: Upcoming Garage Band ...

let feel          =   10    // 0.0 .. guitar | 100.0 .. rave
let transposition =   -2    // -2 .. D | 0 .. E | 2 .. F#
let drunk         =    1    // How many beers did each band member have?
let snareHz       =  210    // Where does the snare cut through?
let leadHz        = 3700    // Where does the lead sit?

let guitar = (() => {

  // --- Overridable params ---------------------------------------------------------------------------------------
  let pVoices  = OscSlot.voices
  let pSpread  = OscSlot.spread
  let pAnalog  = OscSlot.analog

  let pMidsHz     = Osc.param("midsHz",      850.000, "Mids frequency")
  let pMidsQ      = Osc.param("midsQ",         0.707, "Mids Q")
  let pMids       = Osc.param("mids",          1.700, "Mids Volume")

  let pPresenceHz = Osc.param("presenceHz", 2500.000, "Presence frequency")
  let pPresenceQ  = Osc.param("presenceQ",     0.707, "Presence Q")
  let pPresence   = Osc.param("presence",      5.000, "Presence Volume")
  
  let pHpTrack    = Osc.param("hptrack",       1.000, "Highpass cutoff as a multiple of the note frequency")
  let pHpQ        = Osc.param("hpq",           0.707, "Highpass resonance")
  // --------------------------------------------------------------------------------------------------------------

  let signal = Osc.supersaw(freq = Osc.freq(), voices = pVoices, spread = pSpread)
    // enable the phase-pool for consistent onsets and fundamentals
    .phasePool(on = 1, kMin = 0.70, kMax = 0.80, warmup = 0)
    // character knobs — plain scalars, SuperSaw-typed, must precede the filter
    .analog(pAnalog).spreadPower(1.0).sideAtten(0.0).gainJitter(0.20).centerJitter(0.20)
    // Simulate plucked string, add noise burst
    .pitchEnvelope(0.3, 0.001, 0.02)
    .plus(Osc.whitenoise().highpass(2000).adsr(0.000, 0.05, 0.0, 0.005).mul(0.16))
    // Distort
    .distort(0.35, "hard", 4)
   
  return signal.eq()
    .tap(freq = pMidsHz,     q = pMidsQ,     gain = pMids)      // mids: parallel boost off the dry signal
    .tap(freq = pPresenceHz, q = pPresenceQ, gain = pPresence)  // presence: parallel boost off the dry signal
    .notch(cutoffHz = snareHz, q = 4.0)                         // let the snare cut through
    .highpass(cutoffHz = Osc.freq().mul(pHpTrack), q = pHpQ)    // follow freq to avoid low mud
    .lowpass(5200).lowpass(5200)
})()

export guitarDyna = "0.98 0.94!7 0.96 0.94!7"

// Lead - Inspired by: Editors - Papillon  ---------------------------------------------------------------------------------------------------------------------
export lead_pat =  `<[-7 0 2 4] [-7 0 4 [2 6]|[4 2]|2|2|2|2] [-5 -1 2 4] [-4 -1 [4 3]|[5 3]|3|3|3|3 [1 -1]|1|1|1|1]>*2`

export lead_shape = x => x.gain(0.65).sound(guitar).unison(5).spread(0.15)
  .oscp("mids", 0.0).oscp("presence", 0.0).oscp("hptrack", 1.0).bandf(leadHz).bandq(2.5).hpf(leadHz - 300)
  .clip(0.85).adsr("0.010:5.0:0.1:0.25").lpf(leadHz).lpe(1).lpadsr("0.010:1.0:0.1:0.25") 
  .body("steel").bodyMix(0.8).apply(x => x.transpose(0).velocity(0.5).pan(0.33).superimpose(pan(0.66)))

export lead_arrange = x => x.orbit(0)
  .scale("<e4:minor!48 e5:minor!16 e4:minor!48 e3:minor!16>").postgain("<0.500!48 0.215!16 0.500!48 0.375!16>")  
  .shuffle("<1!64 0!16 1!1 4/8!14 1!33>")                                                                            
  .mute("<1!64 0!32 1!32 0!16>")
  .late(berlin.range(0.0005, 0.0015).mul(drunk))

export lead = n(lead_pat).apply(lead_shape).room("0.1:5:0.1").tag("lead")

// Guitar 1  --------------------------------------------------------------------------------------------------------------------------------------------------
export guitar1_pat = 
  `<[0 [0@4 [4 7] -3] -1 <4 3 1> [0 2 4 3] 0 2 <[-1 1 3@2] [[3 4] 6@2 7] [[1 3] 4 3 2] [[6 10 7 5]]>]!4
    [[4 [4 4 2 0] [4 3 2 0] 0] [-1 [1 [<3 2> 1] -1 -4]] [-3!4 -3!8 4 2 4 0] [2 [2 6@3]]]!2
    [[-3,-7] [[-4,-8] [-1,-4]] [0,-3] <[[4 6],[-2 3]] [0,-1]>] [<[7,4] [[7 4 6 0  7 4 2 0]!2]> [2 0 3 0] 0 [[-5 -2 0 2] 4]]>/4`

export guitar1_shape = x => x.gain(0.5).velocity(guitarDyna.fast(2)).sound(guitar).unison(15).spread(0.06) // . solo()
  .oscp("hptrack", 1.00).oscp("hpq", 0.7)
  .oscp("midsHz", 1160).oscp("midsQ", 0.7).oscp("mids", 1.5).oscp("presence", 4.8).oscp("presenceHz", 3300).oscp("presenceQ", 1.8)
  .clip("<0.97!31 0.93 0.97!31 0.92 0.97!30 0.88 0.92>".fast(2)).adsr("0.004:4.0:0.0:0.010")
  .pan(0.55).superimpose(pan(0.65)).body("oak").bodyMix(0.3)

export guitar1_arrange = x => x.orbit(1)
  .scale("<e3:minor!48 e4:minor!16 e3:minor!48 e4:minor!16>").postgain(0.105)
  .late(berlin.range(0.0003, 0.0008).mul(drunk))

export guitar1 = n(guitar1_pat).struct("<[x!16]!7 [x!24]!1 [x!16]!16>").apply(guitar1_shape).tag("guitar1")

// Guitar 2  --------------------------------------------------------------------------------------------------------------------------------------------------
export guitar2_pat = 
  `<[11 11 9 8  7 7 9 6] [11 11 [13 11] 8   7  7 5  6] [11 11 9 11  7 7 7 8] [11 11 [13  9] 4      7 4         2         3]
    [ 4  4 6 8  4 4 5 6] [ 4  4 6       8  11 11 9 10] [ 4  4 3  6  4 4 2 3] [ 7 11 [ 3  7] [6 7]  [4 4 6 4]!2 [0 3 4 6] 10]>/4`

export guitar2_shape = x => x.gain(0.5).velocity(guitarDyna.fast(2)).sound(guitar).unison(13).spread(0.07)
  .oscp("hptrack", 1.00).oscp("hpq", 1.2)
  .oscp("midsHz", 980).oscp("midsQ", 0.7).oscp("mids", 2.2).oscp("presence", 4.7).oscp("presenceHz", 2700).oscp("presenceQ", 1.8)
  .clip("<0.97!31 0.93 0.97!31 0.92 0.97!30 0.88 0.92>".fast(2)).adsr("0.003:4.0:0.0:0.010")
  .pan(0.45).superimpose(pan(0.35)).body("cedar").bodyMix(0.3)

export guitar2_arrange = x => x.orbit(2)
  .scale("<e2:minor>").postgain(0.105).mute("<0!128 1!16 0!16>")
  .late(berlin.range(0.0000, 0.0007).mul(drunk))

export guitar2 = n(guitar2_pat).struct("<[x!16]!7 [x!24]!1 [x!16]!16>").apply(guitar2_shape).tag("guitar2")

// Guitar 3  --------------------------------------------------------------------------------------------------------------------------------------------------
export guitar3_pat = 
  `<[0 0 2 4 0 0 -2 -1]!4
    [0 0 2 4 0 0 -2 -1]!2 [0 0 -1 3  0 0 -2 -1]!1 [0 0 3 0  0 0 [5 -2 0 2] 6]!1>/4`

export guitar3_shape = x => x.gain(0.5).velocity(guitarDyna.fast(2)).sound(guitar).unison(11).spread(0.08)
  .oscp("hptrack", 1.00).oscp("hpq", 1.4)
  .oscp("midsHz", 700).oscp("midsQ", 0.7).oscp("mids", 3.2).oscp("presence", 4.6).oscp("presenceHz", 2300).oscp("presenceQ", 1.8)
  .clip("<0.97!31 0.93 0.97!31 0.92 0.97!30 0.88 0.92>".fast(2)).adsr("0.003:4.0:0.0:0.010")
  .pan(0.40).superimpose(pan(0.60))

export guitar3_arrange = x => x.orbit(2)
  .scale("<e2:minor>").postgain(0.105).mute("<0!128 1!16 0!16>")
  .late(berlin.range(0.0000, 0.0006).mul(drunk))

export guitar3 = n(guitar3_pat).struct("<[x!16]!7 [x!24]!1 [x!16]!16>").apply(guitar3_shape).tag("guitar3")

// Bass  ------------------------------------------------------------------------------------------------------------------------------------------------------
export bass_pat = 
  `<[0 0 2 4 0 0 -2 -1]!4
    [0 0 2 4 0 0 -2 -1]!2 [0 0 -1 3  0 0 -2 -1]!1 [0 0 3 0  0 0 [0 3 4 6] 2]!1>/8`

export bass_shape = x => x.gain(1.0).velocity("0.98 0.96 0.97 0.96".fast(2)).sound("sine").postgain(0.065).clip(0.70)
  .adsr("0.007:4.0:0.5:0.010").lpadsr("0.000:0.03:0.0:0.01").hpf(25)
  .superimpose(x => x.lpf(200).lpe(24).distort("0.5:tube:4").hpf(60).postgain(0.030).pan(0.45).superimpose(pan(0.55)))

export bass_arrange = x => x.orbit(3)
  .scale("e1:minor").notchf(snareHz).notchq(1.0).mute("<0!128 1!32>")
  .late(berlin.range(0.0000, 0.0005).mul(drunk))

export bass = n(bass_pat).struct("<[x!2]!16 [x!2 [x x] x]!16 [[x x] x!3]!32>").fast(2).apply(bass_shape).tag("bass")

// Drums  -----------------------------------------------------------------------------------------------------------------------------------------------------
export kick_pat = `<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd  ~ bd  ~]!32 [bd!4]!16 [bd ~ bd [~ bd]]!15 [bd!]!1>`
export kick_shape = x => x.n(0).gain(0.200).velocity("0.98 0.96 0.97 0.96").pan(0.5)
  .hpf(30).hpq(1).lpf(8000).adsr("0.001:0.05:0.95:0.75").distort(0.1)
  .superimpose(x => x.bandf("75").bandq(5.0).vel(0.80))
export kick_arrange = x => x.orbit(5).mute("<0!128 1!32>").late(berlin.range(0.0000, 0.0007).mul(drunk))
export kick = sound(kick_pat).apply(kick_shape).tag("kick")

export snare_pat = `<[~!2]!2  [~!4]!2  [~!8]!2  [~!16]  [~!24]  [~  sd  ~ sd]!32 [~ sd ~ sd]!32>`
export snare_shape = x => x.n(5).gain(0.255).pan(0.5)
  .hpf(80).lpf(15000).lpq(0.6).adsr("0.001:0.05:0.95:0.75")
  .superimpose(x => x.bandf(pure(snareHz).add(berlin.mul(5).fast(4))).bandq(4.0).vel(0.80))
export snare_arrange = x => x.orbit(5).mute("<0!128 1!32>").late(berlin.range(0.0010, 0.0020).mul(drunk))
export snare = sound(snare_pat).apply(snare_shape).tag("snare")

export hats_pat = `<[hh hh hh hh]!16 [hh hh oh hh]!24 [cr hh cr hh]!24 [~ rd ~ rd]!32>`
export hats_shape = x => x.gain(0.245).pan(0.50)
  .hpf(700).lpf("13500".add(perlin.mul(250).fast(4))).lpq(0.7).adsr("0.003:0.05:0.95:1.5")
export hats_arrange = x => x.orbit(7).mute("<0!128 1!32>").late(berlin.range(0.0020, 0.0030).mul(drunk))
export hats = sound(hats_pat).fast(2).apply(hats_shape).velocity("<1.0 0.85 0.93 0.85>*4")tag("hats")

export clap_pat = `<~!79 [~ ~ ~ cp  cp ~ cp ~] ~!47 [~ ~ ~ cp  cp ~ cp ~]>`
export clap_shape = x => x.gain(0.062).pan(0.3).superimpose(pan(0.7))
export clap_arrange = x => x.orbit(8).mute("<0!128 1!32>")
export clap = sound(clap_pat).apply(clap_shape).tag("clap")

export shaker_pat = `<pink ~ pink pink>*16`
export shaker_shape = x => x.gain(0.135).velocity("<1.0 0.90 0.95 0.90>*16")
  .hpf(8000).hpq(0.5).lpf(17000).lpq(0.7)
  .pan(sine.range(0.35, 0.65).slow(8)).adsr("0.010:0.15:0.0:0.01")
export shaker_arrange = x => x.orbit(9).late(berlin.range(0.0025, 0.0035).mul(drunk))
export shaker = sound(shaker_pat).apply(shaker_shape).tag("shaker")

// Count-in  --------------------------------------------------------------------------------------------------------------------------------------------------
export countin = sound("oh!2").apply(hats_shape).velocity(0.5).tag("countin").room("0.1")
export countin_arrange = x => x.orbit(7).filterWhen(t => t < 2)

// Song  ------------------------------------------------------------------------------------------------------------------------------------------------------
export song_arrange = x => x.late(2).filterWhen(t => t >= 2) // shift the song one cycle to make room for the count-in

export song_body = stack(
  stack(
    // Lead - Inspired by: Editors - Papillon       
    lead.apply(lead_arrange) // .solo()
    , // Guitars       
    stack(
      // Guitar 1
      guitar1.apply(guitar1_arrange) // .solo() .mute()
      , // Guitar 2
      guitar2.apply(guitar2_arrange) // .solo() .mute()
      , // Guitar 3
      guitar3.apply(guitar3_arrange) // .solo() .mute()
    ).room(0.20).rsize(3.0).rlp(3000).compressor("-26:2:6:0.005:0.15")
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
  ).analog(feel / 2).room(0.20).rsize(3.0).rlp(8000).compressor("-28:2:6:0.005:0.15") // .mute()
).seed(timeOfDay.mul(60*60*60*24)).shuffle("<1!64 2!48 1!96 2!32>").swingBy(0.005, 4)

export song = stack(
  // Count-in: four open hats at half the song's hat speed
  countin.apply(countin_arrange)
  , // Song body
  song_body.apply(song_arrange)
  , // Master
  master(Master.of(MasterFx.reverb().wet(0.05).damp(0.5).roomSize(7), MasterFx.gain(2.5)))
)







    """,
)
