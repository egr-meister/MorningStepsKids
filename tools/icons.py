#!/usr/bin/env python3
"""Generates the bundled step / theme icons as Android VectorDrawables (+ SVG previews).

All icons use a 48x48 viewport, a dark-slate outline and soft fills.
"""
import os, sys

OUT_RES = sys.argv[1] if len(sys.argv) > 1 else "out/drawable"
OUT_SVG = sys.argv[2] if len(sys.argv) > 2 else "out/svg"

INK = "#2F3B48"
WHITE = "#FFFFFF"
SKIN = "#F6D3B9"
WATER = "#8CC8EC"
SKY = "#A9D3F2"
MINT = "#A8DCC6"
PEACH = "#F7C6A3"
LAV = "#C9B9E6"
SUN = "#F8D57E"
RED = "#F2A69A"
WOOD = "#D8B48A"
GREY = "#DCE3EA"
SW = 2.2


def circle(cx, cy, r):
    return f"M{cx - r},{cy} a{r},{r} 0 1,0 {2 * r},0 a{r},{r} 0 1,0 {-2 * r},0 Z"


def rrect(x, y, w, h, r):
    return (f"M{x + r},{y} H{x + w - r} A{r},{r} 0 0 1 {x + w},{y + r} V{y + h - r} "
            f"A{r},{r} 0 0 1 {x + w - r},{y + h} H{x + r} A{r},{r} 0 0 1 {x},{y + h - r} "
            f"V{y + r} A{r},{r} 0 0 1 {x + r},{y} Z")


def P(d, fill=None, stroke=INK, sw=SW):
    return dict(d=d, fill=fill, stroke=stroke, sw=sw)


def F(d, fill):  # fill only
    return dict(d=d, fill=fill, stroke=None, sw=0)


def S(d, sw=SW, stroke=INK):  # stroke only
    return dict(d=d, fill=None, stroke=stroke, sw=sw)


ICONS = {}

# --- wash face: face + droplets
ICONS["wash_face"] = [
    P(circle(22, 26, 13), SKIN),
    F(circle(17, 25, 1.6), INK), F(circle(27, 25, 1.6), INK),
    S("M17.5,31 Q22,34.5 26.5,31"),
    P("M38,6 C40.5,10 42,12 42,14 A4,4 0 0 1 34,14 C34,12 35.5,10 38,6 Z", WATER),
    P("M41,21 C42.5,23.5 43.5,25 43.5,26.2 A2.5,2.5 0 0 1 38.5,26.2 C38.5,25 39.5,23.5 41,21 Z", WATER, sw=1.6),
]

# --- get dressed: t-shirt
SHIRT = "M17,8 L8,13 L11,21 L15,19.5 V40 H33 V19.5 L37,21 L40,13 L31,8 Q24,13 17,8 Z"
ICONS["get_dressed"] = [P(SHIRT, SKY), S("M19,27 H29", 1.8)]

# --- breakfast: bowl + spoon + steam
ICONS["breakfast"] = [
    S("M18,8 Q15,11 18,14 Q21,17 18,20", 1.8), S("M26,8 Q23,11 26,14 Q29,17 26,20", 1.8),
    P("M6,24 H42 Q41,38 30,40 H18 Q7,38 6,24 Z", PEACH),
    S("M15,43 H33"),
    F("M10,24 H38 L37.5,26 H10.5 Z", "#FFF4E2"),
]

# --- brush teeth: toothbrush + tooth
ICONS["brush_teeth"] = [
    P("M24,13 C20,9 12,10 11,16 C10,22 12,24 13,30 C14,36 15,40 17,40 C19.5,40 19,32 22,32 "
      "C25,32 24.5,40 27,40 C29,40 30,36 31,30 C31.5,27 32,24 32.5,22", WHITE),
    P(rrect(30, 6, 14, 4.5, 2.2), MINT, sw=1.8),
    P("M30,6 V3 M33,6 V3 M36,6 V3 M39,6 V3", None, sw=1.6),
    P(rrect(35, 10.5, 4, 24, 2), MINT, sw=1.8),
]

# --- pack bag: backpack
BAG = rrect(11, 12, 26, 30, 7)
ICONS["pack_bag"] = [
    S("M18,12 V9 A6,6 0 0 1 30,9 V12"),
    P(BAG, MINT),
    P(rrect(16, 27, 16, 10, 3), WHITE, sw=1.8),
    S("M21,31 H27", 1.8),
]

# --- bag away: backpack on a hook
ICONS["bag_away"] = [
    S("M6,8 H42"), S("M24,8 V13 A3,3 0 1 1 21,16", 2.0),
    P(rrect(13, 18, 22, 25, 6), PEACH),
    P(rrect(17, 30, 14, 9, 3), WHITE, sw=1.8),
]

