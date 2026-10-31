#!/usr/bin/env python3
"""Stellt den Upload-Satz für ein F-Droid-Repo zusammen: APK und Store-Eintrag aus myzel/fastlane.

    python3 myzel/tools/make-fdroid-upload.py debug <ziel> [--nur-metadaten]   Debug-Build für fdroid.pan1k.de
    python3 myzel/tools/make-fdroid-upload.py release <ziel> --quelle stabil   Release für apps.needanapp.de
    python3 myzel/tools/make-fdroid-upload.py release <ziel> --quelle test     Release für fdroid.needanapp.de (Testing)

Mit --nur-metadaten nur der Store-Eintrag ohne APK, etwa für neue Bilder. Version und Signatur kommen dann aus
--apk <pfad>, wenn die Quelle eine andere Version führt als der letzte Build (Testing: die letzte APK von dort).

<ziel> bekommt repo/<appid>_<code>.apk, metadata/<appid>.yml und metadata/<appid>/<sprache>/ mit name.txt,
summary.txt, description.txt, changelogs/, featureGraphic.png, phoneScreenshots/, wearScreenshots/ und
en-US/icon.png. Der Debug-Satz heißt „Myzel (Debug)“ und sagt das in der Beschreibung. Der Release muss mit dem Release-Schlüssel von needanapp.de
signiert sein; zu ihm gehört Myzel Wear als repo/<appid>_<code - 1000>.apk, die Uhr-Version von Myzel mit
demselben Paketnamen und Schlüssel (Debug-Builds legen sie bei). Für den Release entsteht zusätzlich sftp-batch.txt
für `sftp -b` (der Server leert seinen Eingang nach jedem Durchlauf, deshalb immer den ganzen Satz hochladen). Die
Quelle muss beim Release jedes Mal gewählt werden: stabil (apps.needanapp.de, Eingang /stabil/incoming) oder test
(fdroid.needanapp.de, Eingang /incoming); scheitert das cd dorthin, bricht sftp ab.
Vorher bauen: :app:assembleBasicDefaultDebug bzw. :app:assembleBasicDefaultRelease und :myzel-wear:assembleRelease.
Liest keine Passwörter.
"""
import os
import re
import shutil
import subprocess
import sys

MYZEL = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = os.path.dirname(MYZEL)
FASTLANE = os.path.join(MYZEL, "fastlane", "metadata", "android")
SDK = os.path.expanduser(os.environ.get("ANDROID_HOME", "~/Android/Sdk"))
APK_DIR = os.path.join(REPO, "app", "build", "outputs", "apk", "basicDefault")
WEAR_APK = os.path.join(MYZEL, "wear", "build", "outputs", "apk", "release", "myzel-wear-release.apk")
# wie wearVersionOffset in myzel/wear/build.gradle.kts: Myzel 1023 gehört zu Myzel Wear 23
WEAR_VERSION_OFFSET = 1_000
# Release-Schlüssel je Quelle (SHA-256 des Zertifikats): apps.needanapp.de nimmt seit 0.22.0 nur den neuen
# (needanapp-signing/stabil, bauen mit -Pneedanapp.signingDir=…/stabil), Testing bleibt beim bisherigen
RELEASE_CERT = {
    "stabil": "c60ea3b56da70e60eff5cb96dd8dc19a41860a3ed4b29aeb2a299f833a6351d9",
    "test": "4ca4f74f692f10e767cd84253851dc854f14d399b23c7c480bfabbd6fed54702",
}
# Eingang auf dem Server je Quelle (absolute Pfade im chroot von fdroid-upload)
INCOMING = {"stabil": "/stabil/incoming", "test": "/incoming"}
LOCALES = ["en-US", "de-DE"]

COMMON_YML = """Categories:
  - System
License: GPL-3.0-or-later
AuthorName: needanapp.de
AuthorWebSite: https://needanapp.de
SourceCode: https://github.com/needanapp-de/myzel
"""

DEBUG_NOTE = {
    "en-US": "This is the debug build for testing (de.needanapp.myzel.debug). It installs alongside the release.",
    "de-DE": "Dies ist der Debug-Build zum Testen (de.needanapp.myzel.debug). Er lässt sich neben dem Release "
             "installieren.",
}


def build_tool(name):
    tools = os.path.join(SDK, "build-tools")
    version = sorted(os.listdir(tools), key=lambda v: [int(p) if p.isdigit() else p for p in re.split(r"[.-]", v)])[-1]
    return os.path.join(tools, version, name)


def badging(apk):
    out = subprocess.run([build_tool("aapt2"), "dump", "badging", apk], capture_output=True, text=True,
                         check=True).stdout
    match = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", out, re.MULTILINE)
    return match.groups()


def wear_apk(app_id, code, version, cert):
    """Myzel Wear zum Release: gleiches Paket, gleicher Versionsname und Schlüssel, Uhr-Merkmal, passender Code."""
    if not os.path.exists(WEAR_APK):
        sys.exit(f"Fehlt: {WEAR_APK} – erst :myzel-wear:assembleRelease bauen")
    wear_id, wear_code, wear_version = badging(WEAR_APK)
    expected = int(code) - WEAR_VERSION_OFFSET
    if expected < 1:
        sys.exit(f"versionCode {code} von Myzel muss über {WEAR_VERSION_OFFSET} liegen (myzel/version.properties)")
    if (wear_id, int(wear_code), wear_version) != (app_id, expected, version):
        sys.exit(f"Myzel Wear passt nicht zum Release: {wear_id} {wear_version} ({wear_code}), "
                 f"erwartet {app_id} {version} ({expected})")
    if signer(WEAR_APK) != [cert]:
        sys.exit("Myzel Wear ist nicht (nur) mit dem Release-Schlüssel dieser Quelle signiert")
    out = subprocess.run([build_tool("aapt2"), "dump", "badging", WEAR_APK], capture_output=True, text=True,
                         check=True).stdout
    if "uses-feature: name='android.hardware.type.watch'" not in out:
        sys.exit("Myzel Wear verlangt das Merkmal android.hardware.type.watch nicht")
    return WEAR_APK, expected


