package io.peekandpoke.samples

/** What the pattern asks for (bank + sound + optional variant index). */
data class SampleRequest(
    /** Name of the requested bank ... null means default sounds */
    val bank: String?,
    /** Name of the requested sound */
    val sound: String?,
    /** Index of the requested variant (if any) */
    val index: Int?,
    /** Note at which the sample would be played. Helps to find the best sample. */
    val note: String?,
)
