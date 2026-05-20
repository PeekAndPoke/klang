package io.peekandpoke.klang.audio_bridge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Authoring-layer representation of a sound reference.
 *
 * A voice may select its sound either by name (looking up a sample bank or
 * a pre-registered ignitor) or by inlining an [IgnitorDsl] tree directly.
 *
 * At the playback → wire boundary, [Osc] is denormalized to a stable synthetic
 * name — the playback context allocates one via `registerIgnitor` — so the
 * wire-level [VoiceData] still carries `sound: String?` and the BE protocol
 * stays unchanged.
 */
@Serializable
sealed interface SoundValue {

    /** Sound referenced by a stable name (sample bank entry, pre-registered ignitor, etc.). */
    @Serializable
    @SerialName("named")
    data class Named(val name: String) : SoundValue

    /** Sound defined inline as an ignitor signal graph. */
    @Serializable
    @SerialName("osc")
    data class Osc(val osc: IgnitorDsl) : SoundValue
}
