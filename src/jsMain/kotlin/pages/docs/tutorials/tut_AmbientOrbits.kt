package io.peekandpoke.klang.pages.docs.tutorials

val ambientOrbitsTutorial = Tutorial(
    slug = "ambient-orbits",
    title = "Ambient Orbits",
    description = "Build a layered ambient soundscape using orbits for spatial separation and frequency carving with filters.",
    difficulty = TutorialDifficulty.Advanced,
    scope = TutorialScope.DeepDive,
    tags = listOf("ambient", "orbits", "mixing", "frequency"),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "Ambient music is about layers breathing together in space. In this tutorial you will build a multi-layer soundscape where each voice lives in its own orbit with distinct frequency character. You will learn to carve out spectral space with filters and use orbits to keep effects separate between layers.",
        ),
        TutorialSection(
            heading = "A Breathing Pad with legato()",
            text = "Ambient sounds need to overlap and sustain. The legato() function controls how long each note rings relative to the cycle — values above 1 mean notes overlap, creating smooth, continuous pads. Combined with a slow adsr, this creates a drone that breathes.",
            code = """n("<0 3 5 7>").scale("C3:minor")
  .sound("supersaw").lpf(400)
  // Feel: legato > 1 lets the notes melt into each other, like butter on a warm pan
  .legato(2).adsr("0.5:0.5:0.8:1.0")
  .room(0.3).rsize(8.0).gain(0.2)""",
        ),
        TutorialSection(
            heading = "Carve Frequency Space",
            text = "When multiple layers play at once they can clash. The hpf() function (high-pass filter) removes low frequencies, and bandf() focuses on a specific frequency band. By giving each layer its own frequency range, they coexist without muddiness. This pad only keeps the high shimmer.",
            code = """n("<7 5 3 0>").scale("C4:minor")
  .sound("sine")
  // Look: hpf skims the cream off the bottom — only the high shimmer remains
  .hpf(2000).legato(2)
  .adsr("0.8:0.3:0.6:1.5")
  .room(0.4).rsize(10.0).gain(0.25)""",
        ),
        TutorialSection(
            heading = "Separate with orbit()",
            text = "The orbit() function sends a voice to its own effect bus. Without it, all voices share the same reverb and delay. With orbit(), each layer gets independent effects — the sub drone can be dry while the shimmer is drenched in reverb. Different orbit numbers mean different effect spaces.",
            code = """stack(
  n("<0 0 0 0>").scale("C2:minor")
    .sound("sine").lpf(200).legato(2)
    .adsr("0.3:0.5:0.9:0.5")
    // Try: orbit(0) seats this layer at its own table — private effect bus
    .orbit(0).gain(0.4),
  n("<0 3 5 7>").scale("C3:minor")
    .sound("supersaw").lpf(400).legato(2)
    .adsr("0.5:0.5:0.8:1.0")
    .room(0.3).rsize(8.0)
    // Voila: orbit(1) keeps the reverb sauce separate from the dry sub
    .orbit(1).gain(0.2)
)""",
        ),
        TutorialSection(
            heading = "Add a Sparse Melody",
            text = "Ambient music often has a delicate melodic line floating above the pads. Use silence in the pattern with ~ to create space between notes. A long release and heavy reverb let each note ring out and decay naturally.",
            code = """n("0 ~ ~ 4 ~ ~ 7 ~").scale("C4:minor")
  .sound("sine").legato(1.5)
  .adsr("0.1:0.3:0.5:1.5")
  .delay(1).delaytime(0.5).delayfeedback(0.3)
  .room(0.3).rsize(6.0).gain(0.25)""",
        ),
        TutorialSection(
            heading = "Texture with High-Pass Noise",
            text = "A subtle layer of filtered noise adds organic texture — like wind or tape hiss. Brown noise through a high-pass filter creates a gentle wash that fills the background without competing with the musical layers.",
            code = """sound("brown").hpf(3000).gain(0.08).orbit(3)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is the complete ambient piece. Four layers, each in its own orbit with its own frequency range: a sub bass drone, a mid-range pad with phaser, a sparse delayed melody, and high-frequency noise texture. Each voice has space to breathe — this is ambient mixing.",
            code = """stack(
  n("<0 0 0 0>").scale("C2:minor")
    .sound("sine").lpf(200).legato(2)
    .adsr("0.3:0.5:0.9:0.5")
    .orbit(0).gain(0.35),
  n("<0 3 5 7>").scale("C3:minor")
    .sound("supersaw").lpf(400).legato(2)
    .adsr("0.5:0.5:0.8:1.0")
    .phaser(1).room(0.3).rsize(8.0)
    .orbit(1).gain(0.2),
  n("0 ~ ~ 4 ~ ~ 7 ~").scale("C4:minor")
    .sound("sine").legato(1.5)
    .adsr("0.1:0.3:0.5:1.5")
    .delay(1).delaytime(0.5).delayfeedback(0.3)
    .room(0.2).rsize(6.0)
    .orbit(2).gain(0.2),
  sound("brown").hpf(3000).gain(0.06).orbit(3)
)""",
        ),
    ),
)
