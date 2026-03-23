package io.peekandpoke.klang.pages.docs.tutorials

val cinematicSoundscapesTutorial = Tutorial(
    slug = "cinematic-soundscapes",
    title = "Cinematic Soundscapes",
    description = "Build sweeping, emotional film-score textures using wide pads, tension chords, and dramatic silence.",
    difficulty = TutorialDifficulty.Pro,
    scope = TutorialScope.Standard,
    tags = listOf("cinematic", "genre", "soundtrack", "dramatic"),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "Film scores are about emotion in space. A single sustained chord can carry more weight than a hundred notes if it has the right texture, the right reverb, and the right moment of silence before it. In this tutorial you build a cinematic piece — all atmosphere, no rush.",
        ),
        TutorialSection(
            heading = "The Wide Pad",
            text = "Cinematic pads need width and sustain. Use supersaw with a slow attack, heavy reverb, and jux() to spread the sound across stereo. legato() makes notes overlap so the pad never goes silent between chords. This is the sky your melody will fly through.",
            code = """chord("<Am F C G>").voicing()
  .sound("supersaw").lpf(400)
  // Slow fade in — like a sunrise
  .adsr("0.8:0.5:0.8:1.5")
  .legato(2)
  // Spread wide across the stereo sky
  .jux(slow(2))
  .room(0.35).rsize(10.0)
  .gain(0.15).orbit(0)""",
        ),
        TutorialSection(
            heading = "Tension with Silence",
            text = "The most powerful moment in cinema is silence before impact. Use slowcat() to cycle between full chords and complete silence. The gap creates anticipation — when the sound returns, it hits twice as hard.",
            code = """slowcat(
  chord("<Am>").voicing()
    .sound("supersaw").lpf(350)
    .adsr("0.5:0.3:0.8:1.0")
    .legato(2)
    .room(0.3).rsize(8.0)
    .gain(0.15),
  // The dramatic pause — let it breathe
  silence,
  chord("<F>").voicing()
    .sound("supersaw").lpf(400)
    .adsr("0.5:0.3:0.8:1.0")
    .legato(2)
    .room(0.3).rsize(8.0)
    .gain(0.15),
  silence
).orbit(0)""",
        ),
        TutorialSection(
            heading = "A Lonely Melody",
            text = "Over the wide pad, a single sparse melody tells the story. Use sine for purity, delay for depth, and lots of rests. In cinematic music, the spaces between notes matter as much as the notes themselves. Every note should feel like it means something.",
            code = """n("0 ~ ~ ~ 4 ~ ~ ~ 7 ~ ~ ~ 4 ~ ~ ~")
  .scale("A3:minor")
  .sound("sine")
  .adsr("0.1:0.3:0.5:1.5")
  // Each note trails off into the distance
  .delay(1).delaytime(0.5).delayfeedback(0.35)
  .room(0.2).rsize(6.0)
  .gain(0.25).orbit(1)""",
        ),
        TutorialSection(
            heading = "The Sub Rumble",
            text = "Film scores use deep sub-bass to create a physical sense of weight and unease. A low sine note at the edge of hearing adds gravitas to everything above it. Keep it simple — one note, barely there, always felt.",
            code = """// Feel it in your chest, not your ears
note("a1").sound("sine")
  .lpf(100).gain(0.35).orbit(2)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is the complete cinematic piece. A wide pad breathes across stereo. A sparse melody floats on delay echoes. A sub rumble anchors everything to the earth. Silence creates tension between sections. Close your eyes and you are scoring a film.",
            code = """stack(
  // The sky: wide, breathing pad
  chord("<Am Am F F C C G G>").voicing()
    .sound("supersaw").lpf(350)
    .adsr("0.8:0.5:0.8:1.5")
    .legato(2).jux(slow(2))
    .room(0.35).rsize(10.0)
    .gain(0.12).orbit(0),
  // The story: sparse melody with echo trails
  n("0 ~ ~ ~ 4 ~ ~ ~ 7 ~ ~ ~ 4 ~ ~ ~")
    .scale("A3:minor").sound("sine")
    .adsr("0.1:0.3:0.5:1.5")
    .delay(1).delaytime(0.5)
    .delayfeedback(0.3)
    .gain(0.2).orbit(1),
  // The earth: sub bass you feel
  note("a1").sound("sine")
    .lpf(100).gain(0.3).orbit(2),
  // Atmosphere: a whisper of pink noise
  sound("pink").hpf(5000)
    .gain(0.03).orbit(3)
)""",
        ),
    ),
)
