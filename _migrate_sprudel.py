#!/usr/bin/env python3
"""Generic mechanical migration of sprudel DSL delegates to @KlangScript.Function annotations.

Usage: python3 _migrate_sprudel.py <file.kt>
"""
import re
import sys

if len(sys.argv) < 2:
    print("Usage: python3 _migrate_sprudel.py <file.kt>")
    sys.exit(1)

filepath = sys.argv[1]

with open(filepath, 'r') as f:
    content = f.read()

# ================================================================
# STEP 0: Build delegate_name → applyFn mapping from SprudelPattern delegates
# ================================================================
# Parse: internal val SprudelPattern._xxx by dslPatternExtension { ... applyYyy(p, args) }
# Both single-line and multi-line delegates

delegate_to_apply = {}  # e.g., "distort" -> "applyDistort", "dist" -> "applyDistort"

for m in re.finditer(r'internal val SprudelPattern\._(\w+) by dslPatternExtension', content):
    delegate_name = m.group(1)
    # Find the applyXxx call in the delegate body
    start = m.end()
    # Search for applyXxx( in the next ~200 chars
    snippet = content[start:start + 300]
    apply_m = re.search(r'(apply[A-Z]\w*)\(', snippet)
    if apply_m:
        delegate_to_apply[delegate_name] = apply_m.group(1)

# Build: applyFn → primary function name (the delegate whose name matches)
apply_to_primary = {}
for delegate_name, apply_fn in delegate_to_apply.items():
    # Primary = the delegate whose name matches the apply function (case-insensitive)
    # e.g., "roomsize" matches "applyRoomSize", "distort" matches "applyDistort"
    expected_name = apply_fn[5:]  # "applyRoomSize" -> "RoomSize"
    if delegate_name.lower() == expected_name.lower():
        apply_to_primary[apply_fn] = delegate_name

# Build alias → primary mapping
alias_to_primary = {}
for delegate_name, apply_fn in delegate_to_apply.items():
    primary = apply_to_primary.get(apply_fn)
    if primary and delegate_name != primary:
        alias_to_primary[delegate_name] = primary

# Collect all applyXxx function names
apply_fns = set(re.findall(r'fun (apply[A-Z]\w*)\(', content))

# ================================================================
# STEP 1: Add file-level annotation and imports
# ================================================================
if '@file:KlangScript.Library' not in content:
    content = re.sub(
        r'(@file:Suppress\([^)]+\))\n',
        r'\1\n@file:KlangScript.Library("sprudel")\n',
        content,
        count=1,
    )

if 'import io.peekandpoke.klang.script.annotations.KlangScript' not in content:
    content = content.replace(
        'import io.peekandpoke.klang.sprudel.',
        'import io.peekandpoke.klang.script.annotations.KlangScript\nimport io.peekandpoke.klang.script.ast.CallInfo\nimport io.peekandpoke.klang.sprudel.',
        1,
    )

# ================================================================
# STEP 2: Delete ALL delegate blocks
# ================================================================
lines = content.split('\n')
new_lines = []
i = 0
while i < len(lines):
    line = lines[i]
    stripped = line.strip()

    if stripped.startswith('internal val') and 'by dsl' in stripped:
        open_braces = stripped.count('{')
        close_braces = stripped.count('}')
        if open_braces > close_braces:
            depth = open_braces - close_braces
            i += 1
            while i < len(lines) and depth > 0:
                depth += lines[i].count('{') - lines[i].count('}')
                i += 1
            continue
        else:
            i += 1
            continue

    if stripped == '// ===== USER-FACING OVERLOADS =====':
        i += 1
        continue

    new_lines.append(line)
    i += 1

content = '\n'.join(new_lines)

# ================================================================
# STEP 3: Make applyXxx functions private
# ================================================================
content = re.sub(r'\nfun (apply[A-Z]\w*)\(', r'\nprivate fun \1(', content)
content = content.replace('private private fun', 'private fun')

# ================================================================
# STEP 4: Add @KlangScript.Function after @SprudelDsl
# ================================================================
content = re.sub(
    r'@SprudelDsl\n(?!@KlangScript\.Function)fun ',
    '@SprudelDsl\n@KlangScript.Function\nfun ',
    content,
)

# ================================================================
# STEP 5: Process all functions - add callInfo param + rewrite bodies
# ================================================================
lines = content.split('\n')
result = []


