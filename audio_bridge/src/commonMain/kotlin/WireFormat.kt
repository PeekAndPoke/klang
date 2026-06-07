package io.peekandpoke.klang.audio_bridge

/**
 * Marks a type as part of the audio-worklet wire protocol.
 *
 * The `:audio-wire-codec-ksp` processor generates a fast "trust the input" JS-object encode/decode for each
 * annotated type (plus its transitive graph), which `WorkletContract` uses instead of kotlinx
 * `encodeToDynamic` / `decodeFromDynamic`. Annotate the protocol ROOTS (the `KlangCommLink.Cmd` /
 * `KlangCommLink.Feedback` sealed types, `ScheduledVoice`); the processor walks referenced types from there.
 *
 * `SOURCE` retention — purely a compile-time marker for the processor, never present at runtime.
 * See `docs/tasks/worklet-codec-ksp.md`.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class WireFormat
