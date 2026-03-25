package io.peekandpoke.klang.pages.docs.tutorials

import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.semanticui.SemanticIconFn
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.semanticIcon
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.ui.feel.KlangLookAndFeel
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

fun scopeColor(laf: KlangLookAndFeel, scope: TutorialScope): String = when (scope) {
    TutorialScope.Quick -> laf.good
    TutorialScope.Standard -> laf.gold
    TutorialScope.DeepDive -> laf.warning
}

fun TutorialDifficulty.iconFn(): SemanticIconFn = when (this) {
    TutorialDifficulty.Beginner -> semanticIcon { seedling }
    TutorialDifficulty.Intermediate -> semanticIcon { signal }
    TutorialDifficulty.Advanced -> semanticIcon { fire }
    TutorialDifficulty.Pro -> semanticIcon { star }
}

fun TutorialDifficulty.renderIcon(tag: FlowContent) = tag.icon.(iconFn())().render()

fun TutorialScope.iconFn(): SemanticIconFn = when (this) {
    TutorialScope.Quick -> semanticIcon { rocket }
    TutorialScope.Standard -> semanticIcon { hourglass }
    TutorialScope.DeepDive -> semanticIcon { hourglass_half }
}

fun TutorialScope.renderIcon(tag: FlowContent) = tag.icon.(iconFn())().render()

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

fun Tag.scopeLabel(laf: KlangLookAndFeel, scope: TutorialScope) {
    ui.mini.label {
        css {
            backgroundColor = Color("${scopeColor(laf, scope)} !important")
            color = Color("#222 !important")
        }
        scope.renderIcon(this)
        +scope.label
    }
}
