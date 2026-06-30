/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

internal val strangerThingsSong = Song(
    id = "${BuiltInSongs.PREFIX}-stranger-synths",
    title = "Seltsamere Dinge",
    rpm = 34.0,
    icon = "film",
    code = """
import * from "stdlib"
import * from "sprudel"

let wait = 16
let keep = 32 * 6
let notch = sine.range(1 * 440, 3 * 440).slow(16) // 440, 880, 1560 ?

stack(
  // Claps --------------------------------------------------------------------------------------------------------------------
  sound("cp ~ cp ~ ~ cp cp ~  cp ~ ~ ~ cp cp ~ ~").slow(4).orbit(0).gain(0.275).legato(2.0)
    .bandf(sine.range(2000, 2200).fast(3.14)).hpf(800)
    .filterWhen(x => x >= wait * 8 && x < (wait * 12 + keep))
  , // Lyrics ---------------------------------------------------------------------------------------------------------------------------
  n("0").morse("Schön ist es auf der Welt zu sein!").orbit(0)
    .scale("C5:major").scaleTranspose("0 -2 2 2".slow(32)).bandf(2000).bandq(7.0).hpf(1000).analog(2)
    .sound("pulse").warmth(0.75).crush(5).gain(0.05).clip(0.35).pan(berlin.slow(2)).adsr("0.03:0.08:0.2:0.1") // .solo()
    .filterWhen(x => x >= wait * 12 && x < (wait * 6 + keep)).body("membrane")
  , // Melody -----------------------------------------------------------------------------------------------------------------
  n("<[0 2 4 6 7 6 4 2]!14 [0 -1 0 4 6 9 7 6] [-2 -1 0 2 7 4 -1 -3]>") // .solo()
    .scale("[c3:major c3:pentatonic c3:major c3:major]/16")
    .orbit(1).s("supersaw").unison(15).spread(saw.range(0.05, 0.35).slow(16))
    .gain(0.6).distort(1.0).postgain(0.10).adsr("0.005:2.0:0.5:0.1").lpadsr("0.005:5.0:0.5:0.1").clip(1.1)
    .pan(0.5) // . solo()
    .hpf(400).lpf(1200).lpenv(perlin.range(2.0, 3.5).lpq(3.0).slow(8)).analog(5).body("wood")
    .superimpose(x =>
      x.hpf(800).lpf(1500).lpq(5).bandf(notch).bandq(1.0).transpose(12).postgain(0.06).pan(0.2).superimpose(pan(0.8)).body("glass")
    ).filterWhen(x => x >= wait * 4 && x < (wait * 4 + keep)) // . solo()
  , // Bass -----------------------------------------------------------------------------------------------------------------------------
  note("<a1 [f1 c2 e1 [f1 c2]] [a1 [c2 f1] a1 [f1@3 e1]] [a1@2 [c2@3] [d1,d2] [c2,c3] [d1,d1,d2,a2]]>/4").clip(0.7).struct("x!4").slow(16)
    .orbit(2).s("supersaw").unison(9).spread(saw.range(0.05, 0.45).slow(64)).warmth(0.01) // . mute()
    .gain(1.0).adsr("0.01:0.6:0.8:2.75").postgain(0.50).coarse(2).coarseos(2) // solo()
    .superimpose(
      x => x.orbit(3).scaleTranspose("<[12 12 7 12 12 [12 12] 0 -12] [12 12 0 12 12 [0 12] 0 -12]>/32")
        .pan(sine.range(0.15, 0.8).slow(32)).clip(0.825)
    ).lpf(4.5 * 440).lpq(2.5).hpf(60).notchf(notch).notchq(0.75).body("glass").vowel("i a e".slow(12)).vowelMix(0.2)
    .superimpose(
      x => x.gain(saw.range(0.2, 1.0).slow(64).pow(1.25).mul(2.0)).vibrato("0.51".add(perlin.div(10))).vibmod(0.05)
        .crush("1.85".add(berlin2.mul(0.5).slow(4))).crushos(2).lpf(5.5 * 440).hpf(300).postgain(0.45)
        .pan(0.2).superimpose(pan(0.8))                
    ).velocity(cat(saw.range(0.25, 1.0).pow(1.5).slow(32), pure(1).slow(256)).mul("1 0.95 0.975 0.95".fast(2)))
    .analog(10).filterWhen(x => x < (wait * 4 + keep))
  , // Perc 2 ------------------------------------------------------------------------------------------------------------------
  sound("<[hh hh oh hh] [hh hh ~ hh] [hh hh oh hh] [hh hh ~ <cr!7 rd>]>")
    .orbit(4).gain(0.5).pan(0.4).adsr("0.01:0.15:0.8:2.0").fast(2).degrade(0.1).lpf(6800).late(0.001)
    .filterWhen(x => x >= wait * 1 && x < (wait * 2 + keep))
  , // Perc 1 -----------------------------------------------------------------------------------------------------------------------
  sound("[bd bd bd ~  bd ~ bd ~] [bd bd sd:5 ~  bd ~ bd|sd:5 ~]").slow("[8 8 8 8 8 8 4 [2 4]]/32").fast(2)
    .orbit(5).gain(0.7).pan(0.5).adsr("0.02:0.3:0.5:1").degrade(0.01).hpf(120).lpf(7500)
    .filterWhen(x => x >= wait * 1.75 && x < (wait * 1 + keep))
  , // Shore ---------------------------------------------------------------------------------------------------------
  note("c").fast(7).sound("brown")
    .orbit(0).gain(0.12).pan(perlin.early(1.7).range(0.3, 0.7).slow(7)).adsr("0.2:1.0:1.0:2.5")
    .bandf(perlin.range(440, 440 * 4).segment(16).slow(48)).bandq(sine.range(0.25, 5.0).slow(48).early(12))
  ,
).delay("0.2::0.5").delaytime(pure(1/8).div(cps)).room("0.1:10.0").compressor("-15:2:6:0.01:0.2")










        """,
)
