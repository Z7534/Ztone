package de.Z7534.ztone.stonesit.command;

import de.Z7534.ztone.stonesit.config.StoneSitConfig;
import de.Z7534.ztone.stonesit.manager.SitManager;
import de.Z7534.ztone.stonesit.model.Seat;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class SitCommand implements CommandExecutor, TabCompleter {

    private final StoneSitConfig config;
    private final SitManager sitManager;
    private final EnvironmentUtil environmentUtil;

    public SitCommand(StoneSitConfig config, SitManager sitManager, EnvironmentUtil environmentUtil) {
        this.config = config;
        this.sitManager = sitManager;
        this.environmentUtil = environmentUtil;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!EnvironmentUtil.hasPermission(sender, "stonesit.reload", "GSit.Reload")) {
                config.sendMessage(sender, "no-permission");
                return true;
            }
            config.load();
            config.sendMessage(sender, "reloaded");
            return true;
        }

        if (!(sender instanceof Player player)) {
            config.sendMessage(sender, "only-players");
            return true;
        }

        if (!EnvironmentUtil.hasPermission(player, "stonesit.sit", "stonesit.sit.*", "GSit.Sit", "GSit.Sit.*")) {
            config.sendMessage(player, "no-permission");
            return true;
        }

        Seat existingSeat = sitManager.getSeatByEntity(player);
        if (existingSeat != null) {
            sitManager.removeSeat(existingSeat, StopReason.GET_UP);
            return true;
        }

        if (!player.isValid() || player.isSneaking() || !player.isOnGround() || player.getVehicle() != null || player.isSleeping()) {
            config.sendMessage(player, "action-sit-now-error");
            return true;
        }

        if (!environmentUtil.isEntityInAllowedWorld(player)) {
            config.sendMessage(player, "action-sit-world-error");
            return true;
        }

        Location playerLocation = player.getLocation();
        Block block = playerLocation.getBlock().isPassable()
                ? playerLocation.clone().subtract(0, 0.0625, 0).getBlock()
                : playerLocation.getBlock();

        if (sitManager.isBlacklistedSitBlockData(block.getBlockData())) {
            config.sendMessage(player, "action-sit-location-error");
            return true;
        }

        boolean overSize = false;
        try {
            for (BoundingBox boundingBox : block.getCollisionShape().getBoundingBoxes()) {
                if (boundingBox.getMaxY() > 1.25) {
                    overSize = true;
                    break;
                }
            }
        } catch (Throwable ignored) {
        }

        if (!config.isAllowUnsafe() && !(block.getRelative(BlockFace.UP).isPassable() && !overSize && (!block.isPassable() || !config.isCenterBlock()))) {
            config.sendMessage(player, "action-sit-location-error");
            return true;
        }

        if (!environmentUtil.canUseInLocation(block.getLocation(), player)) {
            config.sendMessage(player, "action-sit-region-error");
            return true;
        }

        if (!config.isSameBlockRest()
                && (!sitManager.kickSeatEntitiesFromBlock(block, player)
                || !sitManager.kickPoseEntitiesFromBlock(block, player))) {
            config.sendMessage(player, "action-sit-kick-error");
            return true;
        }

        if (sitManager.createCustomSeat(block, player, true) == null) {
            config.sendMessage(player, "action-sit-error");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1 && EnvironmentUtil.hasPermission(sender, "stonesit.reload", "GSit.Reload")) {
            if ("reload".startsWith(args[0].toLowerCase())) {
                return List.of("reload");
            }
        }
        return List.of();
    }
}
