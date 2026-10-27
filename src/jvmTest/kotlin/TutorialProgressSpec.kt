/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.pages.docs.tutorials.LessonState
import io.peekandpoke.klang.pages.docs.tutorials.TutorialProgress
import io.peekandpoke.klang.pages.docs.tutorials.TutorialStrand
import io.peekandpoke.klang.pages.docs.tutorials.firstStepsTrack
import io.peekandpoke.klang.pages.docs.tutorials.patternLanguageTrack
import io.peekandpoke.klang.pages.docs.tutorials.soundDesignBasicsTrack
import io.peekandpoke.klang.pages.docs.tutorials.strand
import io.peekandpoke.klang.pages.docs.tutorials.theKlangPathTrack

/** A lesson's lamp (KLANG-00370): done, the one next, open, or later; the same wherever the lesson is shown. */
class TutorialProgressSpec : StringSpec({

    val path = theKlangPathTrack.lessons
    val stages = theKlangPathTrack.stages

    "the path's stages hold its lessons, in its order, each once" {
        stages.flatMap { it.lessons } shouldContainExactly path
        path.distinct() shouldBe path
        stages.size shouldBe 3
    }

    "nothing done: the first lesson is next, its stage open, the stages after it later" {
        val progress = TutorialProgress(done = emptySet())

        progress.next shouldBe path.first()
        progress.stateOf(stages[0].lessons[0]) shouldBe LessonState.Next
        progress.stateOf(stages[0].lessons[1]) shouldBe LessonState.Open
        progress.stateOf(stages[1].lessons[0]) shouldBe LessonState.Later
        progress.stateOf(stages[2].lessons.last()) shouldBe LessonState.Later
    }

    "the first stage done: next is the second stage's first, that stage open, the last one later" {
        val progress = TutorialProgress(done = stages[0].lessons.map { it.slug }.toSet())

        progress.next shouldBe stages[1].lessons.first()
        stages[0].lessons.map { progress.stateOf(it) }.toSet() shouldBe setOf(LessonState.Done)
        progress.stateOf(stages[1].lessons[1]) shouldBe LessonState.Open
        progress.stateOf(stages[2].lessons[0]) shouldBe LessonState.Later
    }

    "a lesson done out of order is done; next stays the path's first not done; only one is ever next" {
        val skipped = stages[2].lessons[0]
        val progress = TutorialProgress(done = setOf(skipped.slug))

        progress.stateOf(skipped) shouldBe LessonState.Done
        progress.next shouldBe path.first()
        path.count { progress.stateOf(it) == LessonState.Next } shouldBe 1
    }

    "everything done: nothing next, nothing later" {
        val progress = TutorialProgress(done = path.map { it.slug }.toSet())

        progress.next shouldBe null
        path.map { progress.stateOf(it) }.toSet() shouldBe setOf(LessonState.Done)
    }

    "a track counts its own lessons done" {
        val progress = TutorialProgress(done = setOf(path[0].slug, path[5].slug))

        progress.doneIn(firstStepsTrack) shouldBe 1
        progress.doneIn(theKlangPathTrack) shouldBe 2
    }

    "each lesson of the path is on one strand: sound or pattern" {
        path.map { it.strand() }.filter { it == null } shouldBe emptyList()
        path.filter { it in soundDesignBasicsTrack.lessons && it in patternLanguageTrack.lessons } shouldBe emptyList()
        stages[0].lessons.first().strand() shouldBe TutorialStrand.Pattern
        stages[1].lessons.first().strand() shouldBe TutorialStrand.Sound
    }
})
