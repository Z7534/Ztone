package de.Z7534.ztone.dragonegg.manager;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.dragonegg.config.DragonEggConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet das Tracking von Spielern, die das gesicherte Drachenei im Inventar tragen.
 * <p>
 * Aufgaben:
 * <ul>
 *     <li>Tiefes Durchsuchen von Hauptinventar, Rüstung, Offhand, Cursor, Shulker-Kisten und Bundles.</li>
 *     <li>Permanentes Glowing für Träger (für alle sichtbar).</li>
 *     <li>Verhinderung des Claim-Zutritts bzw. sofortige Evakuierung per {@link Ztone#evictFromClaimIfRestricted(Player)}.</li>
 *     <li>Periodischer 20-Tick Sicherheits-Scan für alle Online-Spieler.</li>
 * </ul>
 */
public class DragonEggManager {

    private final Ztone plugin;
    private final DragonEggConfig config;
    private final DragonEggBypassManager bypassManager;

    private final Set<UUID> eggHolders = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastClaimWarning = new ConcurrentHashMap<>();
    private BukkitTask scanTask;

    public DragonEggManager(Ztone plugin, DragonEggConfig config, DragonEggBypassManager bypassManager) {
        this.plugin = plugin;
        this.config = config;
        this.bypassManager = bypassManager;
    }

    public void start() {
        // Periodischer Check alle 20 Ticks (1 Sekunde)
        this.scanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scanAllOnlinePlayers, 20L, 20L);
    }

    public void stop() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        for (UUID uuid : eggHolders) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.setGlowing(false);
            }
        }
        eggHolders.clear();
        lastClaimWarning.clear();
    }

    public void scanAllOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player);
        }
    }

    /**
     * Prüft das gesamte Inventar eines Spielers auf das gesicherte Drachenei.
     */
    public boolean checkHoldsEgg(Player player) {
        if (player == null || !player.isOnline()) return false;

        // 1. Cursor prüfen (z.B. während Inventar offen ist)
        try {
            ItemStack cursor = player.getItemOnCursor();
            if (isOrContainsEgg(cursor)) {
                return true;
            }
        } catch (Throwable ignored) {}

        try {
            if (player.getOpenInventory() != null) {
                ItemStack cursor = player.getOpenInventory().getCursor();
                if (isOrContainsEgg(cursor)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        // 2. Mainhand & Offhand
        try {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (isOrContainsEgg(mainHand)) {
                return true;
            }
        } catch (Throwable ignored) {}

        try {
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (isOrContainsEgg(offHand)) {
                return true;
            }
        } catch (Throwable ignored) {}

        // 3. Storage contents (Slots 0-35)
        try {
            ItemStack[] storage = player.getInventory().getStorageContents();
            if (storage != null) {
                for (ItemStack item : storage) {
                    if (isOrContainsEgg(item)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 4. Alle Inventar-Slots inkl. Rüstung & Extra
        try {
            for (ItemStack item : player.getInventory().getContents()) {
                if (isOrContainsEgg(item)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    private boolean isOrContainsEgg(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        // Direkt das Drachenei?
        if (config.isSecuredEgg(item)) {
            return true;
        }

        // Shulker-Box im Inventar?
        if (item.getItemMeta() instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox box) {
            for (ItemStack content : box.getInventory().getContents()) {
                if (config.isSecuredEgg(content)) {
                    return true;
                }
            }
        }

        // Bundle (Bündel) im Inventar?
        if (item.getItemMeta() instanceof BundleMeta bundleMeta) {
            for (ItemStack content : bundleMeta.getItems()) {
                if (config.isSecuredEgg(content)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Aktualisiert den Status eines Spielers und führt Glowing / Claim-Evakuierung aus.
     */
    public void updatePlayer(Player player) {
        if (player == null || !player.isOnline()) return;

        boolean holdsEgg = checkHoldsEgg(player);
        UUID uuid = player.getUniqueId();
        boolean wasHolder = eggHolders.contains(uuid);

        if (holdsEgg) {
            if (!wasHolder) {
                eggHolders.add(uuid);
                player.sendMessage(config.getMessage("egg-obtained"));
            }

            boolean bypassed = bypassManager.isActive(player);

            // Glowing setzen, falls kein Bypass aktiv
            if (!bypassed) {
                if (!player.isGlowing()) {
                    player.setGlowing(true);
                }
            } else {
                if (!isOtherGlowActive(player) && player.isGlowing()) {
                    player.setGlowing(false);
                }
            }

            // Claim-Betretung prüfen: Darf er mit dem Ei in Claims stehen?
            if (!bypassed && !config.isAllowClaimEntry()) {
                if (plugin.isInAnyClaim(player.getLocation())) {
                    plugin.evictFromClaimIfRestricted(player);
                    sendClaimWarning(player);
                }
            }
        } else {
            if (wasHolder) {
                eggHolders.remove(uuid);
                lastClaimWarning.remove(uuid);
                player.sendMessage(config.getMessage("egg-lost"));

                // Glowing zurücksetzen, falls nicht durch anderes Modul (z.B. Combat Tag / Clan Glow) aktiv
                if (!isOtherGlowActive(player)) {
                    player.setGlowing(false);
                }
            }
        }
    }

    private void sendClaimWarning(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastClaimWarning.get(player.getUniqueId());
        if (last != null && now - last < 2000L) return;
        lastClaimWarning.put(player.getUniqueId(), now);
        player.sendMessage(config.getMessage("cannot-enter-claim"));
    }

    private boolean isOtherGlowActive(Player player) {
        if (plugin.getGlowManager() != null && plugin.getGlowManager().isExternalGlowActive(player)) {
            return true;
        }
        if (plugin.getPvpManager() != null && plugin.getPvpManager().getCombatManager() != null
                && plugin.getPvpManager().getCombatManager().isTagged(player.getUniqueId())) {
            return true;
        }
        return false;
    }

    public boolean holdsEgg(UUID uuid) {
        return eggHolders.contains(uuid);
    }

    public boolean holdsEgg(Player player) {
        return player != null && eggHolders.contains(player.getUniqueId());
    }

    /**
     * Ob für diesen Spieler das DragonEgg-Glowing aktiv ist (berücksichtigt Bypass).
     */
    public boolean isGlowing(Player player) {
        if (player == null) return false;
        if (!eggHolders.contains(player.getUniqueId())) return false;
        return !bypassManager.isActive(player);
    }

    /**
     * Ob mindestens ein Spieler aktuell durch das Drachenei leuchtet.
     */
    public boolean hasAnyGlowing() {
        for (UUID uuid : eggHolders) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && isGlowing(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ob das Drachenei für diesen Spieler PvP permanent forciert.
     */
    public boolean isPvpForced(Player player) {
        if (player == null) return false;
        if (!config.isPermanentPvp()) return false;
        if (!eggHolders.contains(player.getUniqueId())) return false;
        return !bypassManager.isActive(player);
    }

    /**
     * Ob das Drachenei dem Spieler das Betreten/Verweilen auf Claims verbietet.
     */
    public boolean isClaimBlocked(Player player) {
        if (player == null) return false;
        if (config.isAllowClaimEntry()) return false;
        if (!eggHolders.contains(player.getUniqueId())) return false;
        return !bypassManager.isActive(player);
    }

    public Set<UUID> getEggHolders() {
        return Collections.unmodifiableSet(eggHolders);
    }
}
