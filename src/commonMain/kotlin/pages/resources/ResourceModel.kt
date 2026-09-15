/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.resources

/**
 * Tags on the resources shelf.
 *
 * Its own vocabulary, deliberately not `TutorialTag`: the shelf collects general audio and
 * music production craft, which is cut along different lines than the Klang curriculum.
 * Only tags that a resource actually carries belong here, so no filter button can match nothing.
 */
enum class ResourceTag(val label: String) {
    Mixing("Mixing"),
    Frequencies("Frequencies"),
    EarTraining("Ear training"),
}

/**
 * Who made a resource.
 *
 * [External] is other people's work that we only point at: it always names its author and
 * offers the way out to the place it lives. [Klang] is our own material (none yet).
 * The distinction exists so the shelf can never blur the two.
 */
enum class ResourceOrigin(val label: String) {
    Klang("Klang"),
    External("External"),
}

/**
 * Where a resource lives and what the card can do with it.
 *
 * A source knows how to be visited and pictured, a [Video] source also how to be embedded.
 * A new platform is a new implementation here, never new fields on [Resource].
 */
sealed interface ResourceSource {

    /** Name of the place the resource lives, shown on the way out. */
    val platform: String

    /** The page a viewer lands on when they choose to leave Klang. */
    val url: String

    /** Still image for the card, or null when the card shows a placeholder instead. */
    val imageUrl: String?

    /** A source that can be played in place. */
    sealed interface Video : ResourceSource {

        /** A video always has a still. */
        override val imageUrl: String

        /**
         * Player URL for the in page embed. [autoplay] is only ever true directly after a click,
         * so the browser counts it as a user gesture.
         */
        fun embedUrl(autoplay: Boolean): String
    }

    data class YouTube(val videoId: String) : Video {

        override val platform: String get() = "YouTube"

        override val url: String get() = "https://www.youtube.com/watch?v=$videoId"

        /** hqdefault always exists. It is 4:3 with letterbox bars, which the card crops away. */
        override val imageUrl: String get() = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        /** The nocookie host: no YouTube tracking cookie is set until the viewer hits play. */
        override fun embedUrl(autoplay: Boolean): String =
            "https://www.youtube-nocookie.com/embed/$videoId?rel=0&autoplay=${if (autoplay) 1 else 0}"
    }

    /**
     * A page somewhere on the web: a tool, an article, a reference. Opens in a new tab.
     *
     * [platform] is the host of the [url] with a leading "www." dropped, so the way out
     * always says where it goes and never has to be typed twice.
     */
    data class Website(override val url: String, override val imageUrl: String? = null) : ResourceSource {

        override val platform: String
            get() = url.substringAfter("://").substringBefore("/").removePrefix("www.")
    }
}

/**
 * One resource on the shelf.
 *
 * [author] is not optional: every entry credits the people who made it, ours as much as
 * anyone else's.
 */
data class Resource(
    val id: String,
    val title: String,
    val description: String,
    val author: String,
    val source: ResourceSource,
    val tags: List<ResourceTag>,
    val origin: ResourceOrigin = ResourceOrigin.External,
    val authorUrl: String? = null,
)
