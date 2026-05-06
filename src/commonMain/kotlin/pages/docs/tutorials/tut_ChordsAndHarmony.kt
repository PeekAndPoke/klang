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
            text = "Single notes are expressive but thin. Chords fill the space between melody and rhythm, giving your music emotional depth. In this tutorial you will build chord progressions, spread them across the stereo field, add a walking bass, and layer harmonies.",
        ),
        TutorialSection(
            heading = "Your First Chord Progression",
            text = "The chord() function takes chord names like Am, C, F, G. But chord() alone just defines the harmony — you need voicing() to turn it into actual notes that the synth can play. Without voicing(), nothing sounds. Try changing the chord names.",
            code = """// Watch: chord() is the sketch, voicing() brings it to life
chord("<Am C F G>").voicing()
  .sound("supersaw").lpf(800).gain(0.3)""",
        ),
        TutorialSection(
            heading = "Shape the Chords",
            text = "Raw chords can sound blunt. An adsr envelope gives them a gentle fade-in and a smooth release. A highpass filter removes the muddy low end — that is the bass player's territory.",
            code = """// Smooth the edges — adsr gives the chords room to breathe
chord("<Am C F G>").voicing()
  .sound("supersaw").lpf(800).hpf(200)
  .adsr("0.1:0.3:0.7:0.05").gain(0.3)""",
        ),
        TutorialSection(
            heading = "Layer with superimpose()",
            text = "superimpose() copies your pattern and applies a transformation to the copy, playing both at once. Here we layer the chord progression with a version transposed up an octave. Two layers from one line of code.",
            code = """// Carve a second layer — an octave copy stacked on top
chord("<Am C F G>").voicing()
  .sound("supersaw").lpf(800).hpf(200)
  .adsr("0.1:0.3:0.7:0.05").gain(0.25)
  .superimpose(transpose("12"))""",
        ),
        TutorialSection(
            heading = "Spread Across Stereo",
            text = "Wide chords sound massive. pan() places sound in the stereo field — 0.0 is left, 0.5 is center, 1.0 is right. Place the original chords left and the transposed copy right for a wall of sound.",
            code = """// Split the stone wide — original left, octave copy right
chord("<Am C F G>").voicing()
  .sound("supersaw").lpf(800).hpf(200)
  .adsr("0.1:0.3:0.7:0.05").gain(0.25)
  .pan(0.15)
  .superimpose(transpose("12").pan(0.85))""",
        ),
        TutorialSection(
            heading = "Add a Walking Bass",
            text = "A walking bass does not just sit on the root — it walks between chord tones, stepping through the scale to connect one chord to the next. Four notes per chord, leaping and stepping like a jazz upright player. The bass uses scale numbers with n() so every note fits the key.",
            code = """// The bass walks between chords — leaps and steps, like jazz
n("<[0 4 2 1] [2 6 3 4] [5 7 6 5] [4 3 2 -1]>")
  .scale("A1:minor").sound("saw")
  .lpf(300).adsr("0.01:0.2:0.6:0.1")
  .gain(0.4)""",
        ),
        TutorialSection(
            heading = "Drums That Ring",
            text = "By default, drum samples are cut short when the next note starts. If you want cymbals to ring out and kicks to sustain, add adsr(). The sustain keeps the sound alive, the release lets it fade naturally. Compare with and without to hear the difference.",
            code = """// Let the drums breathe — adsr gives them room to ring out
stack(
  sound("bd sd bd sd")
    .adsr("0.01:0.1:0.5:0.5").gain(0.7),
  sound("cr hh hh hh oh hh hh hh")
    .adsr("0.01:0.1:0.5:0.5").gain(0.5)
)""",
        ),
        TutorialSection(
            heading = "Putting It Together",
            text = "Here is the full composition: wide stereo chords with an octave layer, a walking bass that grooves between the roots, and drums that ring. Each voice lives on its own orbit — the chords get reverb, the bass stays focused, the drums stay punchy. The room() on the outer stack ties everything together in a shared space.",
            code = """// The finished sculpture — harmony, bass, and rhythm in balance
stack(
  // Chords — wide stereo with octave layer
  chord("<Am C F G>").voicing()
    .sound("supersaw").lpf(800).hpf(200)
    .adsr("0.1:0.3:0.7:0.05")
    .gain(0.3).pan(0.15)
    .superimpose(transpose("12").pan(0.85))
    .orbit(0),
  // Walking bass — steps and leaps between chord roots
  n("<[0 4 2 1] [2 6 3 4] [5 7 6 5] [4 3 2 -1]>")
    .scale("A1:minor").sound("saw")
    .lpf(300).adsr("0.01:0.2:0.6:0.1")
    .gain(0.4).orbit(1),
  // Drums — adsr lets them ring out
  sound("bd sd bd sd")
    .adsr("0.01:0.1:0.5:0.5")
    .gain(0.7).orbit(2),
  sound("cr hh hh hh oh hh hh hh")
    .adsr("0.01:0.1:0.5:0.5")
    .gain(0.5).orbit(2)
).room(0.15).rsize(4.0)""",
        ),
    ),
)
