/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

/**
 * Stable wire discriminator name for a sealed leaf in the audio-worklet wire protocol.
 *
 * The `:audio-wire-codec-ksp` processor stamps each sealed subtype with a string type-tag (`t`) instead of a
 * positional ordinal, so inserting/reordering subtypes mid-hierarchy can never silently re-route a tag. Put
 * `@WireName` on every concrete leaf of a `@WireFormat`-reachable sealed type; the value is the tag that
 * crosses the wire. Without it the processor falls back to the simple class name (rename-fragile) — so prefer
 * an explicit name on every wire leaf.
 *
 * Names must be unique within a single sealed hierarchy (the processor fails the build on a collision).
 *
 * `SOURCE` retention — purely a compile-time marker for the processor, never present at runtime.
 * See `docs/tasks/wireformat-enhancements.md`.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class WireName(val name: String)
