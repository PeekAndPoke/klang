package io.peekandpoke.klang.workshop.storage

import io.peekandpoke.ultra.streams.Unsubscribe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * A data class kept as one [StoredDocument] under one [key] of a [KeyValueStore], for a stream ([persist]).
 *
 * - **A write is a merge.** Only the top-level fields that changed since this storage last read or wrote are put
 *   over the document the store holds now ([changedFields], [mergeFields]). So two writers that change different
 *   fields both keep theirs (two tabs, two computers), and a field a writer does not know (an older build meeting a
 *   newer document) stays as it is. Two writers of one field: the later one wins.
 * - **What cannot be read is never lost.** A document that does not decode gives no value, and the text as it was is
 *   put aside first, under the key with [UNREADABLE] after it. One that this build reads only in part (a higher
 *   version, a value a field could not take, a field inside a nested object it does not know) is put aside under the
 *   key with [NEWER] after it. Where the copy cannot be kept, nothing is written over the document in this run.
 *   A field this build has and the document lacks is no such case: it takes its default.
 * - **What was kept before this document existed** is read once by [legacy]: its value becomes the first document,
 *   and the old keys go when that document is kept.
 * - **A change from elsewhere** is told to the stream ([watch]) unless [follows] is false.
 *
 * **What it does not do** (the maintainer, 2026-11-10: the app will ask for a reload when a newer version is out, so
 * these meet rarely and are accepted). A field cannot be removed by a write, only set: a value that leaves out a
 * field leaves the kept one, so the fields of old builds pile up. A removal elsewhere is not followed.
 *
 * **A new document holds only what was chosen:** the fields that differ from what an empty document reads as (the
 * class's own defaults). A field nobody chose is not kept, so a default of the class that changes later reaches
 * everyone who never chose; and a field left out is one that reads back the same. That holds for defaults that are
 * constants: a default that follows another field, or the moment, reads back as it computes then. A class with a
 * field that has no default cannot be read from an empty document: its new document holds every field.
 *
 * Every value read passes [clean] (a number held in its range). The [version] is written and not yet acted upon: a
 * field added later needs none (it takes its default). Until the first migration is written, a field whose type
 * changes gets a new name.
 */
class DocumentStorage<T>(
    val key: String,
    private val version: Int,
    private val serializer: KSerializer<T>,
    private val store: KeyValueStore,
    private val clean: (T) -> T = { it },
    private val legacy: (KeyValueStore) -> Legacy<T>? = { null },
    private val follows: Boolean = true,
    /** Told what went wrong, for the app's log: a read gives no value and a write keeps nothing, neither throws. */
    private val onProblem: (message: String, cause: Throwable?) -> Unit = { _, _ -> },
) : WatchedStreamStorage<T> {

    /**
     * What was kept before the document existed: its [value], and the old [keys] to forget once it is kept anew.
     * [chosen] names the top-level fields that were kept before: they go into the document also where they equal the
     * default, a choice staying a choice.
     */
    class Legacy<T>(val value: T, val keys: List<String>, val chosen: Set<String> = emptySet())

    companion object {
        /** After the key: a document that could not be read, as it was. */
        const val UNREADABLE = "-unreadable"

        /** After the key: a document read only in part, as it was, before this build wrote over it. */
        const val NEWER = "-newer"
    }

    private val json = StoredDocument.json
    private val document = StoredDocument.serializer(serializer)

    /** The data's fields as this storage last read or wrote them; null before that, or when the data is no object. */
    private var known: JsonObject? = null

    /** A document could not be put aside: nothing is written over it in this run. */
    private var frozen = false

    /** The document under the key could not be read (and is put aside): the next write starts a new one. */
    private var unreadable = false

    /** What an empty document reads as, encoded; no field at all where it cannot be read (a field without a default). */
    private val empty: JsonObject by lazy {
        try {
            json.encodeToJsonElement(serializer, json.decodeFromJsonElement(serializer, JsonObject(emptyMap()))) as? JsonObject
        } catch (_: Throwable) {
            null
        } ?: JsonObject(emptyMap())
    }

    override fun read(): T? {
        val text = store.read(key)

        if (text != null) {
            return decode(text)
        }

        val old = try {
            legacy(store)
        } catch (e: Throwable) {
            onProblem("Could not read what was kept before $key", e)
            null
        }

        if (old == null) {
            return null
        }

        val value = clean(old.value)

        // The old keys go only once the new document is kept: nothing is lost when it cannot be
        if (keep(value = value, chosen = old.chosen)) {
            old.keys.forEach { store.remove(it) }
        }

        return value
    }

    override fun write(value: T) {
        keep(value = value, chosen = emptySet())
    }

    override fun watch(onChange: (T) -> Unit): Unsubscribe {
        if (!follows) {
            return {}
        }

        return store.watch(key) { text ->
            // Removed elsewhere: this storage cannot say what the value is now, the stream keeps what it shows
            if (text != null) {
                decode(text)?.let(onChange)
            }
        }
    }

    /** Whether anything is kept under the key now, readable or not: false for a section nobody wrote yet. */
    val holds: Boolean get() = store.read(key) != null

    /** Forgets the document: nothing is kept under the key. */
    fun forget() {
        store.remove(key)
        known = null
        frozen = false
        unreadable = false
    }

    private fun decode(text: String): T? = try {
        val kept = json.decodeFromString(document, text)
        val asKept = (json.parseToJsonElement(text) as JsonObject)["data"]
        val asRead = json.encodeToJsonElement(serializer, kept.data)

        if (kept.v > version || readInPart(asKept = asKept, asRead = asRead, top = true)) {
            onProblem("$key was kept by a newer build, or holds a value this one cannot take: put aside as $key$NEWER", null)
            putAside(suffix = NEWER, text = text)
        }

        val value = clean(kept.data)

        // As cleaned: what cleaning changed is no change of this writer's, and is not written with its next one
        known = json.encodeToJsonElement(serializer, value) as? JsonObject
        unreadable = false

        value
    } catch (e: Throwable) {
        onProblem("Could not read $key: put aside as $key$UNREADABLE", e)
        putAside(suffix = UNREADABLE, text = text)

        unreadable = true

        null
    }

    /**
     * Whether [asKept] holds something that [asRead] lost or changed: a value coerced to a default, a field inside a
     * nested object this build does not know. Not: a field of the [top] level it does not know (a write leaves it
     * alone), a field the kept text lacks (it took its default), a number written another way (`2` for `2.0`).
     */
    private fun readInPart(asKept: JsonElement?, asRead: JsonElement, top: Boolean): Boolean = when {
        asKept == null -> false

        asKept is JsonObject && asRead is JsonObject -> asKept.any { (name, value) ->
            when (val read = asRead[name]) {
                null -> !top
                else -> readInPart(asKept = value, asRead = read, top = false)
            }
        }

        asKept is JsonArray && asRead is JsonArray ->
            asKept.size != asRead.size || asKept.indices.any { readInPart(asKept = asKept[it], asRead = asRead[it], top = false) }

        asKept is JsonPrimitive && asRead is JsonPrimitive -> !sameValue(a = asKept, b = asRead)

        else -> true
    }

    private fun sameValue(a: JsonPrimitive, b: JsonPrimitive): Boolean {
        if (a.isString != b.isString) {
            return false
        }

        if (a.content == b.content) {
            return true
        }

        val number = a.doubleOrNull

        return !a.isString && number != null && number == b.doubleOrNull
    }

    private fun putAside(suffix: String, text: String) {
        if (!store.write(key = key + suffix, text = text)) {
            frozen = true
            onProblem("Could not put $key aside: it is not written over in this run", null)
        }
    }

    private fun keep(value: T, chosen: Set<String>): Boolean {
        if (frozen) {
            return false
        }

        return try {
            merge(value = value, chosen = chosen)
        } catch (e: Throwable) {
            // A value that cannot be encoded: the app goes on with it, unkept
            onProblem("Could not keep $key", e)
            false
        }
    }

    private fun merge(value: T, chosen: Set<String>): Boolean {
        val after = json.encodeToJsonElement(serializer, value)
        val kept = try {
            store.read(key)?.takeUnless { unreadable }?.let { json.parseToJsonElement(it) as? JsonObject }
        } catch (_: Throwable) {
            null
        }
        val keptData = kept?.get("data") as? JsonObject
        val keptVersion = (kept?.get("v") as? JsonPrimitive)?.intOrNull ?: version

        // What this writer changed is measured from what it last read or wrote; with no document there, from what an
        // empty one reads as, so a new document holds only what was chosen
        val before = known?.takeIf { keptData != null }
            ?: JsonObject(empty - chosen)

        val data = when {
            after is JsonObject ->
                mergeFields(kept = keptData ?: JsonObject(emptyMap()), patch = changedFields(before = before, after = after))

            // No object: the whole value
            else -> after
        }

        val written = store.write(
            key = key,
            text = JsonObject(mapOf("v" to JsonPrimitive(maxOf(version, keptVersion)), "data" to data)).toString(),
        )

        if (written) {
            known = after as? JsonObject
            unreadable = false
        }

        return written
    }
}
