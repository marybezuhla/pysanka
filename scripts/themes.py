#!/usr/bin/env python3
"""Pysanka's keyboard themes: the colours of each, put into one shared layout (themes/template.json).

Colours of Ukrainian pysanky, arranged as reading apps arrange theirs for long reading: a calm
background, letters with strong but not glaring contrast (warm cream rather than pure white at
night), the accent used sparingly (Enter, Caps Lock, the swipe trail, chosen tabs).

Run from the repository root: python3 scripts/themes.py
It writes app/src/main/assets/ime/theme/org.florisboard.themes/ (extension.json and stylesheets)
and checks the contrast (letters at least 7:1, other text 4.5:1, icons 3:1); ThemeContrastTest
checks the files again in CI.
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app/src/main/assets/ime/theme/org.florisboard.themes")

# id, label (shown when no translation is known), night?, colours
THEMES = [
    ("kreyda", "Kreyda", False, dict(
        background="#ece6de", background_variant="#e0d8cd", surface="#fffdf9", surface_variant="#efe8df",
        popup="#f6f1ea", popup_focused="#dccfbf", text="#2b2420", text_variant="#5a5048", text_background="#2b2420",
        primary="#b3261e", primary_variant="#8c1d18", on_primary="#ffffff", secondary="#8a5d00",
        one_hand="#f3e4e1")),
    ("visk", "Visk", False, dict(
        background="#e8dbc1", background_variant="#ddcdaf", surface="#f8efdd", surface_variant="#ebdfc7",
        popup="#f2e7d2", popup_focused="#d8c4a0", text="#2e2116", text_variant="#64503c", text_background="#2e2116",
        primary="#9c3d12", primary_variant="#7a2e0c", on_primary="#fff8ec", secondary="#7f5200",
        one_hand="#efdcc0")),
    ("nebo", "Nebo", False, dict(
        background="#dfe5ec", background_variant="#d2dae3", surface="#f9fbfd", surface_variant="#e8eef4",
        popup="#eef2f6", popup_focused="#c6d3e0", text="#1e2a3c", text_variant="#4a596d", text_background="#1e2a3c",
        primary="#2f5da8", primary_variant="#244a87", on_primary="#ffffff", secondary="#b3261e",
        one_hand="#e1e9f4")),
    ("podillia", "Podillia", True, dict(
        background="#15110f", background_variant="#1e1916", surface="#2a2320", surface_variant="#3a312c",
        popup="#3a312c", popup_focused="#8c1d18", text="#f1e6d2", text_variant="#c2b5a3", text_background="#ede3d3",
        primary="#c62f25", primary_variant="#a3241c", on_primary="#ffffff", secondary="#e0a526",
        one_hand="#2a1a17")),
    ("hutsulshchyna", "Hutsulshchyna", True, dict(
        background="#101a15", background_variant="#17241d", surface="#1e2e25", surface_variant="#2a3d32",
        popup="#2a3d32", popup_focused="#7a5410", text="#ece6d6", text_variant="#b9bba8", text_background="#e6e0d0",
        primary="#e39b2d", primary_variant="#c47f18", on_primary="#1a1208", secondary="#f0b84a",
        one_hand="#1f2a1c")),
    ("chornozem", "Chornozem", True, dict(
        background="#000000", background_variant="#0a0908", surface="#151210", surface_variant="#241f1b",
        popup="#241f1b", popup_focused="#6b4e12", text="#ded3c2", text_variant="#a89c8b", text_background="#d8cdbc",
        primary="#d9a33c", primary_variant="#b8862a", on_primary="#1a1206", secondary="#d9a33c",
        one_hand="#14100c")),
]


def defines(c, night):
    return {
        "--primary": c["primary"],
        "--primary-variant": c["primary_variant"],
        "--secondary": c["secondary"],
        "--secondary-variant": c["secondary"],
        "--background": c["background"],
        "--background-variant": c["background_variant"],
        "--surface": c["surface"],
        "--surface-variant": c["surface_variant"],
        "--popup-surface": c["popup"],
        "--focused-popup-surface": c["popup_focused"],
        "--drag-marker": c["primary"],
        "--spacer-color": "rgba(255, 255, 255, 0.2)" if night else "rgba(0, 0, 0, 0.2)",
        "--one-hand-background": c["one_hand"],
        "--one-hand-foreground": c["text"],
        "--incognito-icon-color": "#ffffff11" if night else "#00000011",
        "--on-primary": c["on_primary"],
        "--on-background": c["text_background"],
        "--on-background-disabled": c["text_variant"] + "80",
        "--on-surface": c["text"],
        "--on-surface-variant": c["text_variant"],
        "--shape": "rounded-corner(8dp, 8dp, 8dp, 8dp)",
        "--shape-variant": "rounded-corner(12dp, 12dp, 12dp, 12dp)",
        "--shape-chip": "rounded-corner(50%, 50%, 50%, 50%)",
    }


def lum(h):
    h = h.lstrip("#")[:6]
    def f(v):
        v = int(v, 16) / 255
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = f(h[0:2]), f(h[2:4]), f(h[4:6])
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def ratio(a, b):
    hi, lo = sorted([lum(a), lum(b)], reverse=True)
    return (hi + 0.05) / (lo + 0.05)


def check(name, c):
    rules = [
        ("letters on keys", c["text"], c["surface"], 7.0),
        ("letters on pressed keys", c["text"], c["surface_variant"], 4.5),
        ("hints and the space label", c["text_variant"], c["surface"], 4.5),
        ("Enter", c["on_primary"], c["primary"], 4.5),
        ("Enter pressed", c["on_primary"], c["primary_variant"], 4.5),
        ("popup letters", c["text"], c["popup"], 4.5),
        ("chosen popup letter", c["text"], c["popup_focused"], 3.0),
        ("caps lock", c["secondary"], c["surface"], 3.0),
        ("accent icons on the keyboard", c["primary"], c["background"], 3.0),
        ("text on the keyboard", c["text_background"], c["background"], 7.0),
        ("keys stand out", c["surface"], c["background"], 1.05),
    ]
    bad = [f"{n}: {ratio(a, b):.2f} < {m}" for n, a, b, m in rules if ratio(a, b) < m]
    for b in bad:
        print(f"{name}: {b}", file=sys.stderr)
    return not bad


def main():
    template = json.load(open(os.path.join(ROOT, "scripts/themes/template.json")))
    ok = all(check(i, c) for i, _, _, c in THEMES)
    if not ok:
        sys.exit("contrast too low")
    sheets = os.path.join(OUT, "stylesheets")
    os.makedirs(sheets, exist_ok=True)
    for f in os.listdir(sheets):
        os.remove(os.path.join(sheets, f))
    for theme_id, _, night, c in THEMES:
        sheet = dict(template)
        sheet["@defines"] = defines(c, night)
        with open(os.path.join(sheets, theme_id + ".json"), "w") as f:
            json.dump(sheet, f, ensure_ascii=False, indent=2)
            f.write("\n")
    extension = {
        "$": "ime.extension.theme",
        "meta": {
            "id": "org.florisboard.themes",
            "version": "1.0.0",
            "title": "Pysanka themes",
            "description": "Pysanka's keyboard themes in the colours of Ukrainian pysanky (generated by scripts/themes.py)",
            "maintainers": ["Pysanka"],
            "license": "gpl-3.0",
        },
        "themes": [
            {"id": i, "label": label, "authors": ["Pysanka"], "isNight": night}
            for i, label, night, _ in THEMES
        ],
    }
    with open(os.path.join(OUT, "extension.json"), "w") as f:
        json.dump(extension, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    main()
