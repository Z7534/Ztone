package de.Z7534.ztone.pvpmanager.commands;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import de.Z7534.ztone.pvpmanager.managers.OverrideManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.World;

import java.util.Map;
import java.util.UUID;
import de.Z7534.ztone.pvpmanager.utils.ColorUtils;

public class PvpCommand extends AbstractCommand {

    public PvpCommand(Pvpmanager plugin) {
        super(plugin);
    }

    private boolean canTogglePvp(Player player) {
        String dim = plugin.getOverrideManager().getDimensionKey(player.getWorld());
        if (plugin.getOverrideManager().isDimensionForced(dim) && !player.hasPermission(Permissions.EXEMPT_DIMENSION)) {
            return false;
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if command can be executed (not in combat or has bypass)
        if (!canExecuteCommand(sender)) {
            return true;
        }

        if (args.length >= 1 && plugin.getServer().getPlayerExact(args[0]) != null
                && (args.length == 1 || args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))
                && !args[0].equalsIgnoreCase(sender.getName())) {
            if (!requirePermission(sender, Permissions.CMD_PVP_OTHERS)) {
                return true;
            }
            Player target = resolveTarget(args[0]);
            if (target == null) {
                notFound(sender, args[0]);
                return true;
            }

            if (!canTogglePvp(target)) {
                if (sender instanceof Player player) {
                    plugin.getMessageDispatcher().send(player, "pvp_cant_change_dimension");
                } else {
                    sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("pvp_cant_change_dimension")));
                }
                return true;
            }
            boolean newState = args.length == 2 ? args[1].equalsIgnoreCase("on")
                    : !plugin.getPvpToggleManager().isPvpEnabled(target.getUniqueId());
            Player actor = sender instanceof Player p ? p : null;
            plugin.getPvpToggleManager().setPvpEnabled(target, newState, actor != null ? actor : target);
            return true;
        }

        if (!requirePermission(sender, Permissions.CMD_PVP)) {
            return true;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (!canTogglePvp(player)) {
            plugin.getMessageDispatcher().send(player, "pvp_cant_change_dimension");
            return true;
        }

        boolean newState = args.length >= 1 ? args[0].equalsIgnoreCase("on")
                : !plugin.getPvpToggleManager().isPvpEnabled(player.getUniqueId());
        plugin.getPvpToggleManager().requestToggle(player, newState);
        return true;
    }
}
