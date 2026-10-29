/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.builtinsongs.jingles

import io.peekandpoke.klang.BuiltInJingles
import io.peekandpoke.klang.Jingle

/**
 * The marimba and the melody of "Der Schmetterling" (`builtinsongs/DerSchmetterling.kt`), four cycles: the melody twice
 * (the maintainer, 2026-10-29: "take the marimba and the melody from Schmetterling and make it the new jingle when
 * Startpage is done for 4 cycles"). The song's lead as it plays there: its shape, its key (E minor, three semitones
 * down), its room and its master's level, without the band.
 */
internal val schmetterlingJingle = Jingle(
    id = "${BuiltInJingles.PREFIX}-schmetterling",
    title = "Der Schmetterling",
    rpm = 33.0,
    cycles = 2,
    code = """
// Jingle: the marimba and the melody of "Der Schmetterling"
// Inspired by: Editors - Papillon

// Marimba: a wooden bar with three tuned modes (1 : 4 : 10). The upper two are gone within 100 ms, so the pitch is
// stated at the hit and only the fundamental rings on, longer on low bars. A mallet thump, and the resonator tube
// under the bar in which the thump rings at the fundamental.
let marimba = (() => {
  let pAnalog = Ign.slot.analog
  let ring = Ign.constant(320).div(Ign.freq())                                                            // seconds: 1.2 s on e4, 0.6 s on e5
  let f1  = Ign.sine(x => x.analog(pAnalog)).adsr(0.001, ring, 0.0, 0.40)
  let f4  = Ign.sine(Ign.freq().mul(4.05), x => x.analog(pAnalog)).adsr(0.001, 0.12, 0.0, 0.10).mul(0.35)
  let f10 = Ign.sine(Ign.freq().mul(10.075), x => x.analog(pAnalog)).adsr(0.001, 0.08, 0.0, 0.05).mul(0.25)
  let f12 = Ign.sine(Ign.freq().mul(12.025), x => x.analog(pAnalog)).adsr(0.001, 0.05, 0.0, 0.05).mul(0.05)
  let mallet = Ign.pinknoise().adsr(0.0005, 0.010, 0.0, 0.010).lowpass(2500).mul(9.0)                     // yarn head: a thump, not a click

  return f1.plus(f4).plus(f10).plus(f12).plus(mallet)
    .distort(0.3, "softsat")
    .eq(e => e
      .band(freq = Ign.freq(), q = 30.0, db = 9) // the resonator tube
    )
    .adsr(0.010, 1.0, 0.0, 0.5).mul(0.1)
    .classic()
})()

// The melody: two bars a cycle, four bars round
let melody = `<[-7 0 2 4] [-7 0 4 [2 -1]|[4 2]|[4 6]|2|2|2|2] [-5 -1 2 4] [-6 -1 [4 3]|[5 3]|3|3|3|3 [1 -1]|1|1|1|1]>*2`

// The song's guitar dynamics: the first beat of a bar leans in
let dyna = "0.98!2 0.65!6  0.85!2 0.65!2 0.80!2 0.65!2"
  .sub(perlin(0.00, 0.05).seed(0)).mul("[1.00 0.90 0.95 0.90  1.00 0.90 0.95 0.90]")

let jingle = n(melody).sound(marimba).ply("1|1|1|1|1|1|2".fast(3.sqrt())).adsrOff()
  .velocity(dyna).body(material = "steel", wet = 0.2)
  .hpf(600, 0.7).lpf(7200, 1.2).notch(2700, 0.7)
  .pan(0.05).superimpose(pan(0.95))
  .clip(0.80)
  .orbit(0).scale("e5:minor").gain(0.42)                  // alone, not under the band: 10 dB over the song's lead
  .analog(5).transpose(-3)
  .reverb(wet = 0.10, size = 1.0)

stack(
  jingle
  , // Master: the song's room and level
  master(Katalyst(k => k
    .reverb(0.10, 7, 4500)
    .gain(4.2)
    .limiter(threshold = -3.0, ratio = 20.0, knee = 2.0, attack = 0.005, release = 0.10, lookahead = 0.005)
  ))
)
    """,
)
