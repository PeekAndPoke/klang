@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

internal val irishLamentTechnoSong = Song(
    id = "${BuiltInSongs.PREFIX}-the-synthsale-pipers-last-rave",
    title = "The Synthsale Piper's Last Rave",
    rpm = 37.0,
    icon = "compact disc",
    code = """
import * from "stdlib"
import * from "sprudel"

// The Piper traded the recorder for a 303. Same melody, different floor.
// D minor — arrange-based structure that loops back to a "warm" state
// (kick + hat + bass already going), not a cold silent intro.

// ── Section structure (each entry = section in arrange) ─────────────
//   16   warm        kick + hat + bass — LOOP POINT
//   8    +sub        sub joins
//   8    +perc       clap, open hat, rim
//   8    +leadA      melody phrase A
//   8    +leadB +pad pad enters
//   8    +leadC +pad
//   8    +leadD +pad
//   16   +leadE +pad +wind   (leadE loops once; wind fades in over 16)
//   4    hit         unison hit at beat 1
//   64   quietBuild  smooth morph: mel3 fades out, mel1 fades in,
//                    beats + bass build via gain saws, syncopated 90s
//                    dance pad stabs enter in the 2nd half,
//                    tetris-style techno bassline drives the build
//   64   darkBuild   no melodies — darker filters, more distortion,
//                    bass + bassline sidechain-pump on every kick,
//                    spheric supersine stabs in syncopated rhythm
//   = 212 cycles total, then arrange loops back to warm

// ── Continuous core: kick + hat + bass ──────────────────────────────
let kick = s("bd!4").gain(1.0).hpf(40).adsr("0.06:0.18:0.0:0.02").orbit(1)
let hat  = s("hh!8").gain(0.27).hpf(7000).adsr("0.001:0.04:0.0:0.04").orbit(2)
let bass = note("<[a1!8] [d2!8] [bb1!8] [c2!8] [g1!8] [f1!8] [a1!8] [d2!8]>")
    .sound("saw").legato(0.7).hpf(100).lpf(sine.range(380, 600).slow(48)).resonance(4).lpenv(1.75).lpadsr("0.005:0.08:0.1:0.05")
    .adsr("0.002:0.08:0.5:0.05").distort("0.9:soft:4").postgain(0.22).warmth(0.25)
    .gain(0.5).orbit(0)
let core = stack(kick, hat, bass)

// ── Build layers ────────────────────────────────────────────────────
let sub  = note("<a1 d2 bb1 c2 g1 f1 a1 d2>").struct("x!2").sound("sine").legato(1.0).adsr("0.005:0.05:0.5:0.05").lpf(180).gain(0.35).orbit(6)
let clap = s("~ cp ~ cp").gain(0.21).hpf(300).orbit(4).room(0.2).rsize(3)
let oh   = s("[~ ~ ~ oh]!4").gain(0.19).hpf(5000).orbit(2)
let rim  = s("~ ~ rim ~ ~ ~ rim ~").gain(0.3).hpf(800).orbit(2)

// ── Lead phrases (5 shapes of the recorder melody) ──────────────────
let leadStyle = mel =>mel.sound("supertri").unison(3).detune(0.07).euclid(3, 8)
       .hpf(300).lpf(sine.range(2200, 4000).slow(24)).resonance(2).adsr("0.005:0.12:0.5:0.08").clip(0.7).distort(0.4).postgain(0.25)
       .delay(0.18).delaytime(pure(3/16).div(cps)).delayfeedback(0.32)
       .gain(0.7).orbit(3).room(0.2).rsize(3)
let leadA = leadStyle(note(`<[a4 c5 b4 a4] [d5 c5 a4 g4] [bb4 a4 g4  f4]  [g4 e4 c4 a4] [g4 bb4 d5 bb4] [f4 a4 c5 a4] [a4 c5 e5 c5] [d4 f4 a4 d5]>`))
let leadB = leadStyle(note(`<[a5 e5 a5 c5] [d5 a5 d5 f5] [bb4 d5 bb5 d5]  [c5 g5 c5 e5] [g4 d5  g5 d5]  [c5 a4 f5 a4] [a4 c5 a5 e5] [d5 f5 a5 d5]>`))
let leadC = leadStyle(note(`<[a5 c6 b5 a5] [d6 c6 a5 g5] [bb5 a5 g5  f5]  [g5 e5 c5 a5] [g5 bb5 d6 g5]  [a5 c6 f5 a5] [a5 e6 c6 a5] [d5 f5 a5 d6]>`))
let leadD = leadStyle(note(`<[a5 e5 a5 c5] [d5 a5 d5 f5] [bb4 d5 bb5 d5]  [c5 g5 c5 e5] [g4 d5  g5 d5]  [c5 a4 f5 a4] [a4 c5 a5 e5] [d5 f5 a5 d5]>`))
let leadE = leadStyle(note(`<[a5 e5 d6 c6] [a5 f5 d5 c5] [f5  d5 g5  bb4] [e5 g5 d5 c5] [d5@2   a4@2]   [c5@2  g4@2]  [a4@2  e4@2]  [d4@4]>`))

// ── Pad ─────────────────────────────────────────────────────────────
let pad = chord("<Am Dm Bb C Gm F Am Dm>").voicing()
    .sound("superpulse").unison(4).detune(0.15).lpf(2000).adsr("0.2:0.2:0.5:0.1").legato(1.0)
    .pan(0.3).superimpose(pan(0.7).transpose(12))
    .phaser(0.4).phaserdepth(saw.range(0.0, 0.6).slow(16)).phasersweep(900).phasercenter(1400)
    .gain(0.07).orbit(5).room(0.4).rsize(6) //  .solo()

// ── THE WIND (riser used inside a 16-cycle section so saw ramps once)
let riser = note("c").fast(2).sound("pink").superimpose(x => x.sound("brown"))
    .lpf(saw.range(200, 5000).slow(16)).resonance(1.8)
    .adsr("0.005:0:1:0.05").legato(1.2)
    .gain(saw.range(0.0, 0.19).slow(16))
    .hpf(150).orbit(7)

// ── THE HIT — fires once at the start of every [2, hit] iteration.
// The hit segment is 2 cycles, so inner time advances by 2 per arrange loop;
// plain `t < 1` would only match the very first iteration. `t % 2 < 1`
// matches the first cycle of every 2-cycle period, so the hit fires every loop.
let hitKick = s("bd").gain(0.95).hpf(40).adsr("0.001:0.22:0.0:0.05").orbit(1)
let hitBass = note("d2").sound("saw").distort("0.8:hard:4")
    .hpf(100).lpf(900).resonance(2.5).adsr("0.02:0.3:0.7:6.0")
    .gain(0.4).postgain(0.22).warmth(0.25)
    .orbit(0)
let hitSub  = note("d1").sound("sine")
    .adsr("0.005:0.3:0.7:6.0").lpf(120).gain(0.45)
    .orbit(6)
let hitCrash = s("cr").gain(0.75).hpf(200).orbit(8).room(0.25).rsize(4).adsr("0.005:0.3:1.0:2.0")
let hitStab = chord("Dm").voicing()
    .sound("supersaw").unison(4).detune(0.08).distort(0.2)
    .adsr("0.005:0.3:0.7:6.0")
    .lpf("120:1:15").lpadsr("2.0:1.5:0.33:6.0")
    .pan(0.2).superimpose(transpose(-12), pan(0.8).transpose(12))
    .gain(0.32).postgain(0.3)
    .orbit(9).room(0.4).rsize(5)
// Offbeat hi-hat keeps the rhythmic flow alive through the hit + tail.
// No filterWhen — plays naturally across the full 2-cycle hit segment.
let hitHat = s("[hh ~]!4").gain(0.25).hpf(7000).adsr("0.001:0.04:0.0:0.04").orbit(2)
let hit = stack(
    hitHat,
    stack(hitKick, hitBass, hitSub, hitCrash, hitStab).filterWhen(t => t % 4 < 1),
)

// ── QUIET BUILD-UP: 64 cycles, smooth saw-based morph ──────────────
// melody3 (peak of original lament) degrades out; melody1 (opening of
// lament) un-degrades in. Rhythm + bass elements ramp via saw signals.
let melody3a = note(`<[d6 f6 g6 f6] [e6 c6 a5 g5] [d6 c6 d6 f6] [a5 g5  f5 e5] [d5 a4 d5  f5] [c5 g4 bb4 a4] [d5 c5 a4 g4] [d4@2  d4 ~]>`)
let melody1  = note(`<[d4 f4 e4 d4] [c4 a4 g4 f4] [d4 g4 f4 e4] [c4 bb4 a4 g4] [d5 c5 bb4 a4] [g4 e4 d4 e4]  [a4 g4 f4 e4] [f4 c4 a4 ~]>`)

let mel3 = melody3a.sound("saw").legato(0.75).hpf(80).lpf(2200).adsr("0.25:0.1:0.6:0.85").gain(0.15).orbit(0).pan(0.4)
let mel1 = melody1.sound("saw").legato(0.75).hpf(80).lpf(2500).adsr("0.22:0.1:0.6:0.85").gain(0.225).orbit(0).pan(0.6)

// One big 64-cycle section. saw.slow(64) ramps 0 → 1 across it.
// Rhythm/bass build via clean gain ramps (no degrade — that was unpleasant).
// Only the two melodies cross-fade via degradeBy.
let quietBuild = stack(
    // Kick — clean gain ramp from soft to full
    s("bd!4").gain(saw.range(0.7, 1.1).slow(64)).hpf(40).adsr("0.06:0.18:0.0:0.02").orbit(1),
    // Hat — gain grows
    s("hh!8").gain(saw.range(0.15, 0.3).slow(64)).hpf(7000).adsr("0.001:0.04:0.0:0.04").orbit(2),
    // Clap — gain swells in
    s("~ cp ~ cp").gain(saw.range(0.0, 0.35).slow(64)).hpf(300).orbit(4),
    // Open hat — gain swells in
    s("[~ hh sd oh]!4").gain(saw.range(0.0, 0.22).slow(64)).hpf(5000).orbit(2),
    // Sub bass — always present, gain grows
    note("<a1 d2 bb1 c2 g1 f1 a1 d2>").struct("<[x]!32 [x!2]!32>").sound("sine").legato(1.0).adsr("0.005:0.05:0.5:0.05")
        .hpf(80).lpf(220).lpenv(4).gain(saw.range(0.3, 0.4).slow(64)).orbit(6),
    // Saw bass — gain swells from silent to full
    note("<[a1!4] [d2!4] [bb1!4] [c2!4] [g1!4] [f1!4] [a1!4] [d2!4]>").sound("saw").legato(0.7).hpf(100).lpf(800)
        .adsr("0.002:0.08:0.5:0.05").distort("0.4:soft:2").postgain(0.4)
        .gain(saw.range(0.0, 0.45).slow(64)).orbit(0),
    // Melody 3 — velocity fades from full to silent
    mel3.velocity(saw.range(0.4, 0.8).min(0).max(1).slow(64)).euclidrot(3, 8, 1),
    // Melody 1 — velocity fades from silent to full
    mel1.velocity(saw.range(-0.25, 0.8).min(0).max(1.5).slow(64)).struct("[4!1]!4").euclidrot(3, 8, 1).vib(4).vibmod(0.10),
    // Syncopated pad stabs — 90s dance keyboard rhythm (3-3-4-2-2-2),
    // enter at section-local cycle 32 (= second half of the build)
    chord("<Am Dm Bb C Gm F Am Dm>").voicing().struct("[x@3 x@3 x@4 x@2 x@2 x@2]")
        .sound("superpulse").unison(2).detune(0.15).hpf(350).lpf(3000)
        .adsr("0.005:0.08:0.25:0.08").legato(0.7)
        .gain(0.16).orbit(5).room(0.4).rsize(6)
        .filterWhen(t => t % 64 >= 48),
    // Tetris-style techno bassline — driving 8ths with octave jumps,
    // pattern: root - 5 - octave - 5 - 3rd - 5 - root - 5 per chord
    note(`<[a1 e2 a2 e2 c2 e2 a1 e2] [d2 a2 d3 a2 f2 a2 d2 a2]
          [bb1 f2 bb2 f2 d2 f2 bb1 f2] [c2 g2 c3 g2 e2 g2 c2 g2]
          [g1 d2 g2 d2 bb1 d2 g1 d2] [f1 c2 f2 c2 a1 c2 f1 c2]
          [a1 e2 a2 e2 c2 e2 a1 e2] [d2 a2 d3 a2 f2 a2 d2 a2]>`)
        .sound("supersaw").unison(8).warmth(0.1).gain(0.65).adsr("0.005:0.2:0.7:0.15").pan(0.3)
        .superimpose(transpose("<0 12 24 12>/8").pan(0.7)).phaser(1/13).phaserdepth(0.25).phasercenter(3500).phasersweep(1000)
        .detune(sine.range(0.05, 0.45).slow(64)).hpf(120).lpf(3200)
        .velocity(saw.range(-0.5, 1.0).min(0).slow(64))
        .orbit(7),
)

// ── DARK BUILD-UP: 64 cycles, no melodies ──────────────────────────
// Kick / hat / sub / clap / oh / chords / tetris bass continue.
// Filters close down, distortion grows. Sub + saw bass + tetris bass
// sidechain-pump on every kick. A spheric supersine pad floats on top
// in a syncopated rhythm, drifting slowly across the stereo field.
let darkBuild = stack(
    // Kick — full power, slightly longer body
    s("bd:2!4").gain(1.2).hpf(40).adsr("0.03:0.3:0.5:0.2").orbit(1),
    // Hat
    s("hh!8").gain(0.3).hpf(7000).adsr("0.001:0.04:0.0:0.04").orbit(2),
    // Clap
    s("~ cp ~ [cp,rim]").gain(0.35).hpf(300).orbit(4),
    // Open hat
    s("[~ ~ ~ oh]!4").gain(0.25).hpf(5000).orbit(2),
    // Sub bass — sidechain pump (drops at each kick, recovers between)
    note("<a1 d2 bb1 c2 g1 f1 a1 d2>").struct("<[x!2]!32 [x!4]!32>")
        .sound("sine").legato(1.0).adsr("0.005:0.05:0.5:0.05")
        .hpf(60).lpf(220).gain(saw.fast(4).range(0.35, 0.55))
        .orbit(6),
    // Saw bass — pumping, LPF closes, warmth + distortion grow
    note("<[a1!4] [d2!4] [bb1!4] [c2!4] [g1!4] [f1!4] [a1!4] [d2!4]>")
        .sound("saw").legato(0.7)
        .hpf(90).lpf(saw.range(900, 280).slow(64)).adsr("0.002:0.08:0.5:0.05")
        .distort("0.7:hard:4").postgain(0.5)
        .warmth(saw.range(0.6, 0.2).slow(64))
        .gain(saw.fast(4).range(0.4, 0.7))
        .orbit(0),
    // Tetris bassline — same pattern, pumps, LPF closes, more grit
    note(`<[a1 e2 a2 e2 c2 e2 a1 e2] [d2 a2 d3 a2 f2 a2 d2 a2] [bb1 f2 bb2 f2 d2 f2 bb1 f2] [c2 g2 c3 g2 e2 g2 c2 g2]
          [g1 d2 g2 d2 bb1 d2 g1 d2] [f1 c2 f2 c2 a1 c2 f1 c2] [a1  e2 a2  e2 c2 e2 a1  e2] [d2 a2 d3 a2 f2 a2 d2 a2]>`)
        .sound("supersaw").unison(12).warmth(saw.range(0.3, 0.7).slow(64)).gain(0.25).distort(saw.range(0.35, 0.85).slow(64))
        .pan(0.3).superimpose(pan(0.7)).superimpose(transpose("<0 12 0 -12>/8").gain(0.2))
        .phaser(1/11).phaserdepth(0.25).phasercenter(3500).phasersweep(500)
        .detune(sine.range(0.1, 0.25).slow(64))
        .hpf(120).lpf(saw.range(2000, 4000).slow(64)).adsr("0.005:0.2:0.5:0.15")
        .orbit(7),
    // Syncopated pad stabs — keep the 90s rhythm but darken with section
    chord("<Am [Dm|Dm|D] <Bb!2 [Bb|Bb2]> C Gm [F|F|Dm] Am Dm>").voicing()
        .struct("[x@3 x@3 x@4 x@2 x@2 x@2]")
        .sound("superpulse").unison(2).detune(0.20)
        .hpf(300).lpf(sine.range(2000, 1200).slow(32))
        .adsr("0.005:0.08:0.25:0.08").legato(0.7)
        .gain(0.18).orbit(5).room(0.4).rsize(6),
    // Spheric supersine stabs — syncopated 5-3-3-3 (16ths), wide slow drift
    note("<a5 d6 bb5 c6 g5 f5 a5 d6>")
        .sound("supersine").unison(8).detune(0.07).adsr("0.5:0.5:0.5:0.5")
        .hpf(1000).lpf(3000).bandf(sine.range(2000, 4000).slow(8)).lpenv(2).vib(pure(1/2).div(cps)).vibmod(0.1)
        .gain(saw.range(0.0, 0.4).slow(64))
        .pan(sine.range(0.05, 0.95).slow(3))
        .delay(0.4).delaytime(pure(2/8).div(cps)).delayfeedback(0.45)
        .orbit(10).room(0.7).rsize(10),
)

// ── Sections ────────────────────────────────────────────────────────
let warm     = core
let withSub  = stack(core, sub)
let withPerc = stack(core, sub, clap, oh, rim)
let s1       = stack(core, sub, clap, oh, rim, leadA)
let s2       = stack(core, sub, clap, oh, rim, leadB, pad)
let s3       = stack(core, sub, clap, oh, rim, leadC, pad)
let s4       = stack(core, sub, clap, oh, rim, leadD, pad)
let finale   = stack(core, sub, clap, oh, rim, leadE, pad, riser)
let pause    = silence

arrange(
  [16, warm],         // 0-15: LOOP POINT — kick + hat + bass already running
  [8, withSub],       // 16-23: + sub
  [8, withPerc],      // 24-31: + clap + oh + rim
  [8, s1],            // 32-39: + leadA
  [8, s2],            // 40-47: + leadB + pad
  [8, s3],            // 48-55: + leadC + pad
  [8, s4],            // 56-63: + leadD + pad
  [16, finale],       // 64-79: + leadE (loops once) + pad + 16-cycle wind fade
  [4, hit],           // 80-83: unison hit lands on beat 1 right as wind ends
  [64, quietBuild],   // 84-147: smooth morph — two melodies fade in
  [64, darkBuild]     // 148-211: no melodies — bass + bassline pump, filters close upen up, spheric stabs drift in stereo
).compressor("-15:2:6:0.01:0.2")
 .room(0.1).rsize(4).analog(2.0)

// Inspired by: The Synthsale Piper's Farewell — gone clubbing
// Composed by: Claude, Motör, peekandpoke





            """,
)
