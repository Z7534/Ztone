# 🛡️ Ztone – All-in-One Server-System

Willkommen in der offiziellen Dokumentation für **Ztone** (Version 1.0.5).  
Ztone ist eine hochgradig optimierte, modulare All-in-One-Lösung für Minecraft Paper-, Purpur- und Leaf-Server (kompatibel mit Java 21 & Java 25).

---

## 📑 Inhaltsverzeichnis
1. [Übersicht & Module](#-übersicht--module)
2. [Clan-System & Clankasse](#1-clan-system--clankasse)
3. [Claims-System (Grundstücksverwaltung)](#2-claims-system-grundstücksverwaltung)
4. [PvPManager & Combat-System](#3-pvpmanager--combat-system)
5. [StoneAttack (Leaderboards, PlayerLimit, Performance)](#4-stoneattack-leaderboards-playerlimit-performance)
6. [DeathChest (Todeskisten)](#5-deathchest-todeskisten)
7. [ClearLag (Item-Bereinigung)](#6-clearlag-item-bereinigung)
8. [PlayRewards (Spielzeit & Streak-Belohnungen)](#7-playrewards-spielzeit--streak-belohnungen)
9. [StoneVoice (VoiceChat-Admin-Tools)](#8-stonevoice-voicechat-admin-tools)
10. [Stonemute (LiteBans VoiceChat-Sync)](#9-stonemute-litebans-voicechat-sync)
11. [MapControl (Minimap- & Radar-Steuerung)](#10-mapcontrol-minimap--radar-steuerung)
12. [Stonelog & Hauptverwaltung](#11-stonelog--hauptverwaltung)
13. [Globale Konfiguration, Auto-Updater & Datenbank](#12-globale-konfiguration-auto-updater--datenbank)
14. [PlaceholderAPI Gesamtübersicht](#13-placeholderapi-gesamtübersicht)
15. [Kompilierung & Entwicklung](#14-kompilierung--entwicklung)

---

## 🧩 Übersicht & Module

Ztone bündelt alle wichtigen Core-Features für moderne Survival- und SMP-Netzwerke in einem einzigen Plugin:
* **Clansystem:** Eigene Clans, Ränge mit detaillierten Rechten, Clankasse mit Zinsen/Schulden, Clan-Homes, Clan-Warps, Clankriege und Allianzen. Volle Leader-Rechte über alle Clan-Ränge (inkl. Co-Owner-Beförderung).
* **Claims:** Chunk-basiertes Claiming mit interaktiver Karten-GUI, granularen Schutzflags, Subclaims, Trusted- und Ban-System sowie Anti-Combat-Fluchtsperre.
* **PvPManager:** Umfassender Combat-Tag, Spawn-/Respawn-Schutz, Anti-CombatLog, Anti-Kill-Abuse und strenge PvP-Hierarchie (Wildnis: Dimensions- & Spielerstatus; Claims: standardmäßig deaktiviert, außer bei Clankrieg oder Admin-Override). Session-basiertes In-Memory-System ohne Datenbank-Leichen.
* **StoneAttack:** Holographische Leaderboards mit transparentem Display-Design, dynamisches PlayerLimit und TPS/MSPT-Serverüberwachung.
* **DeathChest:** Sichere Todeskisten mit Despawn-Timer, Dupe-Schutz und Hologrammen.
* **ClearLag:** Intelligente Boden-Item-Bereinigung mit Countdown-Warnungen und Actionbar.
* **PlayRewards:** Tägliche Belohnungen, Playtime-Tracking und Streak-System mit Schutzpunkten.
* **StoneVoice & Stonemute:** SimpleVoiceChat-Gruppenüberwachung für Admins sowie automatischer Mute-Sync über LiteBans.
* **MapControl:** Erzwingen von Fairplay-Regeln für Client-Minimaps (Xaero, JourneyMap, VoxelMap).
* **Stonelog:** Hochperformantes Discord-Audit-Logging mit automatischem Multi-Embed-Batching (bis zu 10 Embeds/Request) und parallelen URL-Queues.
* **Auto-Config-Sync:** Automatisches Ergänzen neuer Funktionen und Konfigurationsoptionen bei Versionsupdates direkt in bestehende `.yml`-Dateien.

---

## 1. Clan-System & Clankasse

### 🔹 Spieler-Befehle (`/clan`)
*Erfordert standardmäßig die Berechtigung:* `clansystem.use`

| Befehl | Beschreibung | Benötigte Berechtigung |
| :--- | :--- | :--- |
| `/clan` | Öffnet das interaktive Hauptmenü des Clans (oder No-Clan-Menü) | `clansystem.use` |
| `/clan create <Name>` | Erstellt einen neuen Clan | `clansystem.create` |
| `/clan disband` | Löst den eigenen Clan auf (mit Bestätigungs-GUI) | Leader-Recht |
| `/clan leave` | Verlässt den aktuellen Clan | `clansystem.leave` |
| `/clan invite <Spieler>` | Lädt einen Spieler in den Clan ein | `clansystem.invite` |
| `/clan accept` | Nimmt eine ausstehende Clan-Einladung an | `clansystem.join` |
| `/clan apply <Clan>` | Sendet eine Bewerbung an einen offenen Clan | `clansystem.join` |
| `/clan join <Clan>` | Tritt einem öffentlich zugänglichen Clan bei | `clansystem.join` |
| `/clan kick <Spieler>` | Wirft ein Mitglied aus dem Clan | `clansystem.kick` |
| `/clan sethome` | Setzt das Clan-Home an der aktuellen Position | `clansystem.home` |
| `/clan home` | Teleportiert zum Clan-Home (mit Warmup/Cooldown) | `clansystem.home` |
| `/clan delhome` | Löscht das bestehende Clan-Home | `clansystem.home` |
| `/clan warps` | Öffnet die Liste aller Clan-Warps | `clansystem.warp` |
| `/clan setwarp <Name>` | Erstellt einen neuen Clan-Warp | `clansystem.warp` |
| `/clan warp <Name>` | Teleportiert zu einem Clan-Warp | `clansystem.warp` |
| `/clan delwarp <Name>` | Löscht einen Clan-Warp | `clansystem.warp` |
| `/clan chest` | Öffnet die virtuelle Clan-Truhe | `clansystem.chest` |
| `/clan bank` | Öffnet das Clan-Bankkonto (Einzahlen/Auszahlen/Historie) | `clansystem.bank` |
| `/clan bank deposit <Betrag>` | Zahlt Geld auf das Clankonto ein | `clansystem.bank.deposit` |
| `/clan bank withdraw <Betrag>`| Hebt Geld vom Clankonto ab | `clansystem.bank.withdraw` |
| `/clan bank top` | Zeigt die reichsten Clans des Servers | `clansystem.bank.top` |
| `/clan info [Clan]` | Zeigt Informationen über den eigenen oder fremden Clan | `clansystem.info` |
| `/clan list` | Listet alle Clans des Servers sortiert nach Punkten auf | `clansystem.list` |
| `/clan chat <Nachricht>` | Schreibt in den privaten Clan-Chat (Präfix: `#`) | `clansystem.chat` |
| `/clan allychat <Nachricht>`| Schreibt in den Allianz-Chat (Präfix: `##`) | `clansystem.ally` |
| `/clan ally <Clan>` | Sendet/akzeptiert eine Allianz-Anfrage | `clansystem.ally` |
| `/clan enemy <Clan>` | Erklärt einem Clan die Feindschaft | `clansystem.enemy` |
| `/clan war <Clan>` | Startet oder verwaltet einen Clankrieg | `clansystem.war` |
| `/clan surrender` | Kapituliert im laufenden Clankrieg | `clansystem.war` |
| `/clan stats` | Zeigt die Kriegs- und Kampfstatistiken des Clans | `clansystem.stats` |
| `/clan rank create <Name>` | Erstellt einen neuen Rang im Clan | `clansystem.rank` |
| `/clan rank delete <Name>` | Löscht einen Clan-Rang | `clansystem.rank` |
| `/clan rank set <Spieler> <Rang>` | Weist einem Mitglied einen Rang zu | `clansystem.rank` |
| `/clan rank list` | Listet alle Ränge des Clans auf | `clansystem.rank` |
| `/clan rank permissions` | Öffnet das Berechtigungsmenü für Ränge | `clansystem.rank` |

---

### 🔹 Admin-Befehle (`/clanadmin`)
*Hauptberechtigung:* `clansystem.admin`

| Befehl | Beschreibung | Berechtigung |
| :--- | :--- | :--- |
| `/clanadmin reload` | Lädt alle Clan-Konfigurationsdateien neu | `clansystem.admin.reload` |
| `/clanadmin disband <Clan>` | Löst einen beliebigen Clan zwangsweise auf | `clansystem.admin.disband` |
| `/clanadmin setleader <Clan> <Spieler>` | Setzt einen neuen Anführer für einen Clan | `clansystem.admin.setleader` |
| `/clanadmin promote <Spieler> [Clan]` | Befördert einen Spieler im Clan | `clansystem.admin.promote` |
| `/clanadmin demote <Spieler> [Clan]` | Degradiert einen Spieler im Clan | `clansystem.admin.demote` |
| `/clanadmin addpoints <Clan> <Menge>` | Fügt einem Clan Ranglisten-Punkte hinzu | `clansystem.admin.points` |
| `/clanadmin removepoints <Clan> <Menge>`| Zieht einem Clan Punkte ab | `clansystem.admin.points` |
| `/clanadmin addmoney <Clan> <Betrag>` | Zahlt Geld auf die Clankasse ein | `clansystem.admin.bank` |
| `/clanadmin removemoney <Clan> <Betrag>`| Hebt Geld von der Clankasse ab | `clansystem.admin.bank` |
| `/clanadmin setlevel <Clan> <Level>` | Setzt das Level eines Clans manuell | `clansystem.admin.level` |
| `/clanadmin spy` | Schaltet den Clan- und Allianz-Chat-Spion ein/aus | `clansystem.admin.spy` |
| `/clanadmin reset` | Bereinigt die gesamte Clan-Datenbank (Vorsicht!) | `clansystem.admin.reset` |

---

### 🔹 Clan-Ränge & Leader-Befugnisse
* **Volle Leader-Freiheit:** Der Clan-Owner (Leader) unterliegt keinen Rang-Prioritätsbeschränkungen mehr. Er kann jedes Mitglied zum höchsten Unterrang (z. B. Co. Owner) befördern, degradieren oder aus dem Clan entfernen.
* **Granulare Rechteverwaltung:** Jeder Rang kann mit individuellen Rechten ausgestattet werden (Bauen im Claim, Clantruhe, Einladungen, Clankasse, Warps, Kriegserklärungen).

---

## 2. Claims-System (Grundstücksverwaltung)

### 🔹 Spieler-Befehle (`/claim`)
*Erfordert standardmäßig die Berechtigung:* `claims.use`

| Befehl | Beschreibung |
| :--- | :--- |
| `/claim` | Öffnet die interaktive Karten-GUI zur visuellen Verwaltung von Chunks |
| `/claim auto` | Claimt den Chunk, auf dem der Spieler gerade steht |
| `/claim unclaim` | Gibt den aktuellen Chunk frei |
| `/claim unclaimall` | Gibt alle Grundstücke des Spielers frei (mit Sicherheitsabfrage) |
| `/claim trust <Spieler>` | Öffnet die Rechte-Verwaltung für einen Spieler auf diesem Claim |
| `/claim untrust <Spieler>` | Entzieht einem Spieler alle Rechte auf dem Claim |
| `/claim ban <Spieler>` | Bannt einen Spieler vom Claim (wird an die Grenze teleportiert) |
| `/claim unban <Spieler>` | Hebt den Bann für einen Spieler auf |
| `/claim list` | Listet alle eigenen Claims mit Koordinaten und Welt auf |
| `/claim pay <Spieler> <Anzahl>` | Überträgt Claim-Blöcke/Slots an einen anderen Spieler |
| `/claim settings` | Öffnet das Einstellungsmenü für Flags (PVP, Explosionen, Mobs etc.) |

---

### 🔹 Admin-Befehle (`/staffclaim`)
*Hauptberechtigung:* `claims.admin`

| Befehl | Beschreibung | Berechtigung |
| :--- | :--- | :--- |
| `/staffclaim bypass [on\|off] [Spieler]` | Aktiviert den Administrator-Bypass für alle Claims | `claims.admin.bypass` |
| `/staffclaim unclaim` | Löscht den Claim an der aktuellen Position zwangsweise | `claims.admin.unclaim` |
| `/staffclaim info` | Zeigt Besitzer, Erstellungsdatum und Flags des aktuellen Chunks | `claims.admin.info` |
| `/staffclaim reload` | Lädt `config.yml` und `messages.yml` des Claims-Moduls neu | `claims.admin.reload` |
| `/claimspvpoverride [on\|off\|toggle\|player\|list]` | Schaltet PvP auf einem Claim frei *(Aliase: `/pvpclaimbypass`, `/claimpvpbypass`, `/pvpclaim`, `/pvpclaimoverride`)* | `claims.admin.pvpbypass` |

---

### 🔹 Claim-Flags & Schutzrechte
Jeder Claim-Besitzer kann pro Spieler oder global folgende Rechte vergeben:
* `BUILD`: Blöcke abbauen und platzieren.
* `CONTAINERS`: Kisten, Fässer, Chiseled Bookshelves, Shelves (Regale), Braustände, Trichter öffnen.
* `DOORS`: Türen, Falltüren, Zauntore öffnen/schließen.
* `REDSTONE`: Knöpfe, Hebel und Druckplatten betätigen.
* `VEHICLES`: Boote, Loren und Flöße platzieren, fahren oder abbauen.
* `ENTITY_INTERACT`: Rüstungsständer, Bilderrahmen, Glow-Frames, Endkristalle nutzen/abbauen; Enderperlen & Wind Charges werfen (Selbstanwendung wie Wind Charge Boost/Jump in Claims erlaubt).
* `ATTACK_MOBS`: Tiere, Dorfbewohner und passive Mobs schlagen (Monster-Notwehr ist immer erlaubt).
* `BUCKETS`: Wasser-, Lava- und Eimer-Nutzung.
* `SPAWNER`: SmartSpawner GUI öffnen und Mob-Spawner modifizieren/abbauen.
* `FIRE_EXPLOSIONS`: Feuerzeug, TNT-Zündung und Explosionen.
* `PVP`: Nur relevant, wenn ein Claim durch Admins mit `/claimspvpoverride` freigegeben ist.

---

### 🔹 Combat-Sperre für Claims (Anti-Combat-Flucht)
> [!IMPORTANT]
> **Claim-Eintrittsverbot im Kampf:**  
> Spieler mit aktivem Combat-Tag können fremde oder geschützte Claims **nicht betreten**.  
> Jeder Versuch (Laufen, Springen, Teleportation, Enderperlen, Reittiere) wird mit einer Rollback-Aktion abgewehrt und Reittiere werden abgeworfen (Verhalten analog zum Claim-Bann).  
> **Ausnahmen:**  
> 1. Claims, auf denen Admins PvP per `/claimspvpoverride` freigeschaltet haben.  
> 2. Admins mit aktivem Bypass (`claims.admin.bypass` oder `/pvpoverride`).

---

## 3. PvPManager & Combat-System

### 🔹 PvP & Combat-Befehle

| Befehl | Aliase | Beschreibung | Berechtigung |
| :--- | :--- | :--- | :--- |
| `/pvp [on\|off]` | `/togglepvp`, `/pvptoggle` | Schaltet den eigenen PvP-Status um | `pvpmanager.command.pvp` |
| `/pvpoverride [Spieler]` | `/pvpo` | Erlaubt Admins, Spieler überall anzugreifen (Bypass) | `pvpmanager.command.override` |
| `/pvpinfo [Spieler]` | - | Zeigt detaillierte PvP- und Combat-Informationen an | `pvpmanager.command.pvpinfo` |
| `/pvpstatus [Spieler]` | - | Zeigt den aktuellen PvP-Status eines Spielers | `pvpmanager.command.pvpstatus` |
| `/pvplist` | `/listpvp` | Listet den PvP-Status aller Online-Spieler auf | `pvpmanager.command.pvplist` |
| `/pvptag [Spieler] [Zeit]` | `/combattag`, `/tag` | Zeigt verbleibende Tag-Zeit oder taggt manuell | `pvpmanager.command.tag` |\n| `/untag [Spieler\|*]` | - | Entfernt den Combat-Tag von einem oder allen Spielern | `pvpmanager.command.untag` |
| `/teleportoverride [Spieler]`| - | Erlaubt Teleportation trotz Combat-Tag | `pvpmanager.command.teleportoverride` |
| `/pvpdimensiontoggle <Dim> <on\|off> [--force]` | - | Schaltet PvP für eine Dimension permanent ein/aus | `pvpmanager.command.pvpdimensiontoggle` |
| `/announce <Nachricht>` | - | Sendet einen serverweiten Broadcast | `pvpmanager.command.announce` |
| `/soup` | - | Füllt leere Schüsseln im Inventar mit Pilzsuppe | `pvpmanager.command.soup` |
| `/testrace` | - | Debug-Befehl für Entwickler | `pvpmanager.command.testrace` |

---

### 🔹 Die vollständige PvP-Logik
> [!IMPORTANT]
> **Hierarchie der Kampfbeschränkungen:**
> 1. **Unclaimed Terrain (Wildnis):**  
>    * In nicht-forcierten Welten entscheidet der persönliche Spielerstatus (`/pvp on/off`). Haben beide Spieler `/pvp on`, können sie kämpfen.  
>    * Hat ein Spieler `/pvp off`, kann er weder andere angreifen noch von ihnen angegriffen werden.  
>    * Bei forcierten Dimensionen (`--force`) greift der globale Dimensions-Status zwingend für alle Spieler ohne Ausnahmerechte.  
> 2. **Claims:**  
>    * PvP ist auf Claims standardmäßig **vollständig deaktiviert**, unabhängig vom `/pvp`-Status der Spieler.  
>    * Ausnahme A: Ein Admin hat mit `/claimspvpoverride` PvP auf diesem Claim aktiviert (verhält sich dann wie Wildnis).  
>    * Ausnahme B: Die Kontrahenten befinden sich in einem aktiven **Clankrieg** (`/clan war`).  
>    * Ausnahme C: Ein Admin besitzt `/pvpoverride` oder den individuellen Admin-Bypass.  
> 3. **Selbstinteraktion & Movement:**  
>    * Wind Charges und Enderperlen auf sich selbst (z. B. Boost-Sprünge) triggern niemals irrtümlich eine PvP-Schadensverweigerung.

> [!NOTE]
> **Temporäre Session-Daten & In-Memory-Verwaltung:**  
> Der persönliche Status eines Spielers (`/pvp on/off`) wird rein im Arbeitsspeicher (Session) gehalten. Temporäre Spielerdaten werden nicht in der Datenbank abgelegt, wodurch nach Server-Neustarts oder Reconnects keine veralteten Zustände verbleiben.  
> **Dimensions-Einstellungen** (`DimensionSettings`) in der `config.yml` bleiben dauerhaft gespeichert. Dimensions-Defaults überschreiben den Status des Spielers niemals ungefragt beim Betreten einer Welt oder beim Joinen.

* **Combat-Tag:** Bei Schaden oder aggressiven Aktionen (auch feindliche Wind Charges/Enderperlen) wird der Combat-Tag aktiviert. Befehle, Fliegen, Gamemode und Elytren werden temporär blockiert.
* **Anti-CombatLog:** Verlässt ein Spieler den Server im Kampf, wird er getötet, sein Loot gedroppt und Blitze schlagen an seiner Position ein.
* **Respawn-Schutz:** Frisch respawnte Spieler erhalten temporären Schutz vor Schaden und Angriffen.

---

## 4. StoneAttack (Leaderboards, PlayerLimit, Performance)

### 🔹 Leaderboard-Befehle (`/leaderboard`)
Verwaltet dynamische Ranglisten-Hologramme für Bestenlisten (Spielzeit, Kills, Tode, Geld).  
*Design:* Nutzt moderne Paper TextDisplay-Entities mit vollständig transparentem Hintergrund (`Color.fromARGB(0)`), gestochen scharfen Schriftfarben und Rangabzeichen (🥇, 🥈, 🥉) ohne störende dunkle Hintergrundboxen.  
*Berechtigung:* `leaderboard.admin`

| Befehl | Beschreibung |
| :--- | :--- |
| `/leaderboard place <Typ>` | Platziert ein neues Leaderboard (`playtime`, `kills`, `deaths`, `money`) |
| `/leaderboard move <ID>` | Verschiebt ein bestehendes Leaderboard an die aktuelle Position |
| `/leaderboard height <ID> <Offset>` | Passt den Zeilenabstand/Höhe der Hologramme an |
| `/leaderboard remove <ID>` | Entfernt ein Leaderboard |
| `/leaderboard removeplayer <Spieler>` | Schließt einen Spieler von allen Leaderboards aus |
| `/leaderboard addplayer <Spieler>` | Fügt einen zuvor ausgeschlossenen Spieler wieder hinzu |
| `/leaderboard excluded` | Zeigt alle ausgeschlossenen Spieler |
| `/leaderboard list` | Listet alle aktiven Leaderboards mit IDs auf |

---

## 5. DeathChest (Todeskisten)

Schützt das Inventar verstorbener Spieler in einer gesicherten Kiste:
* **Hologramm-Timer:** Zeigt verbleibende Restzeit und Spielernamen über der Kiste an.
* **Sicherheit:** Kisten können nicht von fremden Spielern geöffnet werden, solange der Schutz aktiv ist.
* **Befehl:** `/deathchest reload` (Berechtigung: `deathchest.admin`).

---

## 6. ClearLag (Item-Bereinigung)

Intelligente Boden-Item-Bereinigung zur Erhaltung hoher TPS:
* Konfigurierbare Countdown-Intervalle mit Chat- und Actionbar-Warnungen.
* Schont gezielt markierte Items, Tiere, Rüstungsständer und Fahrzeuge.
* **Befehl:** `/clearlag [clear|reload]` (Berechtigung: `clearlag.admin`).

---

## 7. PlayRewards (Spielzeit & Streak-Belohnungen)

* **Daily Rewards:** Tägliche Belohnungen mit Belohnungsstufen und Schutzpunkten.
* **Playtime Rewards:** Belohnungen für aktive Spielstunden auf dem Server.
* **Streak-Schutz:** Verhindert den Verlust der täglichen Serie bei Serverabwesenheit.
* **Befehl:** `/rewards` oder `/playrewards` (Berechtigung: `playrewards.use`).

---

## 8. StoneVoice (VoiceChat-Admin-Tools)

Ermöglicht Moderatoren und Admins die diskrete Überwachung von Simple Voice Chat Gruppenräumen:
* `/adminjoinvoice <Gruppe>` (Berechtigung: `stonevoice.admin`): Tritt einem Sprachkanal bei.
* Modus-Optionen:
  * `[listen]`: Kann nur zuhören, Mikrofon stumm.
  * `[speak]`: Kann sprechen und hören.
  * `[hidden]`: Ist in der Gruppenliste für andere Spieler unsichtbar.
  * `[FullMuted]`: Weder hören noch sprechen (reines Ghosting).

---

## 9. Stonemute (LiteBans VoiceChat-Sync)

Verknüpft LiteBans direkt mit dem Simple Voice Chat:
* Wenn ein Spieler über LiteBans stummgeschaltet (`/mute`) wird, wird er synchron auch im VoiceChat gemutet.
* `stonemute.bypass`: Erlaubt es Teammitgliedern, trotz LiteBans-Mute im VoiceChat zu sprechen.

---

## 10. MapControl (Minimap- & Radar-Steuerung)

Sendet standardisierte Plugin-Nachrichten an VoxelMap, Xaero's Minimap und JourneyMap, um Cheaten zu verhindern:
* Deaktiviert Höhlen-Minimaps (Höhlenansicht).
* Deaktiviert Spieler- und Entity-Radar.
* Berechtigung:
  * `mapcontrol.admin`: Verwaltung via `/mapcontrol <reload|info|send>`
  * `mapcontrol.bypass`: Erlaubt vertrauenswürdigen Spielern volle Minimap-Features.

---

## 11. Stonelog & Hauptverwaltung

### 🔹 Hauptbefehl (`/ztone`)
*Berechtigung:* `ztone.admin`
* `/ztone reload`: Führt einen vollständigen Reload aller Submodule und Konfigurationen durch.

### 🔹 Logging (`/stonelog`)
*Berechtigung:* `stonelog.admin`
* `/stonelog reload`: Lädt die Audit- und Logging-Konfiguration neu.
* **Hochleistungs-Discord-Dispatcher:**
  * **Multi-Embed-Batching:** Bis zu 10 Embeds (Chat, Befehle, Tode, Joins) werden automatisch in einem einzigen HTTP-POST-Request gebündelt versendet.
  * **Parallele Queues:** Jeder Webhook-URL besitzt eine eigene Queue und einen eigenen Worker im 4-Thread-Pool. Ein Rate-Limit (429) auf einer Webhook-URL blockiert niemals andere Kanäle.
  * **Kein Queue-Überlauf:** Die 5000-Nachrichten-Queue läuft selbst bei extremem Chat- oder Befehls-Spam nicht mehr voll.

---

## 12. Globale Konfiguration, Auto-Updater & Datenbank

### 🔄 Automatischer Konfigurations-Updater (Auto-Sync)
Ztone verfügt über ein integriertes **Auto-Sync-System** (`ConfigUtil`):
* Beim Starten oder Neuladen des Plugins gleicht Ztone alle physischen `.yml`-Dateien auf der Festplatte mit den neuesten Standarddateien aus der JAR ab.
* **Kein Verlust bestehender Werte:** Bereits vorhandene Benutzereinstellungen, geänderte Texte oder Zahlen werden niemals überschrieben.
* **Automatische Ergänzung:** Neu hinzugekommene Optionen, Untersektionen und Standard-Kommentare werden automatisch in die Datei auf der Festplatte eingefügt.
* **Gilt für alle Configs:** Hauptkonfiguration (`config.yml`), Clan-System (`clansystem/`), PvPManager (`pvpmanager/`), Claims (`claims/`), DeathChest (`deathchest/`), ClearLag (`clearlag/`), StoneAttack (`stoneattack/`), StoneLog (`stonelog/`), StoneMute (`stonemute/`), MapControl (`mapcontrol/`) und PlayRewards (`playrewards/`).

### ⚙️ Modul-Aktivierung (`plugins/Ztone/config.yml`)
In der zentralen Konfigurationsdatei können alle Submodule einzeln de-/aktiviert werden:

```yaml
modules:
  clansystem: true
  pvpmanager: true
  claims: true
  stoneattack: true
  stonelog: true
  stonemute: true
  mapcontrol: true
  stonevoice: true
  playrewards: true
  deathchest: true
  clearlag: true

database:
  type: "sqlite" # oder "mariadb"
  file: "ztone.db"
  table-prefix: "ztone_"
```

> [!NOTE]
> **Permanente vs. Temporäre Tabellen:**  
> Alle Clans (`clans`, `members`, `ranks`, `homes`, `warps`, `chests`, `alliances`, `wars`) und Claims (`claims_zones`, `claims_members`, etc.) werden dauerhaft und sicher in SQLite/MariaDB persistiert. Die temporäre Tabelle `pvpmanager_players` wurde vollständig zurückgesetzt (`DROP TABLE IF EXISTS`), da temporäre Session-Zustände rein im RAM verwaltet werden.

---

## 13. PlaceholderAPI Gesamtübersicht

Alle Platzhalter erfordern das installierte Plugin **PlaceholderAPI**.

### 🏰 Clan-Platzhalter (`%clansystem_...%` oder `%ztone_...%`)
| Placeholder | Rückgabewert |
| :--- | :--- |
| `%clansystem_clan_name%` | Clan-Name mit Farbcodes |
| `%clansystem_clan_name_raw%` | Clan-Name ohne Farbcodes |
| `%clansystem_clan_tag%` | Clan-Suffix mit Farbe |
| `%clansystem_clan_tag_raw%` | Clan-Suffix als Rohtext |
| `%clansystem_clan_rank%` | Name des eigenen Clan-Rangs |
| `%clansystem_clan_level%` | Aktuelles Clan-Level |
| `%clansystem_clan_points%` | Gesamtpunkte des Clans |
| `%clansystem_clan_balance%` | Kontostand der Clankasse |
| `%clansystem_clan_members_count%`| Anzahl der Mitglieder |
| `%clansystem_clan_members_online%`| Anzahl der online Mitglieder |
| `%clansystem_has_clan%` | `true` oder `false` |
| `%clansystem_is_leader%` | `true` oder `false` |

---

## 14. Kompilierung & Entwicklung

```powershell
# Kompilieren mit Gradle Shadow-Jar
./gradlew shadowJar
```
Die fertige Server-JAR befindet sich im Verzeichnis `build/libs/Ztone-1.0.5.jar`.
