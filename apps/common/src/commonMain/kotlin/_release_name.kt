/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.apps.common

/**
 * A release's name, from the fields of the build's `version.json`: `v<version>-<gitRev>`, as the deploy scripts name a
 * release's folder. A build from a tree with uncommitted changes ([gitDesc] ends with `-dirty`) is named
 * `v<version>-<gitRev>-dirty-<date>`, [date]'s digits only (`2026-11-18 17:29:11` gives `20261118-172911`): two dirty
 * builds at one commit may differ, and each must be a release of its own. The apps ask for the band's figures with
 * this name (a long-cached address that changes with every release), and notstrom shows it in its drawer.
 */
fun releaseName(version: String, gitRev: String, gitDesc: String, date: String): String {
    val name = "v$version-$gitRev"

    if (!gitDesc.endsWith("-dirty")) {
        return name
    }

    val parts = date.split(" ").map { part -> part.filter { it.isDigit() } }.filter { it.isNotEmpty() }

    return if (parts.isEmpty()) "$name-dirty" else "$name-dirty-${parts.joinToString("-")}"
}
