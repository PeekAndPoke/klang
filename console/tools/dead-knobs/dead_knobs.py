#!/usr/bin/env python3
"""Dead-knob finder: every slot key a door writes has an engine reader, and the reverse.

  dead_knobs.py [--root DIR] [--waivers FILE] [--list] [--strict]

A note carries two slot maps over the wire, `VoiceData.ignitorParams` and `VoiceData.katalystParams`. The
WRITERS are the frontends (sprudel's doors and their shared key tables); the READERS are the engine
(`audio_bridge`, `audio_be`): the slot declarations (`IgnitorDsl.Slots`, `KatalystDsl.Slots`, a `Param` built
with a literal name) and the code that places or reads them. This tool reads the Kotlin sources (no build)
and reports, per map:

  (a) written, never read            a dead knob: a door writes a key no engine code declares or reads, or
                                     one that `Slots` declares and no engine code places or reads
  (b) read, written by no door       a knob only a raw `ignp` / `katp` reaches (often deliberate; info)
  (c) near misses                    a key written but not read that looks like a read key (case, a dot,
                                     edit distance 1 or 2), or that the OTHER map reads
  (d) fill defaults that differ      one key filled by `setOrDefault` with different defaults (info)
  (e) declared, never placed         a `Slots` key no engine code places or reads (info)

Exit 1 on unwaived (a) or (c) and on a writer whose key the tool cannot name; 0 otherwise. `--strict` also
fails on (b), (e) and unused waivers. Exit 2 when the tool cannot see the layout it expects (it never reports
"clean" on a tree it could not read).

The waiver file (default: waivers.txt next to this script) holds `<map> <key> <reason>` lines; the reason is
required, `#` starts a comment. A waiver silences that key in every section; one that silences nothing is
reported as unused.

How keys are resolved: string literals; the `Param` objects of `IgnitorDsl.Slots` / `KatalystDsl.Slots` by
path (`IgnitorDsl.Slots.lpf.freq`, `s.lpf.freq` after `val s = IgnitorDsl.Slots`, bare `Slots.x` inside the
DSL's own file, `(x as IgnitorDsl.Param).name`); the key objects of the frontends (`IgnitorSlotKeys.analog`,
`ClassicSlotKeys` through `val k = ClassicSlotKeys`, its `pass(...)` / `band(...)` composites behind
`putFilter`); and a `const val` holding a string. A writer whose key is a bare parameter (`ignitorParam(slot,
...)`, `putKatalystParam(name, ...)`) is OPEN: listed, never counted. Script text inside raw strings (songs,
tutorials) and comments are not read at all.

Standard library only.
"""
import argparse
import bisect
import os
import re
import subprocess
import sys
from collections import defaultdict, namedtuple

HERE = os.path.dirname(os.path.abspath(__file__))
MAPS = ("ignitor", "katalyst")

# The engine side: what is declared or read here is a READER. Everything else under <module>/src/<set>Main/ is
# a frontend, and what it writes is a DOOR write.
ENGINE_MODULES = {"audio_be", "audio_bridge"}

# Not doors and not the engine: scaffolding that fills the maps by hand to time or warm something.
EXCLUDED_MODULES = {"audio_benchmark", "klangscript-ksp", "audio-wire-codec-ksp"}
EXCLUDED_FILE = re.compile(r"Benchmark")

SKIP_DIRS = {".git", "node_modules", "build", ".gradle", ".idea", ".kotlin", "kotlin-js-store", ".build-claude",
             ".gradle-claude"}

Site = namedtuple("Site", "path line kind context")  # kind: door | engine | decl | use | inline


# ── Reading Kotlin ──────────────────────────────────────────────────────────────────────────────────────────


class Source:
    """One Kotlin file with comments and raw-string bodies blanked (same length, newlines kept), and a mask of
    the positions inside ordinary string literals, so a match that starts in a string is not taken as code."""

    def __init__(self, root, rel, text):
        self.rel = rel
        self.module = rel.split("/", 1)[0] if rel.count("/") >= 3 and rel.split("/")[1] == "src" else ""
        self.engine = self.module in ENGINE_MODULES
        self.code, self.in_string = strip_kotlin(text)
        self.line_starts = [0] + [i + 1 for i, ch in enumerate(self.code) if ch == "\n"]
        self.lines = self.code.split("\n")

    def line_of(self, pos):
        return bisect.bisect_right(self.line_starts, pos)

    def context(self, line):
        """Where a site sits: the nearest `fun` above [line], a class or object (indent 4 at most), or a top-level
        val. A local val is not a context."""
        for n in range(line - 1, -1, -1):
            text = self.lines[n]
            indent = len(text) - len(text.lstrip())
            m = DECL_LINE.match(text)
            if not m:
                continue
            word = m.group(1)
            if word == "fun" or (word in ("class", "object") and indent <= 4) or indent == 0:
                return m.group(2)
        return ""


