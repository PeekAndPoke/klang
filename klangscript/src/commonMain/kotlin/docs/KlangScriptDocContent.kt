package io.peekandpoke.klang.script.docs

data class DocSection(
    val title: String,
    val description: String,
    val examples: List<DocExample>,
)

/**
 * Indicates how cleanly a KlangScript example maps onto vanilla JavaScript.
 *
 * The label is a reading aid for people coming from JS — it answers
 * "can I copy this snippet into a plain JS REPL and expect it to behave the
 * same way?". The `import * from "stdlib"` line is treated as a REPL
 * convention (the REPL auto-imports it) and does not by itself mark an
 * example incompatible — the classification is based on the language
 * features and stdlib methods the example actually demonstrates.
 */
enum class JsCompat {
    /** Works the same in vanilla JS (modulo the optional stdlib import line). */
    Compatible,

    /** Uses KlangScript-only syntax or Kotlin-style stdlib methods. */
    Incompatible,
}

data class DocExample(
    val title: String? = null,
    val description: String? = null,
    val code: String,
    val jsCompat: JsCompat,
)

val klangScriptDocSections: List<DocSection> = listOf(

    // ==========================================
    // 1. Hello KlangScript
    // ==========================================
    DocSection(
        title = "Hello KlangScript",
        description = "Welcome to KlangScript! The REPL shows the result of your last expression automatically. Try editing the code and hitting Run.",
        examples = listOf(
            DocExample(
                title = "Your first expression",
                description = "The simplest program is just an expression. The REPL shows its result automatically — no print statement needed.",
                code = """
                    |// Just write an expression. The REPL displays the result.
                    |2 + 3
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
            DocExample(
                title = "console.log for multiple values",
                description = "Use console.log() when you want to print multiple values or label your output.",
                code = """
                    |import * from "stdlib"
                    |
                    |console.log("result:", 6 * 7)
                    |console.log("Hello,", "KlangScript!")
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
            DocExample(
                title = "print() — KlangScript shorthand",
                description = "KlangScript also exposes a shorter `print()` for quick logging. JavaScript has no global `print()`.",
                code = """
                    |import * from "stdlib"
                    |
                    |print("print() also works")
                """.trimMargin(),
                jsCompat = JsCompat.Incompatible,
            ),
            DocExample(
                title = "Comments",
                description = "Comments are ignored by the interpreter. Use them to explain your code or temporarily disable lines.",
                code = """
                    |import * from "stdlib"
                    |
                    |// This is a single-line comment
                    |console.log("This runs")
                    |
                    |/* This is a block comment.
                    |   It can span multiple lines. */
                    |
                    |// console.log("This is commented out — it won't run")
                    |console.log("Done!")
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
        ),
    ),

    // ==========================================
    // 2. Variables & Constants
    // ==========================================
    DocSection(
        title = "Variables & Constants",
        description = "Use `let` for values that change and `const` for values that don't. KlangScript supports numbers, strings, booleans, and null — no `undefined`.",
        examples = listOf(
            DocExample(
                title = "let and const",
                description = "Use let for values that change, const for values that stay fixed. Reassigning a const throws an error.",
                code = """
                    |import * from "stdlib"
                    |
                    |let bpm = 120
                    |const title = "My First Beat"
                    |
                    |console.log(title, "at", bpm, "BPM")
                    |
                    |bpm = 140
                    |console.log("Tempo changed to", bpm, "BPM")
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
            DocExample(
                title = "All value types",
                description = "KlangScript has numbers, strings, booleans, and null — just like JavaScript.",
                code = """
                    |import * from "stdlib"
                    |
                    |const tempo = 128
                    |const swing = 0.65
                    |const key = "A minor"
                    |const active = true
                    |let solo = null
                    |
                    |console.log(`Track: ${"$"}{key}, ${"$"}{tempo} BPM, swing: ${"$"}{swing}`)
                    |console.log("active:", active, "solo:", solo)
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
            DocExample(
                title = "No undefined — uninitialized is null",
                description = "Unlike JavaScript, there is no `undefined` value. An uninitialized `let` is `null`, not `undefined`.",
                code = """
                    |import * from "stdlib"
                    |
                    |let unset
                    |console.log("uninitialized let:", unset)
                """.trimMargin(),
                jsCompat = JsCompat.Incompatible,
            ),
            DocExample(
                title = "Number formats",
                description = "Numbers can be written in decimal, hexadecimal (0x), octal (0o), or binary (0b). They all evaluate to regular numbers.",
                code = """
                    |import * from "stdlib"
                    |
                    |const hex = 0xFF
                    |const octal = 0o77
                    |const binary = 0b1010
                    |
                    |console.log("0xFF =", hex)
                    |console.log("0o77 =", octal)
                    |console.log("0b1010 =", binary)
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
        ),
    ),

    // ==========================================
    // 3. Operators & Expressions
    // ==========================================
    DocSection(
        title = "Operators & Expressions",
        description = "Operators transform values. Arithmetic, comparison, logical, ternary, and assignment operators — plus increment/decrement and short-circuit evaluation.",
        examples = listOf(
            DocExample(
                title = "Arithmetic",
                description = "Standard math operators, plus ** for exponentiation and % for remainder.",
                code = """
                    |import * from "stdlib"
                    |
                    |const bpm = 120
                    |const beatLength = 60 / bpm
                    |console.log("Beat length:", beatLength, "seconds")
                    |
                    |console.log("2 ** 8 =", 2 ** 8)
                    |console.log("17 % 4 =", 17 % 4)
                    |console.log("Halftime:", bpm / 2, "BPM")
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
            DocExample(
                title = "Comparison and ternary",
                description = "Compare values and choose between options. The ternary ? : is a compact if/else expression.",
                code = """
                    |const velocity = 95
                    |const loud = velocity > 80
                    |const exact = velocity == 100
                    |const inRange = velocity >= 1 && velocity <= 127
                    |
                    |const label = velocity >= 90 ? "forte" : "piano"
                    |label
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
            DocExample(
                title = "Logical operators and short-circuit",
                description = "&& and || short-circuit: they stop evaluating as soon as the result is known. Use || for defaults. JS note: Falsy values are 0, \"\", null, and false. No undefined or NaN.",
                code = """
                    |import * from "stdlib"
                    |
                    |const hasKick = true
                    |const hasSnare = false
                    |console.log("both?", hasKick && hasSnare)
                    |console.log("either?", hasKick || hasSnare)
                    |console.log("no snare?", !hasSnare)
                    |
                    |// Short-circuit: use || for defaults
                    |const name = null || "default preset"
                    |console.log("Preset:", name)
                    |
                    |// Falsy values: 0, "", null, false
                    |console.log(0 || "zero is falsy")
                    |console.log("" || "empty string is falsy")
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
            DocExample(
                title = "Assignment operators and increment",
                description = "Compound assignment combines an operator with =. Prefix/postfix ++ and -- increment or decrement.",
                code = """
                    |import * from "stdlib"
                    |
                    |let vol = 50
                    |vol += 20
                    |console.log("after += 20:", vol)
                    |vol -= 10
                    |console.log("after -= 10:", vol)
                    |vol *= 2
                    |console.log("after *= 2:", vol)
                    |vol /= 4
                    |console.log("after /= 4:", vol)
                    |vol %= 7
                    |console.log("after %= 7:", vol)
                    |
                    |let step = 0
                    |console.log("prefix ++:", ++step)
                    |console.log("postfix ++:", step++)
                    |console.log("after postfix:", step)
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
        ),
    ),

    // ==========================================
    // 4. Nullish Coalescing & Optional Chaining
    // ==========================================
    DocSection(
        title = "Nullish Coalescing & Optional Chaining",
        description = "Handle null values cleanly. ?? provides fallbacks for null (not all falsy values), and ?. safely navigates through nullable chains.",
        examples = listOf(
            DocExample(
                title = "Nullish coalescing (??)",
                description = "?? returns the right side only when the left is null. Unlike ||, it doesn't trigger on 0 or empty strings. JS note: Same as JavaScript's ?? — returns fallback only for null, not all falsy values.",
                code = """
                    |import * from "stdlib"
                    |
                    |console.log(null ?? "default")
                    |console.log(0 ?? "default")
                    |console.log("" ?? "default")
                    |
                    |// Compare with ||
                    |console.log("|| with 0:", 0 || "fallback")
                    |console.log("?? with 0:", 0 ?? "fallback")
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
            DocExample(
                title = "Optional chaining (?.)",
                description = "?. safely accesses properties on values that might be null. Returns null instead of throwing an error.",
                code = """
                    |import * from "stdlib"
                    |
                    |const user = null
                    |console.log("user?.name:", user?.name)
                    |
                    |const config = { audio: { sampleRate: 48000 } }
                    |console.log("sample rate:", config?.audio?.sampleRate)
                    |
                    |const empty = { audio: null }
                    |console.log("missing:", empty?.audio?.sampleRate)
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
            DocExample(
                title = "Combining ?? and ?.",
                description = "Chain ?. for safe access, then ?? for a fallback. A clean pattern for nested optional data.",
                code = """
                    |const config = { audio: null }
                    |
                    |const rate = config?.audio?.sampleRate ?? 44100
                    |rate
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
        ),
    ),

    // ==========================================
    // 5. Bitwise & Shift Operators
    // ==========================================
    DocSection(
        title = "Bitwise & Shift Operators",
        description = "Work with individual bits using bitwise and shift operators. Useful for flags, masks, and low-level audio data.",
        examples = listOf(
            DocExample(
                title = "Bitwise basics",
                description = "Bitwise operators work on individual bits of integer values. & masks bits, | combines them, ^ toggles them.",
                code = """
                    |import * from "stdlib"
                    |
                    |console.log("0b1100 & 0b1010 =", 0b1100 & 0b1010)
                    |console.log("0b1100 | 0b1010 =", 0b1100 | 0b1010)
                    |console.log("0b1100 ^ 0b1010 =", 0b1100 ^ 0b1010)
                    |console.log("~0b1010 =", ~0b1010)
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
            DocExample(
                title = "Shift operators",
                description = "Shift bits left or right. Left shift doubles the value per shift; right shift halves it.",
                code = """
                    |import * from "stdlib"
                    |
                    |console.log("1 << 4 =", 1 << 4)
                    |console.log("128 >> 3 =", 128 >> 3)
                    |console.log("-8 >>> 1 =", -8 >>> 1)
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
            DocExample(
                title = "Practical: flags and masks",
                description = "Combine bitwise operators for flag management — common in audio settings and MIDI processing.",
                code = """
                    |import * from "stdlib"
                    |
                    |// Define flags
                    |const REVERB = 1
                    |const DELAY = 2
                    |const CHORUS = 4
                    |
                    |// Set flags with |=
                    |let effects = 0
                    |effects |= REVERB
                    |effects |= CHORUS
                    |console.log("effects:", effects)
                    |
                    |// Check a flag with &
                    |console.log("has reverb?", (effects & REVERB) != 0)
                    |console.log("has delay?", (effects & DELAY) != 0)
                    |
                    |// Toggle with ^=
                    |effects ^= DELAY
                    |console.log("after toggle delay:", effects)
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
        ),
    ),

    // ==========================================
    // 6. Strings & Template Literals
    // ==========================================
    DocSection(
        title = "Strings & Template Literals",
        description = "Template literals with \${...} embed expressions in strings. KlangScript provides rich string methods for searching, transforming, and splitting text.",
        examples = listOf(
            DocExample(
                title = "Template literals",
                description = "Backtick strings embed expressions with \${...}. Cleaner than string concatenation. JS note: KlangScript does NOT allow \"text\" + number. Use template literals instead.",
                code = """
                    |import * from "stdlib"
                    |
                    |const note = "C"
                    |const octave = 4
                    |const velocity = 100
                    |
                    |console.log(`Note: ${"$"}{note}${"$"}{octave}`)
                    |console.log(`Velocity: ${"$"}{velocity} / 127 = ${"$"}{Math.round(velocity * 100 / 127)}%`)
                    |console.log(`Is loud: ${"$"}{velocity > 80 ? "yes" : "no"}`)
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
            DocExample(
                title = "String methods — queries",
                description = "Check string properties: position, prefix, suffix, character access.",
                code = """
                    |import * from "stdlib"
                    |
                    |const note = "C#4"
                    |console.log("starts with C:", note.startsWith("C"))
                    |console.log("ends with 4:", note.endsWith("4"))
                    |console.log("# at index:", note.indexOf("#"))
                    |console.log("char at 0:", note.charAt(0))
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
            DocExample(
                title = "String .length() is a method",
                description = "KlangScript exposes string length as a method call with parens: `s.length()`. In JavaScript, `length` is a property accessed without parens.",
                code = """
                    |import * from "stdlib"
                    |
                    |const note = "C#4"
                    |console.log("length:", note.length())
                """.trimMargin(),
                jsCompat = JsCompat.Incompatible,
            ),
            DocExample(
                title = "String methods — transforms",
                description = "Transform strings: case, trim, split, replace, repeat. These return new strings — the original is unchanged.",
                code = """
                    |import * from "stdlib"
                    |
                    |const raw = "  c4 d4 e4 f4  "
                    |const cleaned = raw.trim()
                    |console.log("trimmed:", cleaned)
                    |console.log("upper:", cleaned.toUpperCase())
                    |console.log("lower:", "LOUD".toLowerCase())
                    |console.log("split:", cleaned.split(" "))
                    |console.log("replace:", cleaned.replace("c4", "g4"))
                    |console.log("slice 0-2:", cleaned.slice(0, 2))
                    |console.log("repeat:", "kick ".repeat(4))
                    |console.log("concat:", "hello".concat(" world"))
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
        ),
    ),

    // ==========================================
    // 7. Arrays
    // ==========================================
    DocSection(
        title = "Arrays",
        description = "Arrays are ordered collections with Kotlin-style methods. Build sequences, filter data, and reshape lists.",
        examples = listOf(
            DocExample(
                title = "Creating and accessing",
                description = "Arrays hold ordered values. Use `[i]` to read and write by index.",
                code = """
                    |import * from "stdlib"
                    |
                    |const notes = ["C4", "E4", "G4", "B4"]
                    |console.log("[0]:", notes[0])
                    |console.log("[2]:", notes[2])
                    |
                    |notes[2] = "G#4"
                    |console.log("after update:", notes)
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
            DocExample(
                title = "Array size and endpoints",
                description = "Arrays use Kotlin-style methods: `.size()` (with parens) instead of the JavaScript `.length` property; `.first()` and `.last()` instead of `[0]` / `[arr.length - 1]`.",
                code = """
                    |import * from "stdlib"
                    |
                    |const notes = ["C4", "E4", "G4", "B4"]
                    |console.log("size:", notes.size())
                    |console.log("first:", notes.first())
                    |console.log("last:", notes.last())
                """.trimMargin(),
                jsCompat = JsCompat.Incompatible,
            ),
            DocExample(
                title = "Array methods — query and transform",
                description = "Search, check, and reshape arrays without mutating the original. JS note: .contains() = JS .includes(). .joinToString() = JS .join(). .reversed() returns a new array (JS .reverse() mutates!).",
                code = """
                    |import * from "stdlib"
                    |
                    |const scale = ["C", "D", "E", "F", "G", "A", "B"]
                    |console.log("contains E:", scale.contains("E"))
                    |console.log("indexOf G:", scale.indexOf("G"))
                    |console.log("isEmpty:", scale.isEmpty())
                    |console.log("reversed:", scale.reversed())
                    |console.log("first 3:", scale.take(3))
                    |console.log("skip 5:", scale.drop(5))
                    |console.log("middle:", scale.subList(2, 5))
                    |console.log("joined:", scale.joinToString(" - "))
                """.trimMargin(),
                jsCompat = JsCompat.Incompatible,
            ),
            DocExample(
                title = "Array methods — mutate",
                description = "Add and remove elements. These methods modify the array in place. JS note: .add() = JS .push(). .removeLast() = JS .pop(). .removeFirst() = JS .shift().",
                code = """
                    |import * from "stdlib"
                    |
                    |const seq = ["kick"]
                    |seq.add("hat")
                    |seq.add("snare")
                    |seq.add("hat")
                    |console.log("built:", seq)
                    |
                    |seq.removeAt(1)
                    |console.log("after removeAt(1):", seq)
                    |seq.removeFirst()
                    |console.log("after removeFirst:", seq)
                    |seq.removeLast()
                    |console.log("after removeLast:", seq)
                """.trimMargin(),
                jsCompat = JsCompat.Incompatible,
            ),
        ),
    ),

    // ==========================================
    // 8. Objects
    // ==========================================
    DocSection(
        title = "Objects",
        description = "Objects group named values with dot and bracket access. Use Object.keys(), Object.values(), and Object.entries() to inspect them.",
        examples = listOf(
            DocExample(
                title = "Object basics",
                description = "Objects group named values. Access properties with dot notation or brackets for dynamic keys.",
                code = """
                    |import * from "stdlib"
                    |
                    |const track = { name: "Lead", note: "C4", velocity: 100 }
                    |console.log(track.name, "plays", track.note)
                    |
                    |// Bracket access with a variable
                    |const field = "velocity"
                    |console.log("velocity:", track[field])
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
            DocExample(
                title = "Object utility methods",
                description = "Object.keys(), .values(), and .entries() let you inspect an object's contents.",
                code = """
                    |import * from "stdlib"
                    |
                    |const mixer = { kick: 80, snare: 70, hat: 50, bass: 90 }
                    |console.log("channels:", Object.keys(mixer))
                    |console.log("levels:", Object.values(mixer))
                    |console.log("entries:", Object.entries(mixer))
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
            DocExample(
                title = "Iterating entries with .size()",
                description = "Arrays use `.size()` in KlangScript — a method call with parens, not the `.length` property you'd find in JavaScript.",
                code = """
                    |import * from "stdlib"
                    |
                    |const mixer = { kick: 80, snare: 70, hat: 50, bass: 90 }
                    |const entries = Object.entries(mixer)
                    |for (let i = 0; i < entries.size(); i++) {
                    |  const pair = entries[i]
                    |  console.log(`  ${"$"}{pair[0]}: ${"$"}{pair[1]}`)
                    |}
                """.trimMargin(),
                jsCompat = JsCompat.Incompatible,
            ),
            DocExample(
                title = "Nested objects and the in operator",
                description = "Objects can contain other objects. Use \"key\" in obj to check if a key exists.",
                code = """
                    |import * from "stdlib"
                    |
                    |const song = {
                    |  title: "Groove",
                    |  verse: { bars: 8, bpm: 120 },
                    |  chorus: { bars: 4, bpm: 130 }
                    |}
                    |
                    |console.log("has verse?", "verse" in song)
                    |console.log("has bridge?", "bridge" in song)
                    |console.log("verse bars:", song.verse.bars)
                    |console.log("chorus bpm:", song.chorus.bpm)
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
        ),
    ),

    // ==========================================
    // 9. Control Flow
    // ==========================================
    DocSection(
        title = "Control Flow",
        description = "Control flow lets your code make decisions and repeat actions. In KlangScript, if/else is an expression that returns a value.",
        examples = listOf(
            DocExample(
                title = "if/else as expression",
                description = "In KlangScript, if/else returns a value — assign it directly to a variable. JS note: This is different from JavaScript where if/else is a statement. In KlangScript, it's an expression.",
                code = """
                    |import * from "stdlib"
                    |
                    |const velocity = 95
                    |const dynamic = if (velocity >= 100) {
                    |  "fortissimo"
                    |} else if (velocity >= 80) {
                    |  "forte"
                    |} else if (velocity >= 50) {
                    |  "mezzo"
                    |} else {
                    |  "piano"
                    |}
                    |
                    |console.log(`Velocity ${"$"}{velocity} = ${"$"}{dynamic}`)
                """.trimMargin(),
                jsCompat = JsCompat.Incompatible,
            ),
            DocExample(
                title = "for and while loops",
                description = "Repeat actions with for (when you know how many times) or while (when you have a condition).",
                code = """
                    |import * from "stdlib"
                    |
                    |// Build a pattern with a for loop
                    |const steps = []
                    |for (let i = 0; i < 8; i++) {
                    |  const hit = if (i % 2 == 0) { "KICK" } else { "hat" }
                    |  steps.add(hit)
                    |}
                    |console.log("pattern:", steps.joinToString(" "))
                    |
                    |// Double tempo with while
                    |let bpm = 60
                    |while (bpm < 200) {
                    |  bpm *= 2
                    |}
                    |console.log("doubled past 200:", bpm)
                """.trimMargin(),
                jsCompat = JsCompat.Incompatible,
            ),
            DocExample(
                title = "do-while",
                description = "do-while runs the body at least once before checking the condition.",
                code = """
                    |import * from "stdlib"
                    |
                    |let attempts = 0
                    |let value = 0
                    |do {
                    |  value += 7
                    |  attempts++
                    |} while (value % 3 != 0)
                    |
                    |console.log(`Found ${"$"}{value} after ${"$"}{attempts} attempts`)
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
            DocExample(
                title = "break and continue",
                description = "break exits a loop early. continue skips to the next iteration.",
                code = """
                    |import * from "stdlib"
                    |
                    |// continue: skip even steps
                    |const oddSteps = []
                    |for (let i = 0; i < 10; i++) {
                    |  if (i % 2 == 0) { continue }
                    |  oddSteps.add(i)
                    |}
                    |console.log("odd steps:", oddSteps)
                    |
                    |// break: find first match
                    |const notes = ["C4", "D4", "E4", "F#4", "G4"]
                    |let sharp = "none"
                    |for (let i = 0; i < notes.size(); i++) {
                    |  if (notes[i].indexOf("#") >= 0) {
                    |    sharp = notes[i]
                    |    break
                    |  }
                    |}
                    |console.log("first sharp:", sharp)
                """.trimMargin(),
                jsCompat = JsCompat.Incompatible,
            ),
        ),
    ),

    // ==========================================
    // 10. Functions & Closures
    // ==========================================
    DocSection(
        title = "Functions & Closures",
        description = "Arrow functions are the only function syntax in KlangScript. They support closures, currying, and higher-order patterns.",
        examples = listOf(
            DocExample(
                title = "Arrow functions — expression vs block",
                description = "Expression body returns implicitly. Block body needs an explicit return. JS note: KlangScript only has arrow functions. No function keyword, no function name() {}.",
                code = """
                    |import * from "stdlib"
                    |
                    |// Expression body (implicit return)
                    |const double = x => x * 2
                    |const add = (a, b) => a + b
                    |
                    |// Block body (explicit return)
                    |const describe = (note, oct) => {
                    |  const name = `${"$"}{note}${"$"}{oct}`
                    |  return `Note: ${"$"}{name}`
                    |}
                    |
                    |console.log(double(64))
                    |console.log(add(3, 4))
                    |console.log(describe("C", 4))
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
            DocExample(
                title = "Closures",
                description = "A function can capture variables from its surrounding scope. This is called a closure — the inner function \"remembers\" the outer scope.",
                code = """
                    |import * from "stdlib"
                    |
                    |const makeCounter = () => {
                    |  let count = 0
                    |  return () => {
                    |    count += 1
                    |    return count
                    |  }
                    |}
                    |
                    |const beat = makeCounter()
                    |console.log("beat:", beat(), beat(), beat(), beat())
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
            DocExample(
                title = "Higher-order functions and currying",
                description = "Functions can take functions as arguments and return functions. Currying builds reusable partial applications.",
                code = """
                    |import * from "stdlib"
                    |
                    |// Higher-order: pass a function as argument
                    |const apply = (fn, value) => fn(value)
                    |const octaveUp = n => n + 12
                    |console.log("C4 + octave:", apply(octaveUp, 60))
                    |
                    |// Currying: function returns function
                    |const transpose = semitones => note => note + semitones
                    |const upFifth = transpose(7)
                    |console.log("C4 up a fifth:", upFifth(60))
                    |console.log("E4 up a fifth:", upFifth(64))
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
        ),
    ),

    // ==========================================
    // 11. Named Arguments
    // ==========================================
    DocSection(
        title = "Named Arguments",
        description = "Call any function by parameter name with `name = value`. Handy when a function has lots of parameters — the call reads like a sentence. JS note: JavaScript has no named arguments. The usual workaround there is to pass an options object (`foo({ name: value })`), which looks different and behaves differently.",
        examples = listOf(
            DocExample(
                title = "Named call to an arrow function",
                description = "Pass arguments by parameter name with `name = value`. Works for any arrow function you define.",
                code = """
                    |import * from "stdlib"
                    |
                    |const greet = (greeting, target) => `${"$"}{greeting}, ${"$"}{target}!`
                    |
                    |// Positional — bound by position
                    |console.log(greet("Hello", "world"))
                    |
                    |// Named — bound by parameter name
                    |console.log(greet(greeting = "Hi", target = "KlangScript"))
                """.trimMargin(),
                jsCompat = JsCompat.Incompatible,
            ),
            DocExample(
                title = "Named arguments can appear in any order",
                description = "When you use named arguments, the order at the call site doesn't matter — each value is routed by name.",
                code = """
                    |import * from "stdlib"
                    |
                    |const subtract = (a, b) => a - b
                    |
                    |// Swap the order — still subtracts b from a
                    |console.log("10 - 3 =", subtract(b = 3, a = 10))
                    |console.log("same:", subtract(a = 10, b = 3))
                """.trimMargin(),
                jsCompat = JsCompat.Incompatible,
            ),
            DocExample(
                title = "All-or-nothing rule",
                description = "A single call must be either fully positional or fully named — never mixed. Mixing is rejected. Rationale: mixing creates ambiguity about which slot a positional arg fills.",
                code = """
                    |import * from "stdlib"
                    |
                    |const add = (a, b) => a + b
                    |
                    |// OK — all positional
                    |console.log(add(1, 2))
                    |
                    |// OK — all named
                    |console.log(add(a = 1, b = 2))
                    |
                    |// ERROR — mixing positional and named is rejected:
                    |//   add(1, b = 2)
                    |//   → "Call must use either all positional or all named arguments"
                """.trimMargin(),
                jsCompat = JsCompat.Incompatible,
            ),
            DocExample(
                title = "Named arguments on stdlib functions",
                description = "Stdlib functions expose parameter names too. Useful when a signature has several numeric parameters that would otherwise be hard to tell apart at the call site.",
                code = """
                    |import * from "stdlib"
                    |
                    |// Positional
                    |console.log("sqrt(16):", Math.sqrt(16))
                    |console.log("pow(2, 10):", Math.pow(2, 10))
                    |
                    |// Named — self-documenting at the call site
                    |console.log("sqrt(x=16):", Math.sqrt(x = 16))
                    |console.log("pow(base=2, exp=10):", Math.pow(base = 2, exp = 10))
                """.trimMargin(),
                jsCompat = JsCompat.Incompatible,
            ),
        ),
    ),

    // ==========================================
    // 12. Imports & the Standard Library
    // ==========================================
    DocSection(
        title = "Imports & the Standard Library",
        description = "The standard library provides Math, console, and more. Use import/export to organize code into modules.",
        examples = listOf(
            DocExample(
                title = "Wildcard import and Math",
                description = "import * from \"stdlib\" loads everything. The Math object provides common mathematical functions.",
                code = """
                    |import * from "stdlib"
                    |
                    |console.log("sqrt(144):", Math.sqrt(144))
                    |console.log("abs(-7):", Math.abs(-7))
                    |console.log("floor(3.9):", Math.floor(3.9))
                    |console.log("ceil(3.1):", Math.ceil(3.1))
                    |console.log("round(3.5):", Math.round(3.5))
                    |console.log("pow(2, 10):", Math.pow(2, 10))
                """.trimMargin(),
                jsCompat = JsCompat.Incompatible,
            ),
            DocExample(
                title = "Trigonometry and clamping",
                description = "Sine, cosine, and tangent for wave calculations. min/max for clamping values to a range.",
                code = """
                    |import * from "stdlib"
                    |
                    |// Generate a sine wave sample
                    |const steps = 8
                    |for (let i = 0; i < steps; i++) {
                    |  const angle = i * 2 * 3.14159 / steps
                    |  const value = Math.round(Math.sin(angle) * 100) / 100
                    |  console.log(`step ${"$"}{i}: sin = ${"$"}{value}`)
                    |}
                    |
                    |// Clamp a value between min and max
                    |const raw = 150
                    |const clamped = Math.min(Math.max(raw, 0), 127)
                    |console.log("clamped:", clamped)
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
            DocExample(
                title = "Selective and aliased imports",
                description = "Import only what you need, or rename imports to avoid conflicts. Namespace imports keep everything under a prefix.",
                code = """
                    |import * from "stdlib"
                    |
                    |// Selective import from a custom module:
                    |// import { midiToFreq } from "music-utils"
                    |// Aliased: import { abs as absolute } from "stdlib"
                    |// Namespace: import * as M from "stdlib"
                    |
                    |// With stdlib, use Math directly:
                    |const hypotenuse = Math.sqrt(Math.pow(3, 2) + Math.pow(4, 2))
                    |console.log("3-4-5 triangle:", hypotenuse)
                    |
                    |const midiToFreq = (note) => Math.round(440 * Math.pow(2, (note - 69) / 12))
                    |console.log("C4 (60):", midiToFreq(60), "Hz")
                    |console.log("A4 (69):", midiToFreq(69), "Hz")
                """.trimMargin(),
                jsCompat = JsCompat.Incompatible,
            ),
        ),
    ),

    // ==========================================
    // 13. Method Chaining & Putting It All Together
    // ==========================================
    DocSection(
        title = "Method Chaining & Putting It All Together",
        description = "Chain methods for fluent, expressive code. Combine everything you've learned into a complete program.",
        examples = listOf(
            DocExample(
                title = "String chaining",
                description = "Chain method calls left-to-right for clean, readable transformations.",
                code = """
                    |import * from "stdlib"
                    |
                    |const result = "  Hello World  ".trim().toLowerCase().replace("world", "klang")
                    |console.log("chained:", result)
                    |console.log("split:", result.split(" "))
                """.trimMargin(),
                jsCompat = JsCompat.Compatible,
            ),
            DocExample(
                title = "Array + string chaining",
                description = "Build data pipelines by chaining array and string operations together.",
                code = """
                    |import * from "stdlib"
                    |
                    |const melody = ["c4", "e4", "g4", "e4", "c4"]
                    |
                    |// Reverse and join
                    |const retrograde = melody.reversed().joinToString(" ")
                    |console.log("retrograde:", retrograde)
                    |
                    |// Take a slice and format
                    |const motif = melody.take(3).joinToString(" > ")
                    |console.log("motif:", motif)
                    |
                    |// Build display string
                    |console.log(`Melody (${"$"}{melody.size()} notes): ${"$"}{melody.joinToString(", ")}`)
                """.trimMargin(),
                jsCompat = JsCompat.Incompatible,
            ),
            DocExample(
                title = "Capstone: putting it all together",
                description = "A complete program combining variables, arrays, objects, loops, functions, closures, template strings, Math, and imports.",
                code = """
                    |import * from "stdlib"
                    |
                    |// === Beat Machine ===
                    |const config = { bpm: 128, bars: 2, stepsPerBar: 8 }
                    |const totalSteps = config.bars * config.stepsPerBar
                    |const beatLen = Math.round(60000 / config.bpm)
                    |
                    |console.log(`Beat Machine: ${"$"}{config.bpm} BPM, ${"$"}{config.bars} bars`)
                    |console.log(`Step duration: ${"$"}{beatLen}ms`)
                    |
                    |// Build patterns using functions
                    |const every = (n) => (step) => step % n == 0
                    |const onBeat = every(config.stepsPerBar / 4)
                    |const offBeat = (step) => step % 4 == 2
                    |
                    |// Generate the sequence
                    |const output = []
                    |for (let i = 0; i < totalSteps; i++) {
                    |  let hit = "."
                    |  if (onBeat(i)) { hit = "KICK" }
                    |  if (offBeat(i)) { hit = "hat" }
                    |  if (i % config.stepsPerBar == 4) { hit = "SNARE" }
                    |  output.add(hit)
                    |}
                    |
                    |// Display bar by bar
                    |for (let bar = 0; bar < config.bars; bar++) {
                    |  const start = bar * config.stepsPerBar
                    |  const barSteps = output.subList(start, start + config.stepsPerBar)
                    |  console.log(`Bar ${"$"}{bar + 1}: ${"$"}{barSteps.joinToString(" | ")}`)
                    |}
                    |
                    |console.log(`Total steps: ${"$"}{output.size()}`)
                """.trimMargin(),
                jsCompat = JsCompat.Incompatible,
            ),
        ),
    ),
)
