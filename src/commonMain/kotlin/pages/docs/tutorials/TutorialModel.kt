/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.constants.MOD_ENV_CURVE

enum class TutorialDifficulty(val label: String) {
    Beginner("Beginner"),
    Intermediate("Intermediate"),
    Advanced("Advanced"),
    Pro("Pro"),
}

enum class TutorialDepth(val label: String) {
    Quick("Quick"),
    Standard("Standard"),
    DeepDive("Deep Dive"),
}

enum class TutorialTag(val label: String) {
    Rhythm("Rhythm"),
    Melody("Melody"),
    Chords("Chords"),
    Synthesis("Synthesis"),
    Effects("Effects"),
    Patterns("Patterns"),
    Mixing("Mixing"),
    Arrangement("Arrangement"),
    LiveCoding("Live Coding"),
    Generative("Generative"),
    Genre("Genre"),
    GettingStarted("Getting Started"),
}

/**
 * Single source of truth for lesson titles. Prose in one lesson refers to another
 * lesson by interpolating these constants — never by reading order ("last lesson",
 * "next lesson" are banned and lint-enforced): readers browse lessons in any order,
 * so every cross-reference must carry the lesson's name. Renaming a lesson here
 * renames every reference to it.
 */
object Tut {
    const val yourFirstBeat = "Your First Beat"
    const val spaceAndRests = "Space and Rests"
    const val firstNotes = "First Notes"
    const val theFourWaveforms = "The Four Waveforms"
    const val shapeOfANote = "The Shape of a Note"
    const val bendOfANote = "The Bend of a Note"
    const val subdivision = "Subdivision"
    const val alternationAndRepetition = "Alternation and Repetition"
    const val filters = "Filters"
    const val theFilterEnvelope = "The Filter Envelope"
    const val layers = "Layers"
    const val chordsInOneStep = "Chords in One Step"
    const val signalsMoveTheKnobs = "Signals Move the Knobs"
    const val scalesAndMelodies = "Scales and Melodies"
    const val theTransformToolkit = "The Transform Toolkit"
    const val gates = "Gates"
    const val thickness = "Thickness"
    const val spaceAndDirt = "Space and Dirt"
}

/**
 * A named, ordered sequence of lessons. A lesson can appear in any number of
 * tracks; the braided main path is itself just a track. [buildsOn] declares the
 * tracks whose vocabulary this one assumes — the per-track curriculum lint
 * allows a lesson to use anything taught by a buildsOn track, by an earlier
 * lesson in this track, or declared in the lesson's own previews.
 */
data class TutorialTrackDef(
    val slug: String,
    val title: String,
    val description: String,
    val lessons: List<Tutorial>,
    val buildsOn: List<TutorialTrackDef> = emptyList(),
    /**
     * The track's stages, in order, holding its [lessons] in order (The Klang Path: the onramp, the core, where the
     * strands meet); none for a track without stages. A lesson in a stage not reached yet is drawn as "later"
     * (KLANG-00370).
     */
    val stages: List<TutorialStage> = emptyList(),
)

/** A stage of a track: its [title] and its [lessons], in teaching order. */
data class TutorialStage(
    val title: String,
    val lessons: List<Tutorial>,
)

/**
 * One content block inside a section. A section is an ordered list of blocks,
 * so text, visuals, and code can interleave freely.
 */
sealed interface Block {
    /**
     * Prose rendered as plain paragraphs (\n\n-separated); callout labels open
     * their own paragraph. Being migrated to [Markdown] — prefer Markdown for
     * new sections; existing Text blocks get converted in a deliberate pass
     * (their prose contains literal `*`, `<>`, `[]` that markdown would mangle
     * unescaped).
     */
    data class Text(val text: String) : Block

    /**
     * Prose rendered as markdown (via MarkdownDisplay / marked). Paragraphs
     * are \n\n-separated as in [Text]; bullets, **bold**, and `inline code`
     * are available. Callout labels ("Try it:", "Listen for:") stay PLAIN at
     * paragraph starts — the curriculum lint anchors on them. Escape or
     * backtick literal mini-notation (`*`, `<>`, `[]`) so marked cannot eat it.
     */
    data class Markdown(val markdown: String) : Block

    /** A runnable (or, for other langs, displayed) code example. */
    data class Code(
        val lang: String = "KlangScript",
        val code: String,
    ) : Block

