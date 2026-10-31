#!/usr/bin/env python3
"""Zeichnet die Feature-Grafik (1024 × 500) für den Store-Eintrag, je Sprache.

Im Stil NeedAnApp wie die Store-Bilder: hell in der Farbwelt „Wolke“ mit einem weichen Schein des Akzents, das Logo
auf einer weißen Karte, Name und Tagline in Geist, zwei Chips in der Fläche des Akzents und rechts zwei Handys mit den
Screenshots 01-discover (hell) und 06-repositories (dunkel) aus
myzel/fastlane/metadata/android/<sprache>/images/phoneScreenshots. Das Logo kommt aus
myzel/icon/myzel-icon-geflecht.svg, die Schriften aus dem Design-Paket. Braucht Pillow.

    python3 myzel/tools/make-store-graphics.py
"""
import os
import re

from PIL import Image, ImageDraw, ImageFilter, ImageFont

MYZEL = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FASTLANE = os.path.join(MYZEL, "fastlane", "metadata", "android")
LOGO = os.path.join(MYZEL, "icon", "myzel-icon-geflecht.svg")
FONTS = os.path.join(MYZEL, "needanapp-design", "design", "src", "main", "res", "font")

W, H = 1024, 500
# Zeichnen in doppelter Größe, am Ende verkleinert: glatte Kanten
S = 2

TEXTS = {
    "de-DE": {
        "tagline": ["Freie Apps – aus F-Droid", "und weiteren Paketquellen"],
        "chips": ["F-Droid · IzzyOnDroid · needanapp", "Favoriten · Sicherung · Root"],
    },
    "en-US": {
        "tagline": ["Free apps – from F-Droid", "and more repositories"],
        "chips": ["F-Droid · IzzyOnDroid · needanapp", "Favorites · Backup · Root"],
    },
}

# Farben aus needanapp-design: Farbwelt „Wolke“ (LightWorld.CLOUD) und Myzels Akzent im Stil NeedAnApp, wie in den
# Store-Bildern
BACKGROUND = (241, 243, 246)
CARD = (255, 255, 255)
OUTLINE = (224, 228, 234)
INK = (27, 33, 48)
LABEL = (89, 98, 115)
ACCENT = (174, 50, 0)
# Fläche und Schrift der Chips, wie der gewählte Filter „Neue Apps“
CHIP = (255, 219, 208)
ON_CHIP = (109, 34, 8)
# Schein des Akzents im Hintergrund
GLOW = (255, 181, 158)
# Rahmen der Handys
BEZEL = (27, 33, 48)
# Farbe der Schatten unter Handys und Logo-Karte
SHADOW = (27, 33, 48)


def font(weight, size, mono=False):
    # variable Schriften: Stärke über ihren Namen setzen
    result = ImageFont.truetype(os.path.join(FONTS, "geist_mono.ttf" if mono else "geist.ttf"), size * S)
    result.set_variation_by_name(weight)
    return result


def background():
    image = Image.new("RGBA", (W * S, H * S), (*BACKGROUND, 255))
    # zwei große, weiche Scheine des Akzents, wie der Schein oben in der App
    glow = Image.new("RGBA", image.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(glow)
    for cx, cy, r, alpha in ((60, -100, 400, 105), (1010, 580, 340, 110)):
        draw.ellipse([(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S], fill=(*GLOW, alpha))
    return Image.alpha_composite(image, glow.filter(ImageFilter.GaussianBlur(110 * S)))


def shadow(image, box, radius, blur, alpha):
    layer = Image.new("RGBA", image.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle([v * S for v in box], radius * S, fill=(*SHADOW, alpha))
    return Image.alpha_composite(image, layer.filter(ImageFilter.GaussianBlur(blur * S)))


def bezier(p0, p1, p2, p3, steps):
    for i in range(steps + 1):
        t = i / steps
        u = 1 - t
        yield (u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0],
               u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1])


def logo(size):
    """Das Geflecht aus der SVG: Kurven „M x y C …“ mit runden Enden und Kreise, nur diese beiden Formen."""
    with open(LOGO, encoding="utf-8") as f:
        svg = f.read()
    color = re.search(r'fill="(#[0-9A-Fa-f]{6})"', svg).group(1)
    scale = size * 4 / 108
    image = Image.new("RGBA", (size * 4, size * 4), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    for width, data in re.findall(r'<path stroke-width="([\d.]+)" d="([^"]+)"', svg):
        r = float(width) * scale / 2
        for segment in re.findall(r"M([^MC]+)C([^M]+)", data):
            start = [float(v) for v in segment[0].split()]
            values = [float(v) for v in segment[1].split()]
            current = start
            for i in range(0, len(values), 6):
                p1, p2, p3 = values[i:i + 2], values[i + 2:i + 4], values[i + 4:i + 6]
                for x, y in bezier(current, p1, p2, p3, 160):
                    draw.ellipse([x * scale - r, y * scale - r, x * scale + r, y * scale + r], fill=color)
                current = p3
    for cx, cy, r in re.findall(r'<circle cx="([\d.]+)" cy="([\d.]+)" r="([\d.]+)"', svg):
        cx, cy, r = float(cx) * scale, float(cy) * scale, float(r) * scale
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)
    return image.resize((size, size), Image.LANCZOS)


def phone(screenshot, width):
    """Handy mit dunklem Rahmen und runden Ecken, unten offen (wird vom Bildrand abgeschnitten)."""
    bezel = 8 * S
    shot = Image.open(screenshot).convert("RGBA")
    inner = width * S - 2 * bezel
    shot = shot.resize((inner, round(shot.height * inner / shot.width)), Image.LANCZOS)
    body = Image.new("RGBA", (width * S, shot.height + 2 * bezel), (0, 0, 0, 0))
    ImageDraw.Draw(body).rounded_rectangle([0, 0, body.width - 1, body.height - 1], 38 * S, fill=(*BEZEL, 255))
    mask = Image.new("L", shot.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, shot.width - 1, shot.height - 1], 31 * S, fill=255)
    body.paste(shot, (bezel, bezel), mask)
    return body


