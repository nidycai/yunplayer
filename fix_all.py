# -*- coding: utf-8 -*-
"""Rewrite broken Compose Modifier imports to: import androidx.compose.ui.Modifier"""
from pathlib import Path

# Correct import (package androidx.compose.ui + class Modifier)
# Must be EXACTLY 3 dots after "import "
correct = "import androidx.compose.ui." + chr(77) + "odifier"
assert correct == "import androidx.compose.ui.modifier"
assert correct.count(".") == 3
assert correct.endswith("Modifier")
assert not correct.endswith("modifier.modifier")
print("correct import:", correct)

root = Path(__file__).resolve().parent / "app" / "src" / "main" / "java"
for path in sorted(root.rglob("*.kt")):
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()
    out = []
    changed = False
    for line in lines:
        s = line.strip()
        if s.startswith("import androidx.compose.ui.") and "modif" in s.lower():
            if s != correct:
                print(f"FIX {path.relative_to(root.parent.parent.parent)}")
                print(f"  was: {s} (dots={s.count('.')})")
                print(f"  now: {correct} (dots={correct.count('.')})")
                out.append(correct)
                changed = True
            else:
                out.append(line)
        else:
            out.append(line)
    if changed:
        path.write_text("\n".join(out) + "\n", encoding="utf-8")

print("\nVERIFY on disk:")
for path in sorted(root.rglob("*.kt")):
    for line in path.read_text(encoding="utf-8").splitlines():
        if "import" in line and "modif" in line.lower():
            s = line.strip()
            print(path.name, s, "OK" if s == correct else "BAD", "dots", s.count("."))