# --- shoes: sneaker
ICONS["shoes"] = [
    P("M5,34 V22 Q5,18 9,18 H15 Q18,25 25,25 L36,28 Q43,30 43,34 V37 H5 Z", PEACH),
    P("M5,37 H43 V40 Q43,42 41,42 H7 Q5,42 5,40 Z", WHITE, sw=2.0),
    S("M18,24 L21,21 M22,26 L25,23", 1.6),
]

# --- tidy: toy box with items
ICONS["tidy"] = [
    P(circle(17, 17, 6), RED), P("M27,8 L35,8 L37,21 L25,21 Z", SUN, sw=2.0),
    P(rrect(7, 21, 34, 20, 3), WOOD),
    S("M7,28 H41", 1.8),
    P(rrect(20, 31, 8, 4, 2), WHITE, sw=1.6),
]

# --- light off: light bulb
ICONS["light_off"] = [
    P("M24,6 A12,12 0 0 1 31,28 Q30,29.5 30,32 H18 Q18,29.5 17,28 A12,12 0 0 1 24,6 Z", SUN),
    P(rrect(18, 32, 12, 7, 2), GREY, sw=2.0),
    S("M21,43 H27"),
    S("M21,24 L24,19 L27,24", 1.6),
]

# --- pajamas: moon + stars
ICONS["pajamas"] = [
    P("M30,7 A16,16 0 1 0 41,30 A13,13 0 1 1 30,7 Z", LAV),
    P("M36,6 L37.3,9.7 L41,11 L37.3,12.3 L36,16 L34.7,12.3 L31,11 L34.7,9.7 Z", SUN, sw=1.4),
    P("M41,18 L41.8,20.2 L44,21 L41.8,21.8 L41,24 L40.2,21.8 L38,21 L40.2,20.2 Z", SUN, sw=1.2),
]

# --- prepare tomorrow's clothes: hanger with shirt
ICONS["hanger"] = [
    S("M24,14 V12 A3.5,3.5 0 1 1 27.5,8.5", 2.0),
    P("M24,14 L6,26 H42 Z", None),
    P("M12,26 H36 V42 H12 Z", LAV, sw=2.0),
    S("M24,26 V42", 1.6),
]

# --- check clothes: mirror on stand
ICONS["check_clothes"] = [
    P("M24,4 A11,15 0 0 1 24,34 A11,15 0 0 1 24,4 Z", WOOD),
    P("M24,8 A7.5,11 0 0 1 24,30 A7.5,11 0 0 1 24,8 Z", SKY, sw=1.6),
    S("M20,14 Q19,18 20,22", 1.6, WHITE),
    S("M24,34 V41 M15,43 H33"),
]

# --- water bottle
ICONS["water_bottle"] = [
    P(rrect(19, 4, 10, 6, 2), MINT, sw=2.0),
    P("M18,10 H30 Q34,14 34,19 V40 Q34,44 30,44 H18 Q14,44 14,40 V19 Q14,14 18,10 Z", WHITE),
    F("M15.2,26 H32.8 V40 Q32.8,42.8 30,42.8 H18 Q15.2,42.8 15.2,40 Z", WATER),
    S("M14,26 H34", 1.8),
]

# --- wash hands: hand + droplets
ICONS["wash_hands"] = [
    P("M14,44 V24 A2.5,2.5 0 0 1 19,24 V14 A2.5,2.5 0 0 1 24,14 V12 A2.5,2.5 0 0 1 29,12 V15 "
      "A2.5,2.5 0 0 1 34,15 V30 Q34,38 30,44 Z", SKIN, sw=2.0),
    S("M24,14 V26 M29,15 V26", 1.6),
    P("M9,8 C10.6,10.6 11.6,12 11.6,13.2 A2.6,2.6 0 0 1 6.4,13.2 C6.4,12 7.4,10.6 9,8 Z", WATER, sw=1.5),
    P("M40,5 C41.6,7.6 42.6,9 42.6,10.2 A2.6,2.6 0 0 1 37.4,10.2 C37.4,9 38.4,7.6 40,5 Z", WATER, sw=1.5),
]

# --- snack: apple
ICONS["snack"] = [
    P("M24,15 C17,10 7,13 8,25 C9,35 15,43 20,43 C22,43 23,42 24,42 C25,42 26,43 28,43 "
      "C33,43 39,35 40,25 C41,13 31,10 24,15 Z", RED),
    S("M24,15 Q24,10 27,6"),
    P("M27,10 Q32,5 37,8 Q32,13 27,10 Z", MINT, sw=1.6),
]

