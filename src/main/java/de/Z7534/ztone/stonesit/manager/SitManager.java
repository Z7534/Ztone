package de.Z7534.ztone.stonesit.manager;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.util.Text;
import de.Z7534.ztone.stonesit.config.StoneSitConfig;
import de.Z7534.ztone.stonesit.model.PoseType;
import de.Z7534.ztone.stonesit.model.Seat;
import de.Z7534.ztone.stonesit.model.StoneSitPose;
import de.Z7534.ztone.stonesit.model.StopReason;
import de.Z7534.ztone.stonesit.util.EnvironmentUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class SitManager {

    public static final double STAIR_XZ_OFFSET = 0.123d;
    public static final double STAIR_Y_OFFSET = 0.5d;
    public static final double DIRECTIONAL_XZ_OFFSET = 0.2d;
    public static final double BASE_OFFSET = -0.025d;
    public static final String SIT_TAG = "StoneSit_Seat";
    public static final String POSE_TAG = "StoneSit_Pose";
    public static final String PLAYERSIT_ENTITY_TAG = "StoneSit_PlayerSitEntity";

    private final Ztone plugin;
    private final StoneSitConfig config;

    // Block-Sit Tracking (O(1) Lookups)
    private final Map<UUID, Seat> seats = new HashMap<>();
    private final Map<Block, Set<Seat>> blockSeats = new HashMap<>();

    // Spin Tracking (/spin & /gspin) -> Jetzt vollwertig als PoseType.SPIN in poses integriert

    // Pose Tracking (/lay & /legsup, O(1) Lookups)
    private final Map<UUID, StoneSitPose> poses = new HashMap<>();
    private final Map<Block, Set<StoneSitPose>> blockPoses = new HashMap<>();
    private BukkitTask poseTickTask;
    private int nightSkipCheckCounter = 0;

    // Player-Sit (Stack) Tracking (O(1) Lookups)
    private final Map<UUID, Map.Entry<UUID, List<UUID>>> bottomToTopStacks = new HashMap<>();
    private final Map<UUID, Map.Entry<UUID, List<UUID>>> topToBottomStacks = new HashMap<>();
    private final Set<UUID> preventDismountStackPlayers = new HashSet<>();
    private BukkitTask playerSitBanTask;

    // Internes Flag für sicheres Teleportieren beim Absteigen ohne Rekursion im TeleportListener
    private final Set<UUID> internalTeleporting = new HashSet<>();

    public SitManager(Ztone plugin, StoneSitConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    // =========================================================================
    //                               BLOCK SIT
    // =========================================================================

    public Map<UUID, Seat> getAllSeats() {
        return seats;
    }

    public boolean isEntitySitting(LivingEntity entity) {
        return entity != null && seats.containsKey(entity.getUniqueId());
    }

    public Seat getSeatByEntity(LivingEntity entity) {
        return entity != null ? seats.get(entity.getUniqueId()) : null;
    }

    public void removeAllSeats() {
        for (Seat seat : new ArrayList<>(seats.values())) {
            removeSeat(seat, StopReason.PLUGIN);
        }
        seats.clear();
        blockSeats.clear();
    }

    public boolean isBlockWithSeat(Block block) {
        return blockSeats.containsKey(block);
    }

    public Set<Seat> getSeatsByBlock(Block block) {
        return blockSeats.getOrDefault(block, Collections.emptySet());
    }

    public boolean kickSeatEntitiesFromBlock(Block block, LivingEntity kicker) {
        if (!isBlockWithSeat(block)) {
            return true;
        }
        if (!EnvironmentUtil.hasPermission(kicker, "stonesit.kick.sit", "stonesit.kick.*", "GSit.Kick.Sit", "GSit.Kick.*")) {
            return false;
        }
        for (Seat seat : new ArrayList<>(getSeatsByBlock(block))) {
            if (!removeSeat(seat, StopReason.KICKED)) {
                return false;
            }
        }
        return true;
    }

    public boolean isValidSitBlockData(BlockData blockData) {
        for (BlockData sitBlockData : config.getSitBlockData().keySet()) {
            if (sitBlockData.matches(blockData)) {
                return true;
            }
        }
        return config.getSitMaterials().containsKey(blockData.getMaterial());
    }

    public double getSitBlockDataHeightOffset(BlockData blockData) {
        for (Map.Entry<BlockData, Double> entry : config.getSitBlockData().entrySet()) {
            if (entry.getKey().matches(blockData)) {
                return entry.getValue();
            }
        }
        return config.getSitMaterials().getOrDefault(blockData.getMaterial(), 0d);
    }

    public boolean isBlacklistedSitBlockData(BlockData blockData) {
        for (BlockData blacklisted : config.getBlockDataBlacklist()) {
            if (blacklisted.matches(blockData)) {
                return true;
            }
        }
        return config.getMaterialBlacklist().contains(blockData.getMaterial());
    }

    public Seat createSeat(Block block, LivingEntity entity) {
        return createSeat(block, entity, true, 0d, 0d, 0d, entity.getLocation().getYaw(), config.isCenterBlock());
    }

    public Seat createSeat(Block block, LivingEntity entity, boolean canRotate, double xOffset, double yOffset, double zOffset, float seatRotation, boolean sitInBlockCenter) {
        if (entity == null || !entity.isValid()) {
            return null;
        }
        Location returnLocation = entity.getLocation();
        Location seatLocation = getSeatLocation(block, returnLocation, xOffset, yOffset, zOffset, sitInBlockCenter);
        seatLocation.setYaw(seatRotation);

        Entity seatEntity = spawnSeatEntity(seatLocation, entity);
        if (seatEntity == null) {
            return null;
        }

        if (config.isCustomMessage() && entity instanceof Player player) {
            config.sendActionBar(player, "action-sit-info");
        }

        Seat seat = new Seat(block, seatLocation, entity, seatEntity, returnLocation);
        seats.put(entity.getUniqueId(), seat);
        blockSeats.computeIfAbsent(block, b -> new HashSet<>()).add(seat);
        return seat;
    }

    private Entity spawnSeatEntity(Location location, LivingEntity rider) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        final boolean[] mounted = {false};
        ArmorStand armorStand = world.spawn(location, ArmorStand.class, stand -> {
            stand.setPersistent(false);
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setInvulnerable(true);
            stand.setSmall(true);
            stand.setBasePlate(false);
            stand.setSilent(true);
            stand.addScoreboardTag(SIT_TAG);
            mounted[0] = stand.addPassenger(rider);
        });
        if (!mounted[0] || !armorStand.isValid()) {
            armorStand.remove();
            return null;
        }
        return armorStand;
    }

    public Location getSeatLocation(Block block, Location location, double xOffset, double yOffset, double zOffset, boolean sitInBlockCenter) {
        double additionalOffset = sitInBlockCenter ? block.getBoundingBox().getMinY() + block.getBoundingBox().getHeight() : 0d;
        double sitBlockDataHeightOffset = getSitBlockDataHeightOffset(block.getBlockData());
        additionalOffset = (sitInBlockCenter ? (additionalOffset == 0d ? 1d : additionalOffset - block.getY()) : additionalOffset) + sitBlockDataHeightOffset;
        if (sitInBlockCenter) {
            return block.getLocation().add(0.5d + xOffset, yOffset - BASE_OFFSET + additionalOffset, 0.5d + zOffset);
        }
        return location.clone().add(xOffset, yOffset - BASE_OFFSET + sitBlockDataHeightOffset, zOffset);
    }

    public void moveSeat(Seat seat, BlockFace blockDirection) {
        if (seat.getEntity() instanceof Player player) {
            PlayerMoveEvent playerMoveEvent = new PlayerMoveEvent(
                    player,
                    player.getLocation(),
                    player.getLocation().add(blockDirection.getModX(), blockDirection.getModY(), blockDirection.getModZ())
            );
            Bukkit.getPluginManager().callEvent(playerMoveEvent);
            if (playerMoveEvent.isCancelled()) {
                return;
            }
        }

        Set<Seat> blockSeatList = blockSeats.get(seat.getBlock());
        if (blockSeatList != null) {
            blockSeatList.remove(seat);
            if (blockSeatList.isEmpty()) {
                blockSeats.remove(seat.getBlock());
            }
        }
        seat.setBlock(seat.getBlock().getRelative(blockDirection));
        blockSeats.computeIfAbsent(seat.getBlock(), b -> new HashSet<>()).add(seat);
        seat.setLocation(seat.getLocation().add(blockDirection.getModX(), blockDirection.getModY(), blockDirection.getModZ()));
        setEntityLocation(seat.getSeatEntity(), seat.getLocation());
    }

    public boolean removeSeat(Seat seat, StopReason stopReason) {
        return removeSeat(seat, stopReason, true);
    }

    public boolean removeSeat(Seat seat, StopReason stopReason, boolean useSafeDismount) {
        if (seat == null) {
            return true;
        }
        LivingEntity entity = seat.getEntity();
        seats.remove(entity.getUniqueId());

        Set<Seat> blockSeatList = blockSeats.get(seat.getBlock());
        if (blockSeatList != null) {
            blockSeatList.remove(seat);
            if (blockSeatList.isEmpty()) {
                blockSeats.remove(seat.getBlock());
            }
        }

        if (useSafeDismount) {
            handleSafeSeatDismount(seat);
        }

        Entity seatEntity = seat.getSeatEntity();
        if (seatEntity != null) {
            if (!stopReason.isUsingEntityTask() || !plugin.isEnabled()) {
                seatEntity.remove();
            } else {
                Bukkit.getScheduler().runTask(plugin, seatEntity::remove);
            }
        }
        return true;
    }

    public void handleSafeSeatDismount(Seat seat) {
        Location returnLocation;
        if (config.isGetUpReturn()) {
            returnLocation = seat.getReturnLocation();
        } else {
            BlockData blockData = seat.getBlock().getBlockData();
            double sitBlockDataHeightOffset = getSitBlockDataHeightOffset(blockData);
            returnLocation = seat.getLocation().add(
                    0d,
                    BASE_OFFSET + (blockData instanceof Stairs ? STAIR_Y_OFFSET : 0d) - sitBlockDataHeightOffset,
                    0d
            );
        }

        LivingEntity entity = seat.getEntity();
        if (entity != null && entity.isValid()) {
            Location entityLocation = entity.getLocation();
            returnLocation.setYaw(entityLocation.getYaw());
            returnLocation.setPitch(entityLocation.getPitch());
            setEntityLocation(entity, returnLocation);
        }
    }

    public void setEntityLocation(Entity entity, Location location) {
        if (entity == null || location == null || !entity.isValid()) {
            return;
        }
        UUID uuid = entity.getUniqueId();
        internalTeleporting.add(uuid);
        try {
            entity.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } catch (Throwable ignored) {
        } finally {
            internalTeleporting.remove(uuid);
        }
    }

    public boolean isInternalTeleporting(UUID uuid) {
        return internalTeleporting.contains(uuid);
    }

    public Seat createCustomSeat(Block block, LivingEntity entity, boolean force) {
        return createCustomSeat(block, entity, force, true, 0d, 0d, 0d, entity.getLocation().getYaw(), config.isCenterBlock());
    }

    public Seat createCustomSeat(Block block, LivingEntity entity, boolean force, boolean canRotate, double xOffset, double yOffset, double zOffset, float seatRotation, boolean sitInBlockCenter) {
        BlockData blockData = block.getBlockData();

        if (!force && blockData instanceof Slab slab && slab.getType() != Slab.Type.BOTTOM && config.isBottomPartOnly()) {
            return null;
        }

        if (blockData instanceof Stairs stair) {
            if (stair.getHalf() == Bisected.Half.BOTTOM) {
                return createStairSeat(block, stair, entity);
            } else if (!force && config.isBottomPartOnly()) {
                return null;
            }
        }

        if (block.getType().name().equals("SHELF_MUSHROOM") && blockData instanceof Directional directional) {
            return createDirectionalSeat(block, directional, entity);
        }

        return createSeat(block, entity, canRotate, xOffset, yOffset, zOffset, seatRotation, sitInBlockCenter);
    }

    private Seat createStairSeat(Block block, Stairs blockData, LivingEntity entity) {
        if (blockData.getHalf() != Bisected.Half.BOTTOM) {
            return createSeat(block, entity);
        }

        BlockFace blockFace = blockData.getFacing().getOppositeFace();
        Stairs.Shape stairShape = blockData.getShape();

        if (stairShape == Stairs.Shape.STRAIGHT) {
            return switch (blockFace) {
                case EAST -> createSeat(block, entity, false, STAIR_XZ_OFFSET, -STAIR_Y_OFFSET, 0d, -90f, true);
                case SOUTH -> createSeat(block, entity, false, 0d, -STAIR_Y_OFFSET, STAIR_XZ_OFFSET, 0f, true);
                case WEST -> createSeat(block, entity, false, -STAIR_XZ_OFFSET, -STAIR_Y_OFFSET, 0d, 90f, true);
                case NORTH -> createSeat(block, entity, false, 0d, -STAIR_Y_OFFSET, -STAIR_XZ_OFFSET, 180f, true);
                default -> null;
            };
        }

        if ((blockFace == BlockFace.NORTH && stairShape == Stairs.Shape.OUTER_RIGHT)
                || (blockFace == BlockFace.EAST && stairShape == Stairs.Shape.OUTER_LEFT)
                || (blockFace == BlockFace.NORTH && stairShape == Stairs.Shape.INNER_RIGHT)
                || (blockFace == BlockFace.EAST && stairShape == Stairs.Shape.INNER_LEFT)) {
            return createSeat(block, entity, false, STAIR_XZ_OFFSET, -STAIR_Y_OFFSET, -STAIR_XZ_OFFSET, -135f, true);
        } else if ((blockFace == BlockFace.NORTH && stairShape == Stairs.Shape.OUTER_LEFT)
                || (blockFace == BlockFace.WEST && stairShape == Stairs.Shape.OUTER_RIGHT)
                || (blockFace == BlockFace.NORTH && stairShape == Stairs.Shape.INNER_LEFT)
                || (blockFace == BlockFace.WEST && stairShape == Stairs.Shape.INNER_RIGHT)) {
            return createSeat(block, entity, false, -STAIR_XZ_OFFSET, -STAIR_Y_OFFSET, -STAIR_XZ_OFFSET, 135f, true);
        } else if ((blockFace == BlockFace.SOUTH && stairShape == Stairs.Shape.OUTER_RIGHT)
                || (blockFace == BlockFace.WEST && stairShape == Stairs.Shape.OUTER_LEFT)
                || (blockFace == BlockFace.SOUTH && stairShape == Stairs.Shape.INNER_RIGHT)
                || (blockFace == BlockFace.WEST && stairShape == Stairs.Shape.INNER_LEFT)) {
            return createSeat(block, entity, false, -STAIR_XZ_OFFSET, -STAIR_Y_OFFSET, STAIR_XZ_OFFSET, 45f, true);
        } else if ((blockFace == BlockFace.SOUTH && stairShape == Stairs.Shape.OUTER_LEFT)
                || (blockFace == BlockFace.EAST && stairShape == Stairs.Shape.OUTER_RIGHT)
                || (blockFace == BlockFace.SOUTH && stairShape == Stairs.Shape.INNER_LEFT)
                || (blockFace == BlockFace.EAST && stairShape == Stairs.Shape.INNER_RIGHT)) {
            return createSeat(block, entity, false, STAIR_XZ_OFFSET, -STAIR_Y_OFFSET, STAIR_XZ_OFFSET, -45f, true);
        }

        return null;
    }

    private Seat createDirectionalSeat(Block block, Directional blockData, LivingEntity entity) {
        BlockFace blockFace = blockData.getFacing().getOppositeFace();
        return switch (blockFace) {
            case EAST -> createSeat(block, entity, false, DIRECTIONAL_XZ_OFFSET, 0d, 0d, 90f, true);
            case SOUTH -> createSeat(block, entity, false, 0d, 0d, DIRECTIONAL_XZ_OFFSET, 180f, true);
            case WEST -> createSeat(block, entity, false, -DIRECTIONAL_XZ_OFFSET, 0d, 0d, -90f, true);
            case NORTH -> createSeat(block, entity, false, 0d, 0d, -DIRECTIONAL_XZ_OFFSET, 0f, true);
            default -> null;
        };
    }

    // =========================================================================
    //                          POSE (/lay & /legsup)
    // =========================================================================

    public Map<UUID, StoneSitPose> getAllPoses() {
        return poses;
    }

    public boolean isPlayerPosing(Player player) {
        return player != null && poses.containsKey(player.getUniqueId());
    }

    public StoneSitPose getPoseByPlayer(Player player) {
        return player != null ? poses.get(player.getUniqueId()) : null;
    }

    public void removeAllPoses() {
        for (StoneSitPose pose : new ArrayList<>(poses.values())) {
            removePose(pose, StopReason.PLUGIN);
        }
        poses.clear();
        blockPoses.clear();
        stopPoseTaskIfIdle();
    }

    public boolean isBlockWithPose(Block block) {
        return blockPoses.containsKey(block);
    }

    public Set<StoneSitPose> getPosesByBlock(Block block) {
        return blockPoses.getOrDefault(block, Collections.emptySet());
    }

    public boolean kickPoseEntitiesFromBlock(Block block, Player kicker) {
        if (!isBlockWithPose(block)) {
            return true;
        }
        if (!EnvironmentUtil.hasPermission(kicker, "stonesit.kick.pose", "stonesit.kick.*", "GSit.Kick.Pose", "GSit.Kick.*")) {
            return false;
        }
        for (StoneSitPose pose : new ArrayList<>(getPosesByBlock(block))) {
            if (!removePose(pose, StopReason.KICKED)) {
                return false;
            }
        }
        return true;
    }

    public StoneSitPose createPose(Block block, Player player, PoseType poseType) {
        return createPose(block, player, poseType, 0d, 0d, 0d, player.getLocation().getYaw(), config.isCenterBlock());
    }

    public StoneSitPose createPose(Block block, Player player, PoseType poseType, double xOffset, double yOffset, double zOffset, float seatRotation, boolean sitInBlockCenter) {
        if (player == null || !player.isValid() || !StoneSitPose.isAvailable()) {
            return null;
        }

        Location returnLocation = player.getLocation();
        Location seatLocation = getSeatLocation(block, returnLocation, xOffset, yOffset, zOffset, sitInBlockCenter);
        seatLocation.setYaw(seatRotation);

        Entity seatEntity = spawnSeatEntity(seatLocation, player);
        if (seatEntity == null) {
            return null;
        }
        player.addScoreboardTag(POSE_TAG);

        if (config.isCustomMessage()) {
            config.sendActionBar(player, "action-pose-info");
        }

        Seat seat = new Seat(block, seatLocation, player, seatEntity, returnLocation);
        StoneSitPose pose;
        try {
            pose = new StoneSitPose(plugin, config, seat, poseType);
        } catch (Throwable t) {
            player.removeScoreboardTag(POSE_TAG);
            seatEntity.remove();
            return null;
        }

        pose.spawn();
        poses.put(player.getUniqueId(), pose);
        blockPoses.computeIfAbsent(block, b -> new HashSet<>()).add(pose);
        ensurePoseTaskRunning();
        return pose;
    }

    public boolean removePose(StoneSitPose pose, StopReason stopReason) {
        return removePose(pose, stopReason, true);
    }

    public boolean removePose(StoneSitPose pose, StopReason stopReason, boolean useSafeDismount) {
        if (pose == null) {
            return true;
        }
        Seat seat = pose.getSeat();
        Player player = pose.getPlayer();

        poses.remove(player.getUniqueId());
        Set<StoneSitPose> blockPoseList = blockPoses.get(seat.getBlock());
        if (blockPoseList != null) {
            blockPoseList.remove(pose);
            if (blockPoseList.isEmpty()) {
                blockPoses.remove(seat.getBlock());
            }
        }

        player.removeScoreboardTag(POSE_TAG);
        if (useSafeDismount) {
            handleSafeSeatDismount(seat);
        }

        pose.remove();

        Entity seatEntity = seat.getSeatEntity();
        if (seatEntity != null) {
            if (!stopReason.isUsingEntityTask() || !plugin.isEnabled()) {
                seatEntity.remove();
            } else {
                Bukkit.getScheduler().runTask(plugin, seatEntity::remove);
            }
        }

        stopPoseTaskIfIdle();
        return true;
    }

    private void ensurePoseTaskRunning() {
        if (poseTickTask == null && plugin.isEnabled() && !poses.isEmpty()) {
            poseTickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPoses, 1L, 1L);
        }
    }

    private void stopPoseTaskIfIdle() {
        if (poses.isEmpty() && poseTickTask != null) {
            poseTickTask.cancel();
            poseTickTask = null;
        }
    }

    private void tickPoses() {
        if (poses.isEmpty()) {
            stopPoseTaskIfIdle();
            return;
        }
        for (StoneSitPose pose : new ArrayList<>(poses.values())) {
            pose.tick();
        }
        if ((++nightSkipCheckCounter % 20) == 0) {
            checkPoseNightSkip();
        }
    }

    /**
     * Prüft, ob durch liegende Spieler (/lay & /legsup) – alleine oder zusammen mit Spielern in echten Betten –
     * die Nacht bzw. das Gewitter übersprungen werden kann (wie in einem echten Bett nach 100 Ticks / 5 Sek.,
     * aber OHNE jemals den Respawn-Punkt des Spielers zu setzen!).
     */
    private void checkPoseNightSkip() {
        if (!config.isPoseLayNightSkip() || poses.isEmpty()) {
            return;
        }

        Set<World> activeWorlds = new HashSet<>();
        for (StoneSitPose pose : poses.values()) {
            if (pose.getPoseType() != PoseType.SPIN && pose.getLayTicks() >= 100 && pose.getPlayer().isValid()) {
                activeWorlds.add(pose.getPlayer().getWorld());
            }
        }
        if (activeWorlds.isEmpty()) {
            return;
        }

        for (World world : activeWorlds) {
            if (!world.isBedWorks()) {
                continue;
            }
            long dayTime = world.getTime() % 24000L;
            boolean isNight = dayTime >= 12542L && dayTime <= 23459L;
            boolean isThundering = world.isThundering();
            if (!isNight && !isThundering) {
                continue;
            }

            Integer percentage = world.getGameRuleValue(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
            int requiredPercentage = percentage != null ? percentage : 100;
            if (requiredPercentage > 100) {
                continue;
            }

            int eligiblePlayers = 0;
            int deeplySleepingOrLaying = 0;
            List<StoneSitPose> worldPoses = new ArrayList<>();

            for (Player p : world.getPlayers()) {
                if (!p.isValid() || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                StoneSitPose pPose = poses.get(p.getUniqueId());
                if (pPose != null) {
                    if (pPose.getPoseType() == PoseType.SPIN) {
                        continue;
                    }
                    worldPoses.add(pPose);
                    if (pPose.wasSleepingIgnoredBeforePose()) {
                        continue;
                    }
                    eligiblePlayers++;
                    if (pPose.getLayTicks() >= 100) {
                        deeplySleepingOrLaying++;
                    }
                } else {
                    if (p.isSleepingIgnored()) {
                        continue;
                    }
                    eligiblePlayers++;
                    if (p.isSleeping() && p.getSleepTicks() >= 100) {
                        deeplySleepingOrLaying++;
                    }
                }
            }

            if (eligiblePlayers <= 0) {
                continue;
            }

            int requiredCount = Math.max(1, (int) Math.ceil((eligiblePlayers * requiredPercentage) / 100.0d));
            if (deeplySleepingOrLaying < requiredCount) {
                continue;
            }

            // Nacht / Gewitter überspringen wie im Vanilla-Bett (ohne Respawn-Punkt zu verändern!)
            long currentFullTime = world.getFullTime();
            long nextDay = currentFullTime + 24000L;
            long targetFullTime = nextDay - (nextDay % 24000L);
            long skipAmount = targetFullTime - currentFullTime;

            TimeSkipEvent timeSkipEvent = new TimeSkipEvent(world, TimeSkipEvent.SkipReason.NIGHT_SKIP, skipAmount);
            Bukkit.getPluginManager().callEvent(timeSkipEvent);
            if (timeSkipEvent.isCancelled()) {
                continue;
            }

            Boolean advanceTime = world.getGameRuleValue(GameRules.ADVANCE_TIME);
            if (isNight && (advanceTime == null || advanceTime)) {
                world.setFullTime(currentFullTime + timeSkipEvent.getSkipAmount());
            }

            Boolean advanceWeather = world.getGameRuleValue(GameRules.ADVANCE_WEATHER);
            if ((advanceWeather == null || advanceWeather) && (world.hasStorm() || world.isThundering())) {
                world.setStorm(false);
                world.setThundering(false);
                world.setWeatherDuration(0);
                world.setThunderDuration(0);
            }

            for (Player p : world.getPlayers()) {
                StoneSitPose checkPose = poses.get(p.getUniqueId());
                if (p.isSleeping() || (checkPose != null && checkPose.getPoseType() != PoseType.SPIN)) {
                    if (config.isPoseLayRest()) {
                        p.setStatistic(Statistic.TIME_SINCE_REST, 0);
                    }
                }
            }

            for (StoneSitPose worldPose : worldPoses) {
                worldPose.resetLayTicks();
                if (config.isPoseLayWakeupMorning()) {
                    removePose(worldPose, StopReason.GET_UP);
                }
            }
        }
    }

    // =========================================================================
    //                             PLAYER SIT (STACK)
    // =========================================================================

    public Set<UUID> getPreventDismountStackPlayers() {
        return preventDismountStackPlayers;
    }

    public boolean isPlayerInPlayerSitStack(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        return bottomToTopStacks.containsKey(uuid) || topToBottomStacks.containsKey(uuid);
    }

    /**
     * Liefert den Spieler, der direkt auf {@code bottomPlayer} sitzt (falls vorhanden).
     */
    public Player getDirectTopPlayer(Player bottomPlayer) {
        if (bottomPlayer == null) return null;
        Map.Entry<UUID, List<UUID>> entry = bottomToTopStacks.get(bottomPlayer.getUniqueId());
        if (entry == null) return null;
        return Bukkit.getPlayer(entry.getKey());
    }

    /**
     * Liefert den Spieler, auf dem {@code topPlayer} direkt sitzt (falls vorhanden).
     */
    public Player getDirectBottomPlayer(Player topPlayer) {
        if (topPlayer == null) return null;
        Map.Entry<UUID, List<UUID>> entry = topToBottomStacks.get(topPlayer.getUniqueId());
        if (entry == null) return null;
        return Bukkit.getPlayer(entry.getKey());
    }

    /**
     * Prüft, ob auf {@code bottomPlayer} (direkt oder im Stapel darüber) ein Admin sitzt,
     * der durch die Permission {@code stonesit.bypass.sneakeject} vor dem Sneak-Abwurf geschützt ist.
     */
    public boolean hasSneakEjectProtectedPassenger(Player bottomPlayer) {
        if (bottomPlayer == null) return false;
        UUID currentUuid = bottomPlayer.getUniqueId();
        int safety = 0;
        while (currentUuid != null && safety++ < 64) {
            Map.Entry<UUID, List<UUID>> entry = bottomToTopStacks.get(currentUuid);
            if (entry == null) break;
            UUID topUuid = entry.getKey();
            Player topPlayer = Bukkit.getPlayer(topUuid);
            if (topPlayer != null && isSneakEjectProtected(topPlayer)) {
                return true;
            }
            currentUuid = topUuid;
        }
        for (org.bukkit.entity.Entity p : bottomPlayer.getPassengers()) {
            if (p instanceof Player topPlayer && isSneakEjectProtected(topPlayer)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Prüft, ob ein geschützter Admin sichtbar (nicht im Vanish) auf dem Kopf sitzt.
     * Wenn alle geschützten Admins im Vanish sind, soll keine Benachrichtigung gesendet werden,
     * damit der Vanish-Status des Admins vor dem Spieler geheim bleibt.
     */
    public boolean hasVisibleSneakEjectProtectedPassenger(Player bottomPlayer) {
        if (bottomPlayer == null) return false;
        UUID currentUuid = bottomPlayer.getUniqueId();
        int safety = 0;
        while (currentUuid != null && safety++ < 64) {
            Map.Entry<UUID, List<UUID>> entry = bottomToTopStacks.get(currentUuid);
            if (entry == null) break;
            UUID topUuid = entry.getKey();
            Player topPlayer = Bukkit.getPlayer(topUuid);
            if (topPlayer != null && isSneakEjectProtected(topPlayer) && !de.Z7534.ztone.util.VanishUtil.isVanished(topPlayer)) {
                return true;
            }
            currentUuid = topUuid;
        }
        for (org.bukkit.entity.Entity p : bottomPlayer.getPassengers()) {
            if (p instanceof Player topPlayer && isSneakEjectProtected(topPlayer) && !de.Z7534.ztone.util.VanishUtil.isVanished(topPlayer)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSneakEjectProtected(Player player) {
        return player != null && EnvironmentUtil.hasPermission(player,
                "stonesit.bypass.sneakeject",
                "stonesit.bypass.kick",
                "stonesit.bypass.*",
                "GSit.ByPass.SneakEject",
                "GSit.ByPass.*");
    }

    public void removeAllPlayerSitStacks() {
        for (UUID topPlayerId : new ArrayList<>(topToBottomStacks.keySet())) {
            Player topPlayer = Bukkit.getPlayer(topPlayerId);
            if (topPlayer != null) {
                stopPlayerSit(topPlayer, StopReason.PLUGIN, false, true);
            }
        }
        bottomToTopStacks.clear();
        topToBottomStacks.clear();
        preventDismountStackPlayers.clear();
        stopPlayerSitBanTaskIfIdle();
    }

    private void ensurePlayerSitBanTaskRunning() {
        if (playerSitBanTask == null && plugin.isEnabled() && !bottomToTopStacks.isEmpty()) {
            playerSitBanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAllPlayerSitStacksForBans, 20L, 20L);
        }
    }

    private void stopPlayerSitBanTaskIfIdle() {
        if (bottomToTopStacks.isEmpty() && playerSitBanTask != null) {
            playerSitBanTask.cancel();
            playerSitBanTask = null;
        }
    }

    public void checkAllPlayerSitStacksForBans() {
        if (bottomToTopStacks.isEmpty()) {
            stopPlayerSitBanTaskIfIdle();
            return;
        }
        for (UUID bottomId : new ArrayList<>(bottomToTopStacks.keySet())) {
            Player bottom = Bukkit.getPlayer(bottomId);
            if (bottom != null && bottom.isOnline()) {
                checkPlayerSitStackBans(bottom, bottom.getLocation());
            }
        }
    }

    /**
     * Prüft alle Spieler in einem PlayerSit-Stack an einer bestimmten Location (z.B. nach Bewegung).
     * Befindet sich der Stack in einem Claim, auf dem ein Reiter gebannt ist,
     * wird dieser Reiter sofort vom Kopf geworfen und per SafeSpot außerhalb des Claims evakuiert.
     */
    public void checkPlayerSitStackBans(Player anyStackPlayer, Location loc) {
        if (anyStackPlayer == null || loc == null) return;
        if (!isPlayerInPlayerSitStack(anyStackPlayer)) return;

        var claimsModule = plugin.getClaims();
        if (claimsModule == null || claimsModule.getClaimManager() == null) return;

        Claim claim = claimsModule.getClaimManager().getClaimAt(loc);
        if (claim == null) return;

        // Finde den untersten Träger
        Player bottom = anyStackPlayer;
        int safety = 0;
        while (safety++ < 64) {
            Player vehicle = getDirectBottomPlayer(bottom);
            if (vehicle == null) break;
            bottom = vehicle;
        }

        // Iteriere von unten nach oben durch den Stack
        Player current = bottom;
        safety = 0;
        while (current != null && safety++ < 64) {
            Player top = getDirectTopPlayer(current);
            if (top == null) break;

            boolean hasBypass = claimsModule.getBypassManager() != null && claimsModule.getBypassManager().isActive(top);
            if (!hasBypass && claim.isBanned(top.getUniqueId())) {
                Player carrier = current;
                Player ejected = top;

                // Sofort vom Kopf werfen
                stopPlayerSit(ejected, StopReason.PLUGIN);

                // Benachrichtigungen
                ejected.sendMessage(config.getMessage("action-playersit-banned-claim",
                        "<prefix> <red>Du wurdest vom Kopf geworfen, da du auf diesem Claim gebannt bist!</red>"));
                carrier.sendMessage(Text.line(config.getPrefix() + " <yellow>" + ejected.getName()
                        + "</yellow> <red>wurde von deinem Kopf geworfen (auf diesem Claim gebannt).</red>"));

                // Sofort sicher außerhalb des Claims evakuieren
                plugin.evictFromClaimIfRestricted(ejected);
            }
            current = top;
        }
    }

    public boolean sitOnPlayer(Player player, Player target) {
        if (player == null || target == null || !player.isValid() || !target.isValid()) {
            return false;
        }

        List<UUID> playerSitEntityIds = createPlayerSitEntities(player, target);
        if (playerSitEntityIds == null) {
            return false;
        }

        if (config.isCustomMessage()) {
            config.sendActionBar(player, "action-playersit-info");
        }

        bottomToTopStacks.put(target.getUniqueId(), new AbstractMap.SimpleImmutableEntry<>(player.getUniqueId(), playerSitEntityIds));
        topToBottomStacks.put(player.getUniqueId(), new AbstractMap.SimpleImmutableEntry<>(target.getUniqueId(), playerSitEntityIds));
        ensurePlayerSitBanTaskRunning();

        // Direkt prüfen, ob target in einem Claim steht, auf dem der neue Reiter gebannt ist!
        checkPlayerSitStackBans(player, target.getLocation());
        return true;
    }

    private List<UUID> createPlayerSitEntities(Player player, Player target) {
        World world = target.getWorld();
        final boolean[] mounted = {false};

        AreaEffectCloud sitEntity = world.spawn(target.getLocation(), AreaEffectCloud.class, aec -> {
            aec.setPersistent(false);
            aec.setRadius(0f);
            aec.setDuration(Integer.MAX_VALUE);
            aec.setWaitTime(0);
            aec.setGravity(false);
            aec.setInvulnerable(true);
            aec.setSilent(true);
            aec.addScoreboardTag(PLAYERSIT_ENTITY_TAG);
            if (target.addPassenger(aec)) {
                mounted[0] = aec.addPassenger(player);
            }
        });

        if (!mounted[0] || !sitEntity.isValid()) {
            sitEntity.remove();
            return null;
        }

        return List.of(sitEntity.getUniqueId());
    }

    public boolean stopPlayerSit(Player source, StopReason stopReason) {
        return stopPlayerSit(source, stopReason, true, true);
    }

    public boolean stopPlayerSit(Player source, StopReason stopReason, boolean removePassengers, boolean removeVehicle) {
        if (source == null) {
            return true;
        }
        UUID sourceUuid = source.getUniqueId();
        Map.Entry<UUID, List<UUID>> passengers = removePassengers ? bottomToTopStacks.get(sourceUuid) : null;
        Map.Entry<UUID, List<UUID>> vehicles = removeVehicle ? topToBottomStacks.get(sourceUuid) : null;
        if (passengers == null && vehicles == null) {
            return true;
        }

        if (passengers != null) {
            source.eject();
            bottomToTopStacks.remove(sourceUuid);
            topToBottomStacks.remove(passengers.getKey());
            for (UUID passengerEntityId : passengers.getValue()) {
                Entity passengerEntity = Bukkit.getEntity(passengerEntityId);
                if (passengerEntity == null) continue;
                if (!stopReason.isUsingEntityTask() || !plugin.isEnabled()) {
                    passengerEntity.remove();
                } else {
                    Bukkit.getScheduler().runTask(plugin, passengerEntity::remove);
                }
            }
        }

        if (vehicles != null) {
            source.leaveVehicle();
            topToBottomStacks.remove(sourceUuid);
            bottomToTopStacks.remove(vehicles.getKey());
            for (UUID vehicleEntityId : vehicles.getValue()) {
                Entity vehicleEntity = Bukkit.getEntity(vehicleEntityId);
                if (vehicleEntity == null) continue;
                if (!stopReason.isUsingEntityTask() || !plugin.isEnabled()) {
                    vehicleEntity.remove();
                } else {
                    Bukkit.getScheduler().runTask(plugin, vehicleEntity::remove);
                }
            }
        }

        if (bottomToTopStacks.isEmpty()) {
            stopPlayerSitBanTaskIfIdle();
        }

        return true;
    }

    /**
     * Beendet sofort alle Sitz-, Pose- und Player-Sit-Zustände für den angegebenen Spieler.
     */
    public void stopAllForPlayer(Player player, StopReason reason) {
        if (player == null) return;
        Seat seat = getSeatByEntity(player);
        if (seat != null) {
            removeSeat(seat, reason, false);
        }
        StoneSitPose pose = getPoseByPlayer(player);
        if (pose != null) {
            removePose(pose, reason, false);
        }
        stopPlayerSit(player, reason);
    }

    // =========================================================================
    //                               SPIN FEATURE
    // =========================================================================

    public boolean isPlayerSpinning(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        StoneSitPose pose = poses.get(player.getUniqueId());
        return pose != null && pose.getPoseType() == PoseType.SPIN;
    }

}
