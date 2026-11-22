# 3. Kontextabgrenzung

```mermaid
flowchart LR
    Spieler([Spieler]) -- "Abbauen, Setzen, TNT,<br/>Feuer, Lava" --> Spigot
    Spigot[(Spigot 26.3<br/>Events, Scheduler)] -- "Block-/Entity-/Player-Events" --> BW
    BW[[BlockWatch]]
    BW -- "abbrechen, Gefängnis,<br/>Teleport, Spielmodus" --> Spigot
    Admin([Admin im Spiel]) -- "/bw …, Inspektionsklick" --> BW
    Konsole([Konsole / RCON]) -- "/bw bei, stats, spieler" --> BW
    BW -- "Statistik im Chat" --> Admin
    BW -- "Statistik als Text" --> Konsole
    BW -- "JDBC" --> DB[(blockwatch.db)]
    BW -- "YAML" --> Jail[(jail.yml, config.yml)]
```

| Partner | Richtung | Inhalt |
|---|---|---|
| Spigot-Events | ein | Abbau, Setzen, Zünden, TNT-Zündung, Eimer, Entity-Setzen, Explosionen, Brand, Interact, Join/Respawn/Teleport/Befehl |
| Spigot | aus | Events abbrechen, Teleport in die Zelle, Spielmodus, Zelle aus Grundgestein bauen |
| Admin im Spiel | beide | `/bw …`, Inspektionsmodus; Gefängnis-Hinweise an Online-OPs |
| Konsole / RCON | beide | Abfragen ohne Position (`/bw bei <welt> x y z`), synchron beantwortet |
| SQLite | aus/ein | Tabelle `events` |
| YAML | aus/ein | `jail.yml` (Insassen), `config.yml` (u. a. Zellenposition) |

Nicht im Umfang: Webseite, Discord, andere Plugins. Was andere Plugins/Skripte direkt per API
setzen (z. B. `Block.setType`), löst keine Spieler-Events aus und ist für BlockWatch unsichtbar
([Risiken](11-risiken.md)).
