package io.peekandpoke.klang.pages.docs.tutorials

enum class TutorialDifficulty(val label: String) {
    Beginner("Beginner"),
    Intermediate("Intermediate"),
    Advanced("Advanced"),
    Pro("Pro"),
}

enum class TutorialScope(val label: String) {
    Quick("Quick"),
    Standard("Standard"),
    DeepDive("Deep Dive"),
}

data class TutorialSection(
    val heading: String? = null,
    val text: String = "",
    val code: String? = null,
)

data class Tutorial(
    val slug: String,
    val title: String,
    val description: String,
    val difficulty: TutorialDifficulty,
    val scope: TutorialScope,
    val tags: List<String>,
    val sections: List<TutorialSection>,
)
