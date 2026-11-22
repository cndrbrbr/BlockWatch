# ADR-0001: Nur protokollieren, kein Rollback

- **Status:** angenommen
- **Datum:** 2026-10-21
- **Version:** 0.1.0

## Kontext

Bisher lief CoreProtect. Es startet auf Spigot 26.3 nicht mehr, und sein Rollback wurde als zu
riskant empfunden: ein falscher Radius oder Zeitraum setzt fremde Bauten zurück, und das Ergebnis
ist schwer zu prüfen. Gebraucht wird vor allem die Antwort auf „wer war das?“.

## Entscheidung

BlockWatch protokolliert und zeigt an, stellt aber nichts wieder her. Es speichert deshalb auch
keine Blockzustände (BlockData, Inhalte), nur Material und Aktion.

## Alternativen

- CoreProtect weiter nutzen oder auf eine 26.3-Version warten: lief nicht.
- Eigenes Rollback: hoher Aufwand (Blockzustände, Container, Entities), gleiches Risiko.

## Folgen

- Kleines Schema, kleiner Code, keine gefährliche Admin-Funktion.
- Reparaturen von Hand oder aus Welt-Backups.
- Schaden lieber vorher verhindern ([ADR-0004](0004-verhindern-mit-gefaengnis.md)).
