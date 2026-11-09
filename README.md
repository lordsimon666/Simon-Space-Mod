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

* **Außen**: Untertasse mit Halbstufen geglättet, Kennung wie beim Original (lesbar von vorne):
  „NCC-1701“ im Bogen entlang des vorderen Rands, darüber klein „USS ENTERPRISE“, „NCC-1701“ und rote Zierstreifen an Gondeln und
  Sekundärhülle, orange glühende Bussard-Kollektoren, Positionslichter (Backbord rot,
  Steuerbord grün), Fensterreihen, Deflektorschüssel, Shuttlehangar.
* **Brücke** (Ø 15 m) nach dem Vorbild der Originalserie: graue Wände mit Monitoren und rotem
  Abschlussstreifen, Stationen mit schrägem Pult und farbig leuchtender Front, rotes Geländer
  um die Kommandomulde, Kommandantensessel auf weißem Sockel, rot-schwarze Steuerkonsole mit
  zwei Stühlen, Hauptbildschirm (7 x 3), ein Turbolift hinten Steuerbord mit roter Tür und
  leuchtendem Rahmen.
* **Maschinenraum** (19 x 11 m, zwei Ebenen): Warpkern vom Boden bis durch die Decke,
  Plasmaleitungen im Boden, Konsolen, Galerie mit Leitern, Turbolift.
* **Transporterraum**: lila Wände, erhöhte rote Plattform mit Stufe und sechs Transporterfeldern,
  grün schimmernde Energiewand mit Säulen, Strahler in der Decke, rot-graue Bedienkonsole, Turbolift.
* **Alle Decks (ab Größe 0,75, voll ausgebaut in 1:1)** – jedes Deck mit Turbolift-Halt:
  * Untertasse: runder Vorraum mit Turbolift in der Mitte, vier Gänge, Ringkorridor, acht große
    Innenräume und 24 Quartiere am Rand (mit Fenstern).
    * Deck 2: **Besprechungsraum** wie in der Serie (langer Konferenztisch mit dreiseitigem
      Bildschirm), Büro des Captains, Offiziersquartiere
    * Deck 3: **Krankenstation** mit Biobetten, Transporterraum, Labore
    * Deck 4: Messe und Freizeitraum
    * Deck 5: Wissenschaftslabore, Deck 6: Mannschaftsquartiere
  * Sekundärhülle: Mittelgang mit Turbolift vorne, Räume links und rechts (Frachträume,
    Quartiere, Labore, Lager), dazu Maschinenraum und Shuttlehangar.
  * In der Größe 1:1 gibt es 13 Turbolift-Halte (Brücke, 5 Decks in der Untertasse,
    6 in der Sekundärhülle, Maschinenraum).

### Funktionen

* **Turbolift**: Rechtsklick öffnet das Turbolift-Menü mit allen Decks des Netzes (von oben nach
  unten sortiert). Ein Klick auf ein Deck fährt dorthin. Im Menü kann man dem Lift einen
  **Decknamen** geben und ihn einem **Netz** zuordnen („Speichern“).
* **Eigene Turbolifte**: Jeder gesetzte Turbolift-Block meldet sich automatisch an (Netz
  „Standard“, Name „Deck Y=…“). Alle Lifte mit demselben Netznamen in derselben Dimension sind
  verbunden – so baust du Fahrstühle in eigenen Schiffen und Basen. Mit unterschiedlichen Netznamen
  bleiben getrennte Basen getrennt. Jede gespawnte Enterprise hat ihr eigenes Netz
  („NCC-1701 x/z“). Ausgestiegen wird auf der Seite, in die die Vorderseite des Blocks zeigt.
* **Transporterfeld**: Rechtsklick beamt dich auf den Boden unter dem Schiff.
  Hast du keinen Kommunikator dabei, bekommst du einen.
* **Kommunikator**: Rechtsklick beamt dich zurück auf das zuletzt benutzte Transporterfeld.
* **Brückenstation / Brückenmonitor**: Rechtsklick schaltet zwischen 4 Varianten (Farbe bzw. Anzeige) um.

### Rezepte (Überlebensmodus)

```
Turbolift (2x)        Kommunikator
E = Eisenbarren        G = Goldbarren
 E F E                    G
 E P E                  E P E
 E R E                  E R E
F = roter Farbstoff, P = Enderperle, R = Redstone
```

## Neue Blöcke (50)

* **Hülle**: Hüllenpanzerung (+ Stufe), Hüllenplatte, dunkle Hüllenplatte, Hüllenfenster,
  Kennungsmarkierung (+ Stufe), roter Zierstreifen, Positionslicht rot/grün, Deflektorschüssel,
  Bussard-Kollektor, Warpgondel-Gitter
* **Brücke**: Brückenwand, roter Abschlussstreifen, Brückenstation, Brückenmonitor, Brückengeländer,
  Brückenboden, Stufe, Kommandantensessel, Stationsstuhl, Steuerkonsole, Hauptbildschirm,
  Brückenkonsole, LCARS-Anzeige, Turbolift, Turbolift-Tür, Turbolift-Rahmen
* **Transporterraum**: Transporterraum-Wand, Transporterplattform-Boden, Transporterfeld,
  Transporter-Energiewand, Transporter-Säule, Transporter-Strahler, Transporterkonsole
* **Möbel**: Konferenztisch, dreiseitiger Tischbildschirm, Biobett, Koje, Schreibtisch,
  Frachtcontainer, Messetisch
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
| `ModBlocks.java`, `block/`, `item/` | Neue Blöcke und Kommunikator |
| `turbolift/` | Turbolift-Netz (gespeichert pro Welt), Menü, Netzwerkpaket zum Umbenennen |
| `client/` | Turbolift-Bildschirm |

*Star Trek und USS Enterprise sind Marken von Paramount. Dies ist ein inoffizielles Fan-Projekt
für den privaten Gebrauch.*
