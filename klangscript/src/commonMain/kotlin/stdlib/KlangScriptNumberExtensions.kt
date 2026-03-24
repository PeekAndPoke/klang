package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.StringValue

@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(NumberValue::class)
internal object KlangScriptNumberExtensions {

    /**
     * Returns the string representation of the number.
     *
     * @param self The number
     * @return The string representation
     * @category number
     */
    @KlangScript.Method(name = "toString")
    fun asString(self: NumberValue): StringValue = StringValue(self.toDisplayString())
}
