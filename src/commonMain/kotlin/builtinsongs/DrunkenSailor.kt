@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

internal val drunkenSailorSong = Song(
    id = "${BuiltInSongs.PREFIX}-drunken-synthlor",
    title = "Drunken Synthlor",
    rpm = 45.0,
    icon = "glass cheers",
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
)
