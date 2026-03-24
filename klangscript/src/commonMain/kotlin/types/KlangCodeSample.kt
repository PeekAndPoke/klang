package io.peekandpoke.klang.script.types

/**
 * The kind of code sample — determines how the frontend renders it.
 */
enum class KlangCodeSampleType {
    /** Music example with play/stop controls (used by sprudel DSL functions). */
    PLAYABLE,

    /** Script example with run button and output panel (used by stdlib, klangscript). */
    EXECUTABLE,
}

/**
 * A code sample attached to a [KlangDecl].
 *
 * @param code The KlangScript source code
 * @param type How the frontend should render this sample
 */
data class KlangCodeSample(
    val code: String,
    val type: KlangCodeSampleType = KlangCodeSampleType.PLAYABLE,
)
