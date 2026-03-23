package io.peekandpoke.klang.pages.docs.tutorials

val soundDesignPlaygroundTutorial = Tutorial(
    slug = "sound-design-playground",
    title = "Sound Design Playground",
    description = "Sculpt wild textures by chaining distortion, bit-crushing, tremolo, vibrato, and phaser effects.",
    difficulty = TutorialDifficulty.Pro,
    scope = TutorialScope.DeepDive,
    tags = listOf("sound-design", "distortion", "modulation", "texture"),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "You have learned to play notes, shape them with filters and envelopes, and transform patterns over time. Now we go deeper — into sound design itself. This tutorial teaches you to sculpt raw timbres using distortion, bit-crushing, and modulation effects. The order you chain them matters, and discovering that is half the fun.",
        ),
        TutorialSection(
            heading = "Distort and Crush",
            text = "The distort() function adds harmonic saturation — subtle warmth at low values, aggressive clipping at high values. The crush() function reduces bit depth for a lo-fi, retro texture. Try values between 1 (extreme) and 16 (subtle). Listen to how they change the character of a simple saw wave.",
            code = """note("c3").sound("saw").distort(0.5).gain(0.4)""",
        ),
        TutorialSection(
            heading = "Lo-Fi Crunch",
            text = "Combine crush() with a filter to tame the harshness. Low crush values create that classic chiptune sound. The lpf() after crush smooths out the digital edges while keeping the grit.",
            code = """n("0 2 4 7").scale("C3:minor").sound("square").crush(4).lpf(1200).gain(0.4)""",
        ),
        TutorialSection(
            heading = "Tremolo — Pulsing Volume",
            text = "Tremolo rapidly modulates volume, creating a pulsing, rhythmic effect. Higher values mean faster pulsing. It turns a static pad into something that breathes. Try values between 1 and 20.",
            code = """n("<0 3 5 7>").scale("C3:minor").sound("supersaw").lpf(600).tremolo(4).adsr("0.2:0.3:0.7:0.5").gain(0.3)""",
        ),
        TutorialSection(
            heading = "Vibrato — Pitch Wobble",
            text = "While tremolo modulates volume, vibrato modulates pitch — adding a natural wobble like a singer's voice or a guitarist bending a string. Subtle vibrato adds life. Heavy vibrato creates a seasick, detuned feel.",
            code = """note("c3").sound("sine").vibrato(5).gain(0.5)""",
        ),
        TutorialSection(
            heading = "Phaser — Sweeping Texture",
            text = "A phaser creates a sweeping, whooshing sound by splitting the signal and shifting its phase. It adds movement and width to pads and leads without changing the notes. Combined with reverb, it creates lush, evolving textures.",
            code = """n("<0 3 5 7>").scale("C3:minor").sound("supersaw").lpf(800).phaser(3).room(0.2).rsize(5.0).adsr("0.3:0.3:0.7:0.5").gain(0.3)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is a full track using everything you learned. The lead melody has vibrato for expression. The pad uses phaser and tremolo for evolving texture. The bass gets subtle distortion for warmth. The drums are crushed for lo-fi grit. Each layer has its own sonic character — that is sound design.",
            code = """stack(
  n("0 2 4 7 6 4 2 0").scale("C4:minor").sound("saw").vibrato(4).lpf(1000).adsr("0.01:0.1:0.6:0.2").gain(0.3).orbit(0),
  n("<0 3 5 3>").scale("C3:minor").sound("supersaw").lpf(500).phaser(2).tremolo(3).adsr("0.3:0.3:0.7:0.5").room(0.2).rsize(4.0).gain(0.2).orbit(1),
  n("0 ~ 0 ~").scale("C2:minor").sound("saw").distort(0.3).lpf(400).adsr("0.01:0.2:0.8:0.1").gain(0.4).orbit(2),
  sound("bd sd bd sd").crush(8).gain(0.7).orbit(3),
  sound("hh hh oh hh").crush(6).gain(0.4).orbit(3)
)""",
        ),
    ),
)
