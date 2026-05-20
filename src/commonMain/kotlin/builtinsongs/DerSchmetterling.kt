@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

internal val derSchmetterlingSong = Song(
    id = "${BuiltInSongs.PREFIX}-der-schmetterling",
    title = "Der Schmetterling",
    rpm = 32.0,
    icon = "bug",
    code = """
import * from "stdlib"                                                                                                           //
import * from "sprudel"                                                                                                         ////
                                                                                                                               //  //
let feel = 3.0   // 0.0 .. mechanical | 10.0 .. old vinyl                                                                     //    //
let snd  = 400 // sine.range(125, 300).slow(11).add(sine.range(125, 300).slow(13))   // 100 .. 2000                          //      //
                                                                                                                            //        //
stack(                                                                                                         //////////////          //////////////
  // Lead                                                                                                        //                              //
  n("<[-7 0 2 4] [-7 0 4 2] [-5 -1 2 4] [-6 -1 3 1]>*2")                                                           //          DISCO!          //
    .orbit(0).scale("e4:minor").sound("supersaw").unison(16).detune(0.08)                                            //       FOREVER!       //
    .hpf(650).lpf(1500).lpe(3.5).lpadsr("0.02:0.2:0.7:0.1").warmth(0.3)                                                //                  //
    .gain(0.70).distort("0.1:gentle:2").postgain(0.59) // . solo()                                                      //       //      //
    .adsr("0.02:0.2:0.7:0.1").clip(0.8)                                                                                //     //    //    //
    .release("<0.105!16 0.4!16 0.110!16 0.325!16 0.16!16 0.35!16 0.09!16 0.4!16 0.095!16 0.5!16>")                    //   //          //  //
    .superimpose(                                                                                                    // //               // //
      x => x.shuffle("<1!64 0!16 1!1 4/8!14 1!33>").seed(sinOfNight.add(1).mul(24 * 60 * 15))                       //                       //
        .superimpose(transpose(12).detune(0.12).velocity("<0!32 0.20!32>").lpf(1800).pan(0.3))
        .superimpose(transpose(24).detune(0.20).velocity("<0!96 0.10!32>").lpf(2100).pan(0.7))
    ).phaser(1/8).phaserdepth("<0.05!64 0.4!16 0.1!48>").phasersweep(1000).phasercenter(1500).mute("<0!128 1!32>"),
  // Pad
  n("<[0 0 2 <4 -1 4 3> 0 0 -2 -1]!4 [0 [2 4] 0 [2 [2 -1@3]]]!2 [0 [6 4] 0 <[2 3] [-2 0]>] [<0 4> [2 1] 0 [-2 <-1 -4>]]>/4")
    .struct("<[x!16]!7 [x!24]!1 [x!16]!16>").velocity("1.02 0.95!3 0.98 0.95!3".fast(2))
    .scale("<e2:minor!48 e3:minor!16>").sound("supersaw").unison(2).detune(0.05).distort("0.1:soft:2")
    .lpf("<1350!48 2250!16>").hpf("<180!48 320!16>").warmth(saw.range(0.3, 0.1).slow(5*32))
    .distort("0.3:gentle:2").distort(sine.range(0.15, 0.3).slow(10*32))
    .adsr("0.0175:0.15:0.45:0.12").clip("<0.55!48 0.6!16>").crush(saw.range(8, 4).slow(32))  // . solo()
    .gain(0.36).orbit(1).pan(0.4).lpe(0.5)
    .superimpose(scaleTranspose(7).gain(0.1).pan(0.2).hpf(400).lpe("1.8"), scaleTranspose(7).gain(0.1).pan(0.8).hpf(400).lpe("1.8"))
  ,
  // Bass
  n("<0 0 2 4 0 0 -2 -1>").struct("<[x!8]!14 [x!12]!2 [x!8]!32>").fast(2).velocity("1.02 0.95!3 0.98 0.95!3".fast(2))
    .scale("<e2:minor!28 e3:minor!4>")
    .sound("saw").warmth(saw.range(0.3, 0.1).slow(5*32))
    .notchf(snd).notchq(0.75).lpf(saw.range(900, 1300).slow(4)).hpf(100).lpe(1.2).distort("0.1:soft:2")
    .adsr("0.015:0.15:0.3:0.12").clip("<0.55!48 0.6!16>") //  . solo()
    .gain(0.38).orbit(2).pan(0.5).mute("<0!160 1!16 0!16>").lpe(0.5)
    .superimpose(scaleTranspose(4).gain(0.25).pan(0.1).hpf(160).lpe(1.5), scaleTranspose(4).gain(0.25).pan(0.9).hpf(160).lpe(1.5))
  ,
  // Drums
  sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd sd bd sd]!24 [bd [bd,sd] bd [bd,sd]]!24>").mute("<0!160 1!32>")// . solo()
    .early(0.002).orbit(3).gain(1.0).crush(10).crushos(2).hpf(90).lpf("2300:1:1.5").lpe("<2!128 2.3!128 2.7!128>").adsr("0.005:0.2:0.5:0.1"),
  sound("<[hh hh oh hh]!48 [cr hh cr hh]!16 [0 hh 0 hh]!32>").fast(2).mute("<0!160 1!32>") //. solo()
    .late(0.004).orbit(4).gain(0.9).crush(10).crushos(2).hpf(800).lpf("3500:1:1.5").adsr("0.01:0.2:0.5:0.1")
  // Master
).compressor("-6:2:10:0.02:0.25").analog(feel).engine("pedal").room("0.01:5")


// Inspired by: Editors - Papillon
// https://open.spotify.com/intl-de/track/7hYiX6LMP8w8d0kEc4KWuW




// Written by: peekandpoke

// Epilepsy Warning: Do not click the oscilloscope!











            """,
)
