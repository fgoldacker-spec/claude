# Blockdoku

Blockdoku-Spiel (9×9-Feld, Blöcke platzieren, Reihen/Spalten/3×3-Boxen abräumen) für Android.

- `game/blockdoku.html` – das Spiel (HTML/JS, Touch-Drag & Drop, Rekord wird lokal gespeichert)
- `android/` – Android-App (WebView), die das Spiel offline einbettet

## APK bauen

Jeder Push auf `game/` oder `android/` startet den Workflow **Android APK** (GitHub Actions).
Die fertige `app-debug.apk` liegt danach unter *Actions → Lauf → Artifacts → blockdoku-apk*.
Auf dem Handy installieren: APK öffnen und „Installation aus unbekannten Quellen“ erlauben.

Lokal (mit Android SDK): `gradle -p android assembleDebug`
