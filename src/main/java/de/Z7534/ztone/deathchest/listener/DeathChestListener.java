package de.Z7534.ztone.deathchest.listener;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.deathchest.config.DeathChestConfig;
import de.Z7534.ztone.deathchest.manager.DeathChestManager;
import de.Z7534.ztone.deathchest.model.DeathChestData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
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
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class DeathChestListener implements Listener {

    private final Ztone plugin;
    private final DeathChestManager deathChestManager;
    private final DeathChestConfig config;

    public DeathChestListener(Ztone plugin, DeathChestManager deathChestManager, DeathChestConfig config) {
        this.plugin = plugin;
        this.deathChestManager = deathChestManager;
        this.config = config;
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
            if (player.getUniqueId().equals(chest.getKillerUuid())) {
                // Killer Claim-Override: Bypasses claim restrictions completely
                Inventory inv = chest.getInventory();
                if (inv != null) {
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

        // 4. Early-Cleanup check: If chest becomes empty after this click
        final UUID clickerUuid = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (deathChestManager.isInventoryEmpty(topInv)) {
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

        if (event.getPlayer() instanceof Player p) {
            p.playSound(chest.getPrimaryLocation(), Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.0f);
        }

        // Ansatz A (1-Tick Delay) + Ansatz B (Skip current closing player to avoid circular closeInventory calls)
        final UUID closingPlayerUuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (deathChestManager.isInventoryEmpty(inv)) {
                deathChestManager.removeDeathChest(chest, true, closingPlayerUuid);
            }
        });
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
}
