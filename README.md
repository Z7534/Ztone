# 🛡️ Ztone – All-in-One Server-System

Willkommen in der offiziellen Dokumentation für **Ztone** (Version 1.0.2).  
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
13. [PlaceholderAPI Gesamtübersicht](#12-placeholderapi-gesamtübersicht)

---

## 🧩 Übersicht & Module

Ztone bündelt alle wichtigen Core-Features für moderne Survival- und SMP-Netzwerke in einem einzigen Plugin:
* **Clansystem:** Eigene Clans, Ränge mit detaillierten Rechten, Clankasse mit Zinsen/Schulden, Clan-Homes, Clan-Warps, Clankriege und Allianzen.
* **Claims:** Chunk-basiertes Claiming mit interaktiver Karten-GUI, granularen Schutzflags, Subclaims, Trusted- und Ban-System.
* **PvPManager:** Umfassender Combat-Tag, Spawn-/Respawn-Schutz, Anti-CombatLog, Anti-Kill-Abuse und strenge Claim-PvP-Regeln (PvP ist in der Wildnis standardmäßig deaktiviert und nur in Claims mit explizitem PvP-Recht oder in Clankriegen aktiv).
* **StoneAttack:** Holographische Leaderboards (Spielzeit, Kills, Tode, Guthaben), dynamisches PlayerLimit und TPS/MSPT-Serverüberwachung.
* **DeathChest:** Sichere Todeskisten mit Despawn-Timer, Dupe-Schutz und Hologrammen.
* **ClearLag:** Intelligente Boden-Item-Bereinigung mit Countdown-Warnungen und Actionbar.
* **PlayRewards:** Tägliche Belohnungen, Playtime-Tracking und Streak-System mit Schutzpunkten.
* **StoneVoice & Stonemute:** SimpleVoiceChat-Gruppenüberwachung für Admins sowie automatischer Mute-Sync über LiteBans.
* **MapControl:** Erzwingen von Fairplay-Regeln für Client-Minimaps (Xaero, JourneyMap, VoxelMap).

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
| `/clan delhome` | Löscht das Clan-Home | `clansystem.home` |
| `/clan homes` | Öffnet die Home-Verwaltung | `clansystem.home` |
| `/clan setwarp <Name>` | Erstellt einen neuen Clan-Warp | `clansystem.warp` |
| `/clan warp <Name>` | Teleportiert zu einem Clan- oder Allianz-Warp | `clansystem.warp` |
| `/clan delwarp <Name>` | Löscht einen Clan-Warp | `clansystem.warp` |
| `/clan warps` | Öffnet das Warp-Menü | `clansystem.warp` |
| `/clan chest` | Öffnet die gemeinsame Clan-Truhe | `clansystem.chest` |
| `/clan ally <invite\|accept\|remove> <Clan>` | Verwaltet Allianzen mit anderen Clans | `clansystem.ally` |
| `/clan rename <NeuerName>` | Benennt den Clan um | `clansystem.use` / `clansystem.rename` |
| `/clan war <challenge\|accept\|surrender\|info> <Clan>` | Verwaltet Clankriege gegen feindliche Clans | `clansystem.war` |
| `/clan rank` | Öffnet die Menü-Verwaltung für Ränge & Rechte | `clansystem.rank` |
| `/clan settings` | Öffnet die Clan-Einstellungen (Beitrittsart, Farben etc.) | `clansystem.settings` |
| `/clan list` | Zeigt alle bestehenden Clans | `clansystem.list` |
| `/clan top` | Zeigt die Top 10 Clans sortiert nach Guthaben | `clansystem.top` |
| `/clan deposit <Betrag>` *(Alias: `/clan einzahlen`)* | Zahlt Geld von der Spielergeldbörse in die Clankasse | `clansystem.use` |
| `/clan withdraw <Betrag>` *(Alias: `/clan auszahlen`)* | Zahlt Geld aus der Clankasse an den Spieler aus | `clansystem.use` |
| `/clan bank [info\|history]` *(Alias: `/clan kasse`)* | Öffnet die Übersicht der Clankasse / Transaktions-Log | `clansystem.use` |
| `/clan info [Clan\|Spieler]` | Ruft Clan-Statistiken ab | `clansystem.info` |
| `/clan chat <Nachricht>` *(Alias: `/clan c`)* | Schreibt in den privaten Clan-Chat | `clansystem.chat` |
| `/clan ac <Nachricht>` | Schreibt in den Allianz-Chat | `clansystem.chat` |
| `/clan suffix <Suffix>` | Ändert den Clan-Suffix/Tag | Leader / Settings |
| `/clan promote <Spieler>` | Befördert ein Clan-Mitglied im Rang | Leader / Ränge |
| `/clan demote <Spieler>` | Degradiert ein Clan-Mitglied im Rang | Leader / Ränge |
| `/clan transfer <Spieler>` | Übergibt die Clan-Leitung an ein anderes Mitglied | Leader |

---

### 🔹 Clan-Admin-Befehle (`/clanadmin`)
*Hauptberechtigung:* `clansystem.admin`

| Befehl | Beschreibung | Berechtigung |
| :--- | :--- | :--- |
| `/clanadmin reload` | Lädt alle Clan-Konfigurationsdateien neu | `clansystem.admin.reload` |
| `/clanadmin delete <Clan>` | Löscht einen Clan unwiderruflich | `clansystem.admin.delete` |
| `/clanadmin setlevel <Clan> <Level>` | Setzt das Level eines Clans manuell | `clansystem.admin.setlevel` |
| `/clanadmin setleader <Clan> <Spieler>` | Setzt den Clan-Leader eines Clans neu fest | `clansystem.admin.setleader` |
| `/clanadmin chest <Clan>` | Öffnet die Clan-Truhe eines fremden Clans | `clansystem.admin.chest` |
| `/clanadmin addpoints <Clan> <Punkte>` | Schreibt einem Clan Clan-Punkte gut | `clansystem.admin.addpoints` |
| `/clanadmin info <clan\|player> <Name>` | Zeigt detaillierte Admin-Informationen zu einem Clan/Spieler | `clansystem.admin.info` |
| `/clanadmin forcejoin <Spieler> <Clan>` | Fügt einen Spieler zwangsweise einem Clan hinzu | `clansystem.admin.bypass` |
| `/clanadmin forceleave <Spieler>` | Entfernt einen Spieler zwangsweise aus seinem Clan | `clansystem.admin.bypass` |
| `/clanadmin list` | Listet alle Clans mit Level, Mitgliedern und Punkten auf | `clansystem.admin.list` |
| `/clanadmin settings <Clan>` | Öffnet die Einstellungen eines fremden Clans als Admin | `clansystem.admin.settings` |
| `/clanadmin bold <Clan> <true\|false>` | Schaltet fette Formatierung für den Clan-Suffix um | `clansystem.admin.bold` |
| `/clanadmin home <Clan>` | Teleportiert den Admin zum Home des Clans | `clansystem.admin.home` |
| `/clanadmin warp <Clan> <list\|Warpname>` | Listet Warps auf oder teleportiert zu einem Clan-Warp | `clansystem.admin.warp` |

---

### 🔹 Clan-Berechtigungen

| Permission | Beschreibung | Standard |
| :--- | :--- | :--- |
| `clansystem.use` | Grundzugriff auf `/clan` und Clan-GUIs | `true` (alle) |
| `clansystem.create` | Clan gründen | `true` |
| `clansystem.join` | Einem Clan beitreten | `true` |
| `clansystem.leave` | Clan verlassen | `true` |
| `clansystem.chat` | Nutzung von Clan- und Allianz-Chat | `true` |
| `clansystem.home` | Nutzung von Clan-Homes | `true` |
| `clansystem.warp` | Nutzung von Clan-Warps | `true` |
| `clansystem.chest` | Öffnen der Clan-Truhe | `true` |
| `clansystem.list` | Anzeigen der Clan-Liste | `true` |
| `clansystem.top` | Anzeigen der Clan-Topliste | `true` |
| `clansystem.info` | Abfragen von Clan-Infos | `true` |
| `clansystem.invite` | Spieler in den Clan einladen | `true` |
| `clansystem.kick` | Spieler aus dem Clan kicken | `true` |
| `clansystem.ally` | Allianzen verwalten | `true` |
| `clansystem.war` | Kriege erklären / annehmen | `true` |
| `clansystem.settings` | Clan-Einstellungen verwalten | `true` |
| `clansystem.rank` | Ränge und Rechte verwalten | `true` |
| `clansystem.bypass.cooldown` | Umgeht alle Teleport-Cooldowns | `op` |
| `clansystem.bypass.warmup` | Umgeht alle Teleport-Warmups | `op` |
| `clansystem.admin` | Voller Admin-Zugriff auf das Clan-System | `op` |

---

## 2. Claims-System (Grundstücksverwaltung)

### 🔹 Spieler-Befehle (`/claim`)
*Erfordert standardmäßig die Berechtigung:* `claims.use`

| Befehl | Beschreibung | Benötigte Berechtigung |
| :--- | :--- | :--- |
| `/claim` | Öffnet das grafische Claim-Hauptmenü (Karte, Flags, Trust, Einstellungen) | `claims.use` |
| `/claim pay <Spieler> <Anzahl>` | Verschenkt freie Claim-Slots an einen anderen Mitspieler | `claims.pay` |

---

### 🔹 Admin-Befehle (`/staffclaim` & `/pvpclaimbypass`)

| Befehl | Beschreibung | Berechtigung |
| :--- | :--- | :--- |
| `/staffclaim addclaims <Spieler> <Zahl>` | Vergibt permanente Bonus-Claim-Slots | `claims.admin.addclaims` |
| `/staffclaim removeclaims <Spieler> <Zahl>` | Zieht Bonus-Claim-Slots ab | `claims.admin.removeclaims` |
| `/staffclaim info <Spieler>` | Listet alle Claims eines Spielers mit Koordinaten und Chunkzahl auf | `claims.admin.info` |
| `/staffclaim tp <Spieler> <ClaimNr>` | Teleportiert den Staffler direkt auf den Claim | `claims.admin.teleport` |
| `/staffclaim unclaim <Spieler> <ClaimNr>` | Löscht einen Claim vollständig | `claims.admin.unclaim` |
| `/staffclaim unclaimhere` | Löscht den Claim am aktuellen Standort des Admins | `claims.admin.unclaim` |
| `/staffclaim unclaimhereonly` | Unclaimt nur den einzelnen Chunk, in dem der Admin steht | `claims.admin.unclaim` |
| `/staffclaim trust <Spieler> <ClaimNr\|all> <add\|remove\|list> [Spieler2]` | Verwaltet Trust-Rechte fremder Claims (auch Bulk-Trust mit `all`) | `claims.admin.trust` |
| `/staffclaim ban <Spieler> <ClaimNr\|all> <add\|remove\|list> [Spieler2]` | Verwaltet Banns auf fremden Claims (inkl. sofortigem Rauswurf) | `claims.admin.ban` |
| `/staffclaim bypass [on\|off] [Spieler]` | Schaltet den Admin-Bypass für Schutz und Claim-Grenzen ein/aus | `claims.admin.bypass` |
| `/staffclaim reload` | Lädt `config.yml` und `messages.yml` des Claims-Moduls neu | `claims.admin.reload` |
| `/pvpclaimbypass [on\|off\|toggle\|player\|list]` | Schaltet den PvP-Bypass auf Claims für Staff frei *(Aliase: `/claimpvpbypass`, `/pvpclaim`)* | `claims.admin.pvpbypass` |

---

### 🔹 Claim-Flags & Schutzrechte
Jeder Claim-Besitzer kann pro Spieler oder global folgende Rechte vergeben:
* `BUILD`: Blöcke abbauen und platzieren.
* `CONTAINERS`: Kisten, Fässer, Chiseled Bookshelves, Shelves, Braustände, Trichter öffnen.
* `DOORS`: Türen, Falltüren, Zauntore öffnen/schließen.
* `REDSTONE`: Knöpfe, Hebel und Druckplatten betätigen.
* `VEHICLES`: Boote, Loren und Flöße platzieren, fahren oder abbauen.
* `ENTITY_INTERACT`: Rüstungsständer, Bilderrahmen, Glow-Frames, Endkristalle nutzen/abbauen; Enderperlen & Wind Charges werfen.
* `ATTACK_MOBS`: Tiere, Dorfbewohner und passive Mobs schlagen (Monster-Notwehr ist immer erlaubt).
* `BUCKETS`: Wasser-, Lava- und Eimer-Nutzung.
* `SPAWNER`: SmartSpawner GUI öffnen und Mob-Spawner modifizieren/abbauen.
* `FIRE_EXPLOSIONS`: Feuerzeug, TNT-Zündung und Explosionen.
* `PVP`: PvP-Kämpfe innerhalb dieses Claims erlauben.

---

### 🔹 Claims-Berechtigungen

| Permission | Beschreibung | Standard |
| :--- | :--- | :--- |
| `claims.use` | Öffnen des `/claim`-Menüs und Claimen | `true` |
| `claims.pay` | Claim-Slots per `/claim pay` übertragen | `true` |
| `claims.transfer` | Claims im GUI an andere Spieler verschenken | `true` |
| `claims.admin` | Übergeordnete Admin-Berechtigung für `/staffclaim` | `op` |
| `claims.admin.bypass` | Schaltet den Bypass für Baurechte und Banns frei | `op` |
| `claims.admin.bypass.join` | Aktiviert den Admin-Bypass automatisch beim Login | `op` |
| `claims.admin.bypass.other` | Erlaubt `/staffclaim bypass <on\|off> <spieler>` | `op` |
| `claims.admin.pvpbypass` | Erlaubt `/pvpclaimbypass` | `op` |

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
| `/pvptag [Spieler] [Zeit]` | `/combattag`, `/tag` | Zeigt verbleibende Tag-Zeit oder taggt manuell | `pvpmanager.command.tag` |
| `/untag [Spieler\|*]` | - | Entfernt den Combat-Tag von einem oder allen Spielern | `pvpmanager.command.untag` |
| `/teleportoverride [Spieler]`| - | Erlaubt Teleportation trotz Combat-Tag | `pvpmanager.command.teleportoverride` |
| `/pvpdimensiontoggle <Dim> <on\|off> [--force]` | - | Schaltet PvP für eine Dimension permanent ein/aus | `pvpmanager.command.pvpdimensiontoggle` |
| `/announce <Nachricht>` | - | Sendet einen serverweiten Broadcast | `pvpmanager.command.announce` |
| `/soup` | - | Füllt leere Schüsseln im Inventar mit Pilzsuppe | `pvpmanager.command.soup` |
| `/testrace` | - | Debug-Befehl für Entwickler | `pvpmanager.command.testrace` |

---

### 🔹 Wichtige Kampf-Regeln
> [!IMPORTANT]
> **Wildnis-PvP-Schutz:**  
> Außerhalb von Claims (in der Wildnis) ist PvP **ausnahmslos deaktiviert**!  
> PvP-Kämpfe sind ausschließlich gestattet:
> 1. Auf Claims, bei denen beide beteiligten Spieler die `PVP`-Flag aktiviert haben.
> 2. Zwischen Gegnern in einem aktiven **Clankrieg** (`/clan war`).
> 3. Durch autorisiertes Server-Personal mit `/pvpoverride` oder `/pvpclaimbypass`.

* **Combat-Tag:** Bei Schaden im Kampf wird der Spieler getaggt. Befehle, Fliegen, Gamemode und Elytren werden temporär blockiert.
* **Anti-CombatLog:** Verlässt ein Spieler den Server im Kampf, wird er getötet und sein Loot gedroppt.
* **Respawn-Schutz:** Frisch respawnte Spieler erhalten temporären Schutz vor Schaden und Angriffen.

---

### 🔹 PvPManager-Berechtigungen

| Permission | Beschreibung | Standard |
| :--- | :--- | :--- |
| `pvpmanager.command.pvp` | Eigenen PvP-Status umschalten | `true` |
| `pvpmanager.command.pvp.others` | PvP-Status anderer Spieler togglen | `op` |
| `pvpmanager.command.pvpinfo` | Eigene PvP-Info ansehen | `true` |
| `pvpmanager.command.pvplist` | `/pvplist` abrufen | `true` |
| `pvpmanager.command.pvpstatus` | Eigene PvP-Statusabfrage | `true` |
| `pvpmanager.admin` | Voller Zugriff auf alle PvPManager-Befehle | `op` |
| `pvpmanager.bypass.lootprotection` | Loot von getöteten Spielern trotz Schutz aufsammeln | `op` |
| `pvpmanager.exempt.blockcommands` | Befehle auch im Kampf ausführen dürfen | `op` |
| `pvpmanager.exempt.combatlog` | Vor Combat-Log-Bestrafung geschützt | `op` |
| `pvpmanager.exempt.combattag` | Kann generell nicht im Kampf getaggt werden | `op` |
| `pvpmanager.exempt.disableactions` | Fliegen/Gamemode bleiben im Kampf aktiv | `op` |
| `pvpmanager.exempt.respawnprotection` | Kein Respawn-Schutz nach dem Tod | `op` |
| `pvpmanager.exempt.pvptogglecooldown` | Kein Cooldown auf `/pvp` | `op` |

---

## 4. StoneAttack (Leaderboards, PlayerLimit, Performance)

### 🔹 Leaderboard-Befehle (`/leaderboard`)
Verwaltet dynamische Hologramme mit Rüstungsständern für Bestenlisten (Spielzeit, Kills, Tode, Geld).  
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
| `/leaderboard tp <ID>` | Teleportiert den Admin zum ausgewählten Leaderboard |
| `/leaderboard reload` | Lädt die Leaderboard-Konfiguration neu |
| `/leaderboard reset <Typ> <Zeitraum>` | Setzt Ranglisten-Werte zurück (`global`, `month`, `week`) |

---

### 🔹 Dynamisches Spielerlimit (`/playerlimit`)
Ermöglicht flexible Slotbegrenzungen für Events oder Leistungsschutz.  
*Berechtigung:* `playerlimit.use` *(Alias: `/plimit`)*

| Befehl | Beschreibung |
| :--- | :--- |
| `/playerlimit set <Anzahl>` | Setzt das aktuelle dynamische Spielerlimit |
| `/playerlimit get` | Zeigt das aktuell eingestellte Limit an |
| `/playerlimit off` | Deaktiviert das Limit (nutzt Standard `server.properties`) |
| `/playerlimit list` | Listet alle aktuell erfassten Spieler auf |
| `/playerlimit reload` | Lädt das Modul neu |

*Bypass & Staff Permissions:*
* `playerlimit.bypass`: Spieler mit dieser Permission werden nie gekickt und zählen nicht ins Limit.
* `playerlimit.staff`: Erhält Chat-Benachrichtigungen, wenn Spieler aufgrund eines neuen Limits gekickt wurden.

---

### 🔹 Performance & Monitoring (`/performance` / `/tps`)
*Berechtigung:* `performance.use`

Zeigt detaillierte Serverstatistiken in Echtzeit:
* TPS (1m, 5m, 15m) und MSPT (Millisekunden pro Tick).
* RAM-Nutzung (Used, Allocated, Max).
* Geladene Chunks und Entities über alle Dimensionen hinweg.
* Automatische Warnungen bei Performance-Einbrüchen an Spieler mit `performance.staff`.

---

## 5. DeathChest (Todeskisten)

Stirbt ein Spieler, spawnt an der Todesstelle eine sichere Holztruhe (bzw. Doppelkiste), die alle Items und Rüstungsteile schützt.
* **Schutz:** Nur der verstorbene Spieler kann seine Kiste öffnen (oder ein Admin mit Bypass).
* **Dupe- & Crash-Schutz:** Synchronisationssicher gegen Event-Loops bei Inventarschließung.
* **Auto-Despawn:** Nach Ablauf des Timers droppen verbleibende Items sicher am Boden.

### Befehle (`/deathchest`)
*Berechtigung:* `deathchest.admin`

| Befehl | Beschreibung |
| :--- | :--- |
| `/deathchest clear [Spieler\|*]` | Entfernt aktive Todeskisten eines oder aller Spieler |
| `/deathchest reload` | Lädt die Todeskisten-Konfiguration neu |

---

## 6. ClearLag (Item-Bereinigung)

Optimiert die Server-Leistung durch automatisches Entfernen herumliegender Boden-Items in festen Intervallen.

### Befehle (`/clearlag`)
*Berechtigung:* `clearlag.admin`

| Befehl | Beschreibung |
| :--- | :--- |
| `/clearlag clear` | Führt sofort eine manuelle Bereinigung aller Boden-Items durch |
| `/clearlag time` | Zeigt die Restzeit bis zur nächsten automatischen Bereinigung |
| `/clearlag reload` | Lädt Intervalle, Warnungen und Item-Blacklists neu |

---

## 7. PlayRewards (Spielzeit & Streak-Belohnungen)

Belohnt treue Spieler mit täglichen Streaks, Belohnungstruhen und geschenkten Vorteilen.

### Befehle (`/playrewards` / `/pr`)
*Berechtigung:* `playrewards.use`

| Befehl | Beschreibung |
| :--- | :--- |
| `/playrewards` | Öffnet das grafische Belohnungsmenü (Streaks & Playtime) |
| `/playrewards reload` | Lädt Konfiguration und Belohnungstabellen neu (`playrewards.admin`) |

*Zusätzliche Berechtigungen:*
* `playrewards.streak.saver`: Gewährt dem Spieler wöchentliche Streak-Schutzpunkte, falls er einen Tag verpasst.

---

## 8. StoneVoice (VoiceChat-Admin-Tools)

Erweitert das SimpleVoiceChat-Plugin um professionelle Moderations-Werkzeuge für Admins.

### Befehle (`/adminjoinvoice`)
*Berechtigung:* `adminjoinvoice.use`

| Befehl | Beschreibung |
| :--- | :--- |
| `/adminjoinvoice <Spieler>` | Tritt der aktuellen Voicechat-Gruppe eines Spielers passwortlos bei |
| `/adminjoinvoice <Gruppenname>` | Tritt einer spezifischen Voicechat-Gruppe bei |
| `/adminjoinvoice leave` | Verlässt die aktuelle Gruppe wieder |

*Optionale Parameter:*
* `[muted]`: Tritt der Gruppe stumm bei.
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

---

## 12. PlaceholderAPI Gesamtübersicht

Alle Platzhalter erfordern das installierte Plugin **PlaceholderAPI**.

### 🏰 Clan-Platzhalter (`%clansystem_...%` oder `%ztone_...%`)
| Placeholder | Rückgabewert |
| :--- | :--- |
| `%clansystem_clan_name%` | Clan-Name mit Farbcodes |
| `%clansystem_clan_name_raw%` | Clan-Name ohne Farbcodes |
| `%clansystem_clan_tag%` | Clan-Suffix mit Farbe |
| `%clansystem_clan_tag_raw%` | Clan-Suffix als Rohtext |
| `%clansystem_clan_tag_formatted%` | Clan-Suffix in eckigen Klammern: `[&rTag&8]` |
| `%clansystem_clan_level%` | Aktuelles Clan-Level als Zahl |
| `%clansystem_clan_points%` | Gesammelte Clan-Punkte |
| `%clansystem_clan_rank%` | Eigener Rang im Clan (farbig) |
| `%clansystem_clan_rank_raw%` | Eigener Rang im Clan (unformatiert) |
| `%clansystem_clan_membercount%` | Anzahl der Mitglieder im Clan |
| `%clansystem_clan_maxmembers%` | Maximale Mitgliederanzahl für das aktuelle Level |
| `%clansystem_clan_balance%` | Guthaben der Clankasse (z. B. `1500.50`) |
| `%clansystem_clan_debt%` | Aktuelle Schulden des Clans |
| `%clansystem_in_clan%` | `true`, wenn im Clan, sonst `false` |
| `%clansystem_clan_leader%` | Name des Clan-Leaders |
| `%clansystem_clan_allies%` | Anzahl verbündeter Clans |
| `%clansystem_clan_wars%` | Anzahl aktiver Clankriege |
| `%clansystem_can_pvp%` | Gibt an, ob der Spieler aktuell im PvP kämpfen darf |
| `%clansystem_dimension_pvp%` | Gibt an, ob PvP in der aktuellen Welt prinzipiell an ist |

---

### 🗺️ Claims-Platzhalter (`%claims_...%`)
| Placeholder | Rückgabewert |
| :--- | :--- |
| `%claims_current_claim%` | Besitzer des aktuellen Claims, auf dem der Spieler steht |
| `%claims_current_name%` | Name des aktuellen Claims |
| `%claims_used%` | Anzahl der aktuell belegten Claim-Chunks |
| `%claims_max%` | Maximale Claim-Slots des Spielers |
| `%claims_free%` | Noch ungenutzte, freie Claim-Slots |
| `%claims_bonus%` | Anzahl erhaltener Bonus-Claim-Slots |
| `%claims_is_in_claim%` | `true`, wenn sich der Spieler auf einem Claim befindet |
| `%claims_is_owner%` | `true`, wenn der Spieler der Besitzer des aktuellen Claims ist |
| `%claims_is_trusted%` | `true`, wenn der Spieler auf dem Claim Baurechte besitzt |

---

### ⚔️ PvPManager-Platzhalter (`%pvpmanager_...%`)
| Placeholder | Rückgabewert |
| :--- | :--- |
| `%pvpmanager_pvp%` | Zeigt den PvP-Status an (`✓` oder `✗`) |
| `%pvpmanager_combat_status%` | `im Kampf` oder `nicht im Kampf` |
| `%pvpmanager_combat_time%` | Verbleibende Sekunden im Combat-Tag |
| `%pvpmanager_current_enemy%` | Name des aktuellen Kampfgegners |
| `%pvpmanager_current_enemy_health%`| Lebenspunkte des Gegners |
| `%pvpmanager_respawn_protected%` | Zeigt an, ob Respawn-Schutz aktiv ist |
| `%pvpmanager_respawn_time%` | Verbleibende Sekunden des Respawn-Schutzes |

---

### 🏆 StoneAttack & Leaderboard-Platzhalter (`%stoneattack_...%`)
| Placeholder | Rückgabewert |
| :--- | :--- |
| `%stoneattack_playerlimit_max%` | Aktuell erlaubtes Spielerlimit |
| `%stoneattack_playerlimit_current%`| Anzahl online befindlicher Spieler |
| `%stoneattack_playerlimit_free%` | Freie Plätze bis zum Erreichen des Limits |
| `%stoneattack_leaderboard_<typ>_<zeitraum>_rank%` | Eigene Platzierung im Leaderboard |
| `%stoneattack_leaderboard_<typ>_<zeitraum>_value%` | Eigener Wert im Leaderboard |
| `%stoneattack_leaderboard_<typ>_<zeitraum>_top<N>_name%` | Name des Spielers auf Platz `<N>` |
| `%stoneattack_leaderboard_<typ>_<zeitraum>_top<N>_value%`| Formatierter Wert des Spielers auf Platz `<N>` |
| `%stoneattack_leaderboard_<typ>_<zeitraum>_top<N>_clan%` | Clan-Name des Spielers auf Platz `<N>` |
| `%stoneattack_leaderboard_<typ>_<zeitraum>_top<N>_clantag%`| Clan-Suffix des Spielers auf Platz `<N>` |

*Typen:* `playtime`, `kills`, `deaths`, `money`  
*Zeiträume:* `global`, `month`, `week`  
*Beispiel:* `%stoneattack_leaderboard_playtime_global_top1_name%`

---

### 🎁 PlayRewards-Platzhalter (`%playrewards_...%`)
| Placeholder | Rückgabewert |
| :--- | :--- |
| `%playrewards_streak%` | Aktuelle Anzahl aufeinanderfolgender Tage (Streak) |
| `%playrewards_streak_claimed_today%`| `true`, wenn heute bereits abgeholt |
| `%playrewards_playtime_seconds%` | Reine Spielzeit in Sekunden |
| `%playrewards_playtime_hours%` | Reine Spielzeit in vollen Stunden |
| `%playrewards_playtime_days%` | Reine Spielzeit in vollen Tagen |
| `%playrewards_playtime_formatted%`| Formatierte Spielzeit (z. B. `12h 30m`) |
| `%playrewards_playtime_detailed%` | Detaillierte Spielzeit (z. B. `5 Tage, 8 Stunden`) |
| `%playrewards_daily_available%` | Anzahl abholbarer Belohnungen |
| `%playrewards_next_reward_time%` | Countdown bis Mitternacht |
| `%playrewards_streak_saver_points%`| Verbleibende Streak-Schutzpunkte |

---

### 🧹 ClearLag-Platzhalter (`%clearlag_...%`)
| Placeholder | Rückgabewert |
| :--- | :--- |
| `%clearlag_countdown%` | Kleingeschriebener Countdown (z. B. `02:45`) |
| `%clearlag_digital%` | Digitaler Countdown (`mm:ss`) |
| `%clearlag_seconds%` | Verbleibende Sekunden bis zum nächsten Clear |
| `%clearlag_minutes%` | Verbleibende Minuten bis zum nächsten Clear |

---

## 🛠️ Kompilierung & Entwicklung

* **Core:** Paper / Leaf API 26.2 (kompatibel mit 1.21.x)
* **Java:** Mindestens Java 21 LTS (kompatibel bis Java 25)
* **Build-Tool:** Gradle mit ShadowJar

```bash
# Projekt mit Gradle bauen:
gradlew build shadowJar
```
Das fertige Artefakt befindet sich unter `build/libs/Ztone-1.0.2.jar`.
