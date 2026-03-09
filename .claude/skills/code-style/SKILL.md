---
name: code-style
description: Use when someone asks to apply code style rules, check code style, clean up code style, or follow project code conventions.
---

## What This Skill Does

Loads the code style rules for the Klang project. Apply these rules whenever writing or editing code.

## Rules

### 1. Always Use Curly Braces

All `if`, `else`, `for`, `while`, and `when` branch bodies must use `{ }`, even for one-liners.

**Wrong:**

```kotlin
if (condition) doSomething()
if (condition) doSomething() else doOther()
```

**Correct:**

```kotlin
if (condition) {
    doSomething()
}
if (condition) {
    doSomething()
} else {
    doOther()
}
```

This applies to all languages used in the project (Kotlin, TypeScript, etc.).
