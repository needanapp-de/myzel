#!/usr/bin/env python3
"""Nimmt einen Store-Screenshot vom angeschlossenen Gerät auf und übermalt die Statusleiste.

Statt des Demo-Modus der Statusleiste (unter Android 17 bleibt danach die Uhr stehen) wird jede Zeile der
Statusleiste mit der Farbe in ihrer Mitte gefüllt; dort liegt kein Symbol, und der Hintergrund von Myzel ändert
sich nur von oben nach unten. Die Höhe der Statusleiste kommt aus `dumpsys window`.

    ANDROID_SERIAL=<gerät> python3 myzel/tools/store-screenshot.py <ziel.png>
"""
import io
import re
import subprocess
import sys

from PIL import Image


def adb(*args):
    result = subprocess.run(["adb", *args], capture_output=True)
    if result.returncode != 0:
        # z. B. mehrere Geräte angeschlossen und ANDROID_SERIAL nicht gesetzt
        sys.exit(f"adb {' '.join(args)}: {result.stderr.decode().strip()}")
    return result.stdout


def status_bar_height():
    windows = adb("shell", "dumpsys", "window").decode()
    match = re.search(r"type=statusBars frame=\[0,0\]\[\d+,(\d+)\]", windows)
    if not match:
        sys.exit("Höhe der Statusleiste nicht gefunden")
    return int(match.group(1))


def main(target):
    height = status_bar_height()
    image = Image.open(io.BytesIO(adb("exec-out", "screencap", "-p"))).convert("RGB")
    pixels = image.load()
    for y in range(height):
        color = pixels[image.width // 2, y]
        for x in range(image.width):
            pixels[x, y] = color
    image.save(target, optimize=True)
    print(target, image.size)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