DECL_LINE = re.compile(r"^\s*(?:(?:private|internal|public|protected|override|inline|const|data|operator|"
                       r"infix|suspend|abstract|open|sealed)\s+)*(fun|val|var|object|class)\s+(?:<[^>]*>\s*)?"
                       r"(?:[\w.?<>, ]+\.)?(\w+)")


def strip_kotlin(text):
    out = list(text)
    in_string = bytearray(len(text))
    i, n = 0, len(text)

    def blank(a, b):
        for j in range(a, b):
            if out[j] != "\n":
                out[j] = " "

    while i < n:
        c = text[i]
        if text.startswith("//", i):
            j = text.find("\n", i)
            j = n if j < 0 else j
            blank(i, j)
            i = j
        elif text.startswith("/*", i):
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
        elif text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j < 0 else j
            while j + 3 < n and text[j + 3] == '"':  # a raw string may end in quotes: """a""""
                j += 1
            blank(i + 3, j)
            i = j + 3
        elif c == '"':
            j = i + 1
            while j < n and text[j] != '"' and text[j] != "\n":
                j += 2 if text[j] == "\\" else 1
            for k in range(i + 1, min(j, n)):
                in_string[k] = 1
            i = j + 1
        elif c == "'":
            m = re.match(r"'(?:\\u[0-9a-fA-F]{4}|\\.|[^\\'\n])'", text[i:i + 8])
            i += len(m.group(0)) if m else 1
        else:
            i += 1
    return "".join(out), in_string


def call_args(code, open_paren):
    """The top-level arguments of the call whose '(' is at [open_paren], as (text, start) pairs, and the end."""
    depth, i, start, args, n = 0, open_paren, open_paren + 1, [], len(code)
    in_str = False
    while i < n:
        c = code[i]
        if in_str:
            if c == "\\":
                i += 2
                continue
            if c == '"':
                in_str = False
        elif c == '"':
            in_str = True
        elif c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
            if depth == 0:
                args.append((code[start:i], start))
                return [(a.strip(), s) for a, s in args if a.strip()], i  # a trailing comma is no argument
        elif c == "," and depth == 1:
            args.append((code[start:i], start))
            start = i + 1
        i += 1
    return [], n


def named_or_positional(args, names):
    """{name: text} from a call's arguments, named (`door = "x"`) or positional in the order of [names]."""
    got, pos = {}, 0
    for text, _ in args:
        m = re.match(r"^(\w+)\s*=(?!=)\s*(.*)$", text, re.S)
        if m:
            got[m.group(1)] = m.group(2).strip()
        else:
            if pos < len(names):
                got[names[pos]] = text
            pos += 1
    return got


def literal(text):
    m = re.fullmatch(r'"([^"\\$]*)"', text.strip())
    return m.group(1) if m else None


def block_end(code, open_brace):
    depth = 0
    for i in range(open_brace, len(code)):
        if code[i] == "{":
            depth += 1
        elif code[i] == "}":
            depth -= 1
            if depth == 0:
                return i
    return len(code)


# ── The files ───────────────────────────────────────────────────────────────────────────────────────────────


MAIN_SOURCE = re.compile(r"^(?:[\w.-]+/)?src/\w*Main/.*\.kt$")


def source_files(root):
    r = subprocess.run(["git", "ls-files", "-co", "--exclude-standard", "-z"], cwd=root, capture_output=True,
                       text=True)
    if r.returncode == 0:
        rels = [f for f in r.stdout.split("\0") if f]
    else:
        rels = []
        for dirpath, dirnames, filenames in os.walk(root):
            dirnames[:] = sorted(d for d in dirnames if d not in SKIP_DIRS)
            rels += [os.path.relpath(os.path.join(dirpath, f), root).replace(os.sep, "/") for f in filenames]
    out = []
    for rel in sorted(rels):
        if not MAIN_SOURCE.match(rel) or not os.path.isfile(os.path.join(root, rel)):
            continue
        module = rel.split("/", 1)[0] if not rel.startswith("src/") else ""
        if module in EXCLUDED_MODULES or EXCLUDED_FILE.search(os.path.basename(rel)):
            continue
        out.append(rel)
    return out


def load(root):
    sources = []
    for rel in source_files(root):
        with open(os.path.join(root, rel), encoding="utf-8", errors="replace") as f:
            sources.append(Source(root, rel, f.read()))
    return sources


# ── The slot vocabulary: Slots objects, their groups, the key objects, constants ─────────────────────────────


class ToolError(Exception):
    pass


SLOT_HELPERS = {"slot": (("door", "param"), r'"\$door\.\$param"'),
                "knob": (("stage", "knob"), r'"\$stage\.\$knob"')}
