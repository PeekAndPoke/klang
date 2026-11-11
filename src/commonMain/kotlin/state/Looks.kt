/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.state

import kotlinx.serialization.Serializable

/**
 * The look the user wears and the looks equipped, as they are kept (KLANG-00422): one document, here in the shared
 * code so a server can keep the same one.
 */
@Serializable
data class LookChoice(
    /** The id of the look worn, built-in or own; null for one who never chose, who wears the first-visit look. */
    val worn: String? = null,
    /**
     * The equipped slots, each with a look's id or null for an empty one; null as a whole for one who never arranged
     * them, who finds the looks equipped by default, also when those change.
     */
    val slots: List<String?>? = null,
)

/**
 * One look of the user's own, as it is kept (KLANG-00422). The document of the own looks has a field per look, its
 * id, so two tabs that each make a look both keep theirs; a look deleted is `null`, since a write cannot remove a
 * field.
 */
@Serializable
data class OwnLook(
    val name: String = "",
    val author: String = "",
    /** The look's values, each under its role's CSS variable (`--kl-ground-app`). */
    val values: Map<String, String> = emptyMap(),
)
