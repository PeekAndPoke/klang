/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
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
    code = """
import * from "stdlib"
import * from "sprudel"

let drive = 4 // <--- Do not put 11
let feel  = 10

let stay = 64
let tp = "[0 1 3 -2 -4 -10 -3 2]/8".slow(stay) // <---- transposition ... wait for it ... or change it ... NEVER try -12!

let guitar = (() => {

  let pSpread     = Osc.param("spread", 0.05, "Supersaw voice detuning")
  let pAnalog     = Osc.param("analog", 3.50, "Analog pitch drift")
  let pVoices     = Osc.param("voices", 7,    "Number of unison voices")
 
  let pDrive      = Osc.param("drive",         1.0,    "Primary distortion drive level")
  let pBrightness = Osc.param("brightness", 5000.0,    "Post-distortion lowpass cutoff in Hz")
  let pAttack     = Osc.param("attack",         0.008, "Attack time in seconds")
  let pSustain    = Osc.param("sustain",        0.2,   "Sustain level")

  let signal = Osc.supersaw(freq = Osc.freq(), voices = pVoices, spread = pSpread).analog(pAnalog).mul(0.10)
    // Zawtooth overtones for more grit
    .plus(Osc.superramp(freq = Osc.freq().mul(2), voices = pVoices, spread = pSpread).analog(pAnalog).mul(0.06))
   
  return signal
    .lowpass(Osc.sine(0.50).plus(1).times(1000).plus(pBrightness), 1.50)            // Pre-distortion: sweeping lowpass adds dynamic character
    .plus(Osc.berlin(4.0).highpass(2000).adsr(pAttack, 0.05, 0.0, 0.005).mul(0.3))  // Noise burst
    .bandpass(700, 0.30)                                                            // Gentle mid-focus before distortion
    .distort(pDrive, "tube", 8)                                                     // Overdrive + Oversample
    .lowpass(pBrightness, 1.8, pAnalog)                                             // Post-distortion: control fizz + warmth roll-off
    .highpass(Osc.freq(), 0.7, pAnalog)                                             // Cut away muddy low frequencies
    .coarse(2)
    .adsr(pAttack, 8.0, pSustain, 0.07).adsrCurves("exp", "exp", "exp")             // Tight rhythm envelope
   
})()

stack( // Gitarre! ----------------------------------------------------------------------------
  morse("Gitarre!").n("-5").scale("c4:chromatic").sound("tri").clip(0.5).orbit(7).fast(2).transpose(tp)
    .gain(0.8).distort(1).warmth(0.3).postgain("0.25 0.10 0.15 0.25".slow(stay)).hpf(1800).lpf(2450).lpe(2).lpq(1.5).pan(0.5) // .solo()
  ,// Melody 1 ---------------------------------------------------------------------------------
  n(`<   [0 0 0 7] [0 5 0 2] [0 3 0 5] [0 3 0 0]  [ 0 0 0 7] [0  5 0 8] [0 7 0 5] [ 0 7 0 0]
         [0 0 0 7] [0 5 0 2] [0 3 0 5] [0 3 0 0]  [12 0 0 0] [0 10 0 7] [0 8 7 8] [10 8 7@2]>`)
    .orbit(1).fast(4).scale("C3:chromatic").hpf(600).lpf(3100).lpe(1.5).lpq(1.50).clip(0.96) // .solo()
    .s(guitar).oscp("drive", drive * 0.9).oscp("brightness", 6000).oscp("spread", 0.05).postgain(0.080).body("mahogany").bodyMix(0.5)
    .transpose(tp).pan(0.25).superimpose(pan(0.75)).velocity("<[1.0 0.95 0.975 0.95]>").filterWhen(t => t % stay > 16)
  , // Melody 2 --------------------------------------------------------------------------------------------------
  n(`<   [0 0 0 7] [0 5 0 2] [0 3 0 5] [0 3 0 0]  [ 0 0 0 7] [0  5 0  8] [0 7 0 5] [ 0 7 0 0]
         [0 0 0 7] [0 5 0 2] [0 3 0 5] [0 3 0 0]  [12 0 0 0] [0 10 0 7] [0 8 7 8] [10 8 7@2]>`)
    .orbit(2).fast(4).scale("C4:chromatic").hpf(1200).lpf(3200).lpe(1.5).lpq(1.50).clip(0.96).late(0.001)  // . solo()
    .s(guitar).oscp("drive", drive * 0.9).oscp("brightness", 6200).oscp("spread", 0.04).postgain(0.065).body("oak").bodyMix(0.5)
    .transpose(tp).pan(0.10).superimpose(pan(0.90)).velocity("<[1.0 0.95 0.975 0.95]>").filterWhen(t => t % stay > 32)
  , // Rhythm -----------------------------------------------------------------------------------------------------------------
  cat(n(`<[0,7,12]                                [[0,7,12]!3 ~                ~!12]
          [0,7,12]                                [[[8,15,20]@12 [8,15,20]@4]  [10,5|10|10,17|17|22|22]*8]>`).repeat(2),
      n(`<[0 0 0 0 0 0 0 0 0 0 0 8 8 8 8 7]       [0!9 8 8 5 5 5 5 3]
          [0!11 5 8 8 [8,15] [7,14]]              [[[8,15]!4 [8,15]!3 [10,17]] [10,10|10|17|17|17|17|22]*8]>`).repeat(2),
  ).orbit(3).fast(1).scale("C2:chromatic").clip(0.9925).hpf(115).lpf(2950).lpe(1.25).lpq(1.50).postgain(0.11)
    .s(guitar).oscparam("drive", drive).oscp("brightness", 6000).oscp("spread", 0.09).body("cedar").bodyMix(0.5) //  . mute()
    .transpose(tp).pan(0.40).superimpose(pan(0.60).late(0.001)).velocity("<[1.0 0.95 0.975 0.95]>").filterWhen(t => t % stay >= 4) //  .solo()
  , // Bass -----------------------------------------------------------------------------------------------------------------
  cat(n(`<[0]                                     [[0]!3 ~                     ~!12]
          [0]                                     [[[8]@12     [8]@4]          [10]*8]>`).repeat(2),
      n(`<[0 0 0 0 0 0 0 0 0 0 0 8 8 8 8 7]       [0!9 8 8 5 5 5 5 3]
          [0!11 5 8 8 [8] [7]]                    [[7 8 8 8 [8]!3 [10]]          [10]*8]>`).repeat(2),
  ).orbit(4).scale("C2:chromatic").clip(0.95).sound("saw").gain(1.2).distort("0.8:tube:4").analog(1).postgain(0.19)
    .adsr("0.005:0.5:0.2:0.025").lpadsr("0.005:0.1:0.0:0.075").hpf(70).lpf(180).lpe(20.0).lpq(1).velocity("<[1.0 0.95 0.975 0.95]>")
    .pan(0.55).transpose(tp).filterWhen(t => t % stay >= 4)  // .solo()
  , // Noise --------------------------------------------------------------------------------------------------------------
  s("cp cp cp cp").orbit(5).bandf("1800 600 1200 600").gain("0.1") // .solo()
  ,note("a").sound("brown").gain(0.1).hpf(3000).lpf(13500).crush(6).crushos(4) // .solo()
  , // Drums 1 -----------------------------------------------------------------------------------------------
  cat(s(`<[lt,sd]                                 [[lt,sd]!3 ~                ~!12]
          [lt,sd]                                 [[[mt,sd]@12 [lt]@4]        [mt,sd]]>`).repeat(2),
      s(`<[bd bd] [sd bd] [~ bd] [sd bd]          [~ bd] [sd bd]              [~ bd] [sd bd]
          [bd bd] [sd bd] [~ bd] [sd bd]          [~ bd] [sd bd]              [~ bd] sd>`).fast(8).repeat(4)
  ).orbit(6).early(0.002).adsr("0.005:0.2:0.2:0.5").gain(0.60).hpf(90).lpf(11500)
    .superimpose(bandf(195).bandq(1.1).gain(0.2)).filterWhen(t => t % stay >= 3.95)  // .solo()
  , // Drums 1 ------------------------------------------------------------------------------------------------
  s("<[cr hh!7]!7 [cr hh!3 [hh hh] [hh hh] [oh hh] [oh hh]]>")
    .orbit(7).late(0.001).adsr("0.007:0.2:0.9:0.7").gain(0.85).hpf(800).lpf(12500).velocity("<[1.0 0.95 0.975 0.95]>") // .solo()
).analog(feel).room(0.02).rsize(3.0).compressor("-6:3:10:0.02:0.05") /*



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
