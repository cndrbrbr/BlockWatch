# ADR-0003: Abfragen zeigen nur Statistik, keine Einzeleinträge

- **Status:** angenommen
- **Datum:** 2026-10-21
- **Version:** 0.3.0

## Kontext

In 0.1/0.2 zeigten Abfragen Einzeleinträge. Bei einem Krater oder einer abgebauten Wand sind das
hunderte Zeilen im Chat; die eigentliche Frage („wer, womit, wie viel?“) geht unter. Gewünscht:
keine Einzeleinträge.

## Entscheidung

Alle Abfragen (`hier`, `gefahr`, `spieler`, `bei`, Inspektion) liefern eine Zeile je Akteur und
Blocktyp: Anzahl je Aktion und Zeitpunkt der letzten Aktion. Sortiert nach Akteur (meiste Aktionen
zuerst), dann nach Anzahl. `/bw stats` zeigt eine Rangliste je Akteur.

## Folgen

- Kurze, verständliche Ausgabe, auch über RCON.
- Genaue Zeitpunkte einzelner Aktionen sind nur noch per `sqlite3` direkt in der Datei abfragbar.
