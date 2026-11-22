package de.Z7534.ztone.playrewards.command;

import de.Z7534.ztone.playrewards.PlayReward;
import de.Z7534.ztone.playrewards.menu.MainRewardMenu;
import de.Z7534.ztone.playrewards.util.MessageUtil;
import de.Z7534.ztone.playrewards.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PlayRewardsCommand implements CommandExecutor {

    private final PlayReward plugin;

    public PlayRewardsCommand(PlayReward plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                MessageUtil.sendMessage(sender, plugin.getPrefix(), "<red>Dieser Befehl kann nur von einem Spieler im Spiel ausgeführt werden!");
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("playrewards.use")) {
                MessageUtil.sendMessage(player, plugin.getPrefix(), "<red>Du hast keine Berechtigung für diesen Befehl!");
                return true;
            }

            // Öffnet das 3er-Haupt-Menü (synchronisiert zuvor asynchron Spielzeit über den konfigurierten Placeholder)
            plugin.getPlaytimeService().getActivePlaytimeSeconds(player.getUniqueId()).thenAccept(sec -> {
                Bukkit.getScheduler().runTask(plugin.getPlugin(), () -> {
                    if (player.isOnline()) {
                        new MainRewardMenu(plugin, player).open();
                        plugin.getRewardClaimService().playClickSound(player);
                    }
                });
            });
            return true;
        }

        String subCommand = args[0].toLowerCase();

        // Admin-Befehl: /playrewards reload
        if (subCommand.equals("reload")) {
            if (!sender.hasPermission("playrewards.admin")) {
                MessageUtil.sendMessage(sender, plugin.getPrefix(), "<red>Keine Rechte für diesen Befehl!");
                return true;
            }
            plugin.getConfigManager().reloadAll();
            MessageUtil.sendMessage(sender, plugin.getPrefix(), "<green>Alle Konfigurationsdateien wurden erfolgreich neu geladen!");
            return true;
        }

        // Admin-Befehl: /playrewards sync [spieler]
        if (subCommand.equals("sync")) {
            if (!sender.hasPermission("playrewards.admin")) {
                MessageUtil.sendMessage(sender, plugin.getPrefix(), "<red>Keine Rechte für diesen Befehl!");
                return true;
            }
            Player target = (sender instanceof Player p && args.length < 2) ? p : (args.length >= 2 ? Bukkit.getPlayer(args[1]) : null);
            if (target == null) {
                MessageUtil.sendMessage(sender, plugin.getPrefix(), "<red>Spieler nicht gefunden oder offline!");
                return true;
            }

            String totalPh = plugin.getConfig().getString("playtime_tracking.total_playtime.placeholder", "%stonesmpcore_playtime_seconds%");
            String dailyPh = plugin.getConfig().getString("playtime_tracking.daily_playtime.placeholder", "%stonesmpcore_playtime_today_seconds%");
            MessageUtil.sendMessage(sender, plugin.getPrefix(), "<yellow>Frage Spielzeit über Placeholder für <white>" + target.getName() + "</white> ab...");

            plugin.getPlaytimeService().getActivePlaytimeSeconds(target.getUniqueId()).thenAccept(seconds -> {
                long dailySeconds = plugin.getPlaytimeService().getDailyPlaytimeSeconds(target.getUniqueId());
                long days = seconds / 86400;
                long hours = (seconds % 86400) / 3600;
                long minutes = (seconds % 3600) / 60;
                long secs = seconds % 60;

                String formattedDaily = TimeUtil.formatHoursAndMinutes(dailySeconds);

                MessageUtil.sendMessage(sender, plugin.getPrefix(), "<green>Erfolg! Spielzeit über Placeholder synchronisiert:");
                MessageUtil.sendMessage(sender, plugin.getPrefix(), " <gray>Gesamt-Spielzeit (" + totalPh + "): <gold>" +
                        days + " Tage, " + hours + " Stunden, " + minutes + " Minuten, " + secs + "s (" + seconds + "s)</gold>");
                MessageUtil.sendMessage(sender, plugin.getPrefix(), " <gray>Heutige Spielzeit (" + dailyPh + "): <yellow>" +
                        formattedDaily + " (" + dailySeconds + "s)</yellow>");
            });
            return true;
        }

        // Admin-Befehl: /playrewards setplaytime <spieler> <sekunden/stunden>
        if (subCommand.equals("setplaytime")) {
            if (!sender.hasPermission("playrewards.admin")) {
                MessageUtil.sendMessage(sender, plugin.getPrefix(), "<red>Keine Rechte für diesen Befehl!");
                return true;
            }
            if (args.length < 3) {
                MessageUtil.sendMessage(sender, plugin.getPrefix(), "<yellow>Verwendung: /" + label + " setplaytime <Spieler> <Sekunden|Zahl+h|Zahl+d>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                MessageUtil.sendMessage(sender, plugin.getPrefix(), "<red>Spieler '" + args[1] + "' ist nicht online!");
                return true;
            }
            String rawVal = args[2].toLowerCase();
            long sec;
            try {
                if (rawVal.endsWith("d")) {
                    sec = Long.parseLong(rawVal.substring(0, rawVal.length() - 1)) * 86400L;
                } else if (rawVal.endsWith("h")) {
                    sec = Long.parseLong(rawVal.substring(0, rawVal.length() - 1)) * 3600L;
                } else if (rawVal.endsWith("m")) {
                    sec = Long.parseLong(rawVal.substring(0, rawVal.length() - 1)) * 60L;
                } else if (rawVal.endsWith("s")) {
                    sec = Long.parseLong(rawVal.substring(0, rawVal.length() - 1));
                } else {
                    sec = Long.parseLong(rawVal);
                }
            } catch (NumberFormatException e) {
                MessageUtil.sendMessage(sender, plugin.getPrefix(), "<red>Ungültige Zeitangabe! Beispiel: 1000s, 5h, 3d");
                return true;
            }

            plugin.getPlaytimeService().setPlaytimeSeconds(target.getUniqueId(), sec);
            long days = sec / 86400;
            long hours = (sec % 86400) / 3600;
            long mins = (sec % 3600) / 60;
            MessageUtil.sendMessage(sender, plugin.getPrefix(), "<green>Spielzeit für <yellow>" + target.getName() + "</yellow> erfolgreich gesetzt: <gold>" + days + " Tage, " + hours + "h, " + mins + "m (" + sec + "s)</gold>");
            return true;
        }

        // Admin-Befehl: /playrewards resetstreak <spieler>
        if (subCommand.equals("resetstreak")) {
            if (!sender.hasPermission("playrewards.admin")) {
                MessageUtil.sendMessage(sender, plugin.getPrefix(), "<red>Keine Rechte für diesen Befehl!");
                return true;
            }
            if (args.length < 2) {
                MessageUtil.sendMessage(sender, plugin.getPrefix(), "<yellow>Verwendung: /" + label + " resetstreak <Spieler>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                MessageUtil.sendMessage(sender, plugin.getPrefix(), "<red>Der Spieler '" + args[1] + "' ist aktuell nicht online!");
                return true;
            }
            boolean success = plugin.getStreakService().resetStreak(target.getUniqueId());
            if (success) {
                MessageUtil.sendMessage(sender, plugin.getPrefix(), "<green>Die Streak von <yellow>" + target.getName() + "</yellow> wurde erfolgreich zurückgesetzt.");
                MessageUtil.sendMessage(target, plugin.getPrefix(), "<red>Deine Belohnungs-Streak wurde von einem Administrator zurückgesetzt.");
            } else {
                MessageUtil.sendMessage(sender, plugin.getPrefix(), "<red>Fehler beim Zurücksetzen der Streak.");
            }
            return true;
        }

        MessageUtil.sendMessage(sender, plugin.getPrefix(), "<yellow>Verwendung: /" + label + " [reload | sync [Spieler] | setplaytime <Spieler> <Zeit> | resetstreak <Spieler>]");
        return true;
    }
}
