package de.Z7534.ztone.clansystem.commands;



import de.Z7534.ztone.Ztone;

import de.Z7534.ztone.clansystem.gui.*;

import de.Z7534.ztone.clansystem.managers.ClanManager;

import de.Z7534.ztone.clansystem.managers.EconomyManager;

import de.Z7534.ztone.clansystem.managers.LevelManager;

import de.Z7534.ztone.clansystem.managers.MessageManager;

import de.Z7534.ztone.clansystem.models.*;

import org.bukkit.Bukkit;

import org.bukkit.Location;

import org.bukkit.command.Command;

import org.bukkit.command.CommandExecutor;

import org.bukkit.command.CommandSender;

import org.bukkit.command.TabCompleter;

import org.bukkit.entity.Player;



import java.util.*;

import java.util.concurrent.ConcurrentHashMap;

import java.util.stream.Collectors;



public class ClanCommand implements CommandExecutor, TabCompleter {



    private static final String DEFAULT_HOME_NAME = "home";



    private final Ztone plugin;

    private final Set<UUID> disbandConfirm = new HashSet<>();



    private final Map<UUID, Long> homeCooldowns = new ConcurrentHashMap<>();

    private final Map<UUID, Long> warpCooldowns = new ConcurrentHashMap<>();



    public ClanCommand(Ztone plugin) {

        this.plugin = plugin;

    }



    private boolean checkTeleportCooldown(Player player, Map<UUID, Long> cooldowns) {

        if (player.hasPermission("clansystem.bypass.cooldown")) {

            return true;

        }

        Long expiresAt = cooldowns.get(player.getUniqueId());

        if (expiresAt != null && expiresAt > System.currentTimeMillis()) {

            long remaining = (expiresAt - System.currentTimeMillis() + 999) / 1000;

            plugin.getMessageManager().send(player, "general.cooldown",

                    MessageManager.of("time", MessageManager.formatTime(remaining)));

            return false;

        }

        return true;

    }



