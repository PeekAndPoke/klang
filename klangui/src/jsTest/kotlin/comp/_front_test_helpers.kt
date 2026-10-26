/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.modals.ModalsManager
import io.peekandpoke.kraft.modals.ModalsManager.Companion.modals
import io.peekandpoke.kraft.popups.PopupsManager
import io.peekandpoke.kraft.popups.PopupsManager.Companion.popups
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.html.Tag

/** Hands a test the app's modals and popups, to put something in front of a panel (`nothingInFront`). */
internal class FrontGrip(ctx: NoProps) : PureComponent(ctx) {
    companion object {
        var current: FrontGrip? = null

        val modals: ModalsManager get() = current!!.modals

        val popups: PopupsManager get() = current!!.popups
    }

    init {
        current = this
    }

    override fun VDom.render() {}
}

@Suppress("FunctionName")
internal fun Tag.FrontGrip() = comp { FrontGrip(it) }
