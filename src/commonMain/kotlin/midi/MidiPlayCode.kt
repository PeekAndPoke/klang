/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

import io.peekandpoke.klang.script.ast.Argument
import io.peekandpoke.klang.script.ast.ArrowFunction
import io.peekandpoke.klang.script.ast.ArrowFunctionBody
import io.peekandpoke.klang.script.ast.AstIndex
import io.peekandpoke.klang.script.ast.ConstDeclaration
import io.peekandpoke.klang.script.ast.ExpressionStatement
import io.peekandpoke.klang.script.ast.Identifier
import io.peekandpoke.klang.script.ast.LetDeclaration
import io.peekandpoke.klang.script.ast.Program
import io.peekandpoke.klang.script.ast.StringLiteral
import io.peekandpoke.klang.script.ast.lastCallInChain
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.storage.MidiInstrument

/**
 * The MIDI play code (KLANG-00402, D7: "should all be in the one input"): one KlangScript input written like a song.
 * Its top-level `let`s hold the instruments (Ignitors), and it ends in a function from the key's one-note pattern to what
 * sounds, `x => x.sound(warmSaw).reverb(wet = 0.2)`; a built-in is a name in quotes, `.sound("supersaw")`. Here the
 * text work on it, with no engine: the names it binds ([namesOf]), the instrument a key plays ([soundOf]: the last
 * `.sound(...)` of the function's own chain) and the code with another one there ([withSound]); and an old save as one
 * play code ([fromOld]).
 */
object MidiPlayCode {

    /** What a `.sound(...)` plays: one of the play code's own `let`s, or a built-in by its name. */
    sealed interface Sound {
        /** As the code writes it: the name, or the built-in's name in quotes. */
        val code: String

        data class Own(val name: String) : Sound {
            override val code: String get() = name
        }

        data class BuiltIn(val name: String) : Sound {
            override val code: String get() = "\"$name\""
        }
    }

    /** The play code a first visit starts with: the warm saw the page always had, with its reverb. */
    val DEFAULT: String = """
        |let warmSaw = Ign.saw()
        |  .plus(Ign.sine().detune(12).mul(0.3))
        |  .lowpass(1800)
        |  .adsr(0.01, 0.3, 0.6, 0.4)
        |
        |x => x.sound(warmSaw).reverb(wet = 0.2)
        |""".trimMargin()

    /** The built-in a play code plays where it names no instrument. */
    const val FALLBACK_SOUND = "supersaw"

    /** The names [code] binds at its top level (`let`, `const`), in order; none when it does not parse. */
    fun namesOf(code: String): List<String> = parsed(code)?.statements.orEmpty().mapNotNull { statement ->
        when (statement) {
            is LetDeclaration -> statement.name
            is ConstDeclaration -> statement.name
            else -> null
        }
    }

    /**
     * The instrument a key plays through [code]: the last `.sound(...)` of the function's own chain (in a chain the last
     * one wins; one in a `let` before, or inside an argument, is not the chain's), where its argument is a name or a
     * string; else null.
     */
    fun soundOf(code: String): Sound? = playedSoundArgument(code)?.let { (value, _) ->
        when (value) {
            is Identifier -> Sound.Own(value.name)
            is StringLiteral -> Sound.BuiltIn(value.value)
            else -> null
        }
    }

    /**
     * [code] with [sound] in the `.sound(...)` a key plays ([soundOf]); null where it has none whose argument is a single
     * name or a string (an expression there is the user's to change).
     */
    fun withSound(code: String, sound: Sound): String? {
        val (_, range) = playedSoundArgument(code) ?: return null

        return code.replaceRange(range, sound.code)
    }

