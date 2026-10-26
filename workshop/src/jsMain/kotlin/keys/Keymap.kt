package io.peekandpoke.klang.workshop.keys

import org.w3c.dom.events.KeyboardEvent

/**
 * Which keys trigger which action, at one moment: the defaults of every defined [KeyAction] for one [KeyPlatform],
 * with the user's keys over them. Immutable; the [KeysManager] builds a new one when an action is defined or the user
 * changes a key, and publishes it.
 *
 * What is wrong with it is in [problems]: two actions on one key in one scope, a key the browser never hands to a page,
 * a key that cannot be read. A clash stays in the keymap (both actions keep the key; of the two, the newest enabled
 * registration in the top layer answers, see [KeysManager]) so the user sees it and can fix it.
 */
class Keymap(
    val platform: KeyPlatform,
    actions: List<KeyAction>,
    /** The user's keys per action id, over the defaults; an action id no defined action has is kept, not used. */
    val userKeys: Map<String, List<String>> = emptyMap(),
) {
    /** Something wrong in a keymap. */
    sealed interface Problem {
        /**
         * Two or more actions of one scope and one [KeyPriority] share a key; the newest enabled registration in the top
         * layer answers. Different priorities on one key are a deliberate order, no clash.
         */
        data class Clash(val scope: String, val chord: KeyChord, val actionIds: List<String>) : Problem

        /** Two or more actions of one scope and one priority share keys pressed in a row; as for [Clash]. */
        data class SequenceClash(val scope: String, val sequence: KeySequence, val actionIds: List<String>) : Problem

        /**
         * In one scope and one priority, the chord [prefix] is an action's own key and how [sequence] begins: of the two,
         * the newest registration decides whether the chord acts or waits for the second. [actionIds] are the prefix's
         * action, then the sequence's (the same one twice when one action has both).
         */
        data class PrefixClash(
            val scope: String,
            val prefix: KeyChord,
            val sequence: KeySequence,
            val actionIds: List<String>,
        ) : Problem

        /** The browser keeps this key from every page (a new tab, closing the tab): the action would never fire. */
        data class Reserved(val actionId: String, val chord: KeyChord) : Problem

        /** A key no press can match (`Mod-Shift-?`: with Shift, write the key the US keyboard has there, `/`). */
        data class Unreachable(val actionId: String, val chord: KeyChord) : Problem

        /** A key that cannot be read; the action goes without it. */
        data class Unreadable(val actionId: String, val text: String, val reason: String) : Problem
    }

    companion object {
        /** Keys Chrome keeps from every page, as text, also with Shift. */
        private val RESERVED_KEYS = listOf("Mod-N", "Mod-T", "Mod-W", "Ctrl-Tab")

        /** Keys the Mac keeps from every app: quit, hide, minimise. */
        private val RESERVED_MAC_KEYS = listOf("Cmd-Q", "Cmd-H", "Cmd-M")

        /** The reserved keys on [platform], with and without Shift. */
        fun reservedChords(platform: KeyPlatform): List<KeyChord> {
            val texts = when (platform) {
                KeyPlatform.Pc -> RESERVED_KEYS
                KeyPlatform.Mac -> RESERVED_KEYS + RESERVED_MAC_KEYS
            }

            return texts
                .map { KeyChord.parse(it, platform) }
                .flatMap { listOf(it, it.copy(shift = true)) }
        }
    }

    /** Every action, in the order they were defined. */
    val actions: List<KeyAction> = actions

    private val actionsById: Map<String, KeyAction> = actions.associateBy { it.id }

    private val unreadable = mutableListOf<Problem.Unreadable>()

    /** The bindings per action id, in the order of their text, the user's keys where set. */
    private val bindingsById: Map<String, List<KeyBinding>> = actions.associate { action ->
        val texts = userKeys[action.id] ?: action.defaultKeys(platform)

        action.id to texts.mapNotNull { text ->
            try {
                when {
                    KeySequence.isSequence(text) -> KeySequence.parse(text, platform)
                    else -> KeyChord.parse(text, platform)
                }
            } catch (e: IllegalArgumentException) {
                unreadable.add(Problem.Unreadable(actionId = action.id, text = text, reason = e.message ?: "unreadable"))
                null
            }
        }.distinct()
    }

    /** The single chords per action id. */
    private val chordsById: Map<String, List<KeyChord>> = bindingsById.mapValues { (_, bindings) ->
        bindings.filterIsInstance<KeyChord>()
    }

    /** Every action's chords in a row (`"Mod-K Mod-S"`), in the order the actions were defined. */
    val chordSequences: List<Pair<KeyAction, KeySequence>> = actions.flatMap { action ->
        sequencesOf(action.id).filter { it.chords.isNotEmpty() }.map { action to it }
    }

    /** Everything wrong with this keymap; empty when it is all right. */
    val problems: List<Problem> = buildList {
        addAll(unreadable)

        val reserved = reservedChords(platform).toSet()

        actions.forEach { action ->
            // The chords of keys in a row too: a step the browser keeps or no press can match ends every sequence there
            val chords = (chordsOf(action.id) + sequencesOf(action.id).flatMap { it.chords }).distinct()

            chords.filter { it in reserved }.forEach { chord ->
                add(Problem.Reserved(actionId = action.id, chord = chord))
            }

            chords.filterNot { it.canFire(platform) }.forEach { chord ->
                add(Problem.Unreachable(actionId = action.id, chord = chord))
            }
        }

        // One scope and one priority (its value, as the manager orders them): different priorities on one key are a
        // deliberate order
        actions.groupBy { it.scope to it.priority.value }.forEach { (group, inScope) ->
            val scope = group.first

            inScope
                .flatMap { action -> chordsOf(action.id).map { chord -> chord to action.id } }
                .groupBy(keySelector = { it.first }, valueTransform = { it.second })
                .filterValues { it.size > 1 }
                .forEach { (chord, ids) -> add(Problem.Clash(scope = scope, chord = chord, actionIds = ids)) }

            inScope
                .flatMap { action -> sequencesOf(action.id).map { sequence -> sequence to action.id } }
                .groupBy(keySelector = { it.first }, valueTransform = { it.second })
                .filterValues { it.size > 1 }
                .forEach { (sequence, ids) -> add(Problem.SequenceClash(scope = scope, sequence = sequence, actionIds = ids)) }

            val chordRows = chordSequences.filter { (owner, _) -> owner.scope == scope && owner.priority.value == group.second }

            inScope.forEach { action ->
                chordsOf(action.id).forEach { chord ->
                    chordRows
                        .filter { (_, sequence) -> sequence.chords.first() == chord }
                        .forEach { (owner, sequence) ->
                            val ids = listOf(action.id, owner.id)
                            add(Problem.PrefixClash(scope = scope, prefix = chord, sequence = sequence, actionIds = ids))
                        }
                }
            }
        }
    }

    /** The action with [id], or null when none is defined. */
    fun action(id: String): KeyAction? = actionsById[id]

    /** The chords that trigger the action with [actionId]; empty when it has none or is not defined. */
    fun chordsOf(actionId: String): List<KeyChord> = chordsById[actionId] ?: emptyList()

    /** Everything that triggers the action with [actionId], chords and keys in a row, in the order of their text. */
    fun bindingsOf(actionId: String): List<KeyBinding> = bindingsById[actionId] ?: emptyList()

    /** The keys in a row that trigger the action with [actionId]. */
    private fun sequencesOf(actionId: String): List<KeySequence> = bindingsOf(actionId).filterIsInstance<KeySequence>()

    /** The actions [sequence] triggers, in the order they were defined. */
    fun actionsFor(sequence: KeySequence): List<KeyAction> = actions.filter { action -> sequence in sequencesOf(action.id) }

    /** The actions [event] triggers, in the order they were defined. */
    fun actionsFor(event: KeyboardEvent): List<KeyAction> = actions.filter { action ->
        chordsOf(action.id).any { it.matches(event = event, platform = platform) }
    }

    /** True when [event] triggers the action with [actionId]. */
    fun matches(actionId: String, event: KeyboardEvent): Boolean = chordsOf(actionId).any { it.matches(event = event, platform = platform) }
}
