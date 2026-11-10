#!/usr/bin/env python3
"""Tests for hot_path_lint.py on the fixtures beside this file. Run: python3 -I hot_path_lint.py --self-test
(or python3 -I tests/test_hot_path_lint.py).

A fixture marks every line that must fire with `// expect: rule[, rule]`; every other line must stay quiet, so a
fixture pins both that each rule CAN fire and the patterns it must leave alone."""
import contextlib
import io
import os
import re
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
FIXTURES = os.path.join(HERE, "fixtures")
sys.path.insert(0, os.path.dirname(HERE))

import hot_path_lint as hpl  # noqa: E402

EXPECT = re.compile(r"//\s*expect:\s*([\w\-, ]+)")


def lint_fixture(name, guarded=False):
    path = os.path.join(FIXTURES, name)
    findings, waived, hot, _ = hpl.lint([path], FIXTURES, inline_sources=[], guarded=guarded)
    return findings, waived, hot


def expected(name):
    result = set()
    with open(os.path.join(FIXTURES, name), encoding="utf-8") as f:
        for n, line in enumerate(f, 1):
            m = EXPECT.search(line)
            if m:
                result |= {(n, r.strip()) for r in m.group(1).split(",") if r.strip()}
    return result


class ExpectMarkers(unittest.TestCase):
    def check(self, name):
        findings, _, _ = lint_fixture(name)
        got = {(fd.line, fd.rule) for fd in findings}
        want = expected(name)
        self.assertEqual(sorted(want - got), [], f"{name}: expected, not reported")
        self.assertEqual(sorted(got - want), [], f"{name}: reported, not expected:\n" +
                         "\n".join(str(fd) for fd in findings if (fd.line, fd.rule) in got - want))

    def test_boxed_types(self):
        self.check("boxed_types.kt")

    def test_closures(self):
        self.check("closures.kt")

    def test_render_code(self):
        self.check("render_code.kt")


class RenderCode(unittest.TestCase):
    def test_every_render_rule_has_a_fixture_line(self):
        rules = {rule for name in os.listdir(FIXTURES) if name.endswith(".kt") for _, rule in expected(name)}
        rules |= {"bad-waiver", "unused-waiver"}  # pinned by the waiver test
        rules |= {"alloc-guarded"}  # pinned by test_guarded
        self.assertEqual(sorted(set(hpl.RULES) - rules), [])

    def test_hot_set(self):
        _, _, hot = lint_fixture("render_code.kt")
        names = {f.name for f in hot}
        self.assertIn("generate", names)
        self.assertIn("helper", names)  # called from generate
        self.assertIn("consume", names)
        self.assertNotIn("reset", names)  # cold by name
        self.assertNotIn("describe", names)  # never called from render code

    def test_guarded(self):
        findings, _, _ = lint_fixture("render_code.kt", guarded=True)
        guarded = sorted(fd.line for fd in findings if fd.rule == "alloc-guarded")
        lines = open(os.path.join(FIXTURES, "render_code.kt"), encoding="utf-8").read().split("\n")
        want = [n for n, line in enumerate(lines, 1) if "scratch = DoubleArray(length)" in line or "Holder(1.0)" in line]
        self.assertEqual(guarded, want)  # the resize and the cache on a miss

    def test_cold_marker(self):
        src = os.path.join(FIXTURES, "render_code.kt")
        text = open(src, encoding="utf-8").read()
        marked = text.replace("    private fun helper(", "    // hot-path-lint: cold a test of the marker\n    private fun helper(")
        tmp = os.path.join(HERE, "_marked_tmp.kt")
        try:
            with open(tmp, "w", encoding="utf-8") as f:
                f.write(marked)
            findings, _, hot, _ = hpl.lint([tmp], HERE, inline_sources=[])
        finally:
            os.remove(tmp)
        self.assertNotIn("helper", {f.name for f in hot})
        self.assertFalse([fd for fd in findings if "in Voice.helper" in fd.msg])


class Waivers(unittest.TestCase):
    def test_waivers(self):
        findings, waived, _ = lint_fixture("waivers.kt")
        got = sorted((fd.line, fd.rule) for fd in findings)
        self.assertEqual(got, [
            (16, "bad-waiver"),      # `allow alloc` without a reason
            (17, "alloc-array"),     # ... so its line is not waived
            (19, "unused-waiver"),   # nothing on its line throws
            (22, "bad-waiver"),      # an unknown rule
            (25, "unused-waiver"),   # the wrong rule ...
            (26, "alloc-array"),     # ... leaves the finding standing
        ])
        self.assertEqual(sorted(fd.line for fd in waived), [10, 14])

    def test_exit_code(self):
        path = os.path.join(FIXTURES, "waivers.kt")
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(hpl.main([path, "--root", FIXTURES, "--rules", "map-op"]), 0)
            self.assertEqual(hpl.main([path, "--root", FIXTURES]), 1)


class Masking(unittest.TestCase):
    def test_comments_strings_and_templates(self):
        code, strings, chars = hpl.mask('val a = "x $b ${c("}")}" // Long\n/* Char /* nested */ Long */ val d = \'e\'')
        self.assertNotIn("Long", code)
        self.assertNotIn("Char", code)
        self.assertEqual([s[2] for s in strings], [True])
        self.assertEqual(len(chars), 1)
        self.assertEqual(code.count("\n"), 1)


if __name__ == "__main__":
    unittest.main()