DSL_OBJECTS = {"IgnitorDsl": "ignitor", "KatalystDsl": "katalyst"}
PARAM_CTOR = re.compile(r"(?<![\w.])(IgnitorDsl\.|KatalystKnobDsl\.)?Param\s*\(")


class Vocabulary:
    def __init__(self, sources):
        self.leaves = {}  # "IgnitorDsl.Slots.lpf.freq" -> (map, key, default, Site)
        self.groups = defaultdict(list)  # "IgnitorDsl.Slots.lpf" -> [leaf paths]
        self.key_objects = {}  # "IgnitorSlotKeys.analog" -> [keys]
        self.consts = {}
        self.decl_lines = set()  # (rel, line) of every declaration the vocabulary itself holds
        self._check_helpers(sources)
        classes = self._group_classes(sources)
        for src in sources:
            for m in re.finditer(r"\bobject\s+Slots\s*\{", src.code):
                owner = self._enclosing_dsl(src, m.start())
                if owner:
                    self._slots_object(src, owner, m.end() - 1, classes)
        for src in sources:
            for m in re.finditer(r'\bconst\s+val\s+(\w+)\s*(?::\s*String\s*)?=\s*"([^"\\$]*)"', src.code):
                self.consts.setdefault(m.group(1), set()).add(m.group(2))
        for src in sources:
            if not src.engine:
                self._key_objects(src)
        for name in DSL_OBJECTS:
            if not any(p.startswith(name + ".Slots.") for p in self.leaves):
                raise ToolError(f"no {name}.Slots object with slots found; has the layout changed?")

    def _check_helpers(self, sources):
        """The group classes build keys through `slot(door, param)` and `knob(stage, knob)`; the tool assumes
        their key is `door.param` / `stage.knob` and says so loudly if the helper stops building exactly that."""
        self.helpers = {}
        for src in sources:
            if not src.engine:
                continue
            for name, (params, body) in SLOT_HELPERS.items():
                m = re.search(rf"\bfun\s+{name}\s*\(\s*{params[0]}\s*:\s*String\s*,\s*{params[1]}\s*:\s*String", src.code)
                if not m:
                    continue
                tail = src.code[m.end():m.end() + 400]
                if not re.search(body, tail):
                    raise ToolError(f"{src.rel}:{src.line_of(m.start())}: `{name}(...)` no longer builds "
                                    f"{body.replace(chr(92), '')}; teach dead_knobs.py the new key shape")
                self.helpers[name] = params

    def _enclosing_dsl(self, src, pos):
        best = None
        for m in re.finditer(r"\b(?:data\s+)?(?:class|interface|object)\s+(IgnitorDsl|KatalystDsl)\b", src.code[:pos]):
            best = m.group(1)
        return best

    def _group_classes(self, sources):
        """class X internal constructor(door: String) { val freq: T = slot(door = door, param = "freq", ...) }"""
        classes = {}
        for src in sources:
            if not src.engine:
                continue
            for m in re.finditer(r"\bclass\s+(\w+)\s+(?:internal\s+)?constructor\s*\(([^)]*)\)\s*\{", src.code):
                ctor = [p.split(":")[0].strip() for p in m.group(2).split(",") if p.strip()]
                body_start, body_end = m.end() - 1, block_end(src.code, m.end() - 1)
                fields = {}
                for f in re.finditer(r"\bval\s+(\w+)\s*:\s*[\w.]+\s*=\s*", src.code[body_start:body_end]):
                    at = body_start + f.end()
                    fields_entry = self._field(src, at)
                    if fields_entry:
                        fields[f.group(1)] = fields_entry + (src.line_of(body_start + f.start()),)
                if fields:
                    classes[m.group(1)] = (src, ctor, fields)
        return classes

    def _field(self, src, at):
        """(template parts, default) of a slot field's initializer: `slot(...)`, `knob(...)` or `Param(name=...)`."""
        m = re.match(r"(\w+)\s*\(|(IgnitorDsl\.|KatalystKnobDsl\.)?Param\s*\(", src.code[at:])
        if not m:
            return None
        args, _ = call_args(src.code, at + m.end() - 1)
        if m.group(1) in self.helpers:
            a, b = self.helpers[m.group(1)]
            got = named_or_positional(args, [a, b, "default"])
            return (got.get(a), got.get(b)), got.get("default"), None
        if m.group(1) is None or m.group(1) == "Param":
            got = named_or_positional(args, ["name", "default"])
            return (got.get("name"),), got.get("default"), None
        return None

    def _slots_object(self, src, owner, brace, classes):
        mp = DSL_OBJECTS[owner]
        end = block_end(src.code, brace)
        body = src.code[brace + 1:end]
        depth_at = _depths(body)
        for f in re.finditer(r"\bval\s+(\w+)\s*:\s*[\w.]+\s*=\s*", body):
            if depth_at[f.start()] != 0:
                continue
            at = brace + 1 + f.end()
            line = src.line_of(brace + 1 + f.start())
            path = f"{owner}.Slots.{f.group(1)}"
            entry = self._field(src, at)
            if entry and entry[0] and len(entry[0]) == 1:
                key = literal(entry[0][0] or "")
                if key is not None:
                    self.leaves[path] = (mp, key, entry[1], Site(src.rel, line, "decl", path))
                    self.decl_lines.add((src.rel, line))
                continue
            g = re.match(r"(\w+)\s*\(([^)]*)\)", src.code[at:])
            if not g or g.group(1) not in classes:
                continue
            csrc, ctor, fields = classes[g.group(1)]
            ctor_args = named_or_positional([(a.strip(), 0) for a in g.group(2).split(",") if a.strip()], ctor)
            for fname, (parts, default, _, fline) in fields.items():
                values = []
                for part in parts:
                    lit = literal(part or "")
                    if lit is None and part in ctor_args:
                        lit = literal(ctor_args[part])
                    values.append(lit)
                if None in values:
                    continue
                leaf = f"{path}.{fname}"
                site = Site(csrc.rel, fline, "decl", f"{leaf} (built at {src.rel}:{line})")
                self.leaves[leaf] = (mp, ".".join(values), default, site)
                self.groups[path].append(leaf)
                self.decl_lines.add((csrc.rel, fline))
            self.decl_lines.add((src.rel, line))

    def _key_objects(self, src):
        """object IgnitorSlotKeys { private val s = IgnitorDsl.Slots; val analog: String = name(s.analog) }"""
        for m in re.finditer(r"\bobject\s+(\w+)\s*\{", src.code):
            obj, brace = m.group(1), m.end() - 1
            end = block_end(src.code, brace)
            body = src.code[brace + 1:end]
            aliases = aliases_in(body, self)
            helpers = {}
            for h in re.finditer(r"\bfun\s+(\w+)\s*\(([^)]*)\)\s*=\s*\w+\s*\(", body):
                params = [p.split(":")[0].strip() for p in h.group(2).split(",") if p.strip()]
                args, _ = call_args(body, h.end() - 1)
                fields = []
                for text, _ in args:
                    a = re.fullmatch(r"name\s*\(\s*(\w+)\.(\w+)\s*\)", text)
                    if a and a.group(1) in params:
                        fields.append((params.index(a.group(1)), a.group(2)))
                helpers[h.group(1)] = fields
            depth_at = _depths(body)
            for f in re.finditer(r"\bval\s+(\w+)\s*(?::\s*String\s*)?=\s*([^\n]+)", body):
                if depth_at[f.start()] != 0:
                    continue
                expr = f.group(2).strip()
                keys = None
                c = re.fullmatch(r"(\w+)\s*\((.*)\)", expr)
                if c and c.group(1) == "name":
                    keys = self.resolve_path(c.group(2), aliases, src)
                elif c and c.group(1) in helpers:
                    call = [a.strip() for a in c.group(2).split(",")]
                    keys = []
                    for idx, field in helpers[c.group(1)]:
                        got = self.resolve_path(f"{call[idx]}.{field}", aliases, src) if idx < len(call) else None
                        if got is None:
                            keys = None
                            break
                        keys += got
                elif literal(expr) is not None:
                    keys = [literal(expr)]
                if keys:
                    self.key_objects[f"{obj}.{f.group(1)}"] = keys

    def resolve_path(self, expr, aliases, src, line=None):
        """Keys of a Slots path or a key-object member, or None."""
        expr = expr.strip()
        m = re.fullmatch(r"\(\s*(.+?)\s+as\??\s+[\w.]+\s*\)\s*\.\s*name", expr) or \
            re.fullmatch(r"(.+?)\s*\.\s*name", expr)
        if m and not expr.startswith('"'):
            got = self.resolve_path(m.group(1), aliases, src, line)
            if got is not None:
                return got
        parts = [p.strip() for p in expr.split(".")]
        if not all(re.fullmatch(r"\w+", p) for p in parts):
            return None
        head = alias_target(aliases, parts[0], line)
        candidates = []
        if head:
            candidates.append(".".join([head] + parts[1:]))
        elif parts[0] == "Slots":
            own = owner_of_file(src)
            candidates += [f"{own}.{expr}"] if own else [f"{o}.{expr}" for o in DSL_OBJECTS]
        else:
            candidates.append(expr)
        found = []
        for c in candidates:
            if c in self.leaves:
                found.append([self.leaves[c][1]])
            elif c in self.key_objects:
                found.append(self.key_objects[c])
        return found[0] if len(found) == 1 else None

    def resolve_key(self, expr, aliases, src, line):
        """('ok', [keys]) | ('open', None) | ('unresolved', None) for a writer's key argument."""
        expr = expr.strip()
        lit = literal(expr)
        if lit is not None:
            return "ok", [lit]
        got = self.resolve_path(expr, aliases, src, line)
        if got:
            return "ok", got
        if re.fullmatch(r"[A-Z][A-Z0-9_]*", expr) and len(self.consts.get(expr, ())) == 1:
            return "ok", list(self.consts[expr])
        if re.fullmatch(r"[a-z]\w*", expr):
            return "open", None
        return "unresolved", None


