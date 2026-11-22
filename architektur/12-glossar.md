# 12. Glossar

| Begriff | Bedeutung | Im Code |
|---|---|---|
| Eintrag | Eine protokollierte Aktion an einem Block | `LogEntry`, Tabelle `events` |
| Aktion | Art des Eintrags: abgebaut, gesetzt, TNT gesetzt/gezündet, Feuer, Lava, gesprengt, verbrannt, verbotener Versuch … | `Action` |
| Gefährlich | Aktionen mit TNT, Feuer, Lava, Explosion, Endkristall, sowie verbotene Versuche | `Action.isDangerous()`, Spalte `danger` |
| Akteur | Wer es war: Spielername oder `#Ursache` | Spalte `actor` |
| `#`-Akteur | Kein Spieler verantwortlich, z. B. `#Creeper`, `#Feuer` | – |
| Verbotener Versuch | Abgebrochene Aktion mit einem Gegenstand aus `protection.blocked` | `Action.BLOCKED` |
| Gefängnis / Zelle | Grundgesteinszelle, in der Insassen im Abenteuermodus warten | `JailManager`, `jail.yml` |
| Insasse | Eingesperrter Spieler mit Endzeit, altem Ort und Spielmodus | `JailManager.Inmate` |
| Inspektionsmodus | Klick auf Block zeigt dessen Statistik statt ihn abzubauen | `InspectListener` |
| Statistik | Eine Zeile je Akteur und Blocktyp: Anzahl je Aktion, zuletzt | `Database.MaterialSummary` |
| Bypass | Recht `blockwatch.bypass`: vom Schutz ausgenommen | – |
