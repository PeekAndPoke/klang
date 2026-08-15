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

enum class TutorialTrack(val label: String) {
    Sound("Sound"),
    Pattern("Pattern"),
    Motor("Motör"),
}

data class TutorialSection(
    val heading: String? = null,
    val text: String = "",
    val code: String? = null,
)

data class Tutorial(
    val slug: String,
    val title: String,
    val description: String,
    val track: TutorialTrack,
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
