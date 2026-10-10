/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

/**
 * Marks a wire type whose instances keep their IDENTITY across the audio-worklet wire: a value referenced twice in one
 * message arrives as one object, not as two equal copies.
 *
 * It matters where the receiver shares by identity. The backend builds an [IgnitorDsl] tree through an identity-keyed
 * cache, so `let s = ...; s + s.shimmer()` is one instance on the JVM; without this marker the browser received two
 * equal objects and built two instances (two random phase sets, two drifts, twice the CPU). Found 2026-10-10
 * (`docs/tasks/in-progress/parallel-serial-bands.md`), decided with the maintainer the same day.
 *
 * How the generated codec keeps it: while a `@WireFormat` root is encoded or decoded, the codec of a marked type
 * remembers what it made per instance, so a second reference to the same instance gets the same JS object on encode
 * and the same Kotlin object on decode. `postMessage`'s structured clone keeps shared references within one message,
 * so the sharing survives the hop between the two. Two EQUAL but distinct instances stay two: identity is kept,
 * nothing is interned. The tables live for one root call only (`wireScoped`, `audio_bridge/jsMain/WireCodecSupport.kt`).
 *
 * **Which types get it:** a type whose RECEIVER tells "the same instance twice" apart from "two equal instances", which
 * means it builds or keeps something per instance by identity (the Ignitor build cache keys by `===`). Today that is
 * [IgnitorDsl] alone. Not the others: the Katalyst chain builder builds a fresh stage per list position anyway, and a
 * voice's data is read once. Marking every type would be harmless for correctness (all wire types are immutable) but
 * would put a table lookup per object, and a JS `Map` per message, on the hottest path, the worklet's per-voice
 * decode, which allocates nothing today. A field must name the marked type itself, not a subtype: the processor
 * refuses the subtype (it would skip the table).
 *
 * `SOURCE` retention: purely a compile-time marker for the `:audio-wire-codec-ksp` processor.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class WireShared
