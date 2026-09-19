#!/usr/bin/env python3
"""Preview the app's reading palettes in a truecolor terminal.

Reads app/src/main/java/com/mangesh/reader/ui/Palettes.kt so the preview is the
same as the app. Run:  python3 scripts/palettes.py
"""

import re
from pathlib import Path

KT = Path(__file__).resolve().parent.parent / "app/src/main/java/com/mangesh/reader/ui/Palettes.kt"
W = 46


def palettes():
    src = KT.read_text()
    for m in re.finditer(r'Palette\("([^"]+)",\s*"([^"]+)",\s*(true|false),(.*?)\)\n', src, re.S):
        p = {"key": m.group(1), "name": m.group(2), "dark": m.group(3) == "true"}
        p.update({k: int(v, 16) for k, v in re.findall(r"(\w+) = 0x([0-9A-Fa-f]{6})", m.group(4))})
        p.update({k: float(v) for k, v in re.findall(r"(\w+Alpha) = ([0-9.]+)f", m.group(4))})
        p["hl"] = blend(p["accent"], p["bg"], p["hlAlpha"])   # the accent tint, flattened: a terminal has no alpha
        yield p


def blend(top, under, alpha):
    ch = lambda v, shift: (v >> shift) & 255
    return sum(round(ch(top, s) * alpha + ch(under, s) * (1 - alpha)) << s for s in (16, 8, 0))


def rgb(v):
    return f"{(v >> 16) & 255};{(v >> 8) & 255};{v & 255}"


def fg(v):
    return f"\033[38;2;{rgb(v)}m"


def bg(v):
    return f"\033[48;2;{rgb(v)}m"


RESET = "\033[0m"
BOLD = "\033[1m"
ITALIC = "\033[3m"


def line(p, parts):
    """One row of the sample, padded to W on the palette's background."""
    text = "".join(s for _style, s in parts)
    out = bg(p["bg"]) + "  "
    for style, s in parts:
        out += style + s + RESET + bg(p["bg"])
    return out + " " * max(0, W - 2 - len(text)) + RESET


def block(p):
    base = fg(p["fg"])
    rows = [
        [(base, "The Synthesis Is Here")],
        [(fg(p["muted"]), "THEAMERICANSCHOLAR.ORG · 15 MIN · V1")],
        [(base, "")],
        [(base, "Our future depends on how success-")],
        [(base, "fully we fuse the liberal arts and ")],
        [(base, "technology. See "), (base + "\033[4m", "the essay"), (base, " and the")],
        [(base, "table in "), (bg(p["code"]) + base, " reader.css "), (base, ".")],
        [(base, "")],
        [(fg(p["accent"]), "▎"), (bg(p["hl"]) + base, " M  this cuts against the DELL thesis "), (base, "")],
        [(fg(p["rule"]), "▏"), (fg(p["fg2"]), " PRISM  only if the shortage persists "), (base, "")],
        [(base, "")],
        [(fg(p["rule"]), "─" * (W - 4))],
        [(fg(p["accent"]), "▰▰▰▰▰▰▰▰▰▰▰▰"), (fg(p["rule"]), "▱▱▱▱▱▱▱▱▱▱▱▱▱▱"), (fg(p["muted"]), "  41% · 9 min left")],
    ]
    return [line(p, r) for r in rows]


def main():
    ps = list(palettes())
    for dark in (False, True):
        group = [p for p in ps if p["dark"] == dark]
        print(f"\n{BOLD}{'Dark' if dark else 'Light'}{RESET}  (Settings → Reading → Theme, or Aa in the reader)\n")
        # two side by side
        for i in range(0, len(group), 2):
            pair = group[i:i + 2]
            heads = [f"  {p['name']:<{W - 2}}" for p in pair]
            print("   ".join(f"{BOLD}{h}{RESET}" for h in heads))
            blocks = [block(p) for p in pair]
            for rows in zip(*blocks):
                print("   ".join(rows))
            print()
    print(f"{ITALIC}On the phone it is Playfair Display over Work Sans; the terminal shows the colours, not the type.{RESET}")


if __name__ == "__main__":
    main()
