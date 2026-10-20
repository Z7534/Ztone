package de.Z7534.ztone.stoneattack.commands;

import de.Z7534.ztone.stoneattack.Stoneattack;
import de.Z7534.ztone.stoneattack.modules.leaderboard.LeaderboardModule;
import de.Z7534.ztone.stoneattack.modules.leaderboard.LeaderboardType;
import de.Z7534.ztone.stoneattack.modules.leaderboard.TimePeriod;
import de.Z7534.ztone.stoneattack.modules.leaderboard.hologram.HologramDefinition;
import de.Z7534.ztone.stoneattack.modules.leaderboard.hologram.HologramLayout;
import de.Z7534.ztone.stoneattack.util.ColorUtil;
import org.bukkit.Bukkit;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LeaderboardCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("place", "move", "height", "remove", "removeplayer",
            "addplayer", "excluded", "list", "tp", "reload", "reset");
    private static final List<String> TYPES = List.of("playtime", "kills", "deaths", "money");
    private static final List<String> TYPES_WITH_ALL = List.of("playtime", "kills", "deaths", "money", "all");
    private static final List<String> DIRECTIONS = List.of("north", "south", "east", "west");

    /** Wandblock + Seite, an der das Leaderboard haengen soll. */
    private record WallTarget(World world, int x, int y, int z, BlockFace face) {
    }

    private final Stoneattack plugin;

    public LeaderboardCommand(Stoneattack plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("leaderboard.admin")) {
            plugin.getMessagesManager().send(sender, "general.no-permission");
            return true;
        }
        LeaderboardModule module = plugin.getLeaderboardModule();
        if (module == null) {
            sender.sendMessage(ColorUtil.parse("<red>Das Leaderboard-Modul ist deaktiviert (siehe config.yml).</red>"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ColorUtil.parse("<red>Nutzung: /leaderboard <place|move|height|remove|removeplayer|addplayer|excluded|list|tp|reload|reset></red>"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "place" -> handlePlace(sender, module, args);
            case "move" -> handleMove(sender, module, args);
            case "height" -> handleHeight(sender, module, args);
            case "remove" -> handleRemove(sender, module, args);
            case "removeplayer" -> handleExcludeChange(sender, module, args, true);
            case "addplayer" -> handleExcludeChange(sender, module, args, false);
            case "excluded" -> handleExcludedList(sender, module, args);
            case "list" -> handleList(sender, module);
            case "tp" -> handleTeleport(sender, module, args);
            case "reload" -> handleReload(sender, module);
            case "reset" -> handleReset(sender, module, args);
            default -> plugin.getMessagesManager().send(sender, "general.unknown-subcommand",
                    Map.of("usage", "/leaderboard <place|move|height|remove|removeplayer|addplayer|excluded|list|tp|reload|reset>"));
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
        } else if (sender instanceof Player player) {
            target = targetedWall(player);
        } else {
            plugin.getMessagesManager().send(sender, "leaderboard.console-needs-coords");
            return;
        }
        if (target == null) {
            return;
        }

        HologramDefinition previous = module.getPositionStore().get(type);
        double height = previous != null ? previous.getHeightBlocks() : module.getPositionStore().defaultHeight();

        HologramDefinition def = new HologramDefinition(type, target.world().getName(),
                target.x(), target.y(), target.z(), target.face(), height);
        module.getPositionStore().set(type, def);
        module.getHologramManager().reload();
        plugin.getMessagesManager().send(sender, "leaderboard.place-success", Map.of(
                "leaderboard", type.key(),
                "direction", target.face().name().toLowerCase()));
    }

    private void handleMove(CommandSender sender, LeaderboardModule module, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ColorUtil.parse("<red>Nutzung: /leaderboard move <leaderboard> movetoplayer | [<welt>] <x> <y> <z> <north|south|east|west></red>"));
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
            target = parseBlockTarget(sender, inWorld.getWorld(), args, 2);
        } else {
            plugin.getMessagesManager().send(sender, "leaderboard.console-needs-coords");
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
                "direction", target.face().name().toLowerCase()));
    }

    private void handleHeight(CommandSender sender, LeaderboardModule module, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ColorUtil.parse("<red>Nutzung: /leaderboard height <leaderboard> <ganze bloecke></red>"));
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
                    "input", args[2],
                    "min", trim(HologramLayout.MIN_HEIGHT),
                    "max", trim(HologramLayout.MAX_HEIGHT)));
            return;
        }
        if (height < HologramLayout.MIN_HEIGHT || height > HologramLayout.MAX_HEIGHT) {
            plugin.getMessagesManager().send(sender, "leaderboard.height-invalid", Map.of(
                    "input", args[2],
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
        return parseBlockTarget(sender, world, args, start + 1);
    }

    private WallTarget parseBlockTarget(CommandSender sender, World world, String[] args, int start) {
        BlockFace face;
        try {
            face = BlockFace.valueOf(args[start + 3].toUpperCase());
        } catch (IllegalArgumentException e) {
            face = null;
        }
        if (face == null || !HologramDefinition.isWallFace(face)) {
            plugin.getMessagesManager().send(sender, "leaderboard.invalid-direction", Map.of("input", args[start + 3]));
            return null;
        }
        try {
            int x = (int) Math.floor(Double.parseDouble(args[start]));
            int y = (int) Math.floor(Double.parseDouble(args[start + 1]));
            int z = (int) Math.floor(Double.parseDouble(args[start + 2]));
            return new WallTarget(world, x, y, z, face);
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtil.parse("<red>Ungueltige Koordinaten.</red>"));
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
        module.getHologramManager().despawnType(type);
        module.getPositionStore().remove(type);
        plugin.getMessagesManager().send(sender, "leaderboard.remove-success", Map.of("leaderboard", type.key()));
    }

    private void handleExcludeChange(CommandSender sender, LeaderboardModule module, String[] args, boolean exclude) {
        if (args.length < 3) {
            sender.sendMessage(ColorUtil.parse("<red>Nutzung: /leaderboard " + args[0] + " <spieler> <leaderboard|all></red>"));
            return;
        }
        OfflinePlayer target = resolveKnownPlayer(args[1]);
        if (target == null) {
            plugin.getMessagesManager().send(sender, "general.player-not-found", Map.of("player", args[1]));
            return;
        }
        if (args[2].equalsIgnoreCase("all")) {
            if (exclude) module.getExclusionService().excludeFromAll(target.getUniqueId());
            else module.getExclusionService().includeInAll(target.getUniqueId());
        } else {
            LeaderboardType type = LeaderboardType.fromKey(args[2]);
            if (type == null) {
                plugin.getMessagesManager().send(sender, "leaderboard.invalid-type-with-all", Map.of("input", args[2]));
                return;
            }
            if (exclude) module.getExclusionService().exclude(target.getUniqueId(), type);
            else module.getExclusionService().include(target.getUniqueId(), type);
        }

        String messageKey = exclude ? "leaderboard.excluded-add-success" : "leaderboard.excluded-remove-success";
        plugin.getMessagesManager().send(sender, messageKey, Map.of(
                "player", target.getName() != null ? target.getName() : args[1],
                "leaderboard", args[2].toLowerCase()
        ));
    }

    private void handleExcludedList(CommandSender sender, LeaderboardModule module, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.parse("<red>Nutzung: /leaderboard excluded <leaderboard></red>"));
            return;
        }
        LeaderboardType type = LeaderboardType.fromKey(args[1]);
        if (type == null) {
            plugin.getMessagesManager().send(sender, "leaderboard.invalid-type", Map.of("input", args[1]));
            return;
        }
        var excluded = module.getExclusionService().getExcluded(type);
        plugin.getMessagesManager().send(sender, "leaderboard.excluded-list-header", Map.of("leaderboard", type.key()));
        if (excluded.isEmpty()) {
            plugin.getMessagesManager().send(sender, "leaderboard.excluded-list-empty");
            return;
        }
        for (UUID uuid : excluded) {
            OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
            sender.sendMessage(plugin.getMessagesManager().get("leaderboard.excluded-list-entry",
                    Map.of("player", p.getName() != null ? p.getName() : uuid.toString())));
        }
    }

    private void handleList(CommandSender sender, LeaderboardModule module) {
        plugin.getMessagesManager().send(sender, "leaderboard.list-header");
        for (LeaderboardType type : LeaderboardType.values()) {
            HologramDefinition def = module.getPositionStore().get(type);
            if (def == null) {
                sender.sendMessage(plugin.getMessagesManager().get("leaderboard.list-entry-none", Map.of("leaderboard", type.key())));
            } else {
                sender.sendMessage(plugin.getMessagesManager().get("leaderboard.list-entry", Map.of(
                        "leaderboard", type.key(),
                        "world", def.getWorldName(),
                        "x", String.valueOf(def.getX()),
                        "y", String.valueOf(def.getY()),
                        "z", String.valueOf(def.getZ()),
                        "direction", def.getFacing().name().toLowerCase(),
                        "height", trim(module.getHologramManager().effectiveHeight(def.getHeightBlocks())),
                        "entries", String.valueOf(module.getHologramManager().entriesForHeight(def.getHeightBlocks()))
                )));
            }
        }
    }

    private void handleTeleport(CommandSender sender, LeaderboardModule module, String[] args) {
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
        if (module.getPositionStore().get(type) == null) {
            plugin.getMessagesManager().send(sender, "leaderboard.tp-not-found", Map.of("leaderboard", type.key()));
            return;
        }
        module.getHologramManager().teleportPlayer(player, type);
        plugin.getMessagesManager().send(sender, "leaderboard.tp-success", Map.of("leaderboard", type.key()));
    }

    private void handleReload(CommandSender sender, LeaderboardModule module) {
        plugin.getConfigManager().reload();
        plugin.getMessagesManager().reload();
        module.reload();
        plugin.getMessagesManager().send(sender, "leaderboard.reload-success");
    }

    private void handleReset(CommandSender sender, LeaderboardModule module, String[] args) {
        if (args.length < 2 || (!args[1].equalsIgnoreCase("week") && !args[1].equalsIgnoreCase("month"))) {
            plugin.getMessagesManager().send(sender, "leaderboard.reset-invalid-period");
            return;
        }
        TimePeriod period = args[1].equalsIgnoreCase("week") ? TimePeriod.WEEK : TimePeriod.MONTH;
        Bukkit.getScheduler().runTaskAsynchronously(plugin.getPlugin(), () -> {
            module.getPeriodResetManager().manualReset(period);
            Bukkit.getScheduler().runTask(plugin.getPlugin(), () -> {
                module.getCache().forceRefresh();
                plugin.getMessagesManager().send(sender, "leaderboard.reset-success", Map.of("period", period.key()));
            });
        });
    }

    // ------------------------------------------------------------

    private OfflinePlayer resolveKnownPlayer(String name) {
        for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
            if (name.equalsIgnoreCase(p.getName())) {
                return p;
            }
        }
        Player online = Bukkit.getPlayerExact(name);
        return online;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return CommandUtil.filterStartingWith(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            return switch (sub) {
                case "place", "move", "height", "remove", "excluded", "tp" -> CommandUtil.filterStartingWith(TYPES, args[1]);
                case "removeplayer", "addplayer" -> new ArrayList<>();
                case "reset" -> CommandUtil.filterStartingWith(List.of("week", "month"), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3 && sub.equals("height")) {
            return CommandUtil.filterStartingWith(List.of("3", "4", "5", "6", "7", "8"), args[2]);
        }
        if (args.length == 3 && (sub.equals("removeplayer") || sub.equals("addplayer"))) {
            return CommandUtil.filterStartingWith(TYPES_WITH_ALL, args[2]);
        }
        if (args.length == 3 && sub.equals("move")) {
            List<String> options = new ArrayList<>(List.of("movetoplayer"));
            Bukkit.getWorlds().forEach(w -> options.add(w.getName()));
            return CommandUtil.filterStartingWith(options, args[2]);
        }
        if (args.length == 3 && sub.equals("place")) {
            List<String> options = new ArrayList<>();
            Bukkit.getWorlds().forEach(w -> options.add(w.getName()));
            return CommandUtil.filterStartingWith(options, args[2]);
        }
        // Richtung als letztes Argument: place <lb> <welt> <x> <y> <z> <dir>  bzw. move <lb> [<welt>] <x> <y> <z> <dir>
        if ((sub.equals("place") && args.length == 7)
                || (sub.equals("move") && (args.length == 6 || args.length == 7))) {
            return CommandUtil.filterStartingWith(DIRECTIONS, args[args.length - 1]);
        }
        return List.of();
    }
}