def _depths(body):
    """Brace depth at every position of [body], so only an object's own top-level vals are read."""
    depth, out = 0, bytearray(len(body) + 1)
    for i, c in enumerate(body):
        out[i] = min(depth, 255)
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
    return out


def owner_of_file(src):
    base = os.path.basename(src.rel)
    for name in DSL_OBJECTS:
        if base.startswith(name):
            return name
    return None


ALIAS = re.compile(r"\bva[lr]\s+(\w+)\s*(?::\s*[\w.]+\s*)?=\s*([A-Z][\w]*(?:\.Slots)?)\s*$", re.M)
LET_ALIAS = re.compile(r"\b((?:IgnitorDsl|KatalystDsl)\.Slots)\s*\.\s*let\s*\{\s*(\w+)\s*->")


def aliases_in(code, vocab, src=None):
    """{name: [(line or pos, target)]}: `val s = IgnitorDsl.Slots`, `val k = ClassicSlotKeys`, `.let { s ->`."""
    out = defaultdict(list)
    objects = {k.split(".")[0] for k in vocab.key_objects} | {f"{o}.Slots" for o in DSL_OBJECTS}
    for m in ALIAS.finditer(code):
        if m.group(2) in objects or (m.group(2).endswith(".Slots") and m.group(2).split(".")[0] in DSL_OBJECTS):
            where = src.line_of(m.start()) if src else 0
            out[m.group(1)].append((where, m.group(2)))
    for m in LET_ALIAS.finditer(code):
        where = src.line_of(m.start()) if src else 0
        out[m.group(2)].append((where, m.group(1)))
    return out


