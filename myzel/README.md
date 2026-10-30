# Myzel

Myzel von needanapp.de ist ein App-Store für freie Software aus F-Droid-Paketquellen. Er basiert auf
dem [F-Droid-Client](https://gitlab.com/fdroid/fdroidclient) (Variante *basic*) und trägt das Design
von needanapp.de (Paket `needanapp-design`).

## Aufbau

Damit Updates von F-Droid übernommen werden können, ändert Myzel möglichst wenig an Dateien von
F-Droid. Alles Eigene liegt in neuen Dateien:

| Wo | Was |
| --- | --- |
| `app/myzel.gradle` | alle Build-Abweichungen: App-ID, minSdk 31, Sprachen, Design-Paket, Uhr |
| `myzel/version.properties` | Version von Myzel und Myzel Wear |
| `app/src/myzel/res/` | Ressourcen, die die von F-Droid überschreiben (Name, Symbol, Texte, Sprachliste) |
| `app/src/myzel/AndroidManifest.xml` | Ergänzungen zum Manifest (Sprachwahl unter Android 12) |
| `app/src/main/res/values*/myzel_strings.xml` | eigene Texte (neue Dateien, kollidieren nicht mit `strings.xml`) |
| `app/src/main/kotlin/de/needanapp/myzel/` | eigener Code |
| `myzel/tools/apply-imports.py` | tauscht Bausteine von F-Droid über Import-Zeilen gegen die von Myzel |
| `app/src/main/assets/needanapp_repos.json` | zusätzliche Paketquellen (needanapp.de = apps.needanapp.de und IzzyOnDroid an, „needanapp.de Testing“ = fdroid.needanapp.de und die Droid-ify-Liste aus); fdroid.pan1k.de war bis 0.19.0 dabei (aus), `repo.RemovedRepoCleanup` löscht es beim ersten Start einmal, wenn es noch aus ist. Bestehende Installationen bekommen apps.needanapp.de einmal über `repo.NeedanappRepoMove`, der dabei das Testing-Repo ohne gespeicherte Anmeldung ausschaltet |
| `myzel/needanapp-design/` | Design-Paket als Git-Submodul, Hauptzweig `main` (seit 0.47.0 mit den Stilen Myzel und NeedAnApp; vorher die Zweige `zwei-designs`, `geraet` und `0.21.x`) |
| `myzel/design/build.gradle.kts` | baut das Design-Paket mit den Werkzeugen von F-Droid |
| `myzel/icon/` | Quelle des App-Symbols |
| `myzel/fastlane/metadata/android/` | Store-Eintrag: Texte, Changelogs, Symbol, Feature-Grafik, Screenshots (de-DE, en-US) |
| `myzel/link/` | Myzel Link: Verbindung zu Myzel Wear über Bluetooth (Modul `:myzel-link`) |
| `myzel/wear/` | Myzel Wear, die App für Wear-OS-Uhren (Modul `:myzel-wear`) |

Eingriffe in Dateien von F-Droid sind mit `// Myzel` markiert (`git grep -n "Myzel"`).

### Bildschirme

Die Bildschirme von F-Droid bleiben unverändert. Wo sie anders aussehen sollen, tauscht
`myzel/tools/apply-imports.py` nur die Import-Zeile gegen einen Ersatz von Myzel mit derselben
Signatur (Liste `SWAPS` im Skript):

| Ersatz (`de.needanapp.myzel.ui…`) | statt | wo |
| --- | --- | --- |
| `compat.Scaffold` | Material-`Scaffold` | überall: Platz für die schwebenden Pillen, durchsichtig über dem Verlauf |
| `nav.BottomBar`, `nav.NavigationRail`, `nav.rememberResponsive…Strategy` | Leiste unten | `MainContent.kt`: Pille zentriert, rechts ein Menüknopf mit Paketquellen, Einstellungen und den Zielen von „Mehr“ |
| `nav.NavDisplay` | Navigation3-`NavDisplay` | `MainContent.kt`: Bereiche der Pille liegen zusammengesetzt darunter (`nav.TopLevelPages`) und wechseln ohne Überblendung; neue Seiten blenden nach 50 ms in 250 ms über der alten ein (statt 700 ms beide halbtransparent), zurück auf einen Bereich blendet die alte Seite aus; ohne Bewegung alles sofort |
| `nav.toEntries` | `navigation.toEntries` | `Main.kt`: dieselben Einträge, dazu die Hauptseite jedes Stapels für `nav.TopLevelPages` |
| `discover.topBarMenuItems`, `discover.TopAppBarOverflowButton` | Knöpfe oben | `Discover.kt`: leer, die Knöpfe sind in der Pille |
| `discover.TopAppBar` | Kopfzeile | `Discover.kt`: Logo und Schriftzug „myzel“ statt des App-Namens |
| `compat.TopAppBar` | Material-`TopAppBar` | überall außer `Discover.kt` (dessen Kopfzeile ruft sie auf): behält beim Scrollen ihre Farbe, statt als dunkles Band (surfaceContainer) über dem Verlauf zu stehen |
| `settings.Settings` | Einstellungen | `Main.kt`: gruppierte Karten, „Darstellung“ aus dem Design-Paket |
| `About`, `AboutContent` | Über F-Droid | `Main.kt`, `NoAppSelected.kt` |
| `compat.ExpandableSection` | aufklappbare Abschnitte | App-Details, Versionen, Paketquellen-Details |
| `compat.ListItem` | Listenzeilen | Paketquellen, App-Listen, Meine Apps: Zeilen als Karten |
| `compat.OutlinedCard`, `compat.HorizontalDivider` | Kategorien-Karte | `CategoryList.kt`: Gruppe wie in den Einstellungen |
| `repositories.RepositoriesList` | Liste der Paketquellen | `Repositories.kt`: eingeschaltete oben, ausgeschaltete unter „Weitere Paketquellen“ zum Aufklappen |
| `nav.extraNavigationEntries` | Ziele der Variante | `Main.kt`: die Ziele von F-Droid und dazu die Favoriten (`favorites.FavoritesScreen`) |
| `details.AppDetailsHeader`, `details.RepoChooser` | Kopf der App-Details, Kasten der Paketquelle | `AppDetails.kt`, `AppDetailsHeader.kt`: der Kasten auf zwei Dritteln, daneben das Myzel-Logo als Favoriten-Schalter |
| `discover.DiscoverContent` | Bereiche auf Entdecken | `Discover.kt`: von Neu und Kürzlich aktualisiert ist immer einer offen („Am häufigsten heruntergeladen“ fehlt: fest eingebaute Liste, zählt nur f-droid.org); seine Apps stehen als Karten mit Name, Version und Pillen (rechts übereinander, seit 0.14.2) in zwei Reihen ohne Überschrift und Kasten auf dem Hintergrund und blenden an den Rändern aus, am Ende „Alle anzeigen“; die anderen stehen als Karten darüber und darunter. Ziehen nach unten aktualisiert die Paketquellen (`discover.RepoRefreshBox`, Kreis unter der Kopfleiste über `compat.LocalScaffoldPadding`). Bis Neu und Kürzlich aktualisiert aus der Datenbank kommen, zeigt das Karussell die Apps vom letzten Start (`discover.DiscoverSnapshot`, `files/myzel/discover.json`), beim ersten Start leere Karten mit Ladeanzeige. Darunter `categories.CategoryList` als flaches Bedienfeld: alle Gruppen mit Kurznamen, vier je Reihe, feine Linien dazwischen, alphabetisch mit „Sonstiges“ am Ende und der Rubrik „Uhr“ (siehe „Uhr-Apps“); ein Tipp öffnet unter der Reihe ein kleines Display mit den Kategorien und „Alle →“ (alle Apps der Gruppe, `categories.groupCategoryId`, aufgelöst in `AppListViewModel.kt` mit `// Myzel`), höchstens eine Gruppe offen |
| `lists.AppListRow`, `compat.BadgedBox`, `compat.InstalledBadge` | App-Zeile, Häkchen „installiert“ | `AppList.kt`, `SearchResults.kt`, `AddRepoPreviewScreen.kt`, `AppDetailsHeader.kt`: statt des Häkchens Pillen neben dem Namen, „Update“ (Akzentfarbe, Liste aus dem `UpdatesManager` wie in „Meine Apps“) oder „Installiert“ (Grün) und „Neu“ für Apps, die höchstens 14 Tage in einer Paketquelle sind; installierte Apps ohne Update sind ausgegraut (Symbol ohne Farbe, alles blasser). Gemeinsam mit den Karten auf Entdecken in `apps.AppBadges.kt` |
| `details.AppDetails` | App-Details | `AppDetailsEntry.kt`: die App-Seite als Karten (`details.AppDetailsPage`, siehe „Kartenlook“) |
| `install.SessionInstallManager` | Installation | `AppInstallManager.kt` (und sein Test), `AppDetailsItem.kt`: mit Root oder Shizuku installiert Myzel selbst, sonst der Paketinstaller von F-Droid |

Hell und dunkel stellt das Design-Paket ein; die Einstellungsseite gleicht die Einstellung „theme“
von F-Droid daran an, damit AppCompat (Fenster, Systemleisten, `isSystemInDarkTheme()`) passt.

Die eigene Einstellungsseite enthält dieselben Einstellungen wie die von F-Droid, nur anders
dargestellt. Bei einem Update von F-Droid `org.fdroid.ui.settings.Settings` mit
`de.needanapp.myzel.ui.settings.Settings` vergleichen, ob Einstellungen dazugekommen oder
weggefallen sind (auch `extraNetworkSettings`/`extraPrivacySettings` der Variante basic, derzeit
leer).

Ebenso sind `repositories.RepositoriesList` (samt Dialogen), `repositories.RepoDragDrop`,
`discover.DiscoverContent` und `categories.CategoryList` (samt `CategoryGroupRow`) Kopien von
F-Droid: bei einem Update mit `org.fdroid.ui.repositories.RepositoriesList`,
`org.fdroid.ui.utils.DragDropState`, `org.fdroid.ui.discover.DiscoverContent` und
`org.fdroid.ui.categories.CategoryList`/`CategoryGroupRow` vergleichen. `favorites.FavoritesViewModel`
übernimmt das Umwandeln der Apps aus `org.fdroid.ui.lists.AppListViewModel.loadApps`.

### Favoriten

Die Favoriten sind eine Menge von Paketnamen in eigenen SharedPreferences (`myzel_favorites`,
`favorites.FavoritesManager`), nicht in der Datenbank von F-Droid. Geschaltet werden sie in den
App-Details mit dem Myzel-Logo neben der Paketquelle (ohne Rahmen, danach eine kurze Meldung), die
Liste öffnet die Menüpille.

Jeder Favorit ist eine Karte wie in „Meine Apps“ (`PanelCard`), langes Drücken klappt darunter
dasselbe Bedienfeld auf (`AppPanel`). Installierte Favoriten bekommen dafür ihren Stand aus
`UpdatesManager` (Update, Problem) bzw. vom Gerät; bei nicht installierten zeigt die Anzeige
„Nicht installiert“, vorn steht „Installieren“, und was eine installierte App braucht (Öffnen,
Ignorieren, App-Info, Entfernen), ist gedimmt. „Installieren“ nimmt die vorgeschlagene Version wie
`org.fdroid.install.UnarchiveWorker` und öffnet die App-Seite, die Fortschritt und Bestätigung
zeigt. Die Dialoge (getaktete Verbindung, Problem ignorieren, Deinstallieren) bringt
`rememberAppPanelHost` mit.

### Sicherung

Unter Einstellungen → Sicherung lassen sich Paketquellen, Favoriten, Einstellungen und Darstellung
einzeln in eine unverschlüsselte JSON-Datei sichern und wieder einspielen (`backup.BackupManager`,
Format und Prüfung in `backup/BackupFormat.kt`, Dialoge in `ui/settings/BackupDialogs.kt` nach dem
Vorbild von needafile). Zugangsdaten von Paketquellen kommen nicht hinein.

- Paketquellen und Favoriten kommen zu den vorhandenen dazu. Quellen werden über das Zertifikat
  zugeordnet; eine Adresse, die hier mit einem anderen Zertifikat eingetragen ist, wird
  übersprungen. Archive, Quellen ohne Web-Adresse und eigene Zugangsdaten bleiben außen vor.
- Die Reihenfolge stellt `RepoManager.reorderRepositories` Schritt für Schritt her. Das Umsortieren
  der Datenbank selbst ist intern (`RepositoryDaoInt`), deshalb wartet jeder Schritt, bis das neue
  Gewicht in der Datenbank steht.
- Einstellungen werden wie bei F-Droid über `SettingsManager.prefsFlow` geschrieben, die Schlüssel
  kommen aus `SettingsConstants`. Bei einem Update von F-Droid prüfen, ob Einstellungen
  dazugekommen sind, die in `BackupSettings` gehören.
- Die Darstellung spielt das Design-Paket ein (`ThemeSettingsStore.importBackup`), als Letztes und
  erst nachdem der Dialog geschlossen ist: Eine andere Sprache baut die Activity neu auf.

### Installation über Root oder Shizuku

Unter Einstellungen → Updates → Installation wählt man zwischen dem Paketinstaller von Android
(wie bei F-Droid), Root und Shizuku. Root und Shizuku werden erst eingestellt, wenn der Zugriff
klappt; dabei erscheint die Abfrage von Magisk bzw. Shizuku. Legacy (Installation per Intent) gibt
es nicht: ab Android 12 fragt sie genauso nach wie der Paketinstaller.

- `install.SessionInstallManager` ersetzt über einen ergänzten Import den von F-Droid in
  `AppInstallManager` und reicht alle Aufrufe durch. Mit Root oder Shizuku gibt es keine
  Vorabfreigabe, `install` übernimmt `install.PrivilegedInstaller`: `pm install -r -i <Myzel>
  --user current -S <Größe>` mit der APK über die Standardeingabe, über `su -c` bzw.
  `Shizuku.newProcess`. Fehlt der Zugriff (kein su, Shizuku gestoppt), installiert wieder der
  Paketinstaller; schlägt pm selbst fehl, zeigt Myzel dessen Fehler.
- `Shizuku.newProcess` ist seit der API 13 privat und wird über Reflexion aufgerufen;
  `app/src/myzel/proguard-myzel.pro` hält es im Release-Build. Den Binder bekommt Myzel über den
  `ShizukuProvider` in `app/src/myzel/AndroidManifest.xml`.
- Die Einstellung (`myzel_installMethod` in den Preferences von F-Droid) gehört zum Gerät und
  steht nicht in der Sicherung.
- `apply-imports.py` tauscht auch in `AppInstallManagerTest`, damit der Test den Baustein von
  Myzel mockt. Bei einem Update von F-Droid prüfen, ob `AppInstallManager` weitere Funktionen von
  `SessionInstallManager` aufruft.

### Stil „Myzel“ (früher Design „Gerät“)

Seit 0.17.0 sieht Myzel aus wie ein Gerät aus Alu und Graphit. Das Aussehen entstand auf dem Zweig
`geraet` des Design-Pakets (`0.21.3-geraet.3`) und ist seit Paket 0.47.0 dort im Hauptzweig der
Stil „Myzel“, der Standard von Myzel: hell Alu, dunkel Graphit, Leiste und Menüs in Graphit, Orange (#FF5A1F,
`MyzelAccent` in `ui/MyzelTheme.kt`) als Signalfarbe, Beschriftungen in Geist Mono und schwarze
Anzeigen mit Punktmatrix-Schrift. Das alte Aussehen in Türkis (Paket-Zweig `0.21.x`) steht im Tag
`myzel-0.16.0`.

- Das Myzel-Logo zeichnet `ui/MyzelLogo.kt` aus denselben Pfaden wie `R.drawable.myzel_logo`. Ändert
  sich `signal`, läuft einmal ein dezentes Signal wie auf myzel.dev hindurch (zwei Impulse vom Kern
  zu den Spitzen, kurzes Nachglimmen, kleine Ringe); ist die Bewegung aus, leuchten die Wege nur kurz.
- Die Pille (Entdecken, Suche, Meine Apps) ist 52 dp hoch, nur so breit wie mit „MEINE APPS“ aktiv
  und steht mittig; ihre Breite ändert sich zwischen den Seiten nicht.
- Die drei Seiten bleiben auf kleinen Bildschirmen zusammengesetzt und liegen nebeneinander unter
  NavDisplay (`ui/nav/TopLevelPages.kt`, `toEntries` per Importtausch in `Main.kt`); deren Szene ist
  leer und trägt nur die Pille. Ein Wechsel baut keine Seite neu auf, die Suche steht sofort mit
  ihren Kategorien da. Die beiden anderen Seiten entstehen 0,8 bzw. 1,1 s nach dem Start. Seiten,
  die nicht dran sind, bekommen keine Zurück-Ereignisse (eigener Dispatcher je Seite) und verlieren
  den Fokus, die Tastatur der Suche schließt sich.
- Zwischen den drei Seiten lässt sich wischen (`ui/nav/TopLevelSwipe.kt`, eingehängt in
  `MainContent.kt` mit `// Myzel`): Die Seiten folgen dem Finger; ab einem Drittel der Breite oder
  mit einem schnellen Wisch geht es zum Nachbarn, sonst federt die Seite zurück, am Rand gibt sie
  nur 48 dp nach. Was selbst waagrecht scrollt (Karussell), geht vor; ein Tipp auf die Pille
  wechselt sofort.
- Der Menüknopf unten rechts ist das Myzel auf Graphit (`menuButtonContent` in `ui/nav/BottomBar.kt`),
  oben im Menü steht eine kompakte Anzeige, ob die Paketquellen aktuell sind, an den Einträgen kurze
  Werte (Zahl der Favoriten, aktive Paketquellen, Version). Das Menü hat eine feste Größe; während
  einer Aktualisierung zeigt die Anzeige dort nur „LÄDT <QUELLE>“ und „n/gesamt“, ohne Punktreihe.
- Anzeigen oben auf Entdecken („N NEU · N UPDATES“ und Uhrzeit, danach im Wechsel dasselbe je
  Paketquelle mit ihrem Namen; Quellen ohne Neues fallen weg), in der Suche („N TREFFER“), in
  Meine Apps (Updates mit Größe und „Alle aktualisieren“) und über den Paketquellen („N AKTIV“). Die
  Anzeige der Paketquellen ist der erste Eintrag der Liste, deshalb bekommen die ziehbaren Zeilen
  ihren Index plus eins.
- Langes Drücken auf eine App in „Meine Apps“ klappt unter ihr ein Bedienfeld auf
  (`ui/apps/AppPanel.kt`, Vorschlag C im Design-Canvas): eine kompakte Anzeige mit dem Stand
  (Update bereit, Installiert, Problem, Keine Updates) und der Paketquelle, darunter acht Tasten mit
  feinen Linien wie bei den Kategorien: Aktualisieren, Öffnen, Favorit, Teilen, Überspringen bzw.
  Ignorieren (alle Updates, bei Problemen das Problem), Auf der Uhr, App-Info, Entfernen. Die Tasten
  stehen immer am selben Platz, was nicht geht, ist gedimmt; höchstens ein Feld ist offen, Zurück
  klappt es zu. Teilen braucht die Webadresse der Paketquelle (`webBaseUrl`). „Auf der Uhr“ bringt
  die neueste passende Version direkt hin, wenn die Uhr erreichbar ist, sonst öffnet es die App-Seite.
  Nicht bei Apps, die gerade installiert werden.
- Beim Aktualisieren der Paketquellen zeigen die Anzeigen auf Entdecken, im Menü und über den
  Paketquellen, welche Quelle gerade lädt, als wievielte von wie vielen, mit Prozent und einer
  Punktreihe für alle zusammen. Dafür meldet `RepoUpdateManager.updateRun` (`// Myzel`) die laufende
  Quelle; die Namen kürzt `displayName()` um ein angehängtes „F-Droid Repo“.
- Die Knöpfe der Kopfleisten sind runde Alu-Tasten (`compat.TopAppBarButton`); in Kopfleisten von
  F-Droid mit eigenem `IconButton` (Paketquellen, Paketquelle hinzufügen, App-Listen, Über) tauscht
  `apply-imports.py` ihn gegen `compat.IconButton`. Alle Schalter von F-Droid laufen über
  `compat.Switch` mit den Farben des Pakets (Signalfarbe, heller Daumen).
- Entstanden ist das Aussehen auf dem Zweig `geraet`, von dem 0.17.0 kam; seit 0.18.0 ist es der
  Hauptzweig `myzel` (vorgespult, `geraet` steht auf demselben Stand).

### Stil „NeedAnApp“

Unter Einstellungen → Darstellung → „Stil“ lässt sich zwischen „Myzel“ (Standard, sieht aus wie oben)
und „NeedAnApp“ wählen (`styles` in `ui/settings/Settings.kt`). NeedAnApp ist der Kartenlook von
needafile aus dem Hauptzweig des Pakets, wie Vorschlag B im Design-Canvas (Seite „needanapp“): hell
„Wolke“, dunkel „Nordnacht“ mit Orange als Hauptfarbe, Karten mit Schatten, Systemschrift, die
Bausteine aus „Myzel“ (Anzeigen, Bedienfeld, Myzel-Knopf, Menü mit Werten, LED in der Pille) im
needanapp-Look. Entstanden ist er auf dem Zweig `zwei-designs` (0.20.0), seit Paket 0.47.0 kommen
beide Stile aus dem Hauptzweig.

- Die Bildschirme fragen den Stil mit `isNeedanappDesign` ab und verzweigen am Anfang; der Weg von
  „Myzel“ bleibt unverändert (Bildschirmfotos vorher/nachher auf dem OnePlus bis auf Uhrzeit und
  wechselnde Anzeigen gleich, hell und dunkel).
- Die Pille mit Logo, Trennlinie und dem Menü mit Anzeige schaltet `myzelMenu = true` in
  `ui/nav/BottomBar.kt` ein; die Versionskarte der App-Seite zeigt den Zustand als Anzeige statt als
  Band (`VersionsCardFrame`).
- Meine Apps: Titel in der Hauptfarbe, jede App eine Karte (Symbol 40 dp), bei Updates ein runder
  Knopf statt „Was ist neu“, oben „Alle aktualisieren“ als Kachel; das Bedienfeld liegt in einem
  Raster mit Haarlinien.
- Entdecken (`ui/discover/NeedanappDiscover.kt`): die Anzeige in einer Karte, Neue Apps und Kürzlich
  aktualisiert als Chips, das Karussell in einer Reihe kleiner Karten (Apps mit Uhr-Version tragen
  rechts oben eine kleine Uhr), die Kategorien drei je Reihe in einer Karte.
- App-Seite: Kopf mit Autor und Kategorie, Versionskarte mit Kacheln, Haupttaste 52 dp, Bildschirmfotos
  ohne Karte, Optionen-Knopf 56 dp mit großem Myzel, ohne Abdunkeln.
- Listen von F-Droid (`compat.ListItem`, `compat.OutlinedCard`) werden Karten, die Knöpfe der
  Kopfleisten schlichte Symbole.

### Kartenlook

Das Aussehen kommt aus dem Design-Paket (ab 0.20, „Kartenlook“): getönte Flächen, ein Verlauf mit
einem Schein der Akzentfarbe hinter allen Bildschirmen und Inhalte auf Karten. Bis 0.16.0
brachte Myzel Türkis (#0097A7) als Standard-Akzent mit, seit 0.17.0 Orange (Stil „Myzel“); unter
Darstellung lässt sich alles weiter ändern.

Bis 0.20.0 blieb Myzel beim Aussehen von Paket 0.21 (Zweige `0.21.x`, `geraet`, `zwei-designs`).
Seit Paket 0.47.0 hängt es wieder am Hauptzweig wie die anderen Apps: Der Stil NeedAnApp ist der
aktuelle Kartenlook, der Stil Myzel das Gerät. `prepareMyzelAppearance` (`ui/MyzelTheme.kt`, aus
`App.onCreate`) setzt `ThemeSettingsStore.defaults` auf `MyzelThemeDefaults` (Stil Myzel, Orange)
und stellt beim ersten Start einmal Farbton, Helligkeiten, Paletten-Stil, Farbverlauf, Ecken und
Schrift auf die Standards zurück (Merker `styles_merged`): Die Zweige übergingen sie, auf dem
Hauptzweig gelten sie wieder. Seit 0.21.1 wird außerdem einmal die Vorlage Orange zur Akzentquelle
„Standard“ (Merker `orange_to_standard`): In 0.20.0 setzte jeder Tipp auf eine Signalfarbe die
Vorlage, die Farbwelten bringen ihre eigene Farbe aber nur bei „Standard“ mit. Im Stil Myzel sind alle Farboptionen nutzbar; bei Alu und Graphit
wirkt der Paletten-Stil nicht, Ecken und Schrift sind fest.

- `MyzelTheme` zeichnet den Verlauf hinter alles, `PillScene` innerhalb der Unschärfequelle der
  Pille. Steht eine Szene mit Pille still, meldet sie das über `LocalBackgroundCover`, und
  `MyzelTheme` lässt den Verlauf aus; während eines Übergangs zeichnet es ihn wieder. Der Ersatz `compat.Scaffold` ist durchsichtig und legt die Kopfleisten
  von F-Droid (Farbe surface) durchsichtig auf den passenden Ausschnitt des Verlaufs. Damit sie das
  auch beim Scrollen bleiben, behält `compat.TopAppBar` dabei die Farbe; im Farbschema lässt sich
  surfaceContainer nicht durchsichtig machen, sonst wären auch die Menüs der Kopfleiste durchsichtig.
- Die App-Seite ist `ui/details/AppDetailsPage.kt`, gebaut aus den Karten des Pakets: Kopf mit
  Zurück als Alu-Taste, großem Symbol und rechts dem Myzel als Favoriten-Schalter (beim Einschalten
  läuft das Signal durch das graue Myzel, dann leuchtet es in seiner Farbe), Versionen mit Anzeige
  für den Zustand („UPDATE 2.5.0 · VON 2.4.1“, beim Herunterladen „LÄDT 42 %“ mit Punkt-Fortschritt)
  und einem Raster aus Installiert, Neueste, Änderungen und Dateien, gleich darunter die Haupttaste
  (bei installierten Apps daneben Entfernen in Graphit), dann Screenshots, Beschreibung und der
  Rest nach Bedeutung: Uhr, Links, Details, Sicherheit (seit 0.15.0). Unten rechts schwebt der runde
  Optionen-Knopf mit dem Myzel; er klappt ein Graphit-Feld auf (App-Info, Teilen, APK teilen,
  Beta-Versionen, Updates ignorieren) und verschwindet beim Scrollen nach unten. Er hat eine eigene
  Unschärfequelle samt Verlauf, weil die Seite schon in der Quelle der Szene liegt und Haze keine
  Quelle eines Vorfahren unscharf zeichnet. Eingebunden wird sie über einen
  ergänzten Import in `AppDetailsEntry.kt` (`apply-imports.py`); Laden und „nicht gefunden“ zeigt
  weiter F-Droid. Bei einem Update von F-Droid mit `org.fdroid.ui.details.AppDetails` und
  `AppDetailsHeader` vergleichen (Installationsablauf in `InstallStateEffects`, Menü, Felder von
  `AppDetailsItem`).
- Neu und Kürzlich aktualisiert auf Entdecken sind Karten mit Symbol, Name und neuester Version
  (`ui/discover/AppCards.kt`, Versionen aus der Datenbank über `ui/discover/LatestVersion.kt`, eine
  Abfrage `VersionDao.getLatestVersionNames` je Karussell), hinter
  der Version die Pillen „Installiert“ und „Neu“; fehlt der Platz, entfällt „Neu“.

### Update-Diagnose

Lässt sich eine installierte App nicht aktualisieren, zeigt die App-Seite über der Versionskarte
die Karte „Update-Diagnose“ (`ui/details/UpdateDiagnosisCard.kt`): im Band das Problem, darunter
die Erklärung in Klartext, „Was hilft“ (oft mit Knopf, etwa „IzzyOnDroid bevorzugen“ oder
„Beta-Versionen erlauben“) und aufklappbar die Details: Version mit Code und Daten, Installiert
über, Angestoßen von, Herkunft (ab Android 13), Update-Besitzer (ab Android 14), Signatur mit
Inhaber und Hash, frühere Schlüssel, Besonderheiten (System-App, App-Bundle, archiviert,
deaktiviert, Debug), Ziel-SDK, Gerät, ob Myzel installieren darf, der letzte Installationsfehler
mit der Meldung von Android und je Paketquelle die neueste Version mit Signatur, ✓/✗ und was sie
bietet. „Bericht kopieren“ legt alles als Text in die Zwischenablage. Ohne Update-Knopf sagt das
Band der Versionskarte dann „Update nicht möglich“ (oder „Update aus anderer Quelle“, „Updates
ignoriert“) statt „Aktuell“.

- `diagnose/InstalledAppInfo.kt` liest aus, was Android über die installierte App und das Gerät
  verrät (`signingInfo`, `getInstallSourceInfo`, Flags von `ApplicationInfo`).
- `diagnose/UpdateDiagnosis.kt` ist reine Logik ohne Android (Tests in `UpdateDiagnosisTest`):
  Für jede Paketquelle wird geprüft, ob es Neueres gibt, mit passender Signatur (auch frühere
  Schlüssel; Versionen ohne Signaturangabe passen wie bei F-Droid zu allem), kompatibel, stabil oder
  Beta erlaubt, nicht ignoriert. Hat die bevorzugte Quelle ein Update, gibt es kein Problem (außer
  einem späteren Installationsfehler); hat eine andere eins, heißt es „Update nur bei …“; sonst
  erklärt die Quelle, die am weitesten kommt, das Problem.
- `diagnose/DiagnosisLoader.kt` lädt die Versionen aller eingeschalteten Paketquellen (die Seite
  selbst kennt nur die gewählte), ohne Uhr-Versionen.
- `diagnose/InstallErrorLog.kt` merkt sich je App den letzten gescheiterten Versuch
  (SharedPreferences „myzel_install_errors“), eine erfolgreiche Installation löscht ihn. Der
  Haken sitzt in `AppInstallManager.onStatesUpdated()` (`// Myzel:`).
- Die Warnungen von F-Droid (`AppDetailsWarnings`) erscheinen bei installierten Apps nur noch für
  Sicherheitslücken und wenn die Diagnose kein Problem findet; bei nicht installierten Apps wie
  bisher.
- In „Meine Apps“ steht bei Apps mit Problem unter der Version der Grund in wenigen Worten
  (`diagnose/IssueReason.kt`), etwa „Signatur passt nur bei IzzyOnDroid · über Google Play Store
  installiert“. F-Droid meldet dort Apps aus einem anderen Store nur, wenn eine Paketquelle die
  passende Signatur hat; die Karte auf der App-Seite erscheint auch ohne das.

### Keine Absturzberichte

Myzel enthält den Absturzbericht von F-Droid (ACRA) nicht. Entfernt sind die Abhängigkeiten
`acra-mail`/`acra-dialog` (`app/build.gradle.kts`, `gradle/libs.versions.toml`), der Start in
`App.kt` samt Prüfung auf den ACRA-Prozess, das Paket `org.fdroid.ui.crash` mit Manifest-Eintrag,
Service-Datei, Symbol und Screenshot-Test sowie der Testabsturz „CrashMe“ in `SearchManager`.
Stürzt Myzel ab, zeigt Android seinen normalen Dialog.

Die Texte `crash_*` bleiben in den `strings.xml` von F-Droid stehen, damit Übersetzungs-Updates
nicht jedes Mal kollidieren; im Release-Build entfernt das Shrinking sie.

### Paketquellen laden

`RepoUpdateManager.updateRepos` aktualisiert die Paketquellen über `de.needanapp.myzel.repo.inUpdateOrder`
(eine mit `// Myzel` markierte Zeile): die großen Indexe von f-droid.org zuletzt, sonst wie bei F-Droid
nach Priorität. So zeigt Entdecken beim ersten Start schon nach den kleinen Quellen Apps. Die
Priorität der Quellen bei Apps, die es mehrfach gibt, bleibt unverändert.

Lässt sich eine Quelle nicht laden, melden das die Anzeigen schon nach dem ersten Fehlschlag (F-Droid
wartet auf drei bzw. fünf): auf Entdecken und im Menü rot „! N QUELLE GESTÖRT“ und je Quelle
„! LÄDT NICHT“, in der Liste der Paketquellen ein rotes „! Lädt nicht“ unter dem Namen
(`RepositoryItem.hasIssue`, `RepositoryRow` mit `// Myzel`). Hat der Server mit 401 geantwortet
(`ui/nav/BottomBar.kt`: `needsLogin()` erkennt das an der gespeicherten Meldung von Ktor, über HTTP/2
nur „invalid: 401 .“), heißt es überall „! Anmeldung nötig“, und die Seite der Quelle zeigt statt der
Fehlerseite des Servers einen Hinweis auf das Formular „Anmeldung“.

### Anmeldung bei Paketquellen

- Beim Hinzufügen schaltet „Mit Anmeldung“ Felder für Benutzername und Passwort ein. Myzel setzt sie
  kodiert in die Adresse (`https://name:passwort@host/…`, `withCredentials` in
  `ui/repositories/add/AddRepoScreens.kt`), daraus holt sie F-Droids `RepoUriGetter`; der nimmt dafür
  das letzte `@` und den ersten `:` (`// Myzel`), damit `@` im Namen und `:` im Passwort heil bleiben.
  Verlangt der Server eine Anmeldung, sagt die Fehlerseite das statt der Ausnahme.
- Auf der Seite jeder Quelle steht unter Einstellungen das Formular „Anmeldung“, auch wenn noch
  nichts gespeichert ist. Speichern ruft `updateUsernameAndPassword` und lädt die Quelle neu.
- In die Sicherung kommen Zugangsdaten nicht.

### Neu und aktualisiert

- Karten und Zeilen aus „Kürzlich aktualisiert“ tragen eine Pille in Graphit mit dem Alter des letzten
  Updates in der Paketquelle („↑ heute“, „↑ gestern“, „↑ 3 T.“, bis 14 Tage), außer bei neuen Apps und
  bei Apps mit „Update“ (`rememberAppBadgeState(…, updatedAt)` in `ui/apps/AppBadges.kt`). Im Stil
  NeedAnApp steht das Alter mangels Platz vor der Version.
- Unter dem Karussell klappt „Neu und aktualisiert · N“ (`ui/discover/RecentExpander.kt`) die zwölf
  neuesten Apps beider Bereiche auf, nach Tagen gruppiert; „Weniger“ klappt zu, „Alle N“ öffnet die Seite.
- Die Seite `recent/RecentAppsScreen.kt` zeigt alle neuen und aktualisierten Apps der letzten 14 Tage
  nach Tagen, mit Chips für Alle/Neu/Aktualisiert/Installiert und die Paketquellen, als Karten wie die
  Favoriten (`ui/apps/AppListPanelCard.kt`, langes Drücken öffnet das Bedienfeld). Die Daten kommen aus
  `recent/RecentAppsSource.kt`: F-Droids „Kürzlich aktualisiert“ nimmt nur 200 Apps, das reicht bei
  f-droid.org kaum für zwei Tage, deshalb bis zu 2000; dieselbe Quelle zählt N für Entdecken.
- Ein Tipp auf die Anzeige oben in Entdecken öffnet die Seite, bei der Meldung einer Paketquelle nur mit
  deren Apps; bei einer Störung öffnet er die Paketquelle bzw. die Liste der Paketquellen.

### Uhr-Apps

Phase 3 des Wear-OS-Umbaus. Eine Version ist für die Uhr, wenn sie das Merkmal
`android.hardware.type.watch` verlangt; F-Droid übernimmt es aus dem Manifest in den Index, auf dem
Handy ist sie damit inkompatibel. `watch.WatchCatalog` holt alle Versionen der Apps, die so eine
Version haben (`VersionDao.getWatchAppVersionsFlow`, nur aus der bevorzugten Paketquelle), und teilt
sie auf:

- Reine Uhr-Apps (nur Uhr-Versionen, etwa Telewatch bei IzzyOnDroid) stehen nur in der Rubrik „Uhr“
  und in der Suche. Aus allen Listen (`AppListViewModel.loadApps`) und den Karussells auf Entdecken
  (`DiscoverViewModel`, vor dem Speichern des Schnappschusses) filtert Myzel sie heraus, je eine mit
  `// Myzel` markierte Stelle.
- Handy-Apps mit Uhr-Version (gleicher Paketname, etwa Catima) zählen nur, wenn eine Uhr-Version
  dieselbe Signatur hat wie eine Handy-Version.
- Myzel selbst (seit 0.14.1): Seine Uhr-Version ist Myzel Wear, die die Einrichtung und danach das
  Update von selbst auf die Uhr bringen. `WatchCatalog.shown` enthält es (`WatchApp.own`), damit es
  in der Rubrik „Uhr“ steht und die Pille „Uhr“ trägt; `WatchCatalog.apps`, aus dem Installer,
  „Auf der Uhr“ und Entdecken schöpfen, lässt es weg. Auf seiner App-Seite steht statt der Knöpfe
  die Karte `MyzelWearCard`: neueste Myzel Wear, die auf der Uhr (`PairedWatch.wearVersionCode`),
  ob ein Update läuft (`WearUpdateNote`) oder ansteht; ohne Uhr der Knopf „Uhr einrichten“, der zur
  Seite „Uhr“ führt (dafür nimmt `onNav` von `details.AppDetails` jeden `NavKey`). Die Testvariante
  `.profiling` nimmt dabei den Release als sich selbst.

Die Rubrik ist ein eigener Eintrag in `categories.CategoryList`, der die Liste
`AppListType.Category` mit der Kennung `watch.WATCH_CATEGORY_ID` öffnet; dort gelten Uhr-Apps als
kompatibel, damit der Filter „Inkompatible ausblenden“ sie nicht versteckt. Beide Arten tragen die
Pille „Uhr“ (`apps.AppBadges.kt`). Auf der App-Seite stehen Uhr-Versionen in einer eigenen Karte
„Uhr“ statt unter den Versionen; bei reinen Uhr-Apps zeigt der Zustand „Für die Uhr“, der Knopf ist
aus und die Warnung „nicht kompatibel“ entfällt.

### Uhr einrichten und Myzel Link

Wear-OS-Umbau Phase 1, 2, 4 und der Anfang von 5, übernommen aus dem Prototyp myzel-link-prototype (Repo myzel-wear),
der als Testbett bleibt.

- **Myzel Link** (`myzel/link`): das Protokoll (Frames, Anmeldung mit gemeinsamem Schlüssel per
  HMAC-SHA256 in beide Richtungen, Ping, APK mit SHA-256) und der RFCOMM-Kanal über die bestehende
  Bluetooth-Kopplung. Protokoll 3: Das `Hello` nennt dazu API-Stufe und versionCode der App.
  Protokoll 4: `AppQuery`/`AppStates` (ob und in welcher Version Apps auf der Uhr installiert sind,
  mit Signaturen) und `Uninstall`. Protokoll 5: `AppState.installer` (wer die App installiert hat)
  und hinten im `Hello` der SHA-256 der APK von Myzel Wear. Die Kernnachrichten (`Hello` vorne,
  `Auth`, `AuthResult`, `Offer`, `Result`, `Install`, `Bye`) bleiben seit Protokoll 3 auf der
  Leitung gleich; damit spricht ein neues Handy die Version einer alten Uhr und bringt ihr die neue
  Myzel Wear (`MIN_UPDATE_PROTOCOL`). Der HMAC-Nachweis der Anmeldung enthält die Version, die beide
  für die Verbindung sprechen (bis 31a8af40d war es die eigene; ein neues Handy kam deshalb bei einer
  alten Uhr nicht durch). Eigene
  SDP-Kennung, damit Prototyp und Myzel Wear nebeneinander auf einer Uhr lauschen können. Tests:
  `./gradlew :myzel-link:testDebugUnitTest`.
- **Myzel Wear** (`myzel/wear`): gleicher Paketname, gleicher Versionsname (`myzel/version.properties`)
  und gleicher Schlüssel wie Myzel, minSdk 30, Merkmal `android.hardware.type.watch`. Seit 0.11.0 hat
  Myzel den versionCode 1000 + n und Myzel Wear n (Myzel 1023 gehört zu Myzel Wear 23): Die Codes der
  Uhr liegen in der Paketquelle unter denen des Handys wie bei Catima, damit kein Client die Uhr-APK
  dem Handy als Update anbietet (bis 0.10.0 hatten beide 1 … 22). Lauscht, solange es
  offen ist, mit Vordergrunddienst und hört nach 10 Minuten ohne Handy auf. Nimmt den Schlüssel nur
  aus der ADB-Shell an (`KeyProvider`), installiert empfangene APKs über den PackageInstaller,
  entfernt Apps auf Wunsch des Handys (`REQUEST_DELETE_PACKAGES`, der Nutzer bestätigt) und nennt
  dem Handy die installierte Version (`QUERY_ALL_PACKAGES`). Bestätigungen fragt es aus dem
  Hintergrund per Benachrichtigung. Das Symbol ist eine Kopie der Dateien aus
  `app/src/myzel/res`. Auch der Debug-Build ist mit R8 verkleinert (sonst gut 25 MB).
- **Woher Myzel Wear kommt** (`watch.MyzelWearSource`, seit 0.11.0): Myzel Wear ist eine eigene APK,
  in der Paketquelle die Uhr-Version von Myzel (Fall A). Myzel nimmt die neueste aus den
  eingeschalteten Paketquellen, die wie es selbst signiert ist und zur API-Stufe der Uhr passt, lädt
  sie (der Downloader prüft den SHA-256 aus dem Index) und prüft Paketname und Signatur. Myzel selbst
  steht deshalb nicht im `WatchCatalog` (keine Karte „Uhr“, nicht in „Auf der Uhr“). Nur
  Debug-Builds legen ihre eigene Myzel Wear als Asset `myzel-wear.apk` bei (`app/myzel.gradle`), die
  dann Vorrang hat; mit `-Pmyzel.bundleWear=false` auch sie nicht, um den Weg über die Paketquelle zu
  testen. Bis 0.10.0 lag Myzel Wear jedem Build bei.
- **Seite „Uhr“** im Menü (`watch.ui.WatchScreen`): ohne Uhr der Assistent, sonst Stand,
  „Verbindung testen“, „Neu einrichten“ und „Uhr entfernen“. Die Einrichtung (`watch.setup.WatchSetup`)
  sucht Uhren mit „Debugging über WLAN“ per mDNS, koppelt mit dem Code, verbindet per ADB über WLAN
  (libadb-android über JitPack, Conscrypt, BouncyCastle), prüft das Merkmal
  `android.hardware.type.watch` (Handys mit Debugging über WLAN im selben Netz bekommen nichts; die
  Einrichtung blendet sie ab dann aus, gemerkt je Seriennummer in `watch.json`, auch über „Uhr
  entfernen“ hinweg; die Liste nennt die Zahl, und Einstellungen → Uhr → „Ausgeblendete Geräte“
  zeigt sie einzeln oder alle wieder; ein Gerät, das die Verbindung nach dem TLS-Handshake gleich
  schließt, ist nicht mit diesem Handy gekoppelt: „Das Gerät lässt dieses Handy nicht zu“ statt
  „antwortet nicht“, mit „Gerät ausblenden“, denn ob es eine Uhr ist, lässt sich so nicht prüfen),
  installiert Myzel Wear (aus der Paketquelle; fehlt es dort: „Myzel für die Uhr steht in keiner
  eingeschalteten Paketquelle“), gibt ihm die Rechte
  (`appops`, `pm grant`), übergibt den Schlüssel per `content call`, startet es, sucht die Uhr in der
  Bluetooth-Kopplung (Adresse per ADB, sonst durchprobieren), testet die Verbindung und schaltet
  Debugging über WLAN wieder ab.
- **`watch.WatchBridge`** (`LinkWatchBridge`): die Schnittstelle zur Uhr für den Rest von Myzel,
  `check(packageNames)`, `install(apk)` und `uninstall(packageName)`; ein RFCOMM-Kanal je Auftrag.
- **Myzel Wear aktualisieren:** Jeder Auftrag prüft bei der Anmeldung, ob es eine neuere Myzel Wear
  gibt: in der Paketquelle mit höherem versionCode als im `Hello` (Debug-Builds: eine Beilage mit
  anderem SHA-256; eine Uhr mit älterem Protokoll lehnt ab und nennt ihre Version, dann zählt der
  zuletzt gemeldete versionCode). Myzel lädt sie (Stand „wird geladen“) und schickt sie mit der
  Protokollversion der Uhr als `Offer`; Myzel Wear
  installiert sich selbst ohne Nachfrage (Selbst-Update mit `UPDATE_PACKAGES_WITHOUT_USER_ACTION`),
  sein Prozess endet, `UpdateReceiver` (`MY_PACKAGE_REPLACED`) startet das Lauschen neu, und Myzel
  meldet sich bei der neuen Version an (bis 60 s). Den Stand zeigen die Seite „Uhr“, die Karte „Uhr“
  und „Meine Apps“ (`WatchBridge.wearUpdate`). Nach einem Fehlschlag versucht Myzel es 15 Minuten
  lang nicht von selbst; auf der Seite „Uhr“ geht es per Tipp sofort. Neu einrichten muss man die
  Uhr dafür nicht mehr. Am 2026-10-07 auf der Pixel Watch 2 von Protokoll 4 auf 5 gelaufen: 17 s,
  ohne Rückfrage auf der Uhr. Myzel Wear zeigt unter dem Stand seine Version und die Kennung des
  Schlüssels, zum Vergleich mit der Seite „Uhr“ auf dem Handy.
- **Auf der Uhr installieren, aktualisieren, entfernen:** Die Karte „Uhr“ auf der App-Seite fragt
  Myzel Wear, ob und in welcher Version die App auf der Uhr ist. Fehlt sie: „Auf der Uhr
  installieren“. Ist eine ältere da: „Aktualisieren“ und „Entfernen“. Ist die neueste da (oder eine
  neuere), oder ist sie anders signiert: nur „Von der Uhr entfernen“, bei anders signierten mit
  der installierenden App (`getInstallSourceInfo` auf der Uhr), der Signatur und den Paketquellen,
  die eine Uhr-Version mit dieser Signatur anbieten (`WatchAppList.reposSignedLike`). Frei sind die Knöpfe erst, wenn
  Myzel Wear antwortet und den Stand genannt hat: Solange die Seite zu sehen ist, meldet sich Myzel
  alle paar Sekunden an, bis die Uhr antwortet, danach alle zwei Minuten (jede Anmeldung steht im
  Verlauf der Uhr); beim Öffnen der Seite sofort. Vor dem Download prüft es noch einmal. Der Knopf nimmt die neueste Uhr-Version, deren minSdk zur API-Stufe der Uhr passt. `watch.WatchInstaller` lädt sie wie F-Droid über den `DownloaderFactory`
  (prüft den SHA-256 aus dem Index), prüft Paketname und Signatur gegen den Index und schickt sie
  über die `WatchBridge`; Myzel Wear prüft den SHA-256 erneut, der Nutzer bestätigt auf der Uhr.
  Danach fragt Myzel den neuen Stand ab. Beim Entfernen fragt Wear OS immer nach. Den Stand eines
  laufenden Auftrags (Laden, Senden, Bestätigen) und Fehler hält der Installer je App, solange Myzel
  läuft; was installiert ist, kommt immer von der Uhr und steht in `watch.json`, damit es auch ohne
  Verbindung bekannt ist (bei einer anderen Uhr oder nach „Uhr entfernen“ verfällt es). Ist die Uhr
  beim Tipp nicht erreichbar (Myzel Wear zu, Bluetooth aus), wartet der Auftrag bis zu 5 Minuten
  auf sie („Wartet auf die Uhr …“) und fragt alle 3 s nach.
- **Nach einer Installation auf dem Handy fragen:** Installiert oder aktualisiert man auf der
  App-Seite eine App, die eine passende Uhr-Version hat und auf der Uhr fehlt oder älter ist,
  fragt Myzel „Auch auf der Uhr installieren?“ (`AskAfterPhoneInstall` in `AppDetailsPage`).
  „Nie fragen“ im Dialog und der Schalter „Nach Installationen fragen“ auf der Seite „Uhr“ stellen
  es ab (`askAfterInstall` in `watch.json`).
- **„Meine Apps“, Abschnitt „Auf der Uhr“** (`watch.ui.watchAppsSection`, eingehängt in
  `MyAppsList` mit `// Myzel`): die Apps aus den Paketquellen, die zuletzt auf der Uhr waren
  (`watch.WatchAppList`), mit Updates (Abzeichen, alte → neue Version) und „Alle auf der Uhr
  aktualisieren“. Myzel fragt die Uhr nur auf Wunsch („Abfragen“, nach allen Apps aus dem
  `WatchCatalog`), denn Myzel Wear antwortet nur, solange es offen ist; die Zeile darunter nennt
  den Stand.
- Die Uhr steht in `no_backup/myzel/watch.json`, der ADB-Schlüssel des Handys in `no_backup/myzel/adb`,
  der gemeinsame Schlüssel im Android-Keystore; nichts davon gelangt in eine Sicherung.
- In den Unit-Tests der App fehlt `conscrypt-android` (`app/myzel.gradle`): Robolectric lädt es
  sonst und findet die JNI-Bibliothek nicht.
- Größe: Das Release-APK wächst um gut 14 MB, vor allem durch die nativen Bibliotheken von Conscrypt
  (10 MB für vier Prozessorarten) und BouncyCastle, das die Keep-Regeln von F-Droid
  (`proguard-rules.pro`) zu gut 2 MB festhalten. Myzel Wear (1,9 MB) liegt seit 0.11.0 nicht mehr
  bei (Release 0.10.0: 34,1 MB, ohne Myzel Wear 33,2 MB).

### Geschwindigkeit

Änderungen am Code von F-Droid, jeweils mit `// Myzel` markiert:

- **Paketquellen gleichzeitig laden:** `RepoUpdateManager.updateRepos` lädt die Indexe aller
  Quellen schon vorab herunter (höchstens drei gleichzeitig, `RepoUpdater.prefetch` →
  `IndexV2Updater.prefetch`), eingelesen werden sie weiter nacheinander in der Reihenfolge oben. Die
  vorab geladene Datei liegt unter dem Pfad, unter dem das Update sie sucht (SHA-256 im
  Cache-Ordner); das Update prüft dann nur noch die Prüfsumme. Liegengebliebene Dateien löscht
  `deleteAbandonedPrefetches` nach 30 Minuten.
- **Suchindex nur einmal schreiben:** `DbV2StreamReceiver` gibt die Verträglichkeit einer App
  schon beim Einfügen mit (`AppDao.insert(…, isCompatible)`), und `updateCompatibility` ändert nur
  noch Zeilen, die abweichen. Vorher schrieb der zweite Durchgang jede App samt Suchindex (FTS)
  ein zweites Mal; der Index von f-droid.org liest sich so etwa 20 % schneller ein.
- **Neue Apps einmal abfragen:** `DiscoverViewModel` nimmt die Liste aus `NewApps`
  (`ui/apps/AppBadges.kt`), die auch die Pillen „Neu“ nutzen.
- **Darstellung vorab lesen:** `App.onCreate` ruft `ThemeSettingsStore.preload()` des
  Design-Pakets; `MyzelTheme` beginnt mit `cachedSettings` und muss nicht erst ein leeres Bild
  zeigen.
- **Karussell:** Die Zwischenebene zum Ausblenden der Ränder gibt es nur, solange ein Rand
  ausblendet.

### Baseline Profile

`app/src/myzel/baselineProfiles/baseline-prof.txt` nennt die Klassen und Methoden, die ART schon
nach der Installation übersetzt (Start, Entdecken, App-Seite). Bei Installationen außerhalb von
Google Play legt die Bibliothek ProfileInstaller das Profil beim ersten Start ab; übersetzt wird beim
nächsten Leerlauf des Geräts.

Neu erzeugen, wenn sich viel am Code geändert hat (Gerät ab Android 13, ohne Root):

```bash
ANDROID_SERIAL=<gerät> ./gradlew :myzel-baselineprofile:connectedBasicDefaultProfilingAndroidTest
cp myzel/baselineprofile/build/outputs/connected_android_test_additional_output/basicDefaultProfiling/connected/*/BaselineProfileGenerator_generate-baseline-prof.txt app/src/myzel/baselineProfiles/baseline-prof.txt
adb uninstall de.needanapp.myzel.profiling; adb uninstall de.needanapp.myzel.baselineprofile
```

Der Test (`myzel/baselineprofile`) startet die Variante `basicDefaultProfiling`: wie Release, aber
nicht verkleinert (R8 schreibt das Profil beim Release-Build auf die verkleinerten Namen um), mit
dem Debug-Schlüssel und unter der App-ID `de.needanapp.myzel.profiling`, so stört sie keine
installierte Myzel. Beim ersten Durchgang lädt sie die Paketquellen, das dauert ein paar Minuten.
Die beiden APKs bleiben danach installiert (`leaveApksInstalledAfterRun` von F-Droid), daher das
Deinstallieren.

## Bauen

```bash
git submodule update --init
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:assembleBasicDefaultDebug
```

Tests: `./gradlew :app:testBasicDefaultDebugUnitTest :myzel-link:testDebugUnitTest`. Formatierung wie
bei F-Droid mit ktfmt: `./gradlew :app:ktfmtFormatMain :app:ktfmtFormatTest :myzel-link:ktfmtFormat :myzel-wear:ktfmtFormat`.

Release: `./gradlew :app:assembleBasicDefaultRelease` signiert mit einem Release-Schlüssel von needanapp.de.
`app/myzel.gradle` (und `myzel/wear/build.gradle.kts`) lesen dazu `~/needanapp-signing/keystore.properties`
(anderer Ordner: `-Pneedanapp.signingDir=…` oder `needanapp.signingDir` in `~/.gradle/gradle.properties`);
fehlt die Datei, bleibt der Release-Build unsigniert. Seit 0.22.0 tragen Releases für apps.needanapp.de den neuen
Schlüssel aus `~/needanapp-signing/stabil` (SHA-256 `C6:0E:A3:B5:…:3A:63:51:D9`, wie needafile und needaradio dort):
`./gradlew -Pneedanapp.signingDir=$HOME/needanapp-signing/stabil :app:assembleBasicDefaultRelease
:myzel-wear:assembleRelease`. Testing-Versionen behalten den bisherigen (`4C:A4:F7:4F:…:D6:FE:D5:47:02`).

## Messen

Start von Entdecken (2026-10-06, Pixel 6, R8-Release, vorab übersetzt): Die Kategorien stehen nach
gut 0,5 s, Neu und Kürzlich aktualisiert kamen erst nach 0,9–1,1 s aus der Datenbank; bis dahin
war das Karussell leer, danach rutschten die Kategorien nach unten. Mit `discover.DiscoverSnapshot`
steht Entdecken ab dem ersten Bild vollständig da; die Pillen „Neu“ und „Update“ kommen etwa
0,3 s später dazu.

Suche (2026-10-25, OnePlus 7 Pro): „kar“ mit 423 Treffern brauchte 829 ms, davon rund 790 ms für
die Beschreibungen aller Treffer in allen Sprachen (12,8 Mio. Zeichen lesen und als JSON
auswerten), die die Trefferliste gar nicht zeigt. Ohne sie (`getAppSearchItems` in
`libs/database/…/AppDao.kt`, mit `// Myzel`) 109 ms; die Bewertung kommt weiter aus `matchinfo`,
gesucht wird weiter auch in den Beschreibungen. Das Suchfeld wartet nach dem letzten Tastendruck
150 statt 500 ms. Vor der Suche zeigt die Seite nur den Verlauf, keine Kategorien mehr.

Ruckler beim Umschalten, Nachtrag (2026-10-25, OnePlus 7 Pro, 90 Hz): Solange jeder Wechsel die
Seite neu aufbaute, hing ein Bild 66–93 ms (Meine Apps) bzw. 136 ms (Suche, nach dem Start 300 ms,
davon 93 ms Messen der Kategorie-Chips), und die Suche erschien erst leer. Mit `TopLevelPages`
kostet der Wechsel noch 17–24 ms in zwei Bildern, das Gleiten läuft ohne Aussetzer.

Ruckler beim Umschalten (2026-10-04, Pixel 6): Der erste Aufbau eines Bereichs kostet 30–45 ms
(R8-Release, vorab übersetzt), verteilt auf Komposition, Layout, Text und die Aufnahme für die
Unschärfe der Pille; ohne Vorabübersetzung etwa doppelt so viel. Eine Überblendung, die sofort
startet, springt danach sichtbar; deshalb wechseln die Bereiche ohne Überblendung
(`ui/nav/NavDisplay.kt`). Nach einem Update ist Myzel erst „verify“ und wird von Android erst im
Leerlauf am Ladegerät übersetzt; nachstellen mit
`adb shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE <app-id>/androidx.profileinstaller.ProfileInstallReceiver`
und `adb shell cmd package compile -m speed-profile -f <app-id>`.

- Bildzeiten: `adb shell dumpsys gfxinfo <app-id> reset`, wechseln, dann
  `adb shell dumpsys gfxinfo <app-id> framestats` (Spalten IntendedVsync … GpuCompleted).
- Abschnitte im Hauptthread: Die Variante `basicDefaultProfiling` ist profileable
  (`app/src/profiling/AndroidManifest.xml`); `adb shell atrace --async_start -a de.needanapp.myzel.profiling -b 32768 gfx view input`,
  wechseln, `atrace --async_dump … > trace.txt`, `atrace --async_stop`. Die Zeilen
  `tracing_mark_write: B|…` / `E|…` des Hauptthreads zeigen Abschnitte wie `Compose:recompose`,
  `AndroidOwner:measureAndLayout` und `HazeSource.record`.

## Veröffentlichen

Der Store-Eintrag liegt in `myzel/fastlane/metadata/android/<sprache>/`. Die Screenshots (1080 × 2400, seit
0.9.1 vom Pixel 6) entstehen mit der Variante `basicDefaultProfiling` (eigene App-ID, startet ohne Daten, stört
keine installierte Myzel) und `myzel/tools/store-screenshot.py`, das die Statusleiste mit dem Hintergrund
übermalt; den Demo-Modus der Statusleiste nicht nutzen, unter Android 17 bleibt danach die Uhr stehen. Die
Sprache der Variante stellt `adb shell cmd locale set-app-locales de.needanapp.myzel.profiling --locales en-US`
um (danach App neu starten; bei einer frisch installierten App erst nach dem ersten Start). Bild 8 (Darstellung)
zeigt den hellen Modus, die übrigen den dunklen. Bild 4 (Meine Apps) zeigt die Apps, die auf dem Gerät
installiert sind.

Bild 6 (Seite „Uhr“) kommt von der installierten Myzel, die mit der Uhr eingerichtet ist; die Testvariante hat
keinen Schlüssel der Uhr. Die Uhrbilder (`images/wearScreenshots/`, 384 × 384, rund mit durchsichtigen Ecken)
zeigen Myzel Wear auf der Pixel Watch 2 (adb über „Debugging über WLAN“). „Verbunden“ steht dort nur für
Sekundenbruchteile, während „Verbindung testen“ auf dem Handy läuft; aufgenommen wird es mit einer Schleife
`screencap /data/local/tmp/…/f$i.raw` direkt auf der Uhr (Rohformat: 16 Byte Kopf, dann RGBA), die danach
wieder gelöscht wird. Sprache von Myzel und Myzel Wear danach mit `--locales ''` zurückstellen.

Die Feature-Grafik zeichnet `python3 myzel/tools/make-store-graphics.py` aus Logo und den Screenshots
1-discover und 5-repositories, seit 0.18.0 in den Farben und Schriften von „Gerät“ (Graphit, Alu, Orange; Geist aus
dem Design-Paket).

Beim ersten Start fragt Android die Testvariante nach Benachrichtigungen. Den Systemdialog schließt die Zurück-Taste
(nicht erlaubt), er erscheint dann aber bei jedem Start wieder. Bild 1 zeigt Entdecken mit geöffneter Gruppe
„Kommunikation“, die Favoriten sind dieselben acht Apps wie bisher.

`myzel/tools/make-fdroid-upload.py` stellt daraus und aus der gebauten APK den Upload-Satz zusammen
(`repo/<appid>_<code>.apk`, `metadata/<appid>.yml`, `metadata/<appid>/<sprache>/`):

- **Debug-Builds** (`de.needanapp.myzel.debug`, „Myzel (Debug)“) gehen nach fdroid.pan1k.de:
  `python3 myzel/tools/make-fdroid-upload.py debug <ziel>` (mit `--nur-metadaten` ohne APK), dann
  `tar -C <ziel> -cf - repo metadata | ssh pan1k-server "cd /opt/fdroid/data && tar -xf - --no-same-owner --no-overwrite-dir && cd /opt/fdroid && ./fdroid update"`.
- **Releases** (`de.needanapp.myzel`) gehen zusammen mit Myzel Wear nach apps.needanapp.de (stabil) oder
  fdroid.needanapp.de (Testing, mit Anmeldung); wohin, wird bei jedem Release neu entschieden
  (vorher `:app:assembleBasicDefaultRelease` und `:myzel-wear:assembleRelease`):
  `python3 myzel/tools/make-fdroid-upload.py release ~/myzel-release/<version>/F-Droid --quelle stabil|test` prüft bei
  beiden Signatur und Version (bei Myzel Wear auch das Uhr-Merkmal), legt sie als
  `repo/de.needanapp.myzel_<code>.apk` und `…_<code - 1000>.apk` ab und schreibt
  `sftp-batch.txt` mit `cd /stabil/incoming` bzw. `cd /incoming` vorn; hochladen mit
  `sftp -b ~/myzel-release/<version>/F-Droid/sftp-batch.txt fdroid-upload`. Der Server übernimmt APK und
  Metadaten nach ein bis drei Minuten und leert den Eingang, deshalb immer den ganzen Satz schicken.
  Jede neue Version braucht einen höheren versionCode (`myzel/version.properties`, seit 0.11.0
  1000 + n).
- **Nur die letzten drei Versionen** von Myzel und Myzel Wear bleiben im Repo (Wunsch seit 0.13.1, seit
  25.10.2026 für alle Apps). Die Archiv-Regel von fdroidserver zählt nach versionCode und würde Handy und
  Uhr vermischen; deshalb verschiebt `fdroid-ingest` auf dem Server bei jeder Veröffentlichung selbst alle
  älteren APKs je App, getrennt nach Handy und Uhr, nach `/opt/stacks/fdroid/data/archiv/<appid>/` (nicht
  ausgeliefert, siehe `Produktivserver/INFRASTRUCTURE.md`). Von Hand ist nichts mehr zu tun; die von Hand
  verschobenen Versionen bis 0.16.0 liegen in `/root/fdroid-myzel-alt-20261009/`.

### F-Droid (vorbereitet)

Noch nicht eingereicht; der Entwurf für fdroiddata liegt in `myzel/fdroiddata/de.needanapp.myzel.yml`:
je Version ein Build für das Handy (`app`, Flavor `basicDefault`) und einer für Myzel Wear
(`myzel/wear`), beide unsigniert gebaut und von F-Droid mit demselben Schlüssel signiert, deshalb
findet Myzel aus F-Droid seine Uhr-Version dort. Die Repo-Website (`repo-website/make-repo-site.py`)
zeigt als neueste Version die fürs Handy und markiert Uhr-Versionen mit „Uhr“.

Die Quellen sind öffentlich (https://git.needanapp.de/needanapp.de/myzel, Branch `myzel`, das Submodul
needanapp-design ebenso). Jede Veröffentlichung bekommt einen annotierten Tag `myzel-<version>` auf den Commit,
der die Version setzt (die Tags `v…` und `2.0.1` sind die von F-Droid). Vor dem Einreichen noch mit
`fdroid build --server` oder im fdroiddata-CI prüfen: libadb-android kommt über JitPack, das F-Droid nur mit
Begründung annimmt; dazu die Testdateien unter `libs/index/src/commonTest/resources`.

## Update von F-Droid übernehmen

Myzel folgt den Release-Tags von F-Droid (Start: 2.0.1).

```bash
git fetch upstream --tags
git merge <neuer-tag>          # z. B. 2.0.2
```

- Konflikte in `gradle/verification-metadata.xml`: die Version von F-Droid nehmen und die Prüfsummen
  neu schreiben lassen:
  `./gradlew --write-verification-metadata sha256 :app:assembleBasicDefaultDebug :app:assembleBasicDefaultRelease :app:testBasicDefaultDebugUnitTest`
- Konflikte rund um ACRA (`App.kt`, Manifest, `app/build.gradle.kts`, `gradle/libs.versions.toml`)
  oder Änderungen von F-Droid in `org/fdroid/ui/crash/`: die Myzel-Seite behalten, also ACRA weiter
  weglassen und gelöschte Dateien mit `git rm` gelöscht lassen (siehe „Keine Absturzberichte“).
- Konflikte in Import-Zeilen: die Version von F-Droid nehmen und `python3 myzel/tools/apply-imports.py`
  laufen lassen (tauscht die Bausteine wieder gegen die von Myzel).
- Konflikte in `README.md`: die Fassung von Myzel behalten (das README von F-Droid ist ersetzt).
- Danach bauen, alle Tests laufen lassen und die App im Emulator durchklicken.
