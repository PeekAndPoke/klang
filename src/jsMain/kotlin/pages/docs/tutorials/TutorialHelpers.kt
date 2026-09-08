/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

import io.peekandpoke.klang.ui.feel.KlangLookAndFeel
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.semanticui.SemanticIconFn
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.semanticIcon
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.Color
import kotlinx.css.backgroundColor
import kotlinx.css.color
import kotlinx.html.FlowContent
import kotlinx.html.Tag

fun difficultyColor(laf: KlangLookAndFeel, difficulty: TutorialDifficulty): String = when (difficulty) {
    TutorialDifficulty.Beginner -> laf.good
    TutorialDifficulty.Intermediate -> laf.gold
    TutorialDifficulty.Advanced -> laf.warning
    TutorialDifficulty.Pro -> laf.accent
}

fun depthColor(laf: KlangLookAndFeel, depth: TutorialDepth): String = when (depth) {
    TutorialDepth.Quick -> laf.good
    TutorialDepth.Standard -> laf.gold
    TutorialDepth.DeepDive -> laf.warning
}

fun TutorialDifficulty.iconFn(): SemanticIconFn = when (this) {
    TutorialDifficulty.Beginner -> semanticIcon { seedling }
    TutorialDifficulty.Intermediate -> semanticIcon { signal }
    TutorialDifficulty.Advanced -> semanticIcon { fire }
    TutorialDifficulty.Pro -> semanticIcon { star }
}

fun TutorialDifficulty.renderIcon(tag: FlowContent) = tag.icon.(iconFn())().render()

fun TutorialDepth.iconFn(): SemanticIconFn = when (this) {
    TutorialDepth.Quick -> semanticIcon { rocket }
    TutorialDepth.Standard -> semanticIcon { hourglass }
    TutorialDepth.DeepDive -> semanticIcon { hourglass_half }
}

fun TutorialDepth.renderIcon(tag: FlowContent) = tag.icon.(iconFn())().render()

fun TutorialsListPage.CompletionFilter.iconFn(): SemanticIconFn = when (this) {
    TutorialsListPage.CompletionFilter.All -> semanticIcon { circle }
    TutorialsListPage.CompletionFilter.Completed -> semanticIcon { check_circle }
    TutorialsListPage.CompletionFilter.Open -> semanticIcon { circle_outline }
}

fun TutorialsListPage.CompletionFilter.renderIcon(tag: FlowContent) = tag.icon.(iconFn())().render()

fun Tag.difficultyLabel(laf: KlangLookAndFeel, difficulty: TutorialDifficulty) {
    ui.mini.icon.label {
        css {
            backgroundColor = Color("${difficultyColor(laf, difficulty)} !important")
            color = Color("#222 !important")
        }
        difficulty.renderIcon(this)
        +difficulty.label
    }
}

fun Tag.depthLabel(laf: KlangLookAndFeel, depth: TutorialDepth) {
    ui.mini.label {
        css {
            backgroundColor = Color("${depthColor(laf, depth)} !important")
            color = Color("#222 !important")
        }
        depth.renderIcon(this)
        +depth.label
    }
}
