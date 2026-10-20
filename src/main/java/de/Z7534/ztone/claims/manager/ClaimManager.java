package de.Z7534.ztone.claims.manager;

import de.Z7534.ztone.claims.config.ConfigManager;
import de.Z7534.ztone.claims.database.ClaimRepository;
import de.Z7534.ztone.claims.database.PlayerRepository;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.model.ChunkPos;
import de.Z7534.ztone.claims.model.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClaimManager {

    private static final Pattern START_PERM = Pattern.compile("^claims\\.start\\.(\\d+)$");

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final ClaimRepository claimRepository;
    private final PlayerRepository playerRepository;

    // Concurrent, weil PlaceholderAPI/TAB und der Spieler-Lookup auch von Async-Threads lesen.
    private final Map<Long, Claim> claimsById = new ConcurrentHashMap<>();
    private final Map<ChunkPos, Claim> chunkIndex = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerData> playerData = new ConcurrentHashMap<>();
    private final BypassManager bypass;

    public ClaimManager(JavaPlugin plugin, ConfigManager config,
                        ClaimRepository claimRepository, PlayerRepository playerRepository,
                        BypassManager bypass) {
        this.bypass = bypass;
        this.plugin = plugin;
        this.config = config;
        this.claimRepository = claimRepository;
        this.playerRepository = playerRepository;
    }

    public void loadAll() {
        claimsById.clear();
        chunkIndex.clear();
        for (Claim claim : claimRepository.loadAll()) {
            index(claim);
        }
        playerData.clear();
        playerData.putAll(playerRepository.loadAll());
        plugin.getLogger().info("Geladen: " + claimsById.size() + " Claims, " + playerData.size() + " Spieler-Datensätze.");
    }

    private void index(Claim claim) {
        claimsById.put(claim.id(), claim);
        for (ChunkPos pos : claim.chunks()) {
            chunkIndex.put(pos, claim);
        }
    }

    private void unindex(Claim claim) {
        claimsById.remove(claim.id());
        for (ChunkPos pos : claim.chunks()) {
            chunkIndex.remove(pos, claim);
        }
    }

    public PlayerData getOrCreatePlayerData(UUID uuid, String name) {
        PlayerData pd = playerData.get(uuid);
        if (pd == null) {
            pd = new PlayerData(uuid, name);
            playerData.put(uuid, pd);
            savePlayerAsync(pd);
        } else if (!pd.name().equalsIgnoreCase(name)) {
            pd.setName(name);
        }
        return pd;
    }

    public Optional<PlayerData> findPlayerData(UUID uuid) {
        return Optional.ofNullable(playerData.get(uuid));
    }

    public Optional<PlayerData> findPlayerDataByNameBlocking(String name) {
        for (PlayerData pd : playerData.values()) {
            if (pd.name().equalsIgnoreCase(name)) {
                return Optional.of(pd);
            }
        }
        return playerRepository.findByName(name);
    }

    public void ensurePlayerData(UUID uuid, String name) {
        if (!playerData.containsKey(uuid)) {
            getOrCreatePlayerData(uuid, name);
        }
    }

    private void async(Runnable task) {
        claimRepository.async(task);
    }

    public void savePlayerAsync(PlayerData pd) {
        final PlayerData snapshot = pd.copy();
        async(() -> playerRepository.upsert(snapshot));
    }

    public void savePlayerSync(PlayerData pd) {
        playerRepository.upsert(pd);
    }

    public int getMaxClaimSlots(Player player) {
        if (config.bypassIgnoresClaimLimit() && bypass.isActive(player)) {
            return Integer.MAX_VALUE;
        }
        int max = 0;
        for (org.bukkit.permissions.PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) continue;
            Matcher m = START_PERM.matcher(info.getPermission());
            if (m.matches()) {
                try {
                    max = Math.max(max, Integer.parseInt(m.group(1)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        PlayerData pd = getOrCreatePlayerData(player.getUniqueId(), player.getName());
        return max + pd.bonusClaimSlots();
    }

    public int getUsedClaimSlots(UUID owner) {
        int used = 0;
        for (Claim c : claimsById.values()) {
            if (c.owner().equals(owner)) {
                used += c.chunks().size();
            }
        }
        return used;
    }

    /**
     * Wie viele Claim-Slots ein Spieler aktuell frei (unbelegt) hat.
     * Kann negativ werden nur wenn er durch Admin-Eingriffe über sein Limit gebracht wurde,
     * daher an den Aufrufstellen immer mit Math.max(0, ...) absichern.
     */
    public int getFreeClaimSlots(Player player) {
        int max = getMaxClaimSlots(player);
        if (max == Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return max - getUsedClaimSlots(player.getUniqueId());
    }

    public enum PayResult {
        OK, INVALID_AMOUNT, SELF, NOT_ENOUGH_FREE, NOT_ENOUGH_OWN
    }

    /**
     * Prüft, ob ein Spieler eine bestimmte Anzahl seiner Claim-Slots an einen anderen
     * Spieler payen/verschenken darf. Zwei unabhängige Fallbacks:
     *  - NOT_ENOUGH_FREE: er müsste dafür unter seine bereits belegten Slots fallen
     *    (Beispiel: 100 Claims, 80 belegt -> nur 20 frei, 30 payen geht nicht).
     *  - NOT_ENOUGH_OWN: er besitzt gar nicht so viele "eigene" (per /staffclaim
     *    addclaims, Belohnung o.ä. erhaltene) Bonus-Slots. Slots aus Permissions
     *    (claims.start.<n>) gehören ihm nicht wirklich und können nicht verschenkt
     *    werden - sonst könnte man mit aktivem Bypass beliebig Slots "drucken".
     */
    public PayResult validatePay(Player sender, UUID target, int amount) {
        if (amount <= 0) {
            return PayResult.INVALID_AMOUNT;
        }
        if (sender.getUniqueId().equals(target)) {
            return PayResult.SELF;
        }
        int free = getFreeClaimSlots(sender);
        if (free < amount) {
            return PayResult.NOT_ENOUGH_FREE;
        }
        PlayerData senderData = getOrCreatePlayerData(sender.getUniqueId(), sender.getName());
        if (senderData.bonusClaimSlots() < amount) {
            return PayResult.NOT_ENOUGH_OWN;
        }
        return PayResult.OK;
    }

    /**
     * Führt die vorher mit validatePay geprüfte Zahlung aus. Muss unmittelbar nach
     * einer erfolgreichen validatePay-Prüfung (OK) aufgerufen werden.
     */
    public void executePay(Player sender, UUID targetUuid, String targetName, int amount) {
        PlayerData senderData = getOrCreatePlayerData(sender.getUniqueId(), sender.getName());
        senderData.setBonusClaimSlots(senderData.bonusClaimSlots() - amount);
        savePlayerAsync(senderData);

        ensurePlayerData(targetUuid, targetName);
        PlayerData targetData = findPlayerData(targetUuid).orElseThrow();
        long newBonus = Math.min(Integer.MAX_VALUE, (long) targetData.bonusClaimSlots() + amount);
        targetData.setBonusClaimSlots((int) newBonus);
        savePlayerAsync(targetData);
    }

    public enum TransferResult {
        OK, SELF, CLAIM_GONE, NOT_ENOUGH_SPACE
    }

    /**
     * Prüft, ob ein Claim aktuell an newOwner übertragen werden darf. newOwner muss
     * online sein (getMaxClaimSlots braucht die aktuellen Berechtigungen des Spielers).
     */
    public TransferResult validateTransfer(Claim claim, Player newOwner) {
        Claim current = claimsById.get(claim.id());
        if (current == null) {
            return TransferResult.CLAIM_GONE;
        }
        if (current.owner().equals(newOwner.getUniqueId())) {
            return TransferResult.SELF;
        }
        int needed = current.chunks().size();
        int free = getFreeClaimSlots(newOwner);
        if (free < needed) {
            return TransferResult.NOT_ENOUGH_SPACE;
        }
        return TransferResult.OK;
    }

    /**
     * Überträgt einen Claim an einen neuen Besitzer. Muss unmittelbar nach einer
     * erfolgreichen validateTransfer-Prüfung (OK) aufgerufen werden. Behält Name,
     * Chunks, Trust-/Ban-Liste und Besucherrechte des Claims unverändert bei -
     * es ändert sich nur der Besitzer.
     */
    public boolean transferClaim(Claim claim, UUID newOwner, String newOwnerName) {
        Claim current = claimsById.get(claim.id());
        if (current == null) {
            return false;
        }
        ensurePlayerData(newOwner, newOwnerName);
        current.setOwner(newOwner);
        // Der neue Besitzer darf weder als Trust noch als Ban-Eintrag auf seinem eigenen Claim stehen.
        current.trusted().remove(newOwner);
        current.banned().remove(newOwner);
        async(() -> {
            claimRepository.updateOwner(current.id(), newOwner);
            claimRepository.removeTrust(current.id(), newOwner);
            claimRepository.removeBan(current.id(), newOwner);
        });
        return true;
    }

    public Claim getClaimAt(ChunkPos pos) {
        return chunkIndex.get(pos);
    }

    public Claim getClaimAt(Location loc) {
        if (loc.getWorld() == null) return null;
        return getClaimAt(new ChunkPos(loc.getWorld().getName(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4));
    }

    public Claim getById(long id) {
        return claimsById.get(id);
    }

    /**
     * Liefert den Claim nur, wenn er noch existiert UND dem Spieler gehört. Schützt GUI-Aktionen
     * vor veraltetem Zustand (z.B. Claim wurde zwischenzeitlich übertragen oder gelöscht).
     */
    public Claim getOwned(UUID player, long id) {
        Claim claim = claimsById.get(id);
        return (claim != null && (claim.owner().equals(player) || claim.isManager(player))) ? claim : null;
    }

    public List<Claim> claimsManagedBy(UUID player) {
        List<Claim> list = new ArrayList<>();
        for (Claim c : claimsById.values()) {
            if (c.isManager(player)) {
                list.add(c);
            }
        }
        return list;
    }

    public Set<Claim> getClaimsWithinRadius(Location loc, int radius) {
        Set<Claim> result = new HashSet<>();
        if (loc.getWorld() == null || radius < 0) return result;
        String world = loc.getWorld().getName();
        int minChunkX = (loc.getBlockX() - radius) >> 4;
        int maxChunkX = (loc.getBlockX() + radius) >> 4;
        int minChunkZ = (loc.getBlockZ() - radius) >> 4;
        int maxChunkZ = (loc.getBlockZ() + radius) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                Claim claim = chunkIndex.get(new ChunkPos(world, cx, cz));
                if (claim == null || result.contains(claim)) continue;
                if (chunkWithinRadius(cx, cz, loc, radius)) {
                    result.add(claim);
                }
            }
        }
        return result;
    }

    private boolean chunkWithinRadius(int chunkX, int chunkZ, Location loc, int radius) {
        double minX = chunkX << 4;
        double maxX = minX + 16;
        double minZ = chunkZ << 4;
        double maxZ = minZ + 16;
        double closestX = Math.max(minX, Math.min(loc.getX(), maxX));
        double closestZ = Math.max(minZ, Math.min(loc.getZ(), maxZ));
        double dx = closestX - loc.getX();
        double dz = closestZ - loc.getZ();
        return (dx * dx + dz * dz) <= (double) radius * radius;
    }

    public List<Claim> claimsOf(UUID owner) {
        List<Claim> result = new ArrayList<>();
        for (Claim c : claimsById.values()) {
            if (c.owner().equals(owner)) {
                result.add(c);
            }
        }
        result.sort(Comparator.comparingLong(Claim::id));
        return result;
    }

    public Collection<Claim> allClaims() {
        return claimsById.values();
    }

    public Set<String> getKnownPlayerNames() {
        Set<String> names = new HashSet<>();
        for (PlayerData pd : playerData.values()) {
            names.add(pd.name());
        }
        return names;
    }

    public Collection<PlayerData> getAllPlayerData() {
        return playerData.values();
    }

    public boolean isSpawnProtected(ChunkPos pos) {
        if (!config.spawnProtectionEnabled()) return false;
        if (config.spawnRadius() <= 0) return false;
        if (!pos.world().equalsIgnoreCase(config.spawnWorld())) return false;
        return isWithinRadius(pos, config.spawnX(), config.spawnZ(), config.spawnRadius());
    }

    public boolean isEndProtected(ChunkPos pos) {
        if (!config.endProtectionEnabled()) return false;
        if (config.endProtectionRadius() <= 0) return false;
        if (!pos.world().equalsIgnoreCase(config.endProtectionWorld())) return false;
        return isWithinRadius(pos, config.endProtectionX(), config.endProtectionZ(), config.endProtectionRadius());
    }

    private boolean isWithinRadius(ChunkPos pos, double centerX, double centerZ, double radius) {
        double chunkMinX = pos.x() << 4;
        double chunkMinZ = pos.z() << 4;
        double closestX = Math.max(chunkMinX, Math.min(centerX, chunkMinX + 16));
        double closestZ = Math.max(chunkMinZ, Math.min(centerZ, chunkMinZ + 16));
        double dx = closestX - centerX;
        double dz = closestZ - centerZ;
        return (dx * dx + dz * dz) <= (radius * radius);
    }

    public boolean isDimensionEnabled(String world) {
        return config.dimensionEnabled(world);
    }

    public enum Result {
        OK, NOT_ENOUGH_SLOTS, SPAWN_PROTECTED, END_PROTECTED, DIMENSION_DISABLED, FOREIGN_CHUNK, NOTHING_SELECTED
    }

    public Result validate(Player player, Set<ChunkPos> toClaim, Set<ChunkPos> toUnclaim) {
        if (toClaim.isEmpty() && toUnclaim.isEmpty()) {
            return Result.NOTHING_SELECTED;
        }
        UUID uuid = player.getUniqueId();
        boolean bypassActive = bypass.isActive(player);

        int newChunks = 0;
        for (ChunkPos pos : toClaim) {
            Claim existing = getClaimAt(pos);
            // Fremde Chunks darf niemand überschreiben - auch nicht mit Bypass. Die Auswahl kann
            // veraltet sein (jemand anderes hat den Chunk in der Zwischenzeit geclaimt).
            if (existing != null && !existing.owner().equals(uuid)) {
                return Result.FOREIGN_CHUNK;
            }
            if (!bypassActive) {
                if (!isDimensionEnabled(pos.world())) return Result.DIMENSION_DISABLED;
                if (isSpawnProtected(pos)) return Result.SPAWN_PROTECTED;
                if (isEndProtected(pos)) return Result.END_PROTECTED;
            }
            if (existing == null) {
                newChunks++;
            }
        }

        // Nur Chunks zählen, die dem Spieler wirklich noch gehören.
        int released = 0;
        for (ChunkPos pos : toUnclaim) {
            Claim existing = getClaimAt(pos);
            if (existing != null && existing.owner().equals(uuid)) {
                released++;
            }
        }

        int used = getUsedClaimSlots(uuid);
        int after = used - released + newChunks;
        int max = getMaxClaimSlots(player);
        // Nur ablehnen, wenn der Spieler dadurch mehr Slots belegt als vorher. Wer durch
        // Admin-Eingriffe über dem Limit liegt, muss trotzdem Chunks freigeben können.
        if (after > max && after > used) {
            return Result.NOT_ENOUGH_SLOTS;
        }
        return Result.OK;
    }

    public void apply(Player player, Set<ChunkPos> toClaim, Set<ChunkPos> toUnclaim) {
        UUID owner = player.getUniqueId();
        if (!toUnclaim.isEmpty()) {
            unclaimChunks(owner, toUnclaim);
        }
        if (!toClaim.isEmpty()) {
            claimChunks(owner, toClaim);
        }
    }

    private void claimChunks(UUID owner, Set<ChunkPos> newChunks) {

        Map<String, Set<ChunkPos>> byWorld = new HashMap<>();
        for (ChunkPos p : newChunks) {
            byWorld.computeIfAbsent(p.world(), w -> new HashSet<>()).add(p);
        }
        for (Map.Entry<String, Set<ChunkPos>> entry : byWorld.entrySet()) {
            claimChunksInWorld(owner, entry.getKey(), entry.getValue());
        }
    }

    private void claimChunksInWorld(UUID owner, String world, Set<ChunkPos> newChunks) {
        Set<ChunkPos> ownerChunks = new HashSet<>();
        for (Claim c : claimsById.values()) {
            if (c.owner().equals(owner) && c.world().equals(world)) {
                ownerChunks.addAll(c.chunks());
            }
        }
        Set<ChunkPos> universe = new HashSet<>(ownerChunks);
        universe.addAll(newChunks);

        Set<ChunkPos> handled = new HashSet<>();
        for (ChunkPos start : newChunks) {
            if (handled.contains(start)) continue;
            Set<ChunkPos> component = floodFill(universe, start);
            handled.addAll(component);

            List<Claim> touched = new ArrayList<>();
            for (Claim c : claimsById.values()) {
                if (c.owner().equals(owner) && c.world().equals(world) && !Collections.disjoint(c.chunks(), component)) {
                    touched.add(c);
                }
            }

            if (touched.isEmpty()) {
                Claim claim = new Claim(-1, owner, generateClaimName(owner), world);
                claim.chunks().addAll(component);
                long id = claimRepositoryInsert(claim);
                if (id < 0) {
                    plugin.getLogger().severe("Claim konnte nicht in der Datenbank gespeichert werden, "
                            + component.size() + " Chunk(s) wurden NICHT geclaimt.");
                    continue;
                }
                claim.setId(id);
                index(claim);
            } else {
                touched.sort(Comparator.comparingInt(c -> -c.chunks().size()));
                Claim survivor = touched.get(0);
                unindex(survivor);
                survivor.chunks().clear();
                survivor.chunks().addAll(component);
                for (int i = 1; i < touched.size(); i++) {
                    Claim victim = touched.get(i);
                    mergeAccessLists(survivor, victim);
                    unindex(victim);
                    claimsById.remove(victim.id());
                    final long victimId = victim.id();
                    async(() -> claimRepository.deleteClaim(victimId));
                }
                index(survivor);
                // Kopie HIER (Server-Thread) anlegen, nicht im Async-Thread - sonst droht eine
                // ConcurrentModificationException, wenn der Claim währenddessen geändert wird.
                final long survivorId = survivor.id();
                final Set<ChunkPos> survivorChunks = new HashSet<>(survivor.chunks());
                async(() -> claimRepository.replaceChunks(survivorId, survivorChunks));
            }
        }
    }

    /**
     * Übernimmt Trust- und Ban-Liste eines aufgelösten Claims in den überlebenden Claim.
     * Regeln: Ban hat Vorrang vor Trust (wer in einem der Claims gebannt ist, ist danach im
     * zusammengeführten Claim gebannt); bei Trust in beiden Claims gelten die Rechte beider
     * Claims zusammen.
     */
    private void mergeAccessLists(Claim survivor, Claim victim) {
        UUID owner = survivor.owner();
        List<UUID> newBans = new ArrayList<>();
        Map<UUID, Integer> trustChanges = new HashMap<>();
        List<UUID> removedTrust = new ArrayList<>();

        for (UUID banned : victim.banned()) {
            if (banned.equals(owner)) continue;
            if (survivor.banned().add(banned)) {
                newBans.add(banned);
            }
            if (survivor.trusted().remove(banned) != null) {
                removedTrust.add(banned);
            }
        }

        for (Map.Entry<UUID, de.Z7534.ztone.claims.model.TrustedPlayer> entry : victim.trusted().entrySet()) {
            UUID uuid = entry.getKey();
            if (uuid.equals(owner) || survivor.banned().contains(uuid)) continue;
            int mask = entry.getValue().permissionMask();
            de.Z7534.ztone.claims.model.TrustedPlayer existing = survivor.trusted().get(uuid);
            if (existing == null) {
                survivor.trusted().put(uuid, new de.Z7534.ztone.claims.model.TrustedPlayer(uuid, mask));
                trustChanges.put(uuid, mask);
            } else {
                int merged = existing.permissionMask() | mask;
                if (merged != existing.permissionMask()) {
                    existing.setPermissionMask(merged);
                    trustChanges.put(uuid, merged);
                }
            }
        }

        final long survivorId = survivor.id();
        async(() -> {
            for (UUID uuid : removedTrust) claimRepository.removeTrust(survivorId, uuid);
            for (UUID uuid : newBans) claimRepository.setBan(survivorId, uuid);
            for (Map.Entry<UUID, Integer> e : trustChanges.entrySet()) {
                claimRepository.setTrust(survivorId, e.getKey(), e.getValue());
            }
        });
    }

    private long claimRepositoryInsert(Claim claim) {

        return claimRepository.insertClaim(claim);
    }

    private void unclaimChunks(UUID owner, Set<ChunkPos> toRemove) {
        Map<Long, Set<ChunkPos>> byClaim = new HashMap<>();
        for (ChunkPos pos : toRemove) {
            Claim claim = getClaimAt(pos);
            if (claim != null && claim.owner().equals(owner)) {
                byClaim.computeIfAbsent(claim.id(), id -> new HashSet<>()).add(pos);
            }
        }
        for (Map.Entry<Long, Set<ChunkPos>> entry : byClaim.entrySet()) {
            Claim claim = claimsById.get(entry.getKey());
            if (claim == null) continue;
            unindex(claim);
            claim.chunks().removeAll(entry.getValue());

            if (claim.chunks().isEmpty()) {
                claimsById.remove(claim.id());
                final long id = claim.id();
                async(() -> claimRepository.deleteClaim(id));
                continue;
            }

            List<Set<ChunkPos>> components = splitIntoComponents(claim.chunks());
            components.sort((a, b) -> b.size() - a.size());

            claim.chunks().clear();
            claim.chunks().addAll(components.get(0));
            index(claim);
            final long keptId = claim.id();
            final Set<ChunkPos> keptChunks = new HashSet<>(claim.chunks());
            async(() -> claimRepository.replaceChunks(keptId, keptChunks));

            for (int i = 1; i < components.size(); i++) {
                Claim split = new Claim(-1, claim.owner(), generateClaimName(claim.owner()), claim.world());
                split.chunks().addAll(components.get(i));
                split.setVisitorPermissionMask(claim.visitorPermissionMask());
                claim.trusted().forEach((uuid, tp) -> split.trusted().put(uuid,
                        new de.Z7534.ztone.claims.model.TrustedPlayer(uuid, tp.permissionMask())));
                split.banned().addAll(claim.banned());
                long id = claimRepository.insertClaim(split);
                if (id < 0) {
                    plugin.getLogger().severe("Abgetrennter Claim konnte nicht gespeichert werden, "
                            + components.get(i).size() + " Chunk(s) gehen verloren.");
                    continue;
                }
                split.setId(id);
                index(split);
                // Schnappschüsse im Server-Thread erstellen, der Async-Thread liest nur die Kopien.
                final long splitId = id;
                final Map<UUID, Integer> trustSnapshot = new HashMap<>();
                split.trusted().forEach((uuid, tp) -> trustSnapshot.put(uuid, tp.permissionMask()));
                final Set<UUID> banSnapshot = new HashSet<>(split.banned());
                final int visitorMask = split.visitorPermissionMask();
                async(() -> {
                    for (Map.Entry<UUID, Integer> t : trustSnapshot.entrySet()) {
                        claimRepository.setTrust(splitId, t.getKey(), t.getValue());
                    }
                    for (UUID banned : banSnapshot) {
                        claimRepository.setBan(splitId, banned);
                    }
                    claimRepository.updateVisitorMask(splitId, visitorMask);
                });
            }
        }
    }

    private Set<ChunkPos> floodFill(Set<ChunkPos> universe, ChunkPos start) {
        Set<ChunkPos> component = new HashSet<>();
        Deque<ChunkPos> queue = new ArrayDeque<>();
        queue.add(start);
        component.add(start);
        while (!queue.isEmpty()) {
            ChunkPos cur = queue.poll();
            for (ChunkPos n : cur.neighbours()) {
                if (universe.contains(n) && !component.contains(n)) {
                    component.add(n);
                    queue.add(n);
                }
            }
        }
        return component;
    }

    private List<Set<ChunkPos>> splitIntoComponents(Set<ChunkPos> chunks) {
        List<Set<ChunkPos>> components = new ArrayList<>();
        Set<ChunkPos> remaining = new HashSet<>(chunks);
        while (!remaining.isEmpty()) {
            ChunkPos start = remaining.iterator().next();
            Set<ChunkPos> component = floodFill(remaining, start);
            components.add(component);
            remaining.removeAll(component);
        }
        return components;
    }

    private String generateClaimName(UUID owner) {
        Set<String> existing = new HashSet<>();
        for (Claim c : claimsById.values()) {
            if (c.owner().equals(owner)) existing.add(c.name());
        }
        int n = 1;
        String candidate;
        do {
            candidate = "Claim #" + n;
            n++;
        } while (existing.contains(candidate));
        return candidate;
    }

    public void renameClaim(Claim claim, String newName) {
        claim.setName(newName);
        async(() -> claimRepository.updateName(claim.id(), newName));
    }

    public void deleteClaim(Claim claim) {
        unindex(claim);
        claimsById.remove(claim.id());
        async(() -> claimRepository.deleteClaim(claim.id()));
    }

    public boolean unclaimSingleChunk(ChunkPos pos) {
        Claim claim = getClaimAt(pos);
        if (claim == null) {
            return false;
        }
        unclaimChunks(claim.owner(), Set.of(pos));
        return true;
    }

    public void setTrust(Claim claim, UUID player, int mask) {
        de.Z7534.ztone.claims.model.TrustedPlayer tp = claim.trusted().get(player);
        if (tp == null) {
            tp = new de.Z7534.ztone.claims.model.TrustedPlayer(player, mask);
            claim.trusted().put(player, tp);
        } else {
            tp.setPermissionMask(mask);
        }
        claim.banned().remove(player);
        final int finalMask = mask;
        async(() -> {
            claimRepository.setTrust(claim.id(), player, finalMask);
            claimRepository.removeBan(claim.id(), player);
        });
    }

    public void removeTrust(Claim claim, UUID player) {
        claim.trusted().remove(player);
        async(() -> claimRepository.removeTrust(claim.id(), player));
    }

    public void setBan(Claim claim, UUID player) {
        if (claim.isOwner(player)) {
            return;
        }
        claim.trusted().remove(player);
        claim.banned().add(player);
        async(() -> {
            claimRepository.removeTrust(claim.id(), player);
            claimRepository.setBan(claim.id(), player);
        });
    }

    public void removeBan(Claim claim, UUID player) {
        claim.banned().remove(player);
        async(() -> claimRepository.removeBan(claim.id(), player));
    }

    public void setVisitorMask(Claim claim, int mask) {
        claim.setVisitorPermissionMask(mask);
        async(() -> claimRepository.updateVisitorMask(claim.id(), mask));
    }

    public void setClanTrust(Claim claim, boolean enabled) {
        claim.setClanTrustEnabled(enabled);
        async(() -> claimRepository.setClanTrust(claim.id(), enabled));
    }

    public void setAllyTrust(Claim claim, boolean enabled) {
        claim.setAllyTrustEnabled(enabled);
        async(() -> claimRepository.setAllyTrust(claim.id(), enabled));
    }

    public boolean toggleManager(Claim claim, UUID player) {
        boolean nowManager;
        if (claim.isManager(player)) {
            claim.managers().remove(player);
            async(() -> claimRepository.removeManager(claim.id(), player));
            nowManager = false;
        } else {
            claim.managers().add(player);
            async(() -> claimRepository.setManager(claim.id(), player));
            nowManager = true;
        }
        return nowManager;
    }
}
