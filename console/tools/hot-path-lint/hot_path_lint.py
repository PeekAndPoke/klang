#!/usr/bin/env python3
"""Hot-path lint: a static scan of the audio backend for what the engine rules forbid in render code.

  hot_path_lint.py [PATH ...] [--root DIR] [--rules a,b] [--list-hot] [--show-waived] [--guarded] [--self-test]

PATH defaults to audio_be/src/commonMain/kotlin; directories are searched for *.kt. Prints
"file:line: [rule] message" per finding and a summary line on stderr; exits 1 when a finding is not waived.

The rules come from audio/CLAUDE.md "Engine rules", audio/ref/performance.md and /code-style sections 5 to 7
and 19. Probe 5 of KLANG-00420 (audio/ref/verification.md, "The probes").

Whole files (code only: comments, KDoc and string literals are masked first):
  boxed-type      Long, ULong, Byte, UByte, Short, UShort, Char as a type, their arrays, `.toLong()` and the
                  other conversions, `1L` and unsigned literals, char literals (/code-style 5)
  fun-interface   a `fun interface` declaration (a SAM type; performance.md rule 1)
  sam-ignitor     a SAM constructor `Name { ... }` of a fun interface
  captured-var    a closure that reads a local `var` of its function (an ObjectRef on Kotlin/JS, performance.md
                  rule 1): an anonymous object, a SAM constructor, a stored lambda, a lambda handed to a call that
                  is not inline; in render code a local fun too

Render code only (the bodies of the hot functions, see "What is render code" below):
  alloc-array       DoubleArray(, FloatArray(, IntArray(, Array(, arrayOf, an array type alias (AudioBuffer(),
                    copyOf, copyOfRange, copyInto (a typed-array view per call on Kotlin/JS)
  alloc-collection  listOf, mutableListOf, setOf, map {, filter {, toList(), sorted, zip, subList ...
  alloc-object      a constructor of a class declared in the scanned sources, audio_bridge or common (or a stdlib
                    collection class), `object :`, `.copy(`, a callable reference `::name`
  alloc-lambda      a lambda handed to a call that is not inline, or stored in a value
  alloc-string      a string template, `+` next to a string literal, toString(), lowercase(), buildString ...
  alloc-pair        Pair(, Triple(, the infix `to`
  map-op            HashMap, mutableMapOf, mapOf, getOrPut, associate, toMap (a stdlib map allocates and, on
                    Kotlin/JS, hashes with a fresh context; performance.md)
  exception         throw, catch, require(, check(, error(, requireNotNull(, checkNotNull(, TODO( (/code-style 7)
  not-null-assert   `!!`, a hidden throw
  nullable-num      a `Double?`, `Int?`, `Float?` or `Boolean?` local, or in a hot function's signature
  nan-merge         `if (c) value else Double.NaN` (or SLOT_UNSET) in expression form (V8 boxes the double arm)
  when-in-loop      a `when (x)` without an `else` arm, in expression position inside a loop (V8 boxes a double
                    result; performance.md, /code-style 19)
  alloc-guarded     with --guarded only: an allocation behind a guard, which the alloc rules pass by default: a
                    cache filled on a miss (`x ?: Foo()`) or storage built in a branch, or after an early return,
                    and kept (`if (buf.size < n) { buf = DoubleArray(n) }`, `pool.add(...)`, `.also { f = it }`),
                    the resize-on-shape-change pattern of performance.md rule 2

What is render code: a function is hot when its name is a ROOT (ROOT_NAMES, ROOT_PREFIXES below) or it is called
from the body of a hot function, followed transitively. A call `x.name(` resolves to the definitions of `name`
whose class the receiver's name points at (`glide.retarget(` to KnobGlide), else to those in the same file, else to
all of them when there are at most CROSS_FILE_LIMIT; an unqualified call reaches no extension of another file, and
kotlin.math names (`abs`, `pow`) never reach the Ignitor DSL's functions of the same name. COLD_NAMES and
COLD_PREFIXES (lifecycle, build and per-event work) never become hot, and COLD_FILES are skipped. `--list-hot`
prints every hot function and why. A comment line `// hot-path-lint: hot` or `// hot-path-lint: cold <reason>`
directly above a `fun` (above its KDoc and annotations too) overrides the decision for that function.

Waivers: `// hot-path-lint: allow <rule> <reason>` on the line of a finding, or as a comment line above it (other
comment lines may sit between), silences that rule's findings on that line; `allow alloc` covers every alloc-*
rule. A waiver without a reason or with an unknown rule is a finding (bad-waiver), one that silences nothing is a
finding (unused-waiver).

Not a Kotlin parser: comments and strings are masked, braces and parentheses matched, and the patterns are read on
the masked text. It sees no types, so it cannot tell a `for` over a list (an iterator) from one over a range, see
a HashMap lookup written as `map[key]`, know which call V8 inlines, or tell a per-block call from a per-note one
inside the same function. Standard library only. Self-test: tests/ (`--self-test`).
"""
import argparse
import os
import re
import subprocess
import sys
from collections import defaultdict

# ---------------------------------------------------------------------------------------------------------------------
# Configuration: what render code is in klang's engine. Kept here, not in a separate file: the tool is klang-only.
# ---------------------------------------------------------------------------------------------------------------------

DEFAULT_PATHS = ["audio_be/src/commonMain/kotlin"]

# Where the names of `inline fun`s are read besides the scanned files (a lambda handed to one is not a closure).
INLINE_SOURCES = ["common/src/commonMain/kotlin", "audio_bridge/src/commonMain/kotlin", "audio_be/src/commonMain/kotlin"]

# The per-block entry points of the engine. Exact names, then prefixes (a name that starts with one of them).
ROOT_NAMES = {
    "generate",            # Ignitor.generate: every node of a voice, every block
    "controlRateValue",    # the block-constant scalar, read per block by the readers of a knob
    "blockStartValue",     # Ignitor.blockStartValue: a knob's per-block read
    "ignite",              # Voice.ignite
    "apply",               # KatalystSlotWriter.apply: per processed block
    "applyParams",         # KatalystChain.applyParams: per block, resolves only on a new map
    "applyKnobArgs",       # the holder hand-over of a stage's knobs, per block
    "applyTeardownFade",   # the voice's fixed stages
    "applyCutFade",
    "mixIntoCylinder",
    "configure",           # a stage's configure, re-applied by its writer every block (performance.md)
    "advance",             # Voice.advance, KnobGlide.advance
    "observeTail",         # the delay's and the reverb's per-block tail step
    "lookaheadStep",       # the house limiter's per-sample step
}
# A prefix matches at a camel-case boundary only: `process` takes `processAndMix`, `render` not `renderingVoiceData`.
ROOT_PREFIXES = ("generate", "process", "render", "tick", "mix", "step")

