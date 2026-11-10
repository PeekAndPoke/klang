package io.peekandpoke.klang.workshop.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A value as it is kept: the version [v] of its shape and its [data], one JSON document, with the one [json] that
 * writes and reads it. A browser and a server keep the same text:
 *
 * ```json
 * {"v": 1, "data": {"highlightsOn": true, "highlightsPerEvent": 25}}
 * ```
 */
@Serializable
class StoredDocument<T>(val v: Int, val data: T) {

    companion object {
        /**
         * How every document is written and read. Every field is written, so the text says all there is. On reading,
         * a field the document has and the class does not is left out, and a value a field cannot take (an enum entry
         * this build does not know, a null where none may be) gives way to the field's default, so one such value does
         * not cost the whole document.
         */
        val json: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }
    }
}
