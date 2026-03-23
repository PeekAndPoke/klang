package io.peekandpoke.klang.pages.docs.tutorials

val yourFirstMelodyTutorial = Tutorial(
    slug = "your-first-melody",
    title = "Your First Melody",
    description = "Play your first melody with synthesizers and discover how scales make everything sound good.",
    difficulty = TutorialDifficulty.Beginner,
    scope = TutorialScope.Standard,
    tags = listOf("melody", "synth", "scale", "notes"),
    sections = listOf(
        TutorialSection(
            heading = "From Beats to Notes",
            text = "In the first tutorial you made a drum beat with sound(). Now it is time to add melody. The note() function plays pitched sounds — real musical notes. Hit play to hear your first melody.",
            code = """note("c3 e3 g3 c4").sound("sine")""",
        ),
        TutorialSection(
            heading = "Choose Your Sound",
            text = "That sine wave is pure and simple. Let's try something richer. Replace \"sine\" with different waveforms to hear how they change the character. Try \"saw\" for a buzzy lead, \"square\" for a retro vibe, or \"supersaw\" for a big wall of sound.",
            code = """note("c3 e3 g3 c4").sound("saw").gain(0.4)""",
        ),
        TutorialSection(
            heading = "Play by Numbers with scale()",
            text = "Remembering note names is hard. With scale(), you can use simple numbers instead — 0 is the root, 1 is the next note in the scale, 2 is the one after that. Every number is guaranteed to sound good together. Try changing \"major\" to \"minor\" or \"pentatonic\".",
            code = """n("0 2 4 6 7 6 4 2").scale("C4:major").sound("saw").gain(0.4)""",
        ),
        TutorialSection(
            heading = "Shape Your Sound",
            text = "Raw waveforms can sound harsh. The lpf() function is a low-pass filter — it softens the sound by cutting high frequencies. Lower numbers sound warmer and darker. Try values between 200 and 2000.",
            code = """n("0 2 4 6 7 6 4 2").scale("C4:major").sound("saw").lpf(800).gain(0.4)""",
        ),
        TutorialSection(
            heading = "Add It to Your Beat",
            text = "Remember stack() from the first tutorial? You already know how to layer sounds. Here is a melody stacked on top of a drum beat — you just made a song.",
            code = """stack(
  n("0 2 4 6 7 6 4 2").scale("C4:major").sound("saw").lpf(800).gain(0.3),
  sound("bd sd bd sd"),
  sound("hh hh oh hh").gain(0.6)
)""",
        ),
    ),
)
