# 11. Risiken und technische Schulden

## 11.1 Risiken

| Risiko | Folge | Gegenmaßnahme / Stand |
|---|---|---|
| Plugins/Skripte setzen TNT o. Ä. direkt per API (`Block.setType`) | Weder protokolliert noch verhindert (gesehen: JS-Workshop-Skripte im Okt. 2026) | Bekannte Lücke; muss im jeweiligen Plugin gelöst werden |
| Große Datenbank auf vollen Servern | Langsamere Abfragen, synchrone Konsolenabfrage kann laggen | Indizes, `retention-days`; bei Bedarf Abfragen begrenzen |
| Absturz zwischen zwei Flushes | Bis zu 2 s Einträge verloren | Akzeptiert |
| Fehler beim Insert | Ganzer Stapel verloren (nur Warnung im Log) | Akzeptiert; Log beobachten |
| Correctness auf demselben Server, Spieler sitzt schon in dessen Gefängnis | Bis 0.3.1: gegenseitig blockierte Teleports, Spieler endete in einer Zelle im Abenteuermodus | **Behoben in 0.3.2 / Correctness 0.1.1** ([ADR-0006](adr/0006-gefaengnisse-markieren-sich-gegenseitig.md)); wirkt nur mit aktuellen Versionen beider Plugins |
| Spigot-Update ändert Events (z. B. neue `ExplosionResult`-Werte) | Fehl- oder Falschprotokolle (Windkugeln vor 0.3.1) | Nach Updates gefährliche Aktionen auf Testserver prüfen |
| Zelle über dem Spawn wird umgebaut oder liegt in ungeeigneter Welt | Insassen könnten entkommen | Zelle mit `/bw setjail` selbst festlegen; Rückholung bei > 4 Blöcken Abstand |

## 11.2 Technische Schulden

| Schuld | Wo | Aufwand |
|---|---|---|
| Keine Schema-Versionierung/Migrationen | `Database` | klein, sobald sich das Schema ändert |
| `Database`-Kommentar sagt „nie vom Server-Thread“, Konsolenabfragen tun es doch | `Database`, `BlockWatchCommand` | klein (Kommentar anpassen) |
| Abfrage-Logik und Ausgabe in einer großen Klasse (~450 Zeilen) | `BlockWatchCommand` | mittel |
| Wenig Tests: nur `Database` und `TimeArgs`; Akteur-Zuordnung und Schutz ungetestet | `WatchListener`, `ProtectionListener` | mittel (Zuordnung Bukkit-frei herauslösen) |
| Kein `/bw reload` | `BlockWatchPlugin` | klein |
| Gefängnis-Code ist in Correctness kopiert; Änderungen (wie die Markierung in 0.3.2) müssen in beiden Repos gemacht werden | `JailManager` | mittel (gemeinsame Bibliothek), nur wenn beide weiterwachsen |
