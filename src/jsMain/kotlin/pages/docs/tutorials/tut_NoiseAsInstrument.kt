package io.peekandpoke.klang.pages.docs.tutorials

val noiseAsInstrumentTutorial = Tutorial(
    slug = "noise-as-instrument",
    title = "Noise as an Instrument",
    description = "Shape brown and pink noise into percussion, textures, and melodic elements using filters and envelopes.",
    difficulty = TutorialDifficulty.Advanced,
    scope = TutorialScope.DeepDive,
    tags = listOf("noise", "brown", "pink", "texture", "experimental"),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "Most people think of noise as something to avoid. But in sound design, noise is a secret weapon. Brown noise is deep and rumbly like ocean waves. Pink noise is balanced and warm like rain on a window. With the right filters and envelopes, noise becomes percussion, atmosphere, and even melody. Time to cook with raw ingredients.",
        ),
        TutorialSection(
            heading = "Brown Noise — Deep and Warm",
            text = "Brown noise has more energy in the low frequencies — it sounds like a distant storm or a waterfall. On its own it is overwhelming, but filtered down it becomes a beautiful sub-layer or a warm bed for other sounds to sit on.",
            code = """// The base stock: rich, warm, and deep
sound("brown")
  .lpf(300).gain(0.15)""",
        ),
        TutorialSection(
            heading = "Pink Noise — Balanced and Airy",
            text = "Pink noise is brighter than brown — it has equal energy per octave, making it feel balanced and natural. High-pass it to create airy texture, or band-pass it for a focused, radio-static quality.",
            code = """// A sprinkle of air: bright and wispy
sound("pink")
  .hpf(3000).gain(0.1)""",
        ),
        TutorialSection(
            heading = "Noise as Percussion",
            text = "Noise with a short adsr becomes percussion. A sharp attack with quick decay turns pink noise into a snare-like hit. Band-pass it at different frequencies to create different timbres — low for toms, mid for snares, high for hats. All from the same ingredient.",
            code = """stack(
  // Low thump — like a hand drum
  sound("brown")
    .bandf(200).adsr("0.01:0.1:0.0:0.05")
    .gain(0.5),
  // Crisp snap — DIY snare
  sound("pink ~ pink ~")
    .hpf(2000).adsr("0.01:0.08:0.0:0.03")
    .gain(0.4),
  // Sizzle — synthetic hi-hat
  sound("pink pink pink pink").fast(2)
    .hpf(6000).adsr("0.01:0.03:0.0:0.02")
    .gain(0.2)
)""",
        ),
        TutorialSection(
            heading = "Evolving Textures with every()",
            text = "Static noise gets boring fast. Use every() to shift the filter frequency on certain cycles, creating movement. The noise opens up and closes back down — like waves washing in and pulling out. Alive without a single note being played.",
            code = """// Watch: the filter breathes every 4th cycle
sound("brown")
  .lpf(300)
  .every(4, lpf(800))
  .gain(0.12)""",
        ),
        TutorialSection(
            heading = "Stereo Noise Bed",
            text = "Pan two different noise sources to opposite sides for a wide, immersive stereo bed. Brown noise rumbles on the left, pink noise shimmers on the right. Together they create a space that feels three-dimensional.",
            code = """stack(
  // Left ear: the warm rumble
  sound("brown").lpf(400)
    .pan(0.15).gain(0.1).orbit(0),
  // Right ear: the bright shimmer
  sound("pink").hpf(4000)
    .pan(0.85).gain(0.08).orbit(1)
)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is a full piece built almost entirely from noise. The drums are shaped noise. The texture bed is stereo noise. Only the bass uses a traditional waveform — and even that sits so low it blurs the line between tone and rumble. Proof that noise is not just background — it is an instrument.",
            code = """stack(
  // Noise percussion — all from scratch
  sound("brown ~ brown ~")
    .bandf(200).adsr("0.01:0.1:0.0:0.05")
    .gain(0.5).orbit(0),
  sound("~ pink ~ pink")
    .hpf(2000).adsr("0.01:0.08:0.0:0.03")
    .gain(0.4).orbit(0),
  sound("[pink pink pink pink]").fast(2)
    .hpf(6000).adsr("0.01:0.03:0.0:0.02")
    .gain(0.15).orbit(0),
  // The stereo noise bed — atmosphere for free
  sound("brown").lpf(300)
    .pan(0.2).gain(0.08).orbit(1),
  sound("pink").hpf(4000)
    .pan(0.8).gain(0.06).orbit(2),
  // Just a touch of pitched bass to anchor it all
  n("0 ~ 0 ~").scale("C2:minor")
    .sound("sine").lpf(150)
    .gain(0.4).orbit(3)
)""",
        ),
    ),
)
