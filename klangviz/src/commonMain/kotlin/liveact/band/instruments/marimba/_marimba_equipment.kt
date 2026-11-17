/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.marimba

import io.peekandpoke.klang.viz.liveact.band.instruments.Equipment

/** What the marimba player needs to equip: four mallets, two in each hand. The marimba makes them this long. */
val MARIMBA_EQUIPMENT: Equipment.Mallets = Equipment.Mallets(perHand = 2, length = 0.40)
