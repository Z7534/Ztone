package de.Z7534.ztone.stonesit.listener;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.stonesit.config.StoneSitConfig;
import de.Z7534.ztone.stonesit.manager.SitManager;
import de.Z7534.ztone.stonesit.model.Seat;
import de.Z7534.ztone.stonesit.model.StoneSitPose;
import de.Z7534.ztone.stonesit.model.StopReason;
import de.Z7534.ztone.stonesit.util.EnvironmentUtil;
import de.Z7534.ztone.stonesit.util.PassengerUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import de.Z7534.ztone.claims.model.Claim;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SitListener implements Listener {

    private final Ztone plugin;
    private final StoneSitConfig config;
    private final SitManager sitManager;
    private final EnvironmentUtil environmentUtil;
    private final Attribute blockInteractionRangeAttribute;

    public SitListener(Ztone plugin, StoneSitConfig config, SitManager sitManager, EnvironmentUtil environmentUtil) {
        this.plugin = plugin;
        this.config = config;
        this.sitManager = sitManager;
        this.environmentUtil = environmentUtil;
        this.blockInteractionRangeAttribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("block_interaction_range"));
    }

    // =========================================================================
    //                        RECHTSKLICK AUF BLÖCKE (SIT)
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractBlock(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getBlockFace() != BlockFace.UP) {
            return;
        }
        if (config.isSitEmptyHandOnly() && event.getItem() != null && event.getItem().getType() != Material.AIR) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!EnvironmentUtil.hasPermission(player, "stonesit.sitclick", "stonesit.sit.*", "GSit.SitClick", "GSit.Sit.*")) {
            return;
        }

        BlockData blockData = clickedBlock.getBlockData();
        if (!sitManager.isValidSitBlockData(blockData) || sitManager.isBlacklistedSitBlockData(blockData)) {
            return;
        }

        if (!player.isValid() || player.isSneaking() || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (!environmentUtil.isEntityInAllowedWorld(player)) {
            return;
        }

        Seat existingSeat = sitManager.getSeatByEntity(player);
        if (existingSeat != null && existingSeat.getBlock().equals(clickedBlock)) {
            return;
        }

        StoneSitPose existingPose = sitManager.getPoseByPlayer(player);
        if (existingPose != null && existingPose.getSeat().getBlock().equals(clickedBlock)) {
            return;
        }

        double maxDistanceSquared = config.getSitMaxDistanceSquared();
        Location blockLoc = clickedBlock.getLocation();
        if (maxDistanceSquared > 0d) {
            Location playerLoc = player.getLocation();
            double dx = (blockLoc.getX() + 0.5d) - playerLoc.getX();
            double dy = (blockLoc.getY() + 0.5d) - playerLoc.getY();
            double dz = (blockLoc.getZ() + 0.5d) - playerLoc.getZ();
            if ((dx * dx + dy * dy + dz * dz) > maxDistanceSquared) {
                return;
            }
        }

        if (!config.isAllowUnsafe() && !clickedBlock.getRelative(BlockFace.UP).isPassable()) {
            return;
        }

        if (!environmentUtil.canUseInLocation(blockLoc, player)) {
            return;
        }

        double range = 5.0d;
        if (blockInteractionRangeAttribute != null) {
            AttributeInstance attr = player.getAttribute(blockInteractionRangeAttribute);
            if (attr != null) {
                range = attr.getValue();
            }
        }
        RayTraceResult rayTrace = player.rayTraceBlocks(range);
        if (rayTrace != null) {
            BlockFace hitFace = rayTrace.getHitBlockFace();
            if (hitFace != null && hitFace != BlockFace.UP) {
                return;
            }
            Block hitBlock = rayTrace.getHitBlock();
            if (hitBlock != null && !clickedBlock.equals(hitBlock)) {
                return;
            }
        }

        if (!config.isSameBlockRest()
                && (!sitManager.kickSeatEntitiesFromBlock(clickedBlock, player)
                || !sitManager.kickPoseEntitiesFromBlock(clickedBlock, player))) {
            return;
        }

        boolean useCenter = config.isCenterBlock();
        double xOffset = useCenter ? 0d : -0.5d;
        double zOffset = xOffset;

        if (!useCenter) {
            try {
                Vector clickedPos = event.getClickedPosition();
                if (clickedPos != null) {
                    useCenter = true;
                    xOffset += clickedPos.getX() - clickedPos.getBlockX();
                    zOffset += clickedPos.getZ() - clickedPos.getBlockZ();
                }
            } catch (Throwable ignored) {
            }
        }
        if (!useCenter) {
            try {
                Location interactionPoint = event.getInteractionPoint();
                if (interactionPoint != null) {
                    useCenter = true;
                    xOffset += interactionPoint.getX() - interactionPoint.getBlockX();
                    zOffset += interactionPoint.getZ() - interactionPoint.getBlockZ();
                }
            } catch (Throwable ignored) {
            }
        }

        if (sitManager.createCustomSeat(clickedBlock, player, false, true, useCenter ? xOffset : 0d, 0d, useCenter ? zOffset : 0d, player.getLocation().getYaw(), true) != null) {
            event.setCancelled(true);
        }
    }

    // =========================================================================
    //                      RECHTSKLICK AUF SPIELER (PLAYERSIT)
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity rightClicked = event.getRightClicked();
        if (!(rightClicked instanceof Player target)) {
            return;
        }

        if (!config.isPlayerSitAllow() && !config.isPlayerSitAllowNpc()) {
            return;
        }

        Player player = event.getPlayer();
        if (!EnvironmentUtil.hasPermission(player, "stonesit.playersit", "stonesit.playersit.*", "GSit.PlayerSit", "GSit.PlayerSit.*")) {
            return;
        }

        if (config.isPlayerSitEmptyHandOnly() && player.getInventory().getItemInMainHand().getType() != Material.AIR) {
            return;
        }

        if (!player.isValid() || !target.isValid() || player.isSneaking() || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (!environmentUtil.isEntityInAllowedWorld(player)) {
            return;
        }

        double maxDistanceSquared = config.getPlayerSitMaxDistanceSquared();
        if (maxDistanceSquared > 0d) {
            Location tLoc = target.getLocation();
            Location pLoc = player.getLocation();
            double dx = tLoc.getX() - pLoc.getX();
            double dy = (tLoc.getY() + target.getHeight() * 0.5d) - (pLoc.getY() + player.getHeight() * 0.5d);
            double dz = tLoc.getZ() - pLoc.getZ();
            if ((dx * dx + dy * dy + dz * dz) > maxDistanceSquared) {
                return;
            }
        }

        if (!environmentUtil.canUseInLocation(target.getLocation(), player)) {
            return;
        }

        if (PassengerUtil.isEntityInPassengerList(target, player) || PassengerUtil.isEntityInPassengerList(player, target)) {
            return;
        }

        long maxStack = config.getPlayerSitMaxStack();
        if (maxStack > 0L) {
            long amount = PassengerUtil.getEntityPassengerCount(target) + 1
                    + PassengerUtil.getEntityVehicleCount(target)
                    + PassengerUtil.getEntityPassengerCount(player);
            if (maxStack <= amount) {
                return;
            }
        }

        Entity highestEntity = PassengerUtil.getTopEntityPassenger(target);
        if (!(highestEntity instanceof Player highestPlayer)) {
            return;
        }

        boolean isNpc = !Bukkit.getOnlinePlayers().contains(highestPlayer);
        if ((isNpc && !config.isPlayerSitAllowNpc()) || (!isNpc && !config.isPlayerSitAllow())) {
            return;
        }

        var claimsModule = plugin.getClaims();
        if (claimsModule != null && claimsModule.getClaimManager() != null) {
            Claim claim = claimsModule.getClaimManager().getClaimAt(target.getLocation());
            if (claim != null) {
                boolean hasBypass = claimsModule.getBypassManager() != null && claimsModule.getBypassManager().isActive(player);
                if (!hasBypass && claim.isBanned(player.getUniqueId())) {
                    player.sendMessage(config.getMessage("action-playersit-target-banned-claim",
                            "<prefix> <red>Du kannst dich hier nicht auf andere Spieler setzen, da du auf diesem Claim gebannt bist!</red>"));
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (sitManager.sitOnPlayer(player, highestPlayer)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }
        Player player = event.getPlayer();
        if (PassengerUtil.isEntityInPassengerList(target, player)) {
            event.setCancelled(true);
        }
    }

    // =========================================================================
    //                   ZENTRALE POSE-EVENTS (O(1) FAST-EXIT)
    // =========================================================================

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPosePlayerInteract(PlayerInteractEvent event) {
        if (config.isPoseInteract() || sitManager.getAllPoses().isEmpty()) {
            return;
        }
        if (sitManager.isPlayerPosing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPosePlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (sitManager.getAllPoses().isEmpty()) {
            return;
        }
        if (sitManager.isPlayerPosing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPoseEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (config.isPoseInteract() || sitManager.getAllPoses().isEmpty()) {
            return;
        }
        if (event.getDamager() instanceof Player damager && sitManager.isPlayerPosing(damager)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPoseProjectileLaunch(ProjectileLaunchEvent event) {
        if (config.isPoseInteract() || sitManager.getAllPoses().isEmpty()) {
            return;
        }
        if (event.getEntity().getShooter() instanceof Player shooter && sitManager.isPlayerPosing(shooter)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPosePlayerAnimation(PlayerAnimationEvent event) {
        if (sitManager.getAllPoses().isEmpty()) {
            return;
        }
        StoneSitPose pose = sitManager.getPoseByPlayer(event.getPlayer());
        if (pose != null) {
            pose.playHandAnimation(event.getAnimationType() == PlayerAnimationType.ARM_SWING
                    ? StoneSitPose.SWING_MAIN_HAND
                    : StoneSitPose.SWING_OFF_HAND);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPoseInventoryClick(InventoryClickEvent event) {
        if (sitManager.getAllPoses().isEmpty()) {
            return;
        }
        if (event.getWhoClicked() instanceof Player player
                && player.getGameMode() == GameMode.CREATIVE
                && sitManager.isPlayerPosing(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPosePlayerDropItem(PlayerDropItemEvent event) {
        if (sitManager.getAllPoses().isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE && sitManager.isPlayerPosing(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPoseEntityPotionEffect(EntityPotionEffectEvent event) {
        if (sitManager.getAllPoses().isEmpty()) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            StoneSitPose pose = sitManager.getPoseByPlayer(player);
            if (pose != null) {
                pose.keepInvisible();
            }
        }
    }

    // =========================================================================
    //             SNEAK-ABWURF MIT ADMIN-BYPASS (NICHT VOM KOPF KICKBAR)
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        if (!config.isPlayerSitSneakEjects() || !event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isFlying()
                || player.getVehicle() != null
                || sitManager.getPreventDismountStackPlayers().contains(player.getUniqueId())
                || player.getPassengers().isEmpty()) {
            return;
        }

        // Admin-Schutz: Wenn ein Admin mit stonesit.bypass.sneakeject auf dem Kopf sitzt,
        // kann er durch Sneaken NICHT vom Kopf geworfen werden!
        if (sitManager.hasSneakEjectProtectedPassenger(player)) {
            if (sitManager.hasVisibleSneakEjectProtectedPassenger(player)) {
                config.sendMessage(player, "action-playersit-admin-protected");
            }
            return;
        }

        sitManager.stopPlayerSit(player, StopReason.KICKED, true, false);
    }

    // =========================================================================
    //                       MOUNT & DISMOUNT EVENTS
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Seat seat = sitManager.getSeatByEntity(player);
        if (seat != null && !seat.getSeatEntity().equals(event.getMount())) {
            if (!sitManager.removeSeat(seat, StopReason.SWITCH, false)) {
                event.setCancelled(true);
                return;
            }
        }
        StoneSitPose pose = sitManager.getPoseByPlayer(player);
        if (pose != null && !pose.getSeat().getSeatEntity().equals(event.getMount())) {
            if (!sitManager.removePose(pose, StopReason.SWITCH, false)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        Seat seat = sitManager.getSeatByEntity(player);
        if (seat != null) {
            if (!config.isGetUpSneak() || !sitManager.removeSeat(seat, StopReason.GET_UP, true)) {
                event.setCancelled(true);
            }
            return;
        }

        StoneSitPose pose = sitManager.getPoseByPlayer(player);
        if (pose != null) {
            if (!config.isGetUpSneak() || !sitManager.removePose(pose, StopReason.GET_UP, true)) {
                event.setCancelled(true);
            }
            return;
        }

        Entity dismounted = event.getDismounted();
        if (!dismounted.getScoreboardTags().contains(SitManager.PLAYERSIT_ENTITY_TAG)
                || !sitManager.isPlayerInPlayerSitStack(player)) {
            return;
        }

        sitManager.getPreventDismountStackPlayers().add(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                sitManager.getPreventDismountStackPlayers().remove(player.getUniqueId()), 2L);

        Entity bottom = PassengerUtil.getBottomEntityVehicle(dismounted);
        if (config.isPlayerSitBottomReturn() && player.isValid() && bottom != null) {
            sitManager.setEntityLocation(player, bottom.getLocation());
        }

        sitManager.stopPlayerSit(player, StopReason.GET_UP, false, true);
    }

    // =========================================================================
    //               SPIELER-LEBENSZYKLUS (QUIT, DEATH, TP, DAMAGE)
    // =========================================================================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Seat seat = sitManager.getSeatByEntity(player);
        if (seat != null) {
            sitManager.removeSeat(seat, StopReason.DISCONNECT, true);
        }
        StoneSitPose pose = sitManager.getPoseByPlayer(player);
        if (pose != null) {
            sitManager.removePose(pose, StopReason.DISCONNECT, true);
        }
        sitManager.stopPlayerSit(player, StopReason.DISCONNECT);
        sitManager.getPreventDismountStackPlayers().remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Seat seat = sitManager.getSeatByEntity(player);
        if (seat != null) {
            sitManager.removeSeat(seat, StopReason.DEATH, false);
        }
        StoneSitPose pose = sitManager.getPoseByPlayer(player);
        if (pose != null) {
            sitManager.removePose(pose, StopReason.DEATH, false);
        }
        sitManager.stopPlayerSit(player, StopReason.DEATH);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        if (sitManager.isPlayerInPlayerSitStack(player)) {
            sitManager.checkPlayerSitStackBans(player, to);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (sitManager.isInternalTeleporting(player.getUniqueId())) {
            return;
        }
        Seat seat = sitManager.getSeatByEntity(player);
        if (seat != null) {
            sitManager.removeSeat(seat, StopReason.TELEPORT, false);
        }
        StoneSitPose pose = sitManager.getPoseByPlayer(player);
        if (pose != null) {
            sitManager.removePose(pose, StopReason.TELEPORT, false);
        }
        sitManager.stopPlayerSit(player, StopReason.TELEPORT);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.SPECTATOR) {
            Player player = event.getPlayer();
            Seat seat = sitManager.getSeatByEntity(player);
            if (seat != null) {
                sitManager.removeSeat(seat, StopReason.GAMEMODE_CHANGE, true);
            }
            StoneSitPose pose = sitManager.getPoseByPlayer(player);
            if (pose != null) {
                sitManager.removePose(pose, StopReason.GAMEMODE_CHANGE, true);
            }
            sitManager.stopPlayerSit(player, StopReason.GAMEMODE_CHANGE);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFallDamageInPlayerSit(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && event.getEntity() instanceof LivingEntity
                && event.getEntity().getVehicle() != null
                && event.getEntity().getVehicle().getScoreboardTags().contains(SitManager.PLAYERSIT_ENTITY_TAG)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        StoneSitPose pose = sitManager.getPoseByPlayer(player);
        if (pose != null) {
            pose.playDamageAnimation();
        }
        if (!config.isGetUpDamage() || event.getDamage() <= 0d) {
            return;
        }
        Seat seat = sitManager.getSeatByEntity(player);
        if (seat != null) {
            sitManager.removeSeat(seat, StopReason.DAMAGE, true);
        }
        if (pose != null) {
            sitManager.removePose(pose, StopReason.DAMAGE, true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Set<String> blacklist = config.getCommandBlacklist();
        if (blacklist.isEmpty()) {
            return;
        }
        String message = event.getMessage();
        if (message.length() <= 1) {
            return;
        }
        Player player = event.getPlayer();
        if (!sitManager.isEntitySitting(player)
                && !sitManager.isPlayerPosing(player)
                && !sitManager.isPlayerInPlayerSitStack(player)) {
            return;
        }
        int spaceIdx = message.indexOf(' ');
        String cmd = (spaceIdx > 1 ? message.substring(1, spaceIdx) : message.substring(1)).toLowerCase(Locale.ROOT);
        if (!blacklist.contains(cmd)) {
            return;
        }
        if (EnvironmentUtil.hasPermission(player, "stonesit.bypass.command", "stonesit.bypass.*", "GSit.ByPass.Command", "GSit.ByPass.*")) {
            return;
        }
        config.sendMessage(player, "action-blocked-error");
        event.setCancelled(true);
    }

    // =========================================================================
    //                   BLOCK-EVENTS (BREAK, PISTON, EXPLODE)
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        handleBlockPistonEvent(event, event.getBlocks());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        handleBlockPistonEvent(event, event.getBlocks());
    }

    private void handleBlockPistonEvent(BlockPistonEvent event, List<Block> blocks) {
        if (sitManager.getAllSeats().isEmpty() && sitManager.getAllPoses().isEmpty()) {
            return;
        }
        Set<Seat> moveList = new HashSet<>();
        Set<StoneSitPose> breakPoses = new HashSet<>();
        for (Block block : blocks) {
            moveList.addAll(sitManager.getSeatsByBlock(block));
            if (config.isGetUpBreak()) {
                breakPoses.addAll(sitManager.getPosesByBlock(block));
            }
        }
        for (Seat seat : moveList) {
            sitManager.moveSeat(seat, event.getDirection());
        }
        for (StoneSitPose pose : breakPoses) {
            sitManager.removePose(pose, StopReason.BLOCK_BREAK);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplodeEvent(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplodeEvent(event.blockList());
    }

    private void handleExplodeEvent(List<Block> blocks) {
        if (!config.isGetUpBreak() || (sitManager.getAllSeats().isEmpty() && sitManager.getAllPoses().isEmpty())) {
            return;
        }
        blocksLoop:
        for (Block block : new ArrayList<>(blocks)) {
            for (Seat seat : new ArrayList<>(sitManager.getSeatsByBlock(block))) {
                if (!sitManager.removeSeat(seat, StopReason.BLOCK_BREAK)) {
                    blocks.remove(block);
                    continue blocksLoop;
                }
            }
            for (StoneSitPose pose : new ArrayList<>(sitManager.getPosesByBlock(block))) {
                if (!sitManager.removePose(pose, StopReason.BLOCK_BREAK)) {
                    blocks.remove(block);
                    break;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        handleBlockBreakLikeEvent(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        handleBlockBreakLikeEvent(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        handleBlockBreakLikeEvent(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        handleBlockBreakLikeEvent(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        handleBlockBreakLikeEvent(event, event.getBlock());
    }

    private void handleBlockBreakLikeEvent(Cancellable event, Block block) {
        if (!config.isGetUpBreak()) {
            return;
        }
        if (sitManager.isBlockWithSeat(block)) {
            for (Seat seat : new ArrayList<>(sitManager.getSeatsByBlock(block))) {
                if (!sitManager.removeSeat(seat, StopReason.BLOCK_BREAK)) {
                    event.setCancelled(true);
                }
            }
        }
        if (!event.isCancelled() && sitManager.isBlockWithPose(block)) {
            for (StoneSitPose pose : new ArrayList<>(sitManager.getPosesByBlock(block))) {
                if (!sitManager.removePose(pose, StopReason.BLOCK_BREAK)) {
                    event.setCancelled(true);
                }
            }
        }
    }
}
