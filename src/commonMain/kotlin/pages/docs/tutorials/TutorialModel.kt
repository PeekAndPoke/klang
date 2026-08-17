/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

enum class TutorialDifficulty(val label: String) {
    Beginner("Beginner"),
    Intermediate("Intermediate"),
    Advanced("Advanced"),
    Pro("Pro"),
}

enum class TutorialScope(val label: String) {
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
    const val subdivision = "Subdivision"
    const val alternationAndRepetition = "Alternation and Repetition"
    const val filters = "Filters"
    const val theFilterEnvelope = "The Filter Envelope"
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
)

data class TutorialSection(
    val heading: String? = null,
    val text: String = "",
    val code: String? = null,
)

data class Tutorial(
    val slug: String,
    val title: String,
    val description: String,
    val difficulty: TutorialDifficulty,
    val scope: TutorialScope,
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
