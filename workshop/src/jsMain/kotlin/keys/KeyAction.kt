package io.peekandpoke.klang.workshop.keys

/**
 * Something the user can do by key: `Play`, `Stop`, `Undo`. Components ask for an action, never for a key; which key
 * triggers it is the [Keymap]'s business, so a hint always shows the key that is set, and the user can change it.
 *
 * [keys] are the default keys ([KeyChord]'s text, `"Mod-Shift-Enter"`), [macKeys] the Mac's where they differ; `Mod`
 * already covers Ctrl against Cmd. An action without keys has none until the user gives it one.
 */
data class KeyAction(
    /** Unique among all actions a [KeysManager] knows; the user's keys are stored under it, so it never changes. */
    val id: String,
    /** What the action does, for the user: in a settings page, a tooltip. */
    val title: String,
    /** The default keys, PC and Mac alike unless [macKeys] says otherwise. */
    val keys: List<String> = emptyList(),
    /** The Mac's default keys, when they are not [keys]. */
    val macKeys: List<String>? = null,
    /** Actions in one scope must not share a key; actions in different scopes may (the page's and an editor's). */
    val scope: String = APP_SCOPE,
    /** How far forward its key stands when several actions in the top layer answer the same key; see [KeyPriority]. */
    val priority: KeyPriority = KeyPriority.Default,
) {
    companion object {
        /** The scope of the whole page. */
        const val APP_SCOPE = "app"
    }

    /** The default keys on [platform], as text. */
    fun defaultKeys(platform: KeyPlatform): List<String> = when (platform) {
        KeyPlatform.Pc -> keys
        KeyPlatform.Mac -> macKeys ?: keys
    }
}

/**
 * How far forward an action's key stands when several actions in the top layer answer the same key, as z-index does
 * for elements (maintainer, 2026-10-26): the highest [value] answers; within one priority, the newest registration.
 * Actions on one key with different priorities are a deliberate order, not a clash. An app keeps all of its priorities
 * in one place, so it always sees which there are; [name] says which one a value is.
 */
data class KeyPriority(val value: Int, val name: String) {
    companion object {
        /** Every action that names none. */
        val Default = KeyPriority(value = 0, name = "default")
    }
}

/**
 * A bag of [KeyAction]s that belong together, defined with [KeysManager.define] in one go: an app's actions, a
 * component library's.
 */
interface KeyBindings {
    /** The actions of this bag. */
    val actions: List<KeyAction>

    /**
     * The rows of an overview of this bag ([KeyBindingsOverlay]), in order; by default each action on its own. A row may
     * name an action of another bag defined in the same manager (the editor's overview shows the page's "Focus editor").
     */
    val overview: List<KeyOverviewRow> get() = actions.map { KeyOverviewRow.Single(it) }
}

/**
 * Where a [KeysManager] keeps the keys the user set, as text per action id. The app gives one (the browser's storage,
 * later a server); [InMemory] keeps them until the page reloads.
 */
interface KeymapStore {
    /** The user's keys, per action id; an empty list means the user took every key off that action. */
    fun load(): Map<String, List<String>>

    /** Keeps [userKeys], all of them, replacing what was kept. */
    fun save(userKeys: Map<String, List<String>>)

    /** Keeps the user's keys for as long as the page lives. */
    class InMemory(initial: Map<String, List<String>> = emptyMap()) : KeymapStore {
        private var kept = initial

        override fun load(): Map<String, List<String>> = kept

        override fun save(userKeys: Map<String, List<String>>) {
            kept = userKeys
        }
    }
}