# --- school tasks: notebook + pencil
ICONS["school_tasks"] = [
    P(rrect(8, 6, 26, 36, 3), WHITE),
    S("M14,6 V42", 1.8),
    S("M19,15 H29 M19,21 H29 M19,27 H25", 1.6),
    P("M37,16 L42,21 L29,34 L23,36 L25,30 Z", SUN, sw=1.8),
]

# --- quiet activity: open book
ICONS["quiet_activity"] = [
    P("M24,13 Q15,8 5,11 V38 Q15,35 24,40 Z", SKY, sw=2.0),
    P("M24,13 Q33,8 43,11 V38 Q33,35 24,40 Z", WHITE, sw=2.0),
    S("M29,18 Q34,16 38,17 M29,24 Q34,22 38,23", 1.5),
]

# --- generic star
ICONS["star"] = [
    P("M24,5 L29.4,17 L42,18.2 L32.4,26.6 L35.4,39.5 L24,32.8 L12.6,39.5 L15.6,26.6 L6,18.2 L18.6,17 Z", SUN),
]

# --- theme icons
ICONS["theme_morning"] = [
    P(circle(24, 26, 9), SUN),
    S("M24,8 V12 M24,40 V44 M6,26 H10 M38,26 H42 M11.3,13.3 L14.1,16.1 M33.9,35.9 L36.7,38.7 "
      "M36.7,13.3 L33.9,16.1 M14.1,35.9 L11.3,38.7"),
]
ICONS["theme_evening"] = [
    P("M27,6 A18,18 0 1 0 42,32 A14,14 0 1 1 27,6 Z", LAV),
    F(circle(38, 12, 1.6), INK), F(circle(43, 20, 1.2), INK),
]
ICONS["theme_before_school"] = [
    P("M6,20 L24,8 L42,20 Z", PEACH, sw=2.0),
    P("M9,20 H39 V42 H9 Z", MINT, sw=2.0),
    P(rrect(20, 30, 8, 12, 1.5), WHITE, sw=1.8),
    P(circle(24, 15, 2.2), WHITE, sw=1.4),
    S("M13,26 H17 M31,26 H35", 1.8),
]
ICONS["theme_after_school"] = [
    P("M5,23 L24,8 L43,23", None),
    P("M10,20 V42 H38 V20", PEACH, sw=2.0),
    P(rrect(20, 29, 8, 13, 1.5), WHITE, sw=1.8),
    P(rrect(29, 25, 6, 6, 1), SKY, sw=1.6),
]


def to_vector(name, prims):
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- Generated by tools/icons.py -->',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="48dp"', '    android:height="48dp"',
           '    android:viewportWidth="48"', '    android:viewportHeight="48">']
    for p in prims:
        attrs = [f'android:pathData="{p["d"]}"']
        if p["fill"]:
            attrs.append(f'android:fillColor="{p["fill"]}"')
        if p["stroke"]:
            attrs += [f'android:strokeColor="{p["stroke"]}"', f'android:strokeWidth="{p["sw"]}"',
                      'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
        out.append("    <path\n        " + "\n        ".join(attrs) + " />")
    out.append("</vector>\n")
    return "\n".join(out)


def to_svg(prims):
    s = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48" width="96" height="96">']
    for p in prims:
        s.append(f'<path d="{p["d"]}" fill="{p["fill"] or "none"}" stroke="{p["stroke"] or "none"}" '
                 f'stroke-width="{p["sw"]}" stroke-linecap="round" stroke-linejoin="round"/>')
    s.append("</svg>")
    return "".join(s)


if __name__ == "__main__":
    os.makedirs(OUT_RES, exist_ok=True)
    os.makedirs(OUT_SVG, exist_ok=True)
    html = ['<html><body style="background:#FBF7EF;font-family:sans-serif;display:flex;flex-wrap:wrap;gap:12px">']
    for name, prims in ICONS.items():
        fname = ("ic_" if name.startswith("theme_") else "ic_step_") + name
        with open(os.path.join(OUT_RES, fname + ".xml"), "w") as f:
            f.write(to_vector(name, prims))
        svg = to_svg(prims)
        with open(os.path.join(OUT_SVG, name + ".svg"), "w") as f:
            f.write(svg)
        html.append(f'<div style="text-align:center;width:120px">{svg}<br><small>{name}</small></div>')
    html.append("</body></html>")
    with open(os.path.join(OUT_SVG, "preview.html"), "w") as f:
        f.write("\n".join(html))
    print("generated", len(ICONS))
