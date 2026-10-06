/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.runtime

/**
 * The default thunk of an optional parameter whose Kotlin default is `null`. One shared instance
 * instead of one `{ wrapAsRuntimeValue(null) }` lambda per generated parameter spec.
 *
 * It lives alone in this file on purpose. A top-level property with an initializer makes Kotlin/JS
 * start EVERY function of its file with an `_init_properties_...()` call; in `NativeInterop.kt` that
 * would hit the hottest interop functions on every door call. (An `object` implementing the function
 * type is not an option: Kotlin/JS forbids implementing a function interface.)
 */
val nullDefault: () -> RuntimeValue = { NullValue }
