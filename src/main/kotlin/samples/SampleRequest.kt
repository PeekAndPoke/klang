package io.peekandpoke.samples

/** What the pattern asks for (bank + sound + optional variant index). */
data class SampleRequest(
    val bank: String?,
    val sound: String?,
    val index: Int?,
)
