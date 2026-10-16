/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.ksp

/**
 * One registered class (an `@Object`, a `@TypeExtensions` class, or the receiver of top-level extension functions)
 * as far as the source files are concerned: the class's own file, null when it lives in another module or is a
 * platform type, and the files of the functions registered on it.
 */
internal data class SourceRefs<F>(val classFile: F?, val methodFiles: List<F?>)

/**
 * The source files of a library's aggregating outputs, in first-seen order, each once. A top-level extension
 * function's entry carries its RECEIVER as the class, which may have no file here (`audio_bridge`'s builders, a
 * `String`), so every type extension's method files count too. Left out, an incremental KSP run that does not dirty
 * such a file drops its registrations from the regenerated outputs (found in the typed-doors rollout S6; the gap
 * predates it and also covered sprudel's `lang_structural_chunk.kt`).
 */
internal fun <F : Any> collectSourceFiles(
    objects: List<SourceRefs<F>>,
    typeExtensions: List<SourceRefs<F>>,
    functionFiles: List<F?>,
    propertyFiles: List<F?>,
): List<F> = buildList {
    objects.forEach { addAll(listOfNotNull(it.classFile)) }
    typeExtensions.forEach { addAll(listOfNotNull(it.classFile)) }
    typeExtensions.forEach { addAll(it.methodFiles.filterNotNull()) }
    addAll(functionFiles.filterNotNull())
    addAll(propertyFiles.filterNotNull())
}.distinct()
