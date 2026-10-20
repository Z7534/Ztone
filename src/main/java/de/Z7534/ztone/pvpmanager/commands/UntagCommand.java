package de.Z7534.ztone.pvpmanager.commands;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import de.Z7534.ztone.pvpmanager.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class UntagCommand extends AbstractCommand {

    public UntagCommand(Pvpmanager plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if command can be executed (not in combat or has bypass)
        if (!canExecuteCommand(sender)) {
            return true;
        }

        if (!requirePermission(sender, Permissions.CMD_UNTAG)) {
            return true;
        }
        if (args.length < 1) {
            if (sender instanceof Player player) {
                plugin.getMessageDispatcher().send(player, "untag_usage");
            } else {
                sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("untag_usage")));
            }
            return true;
        }

        if (args[0].equals("*") || args[0].equalsIgnoreCase("all")) {
            plugin.getCombatManager().untagAll();
            if (sender instanceof Player player) {
                plugin.getMessageDispatcher().send(player, "untag_all");
            } else {
                sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("untag_all")));
            }
            return true;
        }

        Player target = resolveTarget(args[0]);
        if (target == null) {
            notFound(sender, args[0]);
            return true;
        }
        plugin.getCombatManager().untag(target);
        if (sender instanceof Player player) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("target", target.getName());
            plugin.getMessageDispatcher().send(player, "untag_one", placeholders);
        } else {
            String message = plugin.getLocaleManager().raw("untag_one").replace("{target}", target.getName());
            sender.sendMessage(ColorUtils.colorize(message));
        }
        return true;
    }
}