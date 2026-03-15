package io.peekandpoke.klang.script.docs

import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.script.builder.registerLibrary
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.stdlib.KlangStdLib

/**
 * Pre-flight smoke test exercising every KlangScript feature from the tutorial checklist.
 * Each test verifies no exception is thrown — no output validation.
 */
class KlangScriptFeatureSmokeTest : StringSpec({

    fun engine() = klangScript {
        registerLibrary(KlangStdLib.create())
    }

    // --- let / const ---
    "let and const declarations" {
        engine().execute(
            """
            import * from "stdlib"
            let x = 10
            const y = 20
            x = 15
            console.log(x, y)
            """.trimIndent()
        )
    }

    // --- null ---
    "null value" {
        engine().execute(
            """
            import * from "stdlib"
            let x = null
            console.log(x)
            """.trimIndent()
        )
    }

    // --- Number / String / Boolean literals ---
    "number string boolean literals" {
        engine().execute(
            """
            import * from "stdlib"
            const a = 42
            const b = 3.14
            const c = -5
            const s = "hello"
            const t = true
            const f = false
            console.log(a, b, c, s, t, f)
            """.trimIndent()
        )
    }

    // --- // and /* */ comments ---
    "single line and block comments" {
        engine().execute(
            """
            import * from "stdlib"
            // single line comment
            const x = 1 // inline comment
            /* block
               comment */
            console.log(x)
            """.trimIndent()
        )
    }

    // --- Arithmetic: + - * / % ** ---
    "arithmetic operators" {
        engine().execute(
            """
            import * from "stdlib"
            const a = 10 + 3
            const b = 10 - 3
            const c = 10 * 3
            const d = 10 / 3
            const e = 10 % 3
            const f = 2 ** 8
            console.log(a, b, c, d, e, f)
            """.trimIndent()
        )
    }

    // --- Comparison: == != < > <= >= ---
    "comparison operators" {
        engine().execute(
            """
            import * from "stdlib"
            console.log(1 == 1, 1 != 2, 1 < 2, 2 > 1, 1 <= 1, 2 >= 1)
            """.trimIndent()
        )
    }

    // --- Logical: && || ! ---
    "logical operators" {
        engine().execute(
            """
            import * from "stdlib"
            console.log(true && false, true || false, !false)
            """.trimIndent()
        )
    }

    // --- Ternary: cond ? a : b ---
    "ternary operator" {
        engine().execute(
            """
            import * from "stdlib"
            const x = true ? "yes" : "no"
            console.log(x)
            """.trimIndent()
        )
    }

    // --- Assignment: += -= *= /= %= ---
    "assignment operators" {
        engine().execute(
            """
            import * from "stdlib"
            let a = 10
            a += 5
            a -= 2
            a *= 3
            a /= 2
            a %= 7
            console.log(a)
            """.trimIndent()
        )
    }

    // --- Unary: ++ -- (prefix/postfix) ---
    "unary increment decrement" {
        engine().execute(
            """
            import * from "stdlib"
            let a = 5
            let b = ++a
            let c = a++
            let d = --a
            let e = a--
            console.log(a, b, c, d, e)
            """.trimIndent()
        )
    }

    // --- if/else as expression ---
    "if else as expression" {
        engine().execute(
            """
            import * from "stdlib"
            const x = 10
            const label = if (x > 5) { "big" } else { "small" }
            console.log(label)
            """.trimIndent()
        )
    }

    // --- for loop (C-style) ---
    "for loop" {
        engine().execute(
            """
            import * from "stdlib"
            let sum = 0
            for (let i = 0; i < 5; i++) {
              sum += i
            }
            console.log(sum)
            """.trimIndent()
        )
    }

    // --- while loop ---
    "while loop" {
        engine().execute(
            """
            import * from "stdlib"
            let n = 0
            while (n < 5) {
              n++
            }
            console.log(n)
            """.trimIndent()
        )
    }

    // --- do-while loop ---
    "do-while loop" {
        engine().execute(
            """
            import * from "stdlib"
            let n = 0
            do {
              n++
            } while (n < 5)
            console.log(n)
            """.trimIndent()
        )
    }

    // --- break ---
    "break" {
        engine().execute(
            """
            import * from "stdlib"
            let result = 0
            for (let i = 0; i < 100; i++) {
              if (i > 5) { break }
              result = i
            }
            console.log(result)
            """.trimIndent()
        )
    }

    // --- continue ---
    "continue" {
        engine().execute(
            """
            import * from "stdlib"
            let sum = 0
            for (let i = 0; i < 10; i++) {
              if (i % 2 == 0) { continue }
              sum += i
            }
            console.log(sum)
            """.trimIndent()
        )
    }

    // --- Arrow functions (expression body) ---
    "arrow function expression body" {
        engine().execute(
            """
            import * from "stdlib"
            const add = (a, b) => a + b
            console.log(add(3, 4))
            """.trimIndent()
        )
    }

    // --- Arrow functions (block body) ---
    "arrow function block body" {
        engine().execute(
            """
            import * from "stdlib"
            const add = (a, b) => {
              return a + b
            }
            console.log(add(3, 4))
            """.trimIndent()
        )
    }

    // --- Closures ---
    "closures" {
        engine().execute(
            """
            import * from "stdlib"
            const makeCounter = () => {
              let count = 0
              return () => {
                count += 1
                return count
              }
            }
            const c = makeCounter()
            console.log(c(), c(), c())
            """.trimIndent()
        )
    }

    // --- Currying ---
    "currying" {
        engine().execute(
            """
            import * from "stdlib"
            const add = a => b => a + b
            console.log(add(3)(4))
            """.trimIndent()
        )
    }

    // --- Higher-order functions ---
    "higher-order functions" {
        engine().execute(
            """
            import * from "stdlib"
            const apply = (fn, x) => fn(x)
            const double = x => x * 2
            console.log(apply(double, 5))
            """.trimIndent()
        )
    }

    // --- Array literals, .size(), .first(), .last(), [index], .add() ---
    "array basics" {
        engine().execute(
            """
            import * from "stdlib"
            const arr = [1, 2, 3]
            console.log(arr.size(), arr.first(), arr.last(), arr[1])
            arr.add(4)
            console.log(arr)
            """.trimIndent()
        )
    }

    // --- .reversed(), .contains(), .joinToString(), .indexOf() ---
    "array query and transform methods" {
        engine().execute(
            """
            import * from "stdlib"
            const arr = [10, 20, 30]
            console.log(arr.reversed())
            console.log(arr.contains(20))
            console.log(arr.joinToString(", "))
            console.log(arr.indexOf(30))
            """.trimIndent()
        )
    }

    // --- .isEmpty(), .isNotEmpty(), .drop(), .take(), .subList() ---
    "array advanced methods" {
        engine().execute(
            """
            import * from "stdlib"
            const arr = [1, 2, 3, 4, 5]
            console.log(arr.isEmpty(), arr.isNotEmpty())
            console.log(arr.drop(2))
            console.log(arr.take(3))
            console.log(arr.subList(1, 4))
            """.trimIndent()
        )
    }

    // --- .removeAt(), .removeFirst(), .removeLast() ---
    "array mutation methods" {
        engine().execute(
            """
            import * from "stdlib"
            const arr = [1, 2, 3, 4, 5]
            arr.removeAt(2)
            console.log(arr)
            arr.removeFirst()
            console.log(arr)
            arr.removeLast()
            console.log(arr)
            """.trimIndent()
        )
    }

    // --- Array index assignment ---
    "array index assignment" {
        engine().execute(
            """
            import * from "stdlib"
            const arr = [1, 2, 3]
            arr[1] = 99
            console.log(arr)
            """.trimIndent()
        )
    }

    // --- Object literals, dot access, bracket access ---
    "object basics" {
        engine().execute(
            """
            import * from "stdlib"
            const obj = { name: "test", value: 42 }
            console.log(obj.name, obj.value)
            const key = "name"
            console.log(obj[key])
            """.trimIndent()
        )
    }

    // --- Dynamic keys (bracket access with variable) ---
    "object dynamic keys" {
        engine().execute(
            """
            import * from "stdlib"
            const obj = { tempo: 120, key: "C" }
            const field = "tempo"
            console.log(obj[field])
            """.trimIndent()
        )
    }

    // --- Object.keys(), Object.values(), Object.entries() ---
    "Object utility methods" {
        engine().execute(
            """
            import * from "stdlib"
            const obj = { a: 1, b: 2 }
            console.log(Object.keys(obj))
            console.log(Object.values(obj))
            console.log(Object.entries(obj))
            """.trimIndent()
        )
    }

    // --- Template strings with interpolation ---
    "template strings" {
        engine().execute(
            """
            import * from "stdlib"
            const name = "World"
            const x = 42
            console.log(`Hello ${'$'}{name}! x = ${'$'}{x + 1}`)
            """.trimIndent()
        )
    }

    // --- String methods: .length(), .toUpperCase(), .toLowerCase(), .trim() ---
    "string methods basics" {
        engine().execute(
            """
            import * from "stdlib"
            const s = "  Hello World  "
            console.log(s.length(), s.trim(), s.toUpperCase(), s.toLowerCase())
            """.trimIndent()
        )
    }

    // --- .split(), .replace(), .startsWith(), .endsWith() ---
    "string methods search" {
        engine().execute(
            """
            import * from "stdlib"
            const s = "hello world"
            console.log(s.split(" "))
            console.log(s.replace("world", "klang"))
            console.log(s.startsWith("hello"))
            console.log(s.endsWith("world"))
            """.trimIndent()
        )
    }

    // --- .slice(), .substring(), .repeat(), .concat(), .charAt(), .indexOf() ---
    "string methods transform" {
        engine().execute(
            """
            import * from "stdlib"
            const s = "KlangScript"
            console.log(s.slice(0, 5))
            console.log(s.substring(5, 11))
            console.log("ha".repeat(3))
            console.log("hello".concat(" world"))
            console.log(s.charAt(0))
            console.log(s.indexOf("Script"))
            """.trimIndent()
        )
    }

    // --- import * from "stdlib" ---
    "wildcard import" {
        engine().execute(
            """
            import * from "stdlib"
            console.log("stdlib loaded")
            """.trimIndent()
        )
    }

    // --- Math methods ---
    "Math methods" {
        engine().execute(
            """
            import * from "stdlib"
            console.log(Math.sqrt(144))
            console.log(Math.abs(-5))
            console.log(Math.floor(3.7))
            console.log(Math.ceil(3.2))
            console.log(Math.sin(0))
            console.log(Math.cos(0))
            console.log(Math.min(3, 7))
            console.log(Math.max(3, 7))
            console.log(Math.pow(2, 10))
            console.log(Math.round(3.6))
            """.trimIndent()
        )
    }

    // --- console.log() ---
    "console.log" {
        engine().execute(
            """
            import * from "stdlib"
            console.log("test", 1, true, null)
            """.trimIndent()
        )
    }

    // --- print() ---
    "print function" {
        engine().execute(
            """
            import * from "stdlib"
            print("hello from print")
            """.trimIndent()
        )
    }

    // --- Short-circuit evaluation ---
    "short-circuit evaluation" {
        engine().execute(
            """
            import * from "stdlib"
            const name = null || "default"
            const check = true && "yes"
            console.log(name, check)
            """.trimIndent()
        )
    }

    // --- Truthy/falsy values ---
    "truthy falsy values" {
        engine().execute(
            """
            import * from "stdlib"
            console.log(0 || "zero is falsy")
            console.log("" || "empty string is falsy")
            console.log(null || "null is falsy")
            console.log(false || "false is falsy")
            """.trimIndent()
        )
    }

    // --- "key" in obj ---
    "in operator" {
        engine().execute(
            """
            import * from "stdlib"
            const obj = { name: "test", value: 42 }
            console.log("name" in obj)
            console.log("missing" in obj)
            """.trimIndent()
        )
    }

    // --- Method chaining ---
    "method chaining" {
        engine().execute(
            """
            import * from "stdlib"
            const result = "  Hello World  ".trim().toLowerCase().replace("world", "klang")
            console.log(result)
            """.trimIndent()
        )
    }

    // --- Selective import { x } from ---
    "selective import" {
        val eng = klangScript {
            registerLibrary(KlangStdLib.create())
            registerLibrary(
                "mylib", """
                    let add = (a, b) => a + b
                    let mul = (a, b) => a * b
                    export { add, mul }
                """.trimIndent()
            )
        }
        eng.execute(
            """
            import * from "stdlib"
            import { add, mul } from "mylib"
            console.log(add(3, 4), mul(3, 4))
            """.trimIndent()
        )
    }

    // --- Aliased import { x as y } from ---
    "aliased import" {
        val eng = klangScript {
            registerLibrary(KlangStdLib.create())
            registerLibrary(
                "mylib", """
                    let add = (a, b) => a + b
                    export { add }
                """.trimIndent()
            )
        }
        eng.execute(
            """
            import * from "stdlib"
            import { add as sum } from "mylib"
            console.log(sum(3, 4))
            """.trimIndent()
        )
    }

    // --- Namespace import * as ns from ---
    "namespace import" {
        val eng = klangScript {
            registerLibrary(KlangStdLib.create())
            registerLibrary(
                "mylib", """
                    let add = (a, b) => a + b
                    export { add }
                """.trimIndent()
            )
        }
        eng.execute(
            """
            import * from "stdlib"
            import * as lib from "mylib"
            console.log(lib.add(3, 4))
            """.trimIndent()
        )
    }

    // --- export ---
    "export statement" {
        val eng = klangScript {
            registerLibrary(KlangStdLib.create())
            registerLibrary(
                "mylib", """
                    let x = 42
                    let y = 99
                    export { x, y }
                """.trimIndent()
            )
        }
        eng.execute(
            """
            import * from "stdlib"
            import { x, y } from "mylib"
            console.log(x, y)
            """.trimIndent()
        )
    }
})
