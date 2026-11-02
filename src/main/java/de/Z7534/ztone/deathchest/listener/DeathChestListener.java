package de.Z7534.ztone.deathchest.listener;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.deathchest.config.DeathChestConfig;
import de.Z7534.ztone.deathchest.manager.DeathChestBypassManager;
import de.Z7534.ztone.deathchest.manager.DeathChestManager;
import de.Z7534.ztone.deathchest.model.DeathChestData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import de.Z7534.ztone.stonelog.util.MaterialNameUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.util.concurrent.ConcurrentHashMap;

public class DeathChestListener implements Listener {

    private final Ztone plugin;
    private final DeathChestManager deathChestManager;
    private final DeathChestConfig config;
    private final DeathChestBypassManager bypassManager;
    private final Map<UUID, ItemStack[]> activeChestSnapshots = new ConcurrentHashMap<>();


    public DeathChestListener(Ztone plugin, DeathChestManager deathChestManager, DeathChestConfig config,
                              DeathChestBypassManager bypassManager) {
        this.plugin = plugin;
        this.deathChestManager = deathChestManager;
        this.config = config;
        this.bypassManager = bypassManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Bypass gilt nie ueber eine Session hinaus - ausser mit deathchest.bypass.join (Standard: false)
        if (bypassManager.handleJoin(event.getPlayer())) {
            event.getPlayer().sendMessage(config.getMessage("bypass-auto-join"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bypassManager.reset(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getConfigManager().isDeathchestEnabled()) {
            return;
        }

        Player victim = event.getEntity();

        // 1. Collect all drops / inventory items
        List<ItemStack> items = new ArrayList<>();
        if (!event.getDrops().isEmpty()) {
            for (ItemStack drop : event.getDrops()) {
                if (drop != null && !drop.getType().isAir()) {
                    items.add(drop.clone());
                }
            }
            try {
                for (ItemStack keep : event.getItemsToKeep()) {
                    if (keep != null && !keep.getType().isAir()) {
                        items.add(keep.clone());
                    }
                }
            } catch (Throwable ignored) {}
        } else {
            for (ItemStack content : victim.getInventory().getContents()) {
                if (content != null && !content.getType().isAir()) {
                    items.add(content.clone());
                }
            }
        }

        if (items.isEmpty()) {
            return; // No items to store
        }

        // Clear drops and inventory completely to prevent any duping
        event.getDrops().clear();
        try {
            event.getItemsToKeep().clear();
        } catch (Throwable ignored) {}
        try {
            event.setKeepInventory(false);
        } catch (Throwable ignored) {}

        victim.getInventory().clear();
        victim.getInventory().setArmorContents(new ItemStack[4]);
        victim.getInventory().setItemInOffHand(null);

        // 2. Identify killer (PvP or Combat-Log)
        Player killer = victim.getKiller();
        UUID killerUuid = null;
        String killerName = null;

        if (killer != null) {
            killerUuid = killer.getUniqueId();
            killerName = killer.getName();
        } else if (victim.getLastDamageCause() instanceof EntityDamageByEntityEvent edbe) {
            if (edbe.getDamager() instanceof Player direct) {
                killerUuid = direct.getUniqueId();
                killerName = direct.getName();
            } else if (edbe.getDamager() instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player shooter) {
                killerUuid = shooter.getUniqueId();
                killerName = shooter.getName();
            }
        }

        if (killerUuid == null && victim.hasMetadata("combatlog_attacker")) {
            try {
                killerUuid = UUID.fromString(victim.getMetadata("combatlog_attacker").get(0).asString());
                Player p = Bukkit.getPlayer(killerUuid);
                killerName = p != null ? p.getName() : Bukkit.getOfflinePlayer(killerUuid).getName();
            } catch (Throwable ignored) {}
        }

        if (killerUuid == null && plugin.getPvpManager() != null && plugin.getPvpManager().getCombatManager() != null) {
            UUID lastAttacker = plugin.getPvpManager().getCombatManager().getLastAttacker(victim.getUniqueId());
            if (lastAttacker != null) {
                killerUuid = lastAttacker;
                Player p = Bukkit.getPlayer(lastAttacker);
                killerName = p != null ? p.getName() : Bukkit.getOfflinePlayer(lastAttacker).getName();
            } else {
                Set<UUID> opps = plugin.getPvpManager().getCombatManager().getOpponents(victim.getUniqueId());
                if (opps != null && !opps.isEmpty()) {
                    UUID opp = opps.iterator().next();
                    killerUuid = opp;
                    Player p = Bukkit.getPlayer(opp);
                    killerName = p != null ? p.getName() : Bukkit.getOfflinePlayer(opp).getName();
                }
            }
        }

        // 3. Create DeathChest 1 tick later so block-destroying explosions (Creeper, TNT) have fully resolved
        final UUID fVictimUuid = victim.getUniqueId();
        final String fVictimName = victim.getName();
        final Location deathLoc = victim.getLocation().clone();
        final UUID fKillerUuid = killerUuid;
        final String fKillerName = killerName;

        Bukkit.getScheduler().runTask(plugin, () -> {
            deathChestManager.createDeathChest(fVictimUuid, fVictimName, deathLoc, fKillerUuid, fKillerName, items);
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        DeathChestData chest = deathChestManager.getDeathChestAt(block);
        if (chest == null) return;

        // Cancel interaction for BOTH hands so Minecraft never opens the block entity or triggers vanilla interactions
        event.setCancelled(true);

        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();

        if (chest.isKillerLocked()) {
            boolean isKiller = player.getUniqueId().equals(chest.getKillerUuid());
            boolean bypassing = !isKiller && bypassManager.isActive(player);
            if (isKiller || bypassing) {
                // Killer Claim-Override: Bypasses claim restrictions completely
                // DeathChest-Bypass (/deathchest bypass): darf trotz Sperre hineinschauen und entnehmen
                Inventory inv = chest.getInventory();
                if (inv != null) {
                    if (bypassing) {
                        player.sendMessage(config.getMessage("bypass-opened-locked", Map.of(
                                "killer", chest.getKillerName() != null ? chest.getKillerName() : "Killer",
                                "victim", chest.getVictimName() != null ? chest.getVictimName() : "?"
                        )));
                    }
                    if (!player.getUniqueId().equals(chest.getVictimUuid())) {
                        activeChestSnapshots.computeIfAbsent(chest.getId(), id -> cloneContents(inv));
                    }
                    player.openInventory(inv);
                    player.playSound(block.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f);
                }
            } else {
                // Locked for killer -> block other players
                long rem = chest.getRemainingKillerLockSeconds();
                player.sendMessage(config.getMessage("locked-for-killer", Map.of(
                        "killer", chest.getKillerName() != null ? chest.getKillerName() : "Killer",
                        "seconds", String.valueOf(rem),
                        "victim", chest.getVictimName()
                )));
            }
            return;
        }

        // Unlocked / Non-PvP death: Victim or any other player can open internal inventory
        Inventory inv = chest.getInventory();
        if (inv != null) {
            if (!player.getUniqueId().equals(chest.getVictimUuid())) {
                activeChestSnapshots.computeIfAbsent(chest.getId(), id -> cloneContents(inv));
            }
            player.openInventory(inv);
            player.playSound(block.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        Inventory inv = event.getInventory();
        InventoryHolder holder = inv.getHolder();
        if (holder instanceof Chest c) {
            if (deathChestManager.isDeathChest(c.getBlock())) {
                event.setCancelled(true);
            }
        } else if (holder instanceof DoubleChest dc) {
            if (dc.getLeftSide() instanceof Chest c && deathChestManager.isDeathChest(c.getBlock())) {
                event.setCancelled(true);
            } else if (dc.getRightSide() instanceof Chest c && deathChestManager.isDeathChest(c.getBlock())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        DeathChestData chest = deathChestManager.getDeathChestByInventory(topInv);
        if (chest == null) return;

        Player player = (Player) event.getWhoClicked();

        // 1. Shift-click from bottom inventory into top chest
        if (event.isShiftClick()) {
            if (event.getRawSlot() >= topInv.getSize()) {
                event.setCancelled(true);
                player.sendMessage(config.getMessage("take-only"));
                return;
            }
        }

        // 2. Number key swap in top chest
        if (event.getClick() == ClickType.NUMBER_KEY) {
            if (event.getRawSlot() < topInv.getSize()) {
                event.setCancelled(true);
                player.sendMessage(config.getMessage("take-only"));
                return;
            }
        }

        // 3. Cursor place / swap into top chest
        if (event.getRawSlot() < topInv.getSize()) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                event.setCancelled(true);
                player.sendMessage(config.getMessage("take-only"));
                return;
            }

            InventoryAction action = event.getAction();
            switch (action) {
                case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR, HOTBAR_SWAP -> {
                    event.setCancelled(true);
                    player.sendMessage(config.getMessage("take-only"));
                    return;
                }
                default -> {}
            }
        }

        // Loot pickup sound when taking items from the chest
        if (event.getRawSlot() < topInv.getSize()) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && !clickedItem.getType().isAir()) {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
            }
        }

        final boolean isLooter = !player.getUniqueId().equals(chest.getVictimUuid());
        if (isLooter) {
            activeChestSnapshots.computeIfAbsent(chest.getId(), id -> cloneContents(topInv));
        }

        // 4. Early-Cleanup check: If chest becomes empty after this click
        final UUID clickerUuid = player.getUniqueId();
        final String clickerName = player.getName();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isLooter) {
                checkAndNotifyLooted(chest, clickerName, topInv);
            }
            if (deathChestManager.isInventoryEmpty(topInv)) {
                activeChestSnapshots.remove(chest.getId());
                deathChestManager.removeDeathChest(chest, true, clickerUuid);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        DeathChestData chest = deathChestManager.getDeathChestByInventory(topInv);
        if (chest == null) return;

        for (int slot : event.getRawSlots()) {
            if (slot < topInv.getSize()) {
                event.setCancelled(true);
                event.getWhoClicked().sendMessage(config.getMessage("take-only"));
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        DeathChestData chest = deathChestManager.getDeathChestByInventory(inv);
        if (chest == null) return;

        final UUID closingPlayerUuid = event.getPlayer().getUniqueId();
        if (deathChestManager.isPlayerClosing(closingPlayerUuid)) {
            return;
        }

        if (event.getPlayer() instanceof Player p) {
            p.playSound(chest.getPrimaryLocation(), Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.0f);
        }

        boolean noOtherViewers = true;
        for (HumanEntity viewer : inv.getViewers()) {
            if (!viewer.getUniqueId().equals(closingPlayerUuid)) {
                noOtherViewers = false;
                break;
            }
        }
        if (noOtherViewers) {
            activeChestSnapshots.remove(chest.getId());
        }

        if (deathChestManager.isInventoryEmpty(inv)) {
            activeChestSnapshots.remove(chest.getId());
            deathChestManager.removeDeathChest(chest, true, closingPlayerUuid);
        }

        deathChestManager.saveActiveChests(false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        // Prevent hoppers and hopper minecarts from pulling or pushing items
        if (deathChestManager.getDeathChestByInventory(event.getSource()) != null
                || deathChestManager.getDeathChestByInventory(event.getDestination()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (deathChestManager.isDeathChest(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (deathChestManager.isDeathChest(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(deathChestManager::isDeathChest);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(deathChestManager::isDeathChest);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (deathChestManager.isDeathChest(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (deathChestManager.isDeathChest(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (deathChestManager.isDeathChest(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (deathChestManager.isDeathChest(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof TextDisplay td) {
            for (DeathChestData chest : deathChestManager.getChestsById().values()) {
                if (td.getUniqueId().equals(chest.getTextDisplayUuid())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        deathChestManager.onChunkLoad(event.getChunk());
    }

    private ItemStack[] cloneContents(Inventory inv) {
        if (inv == null) return new ItemStack[0];
        ItemStack[] copy = new ItemStack[inv.getSize()];
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && !item.getType().isAir()) {
                copy[i] = item.clone();
            }
        }
        return copy;
    }

    private void checkAndNotifyLooted(DeathChestData chest, String looterName, Inventory topInv) {
        ItemStack[] known = activeChestSnapshots.get(chest.getId());
        if (known == null) {
            return;
        }

        Player victim = Bukkit.getPlayer(chest.getVictimUuid());
        Map<String, Integer> takenItems = new LinkedHashMap<>();

        int size = Math.min(known.length, topInv.getSize());
        for (int i = 0; i < size; i++) {
            ItemStack before = known[i];
            ItemStack current = topInv.getItem(i);

            if (before != null && !before.getType().isAir()) {
                int beforeAmount = before.getAmount();
                int currentAmount = (current != null && !current.getType().isAir() && current.isSimilar(before))
                        ? current.getAmount()
                        : 0;
                int diff = beforeAmount - currentAmount;
                if (diff > 0) {
                    String itemName = formatItemName(before);
                    takenItems.put(itemName, takenItems.getOrDefault(itemName, 0) + diff);
                }
            }

            known[i] = (current != null && !current.getType().isAir()) ? current.clone() : null;
        }

        if (victim != null && victim.isOnline() && !takenItems.isEmpty()) {
            for (Map.Entry<String, Integer> entry : takenItems.entrySet()) {
                victim.sendMessage(config.getMessage("item-looted", Map.of(
                        "player", looterName,
                        "amount", String.valueOf(entry.getValue()),
                        "item", entry.getKey()
                )));
            }
        }
    }

    private String formatItemName(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "Luft";
        }
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            Component dn = item.getItemMeta().displayName();
            if (dn != null) {
                String plain = PlainTextComponentSerializer.plainText().serialize(dn).trim();
                if (!plain.isEmpty()) {
                    return plain;
                }
            }
        }
        return getGermanMaterialName(item.getType());
    }

    private String getGermanMaterialName(org.bukkit.Material mat) {
        if (mat == null) return "Unbekannt";
        return switch (mat) {
            case NETHERITE_SWORD -> "Netheritschwert";
            case DIAMOND_SWORD -> "Diamantschwert";
            case IRON_SWORD -> "Eisenschwert";
            case GOLDEN_SWORD -> "Goldschwert";
            case STONE_SWORD -> "Steinschwert";
            case WOODEN_SWORD -> "Holzschwert";

            case NETHERITE_PICKAXE -> "Netheritspitzhacke";
            case DIAMOND_PICKAXE -> "Diamantspitzhacke";
            case IRON_PICKAXE -> "Eisenspitzhacke";
            case GOLDEN_PICKAXE -> "Goldspitzhacke";
            case STONE_PICKAXE -> "Steinspitzhacke";
            case WOODEN_PICKAXE -> "Holzspitzhacke";

            case NETHERITE_AXE -> "Netheritaxt";
            case DIAMOND_AXE -> "Diamantaxt";
            case IRON_AXE -> "Eisenaxt";
            case GOLDEN_AXE -> "Goldaxt";
            case STONE_AXE -> "Steinaxt";
            case WOODEN_AXE -> "Holzaxt";

            case NETHERITE_SHOVEL -> "Netheritschaufel";
            case DIAMOND_SHOVEL -> "Diamantschaufel";
            case IRON_SHOVEL -> "Eisenschaufel";
            case GOLDEN_SHOVEL -> "Goldschaufel";
            case STONE_SHOVEL -> "Steinschaufel";
            case WOODEN_SHOVEL -> "Holzschaufel";

            case NETHERITE_HOE -> "Netherithacke";
            case DIAMOND_HOE -> "Diamanthacke";
            case IRON_HOE -> "Eisenhacke";
            case GOLDEN_HOE -> "Goldhacke";
            case STONE_HOE -> "Steinhacke";
            case WOODEN_HOE -> "Holzhacke";

            case NETHERITE_HELMET -> "Netherithelm";
            case DIAMOND_HELMET -> "Diamanthelm";
            case IRON_HELMET -> "Eisenhelm";
            case GOLDEN_HELMET -> "Goldhelm";
            case CHAINMAIL_HELMET -> "Kettenhelm";
            case LEATHER_HELMET -> "Lederkappe";
            case TURTLE_HELMET -> "Schildkrötenpanzer";

            case NETHERITE_CHESTPLATE -> "Netheritbrustplatte";
            case DIAMOND_CHESTPLATE -> "Diamantbrustplatte";
            case IRON_CHESTPLATE -> "Eisenbrustplatte";
            case GOLDEN_CHESTPLATE -> "Goldbrustplatte";
            case CHAINMAIL_CHESTPLATE -> "Kettenbrustplatte";
            case LEATHER_CHESTPLATE -> "Lederjacke";

            case NETHERITE_LEGGINGS -> "Netherithose";
            case DIAMOND_LEGGINGS -> "Diamanthose";
            case IRON_LEGGINGS -> "Eisenhose";
            case GOLDEN_LEGGINGS -> "Goldhose";
            case CHAINMAIL_LEGGINGS -> "Kettenhose";
            case LEATHER_LEGGINGS -> "Lederhose";

            case NETHERITE_BOOTS -> "Netheritstiefel";
            case DIAMOND_BOOTS -> "Diamantstiefel";
            case IRON_BOOTS -> "Eisenstiefel";
            case GOLDEN_BOOTS -> "Goldstiefel";
            case CHAINMAIL_BOOTS -> "Kettenstiefel";
            case LEATHER_BOOTS -> "Lederstiefel";

            case BOW -> "Bogen";
            case CROSSBOW -> "Armbrust";
            case TRIDENT -> "Dreizack";
            case MACE -> "Streitkolben";
            case SHIELD -> "Schild";
            case ELYTRA -> "Elytren";
            case ARROW -> "Pfeil";
            case SPECTRAL_ARROW -> "Spektralpfeil";
            case TIPPED_ARROW -> "Getränkter Pfeil";
            case WIND_CHARGE -> "Windkugel";
            case FIREWORK_ROCKET -> "Feuerwerksrakete";

            case TOTEM_OF_UNDYING -> "Totem der Unsterblichkeit";
            case GOLDEN_APPLE -> "Goldener Apfel";
            case ENCHANTED_GOLDEN_APPLE -> "Verzauberter Goldener Apfel";
            case ENDER_PEARL -> "Enderperle";
            case ENDER_EYE -> "Enderauge";
            case EXPERIENCE_BOTTLE -> "Erfahrungsflasche";
            case POTION -> "Trank";
            case SPLASH_POTION -> "Wurftrank";
            case LINGERING_POTION -> "Verweiltrank";

            case NETHERITE_INGOT -> "Netheritbarren";
            case NETHERITE_SCRAP -> "Netheritschrott";
            case NETHERITE_BLOCK -> "Netheritblock";
            case DIAMOND -> "Diamant";
            case DIAMOND_BLOCK -> "Diamantblock";
            case EMERALD -> "Smaragd";
            case EMERALD_BLOCK -> "Smaragdblock";
            case GOLD_INGOT -> "Goldbarren";
            case GOLD_BLOCK -> "Goldblock";
            case IRON_INGOT -> "Eisenbarren";
            case IRON_BLOCK -> "Eisenblock";
            case COPPER_INGOT -> "Kupferbarren";
            case COPPER_BLOCK -> "Kupferblock";
            case LAPIS_LAZULI -> "Lapislazuli";
            case LAPIS_BLOCK -> "Lapislazuliblock";
            case REDSTONE -> "Redstone";
            case REDSTONE_BLOCK -> "Redstoneblock";
            case COAL -> "Kohle";
            case COAL_BLOCK -> "Kohleblock";
            case RAW_IRON -> "Roheisen";
            case RAW_GOLD -> "Rohgold";
            case RAW_COPPER -> "Rohkupfer";

            case OBSIDIAN -> "Obsidian";
            case CRYING_OBSIDIAN -> "Weinender Obsidian";
            case RESPAWN_ANCHOR -> "Seelenanker";
            case END_CRYSTAL -> "Endkristall";
            case TNT -> "TNT";
            case COBWEB -> "Spinnennetz";
            case ANVIL -> "Amboss";
            case CHIPPED_ANVIL -> "Angeschlagener Amboss";
            case DAMAGED_ANVIL -> "Beschädigter Amboss";
            case ENCHANTING_TABLE -> "Zaubertisch";
            case BEACON -> "Leuchtfeuer";
            case CHEST -> "Truhe";
            case ENDER_CHEST -> "Endertruhe";
            case SHULKER_BOX -> "Shulkerkiste";
            case FLINT_AND_STEEL -> "Feuerzeug";
            case SHEARS -> "Schere";
            case FISHING_ROD -> "Angel";
            case WATER_BUCKET -> "Wassereimer";
            case LAVA_BUCKET -> "Lavaeimer";
            case MILK_BUCKET -> "Milcheimer";
            case BUCKET -> "Eimer";
            case COMPASS -> "Kompass";
            case CLOCK -> "Uhr";
            case NAME_TAG -> "Namensschild";
            case LEAD -> "Leine";
            case SADDLE -> "Sattel";

            case GOLDEN_CARROT -> "Goldene Karotte";
            case COOKED_BEEF -> "Steak";
            case COOKED_PORKCHOP -> "Gebratenes Schweinefleisch";
            case COOKED_MUTTON -> "Gebratenes Hammelfleisch";
            case COOKED_CHICKEN -> "Gebratenes Hühnchen";
            case COOKED_SALMON -> "Gebratener Lachs";
            case COOKED_COD -> "Gebratener Kabeljau";
            case BREAD -> "Brot";
            case APPLE -> "Apfel";

            default -> MaterialNameUtil.prettify(mat);
        };
    }
}
