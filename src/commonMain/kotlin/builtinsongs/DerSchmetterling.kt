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
    rpm = 33.8625,
    icon = "bug",
    code = """
import * from "stdlib"                                                                                                                         //.
import * from "sprudel"                                                                                                                       ////.
                                                                                                                                             //  //.
let feel = 20.0    // 0.0 .. ice | 100.0 .. fire                                                                                            //    //.
                                                                                                                                           //      //.
                                                                                                                                          //        //.
stack(                                                                                                                       //////////////          //////////////.
  // Lead                                                                                                                      //                              //.
  n(`<[-7 0 2 4] [-7 0 4 [2 6]|[4 2]|2|2|2] [-5 -1 2 4] [-6 -1 [4 3]|5|3|3|3 [1 -1]|1|1|1|1]>*2`)                                //          DISCO!          //.
    .orbit(0).scale("<e4:minor!48 e5:minor!16 e4:minor!48 e3:minor!16>").sound("superramp").unison(5).spread(0.08)                 //       FOREVER!       //.
    .hpf(1800).lpf(1650).lpe(berlin.range(2, 2.10).fast(4)).lpq(2.5).lpadsr("0.007:1.5:0.0:0.02")                                    //                  //.
    .gain(0.60).distort("0.460:tube:4").postgain("<0.200!48 0.100!16 0.200!48 0.300!16>") // . solo()                                 //       //      //.
    .adsr("0.007:5.0:0.0:0.02").clip(0.89)  // . mute()                                                                              //     //.   //    //.
    .release("<0.04!16 0.11!16>").vibrato(8).vibmod(0.01)                                                                           //   //.         //  //.
    .shuffle("<1!64 0!16 1!1 4/8!14 1!33>")                                                                                        // //.              // //.
    .superimpose(x => x.transpose(12).spread(0.12).mute("<1!16 0!16>").velocity(0.30).pan(0.35).superimpose(pan(0.65)))           //.                      //.
    .mute("<1!32 0!192>").pipeline("pedal").room("0.3:5:0.1")
  , // Guitar 1
  n(`<[7 [4@4 2 0] 2 1 [0 -1 -3 -1] [0 -3] -2 <[-1 0@3] [5 6@3] [[4 5] 8@3] [[3 4] 3@3]>]!4
      [[4@2 [2 0] 0] [-1 -4] [-3 1 -3 1 -3!10 1 -3] [2 [2 6@3]]]!2
      [[-3,-7] [[-4,-5] [-1,-3]] [0,-3] <[[4 6],[0 -1]] [0,-1]>] [<[7,4] [[7 4 6 2]!4]> [-5 -6] [-7,-14] [-5 <-1 -4 -4 1>]]>/4`)
    .orbit(1).scale("<e3:minor!48 e4:minor!16 e3:minor!48 e4:minor!16>").struct("<[x!16]!7 [x!24]!1 [x!16]!16>") //  .mute()
    .velocity("0.98 0.95!7 0.97 0.95!7".fast(2)) //  . solo()
    .sound("supersaw").unison(9).spread(0.06).gain(0.75).postgain(0.12).distort("1:tube:4").distort(0.80)    
    .clip("<0.86!31 0.77 0.86!31 0.85 0.86!30 0.81 0.72>".fast(2)).adsr("0.005:3.0:0.0:0.029").lpadsr("0.005:0.9:0.0:0.011")    
    .hpf("<550!16 450!16 550!16 700!16>").lpf("3200".add(saw.range(1, 0).pow(3.0).mul(200)).slow(8)).lpe(1.0).lpq(1.4)
    .coarse(2).coarseos(4).pan(0.15).superimpose(pan(0.80)).superimpose(hpf(3050).lpf(6500).lpq(0.7).postgain(0.02))
    .pipeline("pedal").body("oak").bodyMix(0.3)
  , // Guitar 2
  n("<0 0 2 4 0 0 -2 -1>")  //  . solo()
    .orbit(2).scale("<e2:minor>").struct("<[x!8]!14 [x!12]!2 [x!8]!32>").fast(2) // . mute()
    .velocity("0.98 0.95!7 0.97 0.95!7".fast(2))
    .sound("supersaw").unison(7).spread(0.09).gain(0.75).postgain(0.115).distort("1:tube:4").distort(0.85)
    .clip("<0.86!31 0.77 0.86!31 0.85 0.86!30 0.81 0.72>".fast(2)).adsr("0.005:3.0:0.0:0.031").lpadsr("0.005:0.6:0.0:0.01")    
    .hpf(110).lpf(2100).lpe(1.0).lpq(1.3)
    .coarse(2).coarseos(4).pan(0.3).superimpose(
      x => x.pan(0.7),
      x => x.postgain(0.115).hpf(260).lpf(3050).scaleTranspose("<4!7 [2 [3 4@3]]!1 4!7 [-7 -3] 4!7 [2 [3 4@3]]!1 4!7 [-3 [2 4@3]]>")
           .pan(0.2).superimpose(pan(0.8))
    ).superimpose(hpf(3000).lpf(5500).lpq(0.7).postgain(0.02)).mute("<0!128 1!16 0!16>").pipeline("pedal").body("rosewood").bodyMix(0.2)
  , // Bass
  n("<0 0 2 4 0 0 -2 -1>").struct("<[x!1]!16 [x@3 x]!48 [x!4]!80>").fast(2).velocity("0.98 0.94 0.96 0.94".fast(2))  // . mute()
    .orbit(3).scale("e1:minor").sound("saw").gain(0.5).distort("0.18:soft:4").postgain(0.20).clip(0.65)
    .adsr("0.015:4.0:0.0:0.015").lpadsr("0.007:0.075:0.0:0.01").hpf(55).lpf(200).lpe(25).lpq(0.8)  //  .solo()
    .pan(0.20).superimpose(pan(0.80)).mute("<0!128 1!32>") // .pipeline("pedal")
  , // Drums
  sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd  ~ bd  ~]!32 [bd!4]!16 [bd ~ bd [~ bd]]!15 [bd!]!1>").mute("<0!128 1!32>")  // . solo()
    .pan(0.5).orbit(5).gain(0.24).hpf(45).lpf(11500).adsr("0.002:0.12:0.5:0.2"),
  sound("<[~!2]!2  [~!4]!2  [~!8]!2  [~!16]  [~!24]  [~  sd  ~ sd]!32 [~ sd ~ sd]!32>").mute("<0!128 1!32>")  // . solo()
    .pan(0.485).late(0.0025).orbit(5).gain(0.30).hpf(350).lpf(11500).adsr("0.002:0.20:0.2:0.2")
    .superimpose(x => x.bandf("205".add(berlin.mul(10).fast(4))).bandq(4).vel(0.60).hpf(190).lpf(350)),
  sound("<[hh hh hh hh]!16 [hh hh oh hh]!24 [cr hh cr hh]!24 [~ rd ~ rd]!32>").fast(2).mute("<0!128 1!32>") // . solo()
    .pan(0.515).late(0.0005).orbit(5).gain(0.32).hpf(800).lpf("10000".add(perlin.mul(300).fast(4))).adsr("0.005:0.15:0.8:0.2"), // . mute()
  sound("brown!8").orbit(6).coarse(sine.range(8, 64).slow(16)).gain(0.50).hpf(4000)
    .pan(sine.range(0.4, 0.6).slow(11)).adsr("0.005:0.2:0.4:0.02")  // .solo(),
  // Master
  ,master(Master.of(MasterFx.reverb().wet(0.05).roomSize(5), MasterFx.gain(2.5), MasterFx.limiter()))
).analog(feel).room("0.1:8:0.12:12000").seed(timeOfDay.mul(60*60*24))



// Inspired by: Editors - Papillon
// https://open.spotify.com/intl-de/track/7hYiX6LMP8w8d0kEc4KWuW




// Written by: peekandpoke

// Epilepsy Warning: Do not click the oscilloscope!











    """,
)
