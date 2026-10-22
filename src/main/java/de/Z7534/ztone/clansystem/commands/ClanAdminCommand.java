package de.Z7534.ztone.clansystem.commands;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.gui.ClanChestGUI;
import de.Z7534.ztone.clansystem.gui.ClanSettingsGUI;
import de.Z7534.ztone.clansystem.managers.MessageManager;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanMember;
import de.Z7534.ztone.clansystem.models.ClanRank;
import de.Z7534.ztone.clansystem.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class ClanAdminCommand implements CommandExecutor, TabCompleter {

    private final Ztone plugin;

    public ClanAdminCommand(Ztone plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("clansystem.admin")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload" -> handleReload(sender);
            case "delete" -> handleDelete(sender, args);
            case "setlevel" -> handleSetLevel(sender, args);
            case "setleader" -> handleSetLeader(sender, args);
            case "chest" -> handleChest(sender, args);
            case "addpoints" -> handleAddPoints(sender, args);
            case "info" -> handleInfo(sender, args);
            case "forcejoin" -> handleForceJoin(sender, args);
            case "forceleave" -> handleForceLeave(sender, args);
            case "list" -> handleList(sender);
            case "settings" -> handleSettings(sender, args);
            case "bold" -> handleBold(sender, args);
            case "home" -> handleHome(sender, args);
            case "warp" -> handleWarp(sender, args);
            case "promote" -> handlePromote(sender, args);
            case "demote" -> handleDemote(sender, args);
            case "help", "?" -> sendHelp(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("clansystem.admin.reload")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return;
        }

        plugin.reload();
        plugin.getMessageManager().send(sender, "general.reload-success");
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("clansystem.admin.delete")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return;
        }

        if (args.length < 2) {
            plugin.getMessageManager().send(sender, "general.invalid-usage",
                    MessageManager.of("usage", "/clanadmin delete <clanname>"));
            return;
        }

        Clan clan = plugin.getClanManager().getClan(args[1]);
        if (clan == null) {
            plugin.getMessageManager().send(sender, "clan.not-found",
                    MessageManager.of("clan", args[1]));
            return;
        }

        String clanName = clan.getColoredName();

        if (plugin.getConfigManager().isBroadcastClanDisbanded()) {
            plugin.getMessageManager().broadcast("broadcast.clan-disbanded",
                    MessageManager.of("clan", clanName));
        }

        plugin.getClanManager().disbandClan(clan);
        plugin.getMessageManager().send(sender, "admin.clan-deleted",
                MessageManager.of("clan", clanName));
    }

    private void handleSetLevel(CommandSender sender, String[] args) {
        if (!sender.hasPermission("clansystem.admin.setlevel")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return;
        }

        if (args.length < 3) {
            plugin.getMessageManager().send(sender, "general.invalid-usage",
                    MessageManager.of("usage", "/clanadmin setlevel <clanname> <level>"));
            return;
        }

        Clan clan = plugin.getClanManager().getClan(args[1]);
        if (clan == null) {
            plugin.getMessageManager().send(sender, "clan.not-found",
                    MessageManager.of("clan", args[1]));
            return;
        }

        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            plugin.getMessageManager().send(sender, "general.invalid-usage",
                    MessageManager.of("usage", "/clanadmin setlevel <clanname> <level>"));
            return;
        }

        if (level < 1 || level > plugin.getLevelManager().getMaxLevel()) {
            plugin.getMessageManager().send(sender, "general.invalid-usage",
                    MessageManager.of("usage", "/clanadmin setlevel <clanname> <1-" + plugin.getLevelManager().getMaxLevel() + ">"));
            return;
        }

        clan.setLevel(level);

        int newChestSize = plugin.getLevelManager().getChestSize(level);
        if (clan.getChestSize() < newChestSize) {
            clan.resizeChest(newChestSize);
        }

        plugin.getClanManager().updateClanInDatabase(clan);

        plugin.getMessageManager().send(sender, "admin.level-set",
                MessageManager.of("clan", clan.getColoredName(), "level", String.valueOf(level)));
    }

    private void handleSetLeader(CommandSender sender, String[] args) {
        if (!sender.hasPermission("clansystem.admin.setleader")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return;
        }

        if (args.length < 3) {
            plugin.getMessageManager().send(sender, "general.invalid-usage",
                    MessageManager.of("usage", "/clanadmin setleader <clanname> <spieler>"));
            return;
        }

        Clan clan = plugin.getClanManager().getClan(args[1]);
        if (clan == null) {
            plugin.getMessageManager().send(sender, "clan.not-found",
                    MessageManager.of("clan", args[1]));
            return;
        }

        Player target = Bukkit.getPlayer(args[2]);
        UUID targetUuid;
        String targetName;

        if (target != null) {
            targetUuid = target.getUniqueId();
            targetName = target.getName();
        } else {
            targetUuid = Bukkit.getOfflinePlayer(args[2]).getUniqueId();
            targetName = args[2];
        }

        ClanMember member = clan.getMember(targetUuid);
        if (member == null) {
            plugin.getMessageManager().send(sender, "clan.target-not-in-your-clan",
                    MessageManager.of("player", targetName));
            return;
        }

        boolean success = plugin.getClanManager().transferLeadership(clan, targetUuid);
        if (!success) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return;
        }

        plugin.getMessageManager().send(sender, "admin.leader-set",
                MessageManager.of("player", targetName, "clan", clan.getColoredName()));

        if (target != null) {
            plugin.getMessageManager().send(target, "rank.rank-changed",
                    MessageManager.of("rank", "Leader"));
        }
    }

    private void handleChest(CommandSender sender, String[] args) {
        if (!sender.hasPermission("clansystem.admin.chest")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return;
        }

        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().sendRaw(sender, "general.player-only");
            return;
        }

        if (args.length < 2) {
            plugin.getMessageManager().send(sender, "general.invalid-usage",
                    MessageManager.of("usage", "/clanadmin chest <clanname>"));
            return;
        }

        Clan clan = plugin.getClanManager().getClan(args[1]);
        if (clan == null) {
            plugin.getMessageManager().send(sender, "clan.not-found",
                    MessageManager.of("clan", args[1]));
            return;
        }

        new ClanChestGUI(plugin, player, clan, true).open();
        plugin.getMessageManager().send(player, "admin.chest-opened",
                MessageManager.of("clan", clan.getColoredName()));
    }

    private void handleAddPoints(CommandSender sender, String[] args) {
        if (!sender.hasPermission("clansystem.admin.addpoints")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return;
        }

        if (args.length < 3) {
            plugin.getMessageManager().send(sender, "general.invalid-usage",
                    MessageManager.of("usage", "/clanadmin addpoints <clanname> <punkte>"));
            return;
        }

        Clan clan = plugin.getClanManager().getClan(args[1]);
        if (clan == null) {
            plugin.getMessageManager().send(sender, "clan.not-found",
                    MessageManager.of("clan", args[1]));
            return;
        }

        int points;
        try {
            points = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            plugin.getMessageManager().send(sender, "general.invalid-usage",
                    MessageManager.of("usage", "/clanadmin addpoints <clanname> <punkte>"));
            return;
        }

        plugin.getClanManager().addPointsToClan(clan, points);

        plugin.getMessageManager().send(sender, "admin.points-added",
                MessageManager.of("points", String.valueOf(points), "clan", clan.getColoredName()));
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("clansystem.admin.info")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return;
        }

        if (args.length < 3) {
            plugin.getMessageManager().send(sender, "general.invalid-usage",
                    MessageManager.of("usage", "/clanadmin info <clan|player> <clanname|spieler>"));
            return;
        }

        String mode = args[1].toLowerCase();
        Clan clan;

        if (mode.equals("clan")) {
            clan = plugin.getClanManager().getClan(args[2]);
            if (clan == null) {
                plugin.getMessageManager().send(sender, "clan.not-found",
                        MessageManager.of("clan", args[2]));
                return;
            }
        } else if (mode.equals("player")) {
            Player target = Bukkit.getPlayer(args[2]);
            UUID targetUuid = target != null ? target.getUniqueId() : Bukkit.getOfflinePlayer(args[2]).getUniqueId();
            String targetName = target != null ? target.getName() : args[2];

            clan = plugin.getClanManager().getClanByPlayer(targetUuid);
            if (clan == null) {
                plugin.getMessageManager().send(sender, "clan.target-not-in-clan",
                        MessageManager.of("player", targetName));
                return;
            }
        } else {
            plugin.getMessageManager().send(sender, "general.invalid-usage",
                    MessageManager.of("usage", "/clanadmin info <clan|player> <clanname|spieler>"));
            return;
        }

        sendClanInfo(sender, clan);
    }

    private void sendClanInfo(CommandSender sender, Clan clan) {
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&8&m----------&8[ &6Clan-Info: " + clan.getColoredName() + " &8]&m----------"));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&7ID: &e" + clan.getId()));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&7Name: " + clan.getColoredName()));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&7Suffix: &r" + (clan.getSuffix().isEmpty() ? "&8Keiner" : clan.getColoredSuffix())));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&7Level: &e" + clan.getLevel()));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&7Punkte: &e" + clan.getPoints()));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&7Mitglieder: &e" + clan.getMemberCount()));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&7Online: &a" + clan.getOnlineMembers().size()));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&7Beitrittsart: &e" + clan.getJoinType().name()));

        ClanMember leader = clan.getMember(clan.getLeaderUuid());
        String leaderName = leader != null ? leader.getName() : "Unbekannt";
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&7Leader: &e" + leaderName));

        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&7Allianzen: &e" + clan.getAllies().size()));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&7Aktive Kriege: &c" + clan.getActiveWars().size()));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&7Homes: &e" + clan.getHomeCount()));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&7Warps: &e" + clan.getWarpCount()));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&7Ränge: &e" + clan.getRanks().size()));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&8&m-----------------------------------------"));
    }

    private void handleForceJoin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("clansystem.admin.bypass")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return;
        }

        if (args.length < 3) {
            plugin.getMessageManager().send(sender, "general.invalid-usage",
                    MessageManager.of("usage", "/clanadmin forcejoin <spieler> <clanname>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            plugin.getMessageManager().send(sender, "general.player-not-found",
                    MessageManager.of("player", args[1]));
            return;
        }

        if (plugin.getClanManager().isInClan(target)) {
            plugin.getMessageManager().send(sender, "clan.target-already-in-clan");
            return;
        }

        Clan clan = plugin.getClanManager().getClan(args[2]);
        if (clan == null) {
            plugin.getMessageManager().send(sender, "clan.not-found",
                    MessageManager.of("clan", args[2]));
            return;
        }

        ClanRank lowestRank = clan.getLowestRank();
        plugin.getClanManager().addMember(clan, target, lowestRank);

        plugin.getMessageManager().send(target, "clan.joined",
                MessageManager.of("clan", clan.getColoredName()));

        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize(
                "&a" + target.getName() + " wurde dem Clan " + clan.getColoredName() + " hinzugefügt."));
    }

    private void handleForceLeave(CommandSender sender, String[] args) {
        if (!sender.hasPermission("clansystem.admin.bypass")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return;
        }

        if (args.length < 2) {
            plugin.getMessageManager().send(sender, "general.invalid-usage",
                    MessageManager.of("usage", "/clanadmin forceleave <spieler>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        UUID targetUuid;
        String targetName;

        if (target != null) {
            targetUuid = target.getUniqueId();
            targetName = target.getName();
        } else {
            targetUuid = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
            targetName = args[1];
        }

        Clan clan = plugin.getClanManager().getClanByPlayer(targetUuid);
        if (clan == null) {
            plugin.getMessageManager().send(sender, "clan.not-in-clan");
            return;
        }

        String clanName = clan.getColoredName();
        plugin.getClanManager().removeMember(clan, targetUuid);

        if (target != null) {
            plugin.getMessageManager().send(target, "clan.left",
                    MessageManager.of("clan", clanName));
        }

        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize(
                "&a" + targetName + " wurde aus dem Clan " + clanName + " entfernt."));
    }

    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("clansystem.admin.list")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return;
        }

        Collection<Clan> clans = plugin.getClanManager().getAllClans();

        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&8&m----------&8[ &6Alle Clans (" + clans.size() + ") &8]&m----------"));

        for (Clan clan : clans) {
            sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize(
                    "&8- " + clan.getColoredName() + " &8| &7Level: &e" + clan.getLevel() +
                            " &8| &7Mitglieder: &e" + clan.getMemberCount() +
                            " &8| &7Punkte: &e" + clan.getPoints()));
        }

        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&8&m-----------------------------------------"));
    }

    private void handleSettings(CommandSender sender, String[] args) {
        if (!sender.hasPermission("clansystem.admin.settings")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return;
        }

        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().sendRaw(sender, "general.player-only");
            return;
        }

        if (args.length < 2) {
            plugin.getMessageManager().send(sender, "general.invalid-usage",
                    MessageManager.of("usage", "/clanadmin settings <clanname>"));
            return;
        }

        Clan clan = plugin.getClanManager().getClan(args[1]);
        if (clan == null) {
            plugin.getMessageManager().send(sender, "clan.not-found",
                    MessageManager.of("clan", args[1]));
            return;
        }

        new ClanSettingsGUI(plugin, player, clan, true).open();
        plugin.getMessageManager().send(player, "admin.settings-opened",
                MessageManager.of("clan", clan.getColoredName()));
    }

    private void handleBold(CommandSender sender, String[] args) {
        if (!sender.hasPermission("clansystem.admin.bold")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return;
        }

        if (args.length < 3) {
            plugin.getMessageManager().send(sender, "general.invalid-usage",
                    MessageManager.of("usage", "/clanadmin bold <clanname> <true|false>"));
            return;
        }

        Clan clan = plugin.getClanManager().getClan(args[1]);
        if (clan == null) {
            plugin.getMessageManager().send(sender, "clan.not-found",
                    MessageManager.of("clan", args[1]));
            return;
        }

        boolean bold;
        if (args[2].equalsIgnoreCase("true")) {
            bold = true;
        } else if (args[2].equalsIgnoreCase("false")) {
            bold = false;
        } else {
            plugin.getMessageManager().send(sender, "general.invalid-usage",
                    MessageManager.of("usage", "/clanadmin bold <clanname> <true|false>"));
            return;
        }

        clan.setSuffix(ColorUtils.setBold(clan.getSuffix(), bold));
        plugin.getClanManager().updateClanInDatabase(clan);

        String suffixDisplay = clan.getSuffix().isEmpty() ? "&8Keiner" : clan.getColoredSuffix();

        plugin.getMessageManager().send(sender, bold ? "admin.bold-enabled" : "admin.bold-disabled",
                MessageManager.of("clan", clan.getColoredName(), "suffix", suffixDisplay));
    }

    private void handleHome(CommandSender sender, String[] args) {
        if (!sender.hasPermission("clansystem.admin.home")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return;
        }

        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().sendRaw(sender, "general.player-only");
            return;
        }

        if (args.length < 2) {
            plugin.getMessageManager().send(sender, "general.invalid-usage",
                    MessageManager.of("usage", "/clanadmin home <clanname>"));
            return;
        }

        Clan clan = plugin.getClanManager().getClan(args[1]);
        if (clan == null) {
            plugin.getMessageManager().send(sender, "clan.not-found",
                    MessageManager.of("clan", args[1]));
            return;
        }

        if (clan.getHomes().isEmpty()) {
            plugin.getMessageManager().send(sender, "admin.home-not-found",
                    MessageManager.of("clan", clan.getColoredName()));
            return;
        }

        de.Z7534.ztone.clansystem.models.ClanHome home = clan.getHome("home");
        if (home == null) {
            home = clan.getHomes().values().iterator().next();
        }

        org.bukkit.Location location = home.getLocation();
        if (location == null) {
            plugin.getMessageManager().send(sender, "admin.home-not-found",
                    MessageManager.of("clan", clan.getColoredName()));
            return;
        }

        player.teleport(location);
        plugin.getMessageManager().send(player, "admin.home-teleported",
                MessageManager.of("clan", clan.getColoredName(), "name", home.getName()));
    }

    private void handleWarp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("clansystem.admin.warp")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return;
        }

        if (args.length < 3) {
            plugin.getMessageManager().send(sender, "general.invalid-usage",
                    MessageManager.of("usage", "/clanadmin warp <clanname> <list|warpname>"));
            return;
        }

        Clan clan = plugin.getClanManager().getClan(args[1]);
        if (clan == null) {
            plugin.getMessageManager().send(sender, "clan.not-found",
                    MessageManager.of("clan", args[1]));
            return;
        }

        String target = args[2];

        if (target.equalsIgnoreCase("list")) {
            Collection<de.Z7534.ztone.clansystem.models.ClanWarp> warps = clan.getWarps().values();

            sender.sendMessage(ColorUtils.colorize("&8&m----------&8[ &6Warps: " + clan.getColoredName() + " &8]&m----------"));
            if (warps.isEmpty()) {
                sender.sendMessage(ColorUtils.colorize("&7Dieser Clan hat keine Warps gesetzt."));
            } else {
                for (de.Z7534.ztone.clansystem.models.ClanWarp warp : warps) {
                    sender.sendMessage(ColorUtils.colorize("&8- &d" + warp.getName()
                            + " &8| &7Welt: &e" + warp.getWorldName()
                            + " &8| &7Für Allianzen: " + (warp.isVisibleToAllies() ? "&aJa" : "&cNein")));
                }
            }
            sender.sendMessage(ColorUtils.colorize("&8&m-----------------------------------------"));
            return;
        }

        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().sendRaw(sender, "general.player-only");
            return;
        }

        de.Z7534.ztone.clansystem.models.ClanWarp warp = clan.getWarp(target);
        if (warp == null) {
            plugin.getMessageManager().send(sender, "admin.warp-not-found",
                    MessageManager.of("clan", clan.getColoredName(), "name", target));
            return;
        }

        org.bukkit.Location location = warp.getLocation();
        if (location == null) {
            plugin.getMessageManager().send(sender, "admin.warp-not-found",
                    MessageManager.of("clan", clan.getColoredName(), "name", target));
            return;
        }

        player.teleport(location);
        plugin.getMessageManager().send(player, "admin.warp-teleported",
                MessageManager.of("clan", clan.getColoredName(), "name", warp.getName()));
    }

    private void handlePromote(CommandSender sender, String[] args) {
        if (!sender.hasPermission("clansystem.admin.promote") && !sender.hasPermission("clansystem.admin")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return;
        }

        if (args.length < 2) {
            plugin.getMessageManager().send(sender, "general.invalid-usage",
                    MessageManager.of("usage", "/clanadmin promote <spieler> [clan]"));
            return;
        }

        String targetName = args[1];
        Player targetPlayer = Bukkit.getPlayer(targetName);
        UUID targetUuid = targetPlayer != null ? targetPlayer.getUniqueId() : Bukkit.getOfflinePlayer(targetName).getUniqueId();
        String resolvedName = targetPlayer != null ? targetPlayer.getName() : targetName;

        Clan clan;
        if (args.length >= 3) {
            clan = plugin.getClanManager().getClan(args[2]);
            if (clan == null) {
                plugin.getMessageManager().send(sender, "clan.not-found",
                        MessageManager.of("clan", args[2]));
                return;
            }
        } else {
            clan = plugin.getClanManager().getClanByPlayer(targetUuid);
            if (clan == null) {
                plugin.getMessageManager().send(sender, "clan.target-not-in-clan",
                        MessageManager.of("player", resolvedName));
                return;
            }
        }

        ClanMember targetMember = clan.getMember(targetUuid);
        if (targetMember == null) {
            plugin.getMessageManager().send(sender, "clan.target-not-in-your-clan",
                    MessageManager.of("player", resolvedName));
            return;
        }

        if (clan.isLeader(targetUuid) || targetMember.isLeader()) {
            sender.sendMessage(ColorUtils.colorize("&8[&6Clan-Admin&8] &cDer Clan-Leader hat bereits die höchste Stellung und kann nicht befördert werden!"));
            return;
        }

        List<ClanRank> sortedRanks = clan.getSortedRanks();
        ClanRank currentRank = targetMember.getRank();
        ClanRank nextRank = null;

        for (int i = 0; i < sortedRanks.size() - 1; i++) {
            if (sortedRanks.get(i + 1).equals(currentRank)) {
                nextRank = sortedRanks.get(i);
                break;
            }
        }

        if (nextRank == null) {
            sender.sendMessage(ColorUtils.colorize("&8[&6Clan-Admin&8] &c" + resolvedName + " hat bereits den höchsten Rang (" + (currentRank != null ? currentRank.getName() : "Co. Owner") + ")!"));
            return;
        }

        targetMember.setRank(nextRank);
        plugin.getDatabaseManager().updateMember(targetMember);

        sender.sendMessage(ColorUtils.colorize("&8[&6Clan-Admin&8] &a" + resolvedName + " im Clan &e" + clan.getName() + " &awurde auf den Rang &e" + nextRank.getName() + " &abefördert."));

        if (targetPlayer != null) {
            plugin.getMessageManager().send(targetPlayer, "rank.rank-changed",
                    MessageManager.of("rank", nextRank.getName()));
        }
    }

    private void handleDemote(CommandSender sender, String[] args) {
        if (!sender.hasPermission("clansystem.admin.demote") && !sender.hasPermission("clansystem.admin")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return;
        }

        if (args.length < 2) {
            plugin.getMessageManager().send(sender, "general.invalid-usage",
                    MessageManager.of("usage", "/clanadmin demote <spieler> [clan]"));
            return;
        }

        String targetName = args[1];
        Player targetPlayer = Bukkit.getPlayer(targetName);
        UUID targetUuid = targetPlayer != null ? targetPlayer.getUniqueId() : Bukkit.getOfflinePlayer(targetName).getUniqueId();
        String resolvedName = targetPlayer != null ? targetPlayer.getName() : targetName;

        Clan clan;
        if (args.length >= 3) {
            clan = plugin.getClanManager().getClan(args[2]);
            if (clan == null) {
                plugin.getMessageManager().send(sender, "clan.not-found",
                        MessageManager.of("clan", args[2]));
                return;
            }
        } else {
            clan = plugin.getClanManager().getClanByPlayer(targetUuid);
            if (clan == null) {
                plugin.getMessageManager().send(sender, "clan.target-not-in-clan",
                        MessageManager.of("player", resolvedName));
                return;
            }
        }

        ClanMember targetMember = clan.getMember(targetUuid);
        if (targetMember == null) {
            plugin.getMessageManager().send(sender, "clan.target-not-in-your-clan",
                    MessageManager.of("player", resolvedName));
            return;
        }

        if (clan.isLeader(targetUuid) || targetMember.isLeader()) {
            sender.sendMessage(ColorUtils.colorize("&8[&6Clan-Admin&8] &cDer Clan-Leader kann nicht degradiert werden! Nutze /clanadmin setleader <clan> <spieler>."));
            return;
        }

        List<ClanRank> sortedRanks = clan.getSortedRanks();
        ClanRank currentRank = targetMember.getRank();
        ClanRank prevRank = null;

        for (int i = 1; i < sortedRanks.size(); i++) {
            if (sortedRanks.get(i - 1).equals(currentRank)) {
                prevRank = sortedRanks.get(i);
                break;
            }
        }

        if (prevRank == null) {
            sender.sendMessage(ColorUtils.colorize("&8[&6Clan-Admin&8] &c" + resolvedName + " hat bereits den niedrigsten Rang (" + (currentRank != null ? currentRank.getName() : "Member") + ")!"));
            return;
        }

        targetMember.setRank(prevRank);
        plugin.getDatabaseManager().updateMember(targetMember);

        sender.sendMessage(ColorUtils.colorize("&8[&6Clan-Admin&8] &a" + resolvedName + " im Clan &e" + clan.getName() + " &awurde auf den Rang &e" + prevRank.getName() + " &adegradiert."));

        if (targetPlayer != null) {
            plugin.getMessageManager().send(targetPlayer, "rank.rank-changed",
                    MessageManager.of("rank", prevRank.getName()));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&8&m----------&8[ &6Clan-Admin &8]&m----------"));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clanadmin reload &8- &7Konfiguration neu laden"));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clanadmin delete <clan> &8- &7Clan löschen"));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clanadmin setlevel <clan> <level> &8- &7Level setzen"));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clanadmin setleader <clan> <spieler> &8- &7Leader setzen"));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clanadmin chest <clan> &8- &7Truhe öffnen"));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clanadmin addpoints <clan> <punkte> &8- &7Punkte hinzufügen"));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clanadmin info <clan|player> <clan|spieler> &8- &7Clan-Info anzeigen"));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clanadmin forcejoin <spieler> <clan> &8- &7Spieler in Clan zwingen"));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clanadmin forceleave <spieler> &8- &7Spieler aus Clan entfernen"));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clanadmin list &8- &7Alle Clans auflisten"));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clanadmin settings <clan> &8- &7Clan-Einstellungen öffnen"));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clanadmin bold <clan> <true|false> &8- &7Suffix fett schalten"));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clanadmin home <clan> &8- &7Zum Home des Clans teleportieren"));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clanadmin warp <clan> <list|warp> &8- &7Zu einem Clan-Warp teleportieren"));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clanadmin promote <spieler> [clan] &8- &7Spieler im Clan befördern"));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clanadmin demote <spieler> [clan] &8- &7Spieler im Clan degradieren"));
        sender.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&8&m---------------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("clansystem.admin")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList(
                    "reload", "delete", "setlevel", "setleader", "chest", "addpoints",
                    "info", "forcejoin", "forceleave", "list", "settings", "bold",
                    "home", "warp", "promote", "demote", "help"
            );
            String input = args[0].toLowerCase();
            completions = subCommands.stream()
                    .filter(s -> s.startsWith(input))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            String input = args[1].toLowerCase();

            switch (subCommand) {
                case "delete", "setlevel", "setleader", "chest", "addpoints", "settings", "bold", "home", "warp" -> {
                    completions = plugin.getClanManager().getAllClans().stream()
                            .map(Clan::getName)
                            .filter(n -> n.toLowerCase().startsWith(input))
                            .collect(Collectors.toList());
                }
                case "info" -> {
                    completions = Arrays.asList("clan", "player").stream()
                            .filter(s -> s.startsWith(input))
                            .collect(Collectors.toList());
                }
                case "forcejoin", "forceleave", "promote", "demote" -> {

                    completions = Arrays.stream(Bukkit.getOfflinePlayers())
                            .map(OfflinePlayer::getName)
                            .filter(java.util.Objects::nonNull)
                            .filter(n -> n.toLowerCase().startsWith(input))
                            .collect(Collectors.toList());
                }
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            String input = args[2].toLowerCase();

            switch (subCommand) {
                case "setlevel" -> {
                    for (int i = 1; i <= plugin.getLevelManager().getMaxLevel(); i++) {
                        String level = String.valueOf(i);
                        if (level.startsWith(input)) {
                            completions.add(level);
                        }
                    }
                }
                case "setleader" -> {
                    Clan clan = plugin.getClanManager().getClan(args[1]);
                    if (clan != null) {
                        completions = clan.getMembers().values().stream()
                                .map(ClanMember::getName)
                                .filter(n -> n.toLowerCase().startsWith(input))
                                .collect(Collectors.toList());
                    }
                }
                case "forcejoin", "promote", "demote" -> {
                    completions = plugin.getClanManager().getAllClans().stream()
                            .map(Clan::getName)
                            .filter(n -> n.toLowerCase().startsWith(input))
                            .collect(Collectors.toList());
                }
                case "bold" -> {
                    completions = Arrays.asList("true", "false").stream()
                            .filter(s -> s.startsWith(input))
                            .collect(Collectors.toList());
                }
                case "info" -> {
                    String mode = args[1].toLowerCase();
                    if (mode.equals("clan")) {
                        completions = plugin.getClanManager().getAllClans().stream()
                                .map(Clan::getName)
                                .filter(n -> n.toLowerCase().startsWith(input))
                                .collect(Collectors.toList());
                    } else if (mode.equals("player")) {
                        completions = Arrays.stream(Bukkit.getOfflinePlayers())
                                .map(OfflinePlayer::getName)
                                .filter(java.util.Objects::nonNull)
                                .filter(n -> n.toLowerCase().startsWith(input))
                                .collect(Collectors.toList());
                    }
                }
                case "warp" -> {
                    Clan clan = plugin.getClanManager().getClan(args[1]);
                    List<String> options = new ArrayList<>();
                    options.add("list");
                    if (clan != null) {
                        options.addAll(clan.getWarps().keySet());
                    }
                    completions = options.stream()
                            .filter(n -> n.toLowerCase().startsWith(input))
                            .collect(Collectors.toList());
                }
            }
        }

        return completions;
    }
}
