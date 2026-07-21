# -*- coding: utf-8 -*-
"""
Ensure correct Compose Modifier usage:
  import: import androidx.compose.ui.Modifier   (class capital M)
  param:  modifier = Modifier.xxx()          (param lower-case m)
"""
from pathlib import Path
import re

MOD_CLASS = chr(77) + "odifier"  # "Modifier"
IMPORT = "import androidx.compose.ui." + MOD_CLASS
assert IMPORT == "import androidx.compose.ui.modifier"

root = Path("app/src/main/java")

for path in root.rglob("*.kt"):
    text = path.read_text(encoding="utf-8")
    orig = text

    # 1) Fix import to exactly 3 dots + capital M class
    lines = []
    for line in text.splitlines():
        s = line.strip()
        if s.startswith("import androidx.compose.ui.") and "modif" in s.lower():
            lines.append(IMPORT)
        else:
            lines.append(line)
    text = "\n".join(lines) + "\n"

    # 2) Fix named parameter "Modifier =" when wrongly written as "modifier ="
    # Pattern: start of arg list / after whitespace, capital Modifier as param name
    # e.g. "Modifier = Modifier.fill" -> "modifier = Modifier.fill"
    text = re.sub(
        r"(?m)(?P<indent>^\s*)" + MOD_CLASS + r"\s*=\s*" + MOD_CLASS,
        lambda m: m.group("indent") + "modifier = " + MOD_CLASS,
        text,
    )
    # also mid-line: ", Modifier = Modifier" or "(Modifier = Modifier"
    text = re.sub(
        r"(?P<pre>[\(,]\s*)" + MOD_CLASS + r"\s*=\s*" + MOD_CLASS,
        lambda m: m.group("pre") + "modifier = " + MOD_CLASS,
        text,
    )

    if text != orig:
        path.write_text(text, encoding="utf-8")
        print("UPDATED", path)
    else:
        print("ok    ", path.name)

print("\n--- imports ---")
for path in root.rglob("*.kt"):
    for line in path.read_text(encoding="utf-8").splitlines():
        if "import" in line and "modif" in line.lower():
            print(path.name, line.strip(), "dots", line.count("."))

print("\n--- suspicious 'Modifier =' named params (capital) ---")
cap_param = re.compile(r"\b" + MOD_CLASS + r"\s*=")
for path in root.rglob("*.kt"):
    for i, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        # skip import and type uses like ": Modifier" and "Modifier: Modifier"
        if "import" in line:
            continue
        if cap_param.search(line) and "modifier =" not in line:
            # capital Modifier as assignment target / named param
            if re.search(r"(^|[\(,])\s*" + MOD_CLASS + r"\s*=", line):
                print(f"{path.name}:{i}: {line.strip()}")

print("\n--- MainActivity Surface block ---")
main = (root / "com/yunplayer/app/MainActivity.kt").read_text(encoding="utf-8")
for i, line in enumerate(main.splitlines(), 1):
    if 48 <= i <= 55:
        # show whether left side starts with lower or upper m
        s = line.strip()
        if "modif" in s.lower() or "Surface" in s or "fillMax" in s:
            print(i, repr(line))
