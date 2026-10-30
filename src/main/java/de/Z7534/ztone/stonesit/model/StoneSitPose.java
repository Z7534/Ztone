package de.Z7534.ztone.stonesit.model;

import com.google.common.collect.ImmutableList;
import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.stonesit.config.StoneSitConfig;
import de.Z7534.ztone.stonesit.manager.SitManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Statistic;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

public final class StoneSitPose {

    public static final int SWING_MAIN_HAND = 0;
    public static final int SWING_OFF_HAND = 3;

    // =========================================================================
    //        STATISCH GECACHTE MOJANG-NMS-HANDLES (PAPER 26.2, 0 OVERHEAD)
    // =========================================================================
    private static final boolean NMS_AVAILABLE;

    private static Method CRAFT_SERVER_GET_SERVER;
    private static Method CRAFT_WORLD_GET_HANDLE;
    private static Method CRAFT_PLAYER_GET_HANDLE;
    private static Method CRAFT_BLOCK_DATA_GET_STATE;

    private static Constructor<?> BLOCK_POS_CTOR;
    private static Constructor<?> GAME_PROFILE_CTOR_3;
    private static Constructor<?> GAME_PROFILE_CTOR_2;
    private static Method GAME_PROFILE_PROPERTIES;

    private static Constructor<?> SERVER_PLAYER_CTOR;
    private static Method SERVER_PLAYER_GET_GAME_PROFILE;
    private static Method SERVER_PLAYER_CLIENT_INFO;
    private static Field SERVER_PLAYER_CONNECTION_FIELD;
    private static Field SERVER_PLAYER_CONTAINER_MENU_FIELD;
    private static Method CONTAINER_MENU_SEND_ALL_DATA;
    private static Method CONNECTION_SEND_PACKET;

    private static Method ENTITY_ABS_SNAP_TO;
    private static Method ENTITY_GET_ID;
    private static Method ENTITY_GET_UUID;
    private static Method ENTITY_GET_X;
    private static Method ENTITY_GET_Y;
    private static Method ENTITY_GET_Z;
    private static Method ENTITY_GET_X_ROT;
    private static Method ENTITY_GET_Y_ROT;
    private static Method ENTITY_GET_Y_HEAD_ROT;
    private static Method ENTITY_GET_TYPE;
    private static Method ENTITY_GET_DELTA_MOVEMENT;
    private static Method ENTITY_GET_ENTITY_DATA;
    private static Method ENTITY_HAS_GLOWING_TAG;
    private static Method ENTITY_SET_GLOWING_TAG;
    private static Method ENTITY_SET_INVISIBLE;
    private static Method ENTITY_SET_POSE;
    private static Method ENTITY_START_RIDING;
    private static Field ENTITY_VEHICLE_FIELD;
    private static Field ENTITY_PASSENGERS_FIELD;
    private static Field ENTITY_PERSIST_FIELD;

    private static Method LIVING_GET_SCALE;
    private static Method LIVING_GET_ATTRIBUTES;
    private static Method ATTRIBUTE_MAP_GET_SYNCABLE;
    private static Method LIVING_GET_ITEM_BY_SLOT;
    private static Method PLAYER_GET_INVENTORY;
    private static Method INVENTORY_GET_CONTENTS;

    private static Constructor<?> AEC_CTOR;
    private static Method AEC_SET_RADIUS;
    private static Method AEC_SET_DURATION;
    private static Method AEC_SET_NO_GRAVITY;
    private static Method AEC_SET_INVULNERABLE;

    private static Object POSE_SLEEPING_ENUM;
    private static Object[] EQUIPMENT_SLOT_VALUES;
    private static Object EMPTY_ITEM_STACK;
    private static Method PAIR_OF_METHOD;

    private static Object POSE_ACCESSOR;
    private static Object SLEEP_BLOCK_POS_ACCESSOR;
    private static Object MAIN_HAND_ACCESSOR;
    private static Object SKIN_ACCESSOR;
    private static Object LEFT_SHOULDER_ACCESSOR;
    private static Object RIGHT_SHOULDER_ACCESSOR;

    private static Method SYNCHED_GET;
    private static Method SYNCHED_SET;
    private static Method SYNCHED_IS_DIRTY;
    private static Method SYNCHED_PACK_DIRTY;
    private static Method SYNCHED_GET_NON_DEFAULT;

    private static Constructor<?> PKT_BLOCK_UPDATE_CTOR;
    private static Constructor<?> PKT_PLAYER_INFO_UPDATE_CTOR;
    private static EnumSet<?> PLAYER_INFO_ACTIONS;
    private static Constructor<?> PKT_PLAYER_INFO_REMOVE_CTOR;
    private static Constructor<?> PKT_REMOVE_ENTITIES_CTOR;
    private static Constructor<?> PKT_ADD_ENTITY_CTOR;
    private static Constructor<?> PKT_SET_ENTITY_DATA_CTOR;
    private static Constructor<?> PKT_UPDATE_ATTRIBUTES_CTOR;
    private static Method POS_MOVE_ROT_OF;
    private static Constructor<?> PKT_TELEPORT_ENTITY_CTOR;
    private static Constructor<?> PKT_SET_PASSENGERS_CTOR;
    private static Constructor<?> PKT_ROTATE_HEAD_CTOR;
    private static Constructor<?> PKT_SET_EQUIPMENT_CTOR;
    private static Constructor<?> PKT_HURT_ANIMATION_CTOR;
    private static Constructor<?> PKT_ANIMATE_CTOR;
    private static Constructor<?> PKT_BUNDLE_CTOR;