# Never hot: lifecycle, build, per-event and diagnostic work. Exact names, then prefixes.
COLD_NAMES = {
    "reset", "retire", "dispose", "kill", "cutOff", "enter", "offer", "checkIn", "adopt", "commitOwner",
    "toString", "hashCode", "equals", "describe", "measureTail", "measureTails", "hasTail",
    "requestChain", "requestSwap", "pollPendingChain", "pollPendingSwap", "hardCut", "ownerClaimed",
    "setGain", "stop", "resume", "handle", "cleanup", "cleanupHard", "scheduleVoice", "scheduleVoices",
    "replaceVoices", "startRealtimeVoice", "stopRealtimeVoice", "addSample", "markRendered",
    "activateVoice", "promoteScheduled",  # a voice built or promoted: per note, the voice is the allocation
    "giveBack",                           # the warehouse takes a unit back: per retired stage
}
COLD_PREFIXES = ("build", "create", "resolve", "register", "install", "land", "begin", "release", "clear",
                 "warmUp", "new", "make", "load", "decode", "encode", "rent", "init", "prepareSwap")

# Files that are never render code, with the reason.
COLD_FILES = {
    "ignitor/GraphCensus.kt": "a diagnostic over the graph, never on the render path (its KDoc)",
    "audio_bridge/src/commonMain/kotlin/IgnitorDsl.kt": "the Ignitor DSL builds a tree and renders nothing (`mix`)",
}

# A call into another file is followed to every definition of its name when there are at most this many and the
# receiver's name does not point at one of them.
CROSS_FILE_LIMIT = 4

# An unqualified call of one of these is kotlin.math, never an `Ignitor.abs()`-style extension of the same name.
MATH_NAMES = {"abs", "pow", "min", "max", "exp", "sqrt", "sign", "tanh", "floor", "ceil", "round", "ln", "log",
              "log2", "log10", "sin", "cos", "tan", "atan", "atan2", "sinh", "cosh", "truncate", "hypot", "mod"}

BANNED_TYPES = ("Long", "ULong", "Byte", "UByte", "Short", "UShort", "Char")

# Lambdas handed to these run inline (no closure object, a captured var stays a local). Kotlin stdlib scope and
# collection functions; `inline fun`s found in the sources are added at run time.
STDLIB_INLINE = {
    "let", "run", "apply", "also", "with", "takeIf", "takeUnless", "repeat", "use", "synchronized",
    "forEach", "forEachIndexed", "onEach", "map", "mapIndexed", "mapNotNull", "mapTo", "flatMap", "filter",
    "filterNot", "filterIsInstance", "filterTo", "any", "all", "none", "count", "sumOf", "fold", "foldIndexed",
    "reduce", "first", "firstOrNull", "last", "lastOrNull", "find", "findLast", "single", "singleOrNull",
    "indexOfFirst", "indexOfLast", "maxOf", "minOf", "maxOfOrNull", "minOfOrNull", "maxBy", "minBy",
    "maxByOrNull", "minByOrNull", "sortedBy", "sortedByDescending", "sortBy", "sortByDescending", "groupBy",
    "associate", "associateBy", "associateWith", "partition", "getOrPut", "getOrElse", "require", "check",
    "requireNotNull", "checkNotNull", "buildString", "buildList", "buildSet", "buildMap", "removeAll",
    "retainAll", "measureTime", "measureTimedValue", "ifEmpty", "ifBlank", "zip", "windowed", "chunked",
    "distinctBy", "sumByDouble", "takeWhile", "dropWhile", "error",
    # the init lambda of an array or list constructor is inlined into its loop
    "Array", "DoubleArray", "FloatArray", "IntArray", "BooleanArray", "List", "MutableList",
}

# Classes whose constructor allocates although they are not declared in the scanned sources.
STDLIB_CTORS = {"ArrayList", "HashMap", "LinkedHashMap", "HashSet", "LinkedHashSet", "StringBuilder", "Regex",
                "ArrayDeque", "KlangMap", "Random", "Exception", "IllegalStateException", "IllegalArgumentException"}

# A type alias of an array type: its "constructor" is an array allocation.
ARRAY_ALIASES_BUILTIN = {"AudioBuffer"}

RULES = ("boxed-type", "fun-interface", "sam-ignitor", "captured-var", "alloc-array", "alloc-collection",
         "alloc-object", "alloc-lambda", "alloc-string", "alloc-pair", "map-op", "exception", "not-null-assert",
         "nullable-num", "nan-merge", "when-in-loop", "alloc-guarded", "bad-waiver", "unused-waiver")

SKIP_DIRS = {".git", "node_modules", "build", ".gradle", ".idea", ".kotlin", "kotlin-js-store", ".build-claude",
             ".gradle-claude"}

KOTLIN_KEYWORDS = {"if", "else", "return", "in", "is", "as", "when", "while", "for", "do", "val", "var", "throw",
                   "try", "catch", "finally", "fun", "class", "object", "this", "super", "null", "true", "false"}

KEYWORD_BLOCKS = {"if", "for", "while", "when", "catch", "else", "try", "finally", "do", "init", "get", "set",
                  "constructor", "where"}

# ---------------------------------------------------------------------------------------------------------------------
# Reading a Kotlin file: mask comments and strings, match braces and parentheses
# ---------------------------------------------------------------------------------------------------------------------

CHAR_LITERAL = re.compile(r"'(?:\\u[0-9a-fA-F]{4}|\\.|[^'\\\n])'")


def mask(text):
    """(code, strings, chars): code is text with comments blanked and string contents blanked (quotes kept),
    positions and newlines unchanged; strings is [(start, end, templated)], chars the positions of char literals."""
    out = list(text)
    strings, chars = [], []
    n = len(text)

    def blank(a, b):
        for k in range(a, min(b, n)):
            if out[k] != "\n":
                out[k] = " "

    def skip_template(j):
        """j at '{' after '$': the index after the matching '}', skipping nested strings naively."""
        depth = 0
        while j < n:
            ch = text[j]
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return j + 1
            elif ch == '"':
                k = text.find('"', j + 1)
                j = n if k < 0 else k
            j += 1
        return n

    i = 0
    while i < n:
        c = text[i]
        if c == "/" and text.startswith("//", i):
            j = text.find("\n", i)
            j = n if j < 0 else j
            blank(i, j)
            i = j
            continue
        if c == "/" and text.startswith("/*", i):
            depth, j = 1, i + 2
            while j < n and depth:
                if text.startswith("/*", j):
                    depth, j = depth + 1, j + 2
                elif text.startswith("*/", j):
                    depth, j = depth - 1, j + 2
                else:
                    j += 1
            blank(i, j)
            i = j
            continue
        if c == '"' and text.startswith('"""', i):
            j, templated = i + 3, False
            while j < n and not text.startswith('"""', j):
                if text[j] == "$" and j + 1 < n and (text[j + 1] == "{" or text[j + 1].isalpha() or text[j + 1] == "_"):
                    templated = True
                    if text[j + 1] == "{":
                        j = skip_template(j + 1)
                        continue
                j += 1
            end = min(n, j + 3)
            while end < n and text[end] == '"':
                end += 1
            blank(i + 3, end - 3)
            strings.append((i, end, templated))
            i = end
            continue
        if c == '"':
            j, templated = i + 1, False
            while j < n and text[j] not in '"\n':
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == "$" and j + 1 < n and (text[j + 1] == "{" or text[j + 1].isalpha() or text[j + 1] == "_"):
                    templated = True
                    if text[j + 1] == "{":
                        j = skip_template(j + 1)
                        continue
                j += 1
            blank(i + 1, j)
            strings.append((i, j + 1, templated))
            i = j + 1
            continue
        if c == "'":
            m = CHAR_LITERAL.match(text, i)
            if m:
                blank(i + 1, m.end() - 1)
                chars.append(i)
                i = m.end()
                continue
        i += 1
    return "".join(out), strings, chars


