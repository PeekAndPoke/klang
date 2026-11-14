/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

internal val aTruthWorthLyingForSong = Song(
    id = "${BuiltInSongs.PREFIX}-a-synth-worth-lying-for",
    title = "A Synth Worth Lying For",
    rpm = 31.0,
    icon = "guitar",
    code = """import * from "stdlib"
import * from "sprudel"

let drive =  4 // <--- Do not put 11
let feel  = 10 // analog feel

let stay  = 64
let tp    = "[0 1 -2 -3 -5 -10 -3 3]/8".slow(stay) // <---- transposition ... wait for it ... or change it ... NEVER try -12!

let guitar = (() => {

  let pSpread     = Ign.param("spread",        0.05, "Supersaw voice detuning")
  let pAnalog     = Ign.param("analog",        3.50, "Analog pitch drift")
  let pVoices     = Ign.param("voices",       21,    "Number of unison voices")
 
  let pDrive      = Ign.param("drive",         1.000, "Primary distortion drive level")
  let pBrightness = Ign.param("brightness", 5000.000, "Post-distortion lowpass cutoff in Hz")
  let pAttack     = Ign.param("attack",        0.008, "Attack time in seconds")
  let pSustain    = Ign.param("sustain",       0.200, "Sustain level")

  let signal = Ign.supersaw(x => x.voices(pVoices).spread(pSpread).analog(pAnalog)).mul(0.10)
    // Zawtooth overtones for more grit
    .plus(Ign.superramp(Ign.freq().mul(2), x => x.voices(pVoices).spread(pSpread).analog(pAnalog)).mul(0.06))
   
  return signal.oversampleAtLeast(4, r => r
    .lowpass(Ign.sine(0.50).plus(1).times(1000).plus(pBrightness), 1.50)            // Pre-distortion: sweeping lowpass adds dynamic character
    .plus(Ign.berlin(4.0).highpass(2000).adsr(pAttack, 0.05, 0.0, 0.005).mul(0.3))  // Noise burst
    .bandpass(700, 0.30)                                                            // Gentle mid-focus before distortion
    .distort(pDrive, "tube")                                                        // Overdrive
    .lowpass(pBrightness, 1.8, x => x.analog(pAnalog))                              // Post-distortion: control fizz + warmth roll-off
    .highpass(Ign.freq(), 0.7, x => x.analog(pAnalog))                              // Cut away muddy low frequencies
    .coarse(2)
    .adsr(pAttack, 8.0, pSustain, 0.07, e => e.curves("exp", "exp", "exp"))         // Tight rhythm envelope
    // cabinett
    .lowpass(5500).lowpass(5500)
  ).classic()
})()

stack( // Gitarre! ------------------------------------------------------------------------------------------------------------------------------------------
  morse("Gitarre!").n("-5").scale("c4:chromatic").sound("tri").clip(0.5).cylinder(7).fast(2).transpose(tp)
    .distort(1).gain("0.35 0.20 0.25 0.35".slow(stay).mul(0.8)).hpf(1800).lpf(freq = 2450, env = 19, q = 1.5).pan(0.5) // .solo()
  ,// Melody 1 ----------------------------------------------------------------------------------------------------------------------------------------------
  n(`<   [0 0 0 7] [0 5 0 2] [0 3 0 5] [0 3 0 0]  [ 0 0 0 7] [0  5 0 8] [0 7 0 5] [ 0 7 0 0]
         [0 0 0 7] [0 5 0 2] [0 3 0 5] [0 3 0 0]  [12 0 0 0] [0 10 0 7] [0 8 7 8] [10 8 7@2]>`)
    .cylinder(1).fast(4).scale("C3:chromatic").hpf(freq = 400).clip(0.96)
    .late(perlin(0.001, 0.002).seed(1)) // .solo()
    .s(guitar).adsrOff().ignp("drive", drive * 0.9).ignp("brightness", 6000).ignp("spread", 0.05).gain(0.125).body(material = "mahogany", wet = 0.5)
    .transpose(tp).pan(0.25).superimpose(pan(0.75)).velocity("<[1.0 0.95 0.975 0.95]>").filterWhen(t => t % stay > 16)
  , // Melody 2 ---------------------------------------------------------------------------------------------------------------------------------------------
  n(`<   [0 0 0 7] [0 5 0 2] [0 3 0 5] [0 3 0 0]  [ 0 0 0 7] [0  5 0  8] [0 7 0 5] [ 0 7 0 0]
         [0 0 0 7] [0 5 0 2] [0 3 0 5] [0 3 0 0]  [12 0 0 0] [0 10 0 7] [0 8 7 8] [10 8 7@2]>`)
    .cylinder(2).fast(4).scale("C4:chromatic").hpf(freq = 800).clip(0.96)
    .late(perlin(0.001, 0.002).seed(2))  // . solo()
    .s(guitar).adsrOff().ignp("drive", drive * 0.9).ignp("brightness", 6200).ignp("spread", 0.04).gain(0.120).body(material = "oak", wet = 0.5)
    .transpose(tp).pan(0.10).superimpose(pan(0.90)).velocity("<[1.0 0.95 0.975 0.95]>").filterWhen(t => t % stay > 32)
  , // Rhythm -----------------------------------------------------------------------------------------------------------------------------------------------
  cat(n(`<[0,7,12]                                [[0,7,12]!3 ~                ~!12]
          [0,7,12]                                [[[8,15,20]@12 [8,15,20]@4]  [10,5|10|10,17|17|22|22]*8]>`).repeat(2),
      n(`<[0 0 0 0 0 0 0 0 0 0 0 8 8 8 8 7]       [0!9 8 8 5 5 5 5 3]
          [0!11 5 8 8 [8,15] [7,14]]              [[[8,15]!4 [8,15]!3 [10,17]] [10,10|10|17|17|17|17|22]*8]>`).repeat(2),
  ).cylinder(3).fast(1).scale("C2:chromatic").clip(0.9925).hpf(freq = 120, passes = 2).gain(0.15)
    .late(perlin(0.000, 0.001).seed(3))
    .s(guitar).adsrOff().ignitorParam("drive", drive).ignp("brightness", 6000).ignp("spread", 0.09).body(material = "cedar", wet = 0.5) //  . mute()
    .transpose(tp).pan(0.40).superimpose(pan(0.60).late(0.001)).velocity("<[1.0 0.95 0.975 0.95]>").filterWhen(t => t % stay >= 4) //  .solo()
  , // Bass -------------------------------------------------------------------------------------------------------------------------------------------------
  cat(n(`<[0]                                     [[0]!3 ~                     ~!12]
          [0]                                     [[[8]@12     [8]@4]          [10]*8]>`).repeat(2),
      n(`<[0 0 0 0 0 0 0 0 0 0 0 8 8 8 8 7]       [0!9 8 8 5 5 5 5 3]
          [0!11 5 8 8 [8] [7]]                    [[[8]!4 [8]!3 [10]]          [10]*8]>`).repeat(2),
  ).cylinder(4).scale("C2:chromatic").clip(0.95).sound("saw").gain(0.40).distort(0.2, "tube").analog(1)
    .adsr(0.005, 5.0, 0.0, 0.025).lpf(freq = 180, attack = 0.005, decay = 0.1, sustain = 0.0, release = 0.075, env = 10).hpf(70)
    .velocity("<[1.0 0.95 0.975 0.95]>")
    .pan(0.50).transpose(tp).filterWhen(t => t % stay >= 4)  // .solo()
  , // Noise ------------------------------------------------------------------------------------------------------------------------------------------------
  s("cp cp cp cp").cylinder(5).bpf("1800 600 1200 600").gain("0.1") // .solo()
  ,note("a").sound("brown").gain(0.1).hpf(3000).lpf(13500).crush(bits = 6) // .solo()
  , // Drums 1 ----------------------------------------------------------------------------------------------------------------------------------------------
  cat(s(`<[lt,sd]                                 [[lt,sd]!3 ~                ~!12]
          [lt,sd]                                 [[[mt,sd]@12 [lt]@4]        [mt,sd]]>`).repeat(2),
      s(`<[bd bd] [sd bd] [~ bd] [sd bd]          [~ bd] [sd bd]              [~ bd] [sd bd]
          [bd bd] [sd bd] [~ bd] [sd bd]          [~ bd] [sd bd]              [~ bd] sd>`).fast(8).repeat(4)
  ).cylinder(6).early(0.002).adsr(0.005, 0.1, 0.9, 1.0).gain(0.45).hpf(90).lpf(13500)
    .reverb(wet = 0.25, size = 4.0)
  , // Drums 1 ----------------------------------------------------------------------------------------------------------------------------------------------
  s("<[cr hh!7]!7 [cr hh!3 [hh hh] [hh hh] [oh hh] [oh hh]]>")
    .cylinder(7).late(0.001).adsr(0.005, 0.1, 0.9, 1.0).gain(0.80).hpf(800).lpf(15000).velocity("<[1.0 0.95 0.975 0.95]>")
    .reverb(wet = 0.25, size = 4.0)// .solo()
  , // Master -----------------------------------------------------------------------------------------------------------------------------------------------
    master(Katalyst(k => k.oversampleAtLeast(4, r => r
    .reverb(0.10, 7, 6500)
    .bands(b => b
      .band(low => low.distort(0.01, "soft").mul(1.00)).cut(150)
      .band(mid => mid.distort(0.10, "soft").mul(0.90)).cut(5000)
      .band(low => low.distort(0.01, "soft").mul(1.05))
    ) // the glue on the mids only: the kick and the hats stay clean
    .gain(1.2)
    .limiter(threshold = -3.0, ratio = 20.0, knee = 2.0, attack = 0.005, release = 0.10, lookahead = 0.005)
  )))


).analog(feel) /*



░▒▓████████▓▒░▒▓█▓▒░░▒▓█▓▒░▒▓████████▓▒░    ░▒▓█▓▒░░▒▓█▓▒░░▒▓██████▓▒░░▒▓█▓▒░      ░▒▓██████▓▒░     ░▒▓████████▓▒░▒▓████████▓▒░▒▓████████▓▒░▒▓████████▓▒░▒▓██████▓▒░▒▓████████▓▒░
   ░▒▓█▓▒░   ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░           ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░     ░▒▓█▓▒░░▒▓█▓▒░    ░▒▓█▓▒░      ░▒▓█▓▒░      ░▒▓█▓▒░      ░▒▓█▓▒░     ░▒▓█▓▒░░▒▓█▓▒░ ░▒▓█▓▒░
   ░▒▓█▓▒░   ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░           ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░     ░▒▓█▓▒░░▒▓█▓▒░    ░▒▓█▓▒░      ░▒▓█▓▒░      ░▒▓█▓▒░      ░▒▓█▓▒░     ░▒▓█▓▒░        ░▒▓█▓▒░
   ░▒▓█▓▒░   ░▒▓████████▓▒░▒▓██████▓▒░      ░▒▓████████▓▒░▒▓████████▓▒░▒▓█▓▒░     ░▒▓█▓▒░░▒▓█▓▒░    ░▒▓██████▓▒░ ░▒▓██████▓▒░ ░▒▓██████▓▒░ ░▒▓██████▓▒░░▒▓█▓▒░        ░▒▓█▓▒░
   ░▒▓█▓▒░   ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░           ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░     ░▒▓█▓▒░░▒▓█▓▒░    ░▒▓█▓▒░      ░▒▓█▓▒░      ░▒▓█▓▒░      ░▒▓█▓▒░     ░▒▓█▓▒░        ░▒▓█▓▒░
   ░▒▓█▓▒░   ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░           ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░     ░▒▓█▓▒░░▒▓█▓▒░    ░▒▓█▓▒░      ░▒▓█▓▒░      ░▒▓█▓▒░      ░▒▓█▓▒░     ░▒▓█▓▒░░▒▓█▓▒░ ░▒▓█▓▒░
   ░▒▓█▓▒░   ░▒▓█▓▒░░▒▓█▓▒░▒▓████████▓▒░    ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░▒▓████████▓▒░▒▓██████▓▒░     ░▒▓████████▓▒░▒▓█▓▒░      ░▒▓█▓▒░      ░▒▓████████▓▒░▒▓██████▓▒░  ░▒▓█▓▒░




                                             https://open.spotify.com/intl-de/track/58Hx7vKWjxuQyZ9XgUh3Wl?si=9c254ec279fe47f3





*/
    """,
)
