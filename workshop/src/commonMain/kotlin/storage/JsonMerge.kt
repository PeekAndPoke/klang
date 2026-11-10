package io.peekandpoke.klang.workshop.storage

import kotlinx.serialization.json.JsonObject

/**
 * The top-level fields of [after] that differ from [before], or that [before] lacks: what a change touched. A field
 * that is itself an object counts as one field: changed anywhere inside, it is in the patch whole.
 */
fun changedFields(before: JsonObject, after: JsonObject): JsonObject =
    JsonObject(after.filter { (name, value) -> before[name] != value })

/**
 * [kept] with every field of [patch] put over it. A field the patch does not name stays as it is: also one the writer
 * of the patch does not know.
 */
fun mergeFields(kept: JsonObject, patch: JsonObject): JsonObject = JsonObject(kept + patch)
