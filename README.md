# Blockdoku

Blockdoku-Spiel (9×9-Feld, Blöcke platzieren, Reihen/Spalten/3×3-Boxen abräumen) für Android.

- `game/blockdoku.html` – das Spiel (HTML/JS, Touch-Drag & Drop, Rekord wird lokal gespeichert)
- `android/` – Android-App (WebView), die das Spiel offline einbettet

## APK bauen

Jeder Push auf `game/` oder `android/` startet den Workflow **Android APK** (GitHub Actions).
Die fertige `app-debug.apk` liegt danach unter *Actions → Lauf → Artifacts → blockdoku-apk*.
Auf dem Handy installieren: APK öffnen und „Installation aus unbekannten Quellen“ erlauben.

Lokal (mit Android SDK): `gradle -p android assembleDebug`

# Notruf-App

`notruf/` – Android-App mit einem großen roten Knopf (2 Sekunden gedrückt halten):

1. Alle Notfallkontakte bekommen **gleichzeitig eine SMS** mit Text und Google-Maps-Link zum Standort
   (bis zu 10 s Warten auf ein GPS-Signal, sonst letzter bekannter Standort bzw. „unbekannt“).
2. Danach werden die Kontakte **nacheinander angerufen**. Nach jedem Anruf startet ein 5-Sekunden-Countdown
   zur nächsten Nummer – wurde jemand erreicht, **STOPP** drücken.

Nummern, eigener Name und SMS-Text werden über ⚙ (oben rechts) eingestellt; Kontakte lassen sich aus dem Adressbuch übernehmen.
Beim ersten Start fragt die App nach den Berechtigungen für Telefon, SMS und Standort – bitte alle erlauben.

Die APK baut der Workflow **Notruf APK** (Artifact `notruf-apk`). Lokal: `gradle -p notruf assembleDebug`.
Hinweis: Ersetzt nicht den Notruf 112.
