/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.runtime

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.builder.registerFunction
import io.peekandpoke.klang.script.builder.registerType
import io.peekandpoke.klang.script.klangScript

/** A registered, named type. */
interface Shape {
    fun describe(): String
}

/** A named implementation — the easy case. */
class NamedShape : Shape {
    override fun describe(): String = "named"
}

/**
 * Type-name resolution for error messages.
 *
 * Sprudel builds patterns from 21 different `object : SprudelPattern { ... }` expressions. An
 * anonymous class has no `simpleName` on any platform, so the reported type used to degrade to
 * `Unknown`, which tells a user nothing about what they called the method on. The registered
 * supertype is reachable from the anonymous class, so that is what should be reported.
 *
 * Runs on JVM AND JS deliberately: `simpleName` availability differs between the two.
 */
class DisplayTypeNameSpec : StringSpec({

    fun engine() = klangScript {
        registerFunction<Int, Shape>("namedShape") { NamedShape() }
        // Anonymous implementation, exactly the sprudel pattern idiom.
        registerFunction<Int, Shape>("anonShape") {
            object : Shape {
                override fun describe(): String = "anon"
            }
        }
        registerType<Shape> {
            registerMethod("describe") { describe() }
        }
    }

    "a NAMED receiver reports its own class name" {
        val e = shouldThrow<KlangScriptTypeError> {
            engine().execute("""namedShape(1).nope()""")
        }
        e.message shouldContain "NamedShape"
    }

    "an ANONYMOUS receiver reports its registered supertype, not 'Unknown'" {
        // The whole point. Before this, sprudel's anonymous patterns produced
        // "Native type 'Unknown' has no method 'ocsp'", which named nothing the user wrote.
        val e = shouldThrow<KlangScriptTypeError> {
            engine().execute("""anonShape(1).nope()""")
        }
        e.message shouldContain "Shape"
    }

    "an anonymous receiver still gets its methods listed and suggested" {
        // A degraded type name also broke the method lookup's usefulness: if the name is
        // Unknown the reader gets no hint at all. Suggestions must survive.
        val e = shouldThrow<KlangScriptTypeError> {
            engine().execute("""anonShape(1).describ()""")
        }
        e.message shouldContain "'describe'"
    }
})
