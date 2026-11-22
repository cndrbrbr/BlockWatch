# 9. Architekturentscheidungen

ADRs in [`adr/`](adr/): eine Datei je Entscheidung, nummeriert, nie gelöscht. Ersetzte
Entscheidungen bekommen den Status „ersetzt durch ADR-xxxx“. Vorlage: [adr/0000-vorlage.md](adr/0000-vorlage.md).

| Nr. | Entscheidung | Status |
|---|---|---|
| [0001](adr/0001-kein-rollback.md) | Nur protokollieren, kein Rollback | angenommen |
| [0002](adr/0002-eine-tabelle-gebuendelt-schreiben.md) | Eine SQLite-Tabelle, gebündelt asynchron schreiben | angenommen |
| [0003](adr/0003-ausgabe-nur-als-statistik.md) | Abfragen zeigen nur Statistik, keine Einzeleinträge | angenommen |
| [0004](adr/0004-verhindern-mit-gefaengnis.md) | Gefährliches verhindern und mit Gefängnis bestrafen | angenommen |
| [0005](adr/0005-konsolenabfragen-synchron.md) | Konsolen-/RCON-Abfragen synchron | angenommen |
| [0006](adr/0006-gefaengnisse-markieren-sich-gegenseitig.md) | Gefängnisse markieren sich gegenseitig im Spieler-PDC | angenommen |
