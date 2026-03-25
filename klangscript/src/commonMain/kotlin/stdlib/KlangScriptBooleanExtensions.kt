package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries
import io.peekandpoke.klang.script.runtime.BooleanValue
import io.peekandpoke.klang.script.runtime.StringValue

@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(BooleanValue::class)
internal object KlangScriptBooleanExtensions {

    /**
     * Returns the string representation of the boolean.
     *
     * @param self The boolean
     * @return "true" or "false"
     * @category boolean
     */
    @KlangScript.Method(name = "toString")
    fun asString(self: BooleanValue): StringValue = StringValue(self.toDisplayString())
}
