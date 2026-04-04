@file:Suppress("unused")

package io.peekandpoke.klang

object BuiltInSongs {

    const val PREFIX = "builtin-song"
    // Next id: 0010

    private val _song = mutableListOf<Song>()
    val songs get() = _song.toList()

    private fun add(song: Song): Song = song.also {
        _song.add(song)
    }

    val tetris = add(
        Song(
            id = "$PREFIX-0001",
            title = "Synthris",
            rpm = 40.2,
            code = TestTextPatterns.tetris,
            icon = "gamepad",
        )
    )

    val sakura = add(
        Song(
            id = "$PREFIX-0009",
            title = "Synthkura",
            rpm = 36.0,
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
      .plus(Osc.perlin(15).mul(0.05))
      .plus(Osc.perlin(20).mul(0.15).highpass(1000).adsr(0.03, 0.2, 0.03, 0.02))
      .lowpass(2500).highpass(300)
      .analog(0.2).vibrato(8, 0.02)
      .pitchEnvelope(1, 0.02, 0.1)
      .adsr(0.1, 0.15, 0.8, 0.3)
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
  `).sound(koto).legato(0.8).slow(14)
    .superimpose(fast(2).gain(0.075).pan(0.0), fast(2).gain(0.075).pan(1.0))

  // Shakuhachi
  ,note(`
    a4@2  ~  ~  ~  ~  b4 ~
    a4@2  ~  ~  ~  ~  f4 ~
    e4@2  ~  ~  ~  ~  a4 ~
    e5@2  ~  ~  c5@2  b4 a4
    c5@2  ~  ~  ~  ~  a4 ~
    a5@2  ~  ~  e5@2  d4@2 
    <[e4@2 e4@2 e4@2 e4@2] [e4 f4 [b4 a4] f4 e4@4] [a4@2 a3@2 a4@2 a3@2] [e5 f5 [b5 a5] f5 e5@4]>@8
  `).sound(shaku).legato(1.0).slow(14).gain(0.175).adsr("0.05:0.1:1:0.2")
    .filterWhen(x => x >= wait * 2)

  // Drums
  ,note("a1 ~  ~  ~  ~  ~  ~  ~  a1 ~  ~  ~  ~  ~  ~  ~").sound(kick).gain(0.8)
  ,note("~  ~  ~  ~  x  ~  ~  ~  ~  ~  x  ~  ~  ~  ~  ~").sound(rim).gain(0.4)
  ,note("~  ~  ~  ~  ~  ~  ~  ~  x  ~  ~  ~  ~  ~  ~  ~").sound(brush).gain(0.3)

  // Sub-Bass
  ,note("a1 d2 a1 f1 c2 e1 a1").sound(sub).slow(14).legato(1.5).gain(0.75)
    .filterWhen(x => x >= wait * 1)
  
  ,stack(
    // Root
    note("a2  d2  a2  f2  c2  e2  a2").sound(pad).slow(14).legato(1.02).gain(0.25)
    // Third (minor/major character)
    ,note("c3  f2  c3  a2  e2  gs2 c3").sound(pad).slow(14).legato(1.03).gain(0.25)
    // Fifth
    ,note("e3  a2  e3  c3  g2  b2  e3").sound(pad).slow(14).legato(1.05).gain(0.25)
    // Octave
    ,note("a3  d3  a3  f3  c3  e3  a3").sound(pad).slow(14).legato(1.08).gain(0.25)
    // High third
    ,note("c4  f3  c4  a3  e3  gs3 c4").sound(pad).slow(14).legato(1.13).gain(0.25)
    // High fifth
    ,note("e4  a3  e4  c4  g3  b3  e4").sound(pad).slow(14).legato(1.21).gain(0.25)    
  ).filterWhen(x => x >= wait * 3)
).room("0.25:10:0.75").delay(0.2).delaytime(pure(1/8).div(cps))


            
            
            
            """    // END: Sakura
        )
    )

    val soundOfTheSea = add(
        Song(
            id = "$PREFIX-0006",
            title = "Sound of the sea",
            rpm = 30.0,
            code = """








             import * from  "stdlib"
              import * from "sprudel"
               let wind       = 0.050
                let water      = 0.070
                 let waves      = 0.210
                  let windSpiel  = 3.500
           
                    stack( //   Lean back and relax... let the waves carry you away
                // Wind ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

             note("c").fast(4).sound("brown").adsr("0.5:1.0:1.0:3.5").warmth(0.1) // . solo()
               .gain(wind).pan(sine.range(0.3, 0.7).slow(21))
               .hpf(1000).bandf(perlin.range(110, 110 * 20).slow(64)).bandq(berlin.range(0.0, 2.0).slow(39))
                     
           , // Water ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            note("c").fast(5).sound("pink").adsr("0.7:0.5:1.0:3.0") // . solo()
              .gain(water)
              .hpf(120).lpf(4000).bandf(300).bandq(1.0).early(2)
           , // Waves ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            note("<c@4 ~!15>").fast(2).sound("pink").adsr("0.75:0.5:1.0:10.0").warmth(0.2) // . solo()
             .gain(waves).pan(0.3).hpf(120).lpf(4000).bandf(perlin.range(100, 500).slow(22)).bandq(rand.range(0.5, 1.5))
                 .superimpose(x => x.pan(0.7))
              , // Windspiel ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                n(randrun(16)).fast(4).sound("glockenspiel").scale("d2:pentatonic").pan(0.3)
                  .gain(0.25).distort(0.2).postgain(windSpiel).adsr("0.1:1.0:1.0:5.0").hpf(300).degradeBy(0.995)
                      .orbit(1).delay(0.25).delaytime(pure(1/4).div(cps)).delayfeedback(0.5) // . solo()
                           ).room(0.25).rsize(10.0)              
      











            """,
            icon = "umbrella beach",
        )
    )

    val aTruthWorthLyingFor = add(
        Song(
            id = "$PREFIX-0003",
            title = "A Synth Worth Lying For",
            rpm = 32.0,
            code = TestTextPatterns.aTruthWorthLyingFor,
            icon = "guitar",
        )
    )

    val strangerThings = add(
        Song(
            id = "$PREFIX-0004",
            title = "Stranger Synths",
            rpm = 34.0,
            code = TestTextPatterns.strangerThingsNetflix,
            icon = "film",
        )
    )

    val finalFantasy7Prelude = add(
        Song(
            id = "$PREFIX-0005",
            title = "Final Synthasy VII Prelude",
            rpm = 42.0,
            icon = "gamepad",
            code = """
import * from "stdlib"
import * from "sprudel"

stack(
  cat(n(`<[ 0  1 2 4] [7 8 9 11] [14 15 16 18] [21 22 23 25] [28 25 23 22] [21 18 16 15] [14 11 9 8] [7 4 2  1]
          [-2 -1 0 2] [5 6 7  9] [12 13 14 16] [19 20 21 23] [26 23 21 20] [19 16 14 13] [12  9 7 6] [5 2 0 -1]>`).repeat(2),
      ).fast(2)
  .sound("sine").warmth(0.5).scale("C3:major").gain(0.5).clip(0.5)
  .adsr("0.05:0.2:0.5:0.15")
  
).room(0.2).rsize(5.0).analog(1)
            """,
        )
    )

    val osiris = add(
        Song(
            id = "$PREFIX-0007",
            title = "Osynthris",
            rpm = 33.0,
            code = """
import * from "stdlib"
import * from "sprudel"

stack(
  // Guitar 1
  cat(
    n(`<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13] [3,10,15]@2 [5,12,17]] [0,7,12]
        [12,19,24] [[6,13,18] [6,13,18]] [[6,13,18] [5,12,17]@2 [6,13,18]]>`).repeat(4),
    n(`<[[0,7]!4 [0,7]@4]!8 [[0,6]!4 [0,6]@4]!4 [[0,7]!4 [0,7]@4]!4 [[0,7]!4 [0,7]@4]!9 [[0,7]!4 [0,8]@4]!7>`)
  )
  .fast(4).scale("e2:chromatic").clip(0.975).hpf(60).lpf(2500).pan(0.5).adsr("0.02:0.3:0.5:0.05")
  .notchf("1440:2:60")
  .s("[superpluck pulse]/16").unison(6).detune(0.025).gain(1.0).distort(3).postgain(0.125).warmth(0.3)
  .superimpose(
    x => x.bandf(360).bandq(1.0).adsr("0.01:0.2:0.1:0.05"),
    x => x.bandf(880).bandq(sine.range(0.5, 1*1.5).slow(50)).sound("pulse").adsr("0.01:0.3:0.5:0.05"),
    // x => x.bandf(1080).bandq(sine.range(0.5, 2*1.25).slow(40)).sound("pulse").adsr("0.01:0.1:0.2:0.05"),
  ).orbit(1) // .solo()

  , // Guitar 2
  cat(
    n("<~!8>").repeat(4),
    n(`<[36!8]!4 [35!8]!4 [24!8]!4 [25!8] [25!4 28!4] [28!4 29!4] [29!4 31!4]
        [32!8] [32!4 36!4] [36!8]!2 [31!8]!4 [30!8]!4 [27!8] [27!4 26!4] [26!4 25!4] [25!4 24!4]
    >`).delay("<~!24 0.5::0.5!8>").delaytime(pure(1).div(cps)),
  ).orbit(2).pan(0.5).scale("e2:chromatic")
  .fast(4).clip(0.5).hpf(200).lpf("1800:1:100").lpadsr("0.05:0.05:0.1:0.02").adsr("0.118:0.1:0.1:0.025")
  .s("[[supersqr supersaw]!16]").gain(0.8).distort(3).postgain(0.1).warmth(0.5)
  .superimpose(x => x.notchf("720:1:50").notchq(1)) // . solo()
   // .mute()

  // Drums 1
  , s("<hh!8!8 [oh rd!5 cr hh]!2!8>").adsr("0.00:0.1:0.5:0.1").hpf(200).postgain(1.25) // .solo()
  // Drums 2
  , s(`<[[bd sd]!2]!4 [bd [bd,sd] bd [bd,sd]]!3 [bd bd [bd,sd] bd  [sd]!4]!1
        [[bd sd]!16]!8>`).n("<7!8 0!8>").adsr("0.00:0.3:0.5:0.1").gain(1.0).hpf(30)  // . solo()
)
  .room(0.3).rsize(5)
  .compressor("-15:2:6:0.01:0.2")
  .accelerate(saw.seg(8).pow(10).mul(0.05).add(0.0).slow(16))
  
  
          """.trimIndent(),
            icon = "guitar",
        )
    )

    val smallTownBoy = add(
        Song(
            id = "$PREFIX-0002",
            title = "Synthtown Boy",
            rpm = 37.2,
            code = TestTextPatterns.smallTownBoy,
            icon = "record vinyl",
        )
    )

    val drunkenSailor = add(
        Song(
            id = "$PREFIX-0008",
            title = "Drunken Synthlor",
            rpm = 45.0,
            code = """

import * from "stdlib"
import * from "sprudel"

stack(
  // Melody
  n(`<[8@2 8 8 8@2 8 8] [8 4  6  8]  [7@2 7 7 7@2 7 7] [7 3  5  7]
      [8@2 8 8 8@2 8 8] [8 9 10 11]  [10 8 7 5]        [4@2 4@2  ]
  >`).sndPluck("0.999:0.8").clip(0.8).scale("c3:dorian").gain(0.8).lpf("2000").lpadsr("0.01:0.1:0.2:0.1")
 .tremolosync(8).tremolodepth(0.33).tremoloshape("sine").analog(1)
  // . solo(0.99) 
  
  // Bass
  , n(`<[8 15 13 15]!2  [7 14 10 14]!2
        [8 15 13 15]!2  [7 14 10 14] [6 7 8 9]
>`).scale("C1:minor").sound("pluck").adsr("0.01:0.2:0.5:0.2").clip(0.5).distort(0.1).warmth(0.2).postgain(0.2) 
  .superimpose(x => x.sound("tri"))
  // Drums 1 
  , s("hh!8").adsr("0.01:0.1:0.1:1.0").gain(0.8) // .solo()
  // Drums 2
  , s("<[[bd sd]!2]!8>").adsr("0.02:0.1:0.7:1.0").gain(0.75) // . solo()
)
  .room(0.02).rsize(3)                
                
            """,
            icon = "glass cheers",
        )
    )
}