def signer(apk):
    out = subprocess.run([build_tool("apksigner"), "verify", "--print-certs", apk], capture_output=True, text=True,
                         check=True).stdout
    return re.findall(r"certificate SHA-256 digest: ([0-9a-f]+)", out)


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read().strip()


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text + "\n")


def copy(src, dst):
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copy2(src, dst)


def metadata(target, app_id, debug):
    """Die Texte und Bilder aus fastlane in der Form, die fdroidserver unter metadata/<appid>/ erwartet."""
    for locale in LOCALES:
        src = os.path.join(FASTLANE, locale)
        dst = os.path.join(target, "metadata", app_id, locale)
        name = read(os.path.join(src, "title.txt"))
        description = read(os.path.join(src, "full_description.txt"))
        if debug:
            name += " (Debug)"
            description = DEBUG_NOTE[locale] + "\n\n" + description
        write(os.path.join(dst, "name.txt"), name)
        write(os.path.join(dst, "summary.txt"), read(os.path.join(src, "short_description.txt")))
        write(os.path.join(dst, "description.txt"), description)
        changelogs = os.path.join(src, "changelogs")
        for log in sorted(os.listdir(changelogs)):
            copy(os.path.join(changelogs, log), os.path.join(dst, "changelogs", log))
        images = os.path.join(src, "images")
        for image in ("icon.png", "featureGraphic.png"):
            if os.path.exists(os.path.join(images, image)):
                copy(os.path.join(images, image), os.path.join(dst, image))
        # Handy und Uhr (wearScreenshots, rund mit durchsichtigen Ecken)
        for kind in ("phoneScreenshots", "wearScreenshots"):
            shots = os.path.join(images, kind)
            for shot in sorted(os.listdir(shots)) if os.path.isdir(shots) else []:
                copy(os.path.join(shots, shot), os.path.join(dst, kind, shot))


def sftp_batch(target, incoming):
    """cd in den Eingang, dann mkdir und put für jede Datei, die APK zuerst; „-“ vor mkdir: vorhandene Ordner sind
    kein Fehler. Das cd steht ohne „-“: Scheitert es, bricht sftp ab, statt im falschen Eingang abzulegen."""
    lines = [f"lcd {target}", f"cd {incoming}"]
    walk = sorted(os.walk(target), key=lambda entry: (not os.path.relpath(entry[0], target).startswith("repo"), entry[0]))
    for root, dirs, files in walk:
        dirs.sort()
        rel = os.path.relpath(root, target)
        if rel != ".":
            lines.append(f"-mkdir {rel}")
        for name in sorted(files):
            if rel != "." and " " not in name:
                lines.append(f"put {os.path.join(rel, name)} {os.path.join(rel, name)}")
    write(os.path.join(target, "sftp-batch.txt"), "\n".join(lines))


def main():
    args = sys.argv[1:]
    if len(args) < 2 or args[0] not in ("debug", "release"):
        sys.exit(__doc__)
    kind, target = args[0], os.path.abspath(args[1])
    with_apk = "--nur-metadaten" not in args[2:]
    given_apk = None
    if "--apk" in args[2:]:
        if with_apk or args.index("--apk") + 1 >= len(args):
            sys.exit("--apk <pfad> nur zusammen mit --nur-metadaten")
        given_apk = os.path.abspath(os.path.expanduser(args[args.index("--apk") + 1]))
    source = None
    if kind == "release":
        # ohne Vorgabe: stabil oder Testing entscheidet sich bei jedem Release neu
        if "--quelle" not in args[2:] or args.index("--quelle") + 1 >= len(args):
            sys.exit("Release braucht --quelle stabil oder --quelle test")
        source = args[args.index("--quelle") + 1]
        if source not in INCOMING:
            sys.exit(f"Unbekannte Quelle {source}: stabil oder test")
    apk = given_apk or os.path.join(APK_DIR, kind, f"app-basic-default-{kind}.apk")
    if not os.path.exists(apk):
        sys.exit(f"Fehlt: {apk} – erst :app:assembleBasicDefault{kind.capitalize()} bauen")
    app_id, code, version = badging(apk)
    if kind == "release" and signer(apk) != [RELEASE_CERT[source]]:
        sys.exit(f"Release-APK ist nicht (nur) mit dem Release-Schlüssel für {source} signiert")

    if os.path.exists(target):
        shutil.rmtree(target)
    if with_apk:
        copy(apk, os.path.join(target, "repo", f"{app_id}_{code}.apk"))
        if kind == "release":
            wear, wear_code = wear_apk(app_id, code, version, RELEASE_CERT[source])
            copy(wear, os.path.join(target, "repo", f"{app_id}_{wear_code}.apk"))
    if kind == "debug":
        # immer die neueste Debug-Version vorschlagen
        yml = COMMON_YML + "\nCurrentVersionCode: 2147483647\n"
    else:
        yml = COMMON_YML + f"\nCurrentVersion: {version}\nCurrentVersionCode: {code}\n"
    write(os.path.join(target, "metadata", f"{app_id}.yml"), yml.rstrip("\n"))
    metadata(target, app_id, debug=kind == "debug")
    if kind == "release":
        sftp_batch(target, INCOMING[source])
    print(f"{app_id} {version} ({code}) -> {target}" + (f" für {source} ({INCOMING[source]})" if source else ""))


if __name__ == "__main__":
    main()
