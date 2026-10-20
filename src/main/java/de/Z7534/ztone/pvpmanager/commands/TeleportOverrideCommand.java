package de.Z7534.ztone.pvpmanager.commands;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import de.Z7534.ztone.pvpmanager.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class TeleportOverrideCommand extends AbstractCommand {

    public TeleportOverrideCommand(Pvpmanager plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if command can be executed (not in combat or has bypass)
        if (!canExecuteCommand(sender)) {
            return true;
        }

        if (!requirePermission(sender, Permissions.CMD_TELEPORTOVERRIDE)) {
            return true;
        }
        if (args.length < 2 || !(args[1].equalsIgnoreCase("true") || args[1].equalsIgnoreCase("false"))) {
            if (sender instanceof Player player) {
                plugin.getMessageDispatcher().send(player, "teleporoverride_usage");
            } else {
                sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("teleporoverride_usage")));
            }
            return true;
        }
        Player target = resolveTarget(args[0]);
        if (target == null) {
            notFound(sender, args[0]);
            return true;
        }
        boolean bypass = Boolean.parseBoolean(args[1]);
        plugin.getCombatManager().setTeleportBypass(target.getUniqueId(), bypass);
        if (sender instanceof Player player) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("target", target.getName());
            placeholders.put("bypass", Boolean.toString(bypass));
            plugin.getMessageDispatcher().send(player, "teleporoverride_set", placeholders);
        } else {
            String message = plugin.getLocaleManager().raw("teleporoverride_set")
                    .replace("{target}", target.getName())
                    .replace("{bypass}", Boolean.toString(bypass));
            sender.sendMessage(ColorUtils.colorize(message));
        }
        return true;
    }
}