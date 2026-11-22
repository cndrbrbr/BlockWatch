# 1. Einführung und Ziele

## 1.1 Aufgabe

BlockWatch protokolliert auf Spigot-Servern, **wer wann wo was** an Blöcken verändert hat, und
besonders, wer mit **TNT, Feuer, Lava** und ähnlich Gefährlichem hantiert hat. Admins fragen das
ortsbezogen ab (`/bw hier`, Inspektionsmodus) oder je Spieler (`/bw spieler`, `/bw stats`).
Optional verhindert BlockWatch gefährliche Gegenstände und sperrt Verursacher kurz ins Gefängnis.

Entstanden im Oktober 2026 als eigener Ersatz für CoreProtect, das auf Spigot 26.3 nicht mehr
startet und dessen Rollback als zu riskant galt.

**Ausdrücklich kein Ziel:** Wiederherstellen (Rollback/Restore), Truhen- oder Chat-Protokoll,
Einzeleintrags-Forensik ([ADR-0001](adr/0001-kein-rollback.md),
[ADR-0003](adr/0003-ausgabe-nur-als-statistik.md)).

## 1.2 Qualitätsziele

| Prio | Ziel | Woran man es merkt |
|---|---|---|
| 1 | **Kein Lag** | Event-Handler legen nur einen Eintrag in eine Queue; Schreiben und Spieler-Abfragen laufen asynchron |
| 2 | **Richtige Verantwortliche** | Explosionen und gezündetes TNT werden dem Spieler zugeordnet, auch über Pfeile; sonst klarer `#`-Akteur |
| 3 | **Wenig Rauschen** | Ausgabe als Statistik; keine natürliche Feuerausbreitung, keine Windkugeln |
| 4 | **Einfacher Betrieb** | Eine Jar, eine SQLite-Datei, Abfragen auch über RCON |
| 5 | **Schutz ohne Admin vor Ort** | Verbotenes wird verhindert und bestraft, auch offline/nach Neustart |

## 1.3 Beteiligte

| Rolle | Erwartung |
|---|---|
| Admins/OPs | Schnell sehen, wer etwas kaputt gemacht hat – im Spiel und per Konsole/RCON |
| Spieler (oft Kinder) | Merken nichts, solange sie nichts Verbotenes tun; klare Meldung im Gefängnis |
| Entwickler | Kleines, verständliches Plugin |

## 1.4 Randbedingungen

- Spigot 26.3 (`api-version: '26.3'`), Java 25, Maven; Bau im Docker-Image
  `maven:3.9-eclipse-temurin-25`.
- Keine fremden Bibliotheken: SQLite-Treiber aus Spigot.
- Texte deutsch.
