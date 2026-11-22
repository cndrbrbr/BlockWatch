# ADR-0005: Konsolen-/RCON-Abfragen synchron

- **Status:** angenommen
- **Datum:** 2026-10-21
- **Version:** 0.1.0

## Kontext

Admins fragen oft von außen per RCON ab (`/bw bei`, `/bw stats`), z. B. über Hilfsskripte im
Container. RCON gibt nur Ausgaben zurück, die *während* der Befehlsausführung kommen; eine Antwort
aus einem Async-Task kommt zu spät und geht verloren.

## Entscheidung

Abfragen von Konsole/RCON laufen direkt auf dem Server-Thread (vorher `flush()`), Abfragen von
Spielern im Async-Task mit Ausgabe zurück auf dem Server-Thread.

## Folgen

- RCON-Abfragen funktionieren ohne Umweg.
- Eine große Abfrage über RCON blockiert kurz den Server. Die Abfragen sind indiziert; bei sehr
  großen Datenbanken beobachten ([Risiken](../11-risiken.md)).
