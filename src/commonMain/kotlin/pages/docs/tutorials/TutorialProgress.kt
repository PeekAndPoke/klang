/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

/** Where a lesson stands for the user, as its lamp shows it (KLANG-00370). */
enum class LessonState {
    /** Marked as done. */
    Done,

    /** The one to take now: the main path's first lesson not done. Only one lesson is ever next. */
    Next,

    /** Free to take: not done, in a stage reached. */
    Open,

    /** In a stage of the main path not reached yet. Still open to read: nothing is locked. */
    Later,
}

/** A strand of the main path: what a lesson teaches, sound or the pattern language (KLANG-00370). */
enum class TutorialStrand(val label: String) {
    Sound("Sound"),
    Pattern("Pattern"),
}

/**
 * The user's progress through the lessons: which are [done] (their slugs, as `TutorialStorage` keeps them), and from
 * that each lesson's [LessonState] and a track's count. The main path ([path]) decides the one next lesson and which
 * stages are reached; plain state, no UI, so the overview, the column and the tutorial page agree.
 */
class TutorialProgress(
    private val done: Set<String>,
    private val path: TutorialTrackDef = theKlangPathTrack,
) {
    /** The lesson to take now: the main path's first not done; null once every lesson there is done. */
    val next: Tutorial? = path.lessons.firstOrNull { it.slug !in done }

    /** The stage the next lesson is in, by its place in the path's stages; past the last when all are done. */
    private val reachedStage: Int = next?.let { stageOf(it) } ?: path.stages.size

    fun isDone(tutorial: Tutorial): Boolean = tutorial.slug in done

    fun stateOf(tutorial: Tutorial): LessonState = when {
        tutorial.slug in done -> LessonState.Done
        tutorial == next -> LessonState.Next
        stageOf(tutorial) > reachedStage -> LessonState.Later
        else -> LessonState.Open
    }

    /** How many of the [track]'s lessons are done. */
    fun doneIn(track: TutorialTrackDef): Int = track.lessons.count { it.slug in done }

    /** The path's stage holding the [tutorial], by its place; -1 when no stage holds it (it is never later). */
    private fun stageOf(tutorial: Tutorial): Int = path.stages.indexOfFirst { tutorial in it.lessons }
}

/** The strand a lesson belongs to: its curated track's; null for a lesson on neither. */
fun Tutorial.strand(): TutorialStrand? = when (this) {
    in soundDesignBasicsTrack.lessons -> TutorialStrand.Sound
    in patternLanguageTrack.lessons -> TutorialStrand.Pattern
    else -> null
}
