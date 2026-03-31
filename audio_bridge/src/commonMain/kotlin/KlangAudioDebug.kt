package io.peekandpoke.klang.audio_bridge

/**
 * Compile-time flag for audio thread debug checks.
 *
 * When false, the Kotlin compiler eliminates all guarded branches entirely — zero overhead.
 * Flip to true during development to surface warnings about unexpected states in audio
 * processing code (e.g., null buffers, unreachable branches).
 *
 * Future: when true, debug messages will be sent through the comm-link feedback channel
 * so they appear in the frontend UI without disrupting audio playback.
 */
// TODO: Wire up in M3 step — replace error("unreachable") calls in ExciterFm.kt and
//  ExciterPitchMod.kt with silent returns guarded by this flag.
const val KLANG_AUDIO_DEBUG = false
