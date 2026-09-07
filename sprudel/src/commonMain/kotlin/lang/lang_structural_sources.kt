/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.pattern.EmptyPattern

// -- silence / rest / nothing -----------------------------------------------------------------------------------------

/**
 * An empty pattern that produces no events.
 *
 * Use `silence` wherever a [SprudelPattern] argument is required but nothing should play.
 * It is the identity element for [stack] and acts as a rest in sequencing functions like [cat].
 *
 *
 * ```KlangScript(Playable)
 * seq("c3", silence, "e3", "g3").note()  // rest on the second step
 * ```
 *
 * ```KlangScript(Playable)
 * cat(s("bd sd"), silence)  // phrase followed by a silent cycle
 * ```
 * @alias rest, nothing
 * @category continuous
 * @tags silence, rest, empty, quiet
 */
@KlangScript.Constant
val silence: SprudelPattern = EmptyPattern

/**
 * An empty pattern that produces no events. Alias for [silence].
 *
 *
 * ```KlangScript(Playable)
 * seq("c3", rest, "e3", "g3").note()  // rest on the second step
 * ```
 *
 * ```KlangScript(Playable)
 * cat(s("bd sd"), rest)  // phrase followed by a silent cycle
 * ```
 * @alias silence, nothing
 * @category continuous
 * @tags rest, silence, empty, quiet
 */
@KlangScript.Constant
val rest: SprudelPattern = EmptyPattern

/**
 * An empty pattern that produces no events. Alias for [silence].
 *
 *
 * ```KlangScript(Playable)
 * seq("c3", nothing, "e3", "g3").note()  // rest on the second step
 * ```
 *
 * ```KlangScript(Playable)
 * cat(s("bd sd"), nothing)  // phrase followed by a silent cycle
 * ```
 * @alias silence, rest
 * @category continuous
 * @tags nothing, silence, rest, empty, quiet
 */
@KlangScript.Constant
val nothing: SprudelPattern = EmptyPattern
