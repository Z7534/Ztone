package de.Z7534.ztone.mapcontrol.commands;

import de.Z7534.ztone.mapcontrol.MapControl;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MapControlCommand implements CommandExecutor, TabCompleter {

    private final MapControl plugin;

    public MapControlCommand(MapControl plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mapcontrol.admin")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload" -> {
                plugin.getPluginConfig().load();
                plugin.applyRestrictionsToAll();
                sender.sendMessage(Component.text("[MapControl] Configuration reloaded and re-applied to all online players.", NamedTextColor.GREEN));
                return true;
            }
            case "info", "status" -> {
                sendInfo(sender);
                return true;
            }
            case "send" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /mapcontrol send <player|all>", NamedTextColor.RED));
                    return true;
                }
                if (args[1].equalsIgnoreCase("all")) {
                    plugin.applyRestrictionsToAll();
                    sender.sendMessage(Component.text("[MapControl] Sent restrictions to all online players.", NamedTextColor.GREEN));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(Component.text("Player '" + args[1] + "' not found.", NamedTextColor.RED));
                    return true;
                }
                plugin.applyAllRestrictions(target);
                sender.sendMessage(Component.text("[MapControl] Sent restrictions to " + target.getName() + ".", NamedTextColor.GREEN));
                return true;
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== MapControl Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/mapcontrol reload", NamedTextColor.YELLOW)
                .append(Component.text(" - Reload config and re-apply settings", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/mapcontrol info", NamedTextColor.YELLOW)
                .append(Component.text(" - Display current settings", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/mapcontrol send <player|all>", NamedTextColor.YELLOW)
                .append(Component.text(" - Force re-send restrictions to player(s)", NamedTextColor.GRAY)));
    }

    private void sendInfo(CommandSender sender) {
        sender.sendMessage(Component.text("=== MapControl Status ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Xaero: ", NamedTextColor.YELLOW)
                .append(Component.text("Cave: " + (plugin.getPluginConfig().isXaeroDisableCaveMode(null) ? "DISABLED" : "ALLOWED") + " | Radar: " + (plugin.getPluginConfig().isXaeroDisableRadar(null) ? "DISABLED" : "ALLOWED") + " | Minimap: " + (plugin.getPluginConfig().isXaeroDisableMinimap(null) ? "DISABLED" : "ENABLED"), NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("JourneyMap: ", NamedTextColor.YELLOW)
                .append(Component.text("Cave: " + (plugin.getPluginConfig().isJourneyMapAllowCaveMapping(null) ? "ALLOWED" : "DISABLED") + " | Radar: " + (plugin.getPluginConfig().isJourneyMapAllowRadar(null) ? "ALLOWED" : "DISABLED"), NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("VoxelMap: ", NamedTextColor.YELLOW)
                .append(Component.text("Cave: " + (plugin.getPluginConfig().isVoxelMapDisableCaveMode(null) ? "DISABLED" : "ALLOWED") + " | Radar: " + (plugin.getPluginConfig().isVoxelMapDisableRadar(null) ? "DISABLED" : "ALLOWED"), NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("Bypass Permission: ", NamedTextColor.YELLOW)
                .append(Component.text(plugin.getPluginConfig().getBypassPermission(), NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("Online Players: ", NamedTextColor.YELLOW)
                .append(Component.text(Bukkit.getOnlinePlayers().size(), NamedTextColor.GREEN)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("mapcontrol.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> options = Arrays.asList("reload", "info", "status", "send", "help");
            List<String> result = new ArrayList<>();
            for (String opt : options) {
                if (opt.toLowerCase().startsWith(args[0].toLowerCase())) {
                    result.add(opt);
                }
            }
            return result;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("send")) {
            List<String> result = new ArrayList<>();
            if ("all".startsWith(args[1].toLowerCase())) {
                result.add("all");
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    result.add(p.getName());
                }
            }
            return result;
        }

        return Collections.emptyList();
    }
}
