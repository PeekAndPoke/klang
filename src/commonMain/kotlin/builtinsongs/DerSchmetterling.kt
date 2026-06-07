@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

internal val derSchmetterlingSong = Song(
    id = "${BuiltInSongs.PREFIX}-der-schmetterling",
    title = "Der Schmetterling",
    rpm = 34.0,
    icon = "bug",
    code = """
import * from "stdlib"                                                                                                           //
import * from "sprudel"                                                                                                         ////
                                                                                                                               //  //
let feel = 1.0   // 0.0 .. mechanical | 10.0 .. old vinyl                                                                     //    //
                                                                                                                             //      //
                                                                                                                            //        //
stack(                                                                                                         //////////////          //////////////
  // Lead                                                                                                        //                              //
  n(`<[-7 0 2 4] [-7 0 4 [2 6]|[4 2]|2|2|2] [-5 -1 2 4] [-6 -1 [4 3]|5|3|3|3 [1 -1]|1|1|1|1]>*2`)                  //          DISCO!          //
    .orbit(0).scale("<e4:minor!48 e5:minor!16>").sound("superramp").unison(11).detune(0.03).analog(feel)             //       FOREVER!       //
    .hpf(1250).lpf(1520).lpe(berlin.range(1.8, 1.8)).lpq(1.8).lpadsr("0.001:2.0:0.2:0.01")                             //                  //
    .gain(1.00).distort("0.300:tube:4").postgain("<0.700!48 0.320!16>") // . solo()                                     //       //      //
    .adsr("0.01:3.0:0.0:0.01").adsrCurves("exp:exp:exp").clip(0.8)  // . mute()                                        //     //    //    //
    .release("<0.20!16 0.35!16>")                                                                                     //   //          //  //
    .phaser(1/8).phaserdepth(0.05).phasersweep(1000).phasercenter(1800)                                              // //               // //
    .shuffle("<1!64 0!16 1!1 4/8!14 1!33>").coarse(2).coarseos(8)                                                   //                       //
    .superimpose(x => x.transpose(12).detune(0.10).velocity("<0!32 0.15!32>").pan(0.3),
                 x => x.transpose(12).detune(0.15).velocity("<0!32 0.15!32>").pan(0.7))
    .mute("<1!32 0!256>").engine("pedal"),
  // Guitar 1
  n(`<[7 4 2 <-1 4 1 3> [0 -1 -3 -1] [0 -3] -2 <[-1 4@3] [5 6@3] [4 7@3] [4 6@3]>]!4
      [[4 2] [-1 -3] 0 [2 [2 6@3]]]!2 [[0 -3] [-1 -3] 0 <[4 6] 0>] [<7 [[7 4 6 4]!4]> [-5 -6] -7 [-2 <3 -1>]]>/4`) // . solo()
    .orbit(1).scale("<e3:minor!48 e4:minor!16 e3:minor!48 e4:minor!16>").struct("<[x!16]!7 [x!24]!1 [x!16]!16>") // .mute()
    .velocity("1.00 0.95!3 0.98 0.95!3".fast(2)).analog(feel)
    .sound("supersaw").unison(19).detune(0.07)
    .distort("1.0:tube:4").distort(1.0)
    .adsr("0.007:2.0:0.1:0.01").adsrCurves("exp:exp:exp").lpadsr("0.007:1.0:0.0:0.01")
    .clip("<0.96!31 0.9 0.96!31 0.825>".fast(2))
    .gain(0.6).postgain(0.33).hpf("<320!48 700!16 320!32 700!32>").lpf(saw.range(1,0).pow(1.8).mul(1200).add(1500).slow(4)).lpe(1.0).lpq(1.25)
    .pan(0.2).superimpose(pan(0.8))
    .engine("pedal")
  ,
  // Guitar 2
  n("<0 0 2 4 0 0 -2 -1>")  //  . solo()
    .orbit(2).scale("<e2:minor>").struct("<[x!8]!14 [x!12]!2 [x!8]!32>").fast(2)
    .analog(feel).sound("supersaw").unison(13).detune(0.05)
    .hpf(180).lpf(1100).lpe(2.0).lpq(1.25)
    .adsr("0.007:2.0:0.0:0.01").adsrCurves("exp:exp:exp").lpadsr("0.007:1.0:0.0:0.01").velocity("1.00 0.95!3 0.98 0.95!3".fast(2))
    .clip("<0.97!31 0.9 0.965!31 0.825>".fast(2)).gain(0.8).distort("1:tube:4").distort(1.0)
    .coarse(2).coarseos(4)  
    .pan(0.35).postgain(0.27).superimpose(
      x => x.pan(0.65),
      x => x.orbit(2).late(0.002).postgain(0.24).hpf(180).lpf(1100) // .lpe(1.0)
            .scaleTranspose("<4!7 [2 [3 4@3]]!1 4!7 [-3 [-4 -3@3]]>").pan(0.3).superimpose(pan(0.7))
    ).mute("<0!128 1!16 0!16>").engine("pedal")
  , // Bass
  n("<0 0 2 4 0 0 -2 -1>").struct("<[x!1]!32 [x@3 x]!32 [x!4]!64>").fast(2).velocity("1.00 0.95!3 0.98 0.95!3".fast(2)) // . mute()
    .scale("<e2:minor!88 e3:minor!8>").sound("saw").gain(0.5).distort("0.2:tube:1").coarse(2).postgain(0.45).clip(1.0)
    .adsr("0.005:1.0:0.0:0.15").adsrCurves("exp:exp:exp").hpf(80).hpq(1).lpf(80).lpe(1).lpadsr("0.007:0.2:0.0:0.15")  // .solo()
    .mute("<0!128 1!32>") // .engine("pedal")
  , // Drums
  sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd ~  bd ~ ]!24 [bd bd bd bd]!24>").mute("<0!128 1!32>") // . solo()
    .early(0.002).orbit(5).gain(0.75).hpf(80).lpf(5000).adsr("0.005:0.15:0.1:0.1"),
  sound("<[~!2]!2  [~!4]!2  [~!8]!2  [~!16]  [~!24]  [~  sd ~  sd]!24 [~  sd ~  sd]!24>").mute("<0!128 1!32>")// . solo()
    .early(0.002).orbit(5).gain(0.90).hpf(180).lpf(7000).adsr("0.006:0.25:0.1:0.1").superimpose(bandf(220).bandq(4).gain(0.4)),
  sound("<[hh hh oh hh]!48 [cr hh cr hh]!16 [0 hh 0 hh]!16>").fast(2).mute("<0!128 1!32>")  // . solo()
    .late(0.004).orbit(5).gain(0.95).hpf(3000).lpf(6200).adsr("0.01:0.2:0.5:0.2")
  // Master
).room("0.3:5").compressor("-10:2:10:0.02:0.25").seed(timeOfDay.mul(60*60*24))


// Inspired by: Editors - Papillon
// https://open.spotify.com/intl-de/track/7hYiX6LMP8w8d0kEc4KWuW




// Written by: peekandpoke

// Epilepsy Warning: Do not click the oscilloscope!









            """,
)
