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
            heading = "Crush for Lo-Fi",
            text = "crush() reduces bit depth for a digital, retro texture. Low values are extreme — try crush(3) for chiptune, crush(8) for subtle warmth. Here we crush a drum beat. The hi-hats get an especially crunchy character.",
            code = """// Chip away the resolution — crush turns clean into crunchy
stack(
  sound("bd sd bd sd").crush(6).gain(0.8),
  sound("hh hh oh hh").crush(7).gain(0.5)
)""",
        ),
        TutorialSection(
            heading = "Tremolo — Pulsing Volume",
            text = "Tremolo rapidly modulates volume, creating a pulsing, rhythmic effect. But tremolo() alone just sets the rate — you need tremolodepth() to control how deep the pulse goes. Without depth, nothing happens. Try changing both values.",
            code = """// Feel the pulse — tremolo breathes life into a static pad
n("<0 4 0 [-2 -4]>").scale("E3:minor")
  .sound("supersquare").lpf(1200).hpf(600)
  // tremolo sets the speed, tremolodepth sets how deep it pulses
  .tremolo(3).tremolodepth(0.4)
  .adsr("0.03:0.1:0.5:0.2").gain(0.3)""",
        ),
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
            heading = "Phaser — Sweeping Texture",
            text = "A phaser creates a sweeping, whooshing sound by splitting the signal and shifting its phase. Like tremolo, phaser() sets the rate and phaserdepth() controls how pronounced the sweep is. Combined with reverb, it creates lush, evolving textures.",
            code = """// The surface shimmers — phaser adds movement without changing notes
n("<0 4 0 [-2 -4]>").scale("E3:minor")
  .sound("supersquare").lpf(1200).hpf(600)
  // phaser sweeps, phaserdepth controls how wide
  .phaser(0.125).phaserdepth(1.5)
  .adsr("0.03:0.1:0.5:0.2")
  .room(0.3).rsize(6).gain(0.3)""",
        ),
        TutorialSection(
            heading = "Stamp a Rhythm with struct()",
            text = "struct() imposes a rhythmic grid onto any sound. Instead of writing out every note, you write one pitch and let struct stamp it into a pattern. \"x\" means play, \"~\" means rest. Here struct(\"x!16\") turns a single chord change into sixteen rapid pulses — like a sculptor pressing a texture mold into wet clay.",
            code = """// Press a rhythm into the clay — struct stamps a grid onto the sound
n("<0 4 0 [-2 -4]>").scale("E3:minor")
  // struct("x!16") turns each chord into 16 rapid pulses
  .struct("x!16")
  .sound("supersquare").lpf(1200).hpf(600)
  .phaser(0.125).phaserdepth(1.5)
  .tremolo(3).tremolodepth(0.2)
  .adsr("0.03:0.1:0.5:0.2").clip(0.85)
  .gain(0.3)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is the finished sculpture, inspired by Editors — Papillon. Every layer has its own sonic identity. The lead is distorted with vibrato for grit and expression. The pad uses struct() for rhythmic pulsing, phaser and tremolo for shimmering movement. The bass is warm with subtle distortion. The drums build from sparse to dense, all crushed for lo-fi character. The compressor on the master bus glues everything together, and analog() adds random pitch drift across all voices for an organic, imperfect feel.",
            code = """// The finished sculpture — every texture carved with purpose
stack(
  // Lead — distorted saw with vibrato expression
  n("<[-7 0 2 4] [-7 0 4 2] [-5 -1 2 4] [-6 -1 3 1]>*2")
    .scale("E4:minor").sound("saw")
    .lpf(2000).hpf(200)
    .vibrato(5).vibratoMod(perlin.mul(0.05).add(0.05))
    .gain(0.3).distort("0.5:gentle").postgain(0.75)
    .adsr("0.01:0.5:0.5:0.2").clip(0.85)
    .orbit(0).pan(0.6),
  // Pad — struct stamps the rhythm, phaser + tremolo shimmer
  n("<0 4 0 [-2 -4]>").struct("x!16")
    .scale("e3:minor").sound("supersquare")
    .lpf(1200).hpf(600)
    .phaser(0.125).phaserdepth(1.5)
    .tremolo(3).tremolodepth(0.2)
    .adsr("0.03:0.1:0.5:0.2").clip(0.85)
    .room(0.3).rsize(6)
    .gain(0.1).orbit(1).pan(0.4),
  // Bass — warm distorted saw, struct for steady pulse
  n("<0 0 2 4 0 0 -2 -1>").struct("x!8").fast(2)
    .scale("e2:minor").sound("saw")
    .lpf(400).distort(0.2)
    .adsr("0.01:0.1:0.5:0.1").clip(0.9)
    .gain(0.65).orbit(2),
  // Drums — crushed, building from sparse to dense
  sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd sd bd sd]!8 [bd [bd,sd] bd [bd,sd]]!8>")
    .crush(6).gain(1.2).orbit(3),
  sound("hh hh oh hh").fast(2).crush(7)
    .gain(0.6).orbit(3)
// Compressor glues it, analog adds organic drift
).compressor("-10:2:10:0.02:0.25").analog(1)

// Inspired by: Editors - Papillon
// https://open.spotify.com/intl-de/track/7hYiX6LMP8w8d0kEc4KWuW""",
        ),
    ),
)