    static {
        boolean ok = false;
        try {
            Class<?> craftServerClass = Class.forName("org.bukkit.craftbukkit.CraftServer");
            Class<?> craftWorldClass = Class.forName("org.bukkit.craftbukkit.CraftWorld");
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            Class<?> craftBlockDataClass = Class.forName("org.bukkit.craftbukkit.block.data.CraftBlockData");

            CRAFT_SERVER_GET_SERVER = craftServerClass.getMethod("getServer");
            CRAFT_WORLD_GET_HANDLE = craftWorldClass.getMethod("getHandle");
            CRAFT_PLAYER_GET_HANDLE = craftPlayerClass.getMethod("getHandle");
            CRAFT_BLOCK_DATA_GET_STATE = craftBlockDataClass.getMethod("getState");

            Class<?> blockPosClass = Class.forName("net.minecraft.core.BlockPos");
            BLOCK_POS_CTOR = blockPosClass.getConstructor(int.class, int.class, int.class);

            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyMapClass = Class.forName("com.mojang.authlib.properties.PropertyMap");
            try {
                GAME_PROFILE_CTOR_3 = gameProfileClass.getConstructor(UUID.class, String.class, propertyMapClass);
            } catch (NoSuchMethodException ignored) {
                GAME_PROFILE_CTOR_2 = gameProfileClass.getConstructor(UUID.class, String.class);
            }
            try {
                GAME_PROFILE_PROPERTIES = gameProfileClass.getMethod("properties");
            } catch (NoSuchMethodException ignored) {
                GAME_PROFILE_PROPERTIES = gameProfileClass.getMethod("getProperties");
            }

            Class<?> minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer");
            Class<?> serverLevelClass = Class.forName("net.minecraft.server.level.ServerLevel");
            Class<?> levelClass = Class.forName("net.minecraft.world.level.Level");
            Class<?> clientInfoClass = Class.forName("net.minecraft.server.level.ClientInformation");
            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            Class<?> nmsPlayerClass = Class.forName("net.minecraft.world.entity.player.Player");
            Class<?> livingEntityClass = Class.forName("net.minecraft.world.entity.LivingEntity");
            Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");

            SERVER_PLAYER_CTOR = serverPlayerClass.getConstructor(
                    minecraftServerClass, serverLevelClass, gameProfileClass, clientInfoClass
            );
            SERVER_PLAYER_GET_GAME_PROFILE = nmsPlayerClass.getMethod("getGameProfile");
            SERVER_PLAYER_CLIENT_INFO = serverPlayerClass.getMethod("clientInformation");
            SERVER_PLAYER_CONNECTION_FIELD = serverPlayerClass.getField("connection");
            SERVER_PLAYER_CONTAINER_MENU_FIELD = nmsPlayerClass.getField("containerMenu");

            Class<?> containerMenuClass = Class.forName("net.minecraft.world.inventory.AbstractContainerMenu");
            CONTAINER_MENU_SEND_ALL_DATA = containerMenuClass.getMethod("sendAllDataToRemote");

            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
            Class<?> commonPacketListenerClass = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl");
            CONNECTION_SEND_PACKET = commonPacketListenerClass.getMethod("send", packetClass);

            ENTITY_ABS_SNAP_TO = entityClass.getMethod("absSnapTo", double.class, double.class, double.class, float.class, float.class);
            ENTITY_GET_ID = entityClass.getMethod("getId");
            ENTITY_GET_UUID = entityClass.getMethod("getUUID");
            ENTITY_GET_X = entityClass.getMethod("getX");
            ENTITY_GET_Y = entityClass.getMethod("getY");
            ENTITY_GET_Z = entityClass.getMethod("getZ");
            ENTITY_GET_X_ROT = entityClass.getMethod("getXRot");
            ENTITY_GET_Y_ROT = entityClass.getMethod("getYRot");
            ENTITY_GET_Y_HEAD_ROT = entityClass.getMethod("getYHeadRot");
            ENTITY_GET_TYPE = entityClass.getMethod("getType");
            ENTITY_GET_DELTA_MOVEMENT = entityClass.getMethod("getDeltaMovement");
            ENTITY_GET_ENTITY_DATA = entityClass.getMethod("getEntityData");
            ENTITY_HAS_GLOWING_TAG = entityClass.getMethod("hasGlowingTag");
            ENTITY_SET_GLOWING_TAG = entityClass.getMethod("setGlowingTag", boolean.class);
            ENTITY_SET_INVISIBLE = entityClass.getMethod("setInvisible", boolean.class);

            Class<?> nmsPoseClass = Class.forName("net.minecraft.world.entity.Pose");
            ENTITY_SET_POSE = entityClass.getMethod("setPose", nmsPoseClass);
            for (Object constant : nmsPoseClass.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals("SLEEPING")) {
                    POSE_SLEEPING_ENUM = constant;
                    break;
                }
            }

            ENTITY_START_RIDING = entityClass.getMethod("startRiding", entityClass, boolean.class, boolean.class);
            for (Field field : entityClass.getDeclaredFields()) {
                if (field.getType().equals(entityClass)) {
                    field.setAccessible(true);
                    ENTITY_VEHICLE_FIELD = field;
                    break;
                }
            }
            ENTITY_PASSENGERS_FIELD = entityClass.getField("passengers");
            ENTITY_PERSIST_FIELD = entityClass.getField("persist");

            LIVING_GET_SCALE = livingEntityClass.getMethod("getScale");
            LIVING_GET_ATTRIBUTES = livingEntityClass.getMethod("getAttributes");
            Class<?> attributeMapClass = Class.forName("net.minecraft.world.entity.ai.attributes.AttributeMap");
            ATTRIBUTE_MAP_GET_SYNCABLE = attributeMapClass.getMethod("getSyncableAttributes");

            Class<?> equipmentSlotClass = Class.forName("net.minecraft.world.entity.EquipmentSlot");
            EQUIPMENT_SLOT_VALUES = equipmentSlotClass.getEnumConstants();
            LIVING_GET_ITEM_BY_SLOT = livingEntityClass.getMethod("getItemBySlot", equipmentSlotClass);

            Class<?> nmsItemStackClass = Class.forName("net.minecraft.world.item.ItemStack");
            EMPTY_ITEM_STACK = nmsItemStackClass.getField("EMPTY").get(null);

            PLAYER_GET_INVENTORY = nmsPlayerClass.getMethod("getInventory");
            Class<?> inventoryClass = Class.forName("net.minecraft.world.entity.player.Inventory");
            INVENTORY_GET_CONTENTS = inventoryClass.getMethod("getContents");

            Class<?> pairClass = Class.forName("com.mojang.datafixers.util.Pair");
            PAIR_OF_METHOD = pairClass.getMethod("of", Object.class, Object.class);

            Class<?> aecClass = Class.forName("net.minecraft.world.entity.AreaEffectCloud");
            AEC_CTOR = aecClass.getConstructor(levelClass, double.class, double.class, double.class);
            AEC_SET_RADIUS = aecClass.getMethod("setRadius", float.class);
            AEC_SET_DURATION = aecClass.getMethod("setDuration", int.class);
            AEC_SET_NO_GRAVITY = entityClass.getMethod("setNoGravity", boolean.class);
            AEC_SET_INVULNERABLE = entityClass.getMethod("setInvulnerable", boolean.class);

            Class<?> serializersClass = Class.forName("net.minecraft.network.syncher.EntityDataSerializers");
            Class<?> serializerClass = Class.forName("net.minecraft.network.syncher.EntityDataSerializer");
            Method createAccessor = serializerClass.getMethod("createAccessor", int.class);

            POSE_ACCESSOR = createAccessor.invoke(serializersClass.getField("POSE").get(null), 6);
            SLEEP_BLOCK_POS_ACCESSOR = createAccessor.invoke(serializersClass.getField("OPTIONAL_BLOCK_POS").get(null), 14);
            MAIN_HAND_ACCESSOR = createAccessor.invoke(serializersClass.getField("HUMANOID_ARM").get(null), 15);
            SKIN_ACCESSOR = createAccessor.invoke(serializersClass.getField("BYTE").get(null), 16);
            LEFT_SHOULDER_ACCESSOR = createAccessor.invoke(serializersClass.getField("OPTIONAL_UNSIGNED_INT").get(null), 19);
            RIGHT_SHOULDER_ACCESSOR = createAccessor.invoke(serializersClass.getField("OPTIONAL_UNSIGNED_INT").get(null), 20);

            Class<?> accessorClass = Class.forName("net.minecraft.network.syncher.EntityDataAccessor");
            Class<?> synchedDataClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData");
            SYNCHED_GET = synchedDataClass.getMethod("get", accessorClass);
            SYNCHED_SET = synchedDataClass.getMethod("set", accessorClass, Object.class);
            SYNCHED_IS_DIRTY = synchedDataClass.getMethod("isDirty");
            SYNCHED_PACK_DIRTY = synchedDataClass.getMethod("packDirty");
            SYNCHED_GET_NON_DEFAULT = synchedDataClass.getMethod("getNonDefaultValues");

            Class<?> blockStateClass = Class.forName("net.minecraft.world.level.block.state.BlockState");
            Class<?> pktBlockUpdateClass = Class.forName("net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket");
            PKT_BLOCK_UPDATE_CTOR = pktBlockUpdateClass.getConstructor(blockPosClass, blockStateClass);

            Class<?> pktInfoUpdateClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<? extends Enum> actionEnumClass = (Class<? extends Enum>) Class.forName(
                    "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action"
            );
            @SuppressWarnings("unchecked")
            EnumSet<?> actions = EnumSet.of(
                    Enum.valueOf(actionEnumClass, "ADD_PLAYER"),
                    Enum.valueOf(actionEnumClass, "INITIALIZE_CHAT"),
                    Enum.valueOf(actionEnumClass, "UPDATE_GAME_MODE"),
                    Enum.valueOf(actionEnumClass, "UPDATE_LATENCY"),
                    Enum.valueOf(actionEnumClass, "UPDATE_DISPLAY_NAME")
            );
            PLAYER_INFO_ACTIONS = actions;
            PKT_PLAYER_INFO_UPDATE_CTOR = pktInfoUpdateClass.getConstructor(EnumSet.class, Collection.class);

            Class<?> pktInfoRemoveClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
            PKT_PLAYER_INFO_REMOVE_CTOR = pktInfoRemoveClass.getConstructor(List.class);

            Class<?> pktRemoveEntitiesClass = Class.forName("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket");
            PKT_REMOVE_ENTITIES_CTOR = pktRemoveEntitiesClass.getConstructor(int[].class);

            Class<?> entityTypeClass = Class.forName("net.minecraft.world.entity.EntityType");
            Class<?> vec3Class = Class.forName("net.minecraft.world.phys.Vec3");
            Class<?> pktAddEntityClass = Class.forName("net.minecraft.network.protocol.game.ClientboundAddEntityPacket");
            PKT_ADD_ENTITY_CTOR = pktAddEntityClass.getConstructor(
                    int.class, UUID.class, double.class, double.class, double.class,
                    float.class, float.class, entityTypeClass, int.class, vec3Class, double.class
            );

            Class<?> pktSetEntityDataClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");
            PKT_SET_ENTITY_DATA_CTOR = pktSetEntityDataClass.getConstructor(int.class, List.class);

            Class<?> pktUpdateAttributesClass = Class.forName("net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket");
            PKT_UPDATE_ATTRIBUTES_CTOR = pktUpdateAttributesClass.getConstructor(int.class, Collection.class);

            Class<?> posMoveRotClass = Class.forName("net.minecraft.world.entity.PositionMoveRotation");
            POS_MOVE_ROT_OF = posMoveRotClass.getMethod("of", entityClass);
            Class<?> pktTeleportEntityClass = Class.forName("net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket");
            PKT_TELEPORT_ENTITY_CTOR = pktTeleportEntityClass.getConstructor(
                    int.class, posMoveRotClass, Set.class, boolean.class
            );

            Class<?> pktSetPassengersClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetPassengersPacket");
            PKT_SET_PASSENGERS_CTOR = pktSetPassengersClass.getConstructor(entityClass);

            Class<?> pktRotateHeadClass = Class.forName("net.minecraft.network.protocol.game.ClientboundRotateHeadPacket");
            PKT_ROTATE_HEAD_CTOR = pktRotateHeadClass.getConstructor(entityClass, byte.class);

            Class<?> pktSetEquipmentClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket");
            PKT_SET_EQUIPMENT_CTOR = pktSetEquipmentClass.getConstructor(int.class, List.class);

            Class<?> pktHurtAnimClass = Class.forName("net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket");
            PKT_HURT_ANIMATION_CTOR = pktHurtAnimClass.getConstructor(livingEntityClass);

            Class<?> pktAnimateClass = Class.forName("net.minecraft.network.protocol.game.ClientboundAnimatePacket");
            PKT_ANIMATE_CTOR = pktAnimateClass.getConstructor(entityClass, int.class);

            Class<?> pktBundleClass = Class.forName("net.minecraft.network.protocol.game.ClientboundBundlePacket");
            PKT_BUNDLE_CTOR = pktBundleClass.getConstructor(Iterable.class);

            ok = true;
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[Ztone/StoneSit] Pose-NMS-Initialisierung fehlgeschlagen: " + t.getMessage());
        }
        NMS_AVAILABLE = ok;
    }

    public static boolean isAvailable() {
        return NMS_AVAILABLE;
    }

    // =========================================================================
    //                          INSTANZ-FELDER
    // =========================================================================
    private final Ztone plugin;
    private final StoneSitConfig config;
    private final Seat seat;
    private final Player seatPlayer;
    private final PoseType poseType;

    private Set<Player> nearbyPlayers = new HashSet<>();
    private final Object serverPlayer;
    private final Object playerNpc;
    private final int npcEntityId;
    private Object vehicleEntity;
    private int vehicleEntityId = -1;
    private final Object hideNameEntity;
    private final int hideNameEntityId;

    private final Location blockLocation;
    private final Block bedBlock;
    private final Object bedPos;
    private final BlockFace direction;

    private final Object setBedPacket;
    private final Object addNpcInfoPacket;
    private final Object removeNpcInfoPacket;
    private final Object removeNpcPacket;
    private final Object createNpcPacket;
    private Object metaNpcPacket;
    private Object attributeNpcPacket;
    private final Object teleportNpcPacket;
    private Object bundle;

    private List<Object> equipmentSlotCache;
    private Object mainSlotCache;
    private float directionCache = Float.NaN;
    private boolean sleepingIgnoredCache;
    private final int renderRangeSquared;

    // Zähler für das Liegen als Bett-Schlaf (100 Ticks = 5 Sekunden wie in Vanilla-Betten)
    private int layTicks = 0;

    public StoneSitPose(Ztone plugin, StoneSitConfig config, Seat seat, PoseType poseType) throws Exception {
        this.plugin = plugin;
        this.config = config;
        this.seat = seat;
        this.seatPlayer = (Player) seat.getEntity();
        this.poseType = poseType;

        this.serverPlayer = CRAFT_PLAYER_GET_HANDLE.invoke(seatPlayer);

        int renderRange = seatPlayer.getWorld().getSimulationDistance() * 16;
        this.renderRangeSquared = renderRange * renderRange;

        Location seatLocation = seat.getLocation();
        this.blockLocation = seatLocation.clone();
        this.blockLocation.setY(blockLocation.getWorld().getMinHeight());
        this.bedBlock = blockLocation.getBlock();
        this.bedPos = BLOCK_POS_CTOR.newInstance(
                blockLocation.getBlockX(),
                blockLocation.getBlockY(),
                blockLocation.getBlockZ()
        );

        Object serverLevel = CRAFT_WORLD_GET_HANDLE.invoke(seatLocation.getWorld());
        this.playerNpc = createNpc(serverLevel);
        this.npcEntityId = (int) ENTITY_GET_ID.invoke(playerNpc);

        double height = seatLocation.getY() + SitManager.BASE_OFFSET;
        float scale = (float) LIVING_GET_SCALE.invoke(serverPlayer);
        double offset = height + (0.1125d * scale);
        ENTITY_ABS_SNAP_TO.invoke(playerNpc, seatLocation.getX(), offset, seatLocation.getZ(), 0f, 0f);

        this.direction = getCardinalDirection(seatLocation.getYaw());

        // Fake-Bett-BlockState über Bukkit BlockData + CraftBlockData#getState erzeugen
        Bed fakeBedData = (Bed) Material.WHITE_BED.createBlockData();
        fakeBedData.setPart(Bed.Part.HEAD);
        fakeBedData.setFacing(direction.getOppositeFace());
        Object nmsBedBlockState = CRAFT_BLOCK_DATA_GET_STATE.invoke(fakeBedData);
        this.setBedPacket = PKT_BLOCK_UPDATE_CTOR.newInstance(bedPos, nmsBedBlockState);

        this.addNpcInfoPacket = PKT_PLAYER_INFO_UPDATE_CTOR.newInstance(
                PLAYER_INFO_ACTIONS, Collections.singletonList(playerNpc)
        );
        this.removeNpcInfoPacket = PKT_PLAYER_INFO_REMOVE_CTOR.newInstance(
                Collections.singletonList((UUID) ENTITY_GET_UUID.invoke(playerNpc))
        );
        this.createNpcPacket = createAddEntityPacket(playerNpc);
        this.teleportNpcPacket = PKT_TELEPORT_ENTITY_CTOR.newInstance(
                npcEntityId, POS_MOVE_ROT_OF.invoke(null, playerNpc), Set.of(), false
        );

        if (poseType == PoseType.LEGS_UP) {
            this.vehicleEntity = createDummyCloud(serverLevel, seatPlayer.getLocation());
            this.vehicleEntityId = (int) ENTITY_GET_ID.invoke(vehicleEntity);
            float sleepYaw = switch (direction) {
                case SOUTH -> 270f;
                case WEST -> 180f;
                case EAST -> 0f;
                default -> 90f;
            };
            ENTITY_ABS_SNAP_TO.invoke(vehicleEntity, seatLocation.getX(), height - (0.375d * scale), seatLocation.getZ(), sleepYaw, 0f);
            this.removeNpcPacket = PKT_REMOVE_ENTITIES_CTOR.newInstance((Object) new int[]{npcEntityId, vehicleEntityId});
        } else {
            this.removeNpcPacket = PKT_REMOVE_ENTITIES_CTOR.newInstance((Object) new int[]{npcEntityId});
        }

        this.hideNameEntity = createDummyCloud(serverLevel, seatPlayer.getLocation());
        this.hideNameEntityId = (int) ENTITY_GET_ID.invoke(hideNameEntity);
    }

    public void spawn() {
        try {
            this.nearbyPlayers = getNearbyPlayers();

            boolean glowing = (boolean) ENTITY_HAS_GLOWING_TAG.invoke(serverPlayer);
            ENTITY_SET_GLOWING_TAG.invoke(playerNpc, glowing);
            if (glowing) {
                ENTITY_SET_GLOWING_TAG.invoke(serverPlayer, false);
            }

            Object npcData = ENTITY_GET_ENTITY_DATA.invoke(playerNpc);
            Object playerData = ENTITY_GET_ENTITY_DATA.invoke(serverPlayer);

            SYNCHED_SET.invoke(npcData, POSE_ACCESSOR, POSE_SLEEPING_ENUM);
            SYNCHED_SET.invoke(npcData, SLEEP_BLOCK_POS_ACCESSOR, Optional.of(bedPos));
            SYNCHED_SET.invoke(npcData, MAIN_HAND_ACCESSOR, SYNCHED_GET.invoke(playerData, MAIN_HAND_ACCESSOR));
            SYNCHED_SET.invoke(npcData, SKIN_ACCESSOR, SYNCHED_GET.invoke(playerData, SKIN_ACCESSOR));
            SYNCHED_SET.invoke(npcData, LEFT_SHOULDER_ACCESSOR, SYNCHED_GET.invoke(playerData, LEFT_SHOULDER_ACCESSOR));
            SYNCHED_SET.invoke(npcData, RIGHT_SHOULDER_ACCESSOR, SYNCHED_GET.invoke(playerData, RIGHT_SHOULDER_ACCESSOR));
            SYNCHED_SET.invoke(playerData, LEFT_SHOULDER_ACCESSOR, OptionalInt.empty());
            SYNCHED_SET.invoke(playerData, RIGHT_SHOULDER_ACCESSOR, OptionalInt.empty());

            ENTITY_SET_INVISIBLE.invoke(serverPlayer, true);
            setEquipmentVisibility(false);

            // Lay zählt als Schlafen im Bett (ohne Respawn-Punkt zu setzen!)
            this.sleepingIgnoredCache = seatPlayer.isSleepingIgnored();
            if (config.isPoseLayNightSkip() && !sleepingIgnoredCache) {
                seatPlayer.setSleepingIgnored(true);
            }
            if (config.isPoseLayRest()) {
                seatPlayer.setStatistic(Statistic.TIME_SINCE_REST, 0);
            }

            boolean dirty = (boolean) SYNCHED_IS_DIRTY.invoke(npcData);
            List<?> packedData = (List<?>) (dirty
                    ? SYNCHED_PACK_DIRTY.invoke(npcData)
                    : SYNCHED_GET_NON_DEFAULT.invoke(npcData));
            this.metaNpcPacket = PKT_SET_ENTITY_DATA_CTOR.newInstance(npcEntityId, packedData != null ? packedData : List.of());

            Object attrMap = LIVING_GET_ATTRIBUTES.invoke(serverPlayer);
            Collection<?> syncableAttrs = (Collection<?>) ATTRIBUTE_MAP_GET_SYNCABLE.invoke(attrMap);
            this.attributeNpcPacket = PKT_UPDATE_ATTRIBUTES_CTOR.newInstance(npcEntityId, syncableAttrs);

            List<Object> packets = new ArrayList<>();
            packets.add(addNpcInfoPacket);
            packets.add(createNpcPacket);

            if (poseType == PoseType.LEGS_UP && vehicleEntity != null) {
                ENTITY_START_RIDING.invoke(playerNpc, vehicleEntity, true, false);
                ENTITY_SET_POSE.invoke(playerNpc, POSE_SLEEPING_ENUM);
                packets.add(createAddEntityPacket(vehicleEntity));
                Object vehicleData = ENTITY_GET_ENTITY_DATA.invoke(vehicleEntity);
                List<?> vehicleNonDefault = (List<?>) SYNCHED_GET_NON_DEFAULT.invoke(vehicleData);
                packets.add(PKT_SET_ENTITY_DATA_CTOR.newInstance(vehicleEntityId, vehicleNonDefault != null ? vehicleNonDefault : List.of()));
                packets.add(PKT_SET_PASSENGERS_CTOR.newInstance(vehicleEntity));
            }

            packets.add(setBedPacket);
            packets.add(metaNpcPacket);
            packets.add(attributeNpcPacket);

            this.bundle = PKT_BUNDLE_CTOR.newInstance(packets);

            for (Player viewer : nearbyPlayers) {
                addViewerPlayer(viewer);
            }

            // Nametag für den liegenden Spieler selbst verstecken
            attachHideNameEntity(hideNameEntity, playerNpc);
            List<Object> selfPackets = new ArrayList<>(3);
            selfPackets.add(createAddEntityPacket(hideNameEntity));
            Object hideData = ENTITY_GET_ENTITY_DATA.invoke(hideNameEntity);
            List<?> hideNonDefault = (List<?>) SYNCHED_GET_NON_DEFAULT.invoke(hideData);
            selfPackets.add(PKT_SET_ENTITY_DATA_CTOR.newInstance(hideNameEntityId, hideNonDefault != null ? hideNonDefault : List.of()));
            selfPackets.add(PKT_SET_PASSENGERS_CTOR.newInstance(playerNpc));
            sendPacketToNms(serverPlayer, PKT_BUNDLE_CTOR.newInstance(selfPackets));
        } catch (Throwable ignored) {
        }
    }

    public void tick() {
        try {
            if (!seatPlayer.isValid() || !seatPlayer.isOnline()) {
                return;
            }
            this.layTicks++;

            // Phantome fernhalten, solange der Spieler liegt
            if (config.isPoseLayRest() && layTicks % 100 == 0) {
                seatPlayer.setStatistic(Statistic.TIME_SINCE_REST, 0);
            }

            Set<Player> currentNearbyPlayers = getNearbyPlayers();

            for (Player viewer : currentNearbyPlayers) {
                if (nearbyPlayers.add(viewer)) {
                    addViewerPlayer(viewer);
                }
            }

            if (nearbyPlayers.size() != currentNearbyPlayers.size()) {
                for (Player viewer : new ArrayList<>(nearbyPlayers)) {
                    if (!currentNearbyPlayers.contains(viewer)) {
                        nearbyPlayers.remove(viewer);
                        removeViewerPlayer(viewer);
                    }
                }
            }

            updateDirection();
            ENTITY_SET_INVISIBLE.invoke(serverPlayer, true);
            updateEquipment();
            setEquipmentVisibility(false);
            updateSkin();

            if (config.isPoseLaySnoringSounds()) {
                long time = seatPlayer.getPlayerTime() % 24000L;
                if ((!config.isPoseLaySnoringNightOnly() || (time >= 12500L && time <= 23500L))
                        && (seatPlayer.getPlayerTime() % 90L == 0L)) {
                    Location loc = seat.getLocation();
                    for (Player viewer : nearbyPlayers) {
                        viewer.playSound(loc, Sound.ENTITY_FOX_SLEEP, SoundCategory.PLAYERS, 1.5f, 0f);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void addViewerPlayer(Player viewer) {
        sendPacket(viewer, bundle);
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (viewer.isOnline()) {
                sendPacket(viewer, teleportNpcPacket);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (viewer.isOnline()) {
                        sendPacket(viewer, teleportNpcPacket);
                    }
                }, 2L);
            }
        }, 2L);
    }

    public void remove() {
        try {
            for (Player viewer : nearbyPlayers) {
                removeViewerPlayer(viewer);
            }
            nearbyPlayers.clear();

            if (vehicleEntityId != -1) {
                sendPacketToNms(serverPlayer, PKT_REMOVE_ENTITIES_CTOR.newInstance((Object) new int[]{vehicleEntityId, hideNameEntityId}));
            } else {
                sendPacketToNms(serverPlayer, PKT_REMOVE_ENTITIES_CTOR.newInstance((Object) new int[]{hideNameEntityId}));
            }

            if (config.isPoseLayNightSkip() && !sleepingIgnoredCache) {
                seatPlayer.setSleepingIgnored(false);
            }

            if (!seatPlayer.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                ENTITY_SET_INVISIBLE.invoke(serverPlayer, false);
            }

            setEquipmentVisibility(true);

            Object npcData = ENTITY_GET_ENTITY_DATA.invoke(playerNpc);
            Object playerData = ENTITY_GET_ENTITY_DATA.invoke(serverPlayer);
            SYNCHED_SET.invoke(playerData, LEFT_SHOULDER_ACCESSOR, SYNCHED_GET.invoke(npcData, LEFT_SHOULDER_ACCESSOR));
            SYNCHED_SET.invoke(playerData, RIGHT_SHOULDER_ACCESSOR, SYNCHED_GET.invoke(npcData, RIGHT_SHOULDER_ACCESSOR));

            boolean glowing = (boolean) ENTITY_HAS_GLOWING_TAG.invoke(playerNpc);
            ENTITY_SET_GLOWING_TAG.invoke(serverPlayer, glowing);
        } catch (Throwable ignored) {
        }
    }

    private void removeViewerPlayer(Player viewer) {
        if (!viewer.isOnline()) {
            return;
        }
        try {
            Object handle = CRAFT_PLAYER_GET_HANDLE.invoke(viewer);
            sendPacketToNms(handle, removeNpcInfoPacket);
            sendPacketToNms(handle, removeNpcPacket);
            viewer.sendBlockChange(blockLocation, bedBlock.getBlockData());
        } catch (Throwable ignored) {
        }
    }

    private Set<Player> getNearbyPlayers() {
        Set<Player> result = new HashSet<>();
        Location seatLoc = seat.getLocation();
        for (Player p : seatPlayer.getWorld().getPlayers()) {
            if (p.canSee(seatPlayer) && seatLoc.distanceSquared(p.getLocation()) <= renderRangeSquared) {
                result.add(p);
            }
        }
        return result;
    }

    private void updateDirection() throws Exception {
        float playerYaw = seatPlayer.getLocation().getYaw();
        if (directionCache == playerYaw) {
            return;
        }
        directionCache = playerYaw;

        if (direction == BlockFace.WEST) playerYaw -= 90f;
        if (direction == BlockFace.EAST) playerYaw += 90f;
        if (direction == BlockFace.NORTH) playerYaw -= 180f;

        playerYaw = (playerYaw < 0f ? 360f + playerYaw : playerYaw) % 360f;
        float clampedYaw = playerYaw >= 315f ? playerYaw - 360f : playerYaw <= 45f ? playerYaw : playerYaw >= 180f ? -45f : 45f;
        byte fixedRotation = getFixedRotation(clampedYaw);

        Object rotateHeadPacket = PKT_ROTATE_HEAD_CTOR.newInstance(playerNpc, fixedRotation);
        for (Player viewer : nearbyPlayers) {
            sendPacket(viewer, rotateHeadPacket);
        }
    }

    private void updateSkin() throws Exception {
        ENTITY_SET_INVISIBLE.invoke(playerNpc, seatPlayer.hasPotionEffect(PotionEffectType.INVISIBILITY));

        Object npcData = ENTITY_GET_ENTITY_DATA.invoke(playerNpc);
        Object playerData = ENTITY_GET_ENTITY_DATA.invoke(serverPlayer);
        SYNCHED_SET.invoke(npcData, MAIN_HAND_ACCESSOR, SYNCHED_GET.invoke(playerData, MAIN_HAND_ACCESSOR));
        SYNCHED_SET.invoke(npcData, SKIN_ACCESSOR, SYNCHED_GET.invoke(playerData, SKIN_ACCESSOR));

        if (!(boolean) SYNCHED_IS_DIRTY.invoke(npcData)) {
            return;
        }
        List<?> dirtyList = (List<?>) SYNCHED_PACK_DIRTY.invoke(npcData);
        if (dirtyList == null || dirtyList.isEmpty()) {
            return;
        }
        Object entityDataPacket = PKT_SET_ENTITY_DATA_CTOR.newInstance(npcEntityId, dirtyList);
        for (Player viewer : nearbyPlayers) {
            sendPacket(viewer, entityDataPacket);
        }
    }

    private void updateEquipment() throws Exception {
        Object mainItemStack = LIVING_GET_ITEM_BY_SLOT.invoke(serverPlayer, EQUIPMENT_SLOT_VALUES[0]);
        Object inv = PLAYER_GET_INVENTORY.invoke(serverPlayer);
        List<?> contents = (List<?>) INVENTORY_GET_CONTENTS.invoke(inv);

        if (equipmentSlotCache != null && equipmentSlotCache.equals(contents) && mainSlotCache == mainItemStack) {
            return;
        }

        equipmentSlotCache = new ArrayList<>(contents);
        mainSlotCache = mainItemStack;

        List<Object> equipmentList = new ArrayList<>(EQUIPMENT_SLOT_VALUES.length);
        for (Object slot : EQUIPMENT_SLOT_VALUES) {
            Object itemStack = LIVING_GET_ITEM_BY_SLOT.invoke(serverPlayer, slot);
            equipmentList.add(PAIR_OF_METHOD.invoke(null, slot, itemStack != null ? itemStack : EMPTY_ITEM_STACK));
        }

        Object setEquipmentPacket = PKT_SET_EQUIPMENT_CTOR.newInstance(npcEntityId, equipmentList);
        for (Player viewer : nearbyPlayers) {
            sendPacket(viewer, setEquipmentPacket);
        }

        Object containerMenu = SERVER_PLAYER_CONTAINER_MENU_FIELD.get(serverPlayer);
        if (containerMenu != null) {
            CONTAINER_MENU_SEND_ALL_DATA.invoke(containerMenu);
        }
    }

    private void setEquipmentVisibility(boolean visibility) throws Exception {
        List<Object> equipmentList = new ArrayList<>(EQUIPMENT_SLOT_VALUES.length);
        for (Object slot : EQUIPMENT_SLOT_VALUES) {
            Object itemStack = visibility ? LIVING_GET_ITEM_BY_SLOT.invoke(serverPlayer, slot) : null;
            equipmentList.add(PAIR_OF_METHOD.invoke(null, slot, itemStack != null ? itemStack : EMPTY_ITEM_STACK));
        }

        int realPlayerId = (int) ENTITY_GET_ID.invoke(serverPlayer);
        Object setEquipmentPacket = PKT_SET_EQUIPMENT_CTOR.newInstance(realPlayerId, equipmentList);
        for (Player viewer : nearbyPlayers) {
            sendPacket(viewer, setEquipmentPacket);
        }
    }

    public void playDamageAnimation() {
        try {
            Object packet = PKT_HURT_ANIMATION_CTOR.newInstance(playerNpc);
            for (Player viewer : nearbyPlayers) {
                sendPacket(viewer, packet);
            }
        } catch (Throwable ignored) {
        }
    }

    public void playHandAnimation(int animationId) {
        try {
            Object packet = PKT_ANIMATE_CTOR.newInstance(playerNpc, animationId);
            for (Player viewer : nearbyPlayers) {
                sendPacket(viewer, packet);
            }
        } catch (Throwable ignored) {
        }
    }

    public void keepInvisible() {
        try {
            ENTITY_SET_INVISIBLE.invoke(serverPlayer, true);
        } catch (Throwable ignored) {
        }
    }

    private Object createNpc(Object serverLevel) throws Exception {
        Object minecraftServer = CRAFT_SERVER_GET_SERVER.invoke(Bukkit.getServer());
        Object playerProfile = SERVER_PLAYER_GET_GAME_PROFILE.invoke(serverPlayer);
        Object properties = GAME_PROFILE_PROPERTIES.invoke(playerProfile);

        Object npcProfile;
        if (GAME_PROFILE_CTOR_3 != null) {
            npcProfile = GAME_PROFILE_CTOR_3.newInstance(UUID.randomUUID(), seatPlayer.getName(), properties);
        } else {
            npcProfile = GAME_PROFILE_CTOR_2.newInstance(UUID.randomUUID(), seatPlayer.getName());
        }

        Object clientInfo = SERVER_PLAYER_CLIENT_INFO.invoke(serverPlayer);
        Object npc = SERVER_PLAYER_CTOR.newInstance(minecraftServer, serverLevel, npcProfile, clientInfo);
        SERVER_PLAYER_CONNECTION_FIELD.set(npc, SERVER_PLAYER_CONNECTION_FIELD.get(serverPlayer));
        return npc;
    }

    private Object createDummyCloud(Object serverLevel, Location loc) throws Exception {
        Object cloud = AEC_CTOR.newInstance(serverLevel, loc.getX(), loc.getY(), loc.getZ());
        ENTITY_PERSIST_FIELD.setBoolean(cloud, false);
        AEC_SET_RADIUS.invoke(cloud, 0f);
        AEC_SET_DURATION.invoke(cloud, Integer.MAX_VALUE);
        AEC_SET_NO_GRAVITY.invoke(cloud, true);
        AEC_SET_INVULNERABLE.invoke(cloud, true);
        return cloud;
    }

    private void attachHideNameEntity(Object passenger, Object vehicle) throws Exception {
        if (ENTITY_VEHICLE_FIELD != null) {
            ENTITY_VEHICLE_FIELD.set(passenger, vehicle);
        }
        ENTITY_PASSENGERS_FIELD.set(vehicle, ImmutableList.of(passenger));
    }

    private Object createAddEntityPacket(Object nmsEntity) throws Exception {
        return PKT_ADD_ENTITY_CTOR.newInstance(
                (int) ENTITY_GET_ID.invoke(nmsEntity),
                (UUID) ENTITY_GET_UUID.invoke(nmsEntity),
                (double) ENTITY_GET_X.invoke(nmsEntity),
                (double) ENTITY_GET_Y.invoke(nmsEntity),
                (double) ENTITY_GET_Z.invoke(nmsEntity),
                (float) ENTITY_GET_X_ROT.invoke(nmsEntity),
                (float) ENTITY_GET_Y_ROT.invoke(nmsEntity),
                ENTITY_GET_TYPE.invoke(nmsEntity),
                0,
                ENTITY_GET_DELTA_MOVEMENT.invoke(nmsEntity),
                (double) ((float) ENTITY_GET_Y_HEAD_ROT.invoke(nmsEntity))
        );
    }

    private void sendPacket(Player player, Object packet) {
        try {
            sendPacketToNms(CRAFT_PLAYER_GET_HANDLE.invoke(player), packet);
        } catch (Throwable ignored) {
        }
    }

    private void sendPacketToNms(Object nmsPlayer, Object packet) throws Exception {
        Object connection = SERVER_PLAYER_CONNECTION_FIELD.get(nmsPlayer);
        if (connection != null) {
            CONNECTION_SEND_PACKET.invoke(connection, packet);
        }
    }

    private static byte getFixedRotation(float rotation) {
        return (byte) (rotation * 256f / 360f);
    }

    private static BlockFace getCardinalDirection(float yaw) {
        if (yaw >= 135f || yaw < -135f) return BlockFace.NORTH;
        if (yaw >= -135f && yaw < -45f) return BlockFace.EAST;
        if (yaw >= -45f && yaw < 45f) return BlockFace.SOUTH;
        if (yaw >= 45f) return BlockFace.WEST;
        return BlockFace.NORTH;
    }

    public Seat getSeat() {
        return seat;
    }

    public Player getPlayer() {
        return seatPlayer;
    }

    public PoseType getPoseType() {
        return poseType;
    }

    public int getLayTicks() {
        return layTicks;
    }

    public void resetLayTicks() {
        this.layTicks = 0;
    }

    public boolean wasSleepingIgnoredBeforePose() {
        return sleepingIgnoredCache;
    }
}
