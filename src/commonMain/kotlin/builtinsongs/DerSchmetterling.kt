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
let snd  = 400  // sine.range(125, 300).slow(5).add(sine.range(125, 300).slow(13))   // 100 .. 2000                          //      //
                                                                                                                            //        //
stack(                                                                                                         //////////////          //////////////
  // Lead                                                                                                        //                              //
  n("<[-7 0 2 4] [-7 0 4 2] [-5 -1 2 4] [-6 -1 3 1]>*2")                                                           //          DISCO!          //
    .orbit(0).scale("e4:minor").sound("supersaw").unison(8).detune(0.10)                                             //       FOREVER!       //
    .hpf(800).lpf(1200).lpe(5.0).lpadsr("0.02:0.2:0.65:0.1").warmth(0.3)                                               //                  //
    .gain(0.70).distort("0.1:gentle:2").postgain(0.52) // . solo()                                                      //       //      //
    .adsr("0.02:0.2:0.65:0.1").clip(0.95)                                                                              //     //    //    //
    .release("<0.105!16 0.4!16 0.110!16 0.325!16 0.16!16 0.35!16 0.09!16 0.4!16 0.095!16 0.5!16>")                    //   //          //  //
    .superimpose(                                                                                                    // //               // //
      x => x.shuffle("<1!64 0!16 1!1 4/8!14 1!33>").seed(sinOfNight.add(1).mul(24 * 60 * 15))                       //                       //
        .superimpose(transpose(12).detune(0.15).velocity("<0!32 0.20!32>").lpf(1800).pan(0.3))
        .superimpose(transpose(24).detune(0.20).velocity("<0!96 0.10!32>").lpf(2100).pan(0.7))
    ).phaser(1/8).phaserdepth("<0.05!64 0.4!16 0.1!48>").phasersweep(1000).phasercenter(1500).mute("<0!160 1!32>"),
  // Pad
  n("<[0 0 2 <4 -1 4 3> 0 0 -2 <-1 3 6 -2>]!4 [0 [2 4] 0 [2 [2 -1@3]]]!2 [0 [6 4] 0 <[5 6] [-2 0]>] [<0 4> [2 1] 0 [-2 <-1 -4>]]>/4")
    .struct("<[x!16]!7 [x!24]!1 [x!16]!16>").velocity("1.02 0.95!3 0.98 0.95!3".fast(2))
    .scale("<e2:minor!48 e3:minor!16>").sound("supersaw").unison(4).detune(0.07).warmth(saw.range(0.2, 0.0).slow(5*32))
    .lpf("<1100!48 1750!16>").lpq(0.8).lpe(2.0).hpf("<140!48 320!16>")
    .distort("2.5:tube:8").distort(sine.range(0.3, 0.6).slow(10*32))
    .adsr("0.01:0.15:0.66:0.15").lpadsr("0.01:0.15:0.66:0.15").clip("<0.6!48 0.7!16>").crush(saw.range(8, 4).slow(32)) // . solo()
    .gain(0.2).postgain(1.8).orbit(1).pan(0.4).late(0.002)
    .superimpose(scaleTranspose(7).postgain(1.35).pan(0.1).hpf(400).lpe(1.5).superimpose(pan(0.9)))
  ,
  // Bass
  n("<0 0 2 4 0 0 -2 -1>").struct("<[x!8]!14 [x!12]!2 [x!8]!32>").fast(2).velocity("1.02 0.95!3 0.98 0.95!3".fast(2))
    .scale("<e2:minor!28 e3:minor!4>")
    .sound("supersaw").unison(3).detune(0.07).warmth(saw.range(0.2, 0.0).slow(5*32))
    .notchf(snd).notchq(0.5)
    .lpf(saw.range(700, 1200).slow(4)).lpq(0.8).lpe(2.0).hpf(80).distort("2.5:tube:8")
    .adsr("0.01:0.15:0.66:0.15").lpadsr("0.01:0.15:0.66:0.15").clip("<0.6!48 0.65!16>")  // . solo()
    .gain(0.2).postgain(1.5).orbit(2).pan(0.5).mute("<0!128 1!16 0!16>")
    .superimpose(scaleTranspose(4).postgain(0.8).pan(0.33).lpe(1.25).hpf(160).superimpose(pan(0.66)))
  ,
  // Drums
  sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd sd bd sd]!24 [bd [bd,sd] bd [bd,sd]]!24>").mute("<0!128 1!32>")// . solo()
    .early(0.002).orbit(3).gain(1.0).crush(10).crushos(2).hpf(80).lpf("2300:1:1.5").lpe("<2!128 2.3!128 2.7!128>").adsr("0.005:0.2:0.5:0.1"),
  sound("<[hh hh oh hh]!48 [cr hh cr hh]!16 [0 hh 0 hh]!16>").fast(2).mute("<0!128 1!32>") //. solo()
    .late(0.004).orbit(4).gain(0.95).crush(10).crushos(2).hpf(800).lpf("3800:1:1.5").adsr("0.01:0.2:0.5:0.2")
  // Master
).compressor("-6:2:10:0.02:0.25").analog(feel).engine("pedal").room("0.1:5")


// Inspired by: Editors - Papillon
// https://open.spotify.com/intl-de/track/7hYiX6LMP8w8d0kEc4KWuW




// Written by: peekandpoke

// Epilepsy Warning: Do not click the oscilloscope!












            """,
)
