/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.storage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.ui.comp.KlangWorkbench
import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** The workbench's layout as the browser keeps it (KLANG-00349): what it wrote it reads back; what it cannot read starts at its default. */
class WorkbenchLayoutStorageSpec : StringSpec({

    fun decode(json: String) = WorkbenchLayoutStorage.decode(Json.parseToJsonElement(json).jsonObject)

    "a layout written is read back as it was" {
        val layout = KlangWorkbench.Layout(
            pinned = setOf("settings", "motor"),
            open = "settings",
            bottomOpen = true,
            bottomPinned = true,
            bottomHeight = 333.5,
        )

        WorkbenchLayoutStorage.decode(WorkbenchLayoutStorage.encode(layout)) shouldBe layout
    }

    "a part of the wrong kind starts at its default, the parts beside it kept" {
        decode("""{"pinned":5,"open":"docs","bottomOpen":"maybe","bottomPinned":true,"bottomHeight":"x"}""") shouldBe
            KlangWorkbench.Layout(open = "docs", bottomPinned = true)
    }

    "a height that is no number starts at the default" {
        decode("""{"bottomHeight":1e999}""").bottomHeight shouldBe KlangWorkbench.Layout().bottomHeight
    }

    "what cannot be read as a layout is no layout; nothing kept is none either" {
        val kept = localStorage.getItem(StorageKeys.WORKBENCH_LAYOUT)

        try {
            localStorage.setItem(StorageKeys.WORKBENCH_LAYOUT, "[1, 2]")
            WorkbenchLayoutStorage.load() shouldBe null

            localStorage.setItem(StorageKeys.WORKBENCH_LAYOUT, "{")
            WorkbenchLayoutStorage.load() shouldBe null

            localStorage.removeItem(StorageKeys.WORKBENCH_LAYOUT)
            WorkbenchLayoutStorage.load() shouldBe null
        } finally {
            if (kept != null) {
                localStorage.setItem(StorageKeys.WORKBENCH_LAYOUT, kept)
            } else {
                localStorage.removeItem(StorageKeys.WORKBENCH_LAYOUT)
            }
        }
    }
})
