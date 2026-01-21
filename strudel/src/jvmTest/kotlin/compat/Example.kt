package io.peekandpoke.klang.strudel.compat

import io.peekandpoke.klang.strudel.StrudelPatternEvent

data class Example(
    val name: String,
    val code: String,
    val skip: Boolean = false,
    val ignoreFields: Set<String> = emptySet(),
    val recover: (graal: StrudelPatternEvent, native: StrudelPatternEvent) -> Boolean = { _, _ -> false },
) {
    companion object {
        operator fun invoke(name: String, code: String) = Example(name = name, code = code)

        operator fun invoke(skip: Boolean, name: String, code: String) =
            Example(name = name, code = code, skip = true)
    }

    fun ignore(ignore: Set<String>) = copy(ignoreFields = ignoreFields + ignore)

    fun ignore(vararg ignore: String) = ignore(ignore.toSet())

    fun recover(recover: (graal: StrudelPatternEvent, native: StrudelPatternEvent) -> Boolean) =
        copy(
            recover = { graal, native ->
                recover(graal, native) || this.recover(graal, native)
            }
        )
}
