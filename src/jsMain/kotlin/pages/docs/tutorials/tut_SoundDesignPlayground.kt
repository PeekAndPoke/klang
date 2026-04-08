package io.peekandpoke.klang.pages.docs.tutorials

val soundDesignPlaygroundTutorial = Tutorial(
    slug = "sound-design-playground",
    title = "Sound Design Playground",
    description = "Sculpt wild textures by chaining distortion, bit-crushing, tremolo, vibrato, and phaser effects.",
    difficulty = TutorialDifficulty.Pro,
    scope = TutorialScope.Standard,
    tags = listOf(TutorialTag.Synthesis, TutorialTag.Effects),
    rpm = 32.0,
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "You have learned to play notes, shape them with filters and envelopes, and transform patterns over time. Now we go deeper — into sound design itself. This tutorial teaches you to sculpt raw timbres using distortion, bit-crushing, and modulation effects. The order you chain them matters, and discovering that is half the fun.",
        ),

        // ── DISTORTION ────────────────────────────────────────────────
        TutorialSection(
            heading = "Distort for Grit",
            text = "distort() adds harmonic saturation — subtle warmth at low values, aggressive clipping at high values. You can pick a shape: \"gentle\" rounds the edges, \"hard\" clips them flat, \"soft\" is somewhere in between. postgain() controls the level after distortion so you don't blow out your speakers.",
            code = """// Rough-cut the stone — distort adds grit and character
n("0 2 4 7").scale("E4:minor")
  .sound("saw").lpf(2000).hpf(200)
  // Try different shapes: "gentle", "soft", "hard"
  .distort("0.5:gentle").postgain(0.5)
  .gain(0.4)""",
        ),
        TutorialSection(
            heading = "Distortion That Moves",
            text = "Here is a pro trick: instead of a fixed distortion amount, use a signal to modulate it. sine.range(0.2, 0.8).slow(64) sweeps the distortion from subtle to aggressive over 64 cycles. The sound evolves on its own — set it and forget it.",
            code = """// The grain shifts over time — modulated distortion is alive
n("0 0 4 0").scale("E2:minor")
  .sound("saw").lpf(800)
  // sine sweeps distort amount from 0.2 to 0.8 over 64 cycles
  .distort(sine.range(0.2, 0.8).slow(64))
  .adsr("0.01:0.1:0.5:0.1").gain(0.5)""",
        ),

        // ── CRUSH ─────────────────────────────────────────────────────
        TutorialSection(
            heading = "Crush for Lo-Fi",
            text = "crush() reduces bit depth for a digital, retro texture. Low values are extreme — try crush(3) for chiptune, crush(8) for subtle warmth. Here we crush a drum beat. The hi-hats get an especially crunchy character.",
            code = """// Chip away the resolution — crush turns clean into crunchy
stack(
  sound("bd sd bd sd").crush(6).gain(0.8),
  sound("hh hh oh hh").crush(7).gain(0.5)
)""",
        ),
        TutorialSection(
            heading = "Crush That Sweeps",
            text = "Just like distortion, you can modulate crush over time. saw.range(6, 1).slow(32) sweeps from mild bit-reduction to extreme destruction over 32 cycles. The sound starts clean and gradually disintegrates.",
            code = """// Erosion over time — crush sweeps from clean to destroyed
n("<0 3 5 7>").scale("E3:minor")
  .sound("supersquare").lpf(1200)
  // saw sweeps crush from 6 bits (mild) to 1 bit (extreme)
  .crush(saw.range(6, 1).slow(32))
  .adsr("0.03:0.1:0.5:0.2").gain(0.3)""",
        ),

        // ── TREMOLO ───────────────────────────────────────────────────
        TutorialSection(
            heading = "Tremolo — Pulsing Volume",
            text = "Tremolo rapidly modulates volume, creating a pulsing, rhythmic effect. But tremolo() alone just sets the rate — you need tremolodepth() to control how deep the pulse goes. Without depth, nothing happens. Try changing both values.",
            code = """// Feel the pulse — tremolo breathes life into a static pad
n("<0 3 5 7>").scale("E3:minor")
  .sound("supersquare").lpf(1200).hpf(600)
  // tremolo sets the speed, tremolodepth sets how deep it pulses
  .tremolo(3).tremolodepth(0.4)
  .adsr("0.03:0.1:0.5:0.2").gain(0.3)""",
        ),

        // ── VIBRATO ───────────────────────────────────────────────────
        TutorialSection(
            heading = "Vibrato — Pitch Wobble",
            text = "While tremolo modulates volume, vibrato modulates pitch. vibrato() sets the speed and vibratoMod() sets how far the pitch bends. Subtle values add life — like a singer's natural wobble. Heavy values create a seasick, detuned feel.",
            code = """// A gentle wobble — vibrato adds the human touch
n("0 2 4 7").scale("E4:minor")
  .sound("saw").lpf(2000)
  // vibrato sets the speed, vibratoMod sets the depth
  .vibrato(5).vibratoMod(0.1)
  .adsr("0.01:0.5:0.5:0.2").gain(0.4)""",
        ),
        TutorialSection(
            heading = "Vibrato with Perlin Noise",
            text = "A fixed vibratoMod sounds mechanical. Use perlin noise to make the depth drift organically — sometimes deeper, sometimes shallower, never exactly the same twice. perlin.mul(0.02).add(0.05) drifts the depth around 0.05 with subtle random variation.",
            code = """// Not mechanical — perlin makes the wobble drift like a real player
n("0 2 4 7").scale("E4:minor")
  .sound("saw").lpf(2000)
  // perlin makes the depth drift — alive, not robotic
  .vibrato(5).vibratoMod(perlin.mul(0.02).add(0.05))
  .adsr("0.01:0.5:0.5:0.2").gain(0.4)""",
        ),

        // ── PHASER ────────────────────────────────────────────────────
        TutorialSection(
            heading = "Phaser — Sweeping Texture",
            text = "A phaser creates a sweeping, whooshing sound by splitting the signal and shifting its phase. Like tremolo, phaser() sets the rate and phaserdepth() controls how pronounced the sweep is. Combined with reverb, it creates lush, evolving textures.",
            code = """// The surface shimmers — phaser adds movement without changing notes
n("<0 3 5 7>").scale("E3:minor")
  .sound("supersquare").lpf(1200).hpf(600)
  // phaser sweeps, phaserdepth controls how wide
  .phaser(0.125).phaserdepth(1.5)
  .adsr("0.03:0.1:0.5:0.2")
  .room(0.3).rsize(6).gain(0.3)""",
        ),

        // ── STRUCT ────────────────────────────────────────────────────
        TutorialSection(
            heading = "Stamp a Rhythm with struct()",
            text = "struct() imposes a rhythmic grid onto any sound. Instead of writing out every note, you write one pitch and let struct stamp it into a pattern. \"x\" means play, \"~\" means rest. Here struct(\"x!16\") turns a single chord change into sixteen rapid pulses — like pressing a texture mold into wet clay.",
            code = """// Press a rhythm into the clay — struct stamps a grid onto the sound
n("<0 3 5 7>").scale("E3:minor")
  // struct("x!16") turns each chord into 16 rapid pulses
  .struct("x!16")
  .sound("supersquare").lpf(1200).hpf(600)
  .phaser(0.125).phaserdepth(0.5)
  .tremolo(3).tremolodepth(0.2)
  .adsr("0.03:0.1:0.5:0.2").clip(0.85)
  .gain(0.3)""",
        ),

        // ── SUPERIMPOSE ───────────────────────────────────────────────
        TutorialSection(
            heading = "Stack Copies with superimpose()",
            text = "superimpose() layers a transformed copy on top of the original. Here we add an octave-up copy that fades in over time using velocity(). The result: a single melody that gradually grows thicker and more intense without you writing a second voice.",
            code = """// Stack a ghost copy on top — it fades in over time
n("0 2 4 7").scale("E4:minor")
  .sound("saw").lpf(2000)
  .gain(0.3)
  // transpose up an octave, detuned, fading in
  .superimpose(
    transpose(12).detune(0.125)
      .velocity("<0!4 0.15!4>")
  )""",
        ),

        // ── THE BUILD-UP ──────────────────────────────────────────────
        TutorialSection(
            heading = "The Drum Build-Up",
            text = "The drums tell a story of escalating tension. The pattern starts with two kicks per cycle, doubles to four, then eight, sixteen, twenty-four — then finally drops into a full groove with snares. crush() adds lo-fi grit throughout. The mini-notation does all the work: bd!2 means two kicks, bd!16 means sixteen.",
            code = """// The build-up — density rises, then drops into the groove
sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd sd bd sd]!8 [bd [bd,sd] bd [bd,sd]]!8>")
  .crush(9).gain(1.0)""",
        ),

        // ── THE MASTER BUS ────────────────────────────────────────────
        TutorialSection(
            heading = "The Master Bus — Compressor and Analog",
            text = "Two final touches on the whole mix. compressor() evens out the volume — it tames the loud parts and lifts the quiet parts, gluing all the layers together like varnish on the finished sculpture. analog() adds random pitch drift to every voice — a subtle imperfection that makes the whole thing feel alive instead of clinical.",
            code = """// Varnish the sculpture — compressor glues, analog breathes life
stack(
  n("0 2 4 7").scale("E4:minor")
    .sound("saw").lpf(2000)
    .distort("0.3:gentle").postgain(0.5)
    .gain(0.3),
  sound("bd sd bd sd").crush(6).gain(0.7)
).compressor("-10:2:10:0.02:0.25").analog(1)""",
        ),

        // ── PUTTING IT ALL TOGETHER ───────────────────────────────────
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is the finished sculpture, inspired by Editors — Papillon. Every layer has its own sonic identity. The lead is distorted with vibrato for grit and expression. The pad uses struct() for rhythmic pulsing, phaser and tremolo for shimmering movement. The bass is warm with subtle distortion. The drums build from sparse to dense, all crushed for lo-fi character. The compressor on the master bus glues everything together, and analog() adds random pitch drift across all voices for an organic, imperfect feel.",
            code = """// The finished sculpture — every texture carved with purpose
stack(
  // Lead — distorted saw with vibrato expression
  n("<[-7 0 2 4] [-7 0 4 2] [-5 -1 2 4] [-6 -1 3 1]>*2")
    .scale("E4:minor").sound("supersaw").unison(2).detune(0.05)
    .lpf(4000).hpf(200)
    .vibrato(5).vibratoMod(perlin.mul(0.02).add(0.05))
    .gain(0.25).distort("0.5:gentle").postgain(0.65)
    .adsr("0.03:0.1:0.6:0.1").clip(0.85)
    .release("<0.1!16 0.4!16>")
    .superimpose(transpose(12).detune(0.125).velocity("<0!32 0.15!32>").lpf(5000))
    .superimpose(transpose(24).detune(0.25).velocity("<0!96 0.05!32>").lpf(6000))
    .orbit(0).pan(0.6),
  // Pad — struct stamps the rhythm, phaser + tremolo shimmer
  n("<[0 2 0 [-2 -4]] [0 2 0 [0 -2]] [0 6 0 [5 6]] [4 2 0 [5 2]]>/4").struct("x!16")
    .scale("e2:minor").sound("supersquare")
    .lpf(1800).hpf(400)
    .phaser(0.25).phaserdepth(0.5)
    .tremolo(3).tremolodepth(0.2)
    .adsr("0.01:0.1:0.5:0.5").clip(0.85).crush(saw.range(6, 1).slow(32))
    .gain(0.4).orbit(1).pan(0.4),
  // Bass — warm distorted saw, struct for steady pulse
  n("<0 0 2 4 0 0 -2 -1>").struct("x!8").fast(2).velocity("1 0.9!3".fast(4))
    .scale("e2:minor").sound("saw")
    .lpf(800).hpf(120).distort(sine.range(0.2, 0.8).slow(64))
    .adsr("0.01:0.125:0.3:0.1").clip(0.9)
    .gain(0.5).orbit(2),
  // Drums — crushed, building from sparse to dense
  sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd sd bd sd]!8 [bd [bd,sd] bd [bd,sd]]!8>")
    .crush(9).gain(1.2).orbit(3),
  sound("<[hh hh oh hh]!24 [cr hh cr hh]!16>").fast(2).crush(8).hpf(3000)
    .gain(0.7).orbit(3)
// Compressor glues it, analog adds organic drift
).compressor("-10:2:10:0.02:0.25").analog(1)

// Inspired by: Editors - Papillon
// https://open.spotify.com/intl-de/track/7hYiX6LMP8w8d0kEc4KWuW""",
        ),
    ),
)
