package io.peekandpoke.klang.workshop.keys

import io.peekandpoke.kraft.KraftApp
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.getAttributeRecursive
import io.peekandpoke.kraft.components.getAttributeRecursiveOrNull

/**
 * Registers [manager] in the Kraft application, with the [KeyHintStage] all hints are drawn on: [hint] is the app's
 * look of a hint and [placement] where hints go, for every [OnKey] that says nothing else. Components reach the
 * manager with [keys].
 */
fun KraftApp.Builder.keys(
    manager: KeysManager,
    hint: KeyHintRenderer = KeyHints.badge,
    placement: KeyHintPlacement = KeyHintPlacement.Below,
) = apply {
    setAttribute(KeysManager.key, manager)
    setAttribute(KeyHintStage.key, KeyHintStage(manager = manager, renderer = hint, placement = placement))
}

/** The app's [KeysManager]. */
val Component<*>.keys: KeysManager get() = getAttributeRecursive(KeysManager.key)

/** The app's [KeyHintStage]; null in an app without one. */
val Component<*>.keyHintStage: KeyHintStage? get() = getAttributeRecursiveOrNull(KeyHintStage.key)
