/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.peekandpoke.klang.workshop.keys.KeyPlatform
import io.peekandpoke.klang.workshop.keys.Keymap

/**
 * Klang's own keys (KLANG-00325) have no problem on either platform: no clash, no key the browser keeps, none that
 * cannot be read. The start-up check of the plan, run as a test.
 */
class KlangKeyBindingsSpec : StringSpec({

    "Klang's default keys are sound on a PC and on a Mac" {
        KeyPlatform.entries.forEach { platform ->
            withClue(platform) {
                Keymap(platform = platform, actions = KlangKeyBindings.actions).problems.shouldBeEmpty()
            }
        }
    }
})
