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
let notch = sine.range(1 * 440, 3 * 440).slow(12) // 440, 880, 1560 ?

stack(
  // Claps --------------------------------------------------------------------------------------------------------------------
  sound("cp ~ cp ~ ~ cp cp ~  cp ~ ~ ~ cp cp ~ ~").slow(4).orbit(0).gain(0.35).legato(2.0)
    .bandf(sine.range(1700, 1800).fast(3.14)).hpf(800)
    .filterWhen(x => x >= wait * 8 && x < (wait * 12 + keep))
  , // Lyrics ---------------------------------------------------------------------------------------------------------------------------
  n("0").morse("Schön ist es auf der Welt zu sein!").orbit(0).analog(5)
    .scale("C5:major").scaleTranspose("0 -2 2 2".slow(32)).bandf(1800).bandq(5.0).hpf(1800).lpf(2000).lpe(0.3).lpq(3)
    .sound("pulse").warmth(0.75).crush(5).gain(0.04).clip(0.35).pan(berlin.slow(2)).adsr("0.03:0.08:0.2:0.1") // .solo()
    .filterWhen(x => x >= wait * 12 && x < (wait * 6 + keep))
  , // Melody -----------------------------------------------------------------------------------------------------------------
  n("<[0 2 4 6 7 6 4 2]!14 [0 -1 0 2 4 2 6 7] [-1 0 2 7 4 2 0 -1]>")
    .scale("[c3:major c3:pentatonic c3:major c3:major]/16").velocity("1.00 0.93!7 0.96 0.93!7".fast(2))
    .orbit(1).s("supersaw").unison(5).detune(saw.range(0.03, 0.25).slow(64))
    .gain(0.6).distort(0.25).warmth(0.02).postgain(0.3).adsr("0.03:0.6:0.5:0.155")
    .pan(sine.range(0.3, 0.7).slow(16)).vibrato(2).vibmod(0.05) // . solo()
    .superimpose(bandf(notch).bandq(1.0).gain(0.125).transpose(12)).body("wood")
    .hpf(600).lpf(1200).lpenv(perlin.range(3.0, 4.5).lpq(8.0).slow(8)).analog(2)
    .filterWhen(x => x >= wait * 4 && x < (wait * 4 + keep)) // . solo()
  , // Bass -----------------------------------------------------------------------------------------------------------------------------
  note("<a1 [f1 c2 e1 [a1 c2]] [a1 [c2 f1] a1 [f1@3 e1]] [a1@2 c2@3 d2 [[c2,g2] [c2,c3]] [a1,d1,a2,d2,f2]]>/4").clip(0.8).struct("x!4").slow(16)
    .orbit(2).s("supersaw").unison(4).detune(saw.range(0.05, 0.40).slow(64)).warmth(0.02)
    .gain(1.0).adsr("0.05:0.6:0.7:1.90").postgain(0.5)
    .superimpose(
      x => x.orbit(3).transpose("<[12 0 12 0 12 7 0 -12] [12 12 0 0 12 7 12 -12]>/32")
        .pan(saw.range(0.5, 0.9).slow(keep * 2)).clip(0.9)
    ).lpf(5.0 * 440).lpq(3.5).hpf(75).hpq(1.0).notchf(notch).notchq(0.75).analog(1).body("glass")
    .superimpose(
      x => x.gain(saw.range(0.1, 1.0).slow(64).pow(1.5).mul(1.8)).vibrato("12.5".add(berlin2)).vibmod(0.15)
        .crush("2.0".add(berlin2.mul(0.75).slow(2))).crushos(4).hpf(300).lpf(6.0 * 440).lpq(5.0).lpe(0.1).postgain(0.6)
        .pan(saw.range(0.5, 0.05).slow(64)).superimpose(pan(saw.range(0.5, 0.95).slow(64)))                
    ).velocity(cat(saw.pow(2).slow(32), pure(1).slow(256))).analog(2.5)  // . solo()
    .filterWhen(x => x < (wait * 4 + keep)) // . mute()
  , // Perc 2 ------------------------------------------------------------------------------------------------------------------
  sound("<[hh hh oh hh] [hh hh ~ hh] [hh hh oh hh] [hh hh ~ <cr!7 rd>]>")
    .orbit(4).gain(0.6).pan(0.4).adsr("0.012:0.15:0.7:2.0").fast(2).degrade(0.1).hpf(1000).lpf(6500).late(0.001)
    .filterWhen(x => x >= wait * 1 && x < (wait * 2 + keep))
  , // Perc 1 -----------------------------------------------------------------------------------------------------------------------
  sound("[bd bd bd ~  bd ~ bd ~] [bd bd sd:5 ~  bd ~ bd|sd:5 ~]").slow("[8 8 8 8 8 8 4 [2 4]]/32").fast(2)
    .orbit(5).gain(0.7).pan(0.55).adsr("0.013:0.15:0.5:1").degrade(0.01).hpf(120).lpf(6500)
    .filterWhen(x => x >= wait * 1.75 && x < (wait * 1 + keep))
  , // Shore ---------------------------------------------------------------------------------------------------------
  note("c").fast(7).sound("brown").vowel("a") // . solo()
    .orbit(0).gain(0.50).pan(perlin.early(1.7).range(0.1, 0.9).slow(8)).adsr("0.5:1.0:1.0:2.5")
    .bandf(perlin.range(440, 440 * 4).segment(16).slow(48)).bandq(sine.range(0.1, 3.0).slow(32).early(16))
  ,
).delay("0.10::0.7").delaytime(pure(1/8).div(cps)).room("0.05:10.0").compressor("-15:2:6:0.01:0.2")










        """,
)
