# 7. Verteilungssicht

## 7.1 Allgemein

Eine Jar `blockwatch-<version>.jar` in `plugins/`, Server neu starten. Es entstehen:

| Datei | Inhalt |
|---|---|
| `plugins/BlockWatch/config.yml` | Einstellungen; neue Schlüssel aus Updates werden beim Start ergänzt; die Zellenposition wird hier eingetragen |
| `plugins/BlockWatch/blockwatch.db` | Protokoll (SQLite, WAL) |
| `plugins/BlockWatch/jail.yml` | Aktuelle Insassen |

## 7.2 Im Einsatz (Stand 2026-11)

| Server | Zweck | `protection.enabled` | Installiert über |
|---|---|---|---|
| codefield | JavaScript-Workshop | ja | von Hand |
| nc3 mc2 | Bauwettbewerb (Survival) | **nein** – Lava/Feuerzeug gehören dort zum Spiel | Plugin-Katalog |
| nc3 mc4 | SoftSurvival | ja | Plugin-Katalog |
| nc3 mc5 | Multiverse-Welt | ja | Plugin-Katalog |
| meckminecraft mc5 | – | ja (Standard) | Plugin-Katalog |

Der Plugin-Katalog von *minecraftHostingServer* (Eintrag `blockwatch`) lädt die Jar aus dem
GitHub-Release. Neue Version = neues GitHub-Release + Katalogeintrag aktualisieren.

Auf nc3 mc3–mc5 liegt zusätzlich noch CoreProtect, das auf 26.3 aber nicht startet.

## 7.3 Bau und Test

| Schritt | Wie |
|---|---|
| Bauen + Tests | `docker run … maven:3.9-eclipse-temurin-25 mvn -q -B package` (siehe README) |
| Unit-Tests | `DatabaseTest` (Gruppierung, Filter, Zusammenfassung, Aufräumen), `TimeArgsTest` |
| Server-Test | Testserver, Ereignisse per RCON auslösen (z. B. TNT), dann `/bw bei …` |