    private void applyTeleportCooldown(Player player, Map<UUID, Long> cooldowns) {

        int seconds = plugin.getConfigManager().getTeleportCooldown();

        if (seconds > 0) {

            cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);

        }

    }



    @Override

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {

            plugin.getMessageManager().sendRaw(sender, "general.player-only");

            return true;

        }



        if (!player.hasPermission("clansystem.use")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return true;

        }



        if (args.length == 0) {



            Clan clan = plugin.getClanManager().getClanByPlayer(player);

            if (clan != null) {

                new ClanMainGUI(plugin, player, clan).open();

            } else {

                new NoClanGUI(plugin, player).open();

            }

            return true;

        }



        String subCommand = args[0].toLowerCase();



        switch (subCommand) {

            case "create" -> handleCreate(player, args);

            case "disband" -> handleDisband(player);

            case "leave" -> handleLeave(player);

            case "kick" -> handleKick(player, args);

            case "invite" -> handleInvite(player, args);

            case "accept" -> handleAccept(player);

            case "apply" -> handleApply(player, args);

            case "join" -> handleJoin(player, args);

            case "sethome" -> handleSetHome(player, args);

            case "home" -> handleHome(player, args);

            case "delhome" -> handleDelHome(player, args);

            case "homes" -> handleHomes(player);

            case "setwarp" -> handleSetWarp(player, args);

            case "warp" -> handleWarp(player, args);

            case "delwarp" -> handleDelWarp(player, args);

            case "warps" -> handleWarps(player);

            case "chest" -> handleChest(player);

            case "ally" -> handleAlly(player, args);

            case "rename" -> handleRename(player, args);

            case "war" -> handleWar(player, args);

            case "rank" -> handleRank(player, args);

            case "settings" -> handleSettings(player);

            case "list" -> handleList(player);

            case "top" -> {

                if (!plugin.getConfigManager().isClankasseEnabled()) {

                    plugin.getMessageManager().send(player, "bank.not-available");

                    return true;

                }

                handleTop(player);

            }

            case "deposit", "einzahlen" -> {

                if (!plugin.getConfigManager().isClankasseEnabled()) {

                    plugin.getMessageManager().send(player, "bank.not-available");

                    return true;

                }

                handleDeposit(player, args);

            }

            case "withdraw", "auszahlen" -> {

                if (!plugin.getConfigManager().isClankasseEnabled()) {

                    plugin.getMessageManager().send(player, "bank.not-available");

                    return true;

                }

                handleWithdraw(player, args);

            }

            case "bank", "kasse" -> {

                if (!plugin.getConfigManager().isClankasseEnabled()) {

                    plugin.getMessageManager().send(player, "bank.not-available");

                    return true;

                }

                handleBank(player, args);

            }

            case "info" -> handleInfo(player, args);

            case "chat", "c" -> handleChat(player, args);

            case "ac" -> handleAllyChat(player, args);

            case "suffix" -> handleSuffix(player, args);

            case "promote" -> handlePromote(player, args);

            case "transfer" -> handleTransfer(player, args);

            case "demote" -> handleDemote(player, args);

            case "help", "?" -> sendHelp(player);

            default -> sendHelp(player);

        }



        return true;

    }



    private void handleRename(Player player, String[] args) {

        if (!player.hasPermission("clansystem.rename") && !player.hasPermission("clansystem.use")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        ClanMember member = clan.getMember(player.getUniqueId());

        if (!clan.isLeader(player.getUniqueId()) && (member == null || !member.hasPermission(Permission.SETTINGS))) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        if (clan.isInDebt()) {

            player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("§8[§6Clan§8] §cDein Clan hat Schulden (§e-" + String.format(java.util.Locale.GERMANY, "%,.2f", clan.getDebt()) + "$§c)! Clan-Aktionen sind gesperrt, bis die Schulden per §e/clan deposit §cbeglichen wurden."));

            return;

        }



        if (args.length < 2) {

            plugin.getMessageManager().send(player, "general.invalid-usage",

                    MessageManager.of("usage", "/clan rename <neuerName>"));

            return;

        }



        String newName = args[1];



        if (!plugin.getClanManager().isValidClanName(newName)) {

            if (newName.length() < plugin.getConfigManager().getClanNameMinLength()) {

                plugin.getMessageManager().send(player, "clan.name-too-short",

                        MessageManager.of("count", String.valueOf(plugin.getConfigManager().getClanNameMinLength())));

            } else if (newName.length() > plugin.getConfigManager().getClanNameMaxLength()) {

                plugin.getMessageManager().send(player, "clan.name-too-long",

                        MessageManager.of("count", String.valueOf(plugin.getConfigManager().getClanNameMaxLength())));

            } else {

                plugin.getMessageManager().send(player, "clan.name-invalid");

            }

            return;

        }



        if (plugin.getClanManager().clanExists(newName)) {

            plugin.getMessageManager().send(player, "clan.name-taken");

            return;

        }



        String oldName = clan.getName();

        if (plugin.getClanManager().renameClan(clan, newName)) {

            player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("§8[§6Clan§8] §aDer Clan wurde erfolgreich von §e" + oldName + " §azu §e" + newName + " §aumbenannt!"));

            clan.broadcastMessage("§8[§6Clan§8] §7Der Clan wurde von §e" + player.getName() + " §7in §e" + newName + " §7umbenannt.");

        }

    }



    private void handleCreate(Player player, String[] args) {

        if (!player.hasPermission("clansystem.create")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        if (args.length < 2) {

            plugin.getMessageManager().send(player, "general.invalid-usage",

                    MessageManager.of("usage", "/clan create <name>"));

            return;

        }



        if (plugin.getClanManager().isInClan(player)) {

            plugin.getMessageManager().send(player, "clan.already-in-clan");

            return;

        }



        if (!player.hasPermission("clansystem.bypass.cooldown")) {

            long cooldownExpires = plugin.getDatabaseManager().getCooldown(player.getUniqueId(), "CREATE");

            if (cooldownExpires > System.currentTimeMillis()) {

                long remaining = (cooldownExpires - System.currentTimeMillis()) / 1000;

                plugin.getMessageManager().send(player, "clan.create-cooldown",

                        MessageManager.of("time", MessageManager.formatTime(remaining)));

                return;

            }

        }



        String name = args[1];



        if (!plugin.getClanManager().isValidClanName(name)) {

            if (name.length() < plugin.getConfigManager().getClanNameMinLength()) {

                plugin.getMessageManager().send(player, "clan.name-too-short",

                        MessageManager.of("count", String.valueOf(plugin.getConfigManager().getClanNameMinLength())));

            } else if (name.length() > plugin.getConfigManager().getClanNameMaxLength()) {

                plugin.getMessageManager().send(player, "clan.name-too-long",

                        MessageManager.of("count", String.valueOf(plugin.getConfigManager().getClanNameMaxLength())));

            } else {

                plugin.getMessageManager().send(player, "clan.name-invalid");

            }

            return;

        }



        if (plugin.getClanManager().clanExists(name)) {

            plugin.getMessageManager().send(player, "clan.name-taken");

            return;

        }



        Clan clan = plugin.getClanManager().createClan(name, player);

        if (clan != null) {

            plugin.getMessageManager().send(player, "clan.created",

                    MessageManager.of("clan", clan.getColoredName()));



            if (plugin.getConfigManager().isBroadcastClanCreated()) {

                plugin.getMessageManager().broadcastExcept("broadcast.clan-created", player,

                        MessageManager.of("clan", clan.getColoredName(), "player", player.getName()));

            }

        }

    }



    private void handleDisband(Player player) {

        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        if (!clan.isLeader(player.getUniqueId())) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        new ConfirmGUI(plugin, player, "Clan auflösen?",

                () -> {



                    if (plugin.getConfigManager().isBroadcastClanDisbanded()) {

                        plugin.getMessageManager().broadcastExcept("broadcast.clan-disbanded", player,

                                MessageManager.of("clan", clan.getColoredName()));

                    }



                    plugin.getClanManager().disbandClan(clan);

                    plugin.getMessageManager().send(player, "clan.disbanded",

                            MessageManager.of("clan", clan.getColoredName()));

                    player.closeInventory();

                },

                () -> player.closeInventory()

        ).open();

    }



    private void handleLeave(Player player) {

        if (!player.hasPermission("clansystem.leave")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        if (clan.isLeader(player.getUniqueId())) {

            plugin.getMessageManager().send(player, "clan.cannot-leave-leader");

            return;

        }



        String clanName = clan.getColoredName();

        plugin.getClanManager().removeMember(clan, player.getUniqueId());



        long cooldownDuration = plugin.getConfigManager().getLeaveCooldown() * 1000L;

        plugin.getDatabaseManager().setCooldown(player.getUniqueId(), "CREATE",

                System.currentTimeMillis() + cooldownDuration);



        plugin.getMessageManager().send(player, "clan.left",

                MessageManager.of("clan", clanName));



        clan.broadcastMessage(plugin.getMessageManager().get("clan.member-left",

                MessageManager.of("player", player.getName())));



        if (plugin.getConfigManager().isBroadcastMemberLeft()) {

            plugin.getMessageManager().broadcastExcept("broadcast.member-left", player,

                    MessageManager.of("player", player.getName(), "clan", clanName));

        }

    }



    private void handleKick(Player player, String[] args) {

        if (!player.hasPermission("clansystem.kick")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        if (args.length < 2) {

            plugin.getMessageManager().send(player, "general.invalid-usage",

                    MessageManager.of("usage", "/clan kick <spieler>"));

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        ClanMember kicker = clan.getMember(player.getUniqueId());

        if (kicker == null || !kicker.hasPermission(Permission.KICK)) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        String targetName = args[1];

        Player target = Bukkit.getPlayer(targetName);

        UUID targetUuid = target != null ? target.getUniqueId() : Bukkit.getOfflinePlayer(targetName).getUniqueId();



        ClanMember targetMember = clan.getMember(targetUuid);

        if (targetMember == null) {

            plugin.getMessageManager().send(player, "clan.target-not-in-your-clan",

                    MessageManager.of("player", targetName));

            return;

        }



        if (targetUuid.equals(player.getUniqueId())) {

            plugin.getMessageManager().send(player, "clan.cannot-kick-self");

            return;

        }



        if (targetMember.getRank() != null && kicker.getRank() != null) {

            if (targetMember.getRank().isHigherOrEqual(kicker.getRank())) {

                plugin.getMessageManager().send(player, "clan.cannot-kick-higher");

                return;

            }

        }



        plugin.getClanManager().removeMember(clan, targetUuid);



        plugin.getMessageManager().send(player, "clan.kicked-by",

                MessageManager.of("player", targetMember.getName(), "target", player.getName()));



        if (target != null) {

            plugin.getMessageManager().send(target, "clan.kicked",

                    MessageManager.of("clan", clan.getColoredName()));

        }



        clan.broadcastMessage(plugin.getMessageManager().get("broadcast.member-kicked",

                MessageManager.of("player", targetMember.getName(), "clan", clan.getColoredName())),

                player.getUniqueId());

    }



    private void handleInvite(Player player, String[] args) {

        if (!player.hasPermission("clansystem.invite")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        if (args.length < 2) {

            plugin.getMessageManager().send(player, "general.invalid-usage",

                    MessageManager.of("usage", "/clan invite <spieler>"));

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        ClanMember member = clan.getMember(player.getUniqueId());

        if (member == null || !member.hasPermission(Permission.INVITE)) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        Player target = Bukkit.getPlayer(args[1]);

        if (target == null) {

            plugin.getMessageManager().send(player, "general.player-not-found",

                    MessageManager.of("player", args[1]));

            return;

        }



        if (plugin.getClanManager().isInClan(target)) {

            plugin.getMessageManager().send(player, "clan.target-already-in-clan");

            return;

        }



        if (clan.hasInvite(target.getUniqueId())) {

            plugin.getMessageManager().send(player, "clan.already-invited");

            return;

        }



        clan.addInvite(target.getUniqueId());

        plugin.getMessageManager().send(player, "clan.invited",

                MessageManager.of("player", target.getName()));



        plugin.getMessageManager().send(target, "clan.invite-received",

                MessageManager.of("player", player.getName(), "clan", clan.getColoredName()));

        plugin.getMessageManager().sendRaw(target, "clan.invite-accept");

    }



    private void handleAccept(Player player) {

        if (plugin.getClanManager().isInClan(player)) {

            plugin.getMessageManager().send(player, "clan.already-in-clan");

            return;

        }



        Clan invitingClan = null;

        for (Clan clan : plugin.getClanManager().getAllClans()) {

            if (clan.hasInvite(player.getUniqueId())) {

                invitingClan = clan;

                break;

            }

        }



        if (invitingClan == null) {

            plugin.getMessageManager().send(player, "clan.no-pending-invite");

            return;

        }



        LevelManager levelManager = plugin.getLevelManager();

        int maxMembers = levelManager.getMaxMembers(invitingClan.getLevel());

        if (invitingClan.getMemberCount() >= maxMembers) {

            plugin.getMessageManager().send(player, "clan.clan-full",

                    MessageManager.of("count", String.valueOf(invitingClan.getMemberCount()),

                            "max", String.valueOf(maxMembers)));

            return;

        }



        invitingClan.removeInvite(player.getUniqueId());

        ClanRank lowestRank = invitingClan.getLowestRank();

        plugin.getClanManager().addMember(invitingClan, player, lowestRank);



        plugin.getMessageManager().send(player, "clan.joined",

                MessageManager.of("clan", invitingClan.getColoredName()));



        invitingClan.broadcastMessage(plugin.getMessageManager().get("clan.member-joined",

                MessageManager.of("player", player.getName())), player.getUniqueId());



        if (plugin.getConfigManager().isBroadcastMemberJoined()) {

            plugin.getMessageManager().broadcastExcept("broadcast.member-joined", player,

                    MessageManager.of("player", player.getName(), "clan", invitingClan.getColoredName()));

        }

    }



    private void handleApply(Player player, String[] args) {

        if (args.length < 2) {

            plugin.getMessageManager().send(player, "general.invalid-usage",

                    MessageManager.of("usage", "/clan apply <clanname>"));

            return;

        }



        if (plugin.getClanManager().isInClan(player)) {

            plugin.getMessageManager().send(player, "clan.already-in-clan");

            return;

        }



        Clan clan = plugin.getClanManager().getClan(args[1]);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-found",

                    MessageManager.of("clan", args[1]));

            return;

        }



        if (clan.getJoinType() != Clan.JoinType.APPLY) {

            plugin.getMessageManager().send(player, "clan.not-open");

            return;

        }



        if (clan.hasApplication(player.getUniqueId())) {

            plugin.getMessageManager().send(player, "clan.already-applied");

            return;

        }



        clan.addApplication(player.getUniqueId());

        plugin.getMessageManager().send(player, "clan.applied",

                MessageManager.of("clan", clan.getColoredName()));



        for (ClanMember member : clan.getOnlineMembers()) {

            if (member.hasPermission(Permission.INVITE)) {

                Player memberPlayer = member.getPlayer();

                if (memberPlayer != null) {

                    plugin.getMessageManager().send(memberPlayer, "clan.application-received",

                            MessageManager.of("player", player.getName()));

                }

            }

        }

    }



    private void handleJoin(Player player, String[] args) {

        if (!player.hasPermission("clansystem.join")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        if (args.length < 2) {

            plugin.getMessageManager().send(player, "general.invalid-usage",

                    MessageManager.of("usage", "/clan join <clanname>"));

            return;

        }



        if (plugin.getClanManager().isInClan(player)) {

            plugin.getMessageManager().send(player, "clan.already-in-clan");

            return;

        }



        Clan clan = plugin.getClanManager().getClan(args[1]);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-found",

                    MessageManager.of("clan", args[1]));

            return;

        }



        if (clan.getJoinType() != Clan.JoinType.OPEN) {

            plugin.getMessageManager().send(player, "clan.not-open");

            return;

        }



        LevelManager levelManager = plugin.getLevelManager();

        int maxMembers = levelManager.getMaxMembers(clan.getLevel());

        if (clan.getMemberCount() >= maxMembers) {

            plugin.getMessageManager().send(player, "clan.clan-full",

                    MessageManager.of("count", String.valueOf(clan.getMemberCount()),

                            "max", String.valueOf(maxMembers)));

            return;

        }



        ClanRank lowestRank = clan.getLowestRank();

        plugin.getClanManager().addMember(clan, player, lowestRank);



        plugin.getMessageManager().send(player, "clan.joined",

                MessageManager.of("clan", clan.getColoredName()));



        clan.broadcastMessage(plugin.getMessageManager().get("clan.member-joined",

                MessageManager.of("player", player.getName())), player.getUniqueId());



        if (plugin.getConfigManager().isBroadcastMemberJoined()) {

            plugin.getMessageManager().broadcastExcept("broadcast.member-joined", player,

                    MessageManager.of("player", player.getName(), "clan", clan.getColoredName()));

        }

    }



    /**

     * Es gibt pro Clan immer nur genau ein Home (siehe LevelManager#getMaxHomes).

     * Ein eigener Name ist daher unnötig - diese Methode liefert das einzige

     * vorhandene Home des Clans, falls eines gesetzt ist.

     */

    private ClanHome getPrimaryHome(Clan clan) {

        return clan.getHomes().values().stream().findFirst().orElse(null);

    }



    private void handleSetHome(Player player, String[] args) {

        if (!player.hasPermission("clansystem.home")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        if (clan.isInDebt()) {

            player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("§8[§6Clan§8] §cDein Clan hat Schulden (§e-" + String.format(java.util.Locale.GERMANY, "%,.2f", clan.getDebt()) + "$§c)! Clan-Aktionen sind gesperrt, bis die Schulden per §e/clan deposit §cbeglichen wurden."));

            return;

        }



        ClanMember member = clan.getMember(player.getUniqueId());

        if (member == null || !member.hasPermission(Permission.SET_HOME)) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        // Da es nur ein Home pro Clan gibt, wird ein bereits vorhandenes Home

        // einfach an die neue Position verschoben, statt nach einem Namen zu fragen.

        ClanHome existing = getPrimaryHome(clan);

        String homeName = existing != null ? existing.getName() : DEFAULT_HOME_NAME;



        ClanHome home = new ClanHome(clan.getId(), homeName, player.getLocation());

        if (existing != null) {

            home.setId(existing.getId());

        }



        clan.addHome(home);

        plugin.getMessageManager().send(player, "home.set",

                MessageManager.of("name", homeName));



        plugin.getDatabaseManager().saveHome(home).thenAccept(id -> home.setId(id));

    }



    private void handleHome(Player player, String[] args) {

        if (!player.hasPermission("clansystem.home")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        if (clan.isInDebt()) {

            player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("§8[§6Clan§8] §cDein Clan hat Schulden (§e-" + String.format(java.util.Locale.GERMANY, "%,.2f", clan.getDebt()) + "$§c)! Clan-Aktionen sind gesperrt, bis die Schulden per §e/clan deposit §cbeglichen wurden."));

            return;

        }



        ClanHome home = getPrimaryHome(clan);



        if (home == null) {

            plugin.getMessageManager().send(player, "home.not-found",

                    MessageManager.of("name", DEFAULT_HOME_NAME));

            return;

        }



        String homeName = home.getName();

        Location location = home.getLocation();

        if (location == null) {

            plugin.getMessageManager().send(player, "home.not-found",

                    MessageManager.of("name", homeName));

            return;

        }



        if (!checkTeleportCooldown(player, homeCooldowns)) {

            return;

        }



        int warmup = plugin.getConfigManager().getTeleportWarmup();

        boolean bypassWarmup = player.hasPermission("clansystem.bypass.warmup");

        boolean cancelOnMove = plugin.getConfigManager().isCancelOnMove() && !bypassWarmup;



        if (warmup > 0 && !bypassWarmup) {

            plugin.getMessageManager().send(player, "home.warmup",

                    MessageManager.of("time", String.valueOf(warmup)));

        }



        plugin.getTeleportManager().startWarmup(player, bypassWarmup ? 0 : warmup, cancelOnMove,

                () -> {

                    player.teleport(location);

                    applyTeleportCooldown(player, homeCooldowns);

                    plugin.getMessageManager().send(player, "home.teleported",

                            MessageManager.of("name", homeName));

                },

                () -> plugin.getMessageManager().send(player, "home.warmup-cancelled")

        );

    }



    private void handleDelHome(Player player, String[] args) {

        if (!player.hasPermission("clansystem.home")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        if (clan.isInDebt()) {

            player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("§8[§6Clan§8] §cDein Clan hat Schulden (§e-" + String.format(java.util.Locale.GERMANY, "%,.2f", clan.getDebt()) + "$§c)! Clan-Aktionen sind gesperrt, bis die Schulden per §e/clan deposit §cbeglichen wurden."));

            return;

        }



        ClanMember member = clan.getMember(player.getUniqueId());

        if (member == null || !member.hasPermission(Permission.DEL_HOME)) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        ClanHome home = getPrimaryHome(clan);



        if (home == null) {

            plugin.getMessageManager().send(player, "home.not-found",

                    MessageManager.of("name", DEFAULT_HOME_NAME));

            return;

        }



        String homeName = home.getName();

        clan.removeHome(homeName);

        plugin.getDatabaseManager().deleteHome(clan.getId(), homeName);



        plugin.getMessageManager().send(player, "home.deleted",

                MessageManager.of("name", homeName));

    }



    private void handleHomes(Player player) {

        if (!player.hasPermission("clansystem.home")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        if (clan.isInDebt()) {

            player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("§8[§6Clan§8] §cDein Clan hat Schulden (§e-" + String.format(java.util.Locale.GERMANY, "%,.2f", clan.getDebt()) + "$§c)! Clan-Aktionen sind gesperrt, bis die Schulden per §e/clan deposit §cbeglichen wurden."));

            return;

        }



        new ClanHomesGUI(plugin, player, clan).open();

    }



    private void handleSetWarp(Player player, String[] args) {

        if (!player.hasPermission("clansystem.warp")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        if (args.length < 2) {

            plugin.getMessageManager().send(player, "general.invalid-usage",

                    MessageManager.of("usage", "/clan setwarp <name>"));

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        if (clan.isInDebt()) {

            player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("§8[§6Clan§8] §cDein Clan hat Schulden (§e-" + String.format(java.util.Locale.GERMANY, "%,.2f", clan.getDebt()) + "$§c)! Clan-Aktionen sind gesperrt, bis die Schulden per §e/clan deposit §cbeglichen wurden."));

            return;

        }



        ClanMember member = clan.getMember(player.getUniqueId());

        if (member == null || !member.hasPermission(Permission.SET_WARP)) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        String warpName = args[1].toLowerCase();

        LevelManager levelManager = plugin.getLevelManager();

        int maxWarps = levelManager.getMaxWarps(clan.getLevel()) + clan.getBonusWarps();



        if (clan.getWarp(warpName) == null && clan.getWarpCount() >= maxWarps) {

            plugin.getMessageManager().send(player, "warp.limit-reached",

                    MessageManager.of("count", String.valueOf(clan.getWarpCount()),

                            "max", String.valueOf(maxWarps)));

            return;

        }



        ClanWarp warp = new ClanWarp(clan.getId(), warpName, player.getLocation(), false);



        clan.addWarp(warp);

        plugin.getMessageManager().send(player, "warp.set",

                MessageManager.of("name", warpName));



        plugin.getDatabaseManager().saveWarp(warp).thenAccept(id -> warp.setId(id));

    }



    private void handleWarp(Player player, String[] args) {

        if (!player.hasPermission("clansystem.warp")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        if (args.length < 2) {

            plugin.getMessageManager().send(player, "general.invalid-usage",

                    MessageManager.of("usage", "/clan warp <name>"));

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        if (clan.isInDebt()) {

            player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("§8[§6Clan§8] §cDein Clan hat Schulden (§e-" + String.format(java.util.Locale.GERMANY, "%,.2f", clan.getDebt()) + "$§c)! Clan-Aktionen sind gesperrt, bis die Schulden per §e/clan deposit §cbeglichen wurden."));

            return;

        }



        String warpName = args[1].toLowerCase();

        ClanWarp warp = clan.getWarp(warpName);



        if (warp == null) {

            for (int allyId : clan.getAllies()) {

                Clan ally = plugin.getClanManager().getClan(allyId);

                if (ally != null) {

                    ClanWarp allyWarp = ally.getWarp(warpName);

                    if (allyWarp != null && allyWarp.isVisibleToAllies()) {

                        warp = allyWarp;

                        break;

                    }

                }

            }

        }



        if (warp == null) {

            plugin.getMessageManager().send(player, "warp.not-found",

                    MessageManager.of("name", warpName));

            return;

        }



        Location location = warp.getLocation();

        if (location == null) {

            plugin.getMessageManager().send(player, "warp.not-found",

                    MessageManager.of("name", warpName));

            return;

        }



        if (!checkTeleportCooldown(player, warpCooldowns)) {

            return;

        }



        int warmup = plugin.getConfigManager().getTeleportWarmup();

        final ClanWarp finalWarp = warp;

        boolean bypassWarmup = player.hasPermission("clansystem.bypass.warmup");

        boolean cancelOnMove = plugin.getConfigManager().isCancelOnMove() && !bypassWarmup;



        if (warmup > 0 && !bypassWarmup) {

            plugin.getMessageManager().send(player, "home.warmup",

                    MessageManager.of("time", String.valueOf(warmup)));

        }



        plugin.getTeleportManager().startWarmup(player, bypassWarmup ? 0 : warmup, cancelOnMove,

                () -> {

                    player.teleport(finalWarp.getLocation());

                    applyTeleportCooldown(player, warpCooldowns);

                    plugin.getMessageManager().send(player, "warp.teleported",

                            MessageManager.of("name", warpName));

                },

                () -> plugin.getMessageManager().send(player, "home.warmup-cancelled")

        );

    }



    private void handleDelWarp(Player player, String[] args) {

        if (!player.hasPermission("clansystem.warp")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        if (args.length < 2) {

            plugin.getMessageManager().send(player, "general.invalid-usage",

                    MessageManager.of("usage", "/clan delwarp <name>"));

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        if (clan.isInDebt()) {

            player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("§8[§6Clan§8] §cDein Clan hat Schulden (§e-" + String.format(java.util.Locale.GERMANY, "%,.2f", clan.getDebt()) + "$§c)! Clan-Aktionen sind gesperrt, bis die Schulden per §e/clan deposit §cbeglichen wurden."));

            return;

        }



        ClanMember member = clan.getMember(player.getUniqueId());

        if (member == null || !member.hasPermission(Permission.DEL_WARP)) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        String warpName = args[1].toLowerCase();

        ClanWarp warp = clan.getWarp(warpName);



        if (warp == null) {

            plugin.getMessageManager().send(player, "warp.not-found",

                    MessageManager.of("name", warpName));

            return;

        }



        clan.removeWarp(warpName);

        plugin.getDatabaseManager().deleteWarp(clan.getId(), warpName);



        plugin.getMessageManager().send(player, "warp.deleted",

                MessageManager.of("name", warpName));

    }



    private void handleWarps(Player player) {

        if (!player.hasPermission("clansystem.warp")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        if (clan.isInDebt()) {

            player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("§8[§6Clan§8] §cDein Clan hat Schulden (§e-" + String.format(java.util.Locale.GERMANY, "%,.2f", clan.getDebt()) + "$§c)! Clan-Aktionen sind gesperrt, bis die Schulden per §e/clan deposit §cbeglichen wurden."));

            return;

        }



        new ClanWarpsGUI(plugin, player, clan).open();

    }



    private void handleChest(Player player) {

        if (!player.hasPermission("clansystem.chest")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        if (clan.isInDebt()) {

            player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("§8[§6Clan§8] §cDein Clan hat Schulden (§e-" + String.format(java.util.Locale.GERMANY, "%,.2f", clan.getDebt()) + "$§c)! Clan-Aktionen sind gesperrt, bis die Schulden per §e/clan deposit §cbeglichen wurden."));

            return;

        }



        ClanMember member = clan.getMember(player.getUniqueId());

        if (member == null || !member.hasPermission(Permission.CHEST_ACCESS)) {

            plugin.getMessageManager().send(player, "chest.no-access");

            return;

        }



        new ClanChestGUI(plugin, player, clan).open();

    }



    private void handleAlly(Player player, String[] args) {

        if (!player.hasPermission("clansystem.ally")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        if (args.length < 2) {

            plugin.getMessageManager().send(player, "general.invalid-usage",

                    MessageManager.of("usage", "/clan ally <invite|accept|remove> <clanname>"));

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        ClanMember member = clan.getMember(player.getUniqueId());

        if (member == null || !member.hasPermission(Permission.ALLY_MANAGE)) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        String subCommand = args[1].toLowerCase();



        switch (subCommand) {

            case "invite" -> {

                if (args.length < 3) {

                    plugin.getMessageManager().send(player, "general.invalid-usage",

                            MessageManager.of("usage", "/clan ally invite <clanname>"));

                    return;

                }



                Clan targetClan = plugin.getClanManager().getClan(args[2]);

                if (targetClan == null) {

                    plugin.getMessageManager().send(player, "clan.not-found",

                            MessageManager.of("clan", args[2]));

                    return;

                }



                if (targetClan.getId() == clan.getId()) {

                    plugin.getMessageManager().send(player, "alliance.cannot-ally-self");

                    return;

                }



                if (clan.isAlliedWith(targetClan.getId())) {

                    plugin.getMessageManager().send(player, "alliance.already-allied");

                    return;

                }



                plugin.getAllianceManager().sendAllianceRequest(clan, targetClan);

                plugin.getMessageManager().send(player, "alliance.invite-sent",

                        MessageManager.of("ally", targetClan.getColoredName()));



                for (ClanMember targetMember : targetClan.getOnlineMembers()) {

                    if (targetMember.hasPermission(Permission.ALLY_MANAGE)) {

                        Player targetPlayer = targetMember.getPlayer();

                        if (targetPlayer != null) {

                            plugin.getMessageManager().send(targetPlayer, "alliance.invite-received",

                                    MessageManager.of("clan", clan.getColoredName()));

                        }

                    }

                }

            }

            case "accept" -> {

                if (args.length < 3) {

                    plugin.getMessageManager().send(player, "general.invalid-usage",

                            MessageManager.of("usage", "/clan ally accept <clanname>"));

                    return;

                }



                Clan senderClan = plugin.getClanManager().getClan(args[2]);

                if (senderClan == null) {

                    plugin.getMessageManager().send(player, "clan.not-found",

                            MessageManager.of("clan", args[2]));

                    return;

                }



                if (!plugin.getAllianceManager().hasPendingRequest(senderClan, clan)) {

                    plugin.getMessageManager().send(player, "alliance.no-pending");

                    return;

                }



                plugin.getAllianceManager().formAlliance(senderClan, clan);

                plugin.getMessageManager().send(player, "alliance.formed",

                        MessageManager.of("ally", senderClan.getColoredName()));

            }

            case "remove" -> {

                if (args.length < 3) {

                    plugin.getMessageManager().send(player, "general.invalid-usage",

                            MessageManager.of("usage", "/clan ally remove <clanname>"));

                    return;

                }



                Clan allyClan = plugin.getClanManager().getClan(args[2]);

                if (allyClan == null) {

                    plugin.getMessageManager().send(player, "clan.not-found",

                            MessageManager.of("clan", args[2]));

                    return;

                }



                if (!clan.isAlliedWith(allyClan.getId())) {

                    plugin.getMessageManager().send(player, "alliance.not-allied");

                    return;

                }



                plugin.getAllianceManager().breakAlliance(clan.getId(), allyClan.getId());

                plugin.getMessageManager().send(player, "alliance.broken",

                        MessageManager.of("ally", allyClan.getColoredName()));

            }

            default -> plugin.getMessageManager().send(player, "general.invalid-usage",

                    MessageManager.of("usage", "/clan ally <invite|accept|remove> <clanname>"));

        }

    }



    private void handleWar(Player player, String[] args) {

        if (!player.hasPermission("clansystem.war")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        if (args.length < 2) {

            plugin.getMessageManager().send(player, "general.invalid-usage",

                    MessageManager.of("usage", "/clan war <declare|surrender> <clanname>"));

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        ClanMember member = clan.getMember(player.getUniqueId());

        if (member == null || !member.hasPermission(Permission.WAR_MANAGE)) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        String subCommand = args[1].toLowerCase();



        switch (subCommand) {

            case "declare" -> {

                if (args.length < 3) {

                    plugin.getMessageManager().send(player, "general.invalid-usage",

                            MessageManager.of("usage", "/clan war declare <clanname>"));

                    return;

                }



                Clan targetClan = plugin.getClanManager().getClan(args[2]);

                if (targetClan == null) {

                    plugin.getMessageManager().send(player, "clan.not-found",

                            MessageManager.of("clan", args[2]));

                    return;

                }



                if (targetClan.getId() == clan.getId()) {

                    plugin.getMessageManager().send(player, "war.cannot-war-self");

                    return;

                }



                if (clan.isAlliedWith(targetClan.getId())) {

                    plugin.getMessageManager().send(player, "war.cannot-war-ally");

                    return;

                }



                if (plugin.getWarManager().areAtWar(clan.getId(), targetClan.getId())) {

                    plugin.getMessageManager().send(player, "war.already-at-war");

                    return;

                }



                if (!clan.hasAnyMemberWithPermission(Permission.WAR_ALWAYS_PVP)) {

                    player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("§8[§6Clan§8] §cDein Clan hat kein Mitglied mit Clankrieg-PvP-Rechten (WAR_ALWAYS_PVP)!"));

                    return;

                }



                if (!targetClan.hasAnyMemberWithPermission(Permission.WAR_ALWAYS_PVP)) {

                    player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("§8[§6Clan§8] §cDer gegnerische Clan hat kein Mitglied mit Clankrieg-PvP-Rechten (WAR_ALWAYS_PVP)!"));

                    return;

                }



                plugin.getWarManager().declareWar(clan, targetClan);

                plugin.getMessageManager().send(player, "war.declared",

                        MessageManager.of("clan", clan.getColoredName(), "enemy", targetClan.getColoredName()));

            }

            case "surrender" -> {

                if (args.length < 3) {



                    new ClanWarsGUI(plugin, player, clan).open();

                    return;

                }



                Clan enemyClan = plugin.getClanManager().getClan(args[2]);

                if (enemyClan == null) {

                    plugin.getMessageManager().send(player, "clan.not-found",

                            MessageManager.of("clan", args[2]));

                    return;

                }



                if (!plugin.getWarManager().areAtWar(clan.getId(), enemyClan.getId())) {

                    plugin.getMessageManager().send(player, "war.not-at-war");

                    return;

                }



                plugin.getWarManager().surrender(clan, enemyClan);

                plugin.getMessageManager().send(player, "war.surrendered",

                        MessageManager.of("clan", clan.getColoredName(), "enemy", enemyClan.getColoredName()));

            }

            default -> plugin.getMessageManager().send(player, "general.invalid-usage",

                    MessageManager.of("usage", "/clan war <declare|surrender> <clanname>"));

        }

    }



    private void handleRank(Player player, String[] args) {

        if (!player.hasPermission("clansystem.rank")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        if (args.length < 2) {

            plugin.getMessageManager().send(player, "general.invalid-usage",

                    MessageManager.of("usage", "/clan rank <set|create|delete|rename> ..."));

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        ClanMember member = clan.getMember(player.getUniqueId());

        if (member == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }

        String subCommand = args[1].toLowerCase();



        switch (subCommand) {

            case "set" -> {

                if (args.length < 4) {

                    plugin.getMessageManager().send(player, "general.invalid-usage",

                            MessageManager.of("usage", "/clan rank set <spieler> <rang>"));

                    return;

                }



                if (!member.hasPermission(Permission.PROMOTE) && !member.hasPermission(Permission.DEMOTE)) {

                    plugin.getMessageManager().send(player, "general.no-permission");

                    return;

                }



                String targetName = args[2];

                String rankName = args[3];



                ClanMember targetMember = null;

                for (ClanMember m : clan.getMembers().values()) {

                    if (m.getName().equalsIgnoreCase(targetName)) {

                        targetMember = m;

                        break;

                    }

                }



                if (targetMember == null) {

                    plugin.getMessageManager().send(player, "clan.target-not-in-your-clan",

                            MessageManager.of("player", targetName));

                    return;

                }



                if (targetMember.getUuid().equals(player.getUniqueId())) {

                    plugin.getMessageManager().send(player, "rank.cannot-change-own");

                    return;

                }



                if (targetMember.getRank() != null && member.getRank() != null) {

                    if (targetMember.getRank().isHigherOrEqual(member.getRank())) {

                        plugin.getMessageManager().send(player, "rank.cannot-change-higher");

                        return;

                    }

                }



                ClanRank newRank = clan.getRankByName(rankName);

                if (newRank == null) {

                    plugin.getMessageManager().send(player, "rank.rank-not-found",

                            MessageManager.of("rank", rankName));

                    return;

                }



                if (member.getRank() != null && newRank.isHigherThan(member.getRank())) {

                    plugin.getMessageManager().send(player, "rank.cannot-set-higher");

                    return;

                }



                targetMember.setRank(newRank);

                plugin.getDatabaseManager().updateMember(targetMember);



                plugin.getMessageManager().send(player, "rank.set",

                        MessageManager.of("player", targetMember.getName(), "rank", newRank.getName()));



                Player targetPlayer = targetMember.getPlayer();

                if (targetPlayer != null) {

                    plugin.getMessageManager().send(targetPlayer, "rank.rank-changed",

                            MessageManager.of("rank", newRank.getName()));

                }

            }

            case "create" -> {

                if (args.length < 3) {

                    plugin.getMessageManager().send(player, "general.invalid-usage",

                            MessageManager.of("usage", "/clan rank create <name>"));

                    return;

                }



                if (member == null || !member.hasPermission(Permission.RANK_MANAGE)) {

                    plugin.getMessageManager().send(player, "general.no-permission");

                    return;

                }



                String newRankName = args[2];

                ClanManager.RankCreateResult createResult = plugin.getClanManager().createRank(clan, newRankName);

                if (createResult == ClanManager.RankCreateResult.ALREADY_EXISTS) {

                    plugin.getMessageManager().send(player, "rank.name-taken",

                            MessageManager.of("rank", newRankName));

                    return;

                }



                plugin.getMessageManager().send(player, "rank.rank-created",

                        MessageManager.of("rank", newRankName));

            }

            case "delete" -> {

                if (args.length < 3) {

                    plugin.getMessageManager().send(player, "general.invalid-usage",

                            MessageManager.of("usage", "/clan rank delete <name>"));

                    return;

                }



                if (member == null || !member.hasPermission(Permission.RANK_MANAGE)) {

                    plugin.getMessageManager().send(player, "general.no-permission");

                    return;

                }



                String deleteRankName = args[2];

                ClanRank deleteRank = clan.getRankByName(deleteRankName);



                if (deleteRank == null) {

                    plugin.getMessageManager().send(player, "rank.rank-not-found",

                            MessageManager.of("rank", deleteRankName));

                    return;

                }



                ClanManager.RankDeleteResult result = plugin.getClanManager().deleteRank(clan, deleteRank);

                switch (result) {

                    case HAS_MEMBERS -> plugin.getMessageManager().send(player, "rank.cannot-delete-with-members");

                    case LAST_RANK -> plugin.getMessageManager().send(player, "rank.cannot-delete-default");

                    case NOT_FOUND -> plugin.getMessageManager().send(player, "rank.rank-not-found",

                            MessageManager.of("rank", deleteRankName));

                    case SUCCESS -> plugin.getMessageManager().send(player, "rank.rank-deleted",

                            MessageManager.of("rank", deleteRankName));

                }

            }

            case "rename" -> {

                if (args.length < 4) {

                    plugin.getMessageManager().send(player, "general.invalid-usage",

                            MessageManager.of("usage", "/clan rank rename <alter-name> <neuer-name>"));

                    return;

                }



                if (member == null || !member.hasPermission(Permission.RANK_MANAGE)) {

                    plugin.getMessageManager().send(player, "general.no-permission");

                    return;

                }



                String oldName = args[2];

                String newName = args[3];



                ClanRank rank = clan.getRankByName(oldName);

                if (rank == null) {

                    plugin.getMessageManager().send(player, "rank.rank-not-found",

                            MessageManager.of("rank", oldName));

                    return;

                }



                ClanManager.RankRenameResult renameResult = plugin.getClanManager().renameRank(clan, rank, newName);

                if (renameResult == ClanManager.RankRenameResult.NAME_TAKEN) {

                    plugin.getMessageManager().send(player, "rank.name-taken",

                            MessageManager.of("rank", newName));

                    return;

                }



                plugin.getMessageManager().send(player, "rank.rank-renamed",

                        MessageManager.of("rank", newName));

            }

            default -> {



                if (member.hasPermission(Permission.RANK_MANAGE)) {

                    new ClanRankGUI(plugin, player, clan).open();

                } else {

                    plugin.getMessageManager().send(player, "general.no-permission");

                }

            }

        }

    }



    private void handlePromote(Player player, String[] args) {

        if (args.length < 2) {

            plugin.getMessageManager().send(player, "general.invalid-usage",

                    MessageManager.of("usage", "/clan promote <spieler>"));

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        ClanMember member = clan.getMember(player.getUniqueId());

        if (member == null || !member.hasPermission(Permission.PROMOTE)) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        String targetName = args[1];

        ClanMember targetMember = null;

        for (ClanMember m : clan.getMembers().values()) {

            if (m.getName().equalsIgnoreCase(targetName)) {

                targetMember = m;

                break;

            }

        }



        if (targetMember == null) {

            plugin.getMessageManager().send(player, "clan.target-not-in-your-clan",

                    MessageManager.of("player", targetName));

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

            plugin.getMessageManager().send(player, "rank.cannot-set-higher");

            return;

        }



        if (member.getRank() != null && nextRank.isHigherOrEqual(member.getRank())) {

            plugin.getMessageManager().send(player, "rank.cannot-set-higher");

            return;

        }



        targetMember.setRank(nextRank);

        plugin.getDatabaseManager().updateMember(targetMember);



        plugin.getMessageManager().send(player, "rank.set",

                MessageManager.of("player", targetMember.getName(), "rank", nextRank.getName()));

    }



    private void handleDemote(Player player, String[] args) {

        if (args.length < 2) {

            plugin.getMessageManager().send(player, "general.invalid-usage",

                    MessageManager.of("usage", "/clan demote <spieler>"));

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        ClanMember member = clan.getMember(player.getUniqueId());

        if (member == null || !member.hasPermission(Permission.DEMOTE)) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        String targetName = args[1];

        ClanMember targetMember = null;

        for (ClanMember m : clan.getMembers().values()) {

            if (m.getName().equalsIgnoreCase(targetName)) {

                targetMember = m;

                break;

            }

        }



        if (targetMember == null) {

            plugin.getMessageManager().send(player, "clan.target-not-in-your-clan",

                    MessageManager.of("player", targetName));

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

            plugin.getMessageManager().send(player, "rank.cannot-change-higher");

            return;

        }



        targetMember.setRank(prevRank);

        plugin.getDatabaseManager().updateMember(targetMember);



        plugin.getMessageManager().send(player, "rank.set",

                MessageManager.of("player", targetMember.getName(), "rank", prevRank.getName()));

    }



    private void handleTransfer(Player player, String[] args) {

        if (args.length < 2) {

            plugin.getMessageManager().send(player, "general.invalid-usage",

                    MessageManager.of("usage", "/clan transfer <spieler>"));

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        if (!clan.isLeader(player.getUniqueId())) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        String targetName = args[1];

        ClanMember targetMember = null;

        for (ClanMember m : clan.getMembers().values()) {

            if (m.getName().equalsIgnoreCase(targetName)) {

                targetMember = m;

                break;

            }

        }



        if (targetMember == null) {

            plugin.getMessageManager().send(player, "clan.target-not-in-your-clan",

                    MessageManager.of("player", targetName));

            return;

        }



        if (targetMember.getUuid().equals(player.getUniqueId())) {

            plugin.getMessageManager().send(player, "rank.cannot-change-own");

            return;

        }



        boolean success = plugin.getClanManager().transferLeadership(clan, targetMember.getUuid());

        if (!success) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        plugin.getMessageManager().send(player, "clan.ownership-transferred",

                MessageManager.of("player", targetMember.getName()));



        if (targetMember.isOnline()) {

            plugin.getMessageManager().send(targetMember.getPlayer(), "clan.ownership-received",

                    MessageManager.of("clan", clan.getColoredName()));

        }

    }



    private void handleSettings(Player player) {

        if (!player.hasPermission("clansystem.settings")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        ClanMember member = clan.getMember(player.getUniqueId());

        if (member == null || (!member.hasPermission(Permission.SETTINGS) && !member.hasPermission(Permission.RANK_MANAGE))) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        new ClanSettingsGUI(plugin, player, clan).open();

    }



    private void handleList(Player player) {

        if (!player.hasPermission("clansystem.list")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        new ClanListGUI(plugin, player).open();

    }



    private void handleTop(Player player) {
        if (!player.hasPermission("clansystem.top") && !player.hasPermission("clansystem.use")) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return;
        }
        new ClanTopGUI(plugin, player).open();
    }

    private void handleDeposit(Player player, String[] args) {

        if (!player.hasPermission("clansystem.use")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        ClanMember member = clan.getMember(player.getUniqueId());

        if (member == null || (!clan.isLeader(player.getUniqueId()) && !member.hasPermission(Permission.BANK_DEPOSIT))) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        if (args.length < 2) {

            plugin.getMessageManager().send(player, "general.invalid-usage",

                    MessageManager.of("usage", "/clan deposit <betrag>"));

            return;

        }



        EconomyManager eco = plugin.getEconomyManager();

        if (eco == null || !eco.isAvailable()) {

            plugin.getMessageManager().send(player, "bank.not-available");

            return;

        }



        double amount;

        try {

            amount = Double.parseDouble(args[1].replace(",", "."));

        } catch (NumberFormatException e) {

            plugin.getMessageManager().send(player, "bank.invalid-amount");

            return;

        }



        if (amount <= 0) {

            plugin.getMessageManager().send(player, "bank.invalid-amount");

            return;

        }



        if (!eco.hasBalance(player, amount)) {

            plugin.getMessageManager().send(player, "bank.insufficient-player-funds");

            return;

        }



        if (!eco.withdrawPlayer(player, amount)) {

            plugin.getMessageManager().send(player, "bank.error");

            return;

        }



        boolean debtCleared = clan.deposit(amount);

        plugin.getDatabaseManager().updateClan(clan);

        if (debtCleared) {

            clan.broadcastMessage("§8[§6Clan§8] §aDie Schulden des Clans wurden beglichen! Alle Clan-Aktionen sind wieder freigeschaltet.");

        }

        ClanBankTransaction tx = new ClanBankTransaction(

                clan.getId(),

                player.getUniqueId(),

                player.getName(),

                ClanBankTransaction.TransactionType.DEPOSIT,

                amount,

                System.currentTimeMillis()

        );

        plugin.getDatabaseManager().logBankTransaction(tx);



        plugin.getMessageManager().send(player, "bank.deposit-success",

                MessageManager.of("amount", eco.format(amount), "balance", eco.format(clan.getBalance())));

        clan.broadcastMessage(plugin.getMessageManager().get("bank.broadcast-deposit",

                MessageManager.of("player", player.getName(), "amount", eco.format(amount))));

    }



    private void handleWithdraw(Player player, String[] args) {

        if (!player.hasPermission("clansystem.use")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        if (clan.isInDebt()) {

            player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("§8[§6Clan§8] §cDein Clan hat Schulden! Du kannst kein Geld abheben."));

            return;

        }



        ClanMember member = clan.getMember(player.getUniqueId());

        if (member == null || (!clan.isLeader(player.getUniqueId()) && !member.hasPermission(Permission.BANK_WITHDRAW))) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        if (args.length < 2) {

            plugin.getMessageManager().send(player, "general.invalid-usage",

                    MessageManager.of("usage", "/clan withdraw <betrag>"));

            return;

        }



        EconomyManager eco = plugin.getEconomyManager();

        if (eco == null || !eco.isAvailable()) {

            plugin.getMessageManager().send(player, "bank.not-available");

            return;

        }



        double amount;

        try {

            amount = Double.parseDouble(args[1].replace(",", "."));

        } catch (NumberFormatException e) {

            plugin.getMessageManager().send(player, "bank.invalid-amount");

            return;

        }



        if (amount <= 0) {

            plugin.getMessageManager().send(player, "bank.invalid-amount");

            return;

        }



        if (!clan.hasBalance(amount)) {

            plugin.getMessageManager().send(player, "bank.insufficient-clan-funds");

            return;

        }



        if (!eco.depositPlayer(player, amount)) {

            plugin.getMessageManager().send(player, "bank.error");

            return;

        }



        clan.withdraw(amount);

        plugin.getDatabaseManager().updateClan(clan);

        ClanBankTransaction tx = new ClanBankTransaction(

                clan.getId(),

                player.getUniqueId(),

                player.getName(),

                ClanBankTransaction.TransactionType.WITHDRAW,

                amount,

                System.currentTimeMillis()

        );

        plugin.getDatabaseManager().logBankTransaction(tx);



        plugin.getMessageManager().send(player, "bank.withdraw-success",

                MessageManager.of("amount", eco.format(amount), "balance", eco.format(clan.getBalance())));

        clan.broadcastMessage(plugin.getMessageManager().get("bank.broadcast-withdraw",

                MessageManager.of("player", player.getName(), "amount", eco.format(amount))));

    }



    private void handleBank(Player player, String[] args) {

        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        ClanMember member = clan.getMember(player.getUniqueId());

        EconomyManager eco = plugin.getEconomyManager();

        String formattedBalance = eco != null ? eco.format(clan.getBalance()) : (clan.getBalance() + " $");



        if (args.length == 1) {

            if (member != null && (clan.isLeader(player.getUniqueId()) || member.hasPermission(Permission.BANK_VIEW_TRANSACTIONS))) {

                new ClanBankTransactionsGUI(plugin, player, clan).open();

            } else {

                plugin.getMessageManager().send(player, "bank.balance-info",

                        MessageManager.of("balance", formattedBalance));

            }

            return;

        }



        String sub = args[1].toLowerCase();

        switch (sub) {

            case "deposit", "einzahlen" -> {

                String[] shifted = new String[args.length - 1];

                System.arraycopy(args, 1, shifted, 0, args.length - 1);

                handleDeposit(player, shifted);

            }

            case "withdraw", "auszahlen" -> {

                String[] shifted = new String[args.length - 1];

                System.arraycopy(args, 1, shifted, 0, args.length - 1);

                handleWithdraw(player, shifted);

            }

            case "top" -> handleTop(player);

            case "gui", "transactions", "transaktionen" -> {

                if (member != null && (clan.isLeader(player.getUniqueId()) || member.hasPermission(Permission.BANK_VIEW_TRANSACTIONS))) {

                    new ClanBankTransactionsGUI(plugin, player, clan).open();

                } else {

                    plugin.getMessageManager().send(player, "general.no-permission");

                }

            }

            default -> {

                plugin.getMessageManager().send(player, "bank.balance-info",

                        MessageManager.of("balance", formattedBalance));

            }

        }

    }



    private void handleInfo(Player player, String[] args) {

        if (!player.hasPermission("clansystem.info")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        Clan clan;



        if (args.length < 2) {

            clan = plugin.getClanManager().getClanByPlayer(player);

            if (clan == null) {

                plugin.getMessageManager().send(player, "general.invalid-usage",

                        MessageManager.of("usage", "/clan info <clanname>"));

                return;

            }

        } else {

            clan = plugin.getClanManager().getClan(args[1]);

            if (clan == null) {

                plugin.getMessageManager().send(player, "clan.not-found",

                        MessageManager.of("clan", args[1]));

                return;

            }

        }



        new ClanInfoGUI(plugin, player, clan).open();

    }



    private void handleChat(Player player, String[] args) {

        if (!player.hasPermission("clansystem.chat")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        if (args.length > 1) {



            StringBuilder message = new StringBuilder();

            for (int i = 1; i < args.length; i++) {

                if (i > 1) message.append(" ");

                message.append(args[i]);

            }



            new de.Z7534.ztone.clansystem.listeners.ChatListener(plugin).sendClanChat(player, clan, message.toString());

        } else {



            de.Z7534.ztone.clansystem.listeners.ChatListener.toggleClanChat(player);

        }

    }



    private void handleAllyChat(Player player, String[] args) {

        if (!player.hasPermission("clansystem.chat")) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        if (args.length > 1) {



            StringBuilder message = new StringBuilder();

            for (int i = 1; i < args.length; i++) {

                if (i > 1) message.append(" ");

                message.append(args[i]);

            }



            new de.Z7534.ztone.clansystem.listeners.ChatListener(plugin).sendAllyChat(player, clan, message.toString());

        } else {



            de.Z7534.ztone.clansystem.listeners.ChatListener.toggleAllyChat(player);

        }

    }



    private void handleSuffix(Player player, String[] args) {

        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {

            plugin.getMessageManager().send(player, "clan.not-in-clan");

            return;

        }



        ClanMember member = clan.getMember(player.getUniqueId());

        if (member == null || !member.hasPermission(Permission.SET_SUFFIX)) {

            plugin.getMessageManager().send(player, "general.no-permission");

            return;

        }



        if (args.length < 2) {



            clan.setSuffix("");

            plugin.getClanManager().updateClanInDatabase(clan);

            plugin.getMessageManager().send(player, "suffix.removed");

            return;

        }



        StringBuilder suffixBuilder = new StringBuilder();

        for (int i = 1; i < args.length; i++) {

            if (i > 1) suffixBuilder.append(" ");

            suffixBuilder.append(args[i]);

        }

        String suffix = suffixBuilder.toString();



        String stripped = de.Z7534.ztone.clansystem.utils.ColorUtils.stripColors(suffix);

        if (stripped.length() > plugin.getConfigManager().getSuffixMaxLength()) {

            plugin.getMessageManager().send(player, "suffix.too-long",

                    MessageManager.of("max", String.valueOf(plugin.getConfigManager().getSuffixMaxLength())));

            return;

        }



        for (String blocked : plugin.getConfigManager().getSuffixBlacklist()) {

            if (stripped.toLowerCase().contains(blocked.toLowerCase())) {

                plugin.getMessageManager().send(player, "suffix.blacklisted");

                return;

            }

        }



        clan.setSuffix(suffix);

        plugin.getClanManager().updateClanInDatabase(clan);

        plugin.getMessageManager().send(player, "suffix.set",

                MessageManager.of("suffix", suffix));

    }



    private void sendHelp(Player player) {

        player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&8&m----------&8[ &6Clan-Hilfe &8]&m----------"));

        player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clan create <name> &8- &7Clan erstellen"));

        player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clan join <name> &8- &7Clan beitreten"));

        player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clan leave &8- &7Clan verlassen"));

        player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clan invite <spieler> &8- &7Spieler einladen"));

        player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clan kick <spieler> &8- &7Spieler kicken"));

        player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clan home &8- &7Zum Home teleportieren"));

        player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clan warp <name> &8- &7Zum Warp teleportieren"));

        player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clan chest &8- &7Clan-Truhe öffnen"));

        player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clan chat [nachricht] &8- &7Clan-Chat (ohne Text: Umschalten)"));

        player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clan ac [nachricht] &8- &7Allianz-Chat (ohne Text: Umschalten)"));

        player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clan list &8- &7Clan-Liste anzeigen"));

        player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clan top &8- &7Top 10 reichste Clans"));

        player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clan deposit <betrag> &8- &7Geld in Clankasse einzahlen"));

        player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clan withdraw <betrag> &8- &7Geld aus Clankasse abheben"));

        player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clan bank &8- &7Clankasse öffnen"));

        player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clan info [clan] &8- &7Clan-Info anzeigen"));

        player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clan settings &8- &7Einstellungen öffnen"));

        player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&e/clan transfer <spieler> &8- &7Eigentümerschaft übertragen"));

        player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("&8&m---------------------------------"));

    }



    @Override

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (!(sender instanceof Player player)) {

            return Collections.emptyList();

        }



        List<String> completions = new ArrayList<>();



        if (args.length == 1) {

            List<String> subCommands = new ArrayList<>(Arrays.asList(

                    "create", "rename", "disband", "leave", "kick", "invite", "accept", "apply", "join",

                    "sethome", "home", "delhome", "homes", "setwarp", "warp", "delwarp", "warps",

                    "chest", "ally", "war", "rank", "settings", "list", "info", "chat", "ac", "suffix",

                    "promote", "demote", "transfer", "help"

            ));

            if (plugin.getConfigManager().isClankasseEnabled()) {

                subCommands.addAll(Arrays.asList("top", "deposit", "einzahlen", "withdraw", "auszahlen", "bank", "kasse"));

            }

            String input = args[0].toLowerCase();

            completions = subCommands.stream()

                    .filter(s -> s.startsWith(input))

                    .collect(Collectors.toList());

        } else if (args.length == 2) {

            String subCommand = args[0].toLowerCase();

            String input = args[1].toLowerCase();



            switch (subCommand) {

                case "kick", "promote", "demote", "transfer" -> {

                    Clan clan = plugin.getClanManager().getClanByPlayer(player);

                    if (clan != null) {

                        completions = clan.getMembers().values().stream()

                                .map(ClanMember::getName)

                                .filter(n -> n.toLowerCase().startsWith(input))

                                .collect(Collectors.toList());

                    }

                }

                case "invite" -> {



                    completions = Bukkit.getOnlinePlayers().stream()

                            .map(Player::getName)

                            .filter(n -> n.toLowerCase().startsWith(input))

                            .collect(Collectors.toList());

                }

                case "apply", "join", "info" -> {

                    completions = plugin.getClanManager().getAllClans().stream()

                            .map(Clan::getName)

                            .filter(n -> n.toLowerCase().startsWith(input))

                            .collect(Collectors.toList());

                }

                case "warp", "delwarp" -> {

                    Clan clan = plugin.getClanManager().getClanByPlayer(player);

                    if (clan != null) {

                        completions = clan.getWarps().keySet().stream()

                                .filter(n -> n.startsWith(input))

                                .collect(Collectors.toList());

                    }

                }

                case "ally" -> {

                    completions = Arrays.asList("invite", "accept", "remove").stream()

                            .filter(s -> s.startsWith(input))

                            .collect(Collectors.toList());

                }

                case "war" -> {

                    completions = Arrays.asList("declare", "surrender").stream()

                            .filter(s -> s.startsWith(input))

                            .collect(Collectors.toList());

                }

                case "rank" -> {

                    completions = Arrays.asList("set", "create", "delete", "rename").stream()

                            .filter(s -> s.startsWith(input))

                            .collect(Collectors.toList());

                }

                case "bank", "kasse" -> {

                    completions = Arrays.asList("deposit", "withdraw", "info", "transaktionen").stream()

                            .filter(s -> s.startsWith(input))

                            .collect(Collectors.toList());

                }

            }

        } else if (args.length == 3) {

            String subCommand = args[0].toLowerCase();

            String input = args[2].toLowerCase();



            if (subCommand.equals("ally") || subCommand.equals("war")) {

                completions = plugin.getClanManager().getAllClans().stream()

                        .map(Clan::getName)

                        .filter(n -> n.toLowerCase().startsWith(input))

                        .collect(Collectors.toList());

            } else if (subCommand.equals("rank") && args[1].equalsIgnoreCase("set")) {



                Clan clan = plugin.getClanManager().getClanByPlayer(player);

                if (clan != null) {

                    completions = clan.getMembers().values().stream()

                            .map(ClanMember::getName)

                            .filter(n -> n.toLowerCase().startsWith(input))

                            .collect(Collectors.toList());

                }

            }

        } else if (args.length == 4) {

            if (args[0].equalsIgnoreCase("rank") && args[1].equalsIgnoreCase("set")) {

                Clan clan = plugin.getClanManager().getClanByPlayer(player);

                if (clan != null) {

                    String input = args[3].toLowerCase();

                    completions = clan.getRanks().values().stream()

                            .map(ClanRank::getName)

                            .filter(n -> n.toLowerCase().startsWith(input))

                            .collect(Collectors.toList());

                }

            }

        }



        return completions;

    }

}