def alias_target(aliases, name, line):
    best = None
    for where, target in aliases.get(name, ()):
        if line is None or where <= line:
            best = target
    return best


# ── Writers ─────────────────────────────────────────────────────────────────────────────────────────────────

DIRECT_WRITERS = {"putIgnitorParam": "ignitor", "ignitorParam": "ignitor", "ignp": "ignitor",
                  "putKatalystParam": "katalyst", "katalystParam": "katalyst", "katp": "katalyst"}
DIRECT_CALL = re.compile(r"(?<![\w$])(" + "|".join(DIRECT_WRITERS) + r")\s*\(")
FUN_DEF_BEFORE = re.compile(r"\bfun\s+(?:<[^>]*>\s*)?(?:[\w.?<>, ]+\.)?\s*$")
BAG_BIND = re.compile(r"\bva[lr]\s+(\w+)\s*=\s*(?:\w+\s*\.\s*)?(ignitorParamsOrNew|katalystParamsOrNew)\s*\(\s*\)|"
                      r"\bva[lr]\s+(\w+)\s*=\s*(ClassicSlotParams)\s*\(")
BAG_SOURCES = {"ignitorParamsOrNew": "ignitor", "katalystParamsOrNew": "katalyst", "ClassicSlotParams": "ignitor"}
BAG_METHODS = {"ignitorParamsOrNew": {"set", "setOrDefault"}, "katalystParamsOrNew": {"set", "setOrDefault"},
               "ClassicSlotParams": {"put", "putCurve", "putFilter"}}
BAG_CALL = re.compile(r"(?<![\w$])(\w+)(\s*\(\s*\))?\s*\.\s*(setOrDefault|set|putCurve|putFilter|put)\s*\(")
MAP_LITERAL = re.compile(r"\b(ignitorParams|katalystParams)\s*=\s*klangMapOf\s*\(|"
                         r"\bwith(Ignitor|Katalyst)Params\s*\(")


class Scan:
    def __init__(self):
        self.written = {mp: defaultdict(list) for mp in MAPS}
        self.read = {mp: defaultdict(list) for mp in MAPS}
        self.open_writers, self.unresolved, self.open_readers = [], [], []
        self.fill_defaults = {mp: defaultdict(list) for mp in MAPS}
        self.declared_default = {mp: {} for mp in MAPS}


