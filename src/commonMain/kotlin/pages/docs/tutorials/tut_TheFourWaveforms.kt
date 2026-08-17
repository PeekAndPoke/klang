/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

/**
 * Curriculum slot A1 — see docs/tasks/tutorial-curriculum.md
 *
 * Teaches no new functions or notation ([teaches] is empty by design): its subject is
 * the voice NAMES passed to sound(), which the vocabulary lint treats as string
 * arguments, plus "voice" as the standing term for oscillator timbre.
 *
 * The per-voice gains are rough RMS trims matched to SINE as the single reference:
 * square 0.35 (its wave carries the most raw energy), triangle 0.55 (its exact sine
 * match ≈0.61 would break the 0.6 authoring ceiling — 0.55 is the compromise). The
 * square-vs-saw pair in §3 is the first render-QA measurement item; see the plan's
 * sanctioned parity exceptions. White at 0.3 is a PERCEPTUAL trim, not an RMS one —
 * broadband noise reads far louder than its RMS (−6.2 dB vs sine, deliberately).
 * Do not "fix" any of these numbers from theory — tune by ear.
 */
val theFourWaveformsTutorial = Tutorial(
    slug = "the-four-waveforms",
    title = Tut.theFourWaveforms,
    description = "Meet sine, saw, square, and triangle — the four basic voices, and what to hear in each.",
    difficulty = TutorialDifficulty.Beginner,
    scope = TutorialScope.Quick,
    tags = listOf(TutorialTag.Synthesis),
    teaches = listOf(),
    sections = listOf(
        TutorialSection(
            heading = "The voice",
            text = "The melody below is the one you ended ${Tut.firstNotes} with, sung by sine — the pure " +
                "tone with nothing added. This lesson keeps the melody fixed and changes only who " +
                "sings it. That \"who\" is the voice: not a recording like the ones drum names fetch, but " +
                "a sound Klang builds on the spot from a repeating wave shape — a waveform — at " +
                "whatever pitch note() asks for. The extra colours a voice rings above its pitch are " +
                "its overtones, and they are what your ear reads as character.\n\nOne note for your " +
                "ears before we start: the voices do not all read equally loud, so the gains in this " +
                "lesson are trimmed to sit roughly level. Judge the colour, not the level.\n\n" +
                "Listen for: the reference. Sine is round, smooth, textureless — no overtones at " +
                "all. Memorize it, because everything in the next three sections is sine plus " +
                "something.",
            code = """
                note("a3 c4 d4 ~  e4 d4 c4 ~")  // the melody from ${Tut.firstNotes}, unchanged all lesson
                  .sound("sine")                // the reference voice: a pure tone
                  .gain(0.5)                    // pure tones read loud — keep them around 0.5
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "Saw — the bright one",
            text = "A saw wave carries the full stack of overtones above every note — every extra " +
                "colour there is, ringing over the pitch you asked for. Your ear reads that stack as " +
                "brightness and buzz.\n\nTry it: swap the // between the two lines and press " +
                "Update.\n\nListen for: the same six notes, but now with an edge riding on top — " +
                "like the difference between humming and singing through your teeth. Saw is the " +
                "workhorse for basses, leads, and anything that has to cut through.",
            code = """
                note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw").gain(0.5)     // bright: the full overtone stack
                // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").gain(0.5) // the pure reference — swap to compare
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "Square — the hollow one",
            text = "A square wave also stacks overtones, but only every second one — half the stack " +
                "is missing. Your ear reads that gap as hollowness. (This pair compares against saw, " +
                "not sine: a gap only shows up against a full stack. And the square's gain sits " +
                "lower — its wave carries more raw energy than the others.)\n\nTry it: swap the // " +
                "to compare square against saw.\n\nListen for: both are bright, but the square " +
                "sounds emptied out — hollow and woody, like a clarinet, or the melodies in old " +
                "video games, which were built almost entirely from square waves.",
            code = """
                note("a3 c4 d4 ~  e4 d4 c4 ~").sound("square").gain(0.35) // hollow: every second overtone missing
                // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw").gain(0.5)  // the full stack — swap to compare
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "Triangle — the soft one",
            text = "A triangle wave carries the same every-second stack as the square — but far " +
                "fainter, fading fast as it climbs. That leaves it almost a sine.\n\nTry it: swap " +
                "the // to compare triangle against sine. This is the subtlest pair on the page, so " +
                "let each version loop a few times before you swap.\n\nListen for: a gentle glow of " +
                "edge that sine does not have — much softer than square, far softer than saw. " +
                "Triangle is the voice for melodies that should sit close and warm.",
            code = """
                note("a3 c4 d4 ~  e4 d4 c4 ~").sound("triangle").gain(0.55) // almost pure: a faint trace of overtones
                // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").gain(0.5)   // fully pure — swap to compare
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "Noise — no pitch at all",
            text = "One more voice, to mark the edge of the map: white noise — every frequency at " +
                "once, so there is no pitch left to sing. Hand it the melody and every note comes " +
                "out sounding the same. More than that: with no pitch changing at the seams, " +
                "neighbouring notes fuse — what remains of the melody is two long blocks of noise " +
                "and the two rests.\n\nTry it: swap the // to hear the melody vanish, then bring it " +
                "back.\n\nListen for: how much the pitches were carrying. This is also why noise " +
                "lives in the drum department: drum machines build their hi-hats from exactly this — " +
                "a short burst of shaped noise. The hh you have been playing is a recording of one; " +
                "building your own from raw noise comes in the Motör track.",
            code = """
                note("a3 c4 d4 ~  e4 d4 c4 ~").sound("white").gain(0.3)   // noise reads loud and rough — kept well down
                // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").gain(0.5) // the melody — swap to bring it back
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "Choose the voice",
            text = "All four voices, one per line, and no correct answer: the notes carry the " +
                "melody, the voice carries the mood. Choosing it is the first real sound-design " +
                "decision you make on every line you write.\n\nTry it: move the // around and give " +
                "each voice a few loops with the melody. As always, keep exactly one line live — " +
                "with four lines it is easy to leave two on, and only the lowest one plays.\n\n" +
                "Listen for: what each voice does to the same leap and the same walk — where saw " +
                "makes the melody insist, triangle makes it confide.\n\n(There is a whole shelf of " +
                "further voices — supersaw, pluck and more; they turn up as the course goes on.)",
            code = """
                note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw").gain(0.5)          // saw: the full overtone stack
                // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").gain(0.5)      // sine: no overtones at all
                // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("square").gain(0.35)   // square: every second overtone
                // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("triangle").gain(0.55) // triangle: the same stack, far fainter
            """.trimIndent(),
        ),
    ),
)
