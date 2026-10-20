package de.Z7534.ztone.claims.listener;

import de.Z7534.ztone.claims.config.ConfigManager;
import de.Z7534.ztone.claims.manager.BypassManager;
import de.Z7534.ztone.claims.manager.ClaimManager;
import de.Z7534.ztone.claims.manager.PvPClaimBypassManager;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.model.ClaimPermission;
import de.Z7534.ztone.claims.model.PlayerData;
import de.Z7534.ztone.claims.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Dispenser;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ProtectionListener implements Listener {

    private final ClaimManager claimManager;
    private final ConfigManager config;
    private final BypassManager bypass;
    private final PvPClaimBypassManager pvpClaimBypass;

    // Speichert Kills/Angriffe von Monstern auf Spieler fuer 30 Sekunden: "monsterUUID:playerUUID" -> Timestamp
    private final Map<String, Long> recentMonsterVictims = new ConcurrentHashMap<>();

    public ProtectionListener(ClaimManager claimManager, ConfigManager config, BypassManager bypass, PvPClaimBypassManager pvpClaimBypass) {
        this.claimManager = claimManager;
        this.config = config;
        this.bypass = bypass;
        this.pvpClaimBypass = pvpClaimBypass;
    }

    private boolean allowed(Player player, Location loc, ClaimPermission permission) {
        if (bypass.isActive(player)) return true;
        Claim claim = claimManager.getClaimAt(loc);
        if (claim == null) {
            if (permission == ClaimPermission.PVP) {
                return false;
            }
            return true;
        }
        if (claim.isBanned(player.getUniqueId())) return false;
        return claim.hasPermission(player.getUniqueId(), permission);
    }

    private boolean allowsExplosions(Location loc) {
        Claim claim = claimManager.getClaimAt(loc);
        if (claim == null) return true;
        return claimManager.findPlayerData(claim.owner())
                .map(PlayerData::explosionsEnabled)
                .orElse(false);
    }

    private void deny(Player player) {
        player.sendMessage(Text.line(config.prefix() + config.message("action-denied")));
    }

    private void deny(Player player, EquipmentSlot hand) {
        if (hand == EquipmentSlot.HAND) {
            deny(player);
        }
    }

    private boolean crossesClaimBorder(Block block, BlockFace direction) {
        Claim from = claimManager.getClaimAt(block.getLocation());
        Claim to = claimManager.getClaimAt(block.getRelative(direction).getLocation());
        return from != to;
    }

    @EventHandler(ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent e) {
        Claim from = claimManager.getClaimAt(e.getBlock().getLocation());
        Claim to = claimManager.getClaimAt(e.getToBlock().getLocation());
        if (from != to) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        BlockFace direction = e.getDirection();
        if (crossesClaimBorder(e.getBlock(), direction)) {
            e.setCancelled(true);
            return;
        }
        for (Block moved : e.getBlocks()) {
            if (crossesClaimBorder(moved, direction)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        BlockFace direction = e.getDirection().getOppositeFace();
        for (Block moved : e.getBlocks()) {
            if (crossesClaimBorder(moved, direction)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() == Material.SPAWNER) {
            if (!allowed(e.getPlayer(), e.getBlock().getLocation(), ClaimPermission.SPAWNER)) {
                e.setCancelled(true);
                deny(e.getPlayer());
                return;
            }
        }
        if (!allowed(e.getPlayer(), e.getBlock().getLocation(), ClaimPermission.BUILD)) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (e.getBlock().getType() == Material.SPAWNER) {
            if (!allowed(e.getPlayer(), e.getBlock().getLocation(), ClaimPermission.SPAWNER)) {
                e.setCancelled(true);
                deny(e.getPlayer());
                return;
            }
        }
        if (!allowed(e.getPlayer(), e.getBlock().getLocation(), ClaimPermission.BUILD)) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreakGeneric(HangingBreakEvent e) {
        if (e.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION) {
            if (!allowsExplosions(e.getEntity().getLocation())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        Entity remover = e.getRemover();
        if (remover instanceof Explosive || remover instanceof TNTPrimed || remover instanceof Creeper) {
            if (!allowsExplosions(e.getEntity().getLocation())) {
                e.setCancelled(true);
                return;
            }
        }
        Player player = resolvePlayer(remover);
        if (player == null) return;
        if (!allowed(player, e.getEntity().getLocation(), ClaimPermission.ENTITY_INTERACT)
                && !allowed(player, e.getEntity().getLocation(), ClaimPermission.BUILD)) {
            e.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        Player player = e.getPlayer();
        if (player == null) return;
        if (!allowed(player, e.getEntity().getLocation(), ClaimPermission.ENTITY_INTERACT)
                && !allowed(player, e.getEntity().getLocation(), ClaimPermission.BUILD)) {
            e.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!allowed(e.getPlayer(), e.getBlock().getLocation(), ClaimPermission.BUCKETS)) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!allowed(e.getPlayer(), e.getBlock().getLocation(), ClaimPermission.BUCKETS)) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();

        if (e.getAction() == Action.PHYSICAL) {
            Block trodden = e.getClickedBlock();
            if (trodden == null) return;
            Material troddenType = trodden.getType();
            if (troddenType == Material.FARMLAND) {
                Claim claim = claimManager.getClaimAt(trodden.getLocation());
                if (claim != null && !bypass.isActive(player)
                        && !claim.isOwner(player.getUniqueId())
                        && !(claim.isTrusted(player.getUniqueId()) && !claim.isBanned(player.getUniqueId()))) {
                    e.setCancelled(true);
                }
                return;
            }
            if (troddenType.name().endsWith("_PRESSURE_PLATE") || troddenType == Material.TRIPWIRE) {
                if (!allowed(player, trodden.getLocation(), ClaimPermission.REDSTONE)) {
                    e.setCancelled(true);
                }
            }
            return;
        }

        // Entity-Items, Enderperlen, Wind Charges, Fahrzeuge & Endkristalle pruefen
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.RIGHT_CLICK_AIR) {
            if (e.getItem() != null) {
                Material held = e.getItem().getType();
                String name = held.name();

                // 1. Enderperlen & Wind Charges werfen/benutzen
                if (held == Material.ENDER_PEARL || held == Material.WIND_CHARGE) {
                    if (!allowed(player, player.getLocation(), ClaimPermission.ENTITY_INTERACT)) {
                        e.setCancelled(true);
                        deny(player);
                        return;
                    }
                }

                // 2. Entity-Platzierung (ArmorStand, ItemFrame, GlowItemFrame, Painting, Spawn-Eggs)
                if (held == Material.ARMOR_STAND || held == Material.ITEM_FRAME || held == Material.GLOW_ITEM_FRAME
                        || held == Material.PAINTING || name.endsWith("_SPAWN_EGG")) {
                    Location targetLoc = e.getClickedBlock() != null ? e.getClickedBlock().getLocation() : player.getLocation();
                    if (!allowed(player, targetLoc, ClaimPermission.ENTITY_INTERACT)) {
                        e.setCancelled(true);
                        deny(player);
                        return;
                    }
                }

                // 3. Fahrzeuge (Boote, Loren, Floesse)
                if (name.endsWith("_BOAT") || name.endsWith("_CHEST_BOAT") || name.endsWith("_RAFT") || name.endsWith("_MINECART")) {
                    Location targetLoc = e.getClickedBlock() != null ? e.getClickedBlock().getLocation() : player.getLocation();
                    if (!allowed(player, targetLoc, ClaimPermission.BUILD) && !allowed(player, targetLoc, ClaimPermission.VEHICLES)) {
                        e.setCancelled(true);
                        deny(player);
                        return;
                    }
                }

                // 4. Endkristalle
                if (held == Material.END_CRYSTAL) {
                    Location targetLoc = e.getClickedBlock() != null ? e.getClickedBlock().getLocation() : player.getLocation();
                    if (!allowed(player, targetLoc, ClaimPermission.FIRE_EXPLOSIONS)
                            && !allowed(player, targetLoc, ClaimPermission.ENTITY_INTERACT)) {
                        e.setCancelled(true);
                        deny(player);
                        return;
                    }
                }
            }
        }

        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        if (block == null) return;
        Material type = block.getType();
        EquipmentSlot hand = e.getHand();

        // Spawner Interaktion (SmartSpawner GUI oeffnen, Mob-Eggs anwenden etc.)
        if (type == Material.SPAWNER) {
            if (!allowed(player, block.getLocation(), ClaimPermission.SPAWNER)) {
                e.setCancelled(true);
                deny(player, hand);
                return;
            }
        }

        if (e.getItem() != null && (e.getItem().getType() == Material.SPAWNER || e.getItem().getType().name().endsWith("_SPAWN_EGG"))) {
            if (type == Material.SPAWNER && !allowed(player, block.getLocation(), ClaimPermission.SPAWNER)) {
                e.setCancelled(true);
                deny(player, hand);
                return;
            }
        }

        if (block.getBlockData() instanceof Openable) {
            if (!allowed(player, block.getLocation(), ClaimPermission.DOORS)) {
                e.setCancelled(true);
                deny(player, hand);
            }
            return;
        }

        if (block.getState() instanceof Container
                || block.getState() instanceof BlockInventoryHolder
                || isShelf(type)
                || isWorkbenchLike(type)) {
            // DeathChests werden exklusiv von DeathChestListener verwaltet
            if (de.Z7534.ztone.Ztone.getInstance().getDeathchest() != null
                    && de.Z7534.ztone.Ztone.getInstance().getDeathchest().getDeathChestManager().isDeathChest(block)) {
                return;
            }
            if (!allowed(player, block.getLocation(), ClaimPermission.CONTAINERS)) {
                e.setCancelled(true);
                deny(player, hand);
            }
            return;
        }

        if (type == Material.LEVER || type.name().endsWith("_BUTTON") || type.name().endsWith("_PRESSURE_PLATE")) {
            if (!allowed(player, block.getLocation(), ClaimPermission.REDSTONE)) {
                e.setCancelled(true);
                deny(player, hand);
            }
            return;
        }

        if (type == Material.BELL || type.name().endsWith("_NOTE_BLOCK") || type == Material.NOTE_BLOCK || type.name().endsWith("_JUKEBOX") || type == Material.JUKEBOX) {
            if (!allowed(player, block.getLocation(), ClaimPermission.BELLS_NOTEBLOCKS)) {
                e.setCancelled(true);
                deny(player, hand);
            }
            return;
        }

        if (type == Material.TNT) {
            if (!allowed(player, block.getLocation(), ClaimPermission.FIRE_EXPLOSIONS)) {
                e.setCancelled(true);
                deny(player, hand);
            }
            return;
        }

        // Betten & Respawn-Anker vor Explosions-Missbrauch durch Fremde schuetzen
        if (type == Material.RESPAWN_ANCHOR) {
            if (!allowed(player, block.getLocation(), ClaimPermission.FIRE_EXPLOSIONS)) {
                e.setCancelled(true);
                deny(player, hand);
                return;
            }
        }
        if (type.name().endsWith("_BED")) {
            if (block.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL) {
                if (!allowed(player, block.getLocation(), ClaimPermission.FIRE_EXPLOSIONS)) {
                    e.setCancelled(true);
                    deny(player, hand);
                    return;
                }
            } else {
                if (!allowed(player, block.getLocation(), ClaimPermission.DOORS)) {
                    e.setCancelled(true);
                    deny(player, hand);
                    return;
                }
            }
        }

        // Shelves, Cauldron, Composter, Decorated Pot, Beeren
        if (isShelf(type) || type == Material.DECORATED_POT
                || type.name().endsWith("CAULDRON") || type == Material.COMPOSTER) {
            if (!allowed(player, block.getLocation(), ClaimPermission.CONTAINERS)) {
                e.setCancelled(true);
                deny(player, hand);
                return;
            }
        }
        if (type == Material.SWEET_BERRY_BUSH || type == Material.CAVE_VINES || type == Material.CAVE_VINES_PLANT) {
            if (!allowed(player, block.getLocation(), ClaimPermission.BUILD)) {
                e.setCancelled(true);
                deny(player, hand);
            }
        }
    }

    private boolean isShelf(Material type) {
        if (type == null) return false;
        String name = type.name();
        return name.endsWith("_SHELF")
                || name.equals("CHISELED_BOOKSHELF")
                || (name.contains("SHELF") && !name.equals("BOOKSHELF") && !name.equals("LEGACY_BOOKSHELF"));
    }

    private boolean isWorkbenchLike(Material type) {
        return switch (type) {
            case CRAFTING_TABLE, ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL, ENCHANTING_TABLE, BREWING_STAND,
                    LOOM, CARTOGRAPHY_TABLE, GRINDSTONE, SMITHING_TABLE, STONECUTTER, BEACON, LECTERN -> true;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent e) {
        Location src = e.getSource().getLocation();
        Location dest = e.getDestination().getLocation();
        if (src == null || dest == null) return;
        Claim srcClaim = claimManager.getClaimAt(src);
        Claim destClaim = claimManager.getClaimAt(dest);
        if (srcClaim != destClaim) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTakeLecternBook(PlayerTakeLecternBookEvent e) {
        if (!allowed(e.getPlayer(), e.getLectern().getLocation(), ClaimPermission.CONTAINERS)) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInsertLecternBook(io.papermc.paper.event.player.PlayerInsertLecternBookEvent e) {
        if (!allowed(e.getPlayer(), e.getLectern().getLocation(), ClaimPermission.CONTAINERS)) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (e.getPlayer() == null) {
            Claim claim = claimManager.getClaimAt(e.getBlock().getLocation());
            if (claim != null) {
                if (e.getBlock().getType() == Material.TNT || !allowsExplosions(e.getBlock().getLocation())) {
                    e.setCancelled(true);
                }
            }
            return;
        }
        if (!allowed(e.getPlayer(), e.getBlock().getLocation(), ClaimPermission.FIRE_EXPLOSIONS)) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent e) {
        Claim claim = claimManager.getClaimAt(e.getBlock().getLocation());
        if (claim != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent e) {
        if (e.getSource().getType() == Material.FIRE) {
            Claim toClaim = claimManager.getClaimAt(e.getBlock().getLocation());
            Claim fromClaim = claimManager.getClaimAt(e.getSource().getLocation());
            if (toClaim != null && toClaim != fromClaim) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) {
        if (!(e.getBlock().getBlockData() instanceof Dispenser dispData)) {
            return;
        }
        BlockFace face = dispData.getFacing();
        Block targetBlock = e.getBlock().getRelative(face);

        Claim fromClaim = claimManager.getClaimAt(e.getBlock().getLocation());
        Claim toClaim = claimManager.getClaimAt(targetBlock.getLocation());

        if (toClaim != null && toClaim != fromClaim) {
            Material item = e.getItem().getType();
            if (item == Material.TNT || item == Material.LAVA_BUCKET || item == Material.WATER_BUCKET
                    || item == Material.FIRE_CHARGE || item == Material.FLINT_AND_STEEL
                    || item.name().endsWith("_MINECART") || item.name().endsWith("_BOAT")
                    || item.name().endsWith("_CHEST_BOAT") || item.name().endsWith("_RAFT")) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        Claim claim = claimManager.getClaimAt(e.getBlock().getLocation());
        if (claim == null) return;

        Entity entity = e.getEntity();
        if (entity instanceof Enderman || entity instanceof Wither || entity instanceof WitherSkull
                || entity instanceof Ravager || entity instanceof Silverfish) {
            e.setCancelled(true);
            return;
        }
        if (entity instanceof FallingBlock) {
            Claim from = claimManager.getClaimAt(entity.getLocation());
            if (from != claim) {
                e.setCancelled(true);
                entity.remove();
                return;
            }
        }
        if (entity instanceof Player player) {
            if (!allowed(player, e.getBlock().getLocation(), ClaimPermission.BUILD)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        if (e.getEntity().getShooter() instanceof Player player) {
            // Enderperlen und Wind Charges duerfen auf fremden Claims ohne ENTITY_INTERACT nicht gestartet werden
            if (e.getEntity() instanceof EnderPearl || e.getEntity() instanceof AbstractWindCharge) {
                if (!allowed(player, player.getLocation(), ClaimPermission.ENTITY_INTERACT)) {
                    e.setCancelled(true);
                    deny(player);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent e) {
        Player shooter = resolvePlayer(e.getEntity());
        Entity hitEntity = e.getHitEntity();
        Block hitBlock = e.getHitBlock();

        // 1. Wind Charge Abwehr (Block oder Entity-Treffer)
        if (e.getEntity() instanceof AbstractWindCharge) {
            Location loc = hitEntity != null ? hitEntity.getLocation() : (hitBlock != null ? hitBlock.getLocation() : e.getEntity().getLocation());
            if (shooter != null && !allowed(shooter, loc, ClaimPermission.ENTITY_INTERACT)) {
                e.setCancelled(true);
                e.getEntity().remove();
                deny(shooter);
                return;
            }
        }

        // 2. Enderperlen-Trefferort pruefen
        if (e.getEntity() instanceof EnderPearl) {
            Location loc = hitEntity != null ? hitEntity.getLocation() : (hitBlock != null ? hitBlock.getLocation() : e.getEntity().getLocation());
            if (shooter != null && !allowed(shooter, loc, ClaimPermission.ENTITY_INTERACT)) {
                e.setCancelled(true);
                e.getEntity().remove();
                deny(shooter);
                return;
            }
        }

        // 3. Projektil trifft eine geschuetzte Entity (ArmorStand, ItemFrame, Painting, Hanging, EndCrystal)
        if (hitEntity != null && shooter != null) {
            if (hitEntity instanceof ArmorStand || hitEntity instanceof ItemFrame || hitEntity instanceof Painting
                    || hitEntity instanceof Hanging || hitEntity instanceof EnderCrystal) {
                if (!allowed(shooter, hitEntity.getLocation(), ClaimPermission.ENTITY_INTERACT)) {
                    e.setCancelled(true);
                    e.getEntity().remove();
                    deny(shooter);
                    return;
                }
            }
        }

        if (hitBlock == null) return;

        if (e.getEntity().getFireTicks() > 0 || e.getEntity() instanceof Fireball || e.getEntity() instanceof SmallFireball) {
            if (hitBlock.getType() == Material.TNT) {
                if (shooter == null || !allowed(shooter, hitBlock.getLocation(), ClaimPermission.FIRE_EXPLOSIONS)) {
                    e.setCancelled(true);
                    e.getEntity().remove();
                    return;
                }
            }
            if (shooter != null && !allowed(shooter, hitBlock.getLocation(), ClaimPermission.BUILD)) {
                e.setCancelled(true);
                e.getEntity().remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent e) {
        Player shooter = null;
        if (e.getEntity().getShooter() instanceof Player p) {
            shooter = p;
        }
        if (shooter == null) return;

        for (LivingEntity affected : e.getAffectedEntities()) {
            if (affected instanceof Player || affected instanceof Monster) continue;
            if (!allowed(shooter, affected.getLocation(), ClaimPermission.ATTACK_MOBS)
                    && !allowed(shooter, affected.getLocation(), ClaimPermission.ENTITY_INTERACT)) {
                e.setIntensity(affected, 0.0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLightning(LightningStrikeEvent e) {
        Claim claim = claimManager.getClaimAt(e.getLightning().getLocation());
        if (claim != null && !allowsExplosions(e.getLightning().getLocation())) {
            if (e.getCause() == LightningStrikeEvent.Cause.TRIDENT) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player player = e.getPlayer();
        PlayerTeleportEvent.TeleportCause cause = e.getCause();

        // 1. Enderperlen-Teleportation: Sowohl Ziel als auch Start duerfen nicht auf fremden Claims ohne ENTITY_INTERACT liegen
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            if (!allowed(player, e.getTo(), ClaimPermission.ENTITY_INTERACT)
                    || !allowed(player, e.getFrom(), ClaimPermission.ENTITY_INTERACT)) {
                e.setCancelled(true);
                deny(player);
                return;
            }
        }

        // 2. Chorus-Frucht Teleportation
        String causeName = cause.name();
        if ("CHORUS_FRUIT".equals(causeName) || "CONSUMABLE_EFFECT".equals(causeName)) {
            Claim to = claimManager.getClaimAt(e.getTo());
            if (to != null && (to.isBanned(player.getUniqueId()) || !allowed(player, e.getTo(), ClaimPermission.ENTITY_INTERACT))) {
                e.setCancelled(true);
                deny(player);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (!allowed(e.getPlayer(), e.getPlayer().getLocation(), ClaimPermission.ITEMS)) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (!allowed(player, player.getLocation(), ClaimPermission.ITEMS)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        // 1. Wenn ein Monster einen Spieler angreift, registrieren wir dieses fuer Notwehr/Vergeltung
        if (e.getEntity() instanceof Player victim) {
            Entity source = e.getDamager();
            if (source instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
                source = shooter;
            }
            if (source instanceof Monster monster) {
                recentMonsterVictims.put(monster.getUniqueId() + ":" + victim.getUniqueId(), System.currentTimeMillis());
            }

            // Creeper & Explosions-Schaden pruefen: wenn Explosionen aus sind, keinen Schaden zufuegen!
            if (source instanceof Creeper || source instanceof TNTPrimed || source instanceof Explosive
                    || e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
                if (!allowsExplosions(victim.getLocation())) {
                    e.setCancelled(true);
                    return;
                }
            }
        }

        Player attacker = resolvePlayer(e.getDamager());

        // Wind Charge Schaden / Knockback an geschuetzten Entities blockieren
        if (e.getDamager() instanceof AbstractWindCharge) {
            if (attacker != null && !allowed(attacker, e.getEntity().getLocation(), ClaimPermission.ENTITY_INTERACT)) {
                e.setCancelled(true);
                return;
            }
        }

        // 2. Schaden an Endkristallen schuetzen
        if (e.getEntity() instanceof EnderCrystal crystal) {
            if (attacker == null || (!allowed(attacker, crystal.getLocation(), ClaimPermission.FIRE_EXPLOSIONS)
                    && !allowed(attacker, crystal.getLocation(), ClaimPermission.ENTITY_INTERACT))) {
                e.setCancelled(true);
                if (attacker != null) deny(attacker);
                return;
            }
        }

        // 3. Schaden an Ruestungsstaendern, Bilderrahmen, Gemaelden schuetzen (benoetigt zwingend ENTITY_INTERACT)
        if (e.getEntity() instanceof ArmorStand || e.getEntity() instanceof ItemFrame
                || e.getEntity() instanceof Painting || e.getEntity() instanceof Hanging) {
            if (attacker == null || !allowed(attacker, e.getEntity().getLocation(), ClaimPermission.ENTITY_INTERACT)) {
                e.setCancelled(true);
                if (attacker != null) deny(attacker);
                return;
            }
        }

        // 4. Schaden an Booten und Fahrzeugen schuetzen
        if (e.getEntity() instanceof Vehicle vehicle) {
            if (attacker == null || (!allowed(attacker, vehicle.getLocation(), ClaimPermission.BUILD)
                    && !allowed(attacker, vehicle.getLocation(), ClaimPermission.VEHICLES))) {
                e.setCancelled(true);
                if (attacker != null) deny(attacker);
            }
            return;
        }

        if (attacker == null) {
            if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || e.getDamager() instanceof Creeper) {
                if (!allowsExplosions(e.getEntity().getLocation())) {
                    e.setCancelled(true);
                }
            }
            return;
        }

        // 5. Schaden an Spielern (PvP)
        if (e.getEntity() instanceof Player victim) {
            // PvP-Claim-Bypass pruefen (Admin-Befehl /pvpclaimbypass)
            if (pvpClaimBypass.isClaimBypassed(victim.getLocation())
                    || pvpClaimBypass.isClaimBypassed(attacker.getLocation())
                    || pvpClaimBypass.isPlayerBypassed(attacker)
                    || pvpClaimBypass.isPlayerBypassed(victim)) {
                return;
            }

            // pvpoverride und staffclaim bypass erlauben Angriffe in Claims
            var pvpMgr = de.Z7534.ztone.Ztone.getInstance().getPvpManager();
            if (pvpMgr != null && pvpMgr.getOverrideManager().isOverriding(attacker.getUniqueId())) {
                return;
            }
            if (bypass.isActive(attacker)) {
                return;
            }

            // Clankrieg: Kriegsgegner duerfen sich immer bekaempfen
            if (de.Z7534.ztone.Ztone.getInstance().areWarOpponentsWithPvP(attacker.getUniqueId(), victim.getUniqueId())) {
                return;
            }

            Claim victimClaim = claimManager.getClaimAt(victim.getLocation());
            Claim attackerClaim = claimManager.getClaimAt(attacker.getLocation());

            // Ohne Claim darf PvP NIEMALS aktiv sein! PvP ist ausschliesslich auf Claims mit beidseitig aktiviertem PvP-Recht erlaubt.
            if (victimClaim == null || attackerClaim == null) {
                e.setCancelled(true);
                deny(attacker);
                return;
            }

            if (!allowed(attacker, victim.getLocation(), ClaimPermission.PVP)
                    || !allowed(victim, victim.getLocation(), ClaimPermission.PVP)
                    || !allowed(attacker, attacker.getLocation(), ClaimPermission.PVP)
                    || !allowed(victim, attacker.getLocation(), ClaimPermission.PVP)) {
                e.setCancelled(true);
                deny(attacker);
            }
            return;
        }

        // 6. Schaden an Monstern: Wenn das Monster den Spieler angegriffen hat oder anvisiert -> ERLAUBT!
        if (e.getEntity() instanceof Monster monster) {
            if (isRetaliating(attacker, monster)) {
                return; // Notwehr erlaubt, auch ohne Claim-Rechte!
            }
            if (!allowed(attacker, monster.getLocation(), ClaimPermission.ATTACK_MOBS)) {
                e.setCancelled(true);
                deny(attacker);
            }
            return;
        }

        // 7. Schaden an sonstigen Lebewesen (Tiere, Dorfbewohner etc.)
        if (e.getEntity() instanceof LivingEntity) {
            if (e.getEntity() instanceof Tameable tameable && tameable.isTamed() && tameable.getOwner() != null) {
                java.util.UUID ownerUuid = tameable.getOwner().getUniqueId();
                if (!attacker.getUniqueId().equals(ownerUuid)) {
                    var pvpManager = de.Z7534.ztone.Ztone.getInstance().getPvpManager();
                    if (pvpManager != null && (!pvpManager.canPvP(ownerUuid) || !pvpManager.canPvP(attacker))) {
                        e.setCancelled(true);
                        deny(attacker);
                        return;
                    }
                }
            }
            if (!allowed(attacker, e.getEntity().getLocation(), ClaimPermission.ATTACK_MOBS)) {
                e.setCancelled(true);
                deny(attacker);
            }
        }
    }

    private boolean isRetaliating(Player attacker, Monster monster) {
        if (monster.getTarget() != null && monster.getTarget().getUniqueId().equals(attacker.getUniqueId())) {
            return true;
        }
        Long attackedAt = recentMonsterVictims.get(monster.getUniqueId() + ":" + attacker.getUniqueId());
        return attackedAt != null && (System.currentTimeMillis() - attackedAt) < 30_000L;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGeneralDamage(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || e.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            if (!allowsExplosions(e.getEntity().getLocation())) {
                e.setCancelled(true);
            }
        }
    }

    private Player resolvePlayer(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p2) return p2;
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent e) {
        Player player = resolvePlayer(e.getAttacker());
        if (player == null) return;
        if (!allowed(player, e.getVehicle().getLocation(), ClaimPermission.BUILD)
                && !allowed(player, e.getVehicle().getLocation(), ClaimPermission.VEHICLES)) {
            e.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent e) {
        Player player = resolvePlayer(e.getAttacker());
        if (player == null) return;
        if (!allowed(player, e.getVehicle().getLocation(), ClaimPermission.BUILD)
                && !allowed(player, e.getVehicle().getLocation(), ClaimPermission.VEHICLES)) {
            e.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent e) {
        if (!(e.getEntered() instanceof Player player)) return;
        if (!allowed(player, e.getVehicle().getLocation(), ClaimPermission.VEHICLES)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onVehicleMove(VehicleMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if ((from.getBlockX() >> 4) == (to.getBlockX() >> 4) && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4)) {
            return;
        }
        Vehicle vehicle = e.getVehicle();
        if (vehicle.getPassengers().isEmpty()) return;

        Player player = null;
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player p) {
                player = p;
                break;
            }
        }
        if (player == null) return;
        if (claimManager.getClaimAt(to) == null) return;
        if (allowed(player, to, ClaimPermission.VEHICLES)) return;

        List<Entity> passengers = new ArrayList<>(vehicle.getPassengers());
        vehicle.eject();
        vehicle.teleport(from);
        for (Entity passenger : passengers) {
            passenger.teleport(from);
        }
        player.sendMessage(Text.line(config.prefix() + config.message("action-denied-vehicle")));
    }

    @EventHandler(ignoreCancelled = true)
    public void onWitherSpawn(CreatureSpawnEvent e) {
        if (e.getEntityType() != EntityType.WITHER) return;
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.BUILD_WITHER) return;
        if (claimManager.getClaimAt(e.getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent e) {
        Location explosionLocation = e.getEntity().getLocation();

        if (!allowsExplosions(explosionLocation)) {
            e.setCancelled(true);
            if (e.getEntity() instanceof TNTPrimed tnt) {
                tnt.remove();
            }
            return;
        }

        int radius = config.explosionClaimBorderRadius();
        for (Claim nearby : claimManager.getClaimsWithinRadius(explosionLocation, radius)) {
            boolean ownerAllowsExplosions = claimManager.findPlayerData(nearby.owner())
                    .map(PlayerData::explosionsEnabled)
                    .orElse(false);
            if (!ownerAllowsExplosions) {
                e.setCancelled(true);
                if (e.getEntity() instanceof TNTPrimed tnt) {
                    tnt.remove();
                }
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        Location explosionLocation = e.getLocation();

        if (!allowsExplosions(explosionLocation)) {
            e.setCancelled(true);
            e.blockList().clear();
            return;
        }

        int radius = config.explosionClaimBorderRadius();
        for (Claim nearby : claimManager.getClaimsWithinRadius(explosionLocation, radius)) {
            boolean ownerAllowsExplosions = claimManager.findPlayerData(nearby.owner())
                    .map(PlayerData::explosionsEnabled)
                    .orElse(false);
            if (!ownerAllowsExplosions) {
                e.setCancelled(true);
                e.blockList().clear();
                return;
            }
        }

        e.blockList().removeIf(block -> !allowsExplosions(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        Location explosionLocation = e.getBlock().getLocation();

        if (!allowsExplosions(explosionLocation)) {
            e.setCancelled(true);
            e.blockList().clear();
            return;
        }

        int radius = config.explosionClaimBorderRadius();
        for (Claim nearby : claimManager.getClaimsWithinRadius(explosionLocation, radius)) {
            boolean ownerAllowsExplosions = claimManager.findPlayerData(nearby.owner())
                    .map(PlayerData::explosionsEnabled)
                    .orElse(false);
            if (!ownerAllowsExplosions) {
                e.setCancelled(true);
                e.blockList().clear();
                return;
            }
        }

        e.blockList().removeIf(block -> !allowsExplosions(block.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        Entity target = e.getRightClicked();
        Player player = e.getPlayer();

        if (target instanceof Vehicle) {
            if (!allowed(player, target.getLocation(), ClaimPermission.ENTITY_INTERACT)
                    && !allowed(player, target.getLocation(), ClaimPermission.VEHICLES)) {
                e.setCancelled(true);
                deny(player);
            }
            return;
        }

        if (!allowed(player, target.getLocation(), ClaimPermission.ENTITY_INTERACT)) {
            e.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteractAt(PlayerInteractAtEntityEvent e) {
        Entity target = e.getRightClicked();
        Player player = e.getPlayer();

        if (target instanceof Vehicle) {
            if (!allowed(player, target.getLocation(), ClaimPermission.ENTITY_INTERACT)
                    && !allowed(player, target.getLocation(), ClaimPermission.VEHICLES)) {
                e.setCancelled(true);
                deny(player);
            }
            return;
        }

        if (!allowed(player, target.getLocation(), ClaimPermission.ENTITY_INTERACT)) {
            e.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent e) {
        if (!allowed(e.getPlayer(), e.getRightClicked().getLocation(), ClaimPermission.ENTITY_INTERACT)) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent e) {
        if (!allowed(e.getPlayer(), e.getEntity().getLocation(), ClaimPermission.ENTITY_INTERACT)) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent e) {
        if (!allowed(e.getPlayer(), e.getEntity().getLocation(), ClaimPermission.ENTITY_INTERACT)) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onUnleash(PlayerUnleashEntityEvent e) {
        if (!allowed(e.getPlayer(), e.getEntity().getLocation(), ClaimPermission.ENTITY_INTERACT)) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        Entity caught = e.getCaught();
        if (caught != null) {
            if (!allowed(e.getPlayer(), caught.getLocation(), ClaimPermission.ENTITY_INTERACT)) {
                e.setCancelled(true);
                deny(e.getPlayer());
            }
        }
    }
}
