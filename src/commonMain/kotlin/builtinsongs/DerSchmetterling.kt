/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

internal val derSchmetterlingSong = Song(
    id = "${BuiltInSongs.PREFIX}-der-schmetterling",
    title = "Der Schmetterling",
    rpm = 34.5,
    icon = "bug",
    code = """
import * from "stdlib"                                                                                                                   //
import * from "sprudel"                                                                                                                 ////
                                                                                                                                       //  //
let feel = 5.0    // 0.0 .. ice | 100.0 .. fire                                                                                       //    //
                                                                                                                                     //      //
                                                                                                                                    //        //
stack(                                                                                                                 //////////////          //////////////
  // Lead                                                                                                                //                              //
  n(`<[-7 0 2 4] [-7 0 4 [2 6]|[4 2]|2|2|2] [-5 -1 2 4] [-6 -1 [4 3]|5|3|3|3 [1 -1]|1|1|1|1]>*2`)                          //          DISCO!          //
    .orbit(0).scale("<e4:minor!48 e5:minor!16>").sound("superramp").unison(5).detune(0.08).analog(feel)                      //       FOREVER!       //
    .hpf(1550).lpf(1550).lpe(berlin.range(2, 2.03).fast(4)).lpq(2.2).lpadsr("0.007:1.1:0.0:0.01")                              //                  //
    .gain(0.50).distort("0.570:tube:4").postgain("<0.210!48 0.110!16>") // . solo()                                             //       //      //
    .adsr("0.007:4.0:0.0:0.01").clip(0.89)  // . mute()                                                                        //     //    //    //
    .release("<0.04!16 0.11!16>").vibrato(8).vibmod(0.01)                                                                     //   //          //  //
    .shuffle("<1!64 0!16 1!1 4/8!14 1!33>")                                                                                  // //               // //
    .superimpose(x => x.transpose(0).detune(0.12).mute("<1!16 0!16>").velocity(0.10).pan(0.1).superimpose(pan(0.9)))        //                       //
    .mute("<1!32 0!192>").engine("pedal").room("0.3:5:0.1"),
  // Guitar 1
  n(`<[7 4 2 1 [0 -1 -3 -1] [0 -3] -2 <[-1 5@3] [5 6@3] [[4 5] 8@3] [[3 4] 3@3]>]!4
      [[4@2 [2 0] 0] [-1 -4] [-3 1 -3 1 -3!10 1 -3] [2 [2 6@3]]]!2
      [[-3,-7] [[-4,-5] [-1,-3]] [0,-3] <[[4 6],[0 -1]] [0,-1]>] [<[7,4] [[7 4 6 2]!4]> [-5 -6] [-7,-14] [-5 <-1 -4 -4 1>]]>/4`)
    .orbit(1).scale("<e3:minor!48 e4:minor!16 e3:minor!48 e4:minor!16>").struct("<[x!16]!7 [x!24]!1 [x!16]!16>") //  .mute()
    .velocity("0.98 0.95!7 0.97 0.95!7".fast(2)).analog(feel)  // . solo()
    .sound("supersaw").unison(9).detune(0.08).gain(0.75).postgain(0.14).distort("1:tube:4").distort(0.7)    
    .clip("<0.86!31 0.78 0.86!31 0.85 0.86!30 0.81 0.71>".fast(2)).adsr("0.006:2.5:0.0:0.032").lpadsr("0.006:1.0:0.0:0.01")    
    .hpf("<450!16 380!16 450!16 750!16>").lpf("3000".add(saw.range(1, 0).pow(1.8).mul(800)).slow(4)).lpe(0.5).lpq(1.2)
    .coarse(2).coarseos(4).pan(0.15).superimpose(pan(0.85))
    .engine("pedal").body("wood").bodyMix(0.2)
  ,
  // Guitar 2
  n("<0 0 2 4 0 0 -2 -1>")  //  . solo()
    .orbit(1).scale("<e2:minor>").struct("<[x!8]!14 [x!12]!2 [x!8]!32>").fast(2)
    .velocity("0.98 0.95!7 0.97 0.95!7".fast(2)).analog(feel)
    .sound("supersaw").unison(7).detune(0.09).gain(0.75).postgain(0.13).distort("1:tube:4").distort(0.7)
    .clip("<0.86!31 0.78 0.86!31 0.85 0.86!30 0.81 0.71>".fast(2)).adsr("0.006:2.5:0.0:0.032").lpadsr("0.006:1.0:0.0:0.01")    
    .hpf(160).lpf(2800).lpe(0.5).lpq(1.1)
    .coarse(2).coarseos(4).pan(0.3).superimpose(
      x => x.pan(0.7),
      x => x.postgain(0.13).scaleTranspose("<4!7 [2 [3 4@3]]!1 4!7 [-3 [-4 -3@3]] 4!7 [2 [3 4@3]]!1 4!7 [-3 [2 4@3]]>")
           .pan(0.25).superimpose(pan(0.75))
    ).mute("<0!128 1!16 0!16>").engine("pedal").body("wood").bodyMix(0.20)
  , // Bass
  n("<0 0 2 4 0 0 -2 -1>").struct("<[x!1]!16 [x@3 x]!48 [x!4]!80>").fast(2).velocity("0.98 0.98 0.99 0.98".fast(2))  // . mute()
    .orbit(4).scale("e1:minor").sound("saw").gain(0.5).distort("0.1:soft:2").postgain(0.20).clip(0.7)
    .adsr("0.004:2.5:0.0:0.015").lpadsr("0.004:0.1:0.0:0.01").hpf(60).lpf(200).lpe(10)  //  .solo()
    .coarse(2).pan(0.50).mute("<0!128 1!32>") // .engine("pedal")
  , // Drums
  sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd  ~ bd  ~]!32 [bd!4]!16 [bd ~ bd [~ bd]]!15 [bd!]!1>").mute("<0!128 1!32>")  // . solo()
    .pan(0.5).orbit(5).gain(0.24).hpf(35).lpf(7500).adsr("0.004:0.15:0.5:0.2"),
  sound("<[~!2]!2  [~!4]!2  [~!8]!2  [~!16]  [~!24]  [~  sd  ~ sd]!32 [~ sd ~ sd]!32>").mute("<0!128 1!32>")  // . solo()
    .pan(0.475).late(0.002).orbit(6).gain(0.23).hpf(180).lpf(8200).adsr("0.005:0.15:0.2:0.2")
    .superimpose(x => x.bandf(200).bandq(4).vel(0.80).lpf(5000)),
  sound("<[hh hh hh hh]!16 [hh hh oh hh]!24 [cr hh cr hh]!24 [~ rd ~ rd]!32>").fast(2).mute("<0!128 1!32>") // . solo()
    .pan(0.525).late(0.0005).orbit(7).gain(0.32).hpf(800).lpf(7500).adsr("0.005:0.15:0.8:0.2"), // . mute()
  sound("pink!8").gain(0.02).pan(sine.range(0.35, 0.65).slow(3)).adsr("0.008:1.0:0.0:0.02")  // .solo(),
  // Master
).room("0.10:8:0.12").rlp(12500).seed(timeOfDay.mul(60*60*24))
 .compressor("-6:2:5:0.02:0.05")


// Inspired by: Editors - Papillon
// https://open.spotify.com/intl-de/track/7hYiX6LMP8w8d0kEc4KWuW




// Written by: peekandpoke

// Epilepsy Warning: Do not click the oscilloscope!











    """,
)
