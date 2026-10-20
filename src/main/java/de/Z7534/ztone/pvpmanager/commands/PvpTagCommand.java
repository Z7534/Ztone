package de.Z7534.ztone.pvpmanager.commands;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import de.Z7534.ztone.pvpmanager.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class PvpTagCommand extends AbstractCommand {

    public PvpTagCommand(Pvpmanager plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if command can be executed (not in combat or has bypass)
        if (!canExecuteCommand(sender)) {
            return true;
        }

        if (args.length >= 1) {
            if (!requirePermission(sender, Permissions.CMD_TAG_OTHERS)) {
                return true;
            }
            Player target = resolveTarget(args[0]);
            if (target == null) {
                notFound(sender, args[0]);
                return true;
            }
            plugin.getCombatManager().tag(target, null);
            if (sender instanceof Player player) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("target", target.getName());
                plugin.getMessageDispatcher().send(player, "pvtg_tagged_manual", placeholders);
            } else {
                String message = plugin.getLocaleManager().raw("pvtg_tagged_manual").replace("{target}", target.getName());
                sender.sendMessage(ColorUtils.colorize(message));
            }
            return true;
        }

        if (!requirePermission(sender, Permissions.CMD_TAG)) {
            return true;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (!plugin.getCombatManager().isTagged(player.getUniqueId())) {
            plugin.getMessageDispatcher().send(player, "Tag_Not_In_Combat");
            return true;
        }

        long remaining = plugin.getCombatManager().remainingSeconds(player.getUniqueId());
        String message = plugin.getLocaleManager().raw("Tag_Timeleft")
                .replace("{time}", de.Z7534.ztone.pvpmanager.utils.TimeFormatter.format(plugin, remaining));
        if (sender instanceof Player p) {
            plugin.getMessageDispatcher().send(p, message);
        } else {
            sender.sendMessage(ColorUtils.colorize(message));
        }
        return true;
    }
}