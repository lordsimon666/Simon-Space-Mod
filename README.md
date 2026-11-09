# Simon Space Mod

Forge-Mod für **Minecraft 26.2** (Forge 65.1.x, Java 25), die die **USS Enterprise NCC-1701**
(Originalserie) mit einem Befehl in die Welt setzt.

## Befehl

```
/enterprise spawn [full|compact|mini|scale <faktor>] [x y z] [north|east|south|west]
```

| Variante              | Hülle         | Größe (L x B x H)    | Brücke / Maschinenraum / Transporterraum |
|-----------------------|---------------|----------------------|------------------------------------------|
| `full`                | 1:1           | 292 x 133 x 76       | 1:1                                      |
| `compact` (Standard)  | 1:2           | 148 x 69 x 48        | **1:1**                                  |
| `mini`                | 1:2,5         | 119 x 57 x 45        | **1:1**                                  |
| `scale <0.4 – 1.0>`   | frei wählbar  | –                    | **1:1**                                  |

* Ohne Position entsteht das Schiff vor dir, der Bug zeigt zu dir.
* Die Position ist die Mitte des Schiffs am tiefsten Punkt, die Richtung gibt an, wohin der Bug zeigt.
* Benötigt OP-Rechte (Level 2).
* Gebaut wird über mehrere Ticks verteilt (max. 15 ms pro Tick), damit der Server nicht einfriert.
  Wenn das Schiff fertig ist, erscheint im Chat ein klickbarer Link **[Zur Brücke]**.

### Bauhöhe

Auch in Originalgröße ist die Enterprise nur ca. 72 m hoch und passt locker in die normale
Bauhöhe der Overworld (-64 bis 320). Die Bauhöhe muss also **nicht** angepasst werden. Passt das
Schiff an der gewählten Stelle nicht hinein, gibt der Befehl eine Fehlermeldung aus.

### Verkleinerte Version, wichtige Räume in Originalgröße

Bei `compact`, `mini` und `scale` wird nur die Hülle verkleinert. Brücke, Maschinenraum und
Transporterraum werden immer 1:1 gebaut. Damit sie hineinpassen, ist die Sekundärhülle bei
starker Verkleinerung etwas dicker und das Brückenmodul im Verhältnis größer.

## Was im Schiff ist

* **Außen**: Untertasse mit Halbstufen geglättet, Kennung „U.S.S. ENTERPRISE“ und groß „NCC-1701“
  (lesbar von vorne wie beim Original), „NCC-1701“ und rote Zierstreifen an Gondeln und
  Sekundärhülle, orange glühende Bussard-Kollektoren, Positionslichter (Backbord rot,
  Steuerbord grün), Fensterreihen, Deflektorschüssel, Shuttlehangar.
* **Brücke** (Ø 15 m) nach dem Vorbild der Originalserie: graue Wände mit Monitoren und rotem
  Abschlussstreifen, Stationen mit schrägem Pult und farbig leuchtender Front, rotes Geländer
  um die Kommandomulde, Kommandantensessel auf weißem Sockel, rot-schwarze Steuerkonsole mit
  zwei Stühlen, Hauptbildschirm (7 x 3), zwei rote Turbolift-Türen mit leuchtendem Rahmen.
* **Maschinenraum** (19 x 11 m, zwei Ebenen): Warpkern vom Boden bis durch die Decke,
  Plasmaleitungen im Boden, Konsolen, Galerie mit Leitern, Turbolift.
* **Transporterraum**: lila Wände, erhöhte rote Plattform mit Stufe und sechs Transporterfeldern,
  grün schimmernde Energiewand mit Säulen, Strahler in der Decke, rot-graue Bedienkonsole, Turbolift.
* Decks mit Deckenlichtern und Ringkorridoren in Untertassen- und Sekundärhülle.

### Funktionen

* **Turbolift**: Rechtsklick fährt zum verbundenen Deck (Brücke ↔ Maschinenraum, Brücke ↔ Transporterraum).
* **Transporterfeld**: Rechtsklick beamt dich auf den Boden unter dem Schiff.
  Hast du keinen Kommunikator dabei, bekommst du einen.
* **Kommunikator**: Rechtsklick beamt dich zurück auf das zuletzt benutzte Transporterfeld.
* **Brückenstation / Brückenmonitor**: Rechtsklick schaltet zwischen 4 Varianten (Farbe bzw. Anzeige) um.

## Neue Blöcke (43)

* **Hülle**: Hüllenpanzerung (+ Stufe), Hüllenplatte, dunkle Hüllenplatte, Hüllenfenster,
  Kennungsmarkierung (+ Stufe), roter Zierstreifen, Positionslicht rot/grün, Deflektorschüssel,
  Bussard-Kollektor, Warpgondel-Gitter
* **Brücke**: Brückenwand, roter Abschlussstreifen, Brückenstation, Brückenmonitor, Brückengeländer,
  Brückenboden, Stufe, Kommandantensessel, Stationsstuhl, Steuerkonsole, Hauptbildschirm,
  Brückenkonsole, LCARS-Anzeige, Turbolift, Turbolift-Tür, Turbolift-Rahmen
* **Transporterraum**: Transporterraum-Wand, Transporterplattform-Boden, Transporterfeld,
  Transporter-Energiewand, Transporter-Säule, Transporter-Strahler, Transporterkonsole
* **Allgemein**: Deckboden, Korridorwand, Deckenverkleidung, Deckenleuchte, Maschinenraum-Konsole,
  Warpkern, Plasmaleitung

Dazu das Item Kommunikator. Alles ist im Kreativ-Tab „Simon Space Mod“ zu finden.

## Bauen

```bash
./gradlew build
```

Die fertige Mod liegt dann in `build/libs/simonspacemod-1.0.0.jar` und kommt in den
`mods`-Ordner einer Forge-26.2-Installation.

* `./gradlew test` prüft den Bauplan in allen Größen (Räume vorhanden, Turbolifte begehbar …)
  und rendert Vorschaubilder (Draufsicht, Seitenansicht, Längsschnitt) nach `build/ship-preview/`.
* `python3 tools/generate_assets.py` erzeugt alle Texturen, Modelle, Blockstates,
  Loot-Tabellen und Übersetzungen (Deutsch + Englisch) neu.

## Aufbau des Codes

| Datei | Aufgabe |
|-------|---------|
| `ship/EnterpriseBlueprint.java` | Prozeduraler Bauplan: Hüllenform, Decks, Räume, Turbolift-Verbindungen |
| `ship/ShipPart.java`, `ship/ShipPalette.java` | Abstrakte Bauteile → echte Blöcke |
| `ship/ShipBuildManager.java` | Asynchrone Berechnung + Platzierung über mehrere Ticks |
| `command/EnterpriseCommand.java` | Der `/enterprise`-Befehl |
| `ModBlocks.java`, `block/`, `item/` | Neue Blöcke, Block-Entity des Turbolifts, Kommunikator |

*Star Trek und USS Enterprise sind Marken von Paramount. Dies ist ein inoffizielles Fan-Projekt
für den privaten Gebrauch.*
