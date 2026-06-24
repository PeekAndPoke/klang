/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

fun jsObject(): dynamic = js("({})")

fun <T> jsObject(block: T.() -> Unit): T {
    val obj = jsObject() as T
    block(obj)
    return obj
}