def chip(image, x, y, text, chip_font):
    """Chip wie der gewählte Filter in der App: Fläche des Akzents, vorne ein Punkt im Akzent, Text wie geschrieben."""
    draw = ImageDraw.Draw(image)
    left, top, right, bottom = draw.textbbox((0, 0), text, font=chip_font)
    pad_x, dot, gap, height = 18 * S, 8 * S, 11 * S, 42 * S
    box = [x * S, y * S, x * S + right - left + 2 * pad_x + dot + gap, y * S + height]
    draw.rounded_rectangle(box, height // 2, fill=(*CHIP, 255))
    cy = box[1] + height // 2
    draw.ellipse([box[0] + pad_x, cy - dot // 2, box[0] + pad_x + dot, cy + dot // 2], fill=(*ACCENT, 255))
    draw.text((box[0] + pad_x + dot + gap - left, box[1] + (height - (bottom - top)) // 2 - top), text,
              font=chip_font, fill=(*ON_CHIP, 255))


def feature_graphic(locale):
    texts = TEXTS[locale]
    shots = os.path.join(FASTLANE, locale, "images", "phoneScreenshots")
    image = background()

    # Handys: hinten Paketquellen in Dunkel (rechts angeschnitten), vorne Entdecken in Hell
    back = phone(os.path.join(shots, "06-repositories.png"), 288)
    front = phone(os.path.join(shots, "01-discover.png"), 288)
    image = shadow(image, (738, 104, 1026, 620), 38, 16, 60)
    image.alpha_composite(back, (738 * S, 92 * S))
    image = shadow(image, (608, 62, 896, 620), 38, 22, 80)
    image.alpha_composite(front, (608 * S, 48 * S))

    # Logo auf der weißen Karte
    image = shadow(image, (64, 100, 168, 204), 26, 14, 34)
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle([64 * S, 92 * S, 168 * S, 196 * S], 26 * S, fill=(*CARD, 255), outline=(*OUTLINE, 255),
                           width=S)
    mark = logo(88 * S)
    image.alpha_composite(mark, ((116 - 44) * S, (144 - 44) * S))

    draw = ImageDraw.Draw(image)
    name_font = font("Bold", 76)
    # Grundlinie so, dass die x-Höhe in der Mitte des Felds liegt
    x_height = -draw.textbbox((0, 0), "x", font=name_font, anchor="ls")[1]
    draw.text((190 * S, 144 * S + x_height // 2), "myzel", font=name_font, anchor="ls", fill=(*INK, 255))

    tagline_font = font("Regular", 31)
    for i, line in enumerate(texts["tagline"]):
        draw.text((64 * S, (252 + i * 42) * S), line, font=tagline_font, anchor="ls", fill=(*LABEL, 255))

    chip_font = font("Medium", 17)
    for i, text in enumerate(texts["chips"]):
        chip(image, 64, 328 + i * 56, text, chip_font)

    out = os.path.join(FASTLANE, locale, "images", "featureGraphic.png")
    image.resize((W, H), Image.LANCZOS).convert("RGB").save(out, optimize=True)
    print(out)


if __name__ == "__main__":
    for locale in TEXTS:
        feature_graphic(locale)
