/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.peekandpoke.klang.viz.liveact.sound.Transport
import io.peekandpoke.klang.viz.liveact.sound.TransportControls
import kotlinx.browser.document
import org.w3c.dom.HTMLAudioElement
import kotlin.math.max

/**
 * An audio file as a [Transport]: the browser plays the file at [url], and the file's time is the music's. For a
 * recording, whose events were written from the very render the file holds. Each one plays the file for itself:
 * [release] stops it and lets the file go.
 *
 * The element is made as `new Audio(url)` makes it: the whole file may be loaded ahead.
 */
class AudioFileTransport(url: String) : Transport, TransportControls {
    private val audio: HTMLAudioElement = document.createElement("audio").unsafeCast<HTMLAudioElement>().also {
        it.preload = "auto"
        it.src = url
    }

    override val time: Double get() = audio.currentTime

    override val paused: Boolean get() = audio.paused

    /** The file is played for this listener alone: it is the listener's to move. */
    override val controls: TransportControls get() = this

    override fun play() {
        audio.play()
    }

    override fun pause() {
        audio.pause()
    }

    override fun skip(seconds: Double) {
        // never before the start; ahead, the browser itself stops at the file's end
        audio.currentTime = max(0.0, audio.currentTime + seconds)
    }

    override fun release() {
        // stopped and let go: without its source the browser stops fetching it too
        audio.pause()
        audio.removeAttribute("src")
        audio.load()
    }
}