    /**
     * A parameterized diagram, rendered as SVG in jsMain from the SAME values the neighbouring code block uses, so
     * picture and code cannot drift (lint-enforced). It draws the mental model (flux ruling); an envelope's stages bend
     * as the engine bends them (the maintainer, 2026-10-27: "the real curves", KLANG-00371), its widths stay schematic.
     */
    sealed interface Visual : Block {
        /**
         * An envelope's shape. On the page it follows the examples right after it, each one a player (KLANG-00371):
         * a light per note on the curve, the curve reshaped to what plays. Before a note sounds it shows [value], the
         * same colon string the first of them writes (drift is lint-enforced); without one, what that example writes
         * in `.adsr(...)`. Only where the example writes its envelope (the maintainer, 2026-10-27, on the lessons
         * before and without one: "these do not define adsrs"; lint-enforced).
         */
        data class Adsr(
            val value: String? = null,
            val envelope: Envelope = Envelope.Loudness,
        ) : Visual {

            /**
             * The four numbers drawn before a note sounds, [example] being the code of the first player after it;
             * null when it writes none or they do not parse (the curriculum lint says so).
             */
            fun numbers(example: String?): List<Double>? {
                if (value != null) {
                    return value.split(":").map { it.trim().toDoubleOrNull() ?: return null }.takeIf { it.size == 4 }
                }

                val written = adsrCall.find(codeOnly(example ?: return null)) ?: return null

                return written.groupValues[1].split(",").map { it.trim().toDoubleOrNull() ?: return null }.takeIf { it.size == 4 }
            }

            /**
             * The three moving stages' curves (attack, decay, release) drawn before a note sounds: what [example]
             * writes in `.adsrCurves(...)`, by place or by name, each one not written the envelope's default (the
             * loudness's [AdsrCurve.Default], a filter's [MOD_ENV_CURVE]).
             */
            fun curves(example: String?): List<AdsrCurve> {
                val default = if (envelope == Envelope.Loudness) AdsrCurve.Default else MOD_ENV_CURVE
                val curves = MutableList(3) { default }

                // The cutoff's curves have a door of their own, which no lesson writes yet
                val written = example?.takeIf { envelope == Envelope.Loudness }?.let { adsrCurvesCall.find(codeOnly(it)) }
                    ?: return curves

                written.groupValues[1].split(",").forEachIndexed { index, argument ->
                    val stage = argument.substringBefore("=", missingDelimiterValue = "").trim()
                    val at = if (stage.isEmpty()) index else curveStages.indexOf(stage)
                    val curve = AdsrCurves.curveOf(argument.substringAfter("=").trim().trim('"'))

                    if (at in curves.indices && curve != null) {
                        curves[at] = curve
                    }
                }

                return curves
            }

            /** Which of a note's envelopes it shows: its door in the voice data, and the word along its edge. */
            enum class Envelope(val door: String, val label: String) {
                Loudness(door = "adsr", label = "loudness"),
                Cutoff(door = "lpf", label = "cutoff"),
            }
        }
    }
}

data class TutorialSection(
    val heading: String? = null,
    val blocks: List<Block>,
) {
    /**
     * Each player that an ADSR picture follows, by its block's index, onto the picture's index: the KlangScript
     * examples right after the picture, one after another, up to the first block that is not one (KLANG-00371).
     */
    fun playersAfterPictures(): Map<Int, Int> = buildMap {
        blocks.forEachIndexed { index, block ->
            if (block is Block.Visual.Adsr) {
                var next = index + 1

                while ((blocks.getOrNull(next) as? Block.Code)?.lang == "KlangScript") {
                    put(next, index)
                    next++
                }
            }
        }
    }
}

data class Tutorial(
    val slug: String,
    val title: String,
    val description: String,
    val difficulty: TutorialDifficulty,
    val depth: TutorialDepth,
    val tags: List<TutorialTag>,
    /**
     * Vocabulary this lesson introduces: function names ("sound", "gain"),
     * mini-notation symbols ("~", "[]"), and code-syntax tokens ("//"). The
     * registry order defines what is already taught; the curriculum lint
     * checks every code block against it.
     */
    val teaches: List<String>,
    /**
     * Vocabulary used here but taught in a LATER lesson. Every entry must be
     * explicitly called out as a preview in the section text, with a pointer
     * to the lesson that teaches it.
     */
    val previews: List<String> = emptyList(),
    val sections: List<TutorialSection>,
    val rpm: Double = 30.0,
)

/** `.adsr(a, d, s, r)` in a line of code: its four arguments. */
private val adsrCall = Regex("""\.adsr\(([^)]*)\)""")

/** `.adsrCurves(...)` in a line of code: its arguments. */
private val adsrCurvesCall = Regex("""\.adsrCurves\(([^)]*)\)""")

/** The stages `.adsrCurves(...)` names, in its order. */
private val curveStages = listOf("attack", "decay", "release")

/** [code] without its comments: what plays, its lines joined. */
fun codeOnly(code: String): String = code.lines().joinToString(" ") { it.substringBefore("//") }
