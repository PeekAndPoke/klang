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
    .scale("C5:major").scaleTranspose("0 -2 2 2".slow(32)).bandf(1800).bandq(5.0).hpf(1600).lpf(2200).lpq(3)
    .sound("pulse").warmth(0.75).crush(5).gain(0.02).clip(0.35).pan(berlin.slow(2)).adsr("0.03:0.08:0.2:0.1") // .solo()
    .filterWhen(x => x >= wait * 12 && x < (wait * 6 + keep))
  , // Melody -----------------------------------------------------------------------------------------------------------------
  n("<[0 2 4 6 7 6 4 2]!14 [-3 -1 0 4 5 4 5 7] [-2 -1 0 7 4 2 0 -1]>")
    .scale("[c3:major c3:pentatonic c3:major c3:major]/16")
    .orbit(1).s("supersaw").unison(5).detune(saw.range(0.03, 0.35).slow(16)).spread(1.0 ).tremolo("0.1:8").tremolodepth(saw.range(0,0.1).slow(256))
    .gain(0.6).distort(0.25).warmth(0.1).postgain(0.3).adsr("0.01:0.6:0.7:0.155")
    .pan(sine.range(0.3, 0.7).slow(16)) // . solo()
    .superimpose(bandf(notch).bandq(1.0).gain(0.125).transpose(12)).body("wood")
    .hpf(400).lpf(950).lpenv(perlin.range(3.0, 4.5).lpq(5.5).slow(8)).analog(2)
    .filterWhen(x => x >= wait * 4 && x < (wait * 4 + keep)) // . solo()
  , // Bass -----------------------------------------------------------------------------------------------------------------------------
  note("<a1 [f1 c2 e1 [d2 c2]] [a1 [c2 f1] a1 [f1@3 e1]] [a1@2 c2@3 d2 [c2,c3] [a1,d1,a2,d2,a3]]>/4").clip(0.8).struct("x!4").slow(16)
    .orbit(2).s("supersaw").unison(4).detune(saw.range(0.05, 0.40).slow(64)).warmth(0.02)
    .gain(1.0).adsr("0.03:0.6:0.7:1.90").postgain(0.5)
    .superimpose(
      x => x.orbit(3).transpose("<[12 0 12 0 12 7 0 -12] [12 12 0 12 0 7 12 -12]>/32")
        .pan(saw.range(0.5, 0.9).slow(keep * 2)).clip(0.9)
    ).lpf(5.0 * 440).lpq(3.5).hpf(80).hpq(1.25).notchf(notch).notchq(0.75).analog(1).body("glass")
    .superimpose(
      x => x.gain(saw.range(0.2, 1.0).slow(64).pow(1.5).mul(2.0)).vibrato("12".add(berlin2)).vibmod(0.15)
        .crush("1.65".add(berlin2.mul(0.5).slow(4))).crushos(2).hpf(300).lpf(6.0 * 440).lpq(3.0).lpe(0.1).postgain(0.6)
        .pan(saw.range(0.5, 0.05).slow(64)).superimpose(pan(saw.range(0.5, 0.95).slow(64)))                
    ).velocity(cat(saw.pow(2).slow(32), pure(1).slow(256)).mul("1 0.95 0.975 0.95".fast(2))).analog(1.5)  // . solo()
    .filterWhen(x => x < (wait * 4 + keep)) // . mute()
  , // Perc 2 ------------------------------------------------------------------------------------------------------------------
  sound("<[hh hh oh hh] [hh hh ~ hh] [hh hh oh hh] [hh hh ~ <cr!7 rd>]>")
    .orbit(4).gain(0.6).pan(0.4).adsr("0.02:0.2:0.7:2.0").fast(2).degrade(0.1).hpf(1000).lpf(6500).late(0.001)
    .filterWhen(x => x >= wait * 1 && x < (wait * 2 + keep))
  , // Perc 1 -----------------------------------------------------------------------------------------------------------------------
  sound("[bd bd bd ~  bd ~ bd ~] [bd bd sd:5 ~  bd ~ bd|sd:5 ~]").slow("[8 8 8 8 8 8 4 [2 4]]/32").fast(2)
    .orbit(5).gain(0.8).pan(0.55).adsr("0.02:0.15:0.5:1").degrade(0.01).hpf(120).lpf(7500)
    .filterWhen(x => x >= wait * 1.75 && x < (wait * 1 + keep))
  , // Shore ---------------------------------------------------------------------------------------------------------
  note("c").fast(7).sound("brown").vowel("o") // . solo()
    .orbit(0).gain(0.50).pan(perlin.early(1.7).range(0.1, 0.9).slow(8)).adsr("0.5:1.0:1.0:2.5")
    .bandf(perlin.range(440, 440 * 4).segment(16).slow(48)).bandq(sine.range(0.05, 5.0).slow(32).early(16))
  ,
).delay("0.10::0.7").delaytime(pure(1/8).div(cps)).room("0.05:10.0").compressor("-15:2:6:0.01:0.2")









        """,
)
