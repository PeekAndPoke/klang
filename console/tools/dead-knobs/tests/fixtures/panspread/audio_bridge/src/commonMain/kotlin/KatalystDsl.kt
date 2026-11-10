package io.peekandpoke.klang.audio_bridge

data class KatalystDsl(val stages: List<Any>) {
    object Slots {
        val delay: KatalystDelaySlots = KatalystDelaySlots()
    }

    companion object {
        val classic: KatalystDsl = KatalystDsl(listOf(Slots.delay.wet))
    }
}
