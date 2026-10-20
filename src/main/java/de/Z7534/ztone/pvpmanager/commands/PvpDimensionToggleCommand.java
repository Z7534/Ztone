package de.Z7534.ztone.pvpmanager.commands;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import de.Z7534.ztone.pvpmanager.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PvpDimensionToggleCommand extends AbstractCommand implements TabExecutor {

    public PvpDimensionToggleCommand(Pvpmanager plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if command can be executed (not in combat or has bypass)
        if (!canExecuteCommand(sender)) {
            return true;
        }

        if (!requirePermission(sender, Permissions.CMD_PVPDIMENSIONTOGGLE)) {
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("pvpdimensiontoggle_usage")));
            sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("pvpdimensiontoggle_example")));
            return true;
        }

        // Handle wildcard "*" for all dimensions
        boolean applyToAllDimensions = args[0].equalsIgnoreCase("*");
        String dimensionInput = applyToAllDimensions ? "" : args[0];
        String stateInput = args[1].toLowerCase();
        boolean force = false;
        if (args.length >= 3 && args[2].equalsIgnoreCase("--force")) {
            force = true;
        }

        boolean enabled;
        if (stateInput.equals("on")) {
            enabled = true;
        } else if (stateInput.equals("off")) {
            enabled = false;
        } else {
            sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("pvpdimensiontoggle_invalid_state")));
            return true;
        }

        if (applyToAllDimensions) {
            // Apply to ALL dimensions and loaded worlds
            plugin.getOverrideManager().setAllDimensionsDefault(enabled, force);

            String stateText = enabled ? plugin.getLocaleManager().raw("Enabled") : plugin.getLocaleManager().raw("Disabled");
            String forceText = force ? " " + plugin.getLocaleManager().raw("pvpdimensiontoggle_force") : "";
            String rawBroadcast = plugin.getLocaleManager().raw("pvpdimensiontoggle_broadcast_all");
            if (!rawBroadcast.contains("{force}")) {
                rawBroadcast = rawBroadcast + "{force}";
            }
            String message = rawBroadcast
                    .replace("{state}", stateText)
                    .replace("{force}", forceText);
            Bukkit.broadcastMessage(ColorUtils.colorize(message));
        } else {
            String dimensionKey = normalizeDimension(dimensionInput);
            if (dimensionKey == null) {
                if (sender instanceof Player player) {
                    plugin.getMessageDispatcher().send(player, "pvpdimensiontoggle_invalid_dimension");
                } else {
                    sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("pvpdimensiontoggle_invalid_dimension")));
                }
                return true;
            }

            plugin.getOverrideManager().setDimensionDefault(dimensionKey, enabled, force);

            String stateText = enabled ? plugin.getLocaleManager().raw("Enabled") : plugin.getLocaleManager().raw("Disabled");
            String forceText = force ? " " + plugin.getLocaleManager().raw("pvpdimensiontoggle_force") : "";
            String rawBroadcast = plugin.getLocaleManager().raw("pvpdimensiontoggle_broadcast_single");
            if (!rawBroadcast.contains("{force}")) {
                rawBroadcast = rawBroadcast + "{force}";
            }
            String message = rawBroadcast
                    .replace("{dimension}", dimensionKey)
                    .replace("{state}", stateText)
                    .replace("{force}", forceText);
            Bukkit.broadcastMessage(ColorUtils.colorize(message));
        }
        return true;
    }

    private String normalizeDimension(String input) {
        if (plugin.getOverrideManager() != null) {
            return plugin.getOverrideManager().normalizeDimensionKey(input);
        }
        switch (input.toLowerCase()) {
            case "minecraft:overworld":
            case "overworld":
            case "world":
                return "minecraft:overworld";
            case "minecraft:the_nether":
            case "nether":
            case "world_nether":
                return "minecraft:the_nether";
            case "minecraft:the_end":
            case "end":
            case "the_end":
            case "world_the_end":
                return "minecraft:the_end";
            default:
                return input;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {

            List<String> suggestions = new ArrayList<>(List.of(
                    "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end", "*"));

            for (var world : Bukkit.getWorlds()) {
                String name = world.getName().toLowerCase();
                if (!name.equals("world") && !name.equals("world_nether") && !name.equals("world_the_end")) {
                    suggestions.add(name);
                    suggestions.add("minecraft:" + name);
                }
            }
            for (String s : suggestions) {
                if (s.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(s);
                }
            }
        } else if (args.length == 2) {

            if ("on".startsWith(args[1].toLowerCase())) {
                completions.add("on");
            }
            if ("off".startsWith(args[1].toLowerCase())) {
                completions.add("off");
            }
        } else if (args.length == 3) {
            if ("--force".startsWith(args[2].toLowerCase())) {
                completions.add("--force");
            }
        }

        if (completions.isEmpty()) {
            return Collections.emptyList();
        }
        return completions;
    }
}
