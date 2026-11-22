package de.Z7534.ztone.stonesit.manager;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.claims.manager.BypassManager;
import de.Z7534.ztone.claims.manager.BypassManager;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.util.Text;
import de.Z7534.ztone.stonesit.config.StoneSitConfig;
import de.Z7534.ztone.stonesit.model.PoseType;
import de.Z7534.ztone.stonesit.model.Seat;
import de.Z7534.ztone.stonesit.model.StoneSitPose;
import de.Z7534.ztone.stonesit.model.StopReason;
import de.Z7534.ztone.stonesit.util.EnvironmentUtil;
import de.Z7534.ztone.stonesit.util.PassengerUtil;
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
    private final Set<UUID> carrierTeleportingAdmins = new HashSet<>();

    // Internes Flag um programmatische/interne Dismounts nicht versehentlich im DismountListener abzublocken
    private final Set<UUID> internalDismounting = new HashSet<>();

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
            // Admin-Schutz: Sitzt ein Admin auf diesem Teppich/Block, kann er nicht gekickt werden!
            if (isKickProtected(seat.getEntity())) {
                return false;
            }
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
        for (BlockData blacklistedBlockData : config.getBlockDataBlacklist()) {
            if (blacklistedBlockData.matches(blockData)) {
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

        if (entity instanceof Player player) {
            StoneSitPose pose = getPoseByPlayer(player);
            if (pose != null) {
                removePose(pose, StopReason.SWITCH);
            }
            if (isPlayerInPlayerSitStack(player)) {
                stopPlayerSit(player, StopReason.SWITCH, false, true);
            }
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
        Block nextBlock = seat.getBlock().getRelative(blockDirection);
        double sitBlockDataHeightOffset = getSitBlockDataHeightOffset(seat.getBlock().getBlockData());
        Location newSeatLocation = seat.getLocation().add(
                blockDirection.getModX(),
                blockDirection.getModY() + (nextBlock.getBlockData() instanceof Stairs ? STAIR_Y_OFFSET : 0d) + sitBlockDataHeightOffset,
                blockDirection.getModZ()
        );
        seat.setBlock(nextBlock);
        seat.setLocation(newSeatLocation);
        setEntityLocation(seat.getSeatEntity(), newSeatLocation);
    }

    public boolean removeSeat(Seat seat, StopReason stopReason) {
        return removeSeat(seat, stopReason, true);
    }

    public boolean removeSeat(Seat seat, StopReason stopReason, boolean useSafeDismount) {
        if (seat == null) {
            return true;
        }
        LivingEntity entity = seat.getEntity();

        // Admin-Schutz: Admins mit Kick-Bypass dürfen niemals gekickt werden!
        if (stopReason == StopReason.KICKED && isKickProtected(entity)) {
            return false;
        }

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
        } finally {
            Bukkit.getScheduler().runTask(plugin, () -> {
                internalTeleporting.remove(uuid);
            });
        }
    }

    public boolean isInternalTeleporting(UUID uuid) {
        return internalTeleporting.contains(uuid);
    }

    public boolean isInternalDismounting(UUID uuid) {
        return uuid != null && internalDismounting.contains(uuid);
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
            // Admin-Schutz: Sitzt/liegt ein Admin hier, darf er nicht gekickt werden!
            if (isKickProtected(pose.getPlayer())) {
                return false;
            }
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

        Seat existingSeat = getSeatByEntity(player);
        if (existingSeat != null) {
            removeSeat(existingSeat, StopReason.SWITCH);
        }
        if (isPlayerInPlayerSitStack(player)) {
            stopPlayerSit(player, StopReason.SWITCH, false, true);
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
        Player player = pose.getPlayer();

        // Admin-Schutz: Admins dürfen niemals gekickt werden!
        if (stopReason == StopReason.KICKED && isKickProtected(player)) {
            return false;
        }

        Seat seat = pose.getSeat();

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
            long time = world.getTime();
            boolean isNight = time >= 12542L && time <= 23460L;
            boolean isThundering = world.isThundering();
            if (!isNight && !isThundering) {
                continue;
            }

            int playersSleepingPercentage = world.getGameRuleValue(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
            if (playersSleepingPercentage > 100) {
                continue;
            }

            List<Player> worldPlayers = world.getPlayers();
            int eligiblePlayers = 0;
            int restingPlayers = 0;

            for (Player p : worldPlayers) {
                if (p.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                eligiblePlayers++;
                if (p.isSleeping()) {
                    restingPlayers++;
                } else if (poses.containsKey(p.getUniqueId())) {
                    StoneSitPose pose = poses.get(p.getUniqueId());
                    if (pose != null && pose.getPoseType() != PoseType.SPIN && pose.getLayTicks() >= 100) {
                        restingPlayers++;
                    }
                }
            }

            if (eligiblePlayers <= 0) {
                continue;
            }

            boolean shouldSkip = false;
            if (playersSleepingPercentage <= 0) {
                shouldSkip = restingPlayers > 0;
            } else {
                int needed = (int) Math.ceil((eligiblePlayers * (double) playersSleepingPercentage) / 100.0);
                shouldSkip = restingPlayers >= needed;
            }

            if (shouldSkip) {
                triggerWorldNightSkip(world);
            }
        }
    }

    private void triggerWorldNightSkip(World world) {
        long currentFullTime = world.getFullTime();
        long currentDayTime = world.getTime();
        long addTime = (currentDayTime <= 24000L) ? (24000L - currentDayTime) : 0L;
        long targetFullTime = currentFullTime + addTime;

        TimeSkipEvent timeSkipEvent = new TimeSkipEvent(world, TimeSkipEvent.SkipReason.NIGHT_SKIP, addTime);
        Bukkit.getPluginManager().callEvent(timeSkipEvent);
        if (timeSkipEvent.isCancelled()) {
            return;
        }

        world.setFullTime(targetFullTime);
        world.setTime(0L);

        if (world.isThundering()) {
            world.setThundering(false);
            world.setThunderDuration(0);
        }
        if (world.hasStorm()) {
            world.setStorm(false);
            world.setWeatherDuration(0);
        }

        for (Player p : world.getPlayers()) {
            if (p.isValid()) {
                try {
                    p.setStatistic(Statistic.TIME_SINCE_REST, 0);
                } catch (Throwable ignored) {}
            }
        }

        if (config.isPoseLayWakeupMorning()) {
            for (StoneSitPose pose : new ArrayList<>(poses.values())) {
                if (pose.getPoseType() != PoseType.SPIN && pose.getPlayer().getWorld().equals(world)) {
                    removePose(pose, StopReason.GET_UP, true);
                }
            }
        }
    }

    // =========================================================================
    //                         PLAYER-SIT & ENTITY-SIT
    // =========================================================================

    public boolean isPlayerInPlayerSitStack(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        return bottomToTopStacks.containsKey(uuid) || topToBottomStacks.containsKey(uuid);
    }

    public boolean isPlayerInPlayerSitStack(Entity entity) {
        if (entity == null) return false;
        UUID uuid = entity.getUniqueId();
        return bottomToTopStacks.containsKey(uuid) || topToBottomStacks.containsKey(uuid);
    }

    public Set<UUID> getPreventDismountStackPlayers() {
        return preventDismountStackPlayers;
    }

    public Player getDirectTopPlayer(Entity bottomEntity) {
        if (bottomEntity == null) return null;
        Map.Entry<UUID, List<UUID>> entry = bottomToTopStacks.get(bottomEntity.getUniqueId());
        if (entry == null) return null;
        return Bukkit.getPlayer(entry.getKey());
    }

    public Player getDirectBottomPlayer(Entity topEntity) {
        if (topEntity == null) return null;
        Map.Entry<UUID, List<UUID>> entry = topToBottomStacks.get(topEntity.getUniqueId());
        if (entry == null) return null;
        return Bukkit.getPlayer(entry.getKey());
    }

    /**
     * Bereinigt verwaiste Passagier-Entities (leere AreaEffectClouds / Sit-ArmorStands)
     * sowie inkonsistente Stack-Einträge auf der übergebenen Entity.
     */
    public void cleanStalePassengerEntities(Entity entity) {
        if (entity == null) {
            return;
        }

        // 1. Bereinige verwaiste Passagier-Entities auf der Bukkit-Entity
        for (Entity passenger : new ArrayList<>(entity.getPassengers())) {
            if (passenger instanceof AreaEffectCloud
                    || passenger.getScoreboardTags().contains(PLAYERSIT_ENTITY_TAG)
                    || passenger.getScoreboardTags().contains(SIT_TAG)
                    || passenger.getScoreboardTags().contains(POSE_TAG)) {
                boolean hasValidOnlineRider = false;
                for (Entity rider : passenger.getPassengers()) {
                    if (rider instanceof Player p && p.isOnline() && p.isValid()) {
                        hasValidOnlineRider = true;
                        break;
                    }
                }
                if (!hasValidOnlineRider) {
                    entity.removePassenger(passenger);
                    passenger.remove();
                }
            }
        }

        // 2. Bereinige verwaiste Einträge in bottomToTopStacks
        UUID entityUuid = entity.getUniqueId();
        Map.Entry<UUID, List<UUID>> topEntry = bottomToTopStacks.get(entityUuid);
        if (topEntry != null) {
            UUID topPlayerUuid = topEntry.getKey();
            Player topPlayer = Bukkit.getPlayer(topPlayerUuid);
            boolean stale = false;

            if (topPlayer == null || !topPlayer.isOnline() || !topPlayer.isValid()) {
                stale = true;
            } else {
                Map.Entry<UUID, List<UUID>> reverseEntry = topToBottomStacks.get(topPlayerUuid);
                if (reverseEntry == null || !entityUuid.equals(reverseEntry.getKey())) {
                    stale = true;
                } else if (!PassengerUtil.isEntityInPassengerList(entity, topPlayer)) {
                    stale = true;
                }
            }

            if (stale) {
                bottomToTopStacks.remove(entityUuid);
                if (topPlayer != null) {
                    Map.Entry<UUID, List<UUID>> reverseEntry = topToBottomStacks.get(topPlayerUuid);
                    if (reverseEntry != null && entityUuid.equals(reverseEntry.getKey())) {
                        topToBottomStacks.remove(topPlayerUuid);
                    }
                }
                for (UUID sitEntityId : topEntry.getValue()) {
                    Entity sitEntity = Bukkit.getEntity(sitEntityId);
                    if (sitEntity != null) {
                        entity.removePassenger(sitEntity);
                        sitEntity.remove();
                    }
                }
            }
        }

        // 3. Bereinige verwaiste Einträge in topToBottomStacks
        Map.Entry<UUID, List<UUID>> bottomEntry = topToBottomStacks.get(entityUuid);
        if (bottomEntry != null) {
            UUID vehicleUuid = bottomEntry.getKey();
            Entity vehicle = Bukkit.getEntity(vehicleUuid);
            boolean stale = false;

            if (vehicle == null || !vehicle.isValid()) {
                stale = true;
            } else {
                Map.Entry<UUID, List<UUID>> reverseEntry = bottomToTopStacks.get(vehicleUuid);
                if (reverseEntry == null || !entityUuid.equals(reverseEntry.getKey())) {
                    stale = true;
                } else if (!PassengerUtil.isEntityInPassengerList(vehicle, entity)) {
                    stale = true;
                }
            }

            if (stale) {
                topToBottomStacks.remove(entityUuid);
                if (vehicle != null) {
                    Map.Entry<UUID, List<UUID>> reverseEntry = bottomToTopStacks.get(vehicleUuid);
                    if (reverseEntry != null && entityUuid.equals(reverseEntry.getKey())) {
                        bottomToTopStacks.remove(vehicleUuid);
                    }
                }
                for (UUID sitEntityId : bottomEntry.getValue()) {
                    Entity sitEntity = Bukkit.getEntity(sitEntityId);
                    if (sitEntity != null) {
                        if (vehicle != null) {
                            vehicle.removePassenger(sitEntity);
                        }
                        sitEntity.remove();
                    }
                }
            }
        }

        if (bottomToTopStacks.isEmpty()) {
            stopPlayerSitBanTaskIfIdle();
        }
    }

    /**
     * Sammelt alle kick-geschützten Admins, die direkt oder indirekt auf {@code bottomEntity} sitzen.
     */
    public List<Player> getProtectedPassengerChain(Entity bottomEntity) {
        if (bottomEntity == null) return Collections.emptyList();
        cleanStalePassengerEntities(bottomEntity);
        List<Player> chain = new ArrayList<>();
        UUID currentUuid = bottomEntity.getUniqueId();
        int safety = 0;
        while (currentUuid != null && safety++ < 64) {
            Map.Entry<UUID, List<UUID>> entry = bottomToTopStacks.get(currentUuid);
            if (entry == null) break;
            UUID topUuid = entry.getKey();
            Player topPlayer = Bukkit.getPlayer(topUuid);
            if (topPlayer != null && isKickProtected(topPlayer)) {
                chain.add(topPlayer);
            }
            currentUuid = topUuid;
        }
        return chain;
    }

    public boolean isCarrierTeleporting(UUID uuid) {
        return carrierTeleportingAdmins.contains(uuid);
    }

    public void handleCarrierTeleport(Entity carrier, Location destination) {
        if (carrier == null || destination == null) {
            return;
        }
        List<Player> protectedAdmins = getProtectedPassengerChain(carrier);
        if (protectedAdmins.isEmpty()) {
            return;
        }

        for (Player admin : protectedAdmins) {
            carrierTeleportingAdmins.add(admin.getUniqueId());
            internalTeleporting.add(admin.getUniqueId());
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (!carrier.isValid()) {
                    return;
                }
                if (carrier instanceof Player playerCarrier && !playerCarrier.isOnline()) {
                    return;
                }

                Entity currentVehicle = carrier;
                for (Player admin : protectedAdmins) {
                    if (!admin.isOnline() || !admin.isValid()) {
                        continue;
                    }
                    Map.Entry<UUID, List<UUID>> entry = topToBottomStacks.remove(admin.getUniqueId());
                    if (entry != null) {
                        bottomToTopStacks.remove(entry.getKey());
                        for (UUID entityId : entry.getValue()) {
                            Entity e = Bukkit.getEntity(entityId);
                            if (e != null) {
                                e.remove();
                            }
                        }
                    }

                    admin.teleport(currentVehicle.getLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                    if (sitOnEntity(admin, currentVehicle)) {
                        currentVehicle = admin;
                    }
                }
            } finally {
                for (Player admin : protectedAdmins) {
                    carrierTeleportingAdmins.remove(admin.getUniqueId());
                    internalTeleporting.remove(admin.getUniqueId());
                }
            }
        }, 1L);
    }

    public boolean hasSneakEjectProtectedPassenger(Player bottomPlayer) {
        if (bottomPlayer == null) return false;
        cleanStalePassengerEntities(bottomPlayer);
        UUID currentUuid = bottomPlayer.getUniqueId();
        int safety = 0;
        while (currentUuid != null && safety++ < 64) {
            Map.Entry<UUID, List<UUID>> entry = bottomToTopStacks.get(currentUuid);
            if (entry == null) break;
            UUID topUuid = entry.getKey();
            Player topPlayer = Bukkit.getPlayer(topUuid);
            if (topPlayer != null && isKickProtected(topPlayer)) {
                return true;
            }
            currentUuid = topUuid;
        }
        for (Entity p : bottomPlayer.getPassengers()) {
            if (p instanceof Player topPlayer && isKickProtected(topPlayer)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasVisibleSneakEjectProtectedPassenger(Player bottomPlayer) {
        if (bottomPlayer == null) return false;
        cleanStalePassengerEntities(bottomPlayer);
        UUID currentUuid = bottomPlayer.getUniqueId();
        int safety = 0;
        while (currentUuid != null && safety++ < 64) {
            Map.Entry<UUID, List<UUID>> entry = bottomToTopStacks.get(currentUuid);
            if (entry == null) break;
            UUID topUuid = entry.getKey();
            Player topPlayer = Bukkit.getPlayer(topUuid);
            if (topPlayer != null && isKickProtected(topPlayer) && !de.Z7534.ztone.util.VanishUtil.isVanished(topPlayer)) {
                return true;
            }
            currentUuid = topUuid;
        }
        for (Entity p : bottomPlayer.getPassengers()) {
            if (p instanceof Player topPlayer && isKickProtected(topPlayer) && !de.Z7534.ztone.util.VanishUtil.isVanished(topPlayer)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isKickProtected(Entity entity) {
        if (entity instanceof Player player) {
            return player.isOp() || EnvironmentUtil.hasPermission(player,
                    "stonesit.bypass.kick",
                    "stonesit.bypass.sneakeject",
                    "stonesit.bypass.*",
                    "stonesit.admin",
                    "GSit.ByPass.Kick",
                    "GSit.ByPass.SneakEject",
                    "GSit.ByPass.*");
        }
        return false;
    }

    public static boolean isSneakEjectProtected(Player player) {
        return isKickProtected(player);
    }

    public void removeAllPlayerSitStacks() {
        carrierTeleportingAdmins.clear();
        for (UUID topPlayerId : new ArrayList<>(topToBottomStacks.keySet())) {
            Player topPlayer = Bukkit.getPlayer(topPlayerId);
            if (topPlayer != null) {
                stopPlayerSit(topPlayer, StopReason.PLUGIN, false, true);
            }
        }
        bottomToTopStacks.clear();
        topToBottomStacks.clear();
        stopPlayerSitBanTaskIfIdle();
    }

    private void ensurePlayerSitBanTaskRunning() {
        if (playerSitBanTask == null && plugin.isEnabled() && !bottomToTopStacks.isEmpty()) {
            playerSitBanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPlayerSitBans, 20L, 20L);
        }
    }

    private void stopPlayerSitBanTaskIfIdle() {
        if (bottomToTopStacks.isEmpty() && playerSitBanTask != null) {
            playerSitBanTask.cancel();
            playerSitBanTask = null;
        }
    }

    private void tickPlayerSitBans() {
        if (bottomToTopStacks.isEmpty()) {
            stopPlayerSitBanTaskIfIdle();
            return;
        }
        for (UUID bottomUuid : new ArrayList<>(bottomToTopStacks.keySet())) {
            Player bottom = Bukkit.getPlayer(bottomUuid);
            if (bottom != null && bottom.isOnline() && bottom.isValid()) {
                checkPlayerSitStackBans(bottom, bottom.getLocation());
            }
        }
    }

    public void checkPlayerSitStackBans(Player anyStackPlayer, Location loc) {
        if (anyStackPlayer == null || loc == null) return;
        if (!isPlayerInPlayerSitStack(anyStackPlayer)) return;

        var claimsModule = plugin.getClaims();
        if (claimsModule == null || claimsModule.getClaimManager() == null) return;

        Claim claim = claimsModule.getClaimManager().getClaimAt(loc);
        if (claim == null) return;

        Player bottom = anyStackPlayer;
        int safety = 0;
        while (safety++ < 64) {
            Player vehicle = getDirectBottomPlayer(bottom);
            if (vehicle == null) break;
            bottom = vehicle;
        }

        Player current = bottom;
        safety = 0;
        while (current != null && safety++ < 64) {
            Player top = getDirectTopPlayer(current);
            if (top == null) break;

            boolean hasStaffClaimBypass = claimsModule.getBypassManager() != null && claimsModule.getBypassManager().isActive(top);
            if (!hasStaffClaimBypass) {
                if (claim.isBanned(top.getUniqueId())) {
                    Player carrier = current;
                    Player ejected = top;

                    stopPlayerSit(ejected, StopReason.PLUGIN);

                    ejected.sendMessage(config.getMessage("action-playersit-banned-claim",
                            "<prefix> <red>Du wurdest vom Kopf geworfen, da du auf diesem Claim gebannt bist!</red>"));
                    carrier.sendMessage(Text.line(config.getPrefix() + " <yellow>" + ejected.getName()
                            + "</yellow> <red>wurde von deinem Kopf geworfen (auf diesem Claim gebannt).</red>"));

                    plugin.evictFromClaimIfRestricted(ejected);
                } else if (!top.hasPermission(BypassManager.PERMISSION_BYPASS_SIT)
                        && !EnvironmentUtil.hasPermission(top, "stonesit.bypass.region", "stonesit.bypass.*", "GSit.ByPass.Region", "GSit.ByPass.*")
                        && !claim.hasPermission(top.getUniqueId(), de.Z7534.ztone.claims.model.ClaimPermission.SIT)) {
                    Player carrier = current;
                    Player ejected = top;

                    stopPlayerSit(ejected, StopReason.PLUGIN);

                    ejected.sendMessage(config.getMessage("action-sit-region-error",
                            "<prefix> <red>Du kannst in dieser Region nicht sitzen!</red>"));
                    carrier.sendMessage(Text.line(config.getPrefix() + " <yellow>" + ejected.getName()
                            + "</yellow> <red>wurde von deinem Kopf geworfen (keine Sit-Rechte auf diesem Claim).</red>"));
                }
            }
            current = top;
        }
    }

    public boolean sitOnPlayer(Player player, Player target) {
        return sitOnEntity(player, target);
    }

    public boolean sitOnEntity(Player player, Entity target) {
        if (player == null || target == null || !player.isValid() || !target.isValid()) {
            return false;
        }

        // Bestehende Sit-Zustände des Spielers vorher sauber abbauen
        Seat seat = getSeatByEntity(player);
        if (seat != null) {
            removeSeat(seat, StopReason.SWITCH);
        }

        StoneSitPose pose = getPoseByPlayer(player);
        if (pose != null) {
            removePose(pose, StopReason.SWITCH);
        }

        if (isPlayerInPlayerSitStack(player)) {
            stopPlayerSit(player, StopReason.SWITCH, false, true);
        }

        // Ziel-Entity vor dem Draufsetzen von verwaisten Entities säubern
        cleanStalePassengerEntities(target); if (!PassengerUtil.canMount(player, target)) { return false; }

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

        checkPlayerSitStackBans(player, target.getLocation());
        return true;
    }

    private List<UUID> createPlayerSitEntities(Player player, Entity target) {
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

    public boolean stopPlayerSit(Entity source, StopReason stopReason) {
        return stopPlayerSit(source, stopReason, true, true);
    }

    public boolean stopPlayerSit(Entity source, StopReason stopReason, boolean removePassengers, boolean removeVehicle) {
        if (source == null) {
            return true;
        }
        UUID sourceUuid = source.getUniqueId();
        Map.Entry<UUID, List<UUID>> passengers = removePassengers ? bottomToTopStacks.get(sourceUuid) : null;
        Map.Entry<UUID, List<UUID>> vehicles = removeVehicle ? topToBottomStacks.get(sourceUuid) : null;
        if (passengers == null && vehicles == null) {
            return true;
        }

        // KICK-SCHUTZ & TELEPORT-SCHUTZ: Geschützte Admins dürfen unter keinen Umständen gekickt werden
        if (stopReason == StopReason.KICKED || stopReason == StopReason.TELEPORT) {
            if (passengers != null) {
                Player topPlayer = Bukkit.getPlayer(passengers.getKey());
                if (topPlayer != null && (isKickProtected(topPlayer) || isCarrierTeleporting(topPlayer.getUniqueId()))) {
                    passengers = null;
                }
            }
            if (vehicles != null && (isKickProtected(source) || isCarrierTeleporting(sourceUuid))) {
                vehicles = null;
            }
            if (passengers == null && vehicles == null) {
                return false;
            }
        }

        if (passengers != null) {
            UUID topUuid = passengers.getKey();
            internalDismounting.add(topUuid);
            try {
                source.eject();
            } finally {
                internalDismounting.remove(topUuid);
            }
            bottomToTopStacks.remove(sourceUuid);
            topToBottomStacks.remove(topUuid);
            for (UUID passengerEntityId : passengers.getValue()) {
                Entity passengerEntity = Bukkit.getEntity(passengerEntityId);
                if (passengerEntity == null) continue;
                internalDismounting.add(topUuid);
                try {
                    passengerEntity.eject();
                } finally {
                    internalDismounting.remove(topUuid);
                }
                if (!stopReason.isUsingEntityTask() || !plugin.isEnabled()) {
                    passengerEntity.remove();
                } else {
                    Bukkit.getScheduler().runTask(plugin, passengerEntity::remove);
                }
            }
        }

        if (vehicles != null) {
            internalDismounting.add(sourceUuid);
            try {
                source.leaveVehicle();
            } finally {
                internalDismounting.remove(sourceUuid);
            }
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

    public Map<UUID, Map.Entry<UUID, List<UUID>>> getBottomToTopStacks() {
        return bottomToTopStacks;
    }

    public Map<UUID, Map.Entry<UUID, List<UUID>>> getTopToBottomStacks() {
        return topToBottomStacks;
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