def match_pairs(code, open_ch, close_ch):
    pairs, stack = {}, []
    for i, c in enumerate(code):
        if c == open_ch:
            stack.append(i)
        elif c == close_ch and stack:
            pairs[stack.pop()] = i
    return pairs


class Func:
    def __init__(self, src, name, pos, body_start, body_end, modifiers, owner, brace_body, is_ext):
        self.src, self.name, self.pos, self.is_ext = src, name, pos, is_ext
        self.body_start, self.body_end = body_start, body_end  # [start, end) of the body in the masked code
        self.modifiers, self.owner, self.brace_body = modifiers, owner, brace_body
        self.line = src.line_of(pos)

    @property
    def qual(self):
        return f"{self.owner}.{self.name}" if self.owner else self.name

    def __repr__(self):
        return f"{self.src.rel}:{self.line} {self.qual}"


class Source:
    def __init__(self, path, rel):
        self.path, self.rel = path, rel
        with open(path, encoding="utf-8", errors="replace") as f:
            self.text = f.read()
        self.lines = self.text.split("\n")
        self.code, self.strings, self.chars = mask(self.text)
        self.code_lines = self.code.split("\n")
        self.line_starts = [0]
        for m in re.finditer("\n", self.text):
            self.line_starts.append(m.end())
        self.braces = match_pairs(self.code, "{", "}")
        self.parens = match_pairs(self.code, "(", ")")
        self.paren_back = {v: k for k, v in self.parens.items()}
        self.brace_list = sorted(self.braces.items())
        self.kind = {}  # brace open position -> "fun", "class", "object", "lambda:<name>", "block:<kw>"
        self.classes = []  # (open, close, name)
        self.funcs = []
        self.class_names, self.alias_arrays, self.fun_interfaces, self.inline_funs = set(), set(), set(), set()
        self.cold_file = None
        self._index()

    def line_of(self, pos):
        lo, hi = 0, len(self.line_starts) - 1
        while lo < hi:
            mid = (lo + hi + 1) // 2
            if self.line_starts[mid] <= pos:
                lo = mid
            else:
                hi = mid - 1
        return lo + 1

    def line_text(self, line):
        return self.code_lines[line - 1] if 0 < line <= len(self.code_lines) else ""

    def enclosing(self, pos, lo=0):
        """Brace opens that contain pos, innermost first (only those opening at or after lo)."""
        result = []
        for o, c in self.brace_list:
            if o >= pos:
                break
            if o >= lo and c > pos:
                result.append(o)
        return result[::-1]

    def innermost(self, pos):
        e = self.enclosing(pos)
        return e[0] if e else None

    # -- the index: classes, functions, the kind of every brace ------------------------------------------------------

    def _index(self):
        code = self.code
        for m in re.finditer(r"\btypealias\s+(\w+)\s*(?:<[^>]*>)?\s*=\s*(\w+)", code):
            if m.group(2).endswith("Array"):
                self.alias_arrays.add(m.group(1))
        for m in re.finditer(r"\bfun\s+interface\s+(\w+)", code):
            self.fun_interfaces.add(m.group(1))
        for m in re.finditer(r"((?:\b\w+[ \t]+)*)\b(class|object|interface)\b(\s+\w+|\s*:|\s*\{)", code):
            mods, kw, tail = m.group(1).split(), m.group(2), m.group(3).strip()
            name = tail if tail and tail[0].isalnum() else "<anonymous>"
            if kw == "object" and "companion" in mods and name == "<anonymous>":
                name = "Companion"
            if kw == "class" and not ({"enum", "annotation", "value", "inline", "sealed", "abstract", "expect"}
                                      & set(mods)):
                self.class_names.add(name)
            body = self._decl_body(m.end())
            if body is not None:
                self.kind[body] = "object" if name == "<anonymous>" else "class"
                self.classes.append((body, self.braces[body], name if name != "<anonymous>" else "object"))
        for m in re.finditer(r"\bfun\b", code):
            self._add_fun(m)
        for o in self.braces:
            if o not in self.kind:
                self.kind[o] = self._classify_brace(o)

    def _decl_body(self, pos):
        """The '{' that opens the body of a class or object declared before pos, or None."""
        code, n, depth, angle = self.code, len(self.code), 0, 0
        i = pos
        while i < n:
            c = code[i]
            if c in "([":
                depth += 1
            elif c in ")]":
                depth -= 1
            elif c == "<":
                angle += 1
            elif c == ">" and angle:
                angle -= 1
            elif c == "{" and depth == 0:
                return i
            elif c in "}=" and depth == 0:
                return None
            elif c == "\n" and depth == 0 and angle == 0:
                before = code[pos:i].rstrip()
                after = code[i:].lstrip()
                if not (before.endswith((":", ",", "(")) or after.startswith((":", ",", "{", "where", ")"))):
                    return None
            i += 1
        return None

    def _add_fun(self, m):
        code, n = self.code, len(self.code)
        i = m.end()
        while i < n and code[i] in " \t":
            i += 1
        if code.startswith("interface", i):
            return
        if i < n and code[i] == "<":
            depth = 0
            while i < n:
                depth += code[i] == "<"
                depth -= code[i] == ">"
                i += 1
                if depth == 0:
                    break
        p = code.find("(", i)
        if p < 0 or p not in self.parens:
            return
        head = code[i:p]
        if "\n" in head or "{" in head or "=" in head:
            return  # `fun` used as a word inside a type (a function type) or something not a declaration
        name_m = re.search(r"(\w+)\s*$", head)
        if not name_m:
            return
        name = name_m.group(1)
        is_ext = "." in head[:name_m.start()]
        j = self.parens[p] + 1
        while j < n and code[j] in " \t":
            j += 1
        if j < n and code[j] == ":":
            depth = 0
            j += 1
            while j < n:
                c = code[j]
                if c in "(<":
                    depth += 1
                elif c in ")>" and not (c == ">" and code[j - 1] == "-"):
                    depth -= 1
                elif depth == 0 and c in "{=\n":
                    break
                j += 1
        k = j
        while k < n and code[k] in " \t\n":
            k += 1
        line_start = code.rfind("\n", 0, m.start()) + 1
        modifiers = code[line_start:m.start()].split()
        owner = self._owner(m.start())
        if k < n and code[k] == "{" and k in self.braces:
            self.kind[k] = "fun"
            self.funcs.append(Func(self, name, m.start(), k, self.braces[k] + 1, modifiers, owner, True, is_ext))
        elif k < n and code[k] == "=" and code[k:k + 2] != "==":
            end = self._expr_end(k + 1)
            self.funcs.append(Func(self, name, m.start(), k + 1, end, modifiers, owner, False, is_ext))
        if "inline" in modifiers:
            self.inline_funs.add(name)

    def _expr_end(self, pos):
        """The end of an expression body that starts at pos (heuristic: a newline at depth 0 that does not continue)."""
        code, n, depth = self.code, len(self.code), 0
        i = pos
        cont_end = ("=", "(", ",", ".", "?:", "&&", "||", "->", "+", "-", "*", "/", "else", "{", ":", "?")
        cont_start = (".", "?.", "?:", "&&", "||", "+", "-", "*", "/", "else", ":", ")", "]")
        while i < n:
            c = code[i]
            if c in "([{":
                depth += 1
            elif c in ")]}":
                depth -= 1
                if depth < 0:
                    return i
            elif c == "\n" and depth == 0:
                before = code[pos:i].rstrip()
                after = code[i:].lstrip()
                if before and not before.endswith(cont_end) and not after.startswith(cont_start):
                    return i
            i += 1
        return n

    def _owner(self, pos):
        best = None
        for o, c, name in self.classes:
            if o < pos < c and (best is None or o > best[0]):
                best = (o, name)
        return best[1] if best else None

    def _word_before(self, i):
        """(word, index of its first char) ending at or before i, skipping blanks."""
        code = self.code
        while i >= 0 and code[i] in " \t\n":
            i -= 1
        j = i
        while j >= 0 and (code[j].isalnum() or code[j] == "_"):
            j -= 1
        return code[j + 1:i + 1], j + 1

    def _classify_brace(self, o):
        code = self.code
        i = o - 1
        while i >= 0 and code[i] in " \t\n":
            i -= 1
        if i < 0:
            return "block:"
        c = code[i]
        if c == ")":
            op = self.paren_back.get(i)
            if op is None:
                return "block:"
            word, _ = self._word_before(op - 1)
            if word in KEYWORD_BLOCKS:
                return "block:" + word
            return "lambda:" + word
        if c == ">" and i > 0 and code[i - 1] == "-":
            return "block:->"
        if c.isalnum() or c == "_":
            word, start = self._word_before(i)
            if word in KEYWORD_BLOCKS:
                return "block:" + word
            if word[:1].isupper() and code[:start].rstrip().endswith((":", ",")):
                return "class"  # a supertype list whose class body was not recognised
            return "lambda:" + word
        if c in "=(,":
            # an argument lambda (`f(x, { ... })`, `f(name = { ... })`): the call is the word before the enclosing
            # '('; a `=` outside any argument list stores the lambda in a value
            call = self._enclosing_call(i)
            if call is None:
                return "lambda:=" if c == "=" else "block:"
            return "lambda:" + call
        return "block:"

    def _enclosing_call(self, i):
        """The name of the call whose argument list holds position i, or None when i is not inside one."""
        code, depth, j = self.code, 0, i
        while j >= 0:
            ch = code[j]
            if ch in ")}]":
                depth += 1
            elif ch in "({[":
                if depth == 0:
                    if ch != "(":
                        return None
                    word, _ = self._word_before(j - 1)
                    return word
                depth -= 1
            j -= 1
        return None


