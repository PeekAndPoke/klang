package io.peekandpoke.klang.pages.docs.tutorials

val yourFirstMelodyTutorial = Tutorial(
    slug = "your-first-melody",
    title = "Your First Melody",
    description = "Play your first melody with synthesizers and discover how scales make everything sound good.",
    difficulty = TutorialDifficulty.Beginner,
    scope = TutorialScope.Standard,
    tags = listOf(TutorialTag.Melody, TutorialTag.Synthesis),
    sections = listOf(
        TutorialSection(
            heading = "From Beats to Notes",
            text = "In the first tutorial you made a drum beat with sound(). Now it is time to add melody. The note() function plays pitched sounds — real musical notes. Hit play to hear your first melody.",
            code = """// Here it is — your raw material
note("c3 e3 g3 c4").sound("sine")""",
        ),
        TutorialSection(
            heading = "Choose Your Sound",
            text = "That sine wave is pure and simple. Let's try something richer. Replace \"sine\" with different waveforms to hear how they change the character. Try \"saw\" for a buzzy lead, \"square\" for a retro vibe, or \"supersaw\" for a big wall of sound.",
            code = """// Pick your chisel — each waveform carves a different shape
note("c3 e3 g3 c4").sound("saw").gain(0.4)""",
        ),
        TutorialSection(
            heading = "Play by Numbers",
            text = "Remembering note names is hard. With n() and scale(), you can use simple numbers instead — 0 is the root, 1 is the next note in the scale, 2 is the one after that. Every number is guaranteed to sound good together. Try changing \"major\" to \"minor\" or \"pentatonic\".",
            code = """// Watch: numbers go in, music comes out
n("0 2 4 5 7 5 4 2").scale("C4:major")
  .sound("saw").gain(0.4)""",
        ),
        TutorialSection(
            heading = "Shape Your Sound",
            text = "Raw waveforms can sound harsh. The lpf() function is a low-pass filter — it softens the sound by cutting high frequencies. Lower numbers sound warmer and darker. Try values between 200 and 2000.",
            code = """// Sand the rough edges — lpf smooths out the harshness
n("0 2 4 5 7 5 4 2").scale("C4:major")
  .sound("saw").lpf(800).gain(0.4)""",
        ),
        TutorialSection(
            heading = "Add a Beat",
            text = "Remember stack() from the first tutorial? It layers sounds on top of each other. Let's put a drum beat under our melody.",
            code = """// Stack the layers — melody on top, rhythm underneath
stack(
  n("0 2 4 5 7 5 4 2").scale("C4:major")
    .sound("saw").lpf(800).gain(0.4),
  sound("bd sd bd sd").gain(0.7),
  sound("hh hh hh oh").gain(0.4)
)""",
        ),
        TutorialSection(
            heading = "Add a Bass Line",
            text = "A bass line gives the music depth and warmth. It works just like the melody — numbers and a scale — but in a much lower octave. Notice how C2 sounds deep and supportive compared to C4.",
            code = """// Feel the foundation — the bass holds everything up
stack(
  n("0 2 4 5 7 5 4 2").scale("C4:major")
    .sound("saw").lpf(800).gain(0.4),
  sound("bd sd bd sd").gain(0.7),
  sound("hh hh hh oh").gain(0.4),
  n("0 0 4 4 5 5 0 0").scale("C2:major")
    .sound("sine").lpf(500).gain(0.5)
)""",
        ),
        TutorialSection(
            heading = "Putting It Together",
            text = "Now for the grand finale. This version uses everything you learned — melody, bass, drums, scale numbers, and lpf — arranged into a real piece of music with a proper ending. This is what you built. From a single note to a complete song.",
            code = """// The finished sculpture — from raw stone to music
stack(
  sound("<[bd sd bd sd] [bd sd bd sd] [bd sd bd sd] [bd sd bd sd] [bd sd bd sd] [bd sd bd sd] [bd sd bd sd] [bd ~ ~ ~]>")
    .gain(0.7),
  sound("<[hh hh hh oh] [hh hh hh oh] [hh hh hh oh] [hh hh hh oh] [hh hh hh oh] [hh hh hh oh] [hh hh hh oh] [~ ~ ~ ~]>")
    .gain(0.4),
  n("<[0 0 4 4] [5 5 0 0] [3 3 4 4] [0 0 5 5] [0 0 4 4] [5 5 0 0] [3 3 0 0] [0@2 ~ ~]>")
    .scale("C2:major").sound("sine")
    .lpf(500).gain(0.5),
  n("<[0 2 4 5] [7 5 4 2] [0 2 4 7] [9 7 5 4] [0 2 4 5] [7 5 4 2] [0 2 4 0] [0@2 ~ ~]>")
    .scale("C4:major").sound("saw")
    .lpf("<1000 900 800 1100 1000 900 800 600>")
    .gain(0.45)
).fast(2)""",
        ),
    ),
)
