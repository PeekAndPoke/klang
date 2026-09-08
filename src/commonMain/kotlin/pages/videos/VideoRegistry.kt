/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.videos

/**
 * The video shelf: useful videos found out in the world, in display order (newest find on top).
 *
 * Everything here is [VideoOrigin.External] until Klang makes videos of its own.
 */
val allVideos: List<Video> = listOf(
    Video(
        id = "four-frequencies-that-ruin-mixes",
        title = "The 4 Frequencies That Ruin Mixes And How To Hear Them",
        description = "The frequency bands that most often muddy a mix, what each one sounds like, " +
                "and how to train your ear to catch them.",
        author = "Sara Carter - Simply Mixing",
        authorUrl = "https://www.youtube.com/@SaraCarterSimplyMixing",
        source = VideoSource.YouTube("Ful_YXaxFCw"),
        tags = listOf(VideoTag.Mixing, VideoTag.Frequencies),
    ),
)
