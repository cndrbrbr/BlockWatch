# ADR-0004: Gefährliches verhindern und mit Gefängnis bestrafen

- **Status:** angenommen
- **Datum:** 2026-10-21
- **Version:** 0.2.0

## Kontext

Ohne Rollback ([ADR-0001](0001-kein-rollback.md)) ist Schaden durch TNT, Lava oder Feuer teuer.
Auf den Kinderservern ist oft kein Admin online. Nur abbrechen ohne Folge lädt zum Ausprobieren ein.

## Entscheidung

- Gegenstände aus `protection.blocked` (Standard: TNT, TNT-Lore, Endkristall, Lavaeimer,
  Feuerzeug, Feuerkugel, Seelenanker) werden für Spieler ohne `blockwatch.bypass` abgefangen,
  als `BLOCKED` protokolliert, und der Spieler kommt für `protection.jail-minutes` (3) ins Gefängnis.
- Gefängnis: Zelle, Abenteuermodus, keine Befehle, keine fremden Teleports; Zustand in `jail.yml`,
  Zeit läuft auch offline. Danach zurück an den alten Ort mit altem Spielmodus.
- Ohne eingestellte Zelle baut BlockWatch einmalig eine Grundgesteinszelle hoch über dem Spawn.
- Je Server abschaltbar (`protection.enabled`), z. B. für Survival-Wettbewerbe.

## Alternativen

- Nur abbrechen: keine abschreckende Wirkung.
- Kick/Bann: zu hart für Kinder, Admin muss wieder entsperren.

## Folgen

- Schaden wird verhindert statt protokolliert; OPs sehen im Chat, wer es versucht hat.
- Abhängig von Spieler-Events: API-gesetztes TNT bleibt eine Lücke ([Risiken](../11-risiken.md)).
- Das Gefängnis wurde später für das Plugin Correctness übernommen.
