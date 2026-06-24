/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import kotlinx.coroutines.Deferred

/**
 * Sprudel pattern compiler.
 */
interface SprudelCompiler {
    fun compile(pattern: String): Deferred<SprudelPattern>
}
