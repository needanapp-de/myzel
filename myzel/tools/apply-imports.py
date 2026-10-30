#!/usr/bin/env python3
"""Tauscht in den Dateien von F-Droid Bausteine gegen die von Myzel, nur über Import-Zeilen.

Die Bildschirme von F-Droid bleiben dadurch unverändert, bis auf einzelne Imports. Nach einem
Update von F-Droid (Konflikte in Import-Zeilen: Version von F-Droid nehmen) einfach erneut laufen
lassen; das Skript ist idempotent. Anschließend sortiert ktfmt die Imports:

    python3 myzel/tools/apply-imports.py
    ./gradlew :app:ktfmtFormatMain :app:ktfmtFormatTest

Mit --check wird nur geprüft, ob alle Tausche angewendet sind (Rückgabewert 1, wenn nicht).
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCES = [ROOT / "app/src/main/kotlin/org/fdroid", ROOT / "app/src/basic/kotlin/org/fdroid"]
TEST_SOURCES = [ROOT / "app/src/test/java/org/fdroid"]

# (Datei relativ zu org/fdroid oder None für alle Dateien, Import von F-Droid, Import von Myzel).
# Ohne Import von F-Droid (None) wird der von Myzel ergänzt: für Bausteine aus demselben Paket, die
# ohne Import benutzt werden; ein ausdrücklicher Import hat in Kotlin Vorrang vor dem eigenen Paket.
SWAPS = [
    # Stufe 2: Platz für die schwebenden Pillen
    (None, "androidx.compose.material3.Scaffold", "de.needanapp.myzel.ui.compat.Scaffold"),
    # Stufe 2: Pillen statt Leiste unten
    ("ui/MainContent.kt", "org.fdroid.ui.navigation.BottomBar", "de.needanapp.myzel.ui.nav.BottomBar"),
    ("ui/MainContent.kt", "org.fdroid.ui.navigation.NavigationRail",
     "de.needanapp.myzel.ui.nav.NavigationRail"),
    ("ui/MainContent.kt", "org.fdroid.ui.navigation.rememberResponsiveNavigationSceneDecoratorStrategy",
     "de.needanapp.myzel.ui.nav.rememberResponsiveNavigationSceneDecoratorStrategy"),
    # Stufe 2: Knöpfe oben in Entdecken wandern in die Werkzeugpille
    ("ui/discover/Discover.kt", "org.fdroid.ui.navigation.topBarMenuItems",
     "de.needanapp.myzel.ui.discover.topBarMenuItems"),
    ("ui/discover/Discover.kt", "org.fdroid.ui.utils.TopAppBarOverflowButton",
     "de.needanapp.myzel.ui.discover.TopAppBarOverflowButton"),
    # Stufe 3: Einstellungen als gruppierte Karten, Darstellung aus dem Design-Paket
    ("ui/Main.kt", "org.fdroid.ui.settings.Settings", "de.needanapp.myzel.ui.settings.Settings"),
    # Stufe 3: Über Myzel statt Über F-Droid
    ("ui/Main.kt", None, "de.needanapp.myzel.ui.About"),
    ("ui/details/NoAppSelected.kt", "org.fdroid.ui.AboutContent", "de.needanapp.myzel.ui.AboutContent"),
    # Stufe 3: aufklappbare Abschnitte wie die Gruppen des Design-Pakets
    (None, "org.fdroid.ui.utils.ExpandableSection", "de.needanapp.myzel.ui.compat.ExpandableSection"),
    # Stufe 3: Kategorien auf „Entdecken“ als Gruppe wie in den Einstellungen
    ("ui/categories/CategoryList.kt", "androidx.compose.material3.OutlinedCard",
     "de.needanapp.myzel.ui.compat.OutlinedCard"),
    ("ui/categories/CategoryList.kt", "androidx.compose.material3.HorizontalDivider",
     "de.needanapp.myzel.ui.compat.HorizontalDivider"),
    # Paketquellen: eingeschaltete oben, weitere darunter zum Aufklappen
    ("ui/repositories/Repositories.kt", None, "de.needanapp.myzel.ui.repositories.RepositoriesList"),
    ("ui/discover/Discover.kt", None, "de.needanapp.myzel.ui.discover.DiscoverContent"),
    ("ui/discover/Discover.kt", "androidx.compose.material3.TopAppBar", "de.needanapp.myzel.ui.discover.TopAppBar"),
    # Kopfleisten behalten beim Scrollen ihre Farbe statt eines dunklen Bands (nach Discover.kt,
    # das seine eigene bekommt)
    (None, "androidx.compose.material3.TopAppBar", "de.needanapp.myzel.ui.compat.TopAppBar"),
    # kurze Überblendung statt 700 ms beim Wechsel der Bereiche
    ("ui/MainContent.kt", "androidx.navigation3.ui.NavDisplay", "de.needanapp.myzel.ui.nav.NavDisplay"),
    # die Bereiche der Pille bleiben zusammengesetzt (TopLevelPages)
    ("ui/Main.kt", "org.fdroid.ui.navigation.toEntries", "de.needanapp.myzel.ui.nav.toEntries"),
    ("ui/Main.kt", "org.fdroid.ui.navigation.extraNavigationEntries", "de.needanapp.myzel.ui.nav.extraNavigationEntries"),
    ("ui/details/AppDetails.kt", None, "de.needanapp.myzel.ui.details.AppDetailsHeader"),
    # App-Details als Karten (Laden und „nicht gefunden“ bleiben die Seite von F-Droid)
    ("ui/details/AppDetailsEntry.kt", None, "de.needanapp.myzel.ui.details.AppDetails"),
    ("ui/details/AppDetailsHeader.kt", None, "de.needanapp.myzel.ui.details.RepoChooser"),
    # Stufe 3: Listenzeilen als Karten wie in den Einstellungen
    # Stempel „Installiert“ und „Neu“ statt des Häkchens
    ("ui/lists/AppList.kt", None, "de.needanapp.myzel.ui.lists.AppListRow"),
    ("ui/search/SearchResults.kt", "org.fdroid.ui.lists.AppListRow", "de.needanapp.myzel.ui.lists.AppListRow"),
    ("ui/repositories/add/AddRepoPreviewScreen.kt", "org.fdroid.ui.lists.AppListRow",
     "de.needanapp.myzel.ui.lists.AppListRow"),
    ("ui/details/AppDetailsHeader.kt", "androidx.compose.material3.BadgedBox", "de.needanapp.myzel.ui.compat.BadgedBox"),
    ("ui/details/AppDetailsHeader.kt", "org.fdroid.ui.utils.InstalledBadge", "de.needanapp.myzel.ui.compat.InstalledBadge"),
    # Installation ohne Nachfrage über Root oder Shizuku
    # Hinweiskarten im Kartenlook des Design-Pakets
    (None, "org.fdroid.ui.utils.OnboardingCard", "de.needanapp.myzel.ui.compat.OnboardingCard"),
    (None, "org.fdroid.ui.utils.OnboardingPopupCard", "de.needanapp.myzel.ui.compat.OnboardingPopupCard"),
    (None, "org.fdroid.ui.utils.HintOverlayContainer", "de.needanapp.myzel.ui.compat.HintOverlayContainer"),
    # Meine Apps im Stil der Einstellungen, mit dem Abschnitt „Auf der Uhr“
    ("ui/apps/MyApps.kt", None, "de.needanapp.myzel.ui.apps.MyAppsList"),
    ("ui/history/History.kt", None, "de.needanapp.myzel.ui.history.HistoryList"),
    # Paketquelle-Details aufgebaut wie die App-Seite
    ("ui/repositories/details/RepoDetails.kt", None, "de.needanapp.myzel.ui.repositories.RepoDetailsContent"),
    # Kopfleisten wie in needafile: Knöpfe ohne Tooltip, Menüs als Blatt, Hinzufügen als runder Knopf
    # (nach den Einträgen für Discover.kt, das sein eigenes ⋮ bekommt)
    (None, "org.fdroid.ui.utils.TopAppBarButton", "de.needanapp.myzel.ui.compat.TopAppBarButton"),
    (None, "org.fdroid.ui.utils.BackButton", "de.needanapp.myzel.ui.compat.BackButton"),
    (None, "org.fdroid.ui.utils.TopAppBarOverflowButton", "de.needanapp.myzel.ui.compat.TopAppBarOverflowButton"),
    (None, "androidx.compose.material3.DropdownMenu", "de.needanapp.myzel.ui.compat.DropdownMenu"),
    (None, "androidx.compose.material3.DropdownMenuItem", "de.needanapp.myzel.ui.compat.DropdownMenuItem"),
    (None, "androidx.compose.material3.FloatingActionButton", "de.needanapp.myzel.ui.compat.FloatingActionButton"),
    ("ui/lists/AppList.kt", "androidx.compose.material3.TooltipBox", "de.needanapp.myzel.ui.compat.TooltipBox"),
    # kleine Dialoge als Dialoge des Design-Pakets mit Symbol
    ("ui/repositories/details/RepoDetails.kt", None, "de.needanapp.myzel.ui.compat.DeleteDialog"),
    ("ui/repositories/details/RepoDetails.kt", None, "de.needanapp.myzel.ui.compat.QrCodeDialog"),
    ("ui/Main.kt", "org.fdroid.ui.history.History", "de.needanapp.myzel.ui.history.History"),
    # Filter von „Alle Apps“ aufgebaut wie die Einstellungen
    ("ui/lists/AppList.kt", None, "de.needanapp.myzel.ui.lists.AppsFilter"),
    # Warnung bei getakteter Verbindung als Dialog des Design-Pakets
    (None, "org.fdroid.ui.utils.MeteredConnectionDialog", "de.needanapp.myzel.ui.compat.MeteredConnectionDialog"),
    # Suche: Feld als Pille, Abschnitte wie in „Meine Apps“
    ("ui/Main.kt", "org.fdroid.ui.search.GlobalSearch", "de.needanapp.myzel.ui.search.GlobalSearch"),
    ("ui/lists/AppList.kt", "org.fdroid.ui.search.TopSearchBar", "de.needanapp.myzel.ui.search.TopSearchBar"),
    ("ui/apps/MyApps.kt", "org.fdroid.ui.search.TopSearchBar", "de.needanapp.myzel.ui.search.TopSearchBar"),
    # Paketquelle hinzufügen: QR-Code und Adresse als Karten, Vorschau wie die Details
] + [
    ("ui/repositories/add/AddRepo.kt", None, f"de.needanapp.myzel.ui.repositories.add.{name}")
    for name in ["AddRepoIntroContent", "AddRepoPreviewScreen", "AddRepoProgressScreen", "AddRepoErrorScreen"]
] + [
    ("install/AppInstallManager.kt", None, "de.needanapp.myzel.install.SessionInstallManager"),
    ("ui/details/AppDetailsItem.kt", "org.fdroid.install.SessionInstallManager",
     "de.needanapp.myzel.install.SessionInstallManager"),
] + [
    # Design „Gerät“: Zurück und Knöpfe der Kopfleisten als runde Alu-Tasten
    (f, "androidx.compose.material3.IconButton", "de.needanapp.myzel.ui.compat.IconButton")
    for f in ["ui/repositories/Repositories.kt", "ui/repositories/add/AddRepo.kt", "ui/lists/AppList.kt", "ui/About.kt"]
] + [
    # Design „Gerät“: Schalter in der Signalfarbe mit hellem Daumen
    (None, "androidx.compose.material3.Switch", "de.needanapp.myzel.ui.compat.Switch"),
    # Häkchen-Kästchen in der Signalfarbe wie die Schalter
    (None, "androidx.compose.material3.Checkbox", "de.needanapp.myzel.ui.compat.Checkbox"),
] + [
    (f, "androidx.compose.material3.ListItem", "de.needanapp.myzel.ui.compat.ListItem")
    for f in [
        "ui/repositories/RepositoryRow.kt",
        "ui/lists/AppListRow.kt",
        "ui/apps/InstalledAppRow.kt",
        "ui/apps/InstallingAppRow.kt",
    ]
]


# Wie SWAPS, für die Unit-Tests von F-Droid (Datei relativ zu test/java/org/fdroid): Tests, die
# einen getauschten Baustein mocken, müssen den von Myzel mocken.
TEST_SWAPS = [
    ("install/AppInstallManagerTest.kt", None, "de.needanapp.myzel.install.SessionInstallManager"),
]


def files():
    for src in SOURCES:
        for path in sorted(src.rglob("*.kt")):
            yield path, SWAPS
    for src in TEST_SOURCES:
        for path in sorted(src.rglob("*.kt")):
            yield path, TEST_SWAPS


def main() -> int:
    check = "--check" in sys.argv
    missing = []
    changed = 0
    for path, swaps in files():
        # Pfad relativ zu .../kotlin/org/fdroid bzw. .../java/org/fdroid
        parts = re.split(r"/(?:kotlin|java)/org/fdroid/", path.as_posix(), maxsplit=1)
        rel = parts[1] if len(parts) == 2 else None
        text = path.read_text(encoding="utf-8")
        new = text
        for target, old, repl in swaps:
            if target is not None and target != rel:
                continue
            if old is None:
                if f"import {repl}" not in new:
                    new = re.sub(r"^import ", f"import {repl}\nimport ", new, count=1, flags=re.M)
                continue
            pattern = re.compile(rf"^import {re.escape(old)}$", re.M)
            if pattern.search(new):
                new = pattern.sub(f"import {repl}", new)
            elif target is not None and f"import {repl}" not in new:
                missing.append(f"{rel}: weder {old} noch {repl} importiert")
        if new != text:
            changed += 1
            if not check:
                path.write_text(new, encoding="utf-8")
            else:
                missing.append(f"{rel}: Tausch noch nicht angewendet")
    for m in missing:
        print("Hinweis:", m)
    if check:
        return 1 if missing else 0
    print(f"{changed} Dateien geändert")
    return 0


if __name__ == "__main__":
    sys.exit(main())