# ---------------------------------------------------------------------------------------------------------------------
# Findings and waivers
# ---------------------------------------------------------------------------------------------------------------------

WAIVER = re.compile(r"//\s*hot-path-lint:\s*allow\b[ \t]*([\w-]*)[ \t]*(.*)$")
MARKER = re.compile(r"//\s*hot-path-lint:\s*(hot|cold)\b[ \t]*(.*)$")


class Finding:
    def __init__(self, src, line, rule, msg):
        self.src, self.line, self.rule, self.msg = src, line, rule, msg

    def key(self):
        return self.src.rel, self.line, self.rule, self.msg

    def __str__(self):
        return f"{self.src.rel}:{self.line}: [{self.rule}] {self.msg}"


def waivers_of(src):
    """{line: [(rule, reason, waiver line)]}: a waiver covers its own line and, as a comment-only line, the next
    code line below its run of line comments."""
    result = defaultdict(list)
    all_waivers = []
    for idx, raw in enumerate(src.lines):
        m = WAIVER.search(raw)
        if not m:
            continue
        line = idx + 1
        rule, reason = m.group(1), m.group(2).strip()
        all_waivers.append((line, rule, reason))
        result[line].append((rule, reason, line))
        if raw.strip().startswith("//"):
            k = idx + 1
            while k < len(src.lines) and src.lines[k].strip().startswith("//"):
                k += 1
            if k < len(src.lines):
                result[k + 1].append((rule, reason, line))
    return result, all_waivers


def rule_matches(waived_rule, rule):
    return waived_rule == rule or (waived_rule == "alloc" and rule.startswith("alloc-"))


# ---------------------------------------------------------------------------------------------------------------------
# The checks
# ---------------------------------------------------------------------------------------------------------------------

BOXED_WORD = re.compile(r"(?<![\w.])(" + "|".join(BANNED_TYPES) + r"|LongArray|ByteArray|ShortArray|CharArray"
                        r"|UByteArray|UShortArray|ULongArray)\b(?!\s*\()")
BOXED_CTOR = re.compile(r"(?<![\w.])(LongArray|ByteArray|ShortArray|CharArray|UByteArray|UShortArray|ULongArray)\s*\(")
BOXED_CONV = re.compile(r"\.(toLong|toULong|toByte|toUByte|toShort|toUShort|toChar)\s*\(")
BOXED_LITERAL = re.compile(r"(?<![\w.])(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|\d[\d_]*)(L|[uU]L?)\b")
TYPE_CONTEXT = re.compile(r"(?:[:<,(]|\bas\??|\bis|!is)\s*$")


