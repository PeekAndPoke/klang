/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages

import io.peekandpoke.klang.ui.feel.KlangLookPreset
import io.peekandpoke.klang.ui.feel.KlangRole

/**
 * The user's own looks as they were when the look editor first showed them, for one visit of the page (KLANG-00444).
 *
 * The editor keeps every change in the look at once, so the look itself no longer says what it was: Reset goes back
 * to what is remembered here, and is offered only while the look differs from it. A look made during the visit (a
 * duplicate, a pasted one) is remembered as it was made. A built-in look is read only and never remembered.
 */
internal class LookBaselines {

    private val first = mutableMapOf<String, KlangLookPreset>()

    /** Remembers [look] as it is now, unless it was seen before or is a built-in one. */
    fun seen(look: KlangLookPreset) {
        if (look.own && look.id !in first) {
            first[look.id] = look
        }
    }

    /** [look] as it was when first seen; [look] itself where it never was. */
    fun of(look: KlangLookPreset): KlangLookPreset = first[look.id] ?: look

    /** How many of [look]'s roles differ from when it was first seen, its name counting as one. */
    fun changes(look: KlangLookPreset): Int {
        val was = of(look)

        return KlangRole.entries.count { look.values[it] != was.values[it] } + (if (look.name != was.name) 1 else 0)
    }

    fun changed(look: KlangLookPreset): Boolean = changes(look) > 0

    /** The look [id] is gone: nothing is remembered of it. */
    fun forget(id: String) {
        first.remove(id)
    }
}
