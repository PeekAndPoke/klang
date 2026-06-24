/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codetools

import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.Unsubscribe
import io.peekandpoke.ultra.streams.ops.persistInLocalStorage
import kotlinx.serialization.builtins.serializer

object KlangToolAutoUpdate : Stream<Boolean> {
    private val source = StreamSource(true)
        .persistInLocalStorage("klang-tool-auto-update", Boolean.serializer())

    override fun invoke(): Boolean = source()

    override fun subscribeToStream(sub: (Boolean) -> Unit): Unsubscribe = source.subscribeToStream(sub)

    fun toggle() {
        source(!source())
    }
}
