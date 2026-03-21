package io.peekandpoke.klang.strudel.compat

import io.peekandpoke.klang.strudel.StrudelPatternEvent

data class Example(
    val name: String,
    val code: String,
    val skip: Boolean = false,
    val ignoreFields: Set<String> = emptySet(),
    val recover: Map<String, (graal: StrudelPatternEvent, native: StrudelPatternEvent) -> Boolean> = emptyMap(),
) {
    companion object {
        operator fun invoke(name: String, code: String) = Example(name = name, code = code)

        operator fun invoke(skip: Boolean, name: String, code: String) =
            Example(name = name, code = code, skip = true)
    }

    fun ignore(ignore: Set<String>) = copy(ignoreFields = ignoreFields + ignore)

    fun ignore(vararg ignore: String) = ignore(ignore.toSet())

    fun tryRecover(field: String, graal: StrudelPatternEvent, native: StrudelPatternEvent): Boolean {
        val fn: (StrudelPatternEvent, StrudelPatternEvent) -> Boolean =
            recover.getOrDefault(field, { _, _ -> false })

        return fn(graal, native)
    }

    fun recovers(field: String, fn: (graal: StrudelPatternEvent, native: StrudelPatternEvent) -> Boolean): Example {
        val current: (StrudelPatternEvent, StrudelPatternEvent) -> Boolean =
            recover.getOrDefault(field, { _, _ -> false })

        val updated: (StrudelPatternEvent, StrudelPatternEvent) -> Boolean = { graal, native ->
            fn(graal, native) || current(graal, native)
        }

        return copy(
            recover = recover.plus(field to updated)
        )
    }
}
