/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Switches

class StageLookSpec : StringSpec({

    "?stage=mesh is the mesh; no switch, an empty one or another word the wall as it was made" {
        StageLook.of(Switches.parse("?stage=mesh")) shouldBe StageLook.Mesh
        StageLook.of(Switches.parse("")) shouldBe StageLook.Wall
        StageLook.of(Switches.parse("?stage")) shouldBe StageLook.Wall
        StageLook.of(Switches.parse("?stage=wall")) shouldBe StageLook.Wall
        StageLook.of(Switches.parse("?stage=fence")) shouldBe StageLook.Wall
    }
})
