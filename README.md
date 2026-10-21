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
| verbotener Versuch | gesperrter Gegenstand benutzt, siehe unten | ✔ |

Einträge ohne verantwortlichen Spieler stehen mit `#` davor (`#Creeper`, `#TNT`, `#Feuer` …).

## Gefährliches verhindern – Gefängnis

Wer TNT, TNT-Loren, Endkristalle, Lava, Feuerzeug, Feuerkugeln oder Seelenanker setzen bzw.
benutzen will (Liste: `protection.blocked` in config.yml), wird gestoppt: die Aktion wird
abgebrochen, als **verbotener Versuch** protokolliert und der Spieler sitzt **3 Minuten**
(`protection.jail-minutes`) im Gefängnis – Abenteuermodus, keine Befehle, keine Teleports,
Countdown in der Aktionsleiste. Logout, Tod oder Neustart helfen nicht; die Zeit läuft auch offline.
Danach geht es zurück an den alten Ort mit dem alten Spielmodus. Online-OPs werden benachrichtigt.

Spieler mit `blockwatch.bypass` (Standard: OP) sind ausgenommen. Werfer dürfen diese Gegenstände
nicht ausgeben. Was Plugins/Scripts direkt per API setzen, kann BlockWatch nicht verhindern.

Die Zelle: ohne Einstellung baut BlockWatch beim ersten Bedarf eine Grundgesteinszelle hoch über dem
Spawn der Hauptwelt. Mit `/bw setjail` an die eigene Position verschieben (dort selbst für eine
ausbruchsichere Zelle sorgen).

## Befehle (Recht `blockwatch.use`, Standard: OP)

| Befehl | |
|---|---|
| `/bw hier [radius] [zeit]` | alles im Umkreis um die eigene Position: erst eine Statistik **Spieler · Blocktyp · Anzahl** (je Aktion), dann die Einträge (neueste zuerst) |
| `/bw gefahr [radius\|alle] [zeit]` | nur gefährliche Aktionen – um dich herum oder mit `alle` auf dem ganzen Server |
| `/bw inspect` | Inspektionsmodus an/aus: **Linksklick** = Verlauf dieses Blocks, **Rechtsklick** = Verlauf des Platzes davor (z. B. wo ein Block abgebaut wurde) |
| `/bw spieler <name> [gefahr] [zeit]` | was ein Spieler wo gemacht hat |
| `/bw stats [zeit]` | Rangliste: abgebaut / gesetzt / gefährlich je Spieler |
| `/bw bei <welt> <x> <y> <z> [radius] [zeit] [gefahr]` | wie `hier`, für die Konsole/RCON |
| `/bw seite <n>` | in der letzten Abfrage blättern |
| `/bw jail <spieler> [minuten]` | Spieler von Hand einsperren |
| `/bw frei <spieler>` | vorzeitig freilassen (offline: beim nächsten Login) |
| `/bw setjail` | Gefängniszelle an die eigene Position setzen |

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
