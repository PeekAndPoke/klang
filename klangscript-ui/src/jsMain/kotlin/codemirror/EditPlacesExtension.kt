/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.codemirror.ext.Extension
import io.peekandpoke.klang.codemirror.ext.KeyBinding
import io.peekandpoke.klang.codemirror.ext.StateEffect
import io.peekandpoke.klang.codemirror.ext.StateEffectType
import io.peekandpoke.klang.codemirror.ext.StateField
import io.peekandpoke.klang.codemirror.ext.StateFieldConfig
import io.peekandpoke.klang.codemirror.ext.Transaction
import io.peekandpoke.klang.codemirror.ext.keymap
import io.peekandpoke.kraft.utils.jsObject

/**
 * Ctrl+Shift+Backspace (Cmd+Shift+Backspace on a Mac): back to where the user last edited (KLANG-00322).
 *
 * Wires [EditPlaces] into the editor and holds no rule of its own: a state field keeps the places and the walk, and a
 * key binding steps back. The places live as long as the editor (one song open) and are not saved.
 *
 * Every document change is an edit (the ticket's point 3, as changed 2026-10-21): typing, a command such as toggle
 * comment or move line (toggle comment carries no user event at all), an editor tool's commit. The one change the
 * editor makes of its own, replacing the whole document, carries [clear] and empties the list. A transaction that
 * changes only the selection (a step back, "go to definition") leaves the places as they are. A cursor move other
 * than the step back's own (by key, by mouse) ends the walk, so the next step starts again from the newest place.
 */
internal object EditPlacesExtension {

    /** The key in CodeMirror's notation: `Mod` is Cmd on a Mac and Ctrl elsewhere. */
    const val KEY = "Mod-Shift-Backspace"

    /** Empties the list: the whole document was replaced (a song loaded or reset through `setCode`). */
    val clear: StateEffectType<Unit> = StateEffect.define()

    /** The walk index after a step back, carried by the transaction that moves the selection there. */
    private val walked: StateEffectType<Int> = StateEffect.define()

    /** The places of one editor. */
    val field: StateField<EditPlaces> = StateField.define(
        jsObject<StateFieldConfig<EditPlaces>> {
            this.create = { EditPlaces() }
            this.update = { places, tr -> update(places = places, tr = tr) }
        }
    )

    /** The field and the key binding, for the editor's extensions. */
    val extension: Extension = arrayOf(
        field,
        keymap.of(
            arrayOf(
                jsObject<KeyBinding> {
                    this.key = KEY
                    this.run = { view, _ -> goBack(view) }
                    // Also when there is no place to go back to: on a Mac, Chrome and Firefox open their dialog
                    // for clearing the browsing history on a Cmd+Shift+Backspace that reaches them.
                    this.preventDefault = true
                }
            )
        ),
    ).unsafeCast<Extension>()

    /**
     * One step back: moves the cursor to the place [EditPlaces.back] picks and scrolls it into view.
     *
     * Returns true when the cursor moved, false when there was no place to go to. Internal, not private, so the spec
     * can step without a key event.
     */
    internal fun goBack(view: EditorView): Boolean {
        val state = view.state
        val places = state.field(field).unsafeCast<EditPlaces>()

        val step = places.back(cursor = state.selection.main.head, lineOf = { pos -> state.doc.lineAt(pos).number })
            ?: return false

        view.dispatch(
            state.update(
                jsObject {
                    this.selection = jsObject<dynamic> { this.anchor = step.target }
                    this.scrollIntoView = true
                    this.effects = walked.of(step.walkIndex)
                }
            )
        )

        return true
    }

    private fun update(places: EditPlaces, tr: Transaction): EditPlaces {
        if (tr.effects.any { it.`is`(clear) }) {
            return EditPlaces()
        }

        // The changes first, so a step back lands on places that are already in the new document
        val changed = if (tr.changes.empty) places else changedBy(places = places, tr = tr)
        val walk = tr.effects.firstOrNull { it.`is`(walked) }

        if (walk != null) {
            return changed.walkedTo(walk.value.unsafeCast<Int>())
        }

        // Any other cursor move (a key, a click, "go to definition") ends the walk; the step back's own move carries
        // `walked` and is handled above
        if (tr.selection != null) {
            return changed.cursorMoved()
        }

        return changed
    }

    /** [places] after the document change of [tr], which is always an edit. */
    private fun changedBy(places: EditPlaces, tr: Transaction): EditPlaces {
        // The new document, not `tr.state`: the state is still being built while the fields update.
        val newDoc = tr.newDoc

        return places.changed(
            mapPos = { pos -> tr.changes.mapPos(pos) },
            ranges = changedRanges(tr),
            lineOf = { pos -> newDoc.lineAt(pos).number },
        )
    }

    /** The ranges a transaction wrote, in the new document and in document order (one per cursor, for example). */
    private fun changedRanges(tr: Transaction): List<EditPlaces.ChangedRange> {
        val ranges = mutableListOf<EditPlaces.ChangedRange>()

        tr.changes.iterChanges { _, _, fromB, toB, _ ->
            ranges.add(EditPlaces.ChangedRange(from = fromB, to = toB))
        }

        return ranges
    }
}
