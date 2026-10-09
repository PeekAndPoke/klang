/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

/** A typed view the builder row fills, as the backend fills `AudioContextOptions`. */
private external interface JsObjectsProbe {
    var name: String
    var count: Int
}

/** [jsObject]: a fresh, empty plain JS object, optionally filled through a typed view. */
class JsObjectsSpec : StringSpec({

    "jsObject() is a fresh empty object each call" {
        // Typed as Any: a matcher is an extension, which a `dynamic` receiver cannot call.
        val a: Any = jsObject()
        val b: Any = jsObject()

        a shouldNotBeSameInstanceAs b
        (js("Object").keys(a).length as Int) shouldBe 0
    }

    "jsObject { } sets the fields the block writes, and nothing else" {
        val probe = jsObject<JsObjectsProbe> {
            name = "worklet"
            count = 2
        }

        probe.name shouldBe "worklet"
        probe.count shouldBe 2
        (js("Object").keys(probe).length as Int) shouldBe 2
    }
})
