/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.videos

/**
 * Tags on the video shelf.
 *
 * Its own vocabulary, deliberately not `TutorialTag`: the shelf collects general audio and
 * music production craft, which is cut along different lines than the Klang curriculum.
 * Only tags that a video actually carries belong here, so no filter button can match nothing.
 */
enum class VideoTag(val label: String) {
    Mixing("Mixing"),
    Frequencies("Frequencies"),
}

/**
 * Who made a video.
 *
 * [External] is other people's work that we only point at: it always names its author and
 * offers the way out to the platform it lives on. [Klang] is our own material (none yet).
 * The distinction exists so the shelf can never blur the two.
 */
enum class VideoOrigin(val label: String) {
    Klang("Klang"),
    External("External"),
}

/**
 * Where a video lives. A source knows how to be watched, embedded and thumbnailed, so a new
 * platform is a new implementation here, never new fields on [Video].
 */
sealed interface VideoSource {

    /** Name of the platform, shown on the way out. */
    val platform: String

    /** The page a viewer lands on when they choose to leave Klang. */
    val watchUrl: String

    /** Still image for the card. */
    val thumbnailUrl: String

    /**
     * Player URL for the in page embed. [autoplay] is only ever true directly after a click,
     * so the browser counts it as a user gesture.
     */
    fun embedUrl(autoplay: Boolean): String

    data class YouTube(val videoId: String) : VideoSource {

        override val platform: String get() = "YouTube"

        override val watchUrl: String get() = "https://www.youtube.com/watch?v=$videoId"

        /** hqdefault always exists. It is 4:3 with letterbox bars, which the card crops away. */
        override val thumbnailUrl: String get() = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        /** The nocookie host: no YouTube tracking cookie is set until the viewer hits play. */
        override fun embedUrl(autoplay: Boolean): String =
            "https://www.youtube-nocookie.com/embed/$videoId?rel=0&autoplay=${if (autoplay) 1 else 0}"
    }
}

/**
 * One video on the shelf.
 *
 * [author] is not optional: every entry credits the people who made it, ours as much as
 * anyone else's.
 */
data class Video(
    val id: String,
    val title: String,
    val description: String,
    val author: String,
    val source: VideoSource,
    val tags: List<VideoTag>,
    val origin: VideoOrigin = VideoOrigin.External,
    val authorUrl: String? = null,
)
