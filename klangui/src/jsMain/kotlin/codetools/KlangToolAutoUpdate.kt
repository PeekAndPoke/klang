package io.peekandpoke.klang.ui.codetools

import de.peekandpoke.ultra.streams.Stream
import de.peekandpoke.ultra.streams.StreamSource
import de.peekandpoke.ultra.streams.Unsubscribe
import de.peekandpoke.ultra.streams.ops.persistInLocalStorage
import kotlinx.serialization.builtins.serializer

object KlangToolAutoUpdate : Stream<Boolean> {
    private val source = StreamSource(true)
        .persistInLocalStorage("klang-tool-auto-update", Boolean.serializer())

    override fun invoke(): Boolean = source()

    override fun subscribeToStream(sub: (Boolean) -> Unit): Unsubscribe = source.subscribeToStream(sub)

    fun toggle() {
        source(!source())
    }
}