def scan_writers(src, vocab, scan):
    aliases = aliases_in(src.code, vocab, src)
    kind = "engine" if src.engine else "door"

    def record(mp, expr, pos, fill_default=None):
        line = src.line_of(pos)
        site = Site(src.rel, line, kind, src.context(line))
        status, keys = vocab.resolve_key(expr, aliases, src, line)
        if status == "ok":
            for key in keys:
                scan.written[mp][key].append(site)
                if fill_default is not None:
                    scan.fill_defaults[mp][key].append((site, fill_default))
        elif status == "open":
            scan.open_writers.append((mp, site, expr))
        else:
            scan.unresolved.append((mp, site, expr))

    for m in DIRECT_CALL.finditer(src.code):
        if src.in_string[m.start()]:
            continue
        line_start = src.code.rfind("\n", 0, m.start()) + 1
        before = src.code[line_start:m.start()]
        if FUN_DEF_BEFORE.search(before) or before.lstrip().startswith("import"):
            continue
        args, _ = call_args(src.code, m.end() - 1)
        if args:
            record(DIRECT_WRITERS[m.group(1)], args[0][0], m.start())

    bags = []  # (pos, name, source function): the latest binding above a call wins
    for m in BAG_BIND.finditer(src.code):
        name, fn = (m.group(1), m.group(2)) if m.group(1) else (m.group(3), m.group(4))
        bags.append((m.start(), name, fn))
    for m in BAG_CALL.finditer(src.code):
        if src.in_string[m.start()]:
            continue
        recv, called, method = m.group(1), m.group(2), m.group(3)
        fn = None
        if called:
            fn = recv if recv in ("ignitorParamsOrNew", "katalystParamsOrNew") else None
        else:
            for pos, name, bound in bags:
                if name == recv and pos < m.start():
                    fn = bound
        if fn is None or method not in BAG_METHODS[fn]:
            continue
        mp = BAG_SOURCES[fn]
        args, _ = call_args(src.code, m.end() - 1)
        idx = 1 if method == "putFilter" else 0
        if len(args) <= idx:
            continue
        fill = None
        if method == "setOrDefault":
            fill = named_or_positional(args, ["name", "value", "default"]).get("default")
        record(mp, args[idx][0], m.start(), fill)

    for m in MAP_LITERAL.finditer(src.code):
        if src.in_string[m.start()]:
            continue
        mp = "ignitor" if (m.group(1) == "ignitorParams" or m.group(2) == "Ignitor") else "katalyst"
        args, _ = call_args(src.code, m.end() - 1)
        for text, start in args:
            pair = re.match(r'("[^"\\$]*")\s+to\b', text)
            if pair:
                record(mp, pair.group(1), start + (len(src.code[start:]) - len(src.code[start:].lstrip())))


# ── Readers ─────────────────────────────────────────────────────────────────────────────────────────────────

LITERAL_READ = re.compile(r'\b(ignitorParams|katalystParams)\s*(?:\?\.|\.)\s*get\s*\(\s*"([^"\\$]+)"\s*\)|'
                          r'\b(ignitorParams|katalystParams)\s*\[\s*"([^"\\$]+)"\s*\](?!\s*=[^=])')
PATH_REF = re.compile(r"(?<![\w.$])((?:IgnitorDsl|KatalystDsl)\.Slots|Slots|[a-z]\w*)((?:\s*\.\s*\w+)+)")


def scan_readers(src, vocab, scan):
    aliases = aliases_in(src.code, vocab, src)
    seen = set()

    def add(mp, key, line, kind, ctx):
        if (mp, key, line, kind) in seen:
            return
        seen.add((mp, key, line, kind))
        scan.read[mp][key].append(Site(src.rel, line, kind, ctx))

    for m in PARAM_CTOR.finditer(src.code):
        if src.in_string[m.start()]:
            continue
        line = src.line_of(m.start())
        if (src.rel, line) in vocab.decl_lines:
            continue
        before = src.code[src.code.rfind("\n", 0, m.start()) + 1:m.start()]
        if re.search(r"\bclass\s*$", before):
            continue
        args, _ = call_args(src.code, m.end() - 1)
        got = named_or_positional(args, ["name", "default"])
        name = got.get("name", "")
        if m.group(1) == "KatalystKnobDsl." or (not m.group(1) and os.path.basename(src.rel).startswith("Katalyst")):
            mp = "katalyst"
        else:
            mp = "ignitor"
        lit = literal(name)
        if lit is not None:
            add(mp, lit, line, "inline", src.context(line))
        elif re.fullmatch(r"\w+", name or ""):
            scan.open_readers.append((mp, Site(src.rel, line, "decl", src.context(line)), name))

    for m in PATH_REF.finditer(src.code):
        if src.in_string[m.start()]:
            continue
        line = src.line_of(m.start())
        if (src.rel, line) in vocab.decl_lines:
            continue
        head = m.group(1)
        if head[0].islower() and not alias_target(aliases, head, line):
            continue
        parts = [p.strip() for p in m.group(2).split(".") if p.strip()]
        # the longest prefix that names a leaf
        for cut in range(len(parts), 0, -1):
            keys = vocab.resolve_path(".".join([head] + parts[:cut]), aliases, src, line)
            if keys:
                full = alias_target(aliases, head, line) or head
                mp = _map_of_path(vocab, full, parts[:cut], src)
                if mp:
                    for key in keys:
                        add(mp, key, line, "use", src.context(line))
                break

    for m in LITERAL_READ.finditer(src.code):
        which, key = (m.group(1), m.group(2)) if m.group(1) else (m.group(3), m.group(4))
        line = src.line_of(m.start())
        add("ignitor" if which == "ignitorParams" else "katalyst", key, line, "use", src.context(line))


