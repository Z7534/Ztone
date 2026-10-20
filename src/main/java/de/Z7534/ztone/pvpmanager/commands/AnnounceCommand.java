package de.Z7534.ztone.pvpmanager.commands;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import de.Z7534.ztone.pvpmanager.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AnnounceCommand extends AbstractCommand {

    public AnnounceCommand(Pvpmanager plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if command can be executed (not in combat or has bypass)
        if (!canExecuteCommand(sender)) {
            return true;
        }

        if (!requirePermission(sender, Permissions.CMD_ANNOUNCE)) {
            return true;
        }
        if (args.length < 1) {
            if (sender instanceof Player player) {
                plugin.getMessageDispatcher().send(player, "announce_usage");
            } else {
                sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("announce_usage")));
            }
            return true;
        }

        String message = String.join(" ", args)
                .replace("{prefix}", plugin.getLocaleManager().raw("Prefix"));
        Bukkit.broadcastMessage(ColorUtils.colorize(message));
        return true;
    }
}
