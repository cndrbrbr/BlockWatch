# BlockWatch – Architekturdokumentation

Gegliedert nach [arc42](https://arc42.org/), schlank gehalten: BlockWatch ist ein kleines Plugin
(~1 600 Zeilen). Die Nummern folgen arc42 – gleiche Gliederung wie bei
[Inigresso](https://github.com/cndrbrbr/inigresso/tree/main/architektur); ausgelassene Nummern sind Absicht.

| Nr. | Abschnitt | Frage |
|---|---|---|
| 1 | [Einführung und Ziele](01-einfuehrung-und-ziele.md) | Wozu, und was ist ausdrücklich *nicht* das Ziel? |
| 3 | [Kontextabgrenzung](03-kontext.md) | Mit wem redet das Plugin? |
| 4 | [Lösungsstrategie](04-loesungsstrategie.md) | Die Grundentscheidungen in einer Tabelle |
| 5 | [Bausteinsicht](05-bausteinsicht.md) | Pakete und Klassen |
| 6 | [Laufzeitsicht](06-laufzeitsicht.md) | Protokollieren, Abfragen, Verbotenes verhindern |
| 7 | [Verteilungssicht](07-verteilungssicht.md) | Auf welchen Servern, wie installiert? |
| 8 | [Querschnittliche Konzepte](08-konzepte.md) | Threads, Speicherung, Akteure, Konfiguration |
| 9 | [Architekturentscheidungen](09-entscheidungen.md) | ADRs in [`adr/`](adr/) |
| 11 | [Risiken und technische Schulden](11-risiken.md) | Lücken und offene Punkte |
| 12 | [Glossar](12-glossar.md) | Begriffe ↔ Code |

Ausgelassen: **2 Randbedingungen** (kurz in 1.4), **10 Qualitätsanforderungen** (in 1.2).

## Andere Dokumente

| Dokument | Für wen | Inhalt |
|---|---|---|
| [README.md](../README.md) | Admins | Was wird protokolliert, Befehle, Schutz/Gefängnis, Installation, Bauen |
| `architektur/` (hier) | Entwickler | Wie ist es gebaut und warum? |

## Pflege

- Neue Befehle, Aktionen, Einstellungen → README. Bauänderung → hier. Grundsatzentscheidung → neues ADR.
- Diagramme als Mermaid im Markdown.
- Lieber einen Abschnitt löschen als halbleer stehen lassen.
