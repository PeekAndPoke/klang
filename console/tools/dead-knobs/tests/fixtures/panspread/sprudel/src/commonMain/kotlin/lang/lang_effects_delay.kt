package io.peekandpoke.klang.sprudel.lang

private val delayMutation = voiceSetter { wet ->
    katalystParamsOrNew().set("delay.wet", wet)
}
