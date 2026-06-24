/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.peekandpoke.klang.AppVersion.Companion.N_A


/**
 * Build metadata for the running app: project name, version, and git info.
 *
 * Mirrors the `version.json` produced by the gradle `versionFile` task (field names match 1:1).
 * Defaults are [N_A] so an un-loaded / non-git build is still a valid, displayable value.
 */
data class AppVersion(
    val project: String = N_A,
    val version: String = N_A,
    val gitBranch: String = N_A,
    val gitRev: String = N_A,
    val gitDesc: String = N_A,
    val date: String? = null,
) {
    companion object {
        const val N_A = "n/a"
    }

    /** True once real metadata has been loaded (i.e. not the all-[N_A] default). */
    val isAvailable: Boolean get() = gitRev != N_A

    /** Short human label, e.g. "v0.94.2-21-gd5fb5b29" or "0.1.0" — falls back to "dev". */
    fun describe(): String = listOf(gitDesc, version)
        .firstOrNull { it != N_A && it.isNotBlank() }
        ?: "dev"
}