    /**
     * An old save (the page's draft and its saved instruments, before the one input) as one play code (D7: "An old
     * save loads once as one play code"): each instrument with an Ignitor a `let`, the draft's last; the draft's mapper
     * with `.sound(<its let>)` in front, or a built-in where the draft had no Ignitor. It always parses: an Ignitor or
     * a mapper that would not read as part of it is kept as a comment, to bring back by hand; so is a saved instrument's
     * own mapper.
     */
    fun fromOld(draft: MidiInstrument?, saved: List<MidiInstrument>): String {
        val mapper = draft?.mapperCode?.trim().orEmpty()
        // `x => x.reverb(...)` (also `(x) =>`, the dot on the next line) keeps its body after the sound, under its own
        // parameter's name (the body may use it again); `reverb(...)`, a mapper value, is chained on
        val arrow = Regex("""^\(?(\w+)\)?\s*=>\s*\1\s*\.([\s\S]+)$""").find(mapper)
        // The function's parameter is no let's name: inside it, it is the key's pattern
        val names = mutableListOf(arrow?.groupValues?.get(1) ?: "x")
        val lets = mutableListOf<String>()

        /** The instrument's `let` and its name; null where its Ignitor does not read as one expression (kept as a comment). */
        fun letFor(instrument: MidiInstrument): String? {
            val name = identifierOf(instrument.name, taken = names)
            val let = "let $name = ${instrument.ignitorCode.trim()}"

            if (parsed(let)?.statements?.size != 1) {
                lets.add(commented(what = "${instrument.name.trim()}'s Ignitor", code = instrument.ignitorCode))

                return null
            }

            names.add(name)
            lets.add(let)

            return name
        }

        // A saved instrument's mapper was its own: kept as a comment under its let, to bring back by hand
        saved.filter { it != draft }.forEach { instrument ->
            if (instrument.ignitorCode.isNotBlank()) {
                letFor(instrument)
            }

            if (instrument.mapperCode.isNotBlank()) {
                lets.add(commented(what = "${instrument.name.trim()}'s mapper", code = instrument.mapperCode))
            }
        }

        val sound = draft?.takeIf { it.ignitorCode.isNotBlank() }?.let(::letFor)?.let { Sound.Own(it) } ?: Sound.BuiltIn(FALLBACK_SOUND)
        val plain = "x => x.sound(${sound.code})"

        val function = when {
            mapper.isEmpty() -> plain
            arrow != null -> arrow.groupValues[1].let { p -> "$p => $p.sound(${sound.code}).${arrow.groupValues[2]}" }
            else -> "$plain.$mapper"
        }
        // A mapper that does not read so (`x => stack(x, x.add(7))`): the plain sound, the old one a comment
        val readable = parsed(function)?.statements?.size == 1

        val ending = if (readable) listOf(function) else listOf(commented(what = "The old mapper", code = mapper), plain)

        return (lets + ending).joinToString("\n\n") + "\n"
    }

    /** Code kept as a comment, each line, under what it was. */
    private fun commented(what: String, code: String): String =
        (listOf("// $what, to bring back by hand:") + code.trim().lines().map { "// $it" }).joinToString("\n")

    /**
     * Names a `let` must not take: the language's words, the function's own parameter, and sprudel's sound names a
     * `let` would hide.
     */
    private val RESERVED = setOf(
        "if", "else", "for", "while", "do", "in", "of", "let", "const", "function", "return", "true", "false", "null",
        "import", "export", "from", "as", "new", "break", "continue", "x",
        "sine", "saw", "sawtooth", "square", "triangle", "tri", "noise", "sound", "note", "n", "s",
    )

    /** An instrument's name as a `let`'s: "Warm saw" is `warmSaw`; one taken already gets a number. */
    private fun identifierOf(name: String, taken: List<String>): String {
        val words = name.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
        val camel = words.mapIndexed { i, word ->
            if (i == 0) word.replaceFirstChar { it.lowercase() } else word.replaceFirstChar { it.uppercase() }
        }.joinToString("")
        // A name begins with a letter
        val word = camel.takeIf { it.firstOrNull()?.isLetter() == true } ?: "instrument${camel.replaceFirstChar { it.uppercase() }}"
        // "If" or "Sine" would be the language's word or hide a sound: an instrument's name
        val base = if (word in RESERVED) "${word}Instrument" else word

        return generateSequence(1) { it + 1 }.map { n -> if (n == 1) base else "$base$n" }.first { it !in taken }
    }

    /**
     * The first positional argument of the `.sound(...)` a key plays and where it is written: the function's own chain
     * (`x => x.sound(lead).reverb(...)`, the program's last statement), its last `.sound(...)`; a name or a string only.
     * Null without one.
     */
    private fun playedSoundArgument(code: String): Pair<Any, IntRange>? {
        val program = parsed(code) ?: return null
        val function = (program.statements.lastOrNull() as? ExpressionStatement)?.expression as? ArrowFunction ?: return null
        val chain = (function.body as? ArrowFunctionBody.ExpressionBody)?.expression ?: return null
        // `s` is sprudel's alias of `sound`
        val call = chain.lastCallInChain("sound", "s") ?: return null
        val value = (call.arguments.firstOrNull() as? Argument.Positional)?.value ?: return null
        val range = AstIndex.build(program, code).offsetOf(value) ?: return null

        return when (value) {
            is Identifier, is StringLiteral -> value to range
            else -> null
        }
    }

    private fun parsed(code: String): Program? = try {
        KlangScriptParser.parse(code)
    } catch (e: Exception) {
        null
    }
}
