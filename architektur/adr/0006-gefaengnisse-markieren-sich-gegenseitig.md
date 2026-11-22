# ADR-0006: Gefängnisse markieren sich gegenseitig im Spieler-PDC

- **Status:** angenommen
- **Datum:** 2026-11-22
- **Version:** BlockWatch 0.3.2 (Correctness 0.1.1)

## Kontext

BlockWatch und Correctness haben jeweils ein eigenes, fast gleiches Gefängnis (Correctness hat es
kopiert). Sitzt ein Spieler im einen und wird vom anderen auch eingesperrt, blockiert jedes Plugin die
Teleports des anderen als „fremd“. Das zweite merkt sich die erste Zelle als „alten Ort“ und
*Abenteuer* als Spielmodus – am Ende wird der Spieler in eine Grundgesteinszelle im Abenteuermodus
„freigelassen“ und kommt ohne Admin nicht heraus. Keines der Plugins soll vom anderen abhängen.

## Entscheidung

- Jedes Plugin schreibt beim Einsperren die Endzeit in den `PersistentDataContainer` des Spielers,
  Schlüssel `<plugin>:jailed_until` (`blockwatch:jailed_until`, `correctness:jailed_until`), und
  entfernt sie beim Freilassen (bzw. beim Login, wenn veraltet). Der Sekundentakt setzt sie auch für
  Insassen aus älteren Versionen.
- Vor dem Einsperren prüft es die Schlüssel der anderen Gefängnisse (`OTHER_JAILS`). Liegt dort eine
  Endzeit in der Zukunft, wird **nicht** eingesperrt; `jail()` liefert `false`.
- Die Aktion wird trotzdem verhindert und protokolliert bzw. der Verstoß gezählt; Spieler und Admins
  bekommen eine Meldung.

## Alternativen

- Gemeinsame Bibliothek mit einem Gefängnis: sauberste Lösung, aber Bauaufwand für zwei kleine Plugins.
- API-Abhängigkeit (softdepend + Aufruf): koppelt die Plugins im Code.
- Strafe anhängen (nach der ersten Haft weiter sitzen): mehr Zustand, kaum Nutzen.

## Folgen

- Kein Doppel-Einsperren, keine Abhängigkeit; Markierung übersteht Neustarts (liegt in den Spielerdaten).
- Wirkt nur, wenn **beide** Plugins diese Version haben.
- Ein weiteres Plugin mit Gefängnis müsste in `OTHER_JAILS` eingetragen werden.
- Bleibt eine Markierung stehen (Plugin entfernt, während jemand sitzt), sperrt das andere Plugin
  diesen Spieler höchstens bis zur gespeicherten Endzeit nicht ein.
