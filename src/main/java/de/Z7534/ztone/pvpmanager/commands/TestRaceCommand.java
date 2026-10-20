package de.Z7534.ztone.pvpmanager.commands;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import de.Z7534.ztone.pvpmanager.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class TestRaceCommand extends AbstractCommand {

    public TestRaceCommand(Pvpmanager plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if command can be executed (not in combat or has bypass)
        if (!canExecuteCommand(sender)) {
            return true;
        }

        if (!requirePermission(sender, Permissions.CMD_TESTRACE)) {
            return true;
        }

        if (args.length < 1) {
            if (sender instanceof Player player) {
                plugin.getMessageDispatcher().send(player, "testrace_usage");
            } else {
                sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("testrace_usage")));
            }
            return true;
        }

        String scenario = args[0].toLowerCase();
        Player target = null;
        if (args.length >= 2) {
            target = resolveTarget(args[1]);
            if (target == null) {
                notFound(sender, args[1]);
                return true;
            }
        }

        switch (scenario) {
            case "double-tag":
                plugin.getCombatManager().tag(target, null);
                plugin.getCombatManager().tag(target, null);
                break;
            case "rapid-untag":
                plugin.getCombatManager().tag(target, null);
                plugin.getCombatManager().untag(target);
                plugin.getCombatManager().tag(target, null);
                break;
            case "concurrent-toggle":
                plugin.getPvpToggleManager().requestToggle(target, true);
                plugin.getPvpToggleManager().requestToggle(target, false);
                break;
            default:
                if (sender instanceof Player player) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("scenario", args[0]);
                    plugin.getMessageDispatcher().send(player, "testrace_invalid_scenario", placeholders);
                } else {
                    String message = plugin.getLocaleManager().raw("testrace_invalid_scenario").replace("{scenario}", args[0]);
                    sender.sendMessage(ColorUtils.colorize(message));
                }
                return true;
        }

        if (sender instanceof Player player) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("scenario", args[0]);
            placeholders.put("target", target.getName());
            plugin.getMessageDispatcher().send(player, "testrace_success", placeholders);
        } else {
            String message = plugin.getLocaleManager().raw("testrace_success")
                    .replace("{scenario}", args[0])
                    .replace("{target}", target.getName());
            sender.sendMessage(ColorUtils.colorize(message));
        }
        return true;
    }
}