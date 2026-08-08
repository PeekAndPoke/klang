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
    code = """import * from "stdlib"                                                                                                      
import * from "sprudel"                                                                                                                         //.
                                                                                                                                               ////.
let feel = 15.0    // 0.0 .. guitar | 100.0 .. rave                                                                                           //  //.
                                                                                                                                             //    //.
                                                                                                                                            //      //.
stack(                                                                                                                                     //        //.
  // Lead                                                                                                                     //////////////          //////////////.
  n(`<[-7 0 2 4] [-7 0 4 [2 6]|[4 2]|2|2|2] [-5 -1 2 4] [-6 -1 [4 3]|4|3|3|3 [1 -1]|1|1|1|1]>*2`)                               //                              //.
    .orbit(0).scale("<e4:minor!48 e5:minor!16 e4:minor!48 e3:minor!16>").sound("superramp").unison(15).spread(0.08)               //          DISCO!          //.
    .hpf(1400).lpf(1850).lpe(berlin.range(1.7, 1.9).fast(4)).lpq(2.3).lpadsr("0.010:0.2:0.8:0.03")                                  //       FOREVER!       //.
    .gain(0.60).distort("0.530:tube:4").postgain("<0.200!48 0.100!16 0.200!48 0.300!16>") // . solo()                                 //                  //.
    .adsr("0.010:4.0:0.2:0.03").clip(0.90).release("<0.08!16 0.15!16>").vibrato(8).vibmod(0.01)  // . mute()                           //       //      //.
    .shuffle("<1!64 0!16 1!1 4/8!14 1!33>")                                                                                           //     //.   //    //.
    .superimpose(x => x.transpose(12).spread(0.12).mute("<1!16 0!16>").velocity(0.25).pan(0.35).superimpose(pan(0.65)))              //   //.         //  //.
    .mute("<1!32 0!192>").pipeline("pedal").room("0.3:5:0.1")                                                                       // //.              // //.
  , // Guitar 1                                                                                                                    //.                      //.
  n(`<[0 [0@6 3 6] [4 [3 2]] [3 1] [-3 -1 2 4] [7 -3] -2 <[-1 0@3] [4 7@3] [[3 4] 3@3] [[4 6] 7@3]>]!4                                
      [[4@2 [2 0] 0] [-1 -4] [-3 1 -3 1 -3!10 1 -3] [2 [2 6@3]]]!2
      [[-3,-7] [[-4,-5] [-1,-3]] [0,-3] <[[4 6],[0 -1]] [0,-1]>] [<[7,4] [[7 4 6 2  7 4 6 2]!2]> [-5 -6] [-7,-14] [-5 <-1 -4 -4 1>]]>/4`)
    .orbit(1).scale("<e3:minor!48 e4:minor!16 e3:minor!48 e4:minor!16>").struct("<[x!16]!7 [x!24]!1 [x!16]!16>") //  .mute()
    .velocity("0.98 0.95!7 0.97 0.95!7".fast(2))  // . solo()
    .sound("supersaw").unison(9).spread(0.04).gain(0.75).postgain(0.100).distort("1:tube:4").distort(0.80)    
    .clip("<0.85!31 0.74 0.85!31 0.77 0.85!30 0.78 0.70>".fast(2)).adsr("0.005:3.5:0.0:0.05").lpadsr("0.005:1.2:0.0:0.040")    
    .hpf("<500!16 500!16 500!16 650!16>").lpf("3000".add(saw.range(1, 0).pow(4.0).mul(300)).slow(8)).lpe(1.0).lpq(1.3)
    .coarse(2).coarseos(4).pan(0.15).superimpose(pan(0.80)).superimpose(hpf(3000).lpf(5000).lpq(1.5).postgain(0.03))
    .pipeline("pedal").body("violin").bodyMix(0.3)
  , // Guitar 2
  n("<0 0 2 4 0 0 -2 -1>")  //  . solo()
    .orbit(2).scale("<e2:minor>").struct("<[x!8]!14 [x!12]!2 [x!8]!32>").fast(2) // . mute()
    .velocity("0.98 0.95!7 0.97 0.95!7".fast(2))
    .sound("supersaw").unison(11).spread(0.07).gain(0.75).postgain(0.13).distort("1:tube:4").distort(0.85)
    .clip("<0.85!31 0.74 0.85!31 0.77 0.85!30 0.78 0.70>".fast(2)).adsr("0.007:3.5:0.0:0.05").lpadsr("0.007:1.1:0.0:0.030")    
    .hpf(80).lpf(1400).lpe(1.0).lpq(1.5)
    .coarse(2).coarseos(4).pan(0.3).superimpose(
      x => x.pan(0.7),
      x => x.postgain(0.12).hpf(300).lpf(3000).scaleTranspose("<4!7 [2 [3 4@3]]!1 4!7 [-7 -3] 4!7 [2 [3 4@3]]!1 4!7 [-3 [2 4@3]]>")
           .pan(0.2).superimpose(pan(0.8))
    ).mute("<0!128 1!16 0!16>").pipeline("pedal").body("spruce").bodyMix(0.3)
  , // Bass
  n("<0 0 2 4 0 0 -2 -1>").struct("<[x!1]!16 [x@3 x]!48 [x!4]!80>").fast(2).velocity("0.98 0.94 0.96 0.94".fast(2))  // . mute()
    .orbit(3).scale("e1:minor").sound("sine").gain(1.0).distort("0.05:soft:4").postgain(0.100).clip(0.66)
    .adsr("0.010:4.0:0.0:0.010").lpadsr("0.005:0.06:0.0:0.01").hpf(45).lpf(120).lpe(30).lpq(0.7)  //  .solo()
    .pan(0.35).superimpose(pan(0.65)).mute("<0!128 1!32>") // .pipeline("pedal")
  , // Drums
  sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd  ~ bd  ~]!32 [bd!4]!16 [bd ~ bd [~ bd]]!15 [bd!]!1>").mute("<0!128 1!32>")  // . solo()
    .pan(0.5).orbit(5).gain(0.23).hpf(40).lpf(7500).adsr("0.002:0.15:0.5:0.2"),
  sound("<[~!2]!2  [~!4]!2  [~!8]!2  [~!16]  [~!24]  [~  sd  ~ sd]!32 [~ sd ~ sd]!32>").mute("<0!128 1!32>") // . solo()
    .pan(0.5).late(0.0015).orbit(5).pan(0.475).gain(0.39).hpf(350).lpf(10500).adsr("0.002:0.10:0.2:0.2")
    .superimpose(x => x.bandf("205".add(berlin.mul(10).fast(4))).bandq(4).vel(0.60).hpf(190).lpf(350)),
  sound("<[hh hh hh hh]!16 [hh hh oh hh]!24 [cr hh cr hh]!24 [~ rd ~ rd]!32>").fast(2).mute("<0!128 1!32>") // . solo()
    .pan(0.525).late(0.0030).orbit(5).gain(0.26).hpf(800).lpf("10500".add(perlin.mul(300).fast(4))).adsr("0.005:0.15:0.8:0.2"), // . mute()
  sound("<~!79 [~ ~ ~ cp  cp ~ cp ~] ~!47 [~ ~ ~ cp  cp ~ cp ~]>").orbit(6).gain(0.10).mute("<0!128 1!32>"),
  sound("pink!8").orbit(7).gain(0.04).hpf(8000).lpf(13500).lpq(0.5)
    .pan(sine.range(0.4, 0.6).slow(11)).adsr("0.005:0.15:0.0:0.05")  // .solo()
  // Master
  ,master(Master.of(MasterFx.reverb().wet(0.03).damp(0.8).roomSize(8),
                    MasterFx.gain(1.40),   // +3.2 dB                                                                                                                                                                                            
                    MasterFx.limiter().lookahead(0.005).thresholdDb(-8.0).ratio(2.0).kneeDb(6.0).attack(0.030).release(0.125),                                                                                                                                    
                    MasterFx.gain(1.35),   // +2.9 dB                                                                                                                                                                                            
                    MasterFx.limiter().lookahead(0.005).thresholdDb(-4.0).ratio(4.0).kneeDb(4.0).attack(0.015).release(0.075),                                                                                                                                    
                    MasterFx.gain(1.20),   // +2.3 dB — least, because the house brickwall is next
  ))
).analog(feel).seed(timeOfDay.mul(10*60*60*24))



// Inspired by: Editors - Papillon
// https://open.spotify.com/intl-de/track/7hYiX6LMP8w8d0kEc4KWuW




// Written by: peekandpoke

// Epilepsy Warning: Do not click the oscilloscope!












    """,
)
