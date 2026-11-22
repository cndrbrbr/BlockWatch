# 4. Lösungsstrategie

| Ziel | Ansatz | Details |
|---|---|---|
| Kein Lag | Handler → `ConcurrentLinkedQueue` → alle 2 s gebündelt in eine Transaktion (Async-Task) | [ADR-0002](adr/0002-eine-tabelle-gebuendelt-schreiben.md), [8.1](08-konzepte.md#81-threads) |
| Nur Tatsachen protokollieren | Protokoll-Handler auf `MONITOR` mit `ignoreCancelled` | [8.3](08-konzepte.md#83-event-prioritäten) |
| Richtige Verantwortliche | Akteur auflösen: Spieler, Schütze eines Projektils, Quelle von gezündetem TNT; sonst `#Ursache` | [8.4](08-konzepte.md#84-akteure) |
| Wenig Rauschen | Ausgabe nur als Statistik je Spieler und Blocktyp; Explosionen ohne Blockschaden ignorieren | [ADR-0003](adr/0003-ausgabe-nur-als-statistik.md) |
| Sicher statt reparieren | Kein Rollback; gefährliche Gegenstände vorher verhindern + Gefängnis | [ADR-0001](adr/0001-kein-rollback.md), [ADR-0004](adr/0004-verhindern-mit-gefaengnis.md) |
| RCON-tauglich | Konsolen-Abfragen synchron, Spieler-Abfragen asynchron | [ADR-0005](adr/0005-konsolenabfragen-synchron.md) |