def check_boxed(src):
    code = src.code
    for m in BOXED_WORD.finditer(code):
        word = m.group(1)
        before = code[max(0, m.start() - 40):m.start()]
        if word in BANNED_TYPES and not TYPE_CONTEXT.search(before) and not code.startswith(".", m.end()):
            # `Long` alone outside a type position: a companion read (Long.MAX_VALUE) is caught by the dot test,
            # anything else (an identifier that happens to be the word) is left alone
            continue
        yield src.line_of(m.start()), f"`{word}`: a banned type (code-style 5)"
    for m in BOXED_CTOR.finditer(code):
        yield src.line_of(m.start()), f"`{m.group(1)}(` allocates a boxed-element array"
    for m in BOXED_CONV.finditer(code):
        yield src.line_of(m.start()), f"`.{m.group(1)}()` makes a banned type"
    for m in BOXED_LITERAL.finditer(code):
        yield src.line_of(m.start()), f"`{m.group(0)}` is a {'Long' if m.group(1) == 'L' else 'unsigned'} literal"
    for pos in src.chars:
        yield src.line_of(pos), "a Char literal (a boxed Char on Kotlin/JS; use the Int code)"


def check_fun_interfaces(src, sam_names):
    for m in re.finditer(r"\bfun\s+interface\s+(\w+)", src.code):
        yield src.line_of(m.start()), "fun-interface", f"`fun interface {m.group(1)}`: a SAM type invites captured-var lambdas (performance.md rule 1)"
    for o, kind in src.kind.items():
        if kind.startswith("lambda:") and kind[7:] in sam_names:
            yield src.line_of(o), "sam-ignitor", f"SAM constructor `{kind[7:]} {{ ... }}`: make it a class with fields (performance.md rule 1)"


VAR_DECL = re.compile(r"\bvar\s+(\w+)")


def closures_in(src, f, inline_names, local_funs):
    """(open, close, label) of every closure inside f's body; local funs only when local_funs (a local fun of a
    build function runs at the build and is gone; one of a render function runs with it)."""
    for o, c in src.brace_list:
        if o < f.body_start or o >= f.body_end:
            continue
        kind = src.kind.get(o, "")
        if kind == "object":
            yield o, c, "an anonymous object"
        elif kind.startswith("lambda:"):
            name = kind[7:]
            if name not in inline_names:
                yield o, c, "a stored lambda" if name == "=" else f"a lambda handed to `{name}`" if name else "a lambda"
        elif kind == "fun" and o != f.body_start and local_funs:
            yield o, c, "a local fun"


def check_captured(src, funcs, inline_names, hot):
    code = src.code
    for f in funcs:
        if not f.brace_body:
            continue
        decls = []
        for m in VAR_DECL.finditer(code, f.body_start, f.body_end):
            scope = src.innermost(m.start())
            if scope is None or src.kind.get(scope) in ("class", "object"):
                continue
            # only vars of this function itself: the scope chain up to f's body crosses no fun, class or object
            chain = src.enclosing(m.start(), f.body_start)
            if any(src.kind.get(o) in ("fun", "class", "object") and o != f.body_start for o in chain):
                continue
            decls.append((m.group(1), m.start(), scope))
        if not decls:
            continue
        for o, c, label in closures_in(src, f, inline_names, local_funs=f in hot):
            names = []
            for name, pos, scope in decls:
                if pos > o or not (scope <= o and src.braces[scope] >= c):
                    continue  # declared after the closure, or in a block that does not hold it
                if re.search(r"(?<![\w.])" + re.escape(name) + r"\b", code[o:c]):
                    # a var declared inside the closure with the same name shadows it; rare, accepted
                    names.append(name)
            if names:
                yield src.line_of(o), f"{label} captures local var {', '.join(sorted(set(names)))} (an ObjectRef on Kotlin/JS, performance.md rule 1)"


ALLOC_COLLECTION = re.compile(
    r"(?<![\w.])(listOf|mutableListOf|arrayListOf|setOf|mutableSetOf|hashSetOf|linkedSetOf|sequenceOf|"
    r"buildList|buildSet|emptyArray)\s*[(<{]"
    r"|\.(map|mapIndexed|mapNotNull|flatMap|filter|filterNot|filterIsInstance|toList|toMutableList|toSet|"
    r"toMutableSet|toTypedArray|toDoubleArray|toFloatArray|toIntArray|sorted|sortedBy|sortedDescending|"
    r"sortedByDescending|reversed|zip|distinct|distinctBy|plus|minus|windowed|chunked|asSequence|partition|"
    r"groupBy|take|drop|takeLast|dropLast|subList|slice|withIndex)\s*[({]")
ALLOC_ARRAY = re.compile(
    r"(?<![\w.])(DoubleArray|FloatArray|IntArray|BooleanArray|Array|arrayOf|arrayOfNulls|doubleArrayOf|"
    r"floatArrayOf|intArrayOf|booleanArrayOf)\s*(?:<[^>]*>)?\s*\("
    r"|\.(copyOf|copyOfRange|sliceArray|copyInto)\s*\(")
ALLOC_STRING_CALL = re.compile(r"\.(toString|lowercase|uppercase|toLowerCase|toUpperCase|format|joinToString|"
                               r"padStart|padEnd|substring|replace|trim|split)\s*\(|(?<![\w.])buildString\s*[({]")
ALLOC_PAIR = re.compile(r"(?<![\w.])(Pair|Triple)\s*(?:<[^>]*>)?\s*\(|(?<=[\w)\]\"])[ \t]+to[ \t]+(?=[\w(\"-])")
MAP_OP = re.compile(r"(?<![\w.])(HashMap|LinkedHashMap|HashSet|mutableMapOf|hashMapOf|linkedMapOf|mapOf|"
                    r"emptyMap)\s*[(<]|\.(getOrPut|associate|associateBy|associateWith|toMap|toMutableMap)\s*[({]")
EXCEPTION = re.compile(r"(?<![\w.])(throw|catch)\b|(?<![\w.])(require|requireNotNull|check|checkNotNull|error|TODO)\s*\(")
NULLABLE_NUM = re.compile(r"\b(val|var)\s+(\w+)\s*:\s*(Double|Int|Float|Boolean)\?")
NAN_MERGE = re.compile(r"\belse\s+(Double\.NaN|SLOT_UNSET|NaN)\b(?!\s*[-+*/.])")
WHEN_EXPR = re.compile(r"(?:=|\breturn|\()\s*when\b")
MAP_WALK = re.compile(r"\.(entries|keys|values)\s*(?:\)|\.\s*(?:forEach|forEachIndexed|map|filter|any|all|none|first|"
                      r"firstOrNull|sumOf|count|iterator|toList|sorted|find|fold|maxOf|minOf)\b)")
DESTRUCTURING_LOOP = re.compile(r"\bfor\s*\(\s*\(")
ENUM_VALUES = re.compile(r"(?<![\w.])(?:\w+\.)?values\(\s*\)")
VARARG_MINMAX = re.compile(r"(?<![\w.])(maxOf|minOf)\s*\(")


def top_level_commas(code, a, b):
    depth, n = 0, 0
    for ch in code[a:b]:
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        elif ch == "," and depth == 0:
            n += 1
    return n


CALLABLE_REF = re.compile(r"(?<![:\w])(\w*)::(\w+)")


def in_loop(src, pos, f):
    return any(src.kind.get(o, "").split(":")[-1] in ("for", "while", "do") for o in src.enclosing(pos, f.body_start))


