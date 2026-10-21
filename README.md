# BlockWatch

Überwacht Blockänderungen und Spieleraktionen auf Spigot-Servern (26.3) – reine
Protokollierung und Statistik, **keine** Wiederherstellung.

Für jede Stelle der Welt lässt sich abfragen: **wer hat wann was an- oder abgebaut**,
und wer hat mit **TNT, Feuer, Lava** oder anderen gefährlichen Dingen hantiert.

## Was protokolliert wird

| Aktion | Wann | Gefährlich |
|---|---|---|
| abgebaut | Spieler baut einen Block ab | |
| gesetzt | Spieler setzt einen Block | |
| TNT gesetzt | Spieler setzt TNT | ✔ |
| TNT gezündet | TNT wird gezündet – Spieler (auch per Pfeil) oder Ursache wie Redstone/Feuer | ✔ |
| TNT-Lore gesetzt | TNT-Lore auf Schienen gesetzt | ✔ |
| Endkristall gesetzt | Endkristall gesetzt | ✔ |
| Feuer gelegt | Spieler zündet etwas an (Feuerzeug, Feuerkugel) | ✔ |
| Lava ausgegossen | Spieler leert einen Lavaeimer | ✔ |
| gesprengt | jeder durch eine Explosion zerstörte Block – mit dem Spieler, der das TNT gezündet hat, sonst z. B. `#Creeper` | ✔ |
| verbrannt | Block durch Feuer zerstört (`#Feuer`) | ✔ |

Einträge ohne verantwortlichen Spieler stehen mit `#` davor (`#Creeper`, `#TNT`, `#Feuer` …).

## Befehle (Recht `blockwatch.use`, Standard: OP)

| Befehl | |
|---|---|
| `/bw hier [radius] [zeit]` | alles im Umkreis um die eigene Position: erst eine Zusammenfassung je Spieler, dann die Einträge (neueste zuerst) |
| `/bw gefahr [radius\|alle] [zeit]` | nur gefährliche Aktionen – um dich herum oder mit `alle` auf dem ganzen Server |
| `/bw inspect` | Inspektionsmodus an/aus: **Linksklick** = Verlauf dieses Blocks, **Rechtsklick** = Verlauf des Platzes davor (z. B. wo ein Block abgebaut wurde) |
| `/bw spieler <name> [gefahr] [zeit]` | was ein Spieler wo gemacht hat |
| `/bw stats [zeit]` | Rangliste: abgebaut / gesetzt / gefährlich je Spieler |
| `/bw bei <welt> <x> <y> <z> [radius] [zeit] [gefahr]` | wie `hier`, für die Konsole/RCON |
| `/bw seite <n>` | in der letzten Abfrage blättern |

Zeitangaben: `30m`, `2h`, `7d`, `2w` (ohne Angabe: alles). Der Radius gilt in alle Richtungen
(Würfel), Standard 5. Im Spiel kann man auf einen Eintrag klicken, um hinzuteleportieren.

## Installation

`blockwatch-<version>.jar` in den `plugins`-Ordner, Server neu starten. Die Daten liegen in
`plugins/BlockWatch/blockwatch.db` (SQLite; der Treiber ist in Spigot enthalten). Einstellungen
in `plugins/BlockWatch/config.yml` (Aufbewahrung, Zeitzone, Radius, Seitengröße).

## Bauen

```bash
docker run --rm -v $PWD:/src -v ~/.m2:/root/.m2 -w /src maven:3.9-eclipse-temurin-25 mvn -q -B package
```
