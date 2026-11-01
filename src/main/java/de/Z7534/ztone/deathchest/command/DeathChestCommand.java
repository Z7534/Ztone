package de.Z7534.ztone.deathchest.command;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.claims.util.Text;
import de.Z7534.ztone.deathchest.config.DeathChestConfig;
import de.Z7534.ztone.deathchest.manager.DeathChestBypassManager;
import de.Z7534.ztone.deathchest.manager.DeathChestManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DeathChestCommand implements CommandExecutor, TabCompleter {

    private final Ztone plugin;
    private final DeathChestManager manager;
    private final DeathChestConfig config;
    private final DeathChestBypassManager bypassManager;

    public DeathChestCommand(Ztone plugin, DeathChestManager manager, DeathChestConfig config,
                             DeathChestBypassManager bypassManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = config;
        this.bypassManager = bypassManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "bypass" -> {
                handleBypass(sender, args);
                return true;
            }
            case "clear" -> {
                if (!sender.hasPermission("deathchest.admin")) {
                    sender.sendMessage(config.getMessage("no-permission"));
                    return true;
                }
                int count = manager.clearAll();
                sender.sendMessage(config.getMessage("all-chests-cleared", Map.of("count", String.valueOf(count))));
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("deathchest.admin")) {
                    sender.sendMessage(config.getMessage("no-permission"));
                    return true;
                }
                config.load();
                sender.sendMessage(config.getMessage("reload-success"));
                return true;
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    private void handleBypass(CommandSender sender, String[] args) {
        // /deathchest bypass [on|off] [spieler]
        if (args.length >= 3) {
            // Bypass für andere Spieler schalten
            if (!sender.hasPermission(DeathChestBypassManager.PERMISSION_OTHER)) {
                sender.sendMessage(config.getMessage("no-permission"));
                return;
            }
            String modeStr = args[1].toLowerCase(Locale.ROOT);
            Boolean enable = parseToggleState(modeStr);
            if (enable == null) {
                sender.sendMessage(config.getMessage("bypass-usage"));
                return;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(config.getMessage("player-not-found", Map.of("player", args[2])));
                return;
            }
            bypassManager.setExplicitActive(target.getUniqueId(), enable);
            sender.sendMessage(config.getMessage(enable ? "bypass-enabled-other" : "bypass-disabled-other",
                    Map.of("player", target.getName())));
            target.sendMessage(config.getMessage(enable ? "bypass-enabled" : "bypass-disabled"));
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getMessage("bypass-usage"));
            return;
        }

        if (!player.hasPermission(DeathChestBypassManager.PERMISSION)) {
            player.sendMessage(config.getMessage("no-permission"));
            return;
        }

        boolean enable;
        if (args.length == 2) {
            // /deathchest bypass <on|off>
            Boolean state = parseToggleState(args[1].toLowerCase(Locale.ROOT));
            if (state == null) {
                // Könnte auch /deathchest bypass <spieler> gemeint sein?
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null && player.hasPermission(DeathChestBypassManager.PERMISSION_OTHER)) {
                    boolean newToggled = !bypassManager.isActive(target);
                    bypassManager.setExplicitActive(target.getUniqueId(), newToggled);
                    player.sendMessage(config.getMessage(newToggled ? "bypass-enabled-other" : "bypass-disabled-other",
                            Map.of("player", target.getName())));
                    target.sendMessage(config.getMessage(newToggled ? "bypass-enabled" : "bypass-disabled"));
                    return;
                }
                player.sendMessage(config.getMessage("bypass-usage"));
                return;
            }
            enable = state;
            bypassManager.setActive(player.getUniqueId(), enable);
        } else {
            enable = bypassManager.toggle(player);
        }

        player.sendMessage(config.getMessage(enable ? "bypass-enabled" : "bypass-disabled"));
    }

    private Boolean parseToggleState(String s) {
        return switch (s) {
            case "on", "an", "true", "1", "enable" -> true;
            case "off", "aus", "false", "0", "disable" -> false;
            default -> null;
        };
    }

    private void sendHelp(CommandSender sender) {
        if (sender.hasPermission("deathchest.admin")) {
            sender.sendMessage(Text.line(config.getPrefix() + " <gray>Verwendung: <yellow>/deathchest <bypass|clear|reload></yellow></gray>"));
        } else if (sender.hasPermission(DeathChestBypassManager.PERMISSION)) {
            sender.sendMessage(Text.line(config.getPrefix() + " <gray>Verwendung: <yellow>/deathchest bypass [on|off] [spieler]</yellow></gray>"));
        } else {
            sender.sendMessage(config.getMessage("no-permission"));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            List<String> sub = new ArrayList<>();
            if (sender.hasPermission(DeathChestBypassManager.PERMISSION)) {
                sub.add("bypass");
            }
            if (sender.hasPermission("deathchest.admin")) {
                sub.add("clear");
                sub.add("reload");
            }
            for (String s : sub) {
                if (s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(s);
                }
            }
            return result;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("bypass")) {
            if (sender.hasPermission(DeathChestBypassManager.PERMISSION)) {
                for (String s : List.of("on", "off")) {
                    if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        result.add(s);
                    }
                }
            }
            if (sender.hasPermission(DeathChestBypassManager.PERMISSION_OTHER)) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        result.add(p.getName());
                    }
                }
            }
            return result;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("bypass")
                && sender.hasPermission(DeathChestBypassManager.PERMISSION_OTHER)) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    result.add(p.getName());
                }
            }
            return result;
        }

        return List.of();
    }
}
