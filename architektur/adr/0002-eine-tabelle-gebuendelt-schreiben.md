# ADR-0002: Eine SQLite-Tabelle, gebündelt asynchron schreiben

- **Status:** angenommen
- **Datum:** 2026-10-21
- **Version:** 0.1.0

## Kontext

Abbauen und Setzen passieren sehr oft (Explosionen: hunderte Blöcke auf einmal). Datenbankzugriffe
auf dem Server-Thread würden laggen. Admins sollen nichts zusätzlich installieren.

## Entscheidung

- SQLite über den Treiber aus Spigot, eine Datei `blockwatch.db`.
- Eine Tabelle `events`, Strings statt Nachschlagetabellen (Welt, Akteur, Material).
- Handler legen Einträge in eine `ConcurrentLinkedQueue`; ein Async-Task schreibt alle 40 Ticks
  den Stapel in einer Transaktion.

## Alternativen

- Nachschlagetabellen wie CoreProtect (`co_material_map`, `co_user`): kleinere Datei, aber mehr
  Joins und Code.
- MySQL: zusätzlicher Dienst.
- Sofort schreiben: Lag.

## Folgen

- Einfache Abfragen mit `GROUP BY`, gut lesbar auch mit `sqlite3` von Hand.
- Datei wächst schneller als normalisiert; begrenzt über `retention-days`.
- Bis zu 2 s Einträge können bei einem Absturz fehlen.
