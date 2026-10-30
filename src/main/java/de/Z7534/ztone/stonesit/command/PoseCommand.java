package de.Z7534.ztone.stonesit.command;

import de.Z7534.ztone.stonesit.config.StoneSitConfig;
import de.Z7534.ztone.stonesit.manager.SitManager;
import de.Z7534.ztone.stonesit.model.PoseType;
import de.Z7534.ztone.stonesit.model.StoneSitPose;
import de.Z7534.ztone.stonesit.model.StopReason;
import de.Z7534.ztone.stonesit.util.EnvironmentUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public final class PoseCommand implements CommandExecutor, TabCompleter {

    private final StoneSitConfig config;
    private final SitManager sitManager;
    private final EnvironmentUtil environmentUtil;
    private final PoseType poseType;
    private final String[] permissions;

    public PoseCommand(StoneSitConfig config, SitManager sitManager, EnvironmentUtil environmentUtil, PoseType poseType, String... permissions) {
        this.config = config;
        this.sitManager = sitManager;
        this.environmentUtil = environmentUtil;
        this.poseType = poseType;
        this.permissions = permissions;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            config.sendMessage(sender, "only-players");
            return true;
        }

        if (!EnvironmentUtil.hasPermission(player, permissions)) {
            config.sendMessage(player, "no-permission");
            return true;
        }

        if (!StoneSitPose.isAvailable()) {
            config.sendMessage(player, "action-pose-error");
            return true;
        }

        StoneSitPose existingPose = sitManager.getPoseByPlayer(player);
        if (existingPose != null && existingPose.getPoseType() == poseType) {
            sitManager.removePose(existingPose, StopReason.GET_UP);
            return true;
        }

        if (!player.isValid() || player.isSneaking() || !player.isOnGround() || player.getVehicle() != null || player.isSleeping()) {
            config.sendMessage(player, "action-pose-now-error");
            return true;
        }

        if (!environmentUtil.isEntityInAllowedWorld(player)) {
            config.sendMessage(player, "action-pose-world-error");
            return true;
        }

        Location playerLocation = player.getLocation();
        Block block = playerLocation.getBlock().isPassable()
                ? playerLocation.subtract(0d, 0.0625d, 0d).getBlock()
                : playerLocation.getBlock();

        if (sitManager.isBlacklistedSitBlockData(block.getBlockData())) {
            config.sendMessage(player, "action-pose-location-error");
            return true;
        }

        boolean overSize = false;
        try {
            for (BoundingBox boundingBox : block.getCollisionShape().getBoundingBoxes()) {
                if (boundingBox.getMaxY() > 1.25d) {
                    overSize = true;
                    break;
                }
            }
        } catch (Throwable ignored) {
        }

        if (!config.isAllowUnsafe() && !(block.getRelative(BlockFace.UP).isPassable() && !overSize && (!block.isPassable() || !config.isCenterBlock()))) {
            config.sendMessage(player, "action-pose-location-error");
            return true;
        }

        if (!environmentUtil.canUseInLocation(block.getLocation(), player)) {
            config.sendMessage(player, "action-pose-region-error");
            return true;
        }

        if (!config.isSameBlockRest()) {
            if (!sitManager.kickPoseEntitiesFromBlock(block, player) || !sitManager.kickSeatEntitiesFromBlock(block, player)) {
                config.sendMessage(player, "action-pose-kick-error");
                return true;
            }
        }

        if (sitManager.createPose(block, player, poseType) == null) {
            config.sendMessage(player, "action-pose-error");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        return Collections.emptyList();
    }
}
