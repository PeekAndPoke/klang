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

    val derSchmetterling = add(
        Song(
            id = "$PREFIX-der-schmetterling",
            title = "Der Schmetterling",
            rpm = 32.5,
            icon = "bug",
            code = """
import * from "stdlib"                                                                                                      //
import * from "sprudel"                                                                                                    ////
                                                                                                                          //  //
let feel = 1.0   // 0.0 .. mechanical | 10.0 .. old vinyl                                                                //    //
let snd  = 550   // 100 .. 2000                                                                                         //      //
                                                                                                                       //        //
stack(                                                                                                    //////////////          //////////////
  // Lead                                                                                                   //                              //
  n("<[-7 0 2 4] [-7 0 4 2] [-5 -1 2 4] [-6 -1 3 1]>*2")                                                      //          DISCO!          //
    .orbit(0).scale("e4:minor").sound("supersaw").unison(2).detune(0.05)                                        //       FOREVER!       //
    .hpf(431).lpf(5000).warmth(0.2)                                                                               //                  //
    .gain(0.35).distort("0.8:gentle:2").postgain(0.1) // . solo()                                                  //       //      //
    .adsr("0.01:0.2:0.6:0.1").clip(0.8)                                                                           //     //    //    //
    .release("<0.105!16 0.275!16 0.110!16 0.3!16 0.06!16 0.25!16 0.09!16 0.4!16 0.075!16 0.5!16>")               //   //          //  //
    .apply(                                                                                                     // //               // //
      x => x.velocity(0.7).detune(0.07),                                                                       //                       //
      x => x.shuffle("<1!64 0!16 1!1 4/8!14 1!33>").seed(sinOfNight.add(1).mul(24 * 60 * 15))            
        .superimpose(transpose(12).detune(0.07).velocity("<0!32 0.35!32>").lpf(5500).pan(0.8))          
        .superimpose(transpose(24).detune(0.10).velocity("<0!96 0.20!32>").lpf(6000).pan(0.33))  
    ).phaser(1/8).phaserdepth("<0.0!64 0.5!16 0.0!48>").phasersweep(1000).phasercenter(1500),
  // Pad
  n("<[0 0 2 4 0 0 -2 -1]!4 [0 [2 4] 0 [2 [2 -1@3]]]!2 [0 [6 4] 0 <[2 3] [-2 -0]>] [4 [2 1] 0 [-2 <-1 -4>]]>/4")
    .struct("<[x!16]!7 [x!24]!1 [x!16]!16>").velocity("1.02 0.95!3 0.98 0.95!3".fast(2))
    .scale("<e2:minor!48 e3:minor!16>").sound("supersaw").unison(3).detune(0.1)
    .notchf(snd).notchq(0.5).lpf("<1800!48 2600!16>").hpf(160).warmth(saw.range(0.3, 0.1).slow(5*32))
    .distort("0.3:gentle:2").distort(sine.range(0.15, 0.3).slow(10*32))
    .adsr("0.01:0.2:0.5:0.035").clip(0.75).crush(saw.range(8, 4).slow(32))  // . solo()
    .gain(0.33).orbit(1).pan(0.275),
  // Bass
  n("<0 0 2 4 0 0 -2 -1>").struct("<[x!8]!14 [x!12]!2 [x!8]!32>").fast(2).velocity("1.02 0.95!3 0.98 0.95!3".fast(2))
    .scale("e2:minor").sound("saw").warmth(saw.range(0.3, 0.1).slow(5*32))
    .notchf(snd).notchq(0.5).lpf("1000").hpf(100).distort("0.2.75:gentle:2")
    .adsr("0.01:0.2:0.5:0.045").clip(0.75)  // . solo()
    .gain(0.42).orbit(2).pan(0.725),
  // Drums
  sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd sd bd sd]!24 [bd [bd,sd] bd [bd,sd]]!8>") // . solo()
    .orbit(3).gain(0.8).crush(10).crushos(2).hpf(60).lpf("2400:1").lpe("<2!128 2.3!128 2.7!128>").adsr("0.01:0.2:0.5:0.1"),
  sound("<[hh hh oh hh]!24 [cr hh cr hh]!16>") //. solo()
    .orbit(4).gain(0.4).fast(2).crush(8).crushos(2).hpf(400).lpf("3500:1:2").adsr("0.01:0:1.0:0.1")
  // Master
).compressor("-10:2:10:0.02:0.25").analog(feel).engine("pedal").room("0.01:5")


// Inspired by: Editors - Papillon
// https://open.spotify.com/intl-de/track/7hYiX6LMP8w8d0kEc4KWuW




// Written by: peekandpoke

// Epilspsy Warning: Do not click the oscilloscope!


           
           
           
           
           
           
           
            """
        )
    )

    val tetris = add(
        Song(
            id = "$PREFIX-synthirs",
            title = "Stein um Stein",
            rpm = 39.5,
            code = TestTextPatterns.tetris,
            icon = "gamepad",
        )
    )

    val soundOfTheSea = add(
        Song(
            id = "$PREFIX-synth-of-the-sea",
            title = "Gestranded",
            rpm = 30.0,
            code = """





          import * from  "stdlib"
           import * from "sprudel"
            let wind       = 0.080
             let water      = 0.080
              let waves      = 0.110
               let windSpiel  = 3.250
        
                 stack( //   Lean back and relax... let the waves carry you away
              // Wind ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

           note("c").fast(8).sound("brown").adsr("0.5:1.0:1.0:3.5").warmth(0.1)  // . solo()
         .gain(wind).pan(berlin.range(0.2, 0.8).slow(34))//.lpf(1500)
        .hpf(120).bandf(perlin.range(110, 110 * 15).slow(64)).bandq(perlin.range(0.5, 5.0).slow(21))
                  
        , // Water ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
       sound("pink pink pink pink").legato(2).degrade(0.5).adsr("1.5:3.0:0.5:5.0") //  . solo()
       .gain(water).hpf(120).lpf(4000).bandf(300).bandq(1.0).early(2)
           
        , // Waves ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        note("c c c c").legato(4).degrade(95/100).sound("pink").adsr("1.0:5.0:0.6:20.0").warmth(0.1) // . solo()
        .gain(waves).hpf(120).lpf(5000).lpadsr("1.0:3.0:0.4:15.0").lpenv(10)
        .bandf(perlin.range(120, 500).slow(22)).bandq(rand.range(0.5, 1.5))
         .pan(sine.range(0.1, 0.4).slow(4)).superimpose(x => x.pan(sine.range(0.9, 0.6).slow(5)))
          .superimpose(x => x.sound("pink").adsr("0.3:0.8:0.2:1.5").velocity(0.2).hpf(2500).lpf(8000))
           
             , // Windspiel ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
               n(randrun(16)).fast(4).sound("glockenspiel").scale("f2:pentatonic").pan(0.3)
                 .gain(0.25).distort(0.05).postgain(windSpiel).adsr("0.05:0.3:0.5:5.0").hpf(400).degradeBy(0.995)
                     .orbit(1).delay(0.25).delaytime(pure(1/4).div(cps)).delayfeedback(0.75) // . solo()
                           ).room(0.25).rsize(10.0).seed(sinOfDay.add(1).mul(24 * 60 * 60 * 100))              
      










            """,
            icon = "umbrella beach",
        )
    )

    val sakura = add(
        Song(
            id = "$PREFIX-synthkura",
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



           
           
           
           
           
            """    // END: Sakura
        )
    )

    val aTruthWorthLyingFor = add(
        Song(
            id = "$PREFIX-a-synth-worth-lying-for",
            title = "A Synth Worth Lying For",
            rpm = 31.0,
            code = TestTextPatterns.aTruthWorthLyingFor,
            icon = "guitar",
        )
    )

    val strangerThings = add(
        Song(
            id = "$PREFIX-stranger-synths",
            title = "Seltsamere Dinge",
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
let melody3a = note("<[d6 f6 g6 f6] [e6 c6 a5 g5] [d6 c6 d6 f6] [a5 g5 f5 e5] [d5 a4 d5 f5] [c5 g4 bb4 a4] [d5 c5 a4 g4] [d4@2 ~ ~]>")
    .sound(blockfloete).gain(0.28).orbit(0).room(0.1).rsize(3)
let melody3b = note("<[f5 d6 e6 d6] [c6 a5 f5 e5] [f5 e5 f5 a5] [f5 e5 d5 c5] [f4 f4 f4 d5] [a4 e4 g4 f4] [f4 e4 f4 e4] [f4@2 ~ ~]>")
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

arrange([8, part1], [8, part2], [8, part3], [8, part2], [8, part3], [8, part2]).room("0.15:10")

// Composed by: Claude, Gemini, Motör, peekandpoke
            """
        )
    )

    val irishLamentTechno = add(
        Song(
            id = "$PREFIX-the-synthsale-pipers-last-rave",
            title = "The Synthsale Piper's Last Rave",
            rpm = 37.5,
            icon = "compact disc",
            code = """
import * from "stdlib"
import * from "sprudel"

// The Piper traded the recorder for a 303. Same melody, different floor.
// D minor — arrange-based structure that loops back to a "warm" state
// (kick + hat + bass already going), not a cold silent intro.

// ── Section structure (each entry = section in arrange) ─────────────
//   16   warm        kick + hat + bass — LOOP POINT
//   8    +sub        sub joins
//   8    +perc       clap, open hat, rim
//   8    +leadA      melody phrase A
//   8    +leadB +pad pad enters
//   8    +leadC +pad
//   8    +leadD +pad
//   16   +leadE +pad +wind   (leadE loops once; wind fades in over 16)
//   2    pause       silence
//   2    hit         unison hit at beat 1, then tail
//   16   quiet       melody3 with sparse rhythm + bass
//   = 100 cycles total, then arrange loops back to warm

// ── Continuous core: kick + hat + bass ──────────────────────────────
let kick = s("bd!4").gain(1.0).hpf(40).adsr("0.04:0.18:0.0:0.02").orbit(1)
let hat  = s("hh!8").gain(0.22).hpf(7000).adsr("0.001:0.04:0.0:0.04").orbit(2)
let bass = note("<[a1!8] [d2!8] [bb1!8] [c2!8] [g1!8] [f1!8] [a1!8] [d2!8]>")
    .sound("saw").legato(0.7)
    .hpf(80).lpf(saw.range(380, 1300).slow(48))
    .resonance(4).lpenv(1.5).lpadsr("0.005:0.08:0.1:0.05")
    .adsr("0.002:0.08:0.5:0.05")
    .distort("0.9:hard:4").postgain(0.22).warmth(0.25)
    .gain(0.55).orbit(0)
let core = stack(kick, hat, bass)

// ── Build layers ────────────────────────────────────────────────────
let sub  = note("<a1 d2 bb1 c2 g1 f1 a1 d2>").struct("x!2")
    .sound("sine").legato(1.0).adsr("0.005:0.05:0.5:0.05")
    .lpf(180).gain(0.35).orbit(6)
let clap = s("~ cp ~ cp").gain(0.3).hpf(300).orbit(4).room(0.2).rsize(3)
let oh   = s("[~ ~ ~ oh]!4").gain(0.22).hpf(5000).orbit(2)
let rim  = s("~ ~ rim ~ ~ ~ rim ~").gain(0.3).hpf(800).orbit(2)

// ── Lead phrases (5 shapes of the recorder melody) ──────────────────
let leadStyle = mel =>
    mel.sound("supersaw").unison(3).detune(0.07).euclid(3, 8)
       .lpf(sine.range(2200, 4400).slow(24)).resonance(2)
       .adsr("0.005:0.1:0.4:0.08").clip(0.7).distort(0.4).postgain(0.2)
       .delay(0.18).delaytime(pure(3/16).div(cps)).delayfeedback(0.32)
       .gain(0.4).orbit(3).room(0.2).rsize(3)
let leadA = leadStyle(note(`<[a4 c5 b4 a4] [d5 c5 a4 g4] [bb4 a4 g4 f4] [g4 e4 c4 a4]
                              [g4 bb4 d5 bb4] [f4 a4 c5 a4] [a4 c5 e5 c5] [d4 f4 a4 d5]>`))
let leadB = leadStyle(note(`<[a5 e5 a5 c5] [d5 a5 d5 f5] [bb4 d5 bb5 d5] [c5 g5 c5 e5]
                              [g4 d5 g5 d5] [c5 a4 f5 a4] [a4 c5 a5 e5] [d5 f5 a5 d5]>`))
let leadC = leadStyle(note(`<[a5 c6 b5 a5] [d6 c6 a5 g5] [bb5 a5 g5 f5] [g5 e5 c5 a5]
                              [g5 bb5 d6 g5] [a5 c6 f5 a5] [a5 e6 c6 a5] [d5 f5 a5 d6]>`))
let leadD = leadStyle(note(`<[d6 f6 g6 f6] [e6 c6 a5 g5] [d6 c6 d6 f6] [a5 g5 f5 e5]
                              [d6 g6 d6 bb5] [c6 a6 f6 a5] [a5 e6 c6 e6] [d6 f6 a5 d6]>`))
let leadE = leadStyle(note(`<[a6 ~ d6 ~] [a5 ~ d5 ~] [f5 ~ g5 ~] [e5 ~ ~ c5]
                              [d5@2 a4@2] [c5@2 g4@2] [a4@2 e4@2] [d4@4]>`))

// ── Pad ─────────────────────────────────────────────────────────────
let pad = chord("<Am Dm Bb C Gm F Am Dm>").voicing()
    .sound("supersaw").unison(2).detune(0.05).lpf(1300)
    .adsr("0.05:0.2:0.7:0.1").legato(1.0)
    .pan(0.3).superimpose(pan(0.7).transpose(12))
    .phaser(0.4).phaserdepth(saw.range(0.0, 0.6).slow(16))
    .phasersweep(900).phasercenter(1400)
    .gain(0.125).orbit(5).room(0.4).rsize(6)

// ── THE WIND (riser used inside a 16-cycle section so saw ramps once)
let riser = note("c").fast(2).sound("white")
    .lpf(saw.range(200, 5500).slow(16)).resonance(1.8)
    .adsr("0.005:0:1:0.05").legato(1.2)
    .gain(saw.range(0.0, 0.18).slow(16))
    .hpf(150).orbit(7)

// ── THE HIT (filterWhen t<1 makes it fire only at section-local cycle 0)
let hitKick = s("bd").gain(0.95).hpf(40).adsr("0.001:0.22:0.0:0.05").orbit(1)
let hitBass = note("d2").sound("saw").legato(0.7)
    .hpf(80).lpf(900).resonance(2.5)
    .adsr("0.002:0.22:0.0:0.1")
    .distort("0.8:hard:4").postgain(0.22).warmth(0.25)
    .gain(0.4).orbit(0)
let hitSub  = note("d1").sound("sine").legato(0.7)
    .adsr("0.005:0.3:0.0:0.15")
    .lpf(120).gain(0.45).orbit(6)
let crash = s("cr").gain(0.5).hpf(200).orbit(8).room(0.25).rsize(4)
let stab = chord("Dm").voicing()
    .sound("supersaw").unison(4).detune(0.08)
    .adsr("0.005:0.2:0.7:4.0").legato(2.0)
    .lpf(3500).distort(0.2).postgain(0.3)
    .pan(0.3).superimpose(pan(0.7).transpose(12))
    .gain(0.32).orbit(9)
    .room(0.4).rsize(5)
let hit = stack(hitKick, hitBass, hitSub, crash, stab).filterWhen(t => t < 1)

// ── QUIET BUILD-UP: 64 cycles, smooth saw-based morph ──────────────
// melody3 (peak of original lament) degrades out; melody1 (opening of
// lament) un-degrades in. Rhythm + bass elements ramp via saw signals.
let melody3a = note(`<[d6 f6 g6 f6] [e6 c6 a5 g5] [d6 c6 d6 f6] [a5 g5 f5 e5]
                      [d5 a4 d5 f5] [c5 g4 bb4 a4] [d5 c5 a4 g4] [d4@2 ~ ~]>`)
let melody3b = note(`<[f5 d6 e6 d6] [c6 a5 f5 e5] [f5 e5 f5 a5] [f5 e5 d5 c5]
                      [f4 f4 f4 d5] [a4 e4 g4 f4] [f4 e4 f4 e4] [f4@2 ~ ~]>`)
let melody1 = note(`<[d4 f4 e4 d4] [c4 a4 g4 f4] [d4 g4 f4 e4] [c4 bb4 a4 g4]
                     [d5 c5 bb4 a4] [g4 e4 d4 f4] [a4 g4 f4 e4] [d4 c4 d4 ~]>`)
let mel3 = stack(melody3a, melody3b)
    .sound("saw").legato(0.85).hpf(80).lpf(2500)
    .adsr("0.01:0.1:0.6:0.2").gain(0.2).orbit(0)
let mel1 = melody1
    .sound("saw").legato(0.85).hpf(80).lpf(2200)
    .adsr("0.01:0.1:0.6:0.2").gain(0.22).orbit(0)

// One big 64-cycle section. saw.slow(64) ramps 0 → 1 across it.
// Rhythm/bass build via clean gain ramps (no degrade — that was unpleasant).
// Only the two melodies cross-fade via degradeBy.
let quietBuild = stack(
    // Kick — clean gain ramp from soft to full
    s("bd!4").gain(saw.range(0.55, 1.0).slow(64))
        .hpf(40).adsr("0.001:0.18:0.0:0.02").orbit(1),
    // Hat — gain grows
    s("hh!8").gain(saw.range(0.15, 0.22).slow(64))
        .hpf(7000).adsr("0.001:0.04:0.0:0.04").orbit(2),
    // Sub bass — always present, gain grows
    note("<a1 d2 bb1 c2 g1 f1 a1 d2>").struct("x!2")
        .sound("sine").legato(1.0).adsr("0.005:0.05:0.5:0.05")
        .lpf(220).gain(saw.range(0.3, 0.4).slow(64)).orbit(6),
    // Saw bass — gain swells from silent to full
    note("<[a1!4] [d2!4] [bb1!4] [c2!4] [g1!4] [f1!4] [a1!4] [d2!4]>")
        .sound("saw").legato(0.7).hpf(80).lpf(800)
        .adsr("0.002:0.08:0.5:0.05").distort("0.4:hard:2").postgain(0.4)
        .gain(saw.range(0.0, 0.45).slow(64)).orbit(0),
    // Clap — gain swells in
    s("~ cp ~ cp").gain(saw.range(0.0, 0.35).slow(64))
        .hpf(300).orbit(4),
    // Open hat — gain swells in
    s("[~ ~ ~ oh]!4").gain(saw.range(0.0, 0.22).slow(64))
        .hpf(5000).orbit(2),
    // Melody 3 — degrades OUT (events removed more and more)
    mel3.degradeBy(saw.range(0, 1).slow(64)),
    // Melody 1 — un-degrades IN (events removed less and less)
    mel1.degradeBy(saw.range(1, 0).slow(64)),
)

// ── Sections ────────────────────────────────────────────────────────
let warm        = core
let withSub     = stack(core, sub)
let withPerc    = stack(core, sub, clap, oh, rim)
let s1 = stack(core, sub, clap, oh, rim, leadA)
let s2 = stack(core, sub, clap, oh, rim, leadB, pad)
let s3 = stack(core, sub, clap, oh, rim, leadC, pad)
let s4 = stack(core, sub, clap, oh, rim, leadD, pad)
let finale = stack(core, sub, clap, oh, rim, leadE, pad, riser)
let pause = silence

arrange(
  [16, warm],         // 0-15: LOOP POINT — kick + hat + bass already running
  [8, withSub],       // 16-23: + sub
  [8, withPerc],      // 24-31: + clap + oh + rim
  [8, s1],            // 32-39: + leadA
  [8, s2],            // 40-47: + leadB + pad
  [8, s3],            // 48-55: + leadC + pad
  [8, s4],            // 56-63: + leadD + pad
  [16, finale],       // 64-79: + leadE (loops once) + pad + 16-cycle wind fade
  [2, hit],           // 80-81: unison hit lands on beat 1 right as wind ends
  [2, pause],         // 82-83: silence (stab tail rings into the quiet section)
  [64, quietBuild]    // 84-147: smooth morph — mel3 fades OUT, mel1 fades IN
                      //         beats + bass build via saw-controlled gains
).compressor("-15:6:6:0.005:0.15")
 .room(0.1).rsize(4)

// Inspired by: The Synthsale Piper's Farewell — gone clubbing
// Composed by: Claude, Motör, peekandpoke
            
            """
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

// Only press play, when you would go to a heavy metal live concert ...

stack(
  // Guitar 1
  cat(
    n(`<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13] [3,10,15]@2 [5,12,17]] [0,7,12]
        [12,19,24] [[6,13,18] [6,13,18]] [[6,13,18] [5,12,17]@2 [6,13,18]]>`).repeat(4),
    n(`<[[0,7]!4 [0,7]@4]!8 [[0,6]!4 [0,6]@4]!4 [[0,7]!4 [0,7]@4]!4 [[0,7]!4 [0,7]@4]!9 [[0,7]!4 [0,8]@4]!7>`)
  )
  .fast(4).scale("e2:chromatic").clip(0.975).hpf(60).lpf(3500).pan(0.5).adsr("0.01:0.3:0.5:0.1")
  .notchf("1440:2:60")
  .s("[superpluck pulse]/16").unison(6).detune(0.02).gain(1.0).distort(4).postgain(0.1).warmth(0.1)
  .superimpose(
    x => x.bandf(300).bandq(1.0),
    x => x.bandf(880).bandq(sine.range(0.5, 1*1.5).slow(50)).sound("pulse"),
    x => x.bandf(1080).bandq(sine.range(0.5, 2*1.25).slow(40)).sound("pulse"),
  ).orbit(1) // .solo()

  , // Guitar 2
  cat(
    n("<~!8>").repeat(4),
    n(`<[36!8]!4 [35!8]!4 [24!8]!4 [25!8] [25!4 28!4] [28!4 29!4] [29!4 31!4]
        [32!8] [32!4 36!4] [36!8]!2 [31!8]!4 [30!8]!4 [27!8] [27!4 26!4] [26!4 25!4] [25!4 24!4]
    >`).delay("<~!24 0.5::0.5!8>").delaytime(pure(1).div(cps)),
  ).orbit(2).pan(0.5).scale("e2:chromatic")
  .fast(4).clip(0.5).hpf(200).lpf("1800").lpadsr("0.05:0.5:0.1:0.02").adsr("0.1:0.3:0.1:0.025")
  .s("[[supersqr supersaw]!16]").gain(0.9).distort(3).postgain(0.1).warmth(0.2)
  .superimpose(x => x.notchf("720")) // . solo()
   // .mute()

  // Drums 1
  , s("<hh!8!8 [oh rd!5 cr hh]!2!8>").adsr("0.00:0.1:0.5:0.1").hpf(200).postgain(1.25) // .solo()
  // Drums 2
  , s(`<[[bd sd]!2]!4 [bd [bd,sd] bd [bd,sd]]!3 [bd bd [bd,sd] bd  [sd]!4]!1
        [[bd sd]!16]!8>`).n("<7!8 0!8>").adsr("0.00:0.3:0.5:0.1").gain(1.0).hpf(30)  // . solo()
)
  .room(0.3).rsize(5)
  .compressor("-10:2:10:0.02:0.25")
  .accelerate(saw.seg(8).pow(10).mul(0.05).add(0.0).slow(16))
  
  
          """.trimIndent(),
            icon = "guitar",
        )
    )
}