def in_branch(src, pos, f):
    """pos lies in an `if`, `else` or `when` arm of f, or on a line that starts with one."""
    if any(src.kind.get(o, "").split(":")[-1] in ("if", "else", "->") for o in src.enclosing(pos, f.body_start)):
        return True
    return bool(re.match(r"\s*(if\b|\}?\s*else\b|.*->)", src.line_text(src.line_of(pos))))


CONT_END = ("=", "(", ",", ".", "?:", "&&", "||", "->", "+", "-", "*", "/")
CONT_START = (".", "?.", "?:", "&&", "||")


def statement_start(src, pos, lo):
    """The start of the statement that holds pos: its line, extended up over continued lines."""
    code = src.code
    start = code.rfind("\n", 0, pos) + 1
    while start > lo:
        prev_start = code.rfind("\n", 0, start - 1) + 1
        prev = code[prev_start:start - 1].rstrip()
        here = code[start:pos].lstrip()
        if prev.endswith(CONT_END) or here.startswith(CONT_START):
            start = prev_start
        else:
            break
    return start


STORE = re.compile(r"\s*(?:if\s*\(.*\)\s*|\}?\s*else\s*)?(?:this\.)?[\w.]+(?:\[[^\]]*\])?\s*(?:=|\?:=)(?!=)")
STORE_CALL = re.compile(r"\.(add|put|set|push|addLast|addFirst)\s*\($")
ALSO_STORE = re.compile(r"^[^\n;]*?\.also\s*\{\s*(?:\w+\s*->\s*)?[\w.]+(?:\[[^\]]*\])?\s*=\s*(?:it|\w+)\s*\}")


def when_without_else(src, pos):
    """The `when` whose keyword ends at pos has a subject and no `else` arm: Kotlin/JS then compiles a `default` that
    throws and leaves the result unassigned, the shape V8 boxes (performance.md). A `when` with an `else` arm compiles
    to a plain if chain."""
    code = src.code
    m = re.match(r"\s*\(", code[pos:pos + 20])
    if not m:
        return False
    close = src.parens.get(pos + m.end() - 1)
    if close is None:
        return False
    o = code.find("{", close)
    if o < 0 or o not in src.braces or code[close + 1:o].strip():
        return False
    body = code[o + 1:src.braces[o]]
    depth, flat = 0, []
    for ch in body:
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
        elif depth == 0:
            flat.append(ch)
    return not re.search(r"(^|\n)\s*else\s*->", "".join(flat))


def guard_of(src, pos, f):
    """Why an allocation at pos is not a per-block one, or None: a cache filled on a miss (`x ?: Foo()`), or storage
    built inside a branch and kept (`if (buf.size < n) { buf = DoubleArray(n) }`, `pool.add(...)`, `.also { f = it }`):
    the resize-on-shape-change pattern of performance.md rule 2."""
    code = src.code
    st = statement_start(src, pos, f.body_start)
    before = code[st:pos]
    if "?:" in before:
        return "cache on miss"
    stored = (STORE.match(before) and not re.match(r"\s*(val|var)\b", before)) or STORE_CALL.search(before.rstrip())
    end = code.find("\n", pos)
    stored = stored or ALSO_STORE.match(code[pos:end if end > 0 else len(code)])
    if stored and (in_branch(src, pos, f) or re.search(r"\breturn\b", code[f.body_start:st])):
        return "stored behind a guard"
    return None


def check_hot(src, f, ctors, array_aliases, inline_names, guarded=False):
    """(pos, rule, message) for f's body. Allocations behind a guard are dropped, or reported as alloc-guarded."""
    code, a, b = src.code, f.body_start, f.body_end
    region = code[a:b]

    def where(pos):
        return "in a loop" if in_loop(src, pos, f) else "per call"

    def at(m):
        return a + m.start()

    def alloc(pos, rule, msg):
        why = guard_of(src, pos, f)
        if why is None:
            return [(pos, rule, f"{msg} {where(pos)}")]
        return [(pos, "alloc-guarded", f"{msg} ({why}; {rule})")] if guarded else []

    for m in ALLOC_ARRAY.finditer(region):
        what = m.group(1) or m.group(2)
        hint = " (a typed-array view per call on JS: use copyRangeInto)" if what == "copyInto" else ""
        yield from alloc(at(m), "alloc-array", f"`{what}` allocates{hint}")
    if array_aliases:
        alias_re = re.compile(r"(?<![\w.])(" + "|".join(sorted(array_aliases)) + r")\s*\(")
        for m in alias_re.finditer(region):
            yield from alloc(at(m), "alloc-array", f"`{m.group(1)}(` (an array) allocates")
    for m in ALLOC_COLLECTION.finditer(region):
        yield from alloc(at(m), "alloc-collection", f"`{m.group(1) or m.group(2)}` allocates a collection")
    for m in MAP_WALK.finditer(region):
        yield at(m), "map-op", f"`.{m.group(1)}` walks a collection through an iterator (on a map about 110 B per entry on Kotlin/JS; KlangMap has forEachEntry)"
    for m in DESTRUCTURING_LOOP.finditer(region):
        yield at(m), "map-op", "a destructuring `for ((a, b) in ...)`: an iterator and an entry per step (a map or a list of pairs)"
    for m in ENUM_VALUES.finditer(region):
        yield from alloc(at(m), "alloc-array", "`values()` clones the enum's array per call (use `entries`)")
    for m in VARARG_MINMAX.finditer(region):
        close = src.parens.get(at(m) + len(m.group(0)) - 1)
        if close is not None and top_level_commas(code, at(m) + len(m.group(0)), close) >= 3:
            yield from alloc(at(m), "alloc-array", f"`{m.group(1)}` with four or more arguments packs a vararg array")
    for m in MAP_OP.finditer(region):
        what = m.group(1) or m.group(2)
        yield at(m), "map-op", f"`{what}`: a stdlib map allocates (and hashes with a context on Kotlin/JS); use KlangMap or an array"
    for m in ALLOC_PAIR.finditer(region):
        if not m.group(1):
            word = re.search(r"(\w+)\W*$", code[max(a, at(m) - 30):at(m)])
            after = re.match(r"(\w+)", code[a + m.end():a + m.end() + 30])
            if (word and word.group(1) in KOTLIN_KEYWORDS) or (after and after.group(1) in KOTLIN_KEYWORDS):
                continue  # `else to`, `to else`: a variable named `to`, not the infix
        yield from alloc(at(m), "alloc-pair", f"`{m.group(1) or 'to'}` allocates a pair")
    for m in ALLOC_STRING_CALL.finditer(region):
        yield at(m), "alloc-string", f"`{m.group(1) or 'buildString'}` makes a string {where(at(m))}"
    for s, e, templated in src.strings:
        if a <= s < b:
            if templated:
                yield s, "alloc-string", f"a string template builds a string {where(s)}"
            elif code[e:e + 3].strip().startswith("+") or code[max(a, s - 3):s].strip().endswith("+"):
                yield s, "alloc-string", f"string concatenation {where(s)}"
    if ctors:
        ctor_re = re.compile(r"(?<![\w:])(" + "|".join(sorted(ctors, key=len, reverse=True)) + r")\s*(?:<[^>]*>)?\s*\(")
        for m in ctor_re.finditer(region):
            name = m.group(1)
            if name.endswith(("Exception", "Error")) and "throw" in src.line_text(src.line_of(at(m))):
                continue  # the throw is the finding
            yield from alloc(at(m), "alloc-object", f"`{name}(...)` constructs an object")
    for o, c in src.brace_list:
        if a <= o < b:
            kind = src.kind.get(o, "")
            if kind == "object":
                yield from alloc(o, "alloc-object", "`object :` allocates an anonymous object")
            elif kind.startswith("lambda:") and kind[7:] not in inline_names:
                name = kind[7:]
                label = "a stored lambda" if name == "=" else f"a lambda handed to `{name}`" if name else "a lambda"
                yield from alloc(o, "alloc-lambda", f"{label} (not inline) allocates a closure")
    for m in re.finditer(r"\.copy\s*\(", region):
        yield from alloc(at(m), "alloc-object", "`.copy(` allocates a data class copy")
    for m in CALLABLE_REF.finditer(region):
        yield from alloc(at(m), "alloc-object", f"`{m.group(0)}`: a callable reference allocates")
    for m in EXCEPTION.finditer(region):
        what = m.group(1) or m.group(2)
        msg = ("a `catch` in render code: something here is expected to throw (code-style 7)" if what == "catch"
               else f"`{what}` in render code (code-style 7)")
        yield at(m), "exception", msg
    for m in re.finditer(r"!!", region):
        yield at(m), "not-null-assert", "`!!` is a hidden throw (a NullPointerException on null) in render code"
    for m in NULLABLE_NUM.finditer(region):
        yield at(m), "nullable-num", f"`{m.group(2)}: {m.group(3)}?` local: a box on the JVM wherever it is not optimized away"
    for m in NAN_MERGE.finditer(region):
        yield at(m), "nan-merge", f"`else {m.group(1)}` merges a double with the global NaN: one store per arm (performance.md)"
    for m in WHEN_EXPR.finditer(region):
        if in_loop(src, at(m), f) and when_without_else(src, a + m.end()):
            yield at(m), "when-in-loop", "a `when` in expression position inside a loop: a double result boxes on V8 (code-style 19)"


