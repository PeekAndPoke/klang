# KlangScript Expected Features Test Suite

This document contains code snippets designed to test and validate KlangScript's JavaScript-like capabilities (excluding
classes and inheritance).

## Status Legend

| Marker             | Meaning                                                             |
|--------------------|---------------------------------------------------------------------|
| ✅                  | Fully implemented                                                   |
| 🟡                 | Partially implemented — notes inline                                |
| ❌ `[EASY]`         | Not implemented — small AST or stdlib-only change                   |
| ❌ `[MEDIUM]`       | Not implemented — requires new AST nodes + parser + interpreter     |
| ❌ `[HARD]`         | Not implemented — significant parser/runtime redesign needed        |
| ❌ `[OUT_OF_SCOPE]` | Explicitly excluded from KlangScript (see `ref/language-design.md`) |

> **Note on method call style:** KlangScript currently calls `length`, `charAt` etc. as methods (`arr.length()`, not
`arr.length`).
> Property-access style (without parens) is tracked separately as a medium-complexity enhancement.

## 1. Basic Literals and Variables

→ [language-features/01-literals-and-variables.md](language-features/01-literals-and-variables.md)

## 2. Basic Operators

→ [language-features/02-operators.md](language-features/02-operators.md)

## 3. Control Flow

→ [language-features/03-control-flow.md](language-features/03-control-flow.md)

## 4. Functions

→ [language-features/04-functions.md](language-features/04-functions.md)

## 5. Arrays

→ [language-features/05-arrays.md](language-features/05-arrays.md)

## 6. Objects

→ [language-features/06-objects.md](language-features/06-objects.md)

## 7. String Methods

→ [language-features/07-strings.md](language-features/07-strings.md)

## 8. Type Coercion and Truthiness

→ [language-features/08-type-coercion.md](language-features/08-type-coercion.md)

## 9. Regular Expressions

→ [language-features/09-regexp.md](language-features/09-regexp.md)

## 10. Math Object

→ [language-features/10-math.md](language-features/10-math.md)

## 11. Error Handling

→ [language-features/11-error-handling.md](language-features/11-error-handling.md)

## 12. Advanced Function Patterns

→ [language-features/12-advanced-functions.md](language-features/12-advanced-functions.md)

## 13. Promises and Async

→ [language-features/13-promises-async.md](language-features/13-promises-async.md)

## 14. JSON

→ [language-features/14-json.md](language-features/14-json.md)

## 15. Sets and Maps

→ [language-features/15-sets-maps.md](language-features/15-sets-maps.md)
