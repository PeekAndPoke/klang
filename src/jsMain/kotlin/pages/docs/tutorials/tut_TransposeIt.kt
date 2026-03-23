package io.peekandpoke.klang.pages.docs.tutorials

val transposeItTutorial = Tutorial(
    slug = "transpose-it",
    title = "Transpose It",
    description = "Shift melodies up or down by semitones with transpose() to create basslines, harmonies, and octave layers.",
    difficulty = TutorialDifficulty.Intermediate,
    scope = TutorialScope.Quick,
    tags = listOf("transpose", "pitch", "octaves"),
    sections = listOf(
        TutorialSection(
            heading = "Shift by Semitones",
            text = "The transpose() function moves every note up or down by a number of semitones. Transpose by 12 to go up one octave, by -12 to go down. Transpose by 7 for a fifth. Try different numbers to hear how the same melody sounds at different pitches.",
            code = """n("0 2 4 7").scale("C4:minor")
  .sound("saw").lpf(800).gain(0.3)
  // Look: transpose lifts the whole melody — like raising a dish to the top shelf
  .transpose("12")""",
        ),
        TutorialSection(
            heading = "Create a Bassline from a Melody",
            text = "Instead of writing a separate bass part, take your melody and transpose it down two octaves. The notes follow the same pattern but sit in the bass range. Stack them together for instant harmony between melody and bass.",
            code = """stack(
  n("0 2 4 7").scale("C4:minor").sound("saw").lpf(800).gain(0.3),
  n("0 2 4 7").scale("C4:minor")
    .sound("sine").lpf(300).gain(0.5)
    // Watch: transpose("-24") sends the melody deep into the bass cellar, 2 octaves down
    .transpose("-24")
)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here the same four-note phrase appears at three different octaves — a high melody, the original in the middle, and a bass underneath. Three layers from one idea. Transpose is how you think in layers without writing separate parts.",
            code = """stack(
  n("0 2 4 7").scale("C3:minor").sound("saw").lpf(600).gain(0.3),
  n("0 2 4 7").scale("C3:minor")
    .sound("saw").lpf(1200).gain(0.2)
    .transpose("12"),
  n("0 2 4 7").scale("C3:minor")
    .sound("sine").lpf(200).gain(0.5)
    .transpose("-12"),
  sound("bd ~ bd ~").orbit(1),
  sound("~ sd ~ sd").orbit(1)
)""",
        ),
    ),
)
