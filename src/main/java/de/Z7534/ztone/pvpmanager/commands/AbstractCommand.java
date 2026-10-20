package de.Z7534.ztone.pvpmanager.commands;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.managers.CombatManager;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import de.Z7534.ztone.pvpmanager.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public abstract class AbstractCommand implements CommandExecutor {

    protected final Pvpmanager plugin;

    protected AbstractCommand(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    protected boolean requirePermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            if (sender instanceof Player player) {
                plugin.getMessageDispatcher().send(player, "Error_Permission");
            } else {
                sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("Error_Permission")));
            }
            return false;
        }
        return true;
    }

    protected Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("Error_Not_Player")));
        return null;
    }

    protected Player resolveTarget(String name) {
        return Bukkit.getPlayerExact(name);
    }

    protected void notFound(CommandSender sender, String name) {
        if (sender instanceof Player player) {
            plugin.getMessageDispatcher().send(player, "Error_Player_Not_Found", Map.of("player", name));
        } else {
            sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("Error_Player_Not_Found").replace("{player}", name)));
        }
    }

    /**
     * Checks if a player can execute commands (not in combat or has bypass permission)
     * @param sender The command sender
     * @return true if the command can be executed, false otherwise
     */
    protected boolean canExecuteCommand(CommandSender sender) {
        // Only check for players, console/command blocks can always execute
        if (!(sender instanceof Player player)) {
            return true;
        }

        // Check if player is in combat
        CombatManager combatManager = plugin.getCombatManager();
        if (combatManager.isTagged(player.getUniqueId())) {
            // Player is in combat, check if they have bypass permission
            if (!player.hasPermission(Permissions.EXEMPT_BLOCKCOMMANDS)
                    && !player.hasPermission(Permissions.EXEMPT)) {
                // Send error message
                plugin.getMessageDispatcher().send(player, "Command_Denied_InCombat");
                return false;
            }
        }
        return true;
    }
}
