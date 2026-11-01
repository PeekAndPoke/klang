/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.midi.KeyLayout
import io.peekandpoke.klang.spine.storage.MidiSettingsStorage
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource

/**
 * The MIDI settings, a part of the Spine (KLANG-00402): for now the laptop keys' [layout], which only says which letters
 * the drawing prints (D9; what plays goes by the keys' positions). It joins the MIDI rig when the rig moves here (step
 * 4).
 */
object MidiSettings {

    private val layoutSource = StreamSource(MidiSettingsStorage.loadLayout())

    /** The layout chosen by hand; null for "Automatic" (the browser's map where it has one, else learned key by key). */
    val layout: Stream<KeyLayout?> = layoutSource

    fun chooseLayout(layout: KeyLayout?) {
        layoutSource(layout)
        MidiSettingsStorage.saveLayout(layout)
    }
}
