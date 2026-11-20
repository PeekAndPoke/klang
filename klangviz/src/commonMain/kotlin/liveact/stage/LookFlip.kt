/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches

/**
 * The key x (`?look=new`, [Switch.Look]): one key that flips the whole look between today, the look as it was made,
 * and the new look ([LookVariants.NEW]), for comparing the two (the maintainer, 2026-11-20: "Give me some hotkey to
 * toggle between variants").
 *
 * Two sets of variants: the one [shown] now, and the one [kept] for the next x. [flip] swaps them; a variant's own key
 * changes only the one shown ([tune]). So each side remembers how it was last tuned: the new look with the world set to
 * night by the key n, flipped to today and back, is at night again. [new] says which side is shown.
 */
data class LookFlip(val shown: LookVariants, val kept: LookVariants, val new: Boolean) {

    /** The other side shown, and this one kept as it is now. */
    fun flip(): LookFlip = LookFlip(shown = kept, kept = shown, new = !new)

    /** The side shown changed to [to] (a variant's own key); the other side stays as it was. */
    fun tune(to: LookVariants): LookFlip = copy(shown = to)

    /** The line shown for a moment when the look is flipped: `look: new`, or `look: today`. */
    fun line(): String = if (new) "look: new" else "look: today"

    companion object {
        /**
         * The look as the address asks: with `?look=new` the new look shown (with the address's single variants over
         * it) and the look as made kept; without it the address's variants shown and the new look kept.
         */
        fun of(switches: Switches): LookFlip {
            val new = LookVariants.asksNew(switches)

            return LookFlip(
                shown = LookVariants.of(switches),
                kept = if (new) LookVariants() else LookVariants.NEW,
                new = new,
            )
        }
    }
}
