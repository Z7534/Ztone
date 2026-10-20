package de.Z7534.ztone.claims.manager;

import de.Z7534.ztone.claims.model.PlayerData;
import de.Z7534.ztone.claims.util.ItemBuilder;
import de.Z7534.ztone.claims.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ClaimMapManager {

    private final JavaPlugin plugin;
    private final ClaimManager claimManager;
    private final SelectionManager selectionManager;
    private final ClaimMapRenderer renderer;
    private final NamespacedKey keyClaimMap;

    private final Map<String, MapView> worldMaps = new HashMap<>();
    private final Map<UUID, ItemStack> savedOffHand = new ConcurrentHashMap<>();

    public ClaimMapManager(JavaPlugin plugin, ClaimManager claimManager, SelectionManager selectionManager) {
        this.plugin = plugin;
        this.claimManager = claimManager;
        this.selectionManager = selectionManager;
        this.renderer = new ClaimMapRenderer(claimManager, selectionManager);
        this.keyClaimMap = new NamespacedKey(plugin, "claim_map");
    }

    public boolean isClaimMap(ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP) return false;
        var meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(keyClaimMap, PersistentDataType.BYTE);
    }

    public MapView getOrCreateMapView(World world) {
        return worldMaps.computeIfAbsent(world.getName(), name -> {
            MapView view = Bukkit.createMap(world);
            view.setScale(MapView.Scale.CLOSEST);
            view.setTrackingPosition(false);
            view.setUnlimitedTracking(false);
            view.setLocked(true);
            for (MapRenderer r : new ArrayList<>(view.getRenderers())) {
                view.removeRenderer(r);
            }
            view.addRenderer(renderer);
            return view;
        });
    }

    public ItemStack createClaimMapItem(Player player) {
        MapView view = getOrCreateMapView(player.getWorld());
        ItemStack map = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) map.getItemMeta();
        if (meta != null) {
            meta.setMapView(view);
            meta.displayName(Text.line("<gradient:#55C4FF:#1E90FF><bold>ᴄʟᴀɪᴍ-ᴋᴀʀᴛᴇ</bold></gradient>"));
            meta.lore(List.of(
                    Text.line("<gray>Blau: <white>Dein Claim</white></gray>"),
                    Text.line("<gray>Rot: <white>Fremder Claim</white></gray>"),
                    Text.line("<gray>Grün: <white>Freier Chunk</white></gray>"),
                    Text.line("<gray>Gelb: <white>Ausgewählt</white></gray>")
            ));
            meta.getPersistentDataContainer().set(keyClaimMap, PersistentDataType.BYTE, (byte) 1);
            map.setItemMeta(meta);
        }
        return map;
    }

    public void giveMap(Player player) {
        if (player == null || !player.isOnline()) return;

        PlayerData pd = claimManager.getOrCreatePlayerData(player.getUniqueId(), player.getName());
        if (!pd.claimMapEnabled()) return;

        UUID uuid = player.getUniqueId();
        if (savedOffHand.containsKey(uuid)) {
            // Bereits aktiv
            return;
        }

        ItemStack currentOffHand = player.getInventory().getItemInOffHand();
        if (isClaimMap(currentOffHand)) {
            return;
        }

        savedOffHand.put(uuid, currentOffHand != null && currentOffHand.getType() != Material.AIR
                ? currentOffHand.clone() : new ItemStack(Material.AIR));

        player.getInventory().setItemInOffHand(createClaimMapItem(player));
        renderer.invalidate(uuid);
    }

    public void restoreOffHand(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();

        if (savedOffHand.containsKey(uuid)) {
            ItemStack saved = savedOffHand.remove(uuid);
            ItemStack currentOffHand = player.getInventory().getItemInOffHand();

            if (isClaimMap(currentOffHand)) {
                player.getInventory().setItemInOffHand(saved != null && saved.getType() != Material.AIR ? saved : null);
            } else {
                removeClaimMapsFromInventory(player);
                if (saved != null && saved.getType() != Material.AIR) {
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(saved);
                    leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                }
            }
        } else {
            removeClaimMapsFromInventory(player);
        }

        renderer.invalidate(uuid);
    }

    public ItemStack removeSavedOffHand(UUID uuid) {
        return savedOffHand.remove(uuid);
    }

    public boolean hasSavedOffHand(UUID uuid) {
        return savedOffHand.containsKey(uuid);
    }

    public void restoreAll() {
        for (UUID uuid : new ArrayList<>(savedOffHand.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                restoreOffHand(p);
            }
        }
        savedOffHand.clear();
        renderer.clearCache();
    }

    public void removeClaimMapsFromInventory(Player player) {
        if (player == null || !player.isOnline()) return;

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isClaimMap(offHand)) {
            player.getInventory().setItemInOffHand(null);
        }

        ItemStack[] contents = player.getInventory().getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            if (isClaimMap(contents[i])) {
                contents[i] = null;
                changed = true;
            }
        }
        if (changed) {
            player.getInventory().setContents(contents);
        }
    }

    public void invalidate(UUID uuid) {
        renderer.invalidate(uuid);
    }

    public ClaimMapRenderer getRenderer() {
        return renderer;
    }
}
