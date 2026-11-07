package de.Z7534.ztone.stoneattack.commands;

import de.Z7534.ztone.stoneattack.Stoneattack;
import de.Z7534.ztone.stoneattack.modules.leaderboard.LeaderboardModule;
import de.Z7534.ztone.stoneattack.modules.leaderboard.LeaderboardType;
import de.Z7534.ztone.stoneattack.modules.leaderboard.TimePeriod;
import de.Z7534.ztone.stoneattack.modules.leaderboard.hologram.HologramDefinition;
import de.Z7534.ztone.stoneattack.modules.leaderboard.hologram.HologramLayout;
import de.Z7534.ztone.stoneattack.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Befehl /leaderboard zum Platzieren, Verschieben, Hoehe einstellen und Verwalten.
 */
public class LeaderboardCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "place", "move", "height", "remove", "removeplayer", "addplayer", "list", "tp", "reload", "reset"
    );

    private static final List<String> TYPES = Arrays.stream(LeaderboardType.values())
            .map(LeaderboardType::key)
            .toList();

    private static final List<String> FACINGS = List.of("north", "south", "east", "west");

    private final Stoneattack plugin;

    public LeaderboardCommand(Stoneattack plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("stoneattack.admin")) {
            plugin.getMessagesManager().send(sender, "general.no-permission");
            return true;
        }

        LeaderboardModule module = plugin.getLeaderboardModule();
        if (module == null) {
            sender.sendMessage(ColorUtil.parse("<red>Das Leaderboard-Modul ist auf diesem Server deaktiviert.</red>"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "place" -> handlePlace(sender, module, args);
            case "move" -> handleMove(sender, module, args);
            case "height" -> handleHeight(sender, module, args);
            case "remove" -> handleRemove(sender, module, args);
            case "removeplayer" -> handleRemovePlayer(sender, module, args);
            case "addplayer" -> handleAddPlayer(sender, module, args);
            case "list" -> handleList(sender, module);
            case "tp" -> handleTp(sender, module, args);
            case "reload" -> handleReload(sender, module);
            case "reset" -> handleReset(sender, module, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handlePlace(CommandSender sender, LeaderboardModule module, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.parse("<red>Nutzung: /leaderboard place <leaderboard> [<welt> <x> <y> <z> <north|south|east|west>]</red>"));
            return;
        }
        LeaderboardType type = LeaderboardType.fromKey(args[1]);
        if (type == null) {
            plugin.getMessagesManager().send(sender, "leaderboard.invalid-type", Map.of("input", args[1]));
            return;
        }

        WallTarget target;
        if (args.length >= 7) {
            target = parseWorldTarget(sender, args, 2);
        } else if (args.length == 6 && sender instanceof Player inWorld) {
            target = parseCoords(sender, inWorld.getWorld(), args, 2);
        } else if (sender instanceof Player self) {
            target = targetedWall(self);
        } else {
            sender.sendMessage(ColorUtil.parse("<red>Konsole muss Koordinaten angeben: /leaderboard place <lb> <welt> <x> <y> <z> <dir></red>"));
            return;
        }
        if (target == null) {
            return;
        }

        double defaultHeight = plugin.getConfigManager().get().getDouble("leaderboard.default-height-blocks", 3.0);
        HologramDefinition def = new HologramDefinition(type, target.world().getName(),
                target.x(), target.y(), target.z(), target.face(), defaultHeight);

        module.getPositionStore().set(type, def);
        module.getHologramManager().reload();

        plugin.getMessagesManager().send(sender, "leaderboard.place-success", Map.of(
                "leaderboard", type.key(),
                "world", target.world().getName(),
                "x", String.valueOf(target.x()),
                "y", String.valueOf(target.y()),
                "z", String.valueOf(target.z()),
                "direction", target.face().name().toLowerCase()));
    }

    private void handleMove(CommandSender sender, LeaderboardModule module, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ColorUtil.parse("<red>Nutzung: /leaderboard move <leaderboard> movetoplayer | [<welt>] <x> <y> <z> <north|south|east|west>]</red>"));
            return;
        }
        LeaderboardType type = LeaderboardType.fromKey(args[1]);
        if (type == null) {
            plugin.getMessagesManager().send(sender, "leaderboard.invalid-type", Map.of("input", args[1]));
            return;
        }
        HologramDefinition existing = module.getPositionStore().get(type);
        if (existing == null) {
            plugin.getMessagesManager().send(sender, "leaderboard.remove-not-found", Map.of("leaderboard", type.key()));
            return;
        }

        WallTarget target;
        if (args[2].equalsIgnoreCase("movetoplayer") || args[2].equalsIgnoreCase("move-to-player")) {
            if (!(sender instanceof Player self)) {
                plugin.getMessagesManager().send(sender, "general.player-only");
                return;
            }
            target = targetedWall(self);
        } else if (args.length >= 7) {
            target = parseWorldTarget(sender, args, 2);
        } else if (args.length == 6 && sender instanceof Player inWorld) {
            target = parseCoords(sender, inWorld.getWorld(), args, 2);
        } else {
            sender.sendMessage(ColorUtil.parse("<red>Nutzung: /leaderboard move <lb> movetoplayer  oder  /leaderboard move <lb> [<welt>] <x> <y> <z> <dir></red>"));
            return;
        }
        if (target == null) {
            return;
        }

        existing.update(target.world().getName(), target.x(), target.y(), target.z(), target.face());
        module.getPositionStore().set(type, existing);
        module.getHologramManager().reload();
        plugin.getMessagesManager().send(sender, "leaderboard.move-success", Map.of(
                "leaderboard", type.key(),
                "world", target.world().getName(),
                "x", String.valueOf(target.x()),
                "y", String.valueOf(target.y()),
                "z", String.valueOf(target.z()),
                "direction", target.face().name().toLowerCase()));
    }

    private void handleHeight(CommandSender sender, LeaderboardModule module, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ColorUtil.parse("<red>Nutzung: /leaderboard height <leaderboard> <hoehe-in-bloecken></red>"));
            return;
        }
        LeaderboardType type = LeaderboardType.fromKey(args[1]);
        if (type == null) {
            plugin.getMessagesManager().send(sender, "leaderboard.invalid-type", Map.of("input", args[1]));
            return;
        }
        HologramDefinition def = module.getPositionStore().get(type);
        if (def == null) {
            plugin.getMessagesManager().send(sender, "leaderboard.remove-not-found", Map.of("leaderboard", type.key()));
            return;
        }

        double height;
        try {
            height = Double.parseDouble(args[2].replace(',', '.'));
        } catch (NumberFormatException e) {
            plugin.getMessagesManager().send(sender, "leaderboard.height-invalid", Map.of(
                    "min", trim(HologramLayout.MIN_HEIGHT),
                    "max", trim(HologramLayout.MAX_HEIGHT)));
            return;
        }

        if (height < HologramLayout.MIN_HEIGHT || height > HologramLayout.MAX_HEIGHT) {
            plugin.getMessagesManager().send(sender, "leaderboard.height-invalid", Map.of(
                    "min", trim(HologramLayout.MIN_HEIGHT),
                    "max", trim(HologramLayout.MAX_HEIGHT)));
            return;
        }

        def.setHeightBlocks(height);
        module.getPositionStore().set(type, def);
        module.getHologramManager().reload();

        plugin.getMessagesManager().send(sender, "leaderboard.height-success", Map.of(
                "leaderboard", type.key(),
                "height", trim(module.getHologramManager().effectiveHeight(def.getHeightBlocks())),
                "entries", String.valueOf(module.getHologramManager().entriesForHeight(def.getHeightBlocks()))));
    }

    private static String trim(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private WallTarget targetedWall(Player player) {
        double reach = plugin.getConfigManager().get().getDouble("leaderboard.placement.max-distance", 8.0);
        RayTraceResult result = player.rayTraceBlocks(reach);
        Block block = result == null ? null : result.getHitBlock();
        BlockFace face = result == null ? null : result.getHitBlockFace();
        if (block == null || face == null) {
            plugin.getMessagesManager().send(player, "leaderboard.place-no-wall",
                    Map.of("distance", String.valueOf((int) reach)));
            return null;
        }
        if (face == BlockFace.UP) {
            BlockFace facing = player.getFacing().getOppositeFace();
            if (!HologramDefinition.isWallFace(facing)) {
                facing = BlockFace.SOUTH;
            }
            return new WallTarget(block.getWorld(), block.getX(), block.getY() + 1, block.getZ(), facing);
        }
        if (!HologramDefinition.isWallFace(face)) {
            plugin.getMessagesManager().send(player, "leaderboard.place-not-a-wall");
            return null;
        }
        return new WallTarget(block.getWorld(), block.getX(), block.getY(), block.getZ(), face);
    }

    private WallTarget parseWorldTarget(CommandSender sender, String[] args, int start) {
        World world = Bukkit.getWorld(args[start]);
        if (world == null) {
            sender.sendMessage(ColorUtil.parse("<red>Unbekannte Welt: " + args[start] + "</red>"));
            return null;
        }
        return parseCoords(sender, world, args, start + 1);
    }

    private WallTarget parseCoords(CommandSender sender, World world, String[] args, int start) {
        try {
            int x = Integer.parseInt(args[start]);
            int y = Integer.parseInt(args[start + 1]);
            int z = Integer.parseInt(args[start + 2]);
            BlockFace face = parseFace(args[start + 3]);
            if (face == null) {
                sender.sendMessage(ColorUtil.parse("<red>Ungueltige Richtung: " + args[start + 3] + " (erlaubt: north, south, east, west)</red>"));
                return null;
            }
            return new WallTarget(world, x, y, z, face);
        } catch (Exception e) {
            sender.sendMessage(ColorUtil.parse("<red>Ungueltige Koordinaten. Bitte ganze Zahlen angeben.</red>"));
            return null;
        }
    }

    private BlockFace parseFace(String name) {
        try {
            BlockFace f = BlockFace.valueOf(name.toUpperCase());
            return HologramDefinition.isWallFace(f) ? f : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void handleRemove(CommandSender sender, LeaderboardModule module, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.parse("<red>Nutzung: /leaderboard remove <leaderboard></red>"));
            return;
        }
        LeaderboardType type = LeaderboardType.fromKey(args[1]);
        if (type == null) {
            plugin.getMessagesManager().send(sender, "leaderboard.invalid-type", Map.of("input", args[1]));
            return;
        }
        if (module.getPositionStore().get(type) == null) {
            plugin.getMessagesManager().send(sender, "leaderboard.remove-not-found", Map.of("leaderboard", type.key()));
            return;
        }
        module.getPositionStore().remove(type);
        module.getHologramManager().removeBoard(type);
        plugin.getMessagesManager().send(sender, "leaderboard.remove-success", Map.of("leaderboard", type.key()));
    }

    private void handleRemovePlayer(CommandSender sender, LeaderboardModule module, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.parse("<red>Nutzung: /leaderboard removeplayer <spieler> [<leaderboard>]</red>"));
            return;
        }
        UUID target = resolvePlayer(args[1]);
        if (target == null) {
            plugin.getMessagesManager().send(sender, "leaderboard.player-not-found", Map.of("player", args[1]));
            return;
        }
        LeaderboardType type = args.length >= 3 ? LeaderboardType.fromKey(args[2]) : null;
        if (args.length >= 3 && type == null) {
            plugin.getMessagesManager().send(sender, "leaderboard.invalid-type", Map.of("input", args[2]));
            return;
        }

        module.getExclusionService().exclude(target, type);
        module.getCache().forceRefresh();
        module.getHologramManager().reload();

        String lbName = type == null ? "alle" : type.key();
        plugin.getMessagesManager().send(sender, "leaderboard.exclude-success", Map.of(
                "player", args[1],
                "leaderboard", lbName));
    }

    private void handleAddPlayer(CommandSender sender, LeaderboardModule module, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.parse("<red>Nutzung: /leaderboard addplayer <spieler> [<leaderboard>]</red>"));
            return;
        }
        UUID target = resolvePlayer(args[1]);
        if (target == null) {
            plugin.getMessagesManager().send(sender, "leaderboard.player-not-found", Map.of("player", args[1]));
            return;
        }
        LeaderboardType type = args.length >= 3 ? LeaderboardType.fromKey(args[2]) : null;
        if (args.length >= 3 && type == null) {
            plugin.getMessagesManager().send(sender, "leaderboard.invalid-type", Map.of("input", args[2]));
            return;
        }

        module.getExclusionService().include(target, type);
        module.getCache().forceRefresh();
        module.getHologramManager().reload();

        String lbName = type == null ? "alle" : type.key();
        plugin.getMessagesManager().send(sender, "leaderboard.include-success", Map.of(
                "player", args[1],
                "leaderboard", lbName));
    }

    private UUID resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            return offline.getUniqueId();
        }
        return null;
    }

    private void handleList(CommandSender sender, LeaderboardModule module) {
        Map<LeaderboardType, HologramDefinition> all = module.getPositionStore().all();
        if (all.isEmpty()) {
            plugin.getMessagesManager().send(sender, "leaderboard.list-empty");
            return;
        }
        plugin.getMessagesManager().send(sender, "leaderboard.list-header");
        for (Map.Entry<LeaderboardType, HologramDefinition> e : all.entrySet()) {
            HologramDefinition def = e.getValue();
            plugin.getMessagesManager().send(sender, "leaderboard.list-entry", Map.of(
                    "leaderboard", e.getKey().key(),
                    "world", def.getWorldName(),
                    "x", String.valueOf(def.getX()),
                    "y", String.valueOf(def.getY()),
                    "z", String.valueOf(def.getZ()),
                    "direction", def.getFacing().name().toLowerCase(),
                    "height", trim(module.getHologramManager().effectiveHeight(def.getHeightBlocks())),
                    "entries", String.valueOf(module.getHologramManager().entriesForHeight(def.getHeightBlocks()))));
        }
    }

    private void handleTp(CommandSender sender, LeaderboardModule module, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessagesManager().send(sender, "general.player-only");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.parse("<red>Nutzung: /leaderboard tp <leaderboard></red>"));
            return;
        }
        LeaderboardType type = LeaderboardType.fromKey(args[1]);
        if (type == null) {
            plugin.getMessagesManager().send(sender, "leaderboard.invalid-type", Map.of("input", args[1]));
            return;
        }
        HologramDefinition def = module.getPositionStore().get(type);
        if (def == null) {
            plugin.getMessagesManager().send(sender, "leaderboard.remove-not-found", Map.of("leaderboard", type.key()));
            return;
        }
        Location target = def.frame().standingSpot();
        player.teleport(target);
        plugin.getMessagesManager().send(sender, "leaderboard.tp-success", Map.of("leaderboard", type.key()));
    }

    private void handleReload(CommandSender sender, LeaderboardModule module) {
        module.getHologramManager().reload();
        plugin.getMessagesManager().send(sender, "leaderboard.reload-success");
    }

    private void handleReset(CommandSender sender, LeaderboardModule module, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.parse("<red>Nutzung: /leaderboard reset <all|woche|monat></red>"));
            return;
        }
        String periodArg = args[1].toLowerCase();
        if (periodArg.equals("all")) {
            module.getPeriodResetManager().manualReset(TimePeriod.WEEK);
            module.getPeriodResetManager().manualReset(TimePeriod.MONTH);
            module.getCache().forceRefresh();
            module.getHologramManager().reload();
            plugin.getMessagesManager().send(sender, "leaderboard.reset-all-success");
            return;
        }
        TimePeriod period = null;
        if (periodArg.equals("woche") || periodArg.equals("week")) {
            period = TimePeriod.WEEK;
        } else if (periodArg.equals("monat") || periodArg.equals("month")) {
            period = TimePeriod.MONTH;
        }
        if (period == null || period == TimePeriod.GLOBAL) {
            sender.sendMessage(ColorUtil.parse("<red>Gueltige Zeitraeume: woche, monat, all</red>"));
            return;
        }
        module.getPeriodResetManager().manualReset(period);
        module.getCache().forceRefresh();
        module.getHologramManager().reload();
        plugin.getMessagesManager().send(sender, "leaderboard.reset-type-success", Map.of("leaderboard", period.key()));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ColorUtil.parse("<gradient:#00e5ff:#00a3cc><b>Stoneattack Leaderboard</b></gradient>"));
        sender.sendMessage(ColorUtil.parse("<gray>/leaderboard place <lb> [<welt> <x> <y> <z> <dir>]</gray> <dark_gray>-</dark_gray> <white>Platzieren</white>"));
        sender.sendMessage(ColorUtil.parse("<gray>/leaderboard move <lb> movetoplayer | [<welt>] <x> <y> <z> <dir></gray> <dark_gray>-</dark_gray> <white>Verschieben</white>"));
        sender.sendMessage(ColorUtil.parse("<gray>/leaderboard height <lb> <bloecke></gray> <dark_gray>-</dark_gray> <white>Hoehe aendern</white>"));
        sender.sendMessage(ColorUtil.parse("<gray>/leaderboard remove <lb></gray> <dark_gray>-</dark_gray> <white>Entfernen</white>"));
        sender.sendMessage(ColorUtil.parse("<gray>/leaderboard removeplayer <spieler> [<lb>]</gray> <dark_gray>-</dark_gray> <white>Ausschliessen</white>"));
        sender.sendMessage(ColorUtil.parse("<gray>/leaderboard addplayer <spieler> [<lb>]</gray> <dark_gray>-</dark_gray> <white>Wieder aufnehmen</white>"));
        sender.sendMessage(ColorUtil.parse("<gray>/leaderboard list</gray> <dark_gray>-</dark_gray> <white>Alle Boards auflisten</white>"));
        sender.sendMessage(ColorUtil.parse("<gray>/leaderboard tp <lb></gray> <dark_gray>-</dark_gray> <white>Hin teleportieren</white>"));
        sender.sendMessage(ColorUtil.parse("<gray>/leaderboard reload</gray> <dark_gray>-</dark_gray> <white>Config & Displays neu laden</white>"));
        sender.sendMessage(ColorUtil.parse("<gray>/leaderboard reset <all|woche|monat></gray> <dark_gray>-</dark_gray> <white>Statistiken zuruecksetzen</white>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("stoneattack.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, new ArrayList<>());
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            return switch (sub) {
                case "place", "move", "height", "remove", "tp" -> StringUtil.copyPartialMatches(args[1], TYPES, new ArrayList<>());
                case "reset" -> {
                    List<String> list = List.of("all", "woche", "monat");
                    yield StringUtil.copyPartialMatches(args[1], list, new ArrayList<>());
                }
                case "removeplayer", "addplayer" -> null; // Online-Spieler
                default -> List.of();
            };
        }
        if (args.length == 3 && sub.equals("height")) {
            return StringUtil.copyPartialMatches(args[2], List.of("3", "4", "5", "6", "7", "8", "9", "10", "11", "12"), new ArrayList<>());
        }
        if (args.length == 3 && sub.equals("move")) {
            return StringUtil.copyPartialMatches(args[2], List.of("movetoplayer"), new ArrayList<>());
        }
        if (args.length == 3 && sub.equals("place")) {
            return StringUtil.copyPartialMatches(args[2], Bukkit.getWorlds().stream().map(World::getName).toList(), new ArrayList<>());
        }
        if (args.length == 3 && (sub.equals("removeplayer") || sub.equals("addplayer"))) {
            return StringUtil.copyPartialMatches(args[2], TYPES, new ArrayList<>());
        }
        // Richtung als letztes Argument: place <lb> <welt> <x> <y> <z> <dir>  bzw. move <lb> [<welt>] <x> <y> <z> <dir>
        if ((sub.equals("place") && args.length == 7)
                || (sub.equals("move") && (args.length == 6 || args.length == 7))) {
            return StringUtil.copyPartialMatches(args[args.length - 1], FACINGS, new ArrayList<>());
        }
        return List.of();
    }

    private record WallTarget(World world, int x, int y, int z, BlockFace face) {}
}
