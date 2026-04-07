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
            id = "$PREFIX-synthirs",
            title = "Synthris",
            rpm = 40.2,
            code = TestTextPatterns.tetris,
            icon = "gamepad",
        )
    )

    val sakura = add(
        Song(
            id = "$PREFIX-synthkura",
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
      .analog(0.2).vibrato(6, 0.02)
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
    a5@2  ~  ~  e5@2  d5@2 
    <[e4@2 ~ ~ e4@2 ~ ~] [e4 f4 [b4 a4] f4 e4@4] [a4@2 ~ ~ a4@2 ~ ~] [e5 f5 [b5 a5] f5 e5@4]>@8
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
            id = "$PREFIX-synth-of-the-sea",
            title = "Synth of the sea",
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
            id = "$PREFIX-a-synth-worth-lying-for",
            title = "A Synth Worth Lying For",
            rpm = 32.0,
            code = TestTextPatterns.aTruthWorthLyingFor,
            icon = "guitar",
        )
    )

    val strangerThings = add(
        Song(
            id = "$PREFIX-stranger-synths",
            title = "Stranger Synths",
            rpm = 34.0,
            code = TestTextPatterns.strangerThingsNetflix,
            icon = "film",
        )
    )

    val finalFantasy7Prelude = add(
        Song(
            id = "$PREFIX-final-synthasy-7-prelude",
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
            id = "$PREFIX-osynthris",
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
            id = "$PREFIX-smalltown-synth",
            title = "Smalltown Synth",
            rpm = 37.2,
            code = TestTextPatterns.smallTownBoy,
            icon = "record vinyl",
        )
    )

    val drunkenSailor = add(
        Song(
            id = "$PREFIX-drunken-synthlor",
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

    val irishLament = add(
        Song(
            id = "$PREFIX-the-synthsale-pipers-farewell",
            title = "The Synthsale Piper's Farewell",
            rpm = 25.0,
            icon = "globe europe",
            code = """
import * from "stdlib"
import * from "sprudel"

// ── Instruments ─────────────────────────────────────────────────────

// Blockflöte — more breath presence
let blockfloete = Osc.register("blockfloete",
    Osc.sine().mul(0.55)
        .plus(Osc.triangle().mul(0.22))
        .plus(Osc.saw().mul(0.01).lowpass(2500))
        .plus(Osc.sine().detune(12).mul(0.04))
        .plus(Osc.sine().detune(19.02).mul(0.02).adsr(0.001, 0.15, 0.0, 0.02))
        .plus(Osc.sine().detune(24).mul(0.015).adsr(0.001, 0.1, 0.0, 0.01))
        .plus(Osc.pinknoise().mul(0.12).lowpass(4000).highpass(800).adsr(0.003, 0.05, 0.0, 0.005))
        .plus(Osc.perlin(10).mul(0.035).lowpass(3500).highpass(1000))
        .plus(Osc.whitenoise().mul(0.08).highpass(4000).lowpass(8000).adsr(0.001, 0.03, 0.0, 0.001))
        .lowpass(2800, 0.8).highpass(150).warmth(2500)
        .vibrato(5, 0.003)
        .analog(0.06)
        .pitchEnvelope(0.5, 0.005, 0.03)
        .adsr(0.015, 0.08, 0.7, 0.08)
)

// Guitar — more sustain and release
let fingerpick = Osc.register("fingerpick",
    Osc.pluck(Osc.freq(), 0.99, 0.45, 0.5)
        .plus(Osc.sine().mul(0.12))
        .plus(Osc.sine().detune(-12).mul(0.03))
        .lowpass(2800)
        .highpass(120)
        .adsr(0.003, 0.6, 0.2, 0.4)
)

// Pizzicato contrabass
let contrabass = Osc.register("contrabass",
  Osc.pluck(Osc.freq(), 0.995, 0.25, 0.55, 0.05)
    .pitchEnvelope(0.5, 0.003, 0.02)
    .plus(Osc.pluck(Osc.freq(), 0.995, 0.25, 0.55, 0.05).detune(0.05).mul(0.15))
    .plus(Osc.sine().detune(0.01).lowpass(200).mul(0.3).adsr(0.005, 0.6, 0.0, 0.15))
    .plus(Osc.triangle().lowpass(1200).mul(0.15).adsr(0.005, 0.3, 0.0, 0.05))
    .plus(Osc.brownnoise().lowpass(600).mul(0.06).adsr(0.001, 0.04, 0.0, 0.01))
    .plus(Osc.crackle(0.03).lowpass(1000).highpass(100).mul(0.008))
    .lowpass(Osc.constant(300).plus(Osc.constant(1200).adsr(0.005, 0.2, 0.0, 0.05)))
    .highpass(30).warmth(600).analog(0.04)
    .adsr(0.005, 0.5, 0.0, 0.15)
)

// ── Part 1: Opening lament (Dm - Gm - C - F) ───────────────────────
let melody1 = note("<[d4 f4 e4 d4] [c4 a4 g4 f4] [d4 g4 f4 e4] [c4 bb4 a4 g4] [d5 c5 bb4 a4] [g4 e4 d4 f4] [a4 g4 f4 e4] [d4 c4 d4 ~]>")
    .sound(blockfloete).gain(0.28).orbit(0).room(0.1).rsize(3)
let guitar1 = note("<[d3 ~ a3 ~ d4 ~ f4 ~] [g2 ~ d3 ~ g3 ~ bb3 ~] [c3 ~ g3 ~ c4 ~ e4 ~] [f2 ~ c3 ~ f3 ~ a3 ~] [d3 ~ a3 ~ d4 ~ f4 ~] [g2 ~ d3 ~ g3 ~ bb3 ~] [c3 ~ g3 ~ c4 ~ e4 ~] [f2 ~ c3 ~ f3 ~ a3 ~]>").fast(2)
    .sound(fingerpick).gain(0.3).adsr("0.003:0.6:0.2:0.4").legato(0.8).orbit(1).room(0.25).rsize(4)
let bass1 = note("<[d2@2 ~ ~] [g2@2 ~ ~] [c2@2 ~ ~] [f2@2 ~ ~] [d2@2 ~ ~] [g2@2 ~ ~] [c2@2 ~ ~] [f2@2 ~ ~]>")
    .sound(contrabass).gain(0.25).adsr("0.005:0.5:0.0:0.15").legato(0.9).orbit(2).room(0.08).rsize(2)

// ── Part 2: Intensified (Am - Dm - Bb - C), higher melody ──────────
let melody2 = note("<[a5 c6 b5 a5] [d6 c6 a5 g5] [bb5 a5 g5 f5] [g5 e5 c5 a5] [a5 c6 b5 a5] [d6 c6 a5 g5] [bb5 a5 g5 f5] [g5 f5 e5 d5]>")
    .sound(blockfloete).gain(0.28).orbit(0).room(0.1).rsize(3)
let guitar2 = note("<[a3 e4 c4 a3 e4 c4 a3 e4] [d4 a4 f4 d4 a4 f4 d4 a4] [bb3 f4 d4 bb3 f4 d4 bb3 f4] [c4 g4 e4 c4 g4 e4 c4 g4] [a3 e4 c4 a3 e4 c4 a3 e4] [d4 a4 f4 d4 a4 f4 d4 a4] [bb3 f4 d4 bb3 f4 d4 bb3 f4] [c4 g4 e4 c4 g4 e4 c4 g4]>").fast(2)
    .sound(fingerpick).gain(0.3).adsr("0.003:0.6:0.2:0.4").legato(0.8).orbit(1).room(0.25).rsize(4)
let bass2 = note("<[a2@2 ~ ~] [d2@2 ~ ~] [bb2@2 ~ ~] [c2@2 ~ ~] [a2@2 ~ ~] [d2@2 ~ ~] [bb2@2 ~ ~] [c2@2 ~ ~]>")
    .sound(contrabass).gain(0.25).adsr("0.005:0.5:0.0:0.15").legato(0.9).orbit(2).room(0.08).rsize(2)

// ── Part 3: Emotional peak ──────────────────────────────────────────
let melody3a = note("<[d6 f6 g6 f6] [e6 c6 a5 g5] [d6 c6 d6 f6] [a5 g5 f5 e5] [d5 a4 d5 f5] [c5 g4 bb4 a4] [d5 c5 a4 g4] [d5 ~ ~ ~]>")
    .sound(blockfloete).gain(0.28).orbit(0).room(0.1).rsize(3)
let melody3b = note("<[f5 d6 e6 d6] [c6 a5 f5 e5] [f5 e5 f5 a5] [f5 e5 d5 c5] [f4 f4 f4 d5] [a4 e4 g4 f4] [f4 e4 f4 e4] [f4 ~ ~ ~]>")
    .sound(blockfloete).gain(0.18).orbit(0).room(0.1).rsize(3)
let melody3 = stack(melody3a, melody3b)
// Guitar: half-speed arpeggios
let guitar3arp = note("<[g3 d4 bb3 g3] [f3 c4 a3 f3] [bb2 f3 d3 bb2] [c3 g3 e3 c3] [d3 a3 f3 d3] [d3 a3 f3 d3] [a2 e3 c3 a2] [d3 a3 f3 d3]>")
    .sound(fingerpick).gain(0.2).adsr("0.003:0.6:0.2:0.4").legato(0.8).orbit(1).room(0.25).rsize(4)
// Guitar melody — half speed, panned left
let guitar3mel = note("<[g4@2 f4@2] [f4@2 e4@2] [bb4@2 a4@2] [c5@2 g4@2] [a4@2 g4@2] [g4@2 f4@2] [c4@2 bb3@2] [d4@3 ~]>")
    .sound(fingerpick).gain(0.2).adsr("0.003:0.6:0.2:0.4").legato(1.0).orbit(1).room(0.25).rsize(4).pan(0.3)
let guitar3 = stack(guitar3arp, guitar3mel)
let bass3 = note("<[g2@2 ~ ~] [f2@2 ~ ~] [bb2@2 ~ ~] [c2@2 ~ ~] [d2@2 ~ ~] [g2@2 ~ ~] [c2@2 ~ ~] [d2@3 ~ ~]>")
    .sound(contrabass).gain(0.25).adsr("0.005:0.5:0.0:0.15").legato(0.9).orbit(2).room(0.08).rsize(2)

// ── Assemble ────────────────────────────────────────────────────────
let part1 = stack(melody1, guitar1, bass1)
let part2 = stack(melody2, guitar2, bass2)
let part3 = stack(melody3, guitar3, bass3)

arrange([8, part1], [8, part2], [8, part3], [8, part2], [8, part3], [8, part2])
  .room("0.15:10")
            """
        )
    )
}
