/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.kraft.KraftApp
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.getAttributeRecursive
import io.peekandpoke.ultra.common.TypedKey

/**
 * The Spine: the app's one data layer under the UI (KLANG-00389), as one object (KLANG-00422; the maintainer,
 * 2026-11-10: "there should only be one global object if at all and components will find what they need through
 * `spine.xyz`"). The app builds it once and registers it on the Kraft app ([spine]), as Kraft does for its popups; a
 * component reaches it as `spine`, a spec gives its own.
 *
 * A part gets the parts it needs through its constructor. Building a Spine starts nothing: the app asks for what it
 * wants at its start (the samples' download, the MIDI devices), a spec's Spine stays quiet.
 */
class Spine(
    /**
     * The app's own Console: the log's records and the errors not seen yet. The app hands in the one `index.kt`
     * installed before anything logs; the default (a spec's) is not installed and hears no log.
     */
    val console: ConsoleLog = ConsoleLog(),
) {

    companion object {
        val key = TypedKey<Spine>("spine")
    }

    /** The MIDI playground's settings: the laptop keys' layout and letters, the key bed's offset. */
    val midiSettings = MidiSettings()

    /** What was opened lately: the songs and the lessons the Motor's menu offers. */
    val recents = Recents()

    /** The work that only shows things: when it rests, and the frames it runs on. */
    val work = UiWork()

    /** The engine: its start, its status, the player once it runs, the samples, the output volume. */
    val player = Player()

    /** What plays: the song deck, the previews, the live MIDI rig, and what holds the stage. */
    val playbacks = Playbacks(engine = player)

    /** Klang Amp's deck: the built-in songs played from the visuals. */
    val klangamp = KlangampDeck(playbacks = playbacks)

    /** The Motor's readings, each smoothed once for every gauge. */
    val motor = MotorReadings(work = work, player = player)

    /** The visuals' state: the lamp over the plate and its light, the song open on a page. */
    val visuals = VisualState(work = work)
}

/** Registers the app's [Spine]. */
fun KraftApp.Builder.spine(spine: Spine) = apply {
    setAttribute(Spine.key, spine)
}

/** The app's [Spine]. */
val Component<*>.spine: Spine get() = getAttributeRecursive(Spine.key)
