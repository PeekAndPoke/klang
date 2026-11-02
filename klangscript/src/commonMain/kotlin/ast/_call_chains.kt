/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.ast

/**
 * The call named one of [names] (a function and its aliases, `sound` and `s`) this chain (`x.sound(lead).reverb()`)
 * reads last: walked down from its last link, the first such call met, so in `x.sound(a).s(b)` the one with `b`. A call
 * inside an argument (`superimpose(y => y.sound(bell))`) is not the chain's own and is passed over; null without one
 * (the MIDI play code's sound, KLANG-00402).
 */
fun Expression.lastCallInChain(vararg names: String): CallExpression? {
    var link: Expression? = this

    while (link is CallExpression) {
        val callee = link.callee

        if ((callee is MemberAccess && callee.property in names) || (callee is Identifier && callee.name in names)) {
            return link
        }

        link = (callee as? MemberAccess)?.obj
    }

    return null
}
