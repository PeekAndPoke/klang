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

enum class TutorialTag(val label: String) {
    Rhythm("Rhythm"),
    Melody("Melody"),
    Chords("Chords"),
    Synthesis("Synthesis"),
    Effects("Effects"),
    Patterns("Patterns"),
    Mixing("Mixing"),
    Arrangement("Arrangement"),
    LiveCoding("Live Coding"),
    Generative("Generative"),
    Genre("Genre"),
    GettingStarted("Getting Started"),
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
    val tags: List<TutorialTag>,
    val sections: List<TutorialSection>,
    val rpm: Double = 30.0,
)
