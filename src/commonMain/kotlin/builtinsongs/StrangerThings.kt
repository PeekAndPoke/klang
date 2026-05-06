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
  sound("cp ~ cp ~ ~ cp cp ~  cp ~ ~ ~ cp cp ~ ~").slow(4).orbit(0).gain(0.3).legato(2.0)
    .bandf(sine.range(1400, 1800).fast(3.14))
    .filterWhen(x => x >= wait * 8 && x < (wait * 12 + keep))
  , // Lyrics ---------------------------------------------------------------------------------------------------------------------------
  n("0").morse("Schön ist es auf der Welt zu sein!").orbit(0)
    .scale("C5:major").scaleTranspose("0 -2 2 2".slow(32)).bandf(1800).bandq(5.0).hpf(1000).analog(3)
    .sound("pulse").warmth(0.75).crush(5).gain(0.05).clip(0.35).pan(berlin.slow(2)).adsr("0.03:0.08:0.2:0.1") // .solo()
    .filterWhen(x => x >= wait * 12 && x < (wait * 6 + keep))
  , // Melody -----------------------------------------------------------------------------------------------------------------
  n("<[0 2 4 6 7 6 4 2]!14 [-2 -1 0 2 4 2 0 -1] [-2 -1 2 6 4 2 0 -1]>")
    .scale("[c3:major c3:pentatonic c3:major c3:major]/16")
    .orbit(1).s("supersaw").unison(3).detune(saw.range(0.0, 0.35).slow(16)).spread(1.0 ).tremolo("0.1:8").tremolodepth(saw.range(0,0.1).slow(256))
    .gain(0.8).distort(0.25).warmth(0.5).postgain(0.2).adsr("0.01:0.2:0.8:0.155")
    .pan(sine.range(0.3, 0.7).slow(16)) // . solo()
    .superimpose(bandf(notch).bandq(1.0).gain(0.125).transpose(12))
    .hpf(260).lpf(800).lpenv(perlin.range(2.5, 4.0).slow(8)).analog(2)
    .filterWhen(x => x >= wait * 4 && x < (wait * 4 + keep)) // . solo()
  , // Bass -----------------------------------------------------------------------------------------------------------------------------
  note("<a1 [f1 c2 e1 [f2 c2]] [a1 [c2 f1] a1 [f1@3 e1]] [a1@2 c2@3 d2 [c2,c3] [d1,d1,d2]]>/8").clip(0.8).struct("x!8")
    .orbit(2).s("supersaw").unison(4).detune(saw.range(0.05, 0.45).slow(64)).warmth(0.3)
    .gain(1.0).adsr("0.01:0.6:0.5:0.4").postgain(0.4).pan(saw.range(0.5, 0.1).slow(keep * 2))
    .superimpose(x => x.orbit(3).scaleTranspose("<[12 12 7 12 12 [12 12] 0 -12] [12 12 0 12 12 [0 12] 0 -12]>/16").pan(saw.range(0.5, 0.9).slow(keep * 2)).legato(1.05))
    .lpf(4 * 440).hpf(90).notchf(notch).notchq(0.75)
    .superimpose(x => x.gain(saw.slow(64).pow(2.0).mul(3)).crush("1.5".add(berlin2.mul(0.25).slow(4))).lpf(4 * 440).hpf(200).postgain(0.4))
    .velocity(cat(saw.pow(2).slow(32), pure(1).slow(256)).mul("1 0.95 0.975 0.95".fast(2))).analog(1.5)  // . solo()
    .filterWhen(x => x < (wait * 4 + keep)) // . mute()
  , // Perc 2 ------------------------------------------------------------------------------------------------------------------
  sound("<[hh hh oh hh] [hh hh ~ hh] [hh hh oh hh] [hh hh ~ <cr!7 rd>]>")
    .orbit(4).gain(0.8).pan(0.4).adsr("0.04:0.2:0.8:2.0").fast(2).degrade(0.1).lpf(4800)
    .filterWhen(x => x >= wait * 1 && x < (wait * 2 + keep))
  , // Perc 1 -----------------------------------------------------------------------------------------------------------------------
  sound("[bd bd bd ~  bd ~ bd ~] [bd bd sd:5 ~  bd ~ bd|sd:5 ~]").slow("[8 8 8 8 8 8 4 [2 4]]/32").fast(2)
    .orbit(5).gain(0.95).pan(0.5).adsr("0.03:0.2:0.5:1").degrade(0.01).hpf(120).lpf(5500)
    .filterWhen(x => x >= wait * 1.75 && x < (wait * 1 + keep))
  , // Shore ---------------------------------------------------------------------------------------------------------
  note("c").fast(7).sound("brown")
    .orbit(0).gain(0.08).pan(perlin.early(1.7).range(0.3, 0.7).slow(21)).adsr("0.2:1.0:1.0:2.5")
    .bandf(perlin.range(440, 440 * 4).segment(16).slow(48)).bandq(sine.range(0.05, 5.0).slow(32).early(16))
  ,
).delay("0.1::0.5").delaytime(pure(1/8).div(cps)).room("0.05:10.0").compressor("-15:2:6:0.01:0.2").analog(1)








        """,
)
