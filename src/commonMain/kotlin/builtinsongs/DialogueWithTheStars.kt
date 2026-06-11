@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

internal val dialogueWithTheStarsSong = Song(
    id = "${BuiltInSongs.PREFIX}-dialogue-with-the-synths",
    title = "Dialogue with the Synths",
    rpm = 31.5,  // 126 BPM / 4 beats per measure → 1 cycle = 1 bar
    icon = "star",
    code = """
import * from "stdlib"
import * from "sprudel"

// ── Three Guitar Variants ─────────────────────────────────────────────────────────

// Variant 0 — open distorted (sustained leads / chords)
let openGuitar = (() => {
  let pSpread     = Osc.param("spread",         0.05,   "Supersaw voice detuning")
  let pAnalog     = Osc.param("analog",         1.00,   "Analog pitch drift")
  let pVoices     = Osc.param("voices",         8,      "Number of unison voices")
  let pDrive      = Osc.param("drive",          1.0,    "Primary distortion drive level")
  let pBrightness = Osc.param("brightness",  5000.0,    "Post-distortion lowpass cutoff in Hz")
  let pAttack     = Osc.param("attack",         0.005,  "Attack time in seconds")
  let pSustain    = Osc.param("sustain",        0.8,    "Sustain level")

  let signal = Osc.supersaw(freq = Osc.freq(), voices = pVoices, freqSpread = pSpread)
      .analog(pAnalog).mul(0.2)

  return signal
    .lowpass(Osc.sine(0.40).plus(1).times(500).plus(pBrightness), 1.20)              // Pre-distortion sweep
    .plus(Osc.berlin(4.0).highpass(2000).adsr(pAttack, 0.05, 0.0, 0.005).mul(0.01))  // Tiny attack noise
    .bandpass(750, 0.30)                                                             // Mid focus
    .distort(pDrive, "tube", 2)                                                      // Overdrive + oversample
    .lowpass(pBrightness, 1.0)                                                       // Post-distortion warmth
    .highpass(Osc.freq(), 1.0)                                                       // Cut muddy lows
    .warmth(12000)
    .adsr(pAttack, 0.15, pSustain, 0.03)
})()

// Variant 1 — palm-muted chug (rhythmic, tight envelope)
let mutedGuitar = (() => {
  let pSpread     = Osc.param("spread",  0.05, "Supersaw voice detuning")
  let pAnalog     = Osc.param("analog",  1.00, "Analog pitch drift")
  let pVoices     = Osc.param("voices",  8,    "Number of unison voices")
  let pDrive      = Osc.param("drive",   1.0,  "Primary distortion drive level")

  let signal = Osc.supersaw(freq = Osc.freq(), voices = pVoices, freqSpread = pSpread)
      .analog(pAnalog).mul(0.2)

  let chugTop = signal
      .highpass(85).distort(pDrive, "tube", 4).lowpass(1800, 0.6)
      .adsr(0.005, 0.05, 0.05, 0.04)

  let chugSub = Osc.sine()
      .adsr(0.005, 0.10, 0.20, 0.04)

  return chugTop.plus(chugSub)
})()

// Variant 2 — nylon-string acoustic. Additive partials, no Karplus-Strong.
let accusticGuitar = (() => {
  // String fundamental + overtones; each overtone gets its own decay so the timbre
  // dims into the fundamental over time — same trick a real guitar string does.
  let signal = Osc.triangle().mul(0.42)
      .plus(Osc.sine().mul(0.30))
      .plus(Osc.sine().detune(12.02).mul(0.18).adsr(0.001, 0.55, 0.20, 0.18))   // octave
      .plus(Osc.sine().detune(19.04).mul(0.10).adsr(0.001, 0.30, 0.08, 0.10))   // 12th
      .plus(Osc.sine().detune(24.05).mul(0.06).adsr(0.001, 0.20, 0.03, 0.06))   // 15th
      .plus(Osc.sine().detune(28.10).mul(0.03).adsr(0.001, 0.12, 0.0,  0.04))   // 17th
      // Fingertip pluck transient — short midrange noise burst
      .plus(Osc.whitenoise().bandpass(2800, 3.5).adsr(0.0005, 0.020, 0.0, 0.008).mul(0.22))
      // Soundbox thump — fixed-pitch low body resonance excited by attack
      .plus(Osc.sine(180).adsr(0.001, 0.08, 0.0, 0.04).mul(0.15))

  return signal
    // Brightness decays — string stiffness loses highs over time
    .lowpass(Osc.constant(2400).plus(Osc.constant(3000).adsr(0.001, 0.45, 0.10, 0.20)))
    .highpass(85)
    .warmth(4200)
    .pitchEnvelope(0.4, 0.001, 0.04)
    .analog(0.6)
    .adsr(0.003, 0.7, 0.35, 0.4)
})()

let guitar = Osc.variants(openGuitar, mutedGuitar, accusticGuitar)

// ── Section Scaffold ─────────────────────────────────────────────────────────
//
// Section map taken from the source arrangement (96 bars total, 4/4, 126 BPM):
//
//   m1–2   Intro          (2)
//   m3–10  Main Theme     (8)
//   m11–18 Riff A         (8)
//   m19–26 Main Theme     (8)
//   m27–34 Riff A         (8)
//   m35–42 Riff B         (8)
//   m43–50 Riff C         (8)
//   m51–58 Riff B         (8)
//   m59–66 Riff C         (8)
//   m67–74 Bridge         (8)
//   m75–82 Bridge (Solo)  (8)
//   m83–90 Main Theme     (8)
//   m91–96 Outro          (6)
//
// Each section currently holds a quiet C3 placeholder so the arrangement
// length is audible — replace each `let X = placeholder` with the real
// stack(...) of voice patterns as notes come in.
//
// Voice naming convention for each section:
//   <section>L  — Left acoustic (gtr Left in the tab)
//   <section>R  — Right acoustic (gtr Right in the tab)
//   <section>B  — Bass / heavy chug / drums (when the section calls for it)

let placeholder = note("c3").s(accusticGuitar).gain(0.20).legato(0.9)

// ── m1–2 · Intro ─────────────────────────────────────────────────────────────
// TODO: introL / introR
let intro = placeholder

// ── m3–10 · Main Theme (also m19–26 and m83–90) ─────────────────────────────
// TODO: mainThemeL / mainThemeR / optional bass
let mainTheme = placeholder

// ── m11–18 · Riff A (also m27–34) ───────────────────────────────────────────
// TODO: riffAL / riffAR (decide later if this is acoustic-only or heavy)
let riffA = placeholder

// ── m35–42 · Riff B (also m51–58) ───────────────────────────────────────────
let riffB = placeholder

// ── m43–50 · Riff C (also m59–66) ───────────────────────────────────────────
let riffC = placeholder

// ── m67–74 · Bridge ─────────────────────────────────────────────────────────
let bridge = placeholder

// ── m75–82 · Bridge (Solo) ──────────────────────────────────────────────────
// TODO: solo voice on openGuitar variant if we go metal here, else acoustic lead
let bridgeSolo = placeholder

// ── m91–96 · Outro ──────────────────────────────────────────────────────────
let outro = placeholder

// ── Arrange ─────────────────────────────────────────────────────────────────
arrange(
  [2, intro],
  [8, mainTheme],
  [8, riffA],
  [8, mainTheme],
  [8, riffA],
  [8, riffB],
  [8, riffC],
  [8, riffB],
  [8, riffC],
  [8, bridge],
  [8, bridgeSolo],
  [8, mainTheme],
  [6, outro],
).compressor("-12:2:6:0.02:0.25").analog(2).engine("pedal")


// Inspired by: In Flames — "Dialogue with the Stars" (The Jester Race, 1996)
// https://open.spotify.com/track/2zNbVdEvJVDvAtRYZUmgvX

            """,
)
