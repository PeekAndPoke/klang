/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import kotlinx.browser.window
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Waits for the next animation frame. */
internal suspend fun nextFrame() = suspendCoroutine { cont -> window.requestAnimationFrame { cont.resume(Unit) } }