def _map_of_path(vocab, head, parts, src):
    if head == "Slots":
        own = owner_of_file(src)
        heads = [f"{own}.Slots"] if own else [f"{o}.Slots" for o in DSL_OBJECTS]
    else:
        heads = [head]
    for h in heads:
        leaf = ".".join([h] + parts)
        if leaf in vocab.leaves:
            return vocab.leaves[leaf][0]
    return None


# ── Findings ────────────────────────────────────────────────────────────────────────────────────────────────


def levenshtein(a, b, cap=3):
    if abs(len(a) - len(b)) >= cap:
        return cap
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def near(w, r):
    """Why [w] looks like [r], or None. Edit distance 2 only for keys of 6 characters or more."""
    if w.lower() == r.lower():
        return "differs in case"
    if w.replace(".", "").lower() == r.replace(".", "").lower():
        return "differs in a dot"
    d = levenshtein(w, r)
    if d == 1 or (d == 2 and min(len(w), len(r)) >= 6):
        return f"edit distance {d}"
    return None


def analyse(scan):
    f = {"a": [], "b": [], "c": [], "d": [], "e": []}
    for mp in MAPS:
        written, read = scan.written[mp], scan.read[mp]
        other = scan.read[MAPS[1 - MAPS.index(mp)]]
        for key in sorted(written):
            placed = any(s.kind in ("use", "inline") for s in read.get(key, ()))
            if key in read and not placed and any(s.kind == "door" for s in written[key]):
                f["a"].append((mp, key))  # a door writes it, Slots declares it, no engine code places it
            if key not in read:
                f["a"].append((mp, key))
                for r in sorted(read):
                    why = near(key, r)
                    if why:
                        f["c"].append((mp, key, r, why))
                if key in other:
                    f["c"].append((mp, key, key, f"read on the other map ({MAPS[1 - MAPS.index(mp)]})"))
        for key in sorted(read):
            if not any(s.kind == "door" for s in written.get(key, ())):
                f["b"].append((mp, key))
            sites = read[key]
            if any(s.kind == "decl" for s in sites) and not any(s.kind in ("use", "inline") for s in sites):
                f["e"].append((mp, key))
        for key in sorted(scan.fill_defaults[mp]):
            values = {d for _, d in scan.fill_defaults[mp][key]}
            if len(values) > 1:
                f["d"].append((mp, key))
    return f


def read_waivers(path):
    waivers, errors = {}, []
    if not os.path.exists(path):
        return waivers, errors
    with open(path, encoding="utf-8") as fh:
        for n, raw in enumerate(fh, 1):
            line = raw.split("#", 1)[0].strip()
            if not line:
                continue
            parts = line.split(None, 2)
            if len(parts) < 3 or parts[0] not in MAPS:
                errors.append(f"{path}:{n}: expected `<ignitor|katalyst> <key> <reason>`, got: {raw.strip()}")
                continue
            waivers[(parts[0], parts[1])] = (parts[2], n)
    return waivers, errors


# ── Output ──────────────────────────────────────────────────────────────────────────────────────────────────


def fmt_site(s):
    ctx = f" ({s.context})" if s.context else ""
    return f"{s.path}:{s.line}{ctx}"


def fmt_sites(sites, limit=None):
    sites = sorted(set(sites), key=lambda s: (s.path, s.line))
    shown = sites if limit is None else sites[:limit]
    more = f" (+{len(sites) - len(shown)} more)" if len(sites) > len(shown) else ""
    return shown, more


def print_list(scan):
    print("map       key                         writers / readers")
    for mp in MAPS:
        keys = sorted(set(scan.written[mp]) | set(scan.read[mp]))
        for key in keys:
            w, r = scan.written[mp].get(key, []), scan.read[mp].get(key, [])
            default = ""
            for path, (lmp, lkey, ldefault, _) in VOCAB_FOR_LIST.leaves.items():
                if lmp == mp and lkey == key and ldefault:
                    default = f"  default {ldefault}"
            print(f"{mp:<9} {key:<27} W{len(w)} R{len(r)}{default}")
            for s in sorted(set(w), key=lambda s: (s.path, s.line)):
                print(f"    W {s.kind:<6} {fmt_site(s)}")
            for s in sorted(set(r), key=lambda s: (s.path, s.line)):
                print(f"    R {s.kind:<6} {fmt_site(s)}")
    print()


VOCAB_FOR_LIST = None


