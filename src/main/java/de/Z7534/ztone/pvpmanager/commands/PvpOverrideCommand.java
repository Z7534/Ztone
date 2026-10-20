package de.Z7534.ztone.pvpmanager.commands;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import de.Z7534.ztone.pvpmanager.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class PvpOverrideCommand extends AbstractCommand {

    public PvpOverrideCommand(Pvpmanager plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if command can be executed (not in combat or has bypass)
        if (!canExecuteCommand(sender)) {
            return true;
        }

        if (args.length >= 1) {
            if (!requirePermission(sender, Permissions.CMD_OVERRIDE_OTHERS)) {
                return true;
            }
            Player target = resolveTarget(args[0]);
            if (target == null) {
                notFound(sender, args[0]);
                return true;
            }
            boolean nowEnabled = plugin.getOverrideManager().toggle(target.getUniqueId());
            if (nowEnabled) {
                if (sender instanceof Player player) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("target", target.getName());
                    plugin.getMessageDispatcher().send(player, "pvpoverride_enabled_others", placeholders);
                } else {
                    String message = plugin.getLocaleManager().raw("pvpoverride_enabled_others").replace("{target}", target.getName());
                    sender.sendMessage(ColorUtils.colorize(message));
                }
            } else {
                if (sender instanceof Player player) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("target", target.getName());
                    plugin.getMessageDispatcher().send(player, "pvpoverride_disabled_others", placeholders);
                } else {
                    String message = plugin.getLocaleManager().raw("pvpoverride_disabled_others").replace("{target}", target.getName());
                    sender.sendMessage(ColorUtils.colorize(message));
                }
            }
            return true;
        }

        if (!requirePermission(sender, Permissions.CMD_OVERRIDE)) {
            return true;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        boolean nowEnabled = plugin.getOverrideManager().toggle(player.getUniqueId());
        if (nowEnabled) {
            if (sender instanceof Player p) {
                plugin.getMessageDispatcher().send(p, "pvpoverride_enabled_self");
            } else {
                sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("pvpoverride_enabled_self")));
            }
        } else {
            if (sender instanceof Player p) {
                plugin.getMessageDispatcher().send(p, "pvpoverride_disabled_self");
            } else {
                sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("pvpoverride_disabled_self")));
            }
        }
        return true;
    }
}