def check_signature(src, f):
    """Nullable numerics in a hot function's parameters and return type."""
    code = src.code
    p = code.find("(", f.pos)
    if p < 0 or p not in src.parens:
        return
    sig = code[p:f.body_start]
    for m in re.finditer(r"(\w+)\s*:\s*(Double|Int|Float|Boolean)\?", sig):
        yield f.pos, "nullable-num", f"`{m.group(1)}: {m.group(2)}?` in the signature of hot `{f.name}`"
    if re.search(r"\)\s*:\s*(Double|Int|Float|Boolean)\?", sig):
        yield f.pos, "nullable-num", f"hot `{f.name}` returns a nullable number (a box on the JVM where not inlined)"


# ---------------------------------------------------------------------------------------------------------------------
# What is render code
# ---------------------------------------------------------------------------------------------------------------------

def camel_prefix(name, prefixes):
    """name is one of prefixes, or starts with one followed by a new camel-case word (`processAndMix` for
    `process`, not `renderingVoiceData` for `render`)."""
    return any(name == p or (name.startswith(p) and name[len(p)].isupper()) for p in prefixes)


def is_cold_name(name):
    return name in COLD_NAMES or camel_prefix(name, COLD_PREFIXES)


def is_root_name(name):
    return name in ROOT_NAMES or camel_prefix(name, ROOT_PREFIXES)


def marker_of(f):
    """'hot', 'cold' or None from a `// hot-path-lint: hot|cold` line in the comment and annotation run above f."""
    idx = f.line - 2
    while idx >= 0:
        raw = f.src.lines[idx].strip()
        m = MARKER.search(raw)
        if m:
            return m.group(1)
        if raw.startswith(("//", "*", "/*", "@")) or raw.endswith("*/"):
            idx -= 1
            continue
        break
    return None


CALL = re.compile(r"(?<![\w])(\w+)\s*(?:\(|\{)")
RECEIVER = re.compile(r"(\w+)\s*\??\.\s*$")


def owner_matches(owner, receiver):
    """A loose type guess from a receiver's name: `glide` for KnobGlide, `lanes` for DriftLanes, `core` for
    EnvelopeCore. Short or generic receivers guess nothing."""
    if not owner or not receiver or len(receiver) < 3 or receiver in ("this", "it", "ctx", "other"):
        return False
    o, r = owner.lower(), receiver.lower().lstrip("_")
    return r in o or o in r


def resolve_call(f, name, receiver, qualified, by_name):
    """The functions a call `name(` in f's body may reach (see "What is render code" in the module doc)."""
    if name in MATH_NAMES:
        # kotlin.math (`sqrt(x)`, `x.pow(2.0)`), unless the receiver names the class that defines it
        return [g for g in by_name[name] if qualified and owner_matches(g.owner, receiver)]
    if qualified:
        candidates = [g for g in by_name[name] if g.owner or g.is_ext]
    else:
        candidates = [g for g in by_name[name] if (g.src is f.src) or (not g.owner and not g.is_ext)
                      or (g.is_ext and f.is_ext)]
    if not candidates:
        return []
    if qualified and receiver:
        guessed = [g for g in candidates if owner_matches(g.owner, receiver)]
        if guessed:
            return guessed
    same = [g for g in candidates if g.src is f.src]
    if same:
        return same
    return candidates if len(candidates) <= CROSS_FILE_LIMIT else []


def hot_functions(sources):
    by_name = defaultdict(list)
    for src in sources:
        if src.cold_file:
            continue
        for f in src.funcs:
            by_name[f.name].append(f)
    hot, queue = {}, []
    for src in sources:
        if src.cold_file:
            continue
        for f in src.funcs:
            marker = marker_of(f)
            if marker == "hot" or (marker is None and is_root_name(f.name) and not is_cold_name(f.name)):
                if f.body_end > f.body_start and "abstract" not in f.modifiers:
                    hot[f] = "root" if marker is None else "marked hot"
                    queue.append(f)
    while queue:
        f = queue.pop()
        code = f.src.code
        for m in CALL.finditer(code, f.body_start, f.body_end):
            name = m.group(1)
            if name not in by_name or is_cold_name(name):
                continue
            before = code[max(f.body_start, m.start() - 60):m.start()]
            qualified = before.rstrip().endswith(".")
            rm = RECEIVER.search(before)
            for g in resolve_call(f, name, rm.group(1) if rm else None, qualified, by_name):
                if g in hot or g is f or marker_of(g) == "cold":
                    continue
                hot[g] = f"called from {f.qual} ({f.src.rel}:{f.line})"
                queue.append(g)
    for f in list(hot):
        if marker_of(f) == "cold":
            del hot[f]
    return hot


