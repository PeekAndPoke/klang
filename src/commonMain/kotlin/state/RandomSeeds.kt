/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.state

import kotlinx.serialization.Serializable

/**
 * The user's random seeds as they are stored (the maintainer, 2026-10-28: "every user should get a ui-seed in the
 * storage"): [metal], which the buttons' grain, the brushed metal and the start page's sheet grow from. Null until
 * it is made, once, on the first visit: a seed has no default a document could leave out, so the Spine's `Seeds`
 * writes it when it makes it.
 */
@Serializable
data class RandomSeeds(val metal: Int? = null)