def find_function_type(result_lines, current_line):
    """Determine function type from declaration. Checks current line first, then looks back."""
    # Check the current line itself (single-line functions)
    for check_line in [current_line] + [result_lines[j] for j in
                                        range(len(result_lines) - 1, max(len(result_lines) - 10, -1), -1)]:
        s = check_line.strip()
        if s.startswith('fun SprudelPattern.'):
            return 'SprudelPattern'
        elif s.startswith('fun String.'):
            return 'String'
        elif s.startswith('fun PatternMapperFn.'):
            return 'PatternMapperFn'
        elif re.match(r'fun \w+\(', s):
            return 'TopLevel'
        elif s and not s.startswith('*') and not s.startswith('/') and not s.startswith('@') and not s.startswith(
                '/**') and check_line != current_line:
            break
    return None


def rewrite_delegate_ref(line, fn_type):
    """Rewrite a delegate reference in a function body."""
    # Extract the delegate reference
    m = re.search(r'(?:this\.)?_(\w+)\(((?:listOfNotNull|listOf)\([^)]*\)\.asSprudelDslArgs\(\)|emptyList\(\))\)', line)
    if not m:
        return line

    delegate_name = m.group(1)
    args = m.group(2)

    # Extract param name
    param_match = re.search(r'(?:listOfNotNull|listOf)\((\w+)\)', args)
    param_name = param_match.group(1) if param_match else None

    # Determine primary name
    primary = alias_to_primary.get(delegate_name, delegate_name)

    # Determine apply function
    apply_fn = delegate_to_apply.get(delegate_name)
    is_primary = (delegate_name not in alias_to_primary)

    indent = re.match(r'(\s*)', line).group(1)
    # Also handle inline bodies (signature + body on same line)
    before_body = line[:m.start()]

    if fn_type == 'SprudelPattern':
        if is_primary and apply_fn:
            new_args = args.replace('.asSprudelDslArgs()', '.asSprudelDslArgs(callInfo)')
            new_body = f'{apply_fn}(this, {new_args})'
        else:
            new_body = f'this.{primary}({param_name}, callInfo)' if param_name else f'this.{primary}(callInfo = callInfo)'
    elif fn_type == 'String':
        new_body = f'this.toVoiceValuePattern().{primary}({param_name}, callInfo)' if param_name else f'this.toVoiceValuePattern().{primary}(callInfo = callInfo)'
    elif fn_type == 'PatternMapperFn':
        new_body = f'this.chain {{ p -> p.{primary}({param_name}, callInfo) }}' if param_name else f'this.chain {{ p -> p.{primary}(callInfo = callInfo) }}'
    elif fn_type == 'TopLevel':
        new_body = f'{{ p -> p.{primary}({param_name}, callInfo) }}' if param_name else f'{{ p -> p.{primary}(callInfo = callInfo) }}'
    else:
        return line

    # Replace in line - handle both inline and separate-line bodies
    return before_body + new_body


for i, line in enumerate(lines):
    # Add callInfo to function signatures
    if 'PatternLike' in line and ('): SprudelPattern' in line or '): PatternMapperFn' in line):
        if 'callInfo' not in line:
            line = re.sub(
                r'(\w+: PatternLike(?:\? = null)?)\): (SprudelPattern|PatternMapperFn)',
                r'\1, callInfo: CallInfo? = null): \2',
                line,
            )

    # Check for delegate references in the line
    if re.search(r'(?:this\.)?_\w+\((?:listOfNotNull|listOf|emptyList)', line):
        # Determine function type
        fn_type = find_function_type(result, line)
        if fn_type:
            line = rewrite_delegate_ref(line, fn_type)

    result.append(line)

content = '\n'.join(result)

# ================================================================
# STEP 6: Clean up
# ================================================================
while '\n\n\n' in content:
    content = content.replace('\n\n\n', '\n\n')

# ================================================================
# Write output
# ================================================================
with open(filepath, 'w') as f:
    f.write(content)

# ================================================================
# Verify
# ================================================================
remaining_delegates = len(re.findall(r'internal val.*by dsl', content))
# Check for delegate body refs (excluding legitimate _applyControl/_liftOr refs)
remaining_refs = [m for m in re.findall(r'\._([a-z]\w+)\(', content) if
                  not m.startswith('applyControl') and not m.startswith('liftOr') and not m.startswith(
                      'liftNumeric') and not m.startswith('fastGap')]
# Simpler check
old_delegate_bodies = len(re.findall(r'(?:this\.)?_[a-z]\w+\((?:listOfNotNull|listOf|emptyList)\(', content))
annotations = content.count('@KlangScript.Function')
callinfos = content.count('callInfo: CallInfo? = null')

print(f"File: {filepath}")
print(f"Remaining delegate registrations: {remaining_delegates}")
print(f"Remaining old-style delegate call bodies: {old_delegate_bodies}")
print(f"@KlangScript.Function count: {annotations}")
print(f"callInfo param count: {callinfos}")
if alias_to_primary:
    print(f"Alias mappings found: {len(alias_to_primary)}")
    for alias, primary in sorted(alias_to_primary.items()):
        print(f"  {alias} -> {primary}")
