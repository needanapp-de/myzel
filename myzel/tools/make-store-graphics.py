#!/usr/bin/env python3
"""Zeichnet die Feature-Grafik (1024 × 500) für den Store-Eintrag, je Sprache.

Im Design „Gerät“ wie die App seit 0.17: Graphit mit orangem Schein (Signalfarbe #FF5A1F), Alu-Feld mit dem Logo, Name
und Tagline in Geist, zwei Chips als Tasten mit Signal-LED in Geist Mono und rechts zwei Handys mit den Screenshots
1-discover und 5-repositories aus myzel/fastlane/metadata/android/<sprache>/images/phoneScreenshots. Das Logo kommt aus
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

# Farben aus needanapp-design (DesignColors): Night und Graphit, Alu, Schrift und Beschriftung im Dunkeln
TOP_LEFT = (17, 17, 17)
BOTTOM_RIGHT = (44, 44, 42)
SIGNAL = (255, 90, 31)
ALU = (228, 228, 224)
INK = (237, 237, 234)
LABEL = (190, 190, 184)
# Farbe der Schatten unter Handys und Logo-Feld
SHADOW = (0, 0, 0)


def font(weight, size, mono=False):
    # variable Schriften: Stärke über ihren Namen setzen
    result = ImageFont.truetype(os.path.join(FONTS, "geist_mono.ttf" if mono else "geist.ttf"), size * S)
    result.set_variation_by_name(weight)
    return result


def background():
    # Verlauf von oben links nach unten rechts
    small = Image.new("RGB", (64, 32))
    for y in range(32):
        for x in range(64):
            t = (x / 63 * 0.6 + y / 31 * 0.4)
            small.putpixel((x, y), tuple(round(a + (b - a) * t) for a, b in zip(TOP_LEFT, BOTTOM_RIGHT)))
    image = small.resize((W * S, H * S), Image.BILINEAR).convert("RGBA")
    # zwei große, weiche Scheine der Signalfarbe, wie der Schein hinter den Bildschirmen der App
    glow = Image.new("RGBA", image.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(glow)
    for cx, cy, r, alpha in ((120, 600, 360, 46), (1000, -60, 320, 34)):
        draw.ellipse([(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S], fill=(*SIGNAL, alpha))
    return Image.alpha_composite(image, glow.filter(ImageFilter.GaussianBlur(90 * S)))


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
    # feine Kante, damit sich der Rahmen vom Graphit abhebt
    ImageDraw.Draw(body).rounded_rectangle([0, 0, body.width - 1, body.height - 1], 38 * S, fill=(18, 19, 24, 255),
                                           outline=(78, 78, 74, 255), width=2 * S)
    mask = Image.new("L", shot.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, shot.width - 1, shot.height - 1], 31 * S, fill=255)
    body.paste(shot, (bezel, bezel), mask)
    return body


def chip(image, x, y, text, chip_font):
    """Taste wie in der App: Graphit mit feinem Rand, vorne eine LED in der Signalfarbe, Beschriftung in Großbuchstaben."""
    text = text.upper()
    # halbdurchsichtig: auf eigener Ebene zeichnen, ImageDraw selbst mischt nicht
    layer = Image.new("RGBA", image.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    left, top, right, bottom = draw.textbbox((0, 0), text, font=chip_font)
    pad_x, led, gap, height = 16 * S, 8 * S, 11 * S, 40 * S
    box = [x * S, y * S, x * S + right - left + 2 * pad_x + led + gap, y * S + height]
    draw.rounded_rectangle(box, height // 2, fill=(255, 255, 255, 16), outline=(255, 255, 255, 52), width=S)
    image.alpha_composite(layer)
    draw = ImageDraw.Draw(image)
    cy = box[1] + height // 2
    draw.ellipse([box[0] + pad_x, cy - led // 2, box[0] + pad_x + led, cy + led // 2], fill=(*SIGNAL, 255))
    draw.text((box[0] + pad_x + led + gap - left, box[1] + (height - (bottom - top)) // 2 - top), text,
              font=chip_font, fill=(*INK, 255))


def feature_graphic(locale):
    texts = TEXTS[locale]
    shots = os.path.join(FASTLANE, locale, "images", "phoneScreenshots")
    image = background()

    # Handys: hinten Paketquellen (rechts angeschnitten), vorne Entdecken
    back = phone(os.path.join(shots, "5-repositories.png"), 288)
    front = phone(os.path.join(shots, "1-discover.png"), 288)
    image = shadow(image, (738, 100, 1026, 620), 38, 14, 90)
    image.alpha_composite(back, (738 * S, 92 * S))
    image = shadow(image, (608, 56, 896, 620), 38, 18, 120)
    image.alpha_composite(front, (608 * S, 48 * S))

    # Logo im weißen Feld
    image = shadow(image, (64, 98, 168, 202), 26, 10, 70)
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle([64 * S, 92 * S, 168 * S, 196 * S], 26 * S, fill=(*ALU, 255))
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

    chip_font = font("Medium", 15, mono=True)
    for i, text in enumerate(texts["chips"]):
        chip(image, 64, 330 + i * 54, text, chip_font)

    out = os.path.join(FASTLANE, locale, "images", "featureGraphic.png")
    image.resize((W, H), Image.LANCZOS).convert("RGB").save(out, optimize=True)
    print(out)


if __name__ == "__main__":
    for locale in TEXTS:
        feature_graphic(locale)
