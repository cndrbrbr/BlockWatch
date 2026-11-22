# 6. Laufzeitsicht

## 6.1 Ein Block wird abgebaut

```mermaid
sequenceDiagram
    participant S as Spigot (Server-Thread)
    participant W as WatchListener
    participant R as Recorder
    participant A as Async-Task (alle 40 Ticks)
    participant D as Database
    S->>W: BlockBreakEvent (MONITOR, nicht abgebrochen)
    W->>R: record(LogEntry)
    Note over W,R: nur Queue, kein I/O
    A->>R: flush()
    R->>D: insert(Stapel) in einer Transaktion
```

Beim Stoppen ruft `onDisable` `flush()` noch einmal synchron auf.

## 6.2 Abfrage `/bw hier 10 2h`

1. `BlockWatchCommand` liest Radius und Zeit (`TimeArgs`), Reihenfolge egal.
2. **Spieler:** Async-Task → `Recorder.flush()` (damit Frisches dabei ist) → `Database.materials(...)`
   → zurück auf den Server-Thread → erste Seite in den Chat; das Ergebnis bleibt für `/bw seite`.
3. **Konsole/RCON:** dasselbe, aber direkt im Befehl
   ([ADR-0005](adr/0005-konsolenabfragen-synchron.md)).

## 6.3 Spieler ohne Recht setzt TNT

```mermaid
sequenceDiagram
    participant Pl as Spieler
    participant P as ProtectionListener (LOW)
    participant R as Recorder
    participant J as JailManager
    Pl->>P: PlayerInteractEvent (Rechtsklick mit TNT)
    alt hat blockwatch.bypass
        P-->>Pl: nichts tun
    else
        P->>P: Event abbrechen
        P->>R: record(BLOCKED, TNT)
        P->>J: jail(spieler, 3 min, Grund)
        J->>J: Insasse + alter Ort + Spielmodus in jail.yml
        J->>Pl: Teleport in die Zelle, Abenteuermodus
        J-->>J: Online-OPs benachrichtigen
    end
    loop jede Sekunde
        J->>Pl: Countdown in der Actionbar, ggf. zurückholen
    end
    J->>Pl: Zeit um: zurück an alten Ort, alter Spielmodus
```

`BlockPlaceEvent`, Eimer und `TNTPrimeEvent` sind Rückfallebenen für Fälle, die der Rechtsklick
nicht erwischt. Werfer dürfen verbotene Gegenstände nicht ausgeben (ohne Strafe, kein Verantwortlicher).
Ist der Spieler offline, wenn die Zeit abläuft, wird er beim nächsten Login freigelassen.
