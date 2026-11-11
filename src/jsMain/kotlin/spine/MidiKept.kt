/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.midi.MidiPlayCode
import io.peekandpoke.klang.spine.storage.SectionStores
import io.peekandpoke.klang.spine.storage.Stored
import io.peekandpoke.klang.state.MidiDesk
import io.peekandpoke.klang.state.MidiPlay
import io.peekandpoke.klang.storage.MidiInstrument
import io.peekandpoke.klang.storage.StorageKeys
import io.peekandpoke.klang.workshop.storage.DocumentStorage
import io.peekandpoke.klang.workshop.storage.KeyValueStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** One instrument as the MIDI page saved it before the one play code. Scaffolding, with the readers below. */
@Serializable
private class InstrumentBefore(val name: String? = null, val ignitor: String = "", val mapper: String = "") {
    fun instrument(): MidiInstrument? = name?.let { MidiInstrument(name = it, ignitorCode = ignitor, mapperCode = mapper) }
}

/**
 * What the MIDI rig and the MIDI settings keep (KLANG-00402, stored as sections since KLANG-00422): the play code,
 * the user's, and this machine's setup, the loadout and the laptop keys' layout.
 *
 * Built with the Spine, not with the rig (which is made on first use), so the storage page can forget a section
 * whether the rig ran or not. Both sections follow another tab, so a rig that starts late plays the play code
 * applied last, wherever it was applied, and an Apply here is written over that one, not over what this tab read
 * when it loaded. A rig that runs is not changed by another tab's play code: it reads the play code when it starts.
 * Its loadout shows another tab's at once.
 */
class MidiKept(stores: SectionStores) {

    companion object {
        private val beforeJson = Json { ignoreUnknownKeys = true }

        /** The instrument the page started with before the one play code, saved as the draft on every visit. */
        private val OLD_DEFAULT = MidiInstrument(
            name = "Warm saw",
            ignitorCode = "Ign.saw()\n  .plus(Ign.sine().detune(12).mul(0.3))\n  .lowpass(1800)\n  .adsr(0.01, 0.3, 0.6, 0.4)",
            mapperCode = "reverb(wet = 0.2)",
        )

        /** [decode] of the text under [key]; null when there is none or it cannot be read. */
        private fun <T> before(store: KeyValueStore, key: String, decode: (String) -> T): T? = try {
            store.read(key)?.let(decode)
        } catch (_: Exception) {
            null
        }

        /**
         * The play code as it was kept until KLANG-00422, a plain text under its key; else the draft and the saved
         * instruments of before the one play code, as one. Scaffolding: it goes when no browser holds the old keys.
         */
        private fun playBefore(store: KeyValueStore): DocumentStorage.Legacy<MidiPlay>? {
            val code = store.read(StorageKeys.MIDI_PLAY_CODE)
            val draft = before(store = store, key = StorageKeys.MIDI_INSTRUMENT_DRAFT) {
                beforeJson.decodeFromString(InstrumentBefore.serializer(), it).instrument()
            }
            val saved = before(store = store, key = StorageKeys.MIDI_INSTRUMENTS) { text ->
                beforeJson.decodeFromString(ListSerializer(InstrumentBefore.serializer()), text).mapNotNull { it.instrument() }
            }
            // Only what was read goes: a text that could not be read stays where it is
            val instrumentKeys = listOfNotNull(
                StorageKeys.MIDI_INSTRUMENT_DRAFT.takeIf { draft != null },
                StorageKeys.MIDI_INSTRUMENTS.takeIf { saved != null },
            )

            if (code != null) {
                // The play code was made from the instruments when it was first kept: they go with its old key
                return DocumentStorage.Legacy(value = MidiPlay(code = code), keys = listOf(StorageKeys.MIDI_PLAY_CODE) + instrumentKeys)
            }

            // Nothing of the user's: none, or only the page's own default it saved on every visit, which stays the app's
            if (saved.isNullOrEmpty() && (draft == null || draft == OLD_DEFAULT)) {
                return null
            }

            return DocumentStorage.Legacy(
                value = MidiPlay(code = MidiPlayCode.fromOld(draft = draft, saved = saved.orEmpty())),
                keys = instrumentKeys,
            )
        }

        /** The loadout and the laptop keys' layout as they were kept until KLANG-00422, a key each. Scaffolding. */
        private fun deskBefore(store: KeyValueStore): DocumentStorage.Legacy<MidiDesk>? {
            val loadout = before(store = store, key = StorageKeys.MIDI_LOADOUT) {
                beforeJson.decodeFromString(ListSerializer(String.serializer()), it)
            }
            val layout = store.read(StorageKeys.MIDI_KEY_LAYOUT)

            if (loadout == null && layout == null) {
                return null
            }

            return DocumentStorage.Legacy(
                value = MidiDesk(loadout = loadout.orEmpty(), keyLayout = layout),
                // A loadout that could not be read stays where it is
                keys = listOfNotNull(
                    StorageKeys.MIDI_LOADOUT.takeIf { loadout != null },
                    StorageKeys.MIDI_KEY_LAYOUT.takeIf { layout != null },
                ),
                chosen = setOfNotNull("loadout".takeIf { loadout != null }, "keyLayout".takeIf { layout != null }),
            )
        }
    }

    /** The play code: the user's. */
    val play = Stored(
        key = StorageKeys.MIDI_PLAY,
        version = 1,
        serializer = MidiPlay.serializer(),
        default = MidiPlay(),
        store = stores.user,
        legacy = ::playBefore,
    )

    /** The loadout and the laptop keys' layout: this machine's. */
    val desk = Stored(
        key = StorageKeys.MIDI_DESK,
        version = 1,
        serializer = MidiDesk.serializer(),
        default = MidiDesk(),
        store = stores.machine,
        legacy = ::deskBefore,
    )

    internal val sections: List<Stored<*>> = listOf(play, desk)
}