def main(argv=None):
    global VOCAB_FOR_LIST
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--root", default=None, help="repo root (default: git top level)")
    p.add_argument("--waivers", default=os.path.join(HERE, "waivers.txt"), help="waiver file")
    p.add_argument("--list", action="store_true", help="print the whole key table (key, map, writers, readers)")
    p.add_argument("--strict", action="store_true", help="also fail on (b), (e), unresolved writers, unused waivers")
    a = p.parse_args(argv)
    try:
        root = a.root or subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True,
                                        check=True).stdout.strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        root = os.getcwd()
    root = os.path.abspath(root)

    try:
        sources = load(root)
        vocab = Vocabulary(sources)
    except ToolError as e:
        print(f"dead-knobs: {e}", file=sys.stderr)
        return 2
    VOCAB_FOR_LIST = vocab
    scan = Scan()
    for src in sources:
        scan_writers(src, vocab, scan)
        if src.engine:
            scan_readers(src, vocab, scan)
    for path, (mp, key, default, site) in vocab.leaves.items():
        scan.read[mp][key].append(site)
    for mp in MAPS:
        if not scan.written[mp] or not scan.read[mp]:
            print(f"dead-knobs: no {mp} {'writers' if not scan.written[mp] else 'readers'} found; "
                  f"has the layout changed?", file=sys.stderr)
            return 2

    waivers, errors = read_waivers(a.waivers)
    if errors:
        for e in errors:
            print(e, file=sys.stderr)
        return 2
    used = set()

    def waived(mp, key):
        if (mp, key) in waivers:
            used.add((mp, key))
            return True
        return False

    found = analyse(scan)
    found = {k: [x for x in v if not waived(x[0], x[1])] for k, v in found.items()}

    if a.list:
        print_list(scan)

    def section(title, items, render):
        print(f"{title} ({len(items)})")
        for item in items:
            render(item)
        print()

    def render_written(item):
        mp, key = item
        print(f"  {mp} {key}")
        for s in sorted(set(scan.written[mp][key]), key=lambda s: (s.path, s.line)):
            print(f"      written {s.kind}: {fmt_site(s)}")

    def render_read(item):
        mp, key = item
        sites = scan.read[mp][key]
        shown, more = fmt_sites(sites, limit=4)
        print(f"  {mp} {key}")
        for s in shown:
            print(f"      {s.kind}: {fmt_site(s)}")
        if more:
            print(f"      {more.strip()}")
        engine = [s for s in scan.written[mp].get(key, ()) if s.kind == "engine"]
        for s in engine:
            print(f"      (engine fill: {fmt_site(s)})")

    def render_near(item):
        mp, w, r, why = item
        print(f"  {mp} written \"{w}\" ~ read \"{r}\": {why}")

    def render_defaults(item):
        mp, key = item
        print(f"  {mp} {key}")
        for s, d in scan.fill_defaults[mp][key]:
            print(f"      default {d}: {fmt_site(s)}")

    section("(a) written, never read", found["a"], render_written)
    section("(c) near misses", found["c"], render_near)
    section("(b) read or declared, written by no door [info]", found["b"], render_read)
    section("(e) declared in Slots, never placed or read by engine code [info]", found["e"], render_read)
    section("(d) one key, different fill defaults [info]", found["d"], render_defaults)
    print(f"open writers: the key is the caller's argument, not counted ({len(scan.open_writers)})")
    for mp, s, expr in scan.open_writers:
        print(f"  {mp} {fmt_site(s)}: {expr}")
    print()
    print(f"open readers: a Param named by the caller, not counted ({len(scan.open_readers)})")
    for mp, s, expr in scan.open_readers:
        print(f"  {mp} {fmt_site(s)}: {expr}")
    print()
    section("unresolved writer arguments: the tool cannot name these keys", scan.unresolved,
            lambda it: print(f"  {it[0]} {fmt_site(it[1])}: {it[2]}"))
    unused = sorted(k for k in waivers if k not in used)
    section("unused waivers", unused, lambda k: print(f"  {a.waivers}:{waivers[k][1]}: {k[0]} {k[1]}"))

    totals = []
    for mp in MAPS:
        w, r = set(scan.written[mp]), set(scan.read[mp])
        totals.append(f"{mp} written {len(w)}, read {len(r)}, both {len(w & r)}")
    fail = bool(found["a"] or found["c"] or scan.unresolved)  # a writer the tool cannot name is not "clean"
    if a.strict:
        fail = fail or bool(found["b"] or found["e"] or unused)
    print(f"dead-knobs: {len(sources)} file(s); {'; '.join(totals)}; (a) {len(found['a'])}, (c) {len(found['c'])}, "
          f"(b) {len(found['b'])}, (e) {len(found['e'])}, (d) {len(found['d'])}", file=sys.stderr)
    return 1 if fail else 0


if __name__ == "__main__":
    sys.exit(main())
