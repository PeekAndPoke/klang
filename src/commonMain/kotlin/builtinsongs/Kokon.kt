/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

internal val kokonSong = Song(
    id = "${BuiltInSongs.PREFIX}-kokon",
    title = "Kokon",
    rpm = 20.0,
    icon = "egg",
    code = """
import * from "stdlib"
import * from "sprudel"

// Kokon: before there is a butterfly, there is a cocoon.
//
// Guitars only, and only one guitar: the instrument of Der Schmetterling, its rig stages and its makeGuitar, played
// through four rigs picked from its menu. A clean arpeggio is spun one thread at a time, a second guitar answers, a
// heartbeat starts under the skin, volume swells stretch it from inside, it holds its breath, and the high gain rig
// splits it open. What flies out at the end is the Schmetterling's own lead, and the last chord is major.
//
// How it is built, bottom up:
//   notes  the harmony and the melodies, as patterns without scale or timing
//   lines  one guitarist playing one way, a function from notes to sound
//   parts  lines played together; a part starts on its own first cycle
//   song   arrange(): which part, for how many cycles (3 s each), in which order

let feel  = 20   // analog drift of the guitars, as in Der Schmetterling
let drunk =  1   // two guitarists, sober this time, mostly

// Rig stages (from Der Schmetterling)  ------------------------------------------------------------
let pickupNeck = x => x
  .lowpass(3400, 1.4)
  .notch(freq = Osc.freq().mul(4), q = 2.0)
  .mul(1.2)

let pickupSingle = x => x
  .lowpass(4800, 2.5)
  .mul(0.8)

let pickupHumbucker = x => x
  .lowpass(2500, 1.6)
  .mul(1.2)

let pedalStock = x => x

let pedalScreamer = x => x
  .plus(x.highpass(720).distort(0.35, "soft", 2).mul(0.6))
  .lowpass(3000)
  .mul(0.5)

let pedalBoost = x => x
  .highpass(400)
  .mul(1.2)

let preampClean = x => x
  .distort(0.12, "tube", 2).highpass(80)
  .eq(e => e.band(freq = 3500, q = 0.7, db = 2.0))
  .mul(3.5)

let preampCrunch = x => x
  .distort(0.30, "tube", 4).highpass(90)
  .distort(0.30, "tube", 4).highpass(90)
  .mul(1.9)

let preampHighGain = x => x
  .highpass(120)
  .distort(0.45, "tube", 4).highpass(100)
  .distort(0.50, "softsat", 4).highpass(100)
  .distort(0.50, "soft", 4)
  .lowpass(6800)
  .mul(0.25)

let powerPushPull = x => x
  .distort(0.25, "soft", 2)
  .eq(e => e.band(freq = 4000, q = 0.7, db = 2.0))
  .mul(1.6)

let powerClassA = x => x
  .distort(0.20, "asym", 2)
  .mul(1.4)


let cab4x12 = x => x
  .eq(e => e
    .band(freq =  120, q = 1.2, db =  3.0)
    .band(freq =  380, q = 0.6, db =  7.0)
    .band(freq = 2700, q = 1.7, db =  4.0)
    .band(freq =  100, q = 0.7, db = -3.0)
  )
  .lowpass(5000, 0.707, x => x.passes(2))

let cab1x12 = x => x
  .highpass(120, 0.707, x => x.passes(2))
  .eq(e => e.band(freq = 3200, q = 1.0, db = 3.0))
  .lowpass(6500, 0.707, x => x.passes(3))

// The guitar (from Der Schmetterling)  -----------------------------------------------------------
let makeGuitar = (pickup, pedal, preamp, power, cab) => {
  let pVoices     = OscSlot.voices
  let pSpread     = OscSlot.spread
  let pAnalog     = OscSlot.analog
  let pAttack     = Osc.param("attack",       0.005, "Attack")
  let pDecay      = Osc.param("decay",        1.000, "Decay")
  let pSustain    = Osc.param("sustain",      0.000, "sustain")
  let pRelease    = Osc.param("release",      0.030, "Release")

  let saw = Osc.supersaw(x => x.voices(pVoices).spread(pSpread)
    .phasePool(on = 1, kMin = 0.60, kMax = 0.85, warmup = 0, selection = "normal")
    .spreadPower(6.0).sideAtten(0.3).gainJitter(0.05).centerJitter(0.20)
    .analog(pAnalog).analogSpread(0.5)
  )

  let signal = saw.mul(Osc.slot.pregain)
    .pitchEnvelope(0.5, x => x.adsr(0.001, 0.05, 0, 0))
    .plus(Osc.crackle(1.25).highpass(1200).adsr(0.001, 0.1, 0.0, 0.05).mul(1.5))
    .adsr(pAttack, pDecay, pSustain, pRelease, e => e.curves("linear", "linear", "linear"))

  return cab(power(preamp(pedal(pickup(signal))))).mul(0.14).classic()
}

// Four rigs, one guitar. The cocoon is clean and dark, the answer is bright, the heartbeat is the clean rig into the big
// box (the 4x12 keeps the thump the 1x12 cuts), and the wings are the Schmetterling's own rhythm rig.
let clean  = makeGuitar(pickupNeck,      pedalStock,    preampClean,    powerClassA,   cab1x12)
let bright = makeGuitar(pickupSingle,    pedalBoost,    preampCrunch,   powerPushPull, cab4x12)
let deep   = makeGuitar(pickupNeck,      pedalStock,    preampClean,    powerClassA,   cab4x12)
let heavy  = makeGuitar(pickupHumbucker, pedalScreamer, preampHighGain, powerPushPull, cab4x12)

// Notes  -----------------------------------------------------------------------------------------------------------
// Scale degrees in D minor, 0 is D3. No scale here: the song sets it once. A line moves its notes to its own octave.

// The cocoon: Dm(add9), Bbmaj7(#11), Gm9, Asus. One chord per cycle, four cycles a round.
export cocoonArp   = `<[0 4 7 8 9 8 7 4] [-2 2 4 8 9 8 4 2] [-4 0 2 4 5 4 2 0] [-3 1 4 7 8 7 4 1]>`
export cocoonRoots = `<0 5 3 4>`
export cocoonPower = `<[0,4] [-2,2] [-4,0] [-3,1]>`
export cocoonSwell = `<[4,7,9] [4,8,9] [2,5,7] [1,4,8]>`

// The lift: the break climbs Bb, C, Dm, three cycles, then lands.
export liftArp   = `<[-2 2 4 8 9 8 4 2] [-1 3 6 8 10 8 6 3] [0 4 7 8 9 8 7 4]>`
export liftRoots = `<5 6 0>`
export liftPower = `<[-2,2] [-1,3] [0,4]>`

// The melody, in two phrases of one round each. The second fits the cocoon and the lift alike.
export melodyOne = `<[4@4 3 2 1 2] [1@4 ~ 0 1 2] [4@3 5 4@2 2 0] [1@6 ~@2]>`
export melodyTwo = `<[7@4 6 4 3 4] [8@4 ~ 7 8 9] [9@3 8 7@2 5 4] [4@6 ~@2]>`

// Out of the cocoon flies the Schmetterling's own lead.
export schmetterlingLead = `<[-7 0 2 4] [-7 0 4 2] [-5 -1 2 4] [-6 -1 4 3]>`

// Lines  -----------------------------------------------------------------------------------------------------------
// One guitarist, one way of playing. Each line sets its own level; a part may set another.

// A plucked string drops after the pick, then rings and fades. The clean lines pick a little softer (pregain), so the
// amp squeezes the drop less and each note stands out from the one still ringing.

// Spin: the clean arpeggio, each note picked, then ringing under the next.
export spin = notes => n(notes)
  .sound(clean).adsrOff().unison(voices = 7, spread = 0.06).pregain(0.7)
  .oscp("decay", 0.30).oscp("sustain", 0.45).oscp("release", 0.8).clip(3)
  .velocity("1.0 0.8 0.9 0.8 0.95 0.8 0.9 0.8")
  .gain(0.22).pan(0.35)                            // the arp guitarist stands a little left of the centre
  .orbit(1).reverb(wet = 0.2, size = 4)

// Sing: the melody on the bright rig, an octave up.
export sing = notes => n(notes.add(7))
  .sound(bright).adsrOff().unison(voices = 11, spread = 0.04) // a narrow chorus: a held note stays one note
  .oscp("decay", 3.0).clip(1.5)
  .hpf(250)                                        // the 4x12 roar sits on the arp; the lowest note is D4 at 293 Hz
  .lpf(3500)                                       // the crunch fizz on held notes covers the arp's picks
  .gain(0.14).pan(0.6)                             // the melody stands near the centre, a little right
  .orbit(2).reverb(wet = 0.3, size = 5)

// Soar: the melody two octaves up, wider, over the wings.
export soar = notes => n(notes.add(14))
  .sound(bright).adsrOff().unison(voices = 15, spread = 0.05)
  .oscp("decay", 3.0).clip(1.5)
  .hpf(400)                                        // two octaves up, nothing of the melody lives below
  .lpf(4000)                                       // less fizz, the wall keeps its own
  .gain(0.50).pan(0.5)
  .orbit(6).reverb(wet = 0.25, size = 5)

// Swell: volume-knob swells, the thing inside stretching. Doubled on the left, a little late.
export swell = chords => n(chords.add(7))
  .sound(bright).adsrOff().unison(voices = 11, spread = 0.06)
  .oscp("attack", 1.6).oscp("decay", 2.0).clip(1)
  .lpf(3000)
  .gain(0.06).pan(0.15).superimpose(x => x.pan(0.25).late(0.02)) // on the left, when they enter
  .orbit(7).reverb(wet = 0.5, size = 6)

// Beat: the heartbeat under the skin, 3-3-2 on the root, an octave down.
export beat = roots => n(roots.add(-7)).struct("x ~ ~ x ~ ~ x ~")
  .sound(deep).adsrOff().unison(voices = 7, spread = 0.06)
  .oscp("decay", 2.0).clip(2)
  .velocity("1.0 0.8 0.9")
  .gain(0.30).pan(0.4)                             // near the centre, a little left, across from the melody
  .orbit(3)

// Chug: the heavy rig, palm-muted on the root, an octave down.
export chug = roots => n(roots.add(-7)).struct("x x ~ x x ~ x x")
  .sound(heavy).adsrOff().unison(voices = 19, spread = 0.10)
  .oscp("decay", 0.15).clip(1)
  .velocity("1.0 0.75 0.9 0.75 0.95 0.75")
  .gain(1.2).pan(0.5)
  .orbit(4)

// Wings: tremolo-picked power chords on the heavy rig, hard left and right.
export wings = chords => n(chords).ply(16)
  .sound(heavy).adsrOff().unison(voices = 11, spread = 0.10)
  .oscp("decay", 0.4).clip(1)
  .velocity("1.0 0.85 0.9 0.85")
  .gain(0.8)
  .pan(0.1).superimpose(x => x.pan(0.9).late(0.004))
  .orbit(5).reverb(wet = 0.15, size = 3)

// Strike: one heavy chord, let ring. It shares the wings' orbit and room.
export strike = chords => n(chords)
  .sound(heavy).adsrOff().unison(voices = 11, spread = 0.10)
  .oscp("decay", 3.5).clip(1)
  .gain(0.8)
  .pan(0.1).superimpose(x => x.pan(0.9).late(0.004))
  .orbit(5).reverb(wet = 0.15, size = 3)

// Chime: a melody on the clean rig, two octaves up, picked and let ring. The butterfly after the storm.
export chime = notes => n(notes.add(14))
  .sound(clean).adsrOff().unison(voices = 7, spread = 0.06).pregain(0.7)
  .oscp("decay", 0.30).oscp("sustain", 0.45).oscp("release", 1.2).clip(2)
  .gain(0.35).pan(0.75)
  .orbit(10).reverb(wet = 0.3, size = 5)

// Strum: the clean rig, one slow strum, let ring.
export strum = notes => n(notes)
  .sound(clean).adsrOff().unison(voices = 7, spread = 0.06).pregain(0.7)
  .oscp("decay", 0.30).oscp("sustain", 0.50).oscp("release", 2.5).clip(40)
  .gain(0.20).pan(0.45)
  .orbit(8).reverb(wet = 0.3, size = 6)

// Parts  -----------------------------------------------------------------------------------------------------------
// Lines played together. Every part starts on its own first cycle, so a round of the cocoon always starts on Dm.

// The cocoon is spun one thread at a time, over two rounds. It starts in the middle and drifts slowly to its place,
// a little left, to make room for the answer.
let spinning = stack(
  spin(cocoonArp).gain(0.25).pan("<0.5 0.5 0.48 0.46 0.43 0.41 0.38 0.36>").mask("<[1 0 0 0 1 0 0 0]!2 [1 0 1 0 1 0 1 0]!2 [1 0 1 1 1 0 1 1]!2 1!2>"),
)

// A second guitar answers.
let answering = stack(
  spin(cocoonArp).gain(0.21),
  sing(melodyOne),
)

// The heartbeat starts.
let quickening = stack(
  spin(cocoonArp).gain(0.21),
  sing(melodyTwo),
  beat(cocoonRoots),
)

// Swells stretch it from inside, the arpeggio grows.
let stretching = stack(
  spin(cocoonArp).gain("<0.20 0.21 0.22 0.23>"),
  sing(melodyOne).gain(0.12),
  swell(cocoonSwell),
  beat(cocoonRoots),
)

// Still growing, and in the last half cycle the arpeggio and the heartbeat hold their breath.
let breath = "<1!3 [1 0]>"

let holdingBreath = stack(
  spin(cocoonArp).gain("<0.24 0.25 0.26 0.27>").mask(breath),
  sing(melodyTwo).gain(0.12),
  swell(cocoonSwell),
  beat(cocoonRoots).mask(breath),
)

// The high gain rig splits it open: the melody against the wall. The arpeggio waits for its own part.
let breakingOpen = stack(
  soar(melodyOne),
  wings(cocoonPower),
  chug(cocoonRoots),
  beat(cocoonRoots).gain(0.7),
)

// The melody steps aside and the cocoon's own thread unravels over the heavy wall: the arpeggio an octave up, leading,
// in the centre between the wings.
let unravelling = stack(
  spin(cocoonArp.add(7)).ply(2).gain(0.57).pan(0.5),
  wings(cocoonPower),
  chug(cocoonRoots),
  beat(cocoonRoots).gain(0.7),
)

// The lift: Bb, C, Dm.
let lifting = stack(
  spin(liftArp).ply(2).gain(0.20).oscp("sustain", 0.6),
  soar(melodyTwo),
  wings(liftPower),
  chug(liftRoots),
  beat(liftRoots).gain(0.7),
)

// It lands on one heavy chord with the low D under it, and the melody holds its A.
let landing = stack(
  spin("[0 4 7 9 11 9 7 4]").ply(2).gain(0.20).oscp("sustain", 0.6),
  soar("[4@6 ~@2]"),
  strike("[-7,0,4]"),
  beat("0").gain(0.7),
)

// The cocoon again, empty now, and the butterfly flies off.
let flyingOff = stack(
  spin(cocoonArp),
  chime(schmetterlingLead),
)

// The last chord is D major: this part brings its own scale, and the first scale on a note wins.
let lastChord = stack(
  strum("<[0 4 7 9 11 ~@27] ~>").scale("d3:major"),
)

// Song  ------------------------------------------------------------------------------------------------------------
export song = stack(
  arrange(
    [8, spinning],
    [4, answering],
    [4, quickening],
    [4, stretching],
    [4, holdingBreath],
    [4, breakingOpen],
    [4, unravelling],
    [3, lifting],
    [1, landing],
    [4, flyingOff],
    [2, lastChord],
  )
    .scale("d3:minor")
    .analog(feel)
    .late(berlin.range(0.0, 0.002).mul(drunk).seg(8)),
  master(Katalyst(k => k
    .gain(1.5).limiter(threshold = -8.0, ratio = 2.0, attack = 0.015, release = 0.25)
    .gain(1.3).limiter(threshold = -4.0, ratio = 4.0, attack = 0.008, release = 0.15)
    .gain(1.2)
  ))
)



// Written by Claude (Opus 5.5) on the guitar of Der Schmetterling, which the maintainer and Claude built stage by stage.
// Fine-tuned and arranged with peekandpoke

    
    
    """,
)
