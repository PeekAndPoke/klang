@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

internal val sakuraSong = Song(
    id = "${BuiltInSongs.PREFIX}-synthkura",
    title = "Die Kirschblüte",
    rpm = 32.0,
    icon = "globe asia",
    code = """
import * from "stdlib"
import * from "sprudel"

let wait = 14

let koto = Osc.register("koto", Osc.pluck()
      .plus(Osc.sine().detune(12).mul(0.1).adsr(0.001, 0.3, 0.0, 0.05))
      .lowpass(Osc.constant(5000).plus(Osc.constant(3000).adsr(0.001, 0.3, 0.0, 0.05)))
      .highpass(200)
)

let shaku = Osc.register("shaku", Osc.sine().mul(0.6)
      .plus(Osc.triangle().mul(0.25))
      .plus(Osc.perlin(13).mul(0.05))
      .plus(Osc.perlin(21).mul(0.10).highpass(2000).adsr(0.02, 0.2, 0.03, 0.02))
      .lowpass(3500).highpass(300)
      .analog(0.2).vibrato(2, Osc.perlin(1).mul(0.1).plus(0.15))
      .pitchEnvelope(1, 0.02, 0.1)
      .adsr(0.07, 0.15, 0.8, 0.3)
)

let kick = Osc.register("kick", Osc.sine()
      .pitchEnvelope(24, 0.001, 0.04)
      .adsr(0.001, 0.2, 0.0, 0.02)
)

let rim = Osc.register("rim", Osc.sine(800)
      .plus(Osc.whitenoise().highpass(4000).mul(0.3))
      .lowpass(3000)
      .adsr(0.001, 0.03, 0.0, 0.005)
)

let brush = Osc.register("brush", Osc.perlin(30).mul(0.5)
      .plus(Osc.whitenoise().mul(0.3))
      .highpass(2000).lowpass(8000)
      .adsr(0.01, 0.08, 0.0, 0.02)
)

let sub = Osc.register("sub", Osc.sine().lowpass(200)
      .adsr(0.005, 0.3, 0.0, 0.05)
)

let pad = Osc.register("pad", Osc.supersine().analog(0.3)
      .lowpass(Osc.sine(0.08).plus(1).times(300).plus(800))
      .adsr(0.8, 0.5, 0.9, 2.0)
)


stack(
  // Sakura melody
  note(`
    [a4 a4 b4 ~ a4 a4 b4 ~]       [a4 b4 c5 b4 a4 [b4 a4] f4@2]
    [e4 c4 e4 f4 e4 [e4 d4] c4@2] [a4 b4 c5 b4 a4 [b4 a4] f4@2]
    [e4 c4 e4 f4 e4 [e4 d4] c4@2] [a4 a4 b4 ~ a4 a4 b4 ~]
    [e4 f4 [b4 a4] f4 e4@4]
  `).sound(koto).legato(0.8).slow(14).gain(0.7)
    .superimpose(fast(2).gain(0.075).pan(0.3))

  // Shakuhachi
  ,note(`
    a4@2  ~  ~  ~  ~  b4 ~
    a4@2  ~  ~  ~  ~  f4 ~
    e4@2  ~  ~  ~  ~  a4 ~
    e5@2  ~  ~  c5@2  b4 a4
    c5@2  ~  ~  ~  ~  a4 ~
    a5@2  ~  ~  e5@2  d5@2
    <[e4@4 e4@1 ~ ~ ~] [e4 f4 [b4 a4] f4 e4@4] [a4@4 a4@1 ~ ~ ~] [e5 f5 [b5 a5] f5 e5@4]>@8
  `).sound(shaku).slow(14).gain(0.225).adsr("0.05:0.1:1:0.2").pan(perlin.range(0.3, 0.7).slow(24))
    .filterWhen(x => x >= wait * 2) // . solo()

  // Drums
  ,note("a1 ~  ~  ~  ~  ~  ~  ~  a1 ~  ~  ~  ~  ~  ~  ~").sound(kick).gain(0.8).hpf(100)
  ,note("~  ~  ~  ~  x  ~  ~  ~  ~  ~  x  ~  ~  ~  ~  ~").sound(rim).gain(0.4)
  ,note("~  ~  ~  ~  ~  ~  ~  ~  x  ~  ~  ~  ~  ~  ~  ~").sound(brush).gain(0.3)

  // Sub-Bass
  ,note("a1 d2 a1 f1 c2 e1 a1").sound(sub).slow(14).legato(1.5).gain(0.5).hpf(40)
    .filterWhen(x => x >= wait * 1)

  ,stack(
    // Root
    note("a2  d2  a2  f2  c2  e2  a2").sound(pad).slow(14).legato(1.02).gain(0.2).pan(0.4)
    // Third (minor/major character)
    ,note("c3  f2  c3  a2  e2  gs2 c3").sound(pad).slow(14).legato(1.02).gain(0.2).pan(0.3)
    // Fifth
    ,note("e3  a2  e3  c3  g2  b2  e3").sound(pad).slow(14).legato(1.02).gain(0.2).pan(0.2)
    // Octave
    ,note("a3  d3  a3  f3  c3  e3  a3").sound(pad).slow(14).legato(1.05).gain(0.2).pan(0.8)
    // High third
    ,note("c4  f3  c4  a3  e3  gs3 c4").sound(pad).slow(14).legato(1.05).gain(0.2).pan(0.7)
    // High fifth
    ,note("e4  a3  e4  c4  g3  b3  e4").sound(pad).slow(14).legato(1.05).gain(0.2).pan(0.6)
  ).hpf(160).filterWhen(x => x >= wait * 3)
).room("0.25:7:0.75").delay(0.2).delaytime(pure(1/8).div(cps)).compressor("-15:2:6:0.01:0.2")





            """,
)
