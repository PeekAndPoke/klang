package io.peekandpoke.klang.pages.docs.tutorials

val chordsAndHarmonyTutorial = Tutorial(
    slug = "chords-and-harmony",
    title = "Chords & Harmony",
    description = "Build rich chord progressions and layer harmonies using chord names, voicing, and stereo panning.",
    difficulty = TutorialDifficulty.Advanced,
    scope = TutorialScope.Standard,
    tags = listOf(TutorialTag.Chords, TutorialTag.Mixing),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "Single notes are expressive but thin. Chords fill the space between melody and rhythm, giving your music emotional depth. In this tutorial you will build chord progressions using chord names, spread them across the stereo field, and layer harmonies with superimpose().",
        ),
        TutorialSection(
            heading = "Your First Chord Progression",
            text = "The chord() function takes chord names like Am, C, D, F. But chord() alone just defines the harmony — you need voicing() to turn it into actual notes. The voicing function picks the right pitches and spreads them across the keyboard. Try changing the chord names.",
            code = """chord("<Am C F G>")
  // Voila: voicing turns the names into real notes — from recipe to plate
  .voicing().sound("supersaw").lpf(600)
  .adsr("0.1:0.3:0.7:0.5").gain(0.3)""",
        ),
        TutorialSection(
            heading = "Layer with superimpose()",
            text = "The superimpose() function copies your pattern and applies a transformation to the copy, playing both at once. Here we layer the chord progression with a version transposed up an octave. Two layers, one line of code.",
            code = """chord("<Am C F G>").voicing()
  .sound("supersaw").lpf(600)
  .adsr("0.1:0.3:0.7:0.5").gain(0.25)
  // Watch: superimpose layers a transposed copy on top, like stacking flavors
  .superimpose(transpose("12"))""",
        ),
        TutorialSection(
            heading = "Spread Across Stereo",
            text = "Wide chords sound massive. The pan() function places sound in the stereo field — 0.0 is left, 0.5 is center, 1.0 is right. Combined with superimpose, you can place the original chords in one ear and the transposed copy in the other.",
            code = """chord("<Am C F G>").voicing()
  .sound("supersaw").lpf(600)
  .adsr("0.1:0.3:0.7:0.5").gain(0.25)
  .pan(0.15)
  // Now fold in some stereo width — superimpose + pan spreads the layers wide
  .superimpose(transpose("12").pan(0.85))""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is a full composition: wide stereo chords with an octave layer, a simple bass line anchoring the root notes, and a drum groove underneath. Each layer with different effects uses its own cylinder to keep the reverb separate from the dry drums.",
            code = """stack(
  chord("<Am C F G>").voicing()
    .sound("supersaw").lpf(500)
    .adsr("0.1:0.3:0.7:0.5").gain(0.2)
    .superimpose(transpose("12").pan(0.85))
    .pan(0.15).room(0.15).rsize(4.0)
    .orbit(0),
  n("<0 3 5 7>").scale("A2:minor")
    .sound("saw").lpf(300)
    .adsr("0.01:0.2:0.8:0.1")
    .gain(0.4).orbit(1),
  sound("bd sd bd sd").orbit(2),
  sound("hh hh oh hh").gain(0.5).orbit(2)
)""",
        ),
    ),
)
