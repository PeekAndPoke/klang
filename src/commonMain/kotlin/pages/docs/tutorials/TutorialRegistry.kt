/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

/**
 * The tutorial registry: named, ordered tracks of lessons. A lesson can appear
 * in any number of tracks. [theKlangPath] is the braided main path — its order
 * IS the curriculum ladder and drives the strictest vocabulary lint; the
 * thematic tracks are curated views that declare what they build on.
 *
 * Curriculum plan: docs/tasks/tutorial-curriculum.md
 */

/** The onramp: the carrier kit, self-contained (lints clean with no buildsOn). */
val firstStepsTrack = TutorialTrackDef(
    slug = "first-steps",
    title = "First Steps",
    description = "From silence to your first beat, groove, and melody — the three lessons everything else builds on.",
    lessons = listOf(
        yourFirstBeatTutorial, // B1
        spaceAndRestsTutorial, // B2
        firstNotesTutorial, // B3
    ),
)

/** The braided main path — the full curriculum ladder in teaching order. */
val theKlangPathTrack = TutorialTrackDef(
    slug = "the-klang-path",
    title = "The Klang Path",
    description = "The main road: sound design and pattern language taught side by side, one idea at a time.",
    lessons = listOf(
        // Stage 1 — Onramp (carrier kit)
        yourFirstBeatTutorial, // B1
        spaceAndRestsTutorial, // B2
        firstNotesTutorial, // B3
        // Stage 2 — Core sound + core notation
        theFourWaveformsTutorial, // A1
        shapeOfANoteTutorial, // A2
        subdivisionTutorial, // B4
        alternationAndRepetitionTutorial, // B5
        filtersTutorial, // A3
        theFilterEnvelopeTutorial, // A4
        layersTutorial, // B6
        chordsInOneStepTutorial, // B7
        // Stage 3 — Where the tracks meet
        signalsMoveTheKnobsTutorial, // A5
        scalesAndMelodiesTutorial, // B8
    ),
)

/** The Sound-track lessons as one curated sequence. */
val soundDesignBasicsTrack = TutorialTrackDef(
    slug = "sound-design-basics",
    title = "Sound Design Basics",
    description = "Waveforms, envelopes, and filters — how a voice gets its character.",
    lessons = listOf(
        theFourWaveformsTutorial, // A1
        shapeOfANoteTutorial, // A2
        filtersTutorial, // A3
        theFilterEnvelopeTutorial, // A4
        signalsMoveTheKnobsTutorial, // A5
    ),
    // A3 leans on <> from B5, so the honest baseline is the whole main path.
    buildsOn = listOf(theKlangPathTrack),
)

/** The Pattern-track lessons as one curated sequence. */
val patternLanguageTrack = TutorialTrackDef(
    slug = "pattern-language",
    title = "The Pattern Language",
    description = "Beats, rests, melodies, and the mini-notation that grows them beyond a single cycle.",
    lessons = listOf(
        yourFirstBeatTutorial, // B1
        spaceAndRestsTutorial, // B2
        firstNotesTutorial, // B3
        subdivisionTutorial, // B4
        alternationAndRepetitionTutorial, // B5
        layersTutorial, // B6
        chordsInOneStepTutorial, // B7
        scalesAndMelodiesTutorial, // B8
    ),
    // B5's bass rides A2's pluck (adsr), so this track assumes the main path too.
    buildsOn = listOf(theKlangPathTrack),
)

/** All tracks shown on the tutorials page, in display order. */
val allTracks: List<TutorialTrackDef> = listOf(
    theKlangPathTrack,
    firstStepsTrack,
    soundDesignBasicsTrack,
    patternLanguageTrack,
)

/**
 * The flat lesson list, derived from the main path. Every lesson must be on
 * [theKlangPathTrack] (lint-enforced) — its order remains the canonical
 * curriculum order for the global vocabulary lint.
 */
val allTutorials: List<Tutorial> = theKlangPathTrack.lessons
