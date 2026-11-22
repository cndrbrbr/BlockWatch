# 8. Querschnittliche Konzepte

## 8.1 Threads

| Was | Thread |
|---|---|
| Event-Handler, `record()` | Server-Thread (nur Queue) |
| `Recorder.flush()` | Async-Task alle 40 Ticks; beim Stoppen synchron |
| Spieler-Abfragen | Async-Task, Ausgabe zurück auf dem Server-Thread |
| Konsolen-/RCON-Abfragen | Server-Thread ([ADR-0005](adr/0005-konsolenabfragen-synchron.md)) |
| Aufräumen (`retention-days`) | Async-Task, erstmals 1 min nach Start, dann täglich |
| Gefängnis-Takt | Server-Thread, jede Sekunde |

`Database` hat eine einzige Verbindung; alle Methoden sind `synchronized`, damit Flush, Abfrage
und Aufräumen sich nicht überschneiden.

## 8.2 Speicherung

- SQLite, `journal_mode=WAL`, `synchronous=NORMAL`.
- Schema über `CREATE TABLE/INDEX IF NOT EXISTS`, keine Versionsnummer (siehe Schulden).
- Insert im Stapel in einer Transaktion; Fehler → Rollback und Warnung im Log, die Einträge
  dieses Stapels sind verloren.
- Aufbewahrung: Einträge älter als `retention-days` (180) werden gelöscht, 0 = nie.

## 8.3 Event-Prioritäten

| Zweck | Priorität | Warum |
|---|---|---|
| Protokoll (`WatchListener`) | `MONITOR`, `ignoreCancelled` | Nur was wirklich passiert ist; nach allen Schutz-Plugins |
| Schutz (`ProtectionListener`) | `LOW` | Früh abbrechen, bevor andere Plugins reagieren |
| Inspektion, Gefängnis-Befehle/Teleport | `LOWEST` | Klick bzw. Befehl soll gar nicht erst wirken |

Ein abgebrochenes TNT-Setzen erscheint deshalb nur als `BLOCKED`, nicht zusätzlich als `TNT_PLACE`.

## 8.4 Akteure

- `actor` ist der Spielername oder eine Ursache mit `#`: `#Creeper`, `#TNT`, `#TNT-Lore`,
  `#Endkristall`, `#Feuer`, `#Redstone`, `#<Entity-Typ>`, `#unbekannt`.
- Zuordnung: Spieler direkt → Schütze eines Projektils → Quelle von gezündetem TNT (rekursiv,
  Kettenreaktionen).
- `player` (UUID) wird mitgespeichert; Abfragen gehen aber über den Namen (ohne Groß/Klein).
- Feuer wird nur protokolliert, wenn ein Spieler es legt; Ausbreitung nur als `#Feuer`-Brand.
- Explosionen zählen nur bei `DESTROY`/`DESTROY_WITH_DECAY` (keine Windkugeln, seit 0.3.1).

## 8.5 Konfiguration

`config.yml` wird beim Start mit neuen Standardwerten ergänzt (`copyDefaults`). Kein `/bw reload`:
Änderungen wirken nach einem Neustart. `protection.enabled: false` registriert den
`ProtectionListener` gar nicht; das Gefängnis selbst (`/bw jail`) bleibt nutzbar.

## 8.6 Texte

Deutsch, fest im Code, Farbcodes mit `§`.
