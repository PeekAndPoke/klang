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
let feel = 1.5   // 0.0 .. mechanical | 10.0 .. old vinyl                                                                     //    //
                                                                                                                             //      //
                                                                                                                            //        //
stack(                                                                                                         //////////////          //////////////
  // Lead                                                                                                        //                              //
  n(`<[-7 0 2 4] [-7 0 4 [2 6]|[4 2]|2|2|2|2] [-5 -1 2 4] [-6 -1 [4 3]|5|3|3|3 [1 -1]|1|1|1|1]>*2`)                //          DISCO!          //
    .orbit(1).scale("e4:minor").sound("supersaw").unison(9).detune(0.07).analog(feel)                                //       FOREVER!       //
    .hpf(1400).lpf(3200).lpe(berlin.range(1.1, 1.2)).lpq(1.5).lpadsr("0.03:0.1:0.7:0.15")                              //                  //
    .gain(0.90).distort("1.0:tube:4").postgain(0.750) // . solo()                                                       //       //      //
    .adsr("0.01:0.2:0.5:0.15").clip(0.91) //  . mute()                                                                 //     //    //    //
    .release("<0.105!16 0.4!16 0.110!16 0.325!16 0.16!16 0.35!16 0.09!16 0.4!16 0.095!16 0.5!16>")                    //   //          //  //
    .phaser(1/8).phaserdepth("<0.1!64 0.20!16 0.1!48>").phasersweep(1000).phasercenter(1800)                         // //               // //
    .shuffle("<1!64 0!16 1!1 4/8!14 1!33>")                                                                         //                       //
    .superimpose(x => x.transpose(12).detune(0.10).velocity("<0!32 0.15!32>").pan(0.4).late(0.001),
                 x => x.transpose(12).detune(0.15).velocity("<0!32 0.15!32>").pan(0.6).late(0.0015))
    .mute("<1!32 0!256>").engine("pedal"),
  // Guitar 1
  n(`<[7 4 2 <4 -1 4 3> [0 -1 -3 -1] [0 -3] -2 <[-1 4@3] [5 6@3] [1 2@3] [2 6@3]>]!4
      [[4 2] [-1 -3] 0 [2 [2 6@3]]]!2 [[0 -3] [-1 -3] 0 <[4 6] [2 3]>] [<7 4> [-5 -6] -7 [-2 <3 -1>]]>/4`)
    .orbit(1).struct("<[x!16]!7 [x!24]!1 [x!16]!16>").velocity("1.00 0.95!3 0.98 0.95!3".fast(2)).analog(feel)
    .scale("<e2:minor!48 e3:minor!16>").sound("supersaw").unison(15).detune(0.10)
    .distort("1.0:soft:4").distort(saw.range(1.5, 1.7).slow(4*32).pow(1.3))
    .adsr("0.006:0.2:0.3:0.15").adsrCurves("cube:exp:scurve").lpadsr("0.005:0.2:0.1:0.20")
    .clip(0.62)  // . solo()
    .gain(0.8).postgain(0.3).hpf(400).lpf(saw.range(3000, 1500).slow(4)).lpe(1.0).lpq(1.5)
            .scaleTranspose(7).pan(0.45).superimpose(pan(0.55))
    .engine("pedal")
  ,
  // Guitar 2
  n("<0 0 2 4 0 0 -2 -1>").struct("<[x!8]!14 [x!12]!2 [x!8]!32>").fast(2).velocity("1.00 0.95!3 0.98 0.95!3".fast(2))
    .orbit(2).scale("<e2:minor>").analog(feel).sound("supersaw").unison(5).detune(0.07)
    .hpf(160).lpf(2000).lpe(1.0).lpq(1.5)
    .adsr("0.01:0.5:0.3:0.05").adsrCurves("cube:exp:scurve").lpadsr("0.001:0.2:0.1:0.05")
    .clip(0.62).gain(0.8).distort("1:hard:4").distort(saw.range(1.5, 1.7).slow(4*32).pow(1.3))
    .coarse(3).coarseos(2)    . solo()
    .pan(0.40).postgain(0.35)
    .superimpose(
      x => x.pan(0.6),
      x => x.unison(4).postgain(0.35).hpf(160).lpf(2000).lpe(0.5)
            .scaleTranspose("<4!7 [2 [3 4@3]]!1 4!7 [-3 [-4 -3@3]]>").pan(0.3).superimpose(pan(0.7))
    ).mute("<0!128 1!16 0!16>").engine("pedal")
  , // Bass
  n("<0 0 2 4 0 0 -2 -1>").struct("<[x!2]!16 [x!4]!16 [x x@2 x]!16>").fast(2).velocity("1.00 0.95!3 0.98 0.95!3".fast(2))
    .scale("<e2:minor!88 e3:minor!8>").sound("saw").gain(0.5).distort("0.2:tube:1").coarse(2).postgain(0.6).clip(0.7)
    .adsr("0.009:0.1:0.66:0.15").hpf(80).lpf(160).lpe(1).lpadsr("0.001:0.5:0.1:0.22") // .solo()
    .mute("<0!128 1!32>") // .engine("pedal")
  , // Drums
  sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd ~  bd ~ ]!24 [bd bd bd bd]!24>").mute("<0!128 1!32>") // . solo()
    .early(0.002).orbit(5).gain(0.90).hpf(80).lpf(7500).adsr("0.005:0.15:0.1:0.1"),
  sound("<[~!2]!2  [~!4]!2  [~!8]!2  [~!16]  [~!24]  [~  sd ~  sd]!24 [~  sd ~  sd]!24>").mute("<0!128 1!32>")// . solo()
    .early(0.002).orbit(5).gain(0.95).hpf(180).lpf(7500).adsr("0.006:0.25:0.1:0.1").superimpose(bandf(500).gain(0.6)),
  sound("<[hh hh oh hh]!48 [cr hh cr hh]!16 [0 hh 0 hh]!16>").fast(2).mute("<0!128 1!32>")  // . solo()
    .late(0.004).orbit(5).gain(1.05).hpf(2000).lpf(7500).adsr("0.01:0.2:0.5:0.2")
  // Master
).room("0.02:5").compressor("-10:2:10:0.02:0.25").seed(timeOfDay.mul(60*60*24))


// Inspired by: Editors - Papillon
// https://open.spotify.com/intl-de/track/7hYiX6LMP8w8d0kEc4KWuW




// Written by: peekandpoke

// Epilepsy Warning: Do not click the oscilloscope!







            """,
)
