/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.utils

import io.peekandpoke.klang.AppVersion
import io.peekandpoke.kraft.utils.launch
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.Unsubscribe
import kotlinx.browser.window
import kotlinx.coroutines.await

/**
 * Global, stream-published build metadata ([AppVersion]).
 *
 * Loads the data *independent of its origin*:
 *  1. the webpack-injected `window.__APP_VERSION__` global (synchronous, no request), else
 *  2. a runtime fetch of `/version.json`.
 *
 * Both paths end in the same [source] push, so subscribers don't care which one fired.
 * Mirrors the [io.peekandpoke.klang.utils.FullscreenController] stream pattern: subscribe via
 * `subscribingTo(version)` in a component and it redraws once the data lands.
 */
class VersionController : Stream<AppVersion> {

    // Starts as the all-"n/a" default; consumers show nothing until isAvailable flips true.
    private val source = StreamSource(AppVersion())

    init {
        val injected = fromDynamic(window.asDynamic().__APP_VERSION__)

        if (injected != null) {
            source(injected)
        } else {
            // Fallback: fetch the same file that is also served at /version.json
            launch {
                runCatching {
                    val res = window.fetch("/version.json").await()
                    val text = res.text().await()
                    fromDynamic(JSON.parse<Any?>(text))
                }.getOrNull()?.let { source(it) }
            }
        }
    }

    override fun invoke(): AppVersion = source()

    override fun subscribeToStream(sub: (AppVersion) -> Unit): Unsubscribe = source.subscribeToStream(sub)

    /** Builds an [AppVersion] from a parsed JS object, or null if it carries no real revision. */
    private fun fromDynamic(d: dynamic): AppVersion? {
        if (d == null || d == undefined) return null

        // Treat an object without a real gitRev as "not loaded" (e.g. an empty injected {})
        val rev = d.gitRev as? String
        if (rev == null || rev == AppVersion.N_A) return null

        return AppVersion(
            project = d.project as? String ?: AppVersion.N_A,
            version = d.version as? String ?: AppVersion.N_A,
            gitBranch = d.gitBranch as? String ?: AppVersion.N_A,
            gitRev = rev,
            gitDesc = d.gitDesc as? String ?: AppVersion.N_A,
            date = d.date as? String,
        )
    }
}