# ---------------------------------------------------------------------------------------------------------------------
# Driver
# ---------------------------------------------------------------------------------------------------------------------

def kt_files(paths):
    for p in paths:
        if os.path.isfile(p):
            yield os.path.abspath(p)
            continue
        for dirpath, dirnames, filenames in os.walk(p):
            dirnames[:] = sorted(d for d in dirnames if d not in SKIP_DIRS)
            for fn in sorted(filenames):
                if fn.endswith(".kt"):
                    yield os.path.abspath(os.path.join(dirpath, fn))


def lint(paths, root, rules=None, inline_sources=None, guarded=False):
    """(findings, waived, hot, sources): findings not waived (waiver problems included), waived ones apart."""
    files = sorted(set(kt_files(paths)))
    sources = [Source(p, os.path.relpath(p, root)) for p in files]
    for src in sources:
        rel = src.rel.replace(os.sep, "/")
        src.cold_file = next((why for key, why in COLD_FILES.items() if rel.endswith("/" + key) or rel == key), None)
    inline_names = set(STDLIB_INLINE)
    ctors, array_aliases, sam_names = set(STDLIB_CTORS), set(ARRAY_ALIASES_BUILTIN), set()
    seen = set(files)
    for d in inline_sources if inline_sources is not None else [os.path.join(root, s) for s in INLINE_SOURCES]:
        for p in kt_files([d]) if os.path.exists(d) else []:
            if p in seen:
                continue
            seen.add(p)
            extra = Source(p, os.path.relpath(p, root))
            inline_names |= extra.inline_funs
            ctors |= extra.class_names
            sam_names |= extra.fun_interfaces
            array_aliases |= extra.alias_arrays
    for src in sources:
        inline_names |= src.inline_funs
        ctors |= src.class_names
        array_aliases |= src.alias_arrays
        sam_names |= src.fun_interfaces
    ctors -= array_aliases
    hot = hot_functions(sources)
    raw = []
    for src in sources:
        for line, msg in check_boxed(src):
            raw.append(Finding(src, line, "boxed-type", msg))
        for line, rule, msg in check_fun_interfaces(src, sam_names):
            raw.append(Finding(src, line, rule, msg))
        for line, msg in check_captured(src, src.funcs, inline_names, hot):
            raw.append(Finding(src, line, "captured-var", msg))
    for f in sorted(hot, key=lambda f: (f.src.rel, f.pos)):
        for pos, rule, msg in check_hot(f.src, f, ctors, array_aliases, inline_names, guarded):
            raw.append(Finding(f.src, f.src.line_of(pos), rule, f"{msg} [in {f.qual}]"))
        for pos, rule, msg in check_signature(f.src, f):
            raw.append(Finding(f.src, f.src.line_of(pos), rule, msg))
    unique = {}
    for fd in raw:
        unique.setdefault(fd.key(), fd)
    raw = sorted(unique.values(), key=lambda fd: (fd.src.rel, fd.line, fd.rule, fd.msg))
    if rules:
        raw = [fd for fd in raw if fd.rule in rules]
    findings, waived, used = [], [], set()
    waiver_maps = {src.rel: waivers_of(src) for src in sources}
    for fd in raw:
        covering = [w for w in waiver_maps[fd.src.rel][0].get(fd.line, []) if rule_matches(w[0], fd.rule) and w[1]]
        if covering:
            waived.append(fd)
            used.update((fd.src.rel, w[2]) for w in covering)
        else:
            findings.append(fd)
    for src in sources:
        for line, rule, reason in waiver_maps[src.rel][1]:
            if rules and not any(rule_matches(rule, r) for r in rules):
                continue
            if rule == "alloc-guarded" and not guarded:
                continue  # not reported in this run, so not judged
            if rule not in RULES and rule != "alloc":
                findings.append(Finding(src, line, "bad-waiver", f"waiver names no known rule (`{rule}`)"))
            elif not reason:
                findings.append(Finding(src, line, "bad-waiver", f"waiver for {rule} gives no reason"))
            elif (src.rel, line) not in used:
                findings.append(Finding(src, line, "unused-waiver", f"waiver for {rule} silences nothing"))
    findings.sort(key=lambda fd: (fd.src.rel, fd.line, fd.rule))
    return findings, waived, hot, sources


def git_root():
    try:
        return subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True,
                              check=True).stdout.strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        return os.getcwd()


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("paths", nargs="*")
    p.add_argument("--root", default=None, help="repo root (default: git top level)")
    p.add_argument("--rules", default=None, help="comma-separated rules to report (default: all)")
    p.add_argument("--list-hot", action="store_true", help="print every hot function and why, then the findings")
    p.add_argument("--show-waived", action="store_true", help="print the waived findings too, marked (waived)")
    p.add_argument("--guarded", action="store_true",
                   help="also report allocations behind a guard (a cache miss, a resize on shape change) as alloc-guarded")
    p.add_argument("--self-test", action="store_true", help="run the fixtures in tests/ and exit")
    a = p.parse_args(argv)
    if a.self_test:
        here = os.path.dirname(os.path.abspath(__file__))
        sys.path.insert(0, os.path.join(here, "tests"))
        import unittest
        import test_hot_path_lint
        suite = unittest.defaultTestLoader.loadTestsFromModule(test_hot_path_lint)
        return 0 if unittest.TextTestRunner(verbosity=1).run(suite).wasSuccessful() else 1
    root = os.path.abspath(a.root or git_root())
    paths = a.paths or [os.path.join(root, d) for d in DEFAULT_PATHS]
    rules = set(a.rules.split(",")) if a.rules else None
    if rules and rules - set(RULES):
        p.error(f"unknown rule(s): {', '.join(sorted(rules - set(RULES)))}; known: {', '.join(RULES)}")
    findings, waived, hot, sources = lint(paths, root, rules, guarded=a.guarded or bool(rules and "alloc-guarded" in rules))
    if a.list_hot:
        for f in sorted(hot, key=lambda f: (f.src.rel, f.pos)):
            print(f"{f.src.rel}:{f.line}: hot {f.qual}: {hot[f]}")
    for fd in findings:
        print(fd)
    if a.show_waived:
        for fd in waived:
            print(f"{fd} (waived)")
    counts = defaultdict(int)
    for fd in findings:
        counts[fd.rule] += 1
    per_rule = ", ".join(f"{r} {counts[r]}" for r in RULES if counts[r])
    print(f"hot-path-lint: {len(sources)} file(s), {len(hot)} hot function(s), {len(findings)} finding(s)"
          f"{' (' + per_rule + ')' if per_rule else ''}, {len(waived)} waived", file=sys.stderr)
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
