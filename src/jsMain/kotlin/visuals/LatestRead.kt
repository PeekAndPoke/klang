/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.visuals

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Reads something that takes a while for the thing that is wanted now, and tells of a read only where it is still
 * the one wanted when it ends.
 *
 * [want] says what is wanted now (null: nothing). Asked for the same thing again, nothing happens: a read that
 * runs goes on. Asked for another, the read that runs is given up, [changed] is told at once (with what was wanted
 * before), and a new [read] starts. [done] is told once the read of what is still wanted has ended, with what it
 * gave, or with null where it failed ([failed] is told why).
 *
 * A read that is given up tells nothing, ever: not when it is given up, and not later.
 */
internal class LatestRead<K : Any, V : Any>(
    private val scope: CoroutineScope,
    private val read: suspend (K) -> V?,
    private val changed: (before: K?, now: K?) -> Unit,
    private val done: (key: K, value: V?) -> Unit,
    private val failed: (key: K, cause: Throwable) -> Unit = { _, _ -> },
) {
    private var wanted: K? = null
    private var job: Job? = null

    fun want(key: K?) {
        val before = wanted

        if (key == before) {
            return
        }

        wanted = key
        job?.cancel()
        job = null
        changed(before, key)

        if (key == null) {
            return
        }

        job = scope.launch {
            val value = try {
                read(key)
            } catch (e: CancellationException) {
                // given up: another is wanted, or nothing, or the scope is gone
                throw e
            } catch (e: Throwable) {
                failed(key, e)
                null
            }

            // only what is still wanted is told of
            if (wanted == key) {
                done(key, value)
            }
        }
    }
}
