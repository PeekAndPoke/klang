/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.selectCss
import io.peekandpoke.kraft.testing.textContent
import kotlinx.html.span

/** The card and the caption (KLANG-00339): a plate around its content, without rivets; a title and its note. */
class KlangCardSpec : StringSpec({

    val styles = KlangControlStyles

    "a card holds its content and the caption under it, and has no rivets" {
        TestBed.preact(
            view = {
                KlangCard {
                    span { +"THING" }
                    KlangCaption(title = "Lamps", note = "klangui/comp/KlangLamp.kt")
                }
            },
        ) { root ->
            root.selectCss(".${styles.card}").textContent() shouldBe "THINGLampsklangui/comp/KlangLamp.kt"
            root.selectCss(".${styles.captionTitle}").textContent() shouldBe "Lamps"
            root.selectCss(".${styles.captionNote}").textContent() shouldBe "klangui/comp/KlangLamp.kt"
            root.selectCss(".${styles.rivet}").size shouldBe 0
        }
    }

    "a caption without a note is its title alone" {
        TestBed.preact(view = { KlangCaption(title = "Lamps") }) { root ->
            root.selectCss(".${styles.captionNote}").size shouldBe 0
            root.textContent() shouldBe "Lamps"
        }
    }
})
