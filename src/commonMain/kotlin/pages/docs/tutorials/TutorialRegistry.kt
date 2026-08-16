/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

/**
 * The tutorial learning path. The list order IS the curriculum order: it drives the
 * Prev/Next navigation, and the vocabulary lint walks it front to back.
 *
 * Curriculum plan: docs/tasks/tutorial-curriculum.md
 */
val allTutorials: List<Tutorial> = listOf(
    // Stage 1 — Onramp (carrier kit)
    yourFirstBeatTutorial, // B1
    spaceAndRestsTutorial, // B2
    firstNotesTutorial, // B3
    // Stage 2 — Core sound + core notation
    theFourWaveformsTutorial, // A1
    shapeOfANoteTutorial, // A2
    subdivisionTutorial, // B4
    alternationAndRepetitionTutorial, // B5
)
