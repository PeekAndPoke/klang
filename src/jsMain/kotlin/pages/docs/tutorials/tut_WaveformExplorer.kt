package io.peekandpoke.klang.pages.docs.tutorials

val waveformExplorerTutorial = Tutorial(
    slug = "waveform-explorer",
    title = "Waveform Explorer",
    description = "Discover the personality of each waveform — sine, saw, square, triangle, and supersaw — and learn when to use them.",
    difficulty = TutorialDifficulty.Beginner,
    scope = TutorialScope.Standard,
    tags = listOf("waveforms", "synthesis", "sound-palette"),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "Every synth sound starts with a waveform — the raw shape of the sound wave. Each shape has a distinct character. Knowing which waveform to reach for is the first step toward designing your own sounds. In this tutorial you will hear and compare all the waveforms available in Klang.",
        ),
        TutorialSection(
            heading = "Sine — Pure and Smooth",
            text = "The sine wave is the simplest sound — a single frequency with no overtones. It sounds pure, clean, and round. Perfect for sub basses, flutes, and gentle pads. Try playing different notes to hear how smooth it stays across the range.",
            code = """note("c3 e3 g3 c4").sound("sine").gain(0.5)""",
        ),
        TutorialSection(
            heading = "Saw — Bright and Buzzy",
            text = "The sawtooth wave is rich in harmonics — it contains every overtone. This makes it bright, buzzy, and full. It is the workhorse of synthesis — great for leads, basses, and pads. Most synth sounds you hear in music start with a saw wave.",
            code = """note("c3 e3 g3 c4").sound("saw").gain(0.4)""",
        ),
        TutorialSection(
            heading = "Square — Hollow and Retro",
            text = "The square wave has a hollow, woody quality — think clarinet or old-school video games. It only contains odd harmonics, which gives it that distinctive hollow character. Great for chiptune sounds and punchy basses.",
            code = """note("c3 e3 g3 c4").sound("square").gain(0.4)""",
        ),
        TutorialSection(
            heading = "Triangle — Soft and Mellow",
            text = "The triangle wave sits between sine and square — softer than a square but with a bit more presence than a sine. It has a gentle, mellow quality that works beautifully for soft leads and background textures.",
            code = """note("c3 e3 g3 c4").sound("tri").gain(0.5)""",
        ),
        TutorialSection(
            heading = "Supersaw — Big and Wide",
            text = "The supersaw is multiple detuned saw waves layered together. The result is massive, wide, and thick — the sound of trance, EDM, and cinematic pads. Use it when you want something that fills the entire stereo field.",
            code = """note("c3 e3 g3 c4").sound("supersaw").gain(0.3)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is a track that uses each waveform for what it does best: sine for the sub bass, saw for the lead melody, square for a retro arpeggio, and supersaw for the big chord pad. Each waveform has a role — choosing the right one is sound design.",
            code = """stack(
  n("<0 3 5 3>").scale("C2:minor").sound("sine").gain(0.5),
  n("0 2 4 7 6 4 2 0").scale("C4:minor").sound("saw").lpf(1000).gain(0.3),
  n("0 4 7 4").scale("C4:minor").sound("square").fast(2).lpf(1200).gain(0.2),
  chord("<Am C F Am>").voicing().sound("supersaw").lpf(400).adsr("0.2:0.3:0.7:0.5").gain(0.15),
  sound("bd sd bd sd"),
  sound("hh hh oh hh").gain(0.5)
)""",
        ),
    ),
)
