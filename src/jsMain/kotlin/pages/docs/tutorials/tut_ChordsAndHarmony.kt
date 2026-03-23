package io.peekandpoke.klang.pages.docs.tutorials

val chordsAndHarmonyTutorial = Tutorial(
    slug = "chords-and-harmony",
    title = "Chords & Harmony",
    description = "Build rich chord progressions and layer harmonies using superimpose and stereo panning.",
    difficulty = TutorialDifficulty.Advanced,
    scope = TutorialScope.Standard,
    tags = listOf("chords", "harmony", "panning", "composition"),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "Single notes are expressive but thin. Chords fill the space between melody and rhythm, giving your music emotional depth. In this tutorial you will build chord progressions, layer harmonies with superimpose(), and spread them across the stereo field.",
        ),
        TutorialSection(
            heading = "Your First Chord",
            text = "The chord() function turns a single note into a full chord. Instead of hearing one pitch, you hear three or more notes stacked together. Try changing \"minor\" to \"major\" or \"sus4\" to hear the mood shift.",
            code = """note("<c3 f3 g3 c3>").chord("minor").sound("supersaw").lpf(600).adsr("0.1:0.3:0.7:0.5").gain(0.3)""",
        ),
        TutorialSection(
            heading = "Transpose for Movement",
            text = "A chord progression needs movement between different pitch levels. The transpose() function shifts notes up or down by semitones. Here each chord in the slow sequence sits at a different pitch, creating a classic progression feel.",
            code = """n("<0 3 5 3>").scale("C3:minor").chord("minor").sound("supersaw").lpf(600).adsr("0.1:0.3:0.7:0.5").gain(0.3)""",
        ),
        TutorialSection(
            heading = "Layer with superimpose()",
            text = "The superimpose() function copies your pattern and applies a transformation to the copy, playing both at once. Here we layer the chord progression with a version transposed up an octave. Two layers, one line of code.",
            code = """n("<0 3 5 3>").scale("C3:minor").chord("minor").sound("supersaw").lpf(600).adsr("0.1:0.3:0.7:0.5").gain(0.25).superimpose(transpose("12"))""",
        ),
        TutorialSection(
            heading = "Spread Across Stereo",
            text = "Wide chords sound massive. The pan() function places sound in the stereo field — 0 is center, negative is left, positive is right. Combined with superimpose, you can place the original chords in one ear and the transposed copy in the other.",
            code = """n("<0 3 5 3>").scale("C3:minor").chord("minor").sound("supersaw").lpf(600).adsr("0.1:0.3:0.7:0.5").gain(0.25).superimpose(transpose("12").pan(0.7)).pan(-0.7)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is a full composition: wide stereo chords with an octave layer, a simple bass line anchoring the root notes, and a drum groove underneath. This is what harmonic live coding sounds like.",
            code = """stack(
  n("<0 3 5 3>").scale("C3:minor").chord("minor").sound("supersaw").lpf(500).adsr("0.1:0.3:0.7:0.5").gain(0.2).superimpose(transpose("12").pan(0.7)).pan(-0.7).room(0.15).rsize(4.0),
  n("<0 3 5 3>").scale("C2:minor").sound("saw").lpf(300).adsr("0.01:0.2:0.8:0.1").gain(0.4),
  sound("bd sd bd sd"),
  sound("hh hh oh hh").gain(0.5)
)""",
        ),
    ),
)
