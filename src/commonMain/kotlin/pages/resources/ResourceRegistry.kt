/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.resources

/**
 * The resources shelf: useful videos, tools and pages found out in the world, in display order
 * (newest find on top).
 *
 * Everything here is [ResourceOrigin.External] until Klang makes material of its own.
 */
val allResources: List<Resource> = listOf(
    Resource(
        id = "jim-lill",
        title = "Jim Lill",
        description = "A Nashville session guitarist who tests where the tone actually comes from: guitar, amp, " +
                "cab, mic, preamp, one variable at a time, with the myths falling as he goes. Rigorous, funny, " +
                "and the best argument for trusting your ears over the spec sheet.",
        author = "Jim Lill",
        source = ResourceSource.Website(
            url = "https://www.youtube.com/@JimLill",
            imageUrl = "/images/resources/jim-lill.svg",
        ),
        tags = listOf(ResourceTag.Tone, ResourceTag.EarTraining),
    ),
    Resource(
        id = "klippel-listening-test",
        title = "Klippel Listening Test",
        description = "A double blind A/B test that finds the lowest distortion level you can still hear. " +
                "Pick a device under test, listen, and watch your threshold move as your ear gets sharper.",
        author = "Klippel GmbH",
        authorUrl = "https://www.klippel.de",
        source = ResourceSource.Website(
            url = "https://www.klippel.de/listeningtest/",
            imageUrl = "/images/resources/klippel-listening-test.svg",
        ),
        tags = listOf(ResourceTag.EarTraining),
    ),
    Resource(
        id = "four-frequencies-that-ruin-mixes",
        title = "The 4 Frequencies That Ruin Mixes And How To Hear Them",
        description = "The frequency bands that most often muddy a mix, what each one sounds like, " +
                "and how to train your ear to catch them.",
        author = "Sara Carter - Simply Mixing",
        authorUrl = "https://www.youtube.com/@SaraCarterSimplyMixing",
        source = ResourceSource.YouTube("Ful_YXaxFCw"),
        tags = listOf(ResourceTag.Mixing, ResourceTag.Frequencies, ResourceTag.EarTraining),
    ),
)
