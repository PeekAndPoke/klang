/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.state

import kotlinx.serialization.Serializable

/** The code tools' settings, as they are kept (KLANG-00422): one document. */
@Serializable
data class ToolSettings(
    /** Whether a tool writes each change into the code at once; off, it waits for its Update button. */
    val autoUpdate: Boolean = true,
)
