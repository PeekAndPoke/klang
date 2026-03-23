package io.peekandpoke.klang.pages.docs.tutorials

import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.ui.feel.KlangLookAndFeel
import kotlinx.css.Color
import kotlinx.css.backgroundColor
import kotlinx.css.color
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

fun Tag.difficultyLabel(laf: KlangLookAndFeel, difficulty: TutorialDifficulty) {
    ui.mini.label {
        css {
            backgroundColor = Color("${difficultyColor(laf, difficulty)} !important")
            color = Color("#222 !important")
        }
        when (difficulty) {
            TutorialDifficulty.Beginner -> icon.seedling()
            TutorialDifficulty.Intermediate -> icon.signal()
            TutorialDifficulty.Advanced -> icon.fire()
            TutorialDifficulty.Pro -> icon.star()
        }
        +difficulty.label
    }
}

fun Tag.scopeLabel(laf: KlangLookAndFeel, scope: TutorialScope) {
    ui.mini.label {
        css {
            backgroundColor = Color("${scopeColor(laf, scope)} !important")
            color = Color("#222 !important")
        }
        when (scope) {
            TutorialScope.Quick -> icon.bolt()
            TutorialScope.Standard -> icon.clock()
            TutorialScope.DeepDive -> icon.hourglass_half()
        }
        +scope.label
    }
}
