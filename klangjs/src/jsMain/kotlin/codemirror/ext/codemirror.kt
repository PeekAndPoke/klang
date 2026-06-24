/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("PropertyName", "unused")

package io.peekandpoke.klang.codemirror.ext

/**
 * State module interface
 */
external interface StateModule {
    val EditorState: dynamic
}

/**
 * View module interface
 */
external interface ViewModule {
    val EditorView: dynamic
}

/**
 * Commands module interface
 */
external interface CommandsModule

/**
 * Language module interface
 */
external interface LanguageModule

/**
 * JavaScript language module interface
 */
external interface JavaScriptModule {
    fun javascript(config: dynamic = definedExternally): dynamic
}

/**
 * Basic setup module interface
 */
external interface BasicSetupModule {
    val basicSetup: Array<Extension>  // basicSetup is an array of extensions
}
