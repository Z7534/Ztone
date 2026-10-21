package de.Z7534.ztone.claims.command;

import de.Z7534.ztone.claims.ClaimsContext;
import de.Z7534.ztone.claims.manager.BypassManager;
import de.Z7534.ztone.claims.manager.PvPClaimBypassManager;
import de.Z7534.ztone.claims.model.ChunkPos;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.model.ClaimPermission;
import de.Z7534.ztone.claims.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;
import de.Z7534.ztone.claims.model.PlayerData;

public final class StaffClaimCommand implements CommandExecutor, TabCompleter {

    private final ClaimsContext ctx;
    private final PvPClaimBypassCommand pvpBypassCommand;

    public StaffClaimCommand(ClaimsContext ctx) {
        this.ctx = ctx;
        this.pvpBypassCommand = new PvPClaimBypassCommand(ctx);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§cVerwendung: /staffclaim <addclaims|removeclaims|info|tp|unclaim|unclaimhere|unclaimhereonly|trust|ban|bypass|pvp|reload> ...");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "addclaims" -> handleAddRemove(sender, args, true);
            case "removeclaims" -> handleAddRemove(sender, args, false);
            case "info" -> handleInfo(sender, args);
            case "tp" -> handleTeleport(sender, args);
            case "unclaim" -> handleUnclaim(sender, args);
            case "unclaimhere" -> handleUnclaimHere(sender);
            case "unclaimhereonly" -> handleUnclaimHereOnly(sender);
            case "trust" -> handleTrustOrBan(sender, args, true);
            case "ban" -> handleTrustOrBan(sender, args, false);
            case "bypass" -> handleBypass(sender, args);
            case "pvp", "pvpclaimbypass", "claimspvpoverride" -> pvpBypassCommand.onCommand(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
            case "reload" -> handleReload(sender);
            default -> sender.sendMessage("§cUnbekannter Unterbefehl.");
        }
        return true;
    }

    private boolean requirePerm(CommandSender sender, String perm) {
        if (!sender.hasPermission(perm)) {
            sender.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("no-permission")));
            return false;
        }
        return true;
    }

    private void handleBypass(CommandSender sender, String[] args) {
        if (!requirePerm(sender, BypassManager.PERMISSION)) return;

        // /staffclaim bypass <on|off> <spieler>
        if (args.length >= 3) {
            if (!requirePerm(sender, "claims.admin.bypass.other")) return;
            String mode = args[1].toLowerCase(Locale.ROOT);
            boolean enable;
            if (mode.equals("on") || mode.equals("an")) {
                enable = true;
            } else if (mode.equals("off") || mode.equals("aus")) {
                enable = false;
            } else {
                sender.sendMessage("§cVerwendung: /staffclaim bypass <on|off> <spieler>");
                return;
            }
            String targetName = args[2];
            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                sender.sendMessage("§cSpieler nicht online oder nicht gefunden: " + targetName);
                return;
            }
            ctx.bypass().setExplicitActive(target.getUniqueId(), enable);
            sender.sendMessage("§aBypass für " + target.getName() + " wurde " + (enable ? "aktiviert" : "deaktiviert") + ".");
            target.sendMessage(Text.line(ctx.config().prefix()
                    + ctx.config().message(enable ? "bypass-enabled" : "bypass-disabled")));
            return;
        }

        // Eigener Bypass
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cVerwendung für Konsole: /staffclaim bypass <on|off> <spieler>");
            return;
        }

        boolean enable;
        if (args.length >= 2) {
            String mode = args[1].toLowerCase(Locale.ROOT);
            if (mode.equals("on") || mode.equals("an")) {
                enable = true;
            } else if (mode.equals("off") || mode.equals("aus")) {
                enable = false;
            } else {
                sender.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("bypass-usage")));
                return;
            }
            ctx.bypass().setActive(player.getUniqueId(), enable);
        } else {
            enable = ctx.bypass().toggle(player);
        }

        sender.sendMessage(Text.line(ctx.config().prefix()
                + ctx.config().message(enable ? "bypass-enabled" : "bypass-disabled")));
    }

    private void handleAddRemove(CommandSender sender, String[] args, boolean add) {
        String perm = add ? "claims.admin.addclaims" : "claims.admin.removeclaims";
        if (!requirePerm(sender, perm)) return;
        if (args.length < 3) {
            sender.sendMessage("§cVerwendung: /staffclaim " + args[0] + " <spieler> <zahl>");
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cUngültige Zahl: " + args[2]);
            return;
        }
        if (amount <= 0) {
            sender.sendMessage("§cDie Zahl muss größer als 0 sein.");
            return;
        }
        String name = args[1];
        ctx.lookup().resolve(name).thenAccept(opt -> Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
            if (opt.isEmpty()) {
                sender.sendMessage("§cSpieler nicht gefunden: " + name);
                return;
            }
            var resolved = opt.get();
            ctx.claims().ensurePlayerData(resolved.uuid(), resolved.name());
            var pd = ctx.claims().findPlayerData(resolved.uuid()).orElseThrow();
            long updated = (long) pd.bonusClaimSlots() + (add ? amount : -(long) amount);
            // Gegen Überlauf absichern (sonst würde ein zu großer Wert negativ werden und auf 0 fallen).
            pd.setBonusClaimSlots((int) Math.max(0L, Math.min(Integer.MAX_VALUE, updated)));
            ctx.claims().savePlayerAsync(pd);
            sender.sendMessage("§a" + resolved.name() + " hat jetzt " + pd.bonusClaimSlots() + " Bonus-Claim-Slots.");
        }));
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (!requirePerm(sender, "claims.admin.info")) return;
        if (args.length < 2) {
            sender.sendMessage("§cVerwendung: /staffclaim info <spieler>");
            return;
        }
        String name = args[1];
        ctx.lookup().resolve(name).thenAccept(opt -> Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
            if (opt.isEmpty()) {
                sender.sendMessage("§cSpieler nicht gefunden: " + name);
                return;
            }
            var resolved = opt.get();
            List<Claim> claims = ctx.claims().claimsOf(resolved.uuid());
            sender.sendMessage("§6Claims von " + resolved.name() + " (" + claims.size() + "):");
            int i = 1;
            for (Claim c : claims) {
                int[] box = c.boundingBox();
                sender.sendMessage("§7#" + i + " §f" + c.name() + " §7- Welt: " + c.world()
                        + " - Chunks: " + c.chunks().size() + " (" + box[4] + "x" + box[5] + ")"
                        + " - Fläche: " + c.areaBlocks());
                i++;
            }
        }));
    }

    private void handleTeleport(CommandSender sender, String[] args) {
        if (!requirePerm(sender, "claims.admin.teleport")) return;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("player-only")));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§cVerwendung: /staffclaim tp <spieler> <claimnr>");
            return;
        }
        withClaim(sender, args[1], args[2], claim -> {
            World world = Bukkit.getWorld(claim.world());
            if (world == null) {
                sender.sendMessage("§cWelt nicht gefunden: " + claim.world());
                return;
            }
            int[] box = claim.boundingBox();
            // Der Mittelpunkt der Bounding-Box kann bei L-förmigen Claims außerhalb liegen.
            // Daher den Chunk des Claims nehmen, der dem Mittelpunkt am nächsten ist.
            double midX = (box[0] + box[2]) / 2.0;
            double midZ = (box[1] + box[3]) / 2.0;
            ChunkPos best = null;
            double bestDist = Double.MAX_VALUE;
            for (ChunkPos pos : claim.chunks()) {
                double dx = pos.x() - midX;
                double dz = pos.z() - midZ;
                double dist = dx * dx + dz * dz;
                if (dist < bestDist) {
                    bestDist = dist;
                    best = pos;
                }
            }
            if (best == null) {
                sender.sendMessage("§cDieser Claim hat keine Chunks.");
                return;
            }
            int centerX = (best.x() << 4) + 8;
            int centerZ = (best.z() << 4) + 8;
            int y = findTeleportY(world, centerX, centerZ);
            player.teleport(new Location(world, centerX + 0.5, y, centerZ + 0.5));
            sender.sendMessage("§aZu " + claim.name() + " teleportiert.");
        });
    }

    /**
     * Sucht eine begehbare Höhe. Im Nether ist die "höchste" Ebene die Bedrock-Decke,
     * dort muss stattdessen von unten nach einer freien Fläche gesucht werden.
     */
    private int findTeleportY(World world, int x, int z) {
        if (world.getEnvironment() == World.Environment.NETHER) {
            for (int y = 120; y > world.getMinHeight() + 5; y--) {
                var ground = world.getBlockAt(x, y, z);
                Material type = ground.getType();
                if (type.isSolid() && type != Material.MAGMA_BLOCK
                        && world.getBlockAt(x, y + 1, z).isPassable() && !world.getBlockAt(x, y + 1, z).isLiquid()
                        && world.getBlockAt(x, y + 2, z).isPassable() && !world.getBlockAt(x, y + 2, z).isLiquid()) {
                    return y + 1;
                }
            }
        }
        return world.getHighestBlockYAt(x, z) + 1;
    }

    private void handleUnclaim(CommandSender sender, String[] args) {
        if (!requirePerm(sender, "claims.admin.unclaim")) return;
        if (args.length < 3) {
            sender.sendMessage("§cVerwendung: /staffclaim unclaim <spieler> <claimnr>");
            return;
        }
        withClaim(sender, args[1], args[2], claim -> {
            ctx.claims().deleteClaim(claim);
            sender.sendMessage("§a" + claim.name() + " wurde gelöscht.");
        });
    }

    private void handleUnclaimHere(CommandSender sender) {
        if (!requirePerm(sender, "claims.admin.unclaim")) return;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("player-only")));
            return;
        }
        Claim claim = ctx.claims().getClaimAt(player.getLocation());
        if (claim == null) {
            sender.sendMessage("§cHier befindet sich kein Claim.");
            return;
        }
        ctx.claims().deleteClaim(claim);
        sender.sendMessage("§a" + claim.name() + " wurde gelöscht.");
    }

    private void handleUnclaimHereOnly(CommandSender sender) {
        if (!requirePerm(sender, "claims.admin.unclaim")) return;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("player-only")));
            return;
        }
        Location loc = player.getLocation();
        ChunkPos pos = new ChunkPos(loc.getWorld().getName(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        Claim claim = ctx.claims().getClaimAt(pos);
        if (claim == null) {
            sender.sendMessage("§cHier befindet sich kein Claim.");
            return;
        }
        boolean ok = ctx.claims().unclaimSingleChunk(pos);
        if (ok) {
            sender.sendMessage("§aDer Chunk (" + pos.x() + ", " + pos.z() + ") von Claim " + claim.name() + " wurde erfolgreich ungeclaimt.");
        } else {
            sender.sendMessage("§cFehler beim Unclaimen des Chunks.");
        }
    }

    private void handleTrustOrBan(CommandSender sender, String[] args, boolean trust) {
        if (!requirePerm(sender, trust ? "claims.admin.trust" : "claims.admin.ban")) return;
        if (args.length < 4) {
            sender.sendMessage("§cVerwendung: /staffclaim " + args[0] + " <spieler> <claimnr|all> <add|remove|list> [spieler2]");
            return;
        }
        String action = args[3].toLowerCase(Locale.ROOT);

        // Bulk Ban / Bulk Trust auf alle Claims des Besitzers
        if (args[2].equalsIgnoreCase("all") || args[2].equals("*")) {
            ctx.lookup().resolve(args[1]).thenAccept(opt -> Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
                if (opt.isEmpty()) {
                    sender.sendMessage("§cSpieler nicht gefunden: " + args[1]);
                    return;
                }
                var ownerResolved = opt.get();
                List<Claim> claims = ctx.claims().claimsOf(ownerResolved.uuid());
                if (claims.isEmpty()) {
                    sender.sendMessage("§c" + ownerResolved.name() + " besitzt keine Claims.");
                    return;
                }
                if (action.equals("list")) {
                    sender.sendMessage("§6Übersicht aller Claims von " + ownerResolved.name() + ":");
                    for (Claim c : claims) {
                        sender.sendMessage("§e" + c.name() + ": §7" + (trust ? c.trusted().keySet().size() : c.banned().size()) + " Einträge");
                    }
                    return;
                }
                if (args.length < 5) {
                    sender.sendMessage("§cEs fehlt der Zielspieler.");
                    return;
                }
                ctx.lookup().resolve(args[4]).thenAccept(targetOpt -> Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
                    if (targetOpt.isEmpty()) {
                        sender.sendMessage("§cSpieler nicht gefunden: " + args[4]);
                        return;
                    }
                    var targetResolved = targetOpt.get();
                    ctx.claims().ensurePlayerData(targetResolved.uuid(), targetResolved.name());
                    int count = 0;
                    for (Claim c : claims) {
                        if (trust) {
                            if (action.equals("add")) {
                                ctx.claims().setTrust(c, targetResolved.uuid(), ClaimPermission.ALL_MASK);
                            } else {
                                ctx.claims().removeTrust(c, targetResolved.uuid());
                            }
                            count++;
                        } else {
                            if (action.equals("add")) {
                                if (!c.isOwner(targetResolved.uuid())) {
                                    ctx.claims().setBan(c, targetResolved.uuid());
                                    ctx.banEnforcer().handleBan(c, targetResolved.uuid(), sender.getName());
                                    count++;
                                }
                            } else {
                                ctx.claims().removeBan(c, targetResolved.uuid());
                                count++;
                            }
                        }
                    }
                    if (trust) {
                        sender.sendMessage("§aTrust für " + targetResolved.name() + " auf " + count + " Claim(s) von " + ownerResolved.name() + " aktualisiert.");
                    } else {
                        sender.sendMessage("§a" + (action.equals("add") ? "Ban" : "Unban") + " für " + targetResolved.name() + " auf " + count + " Claim(s) von " + ownerResolved.name() + " durchgeführt (Bulk-Ban).");
                    }
                }));
            }));
            return;
        }

        withClaim(sender, args[1], args[2], claim -> {
            switch (action) {
                case "list" -> {
                    if (trust) {
                        sender.sendMessage("§6Getrustete Spieler auf " + claim.name() + ":");
                        claim.trusted().keySet().forEach(u -> sender.sendMessage("§7- " + nameOf(u)));
                    } else {
                        sender.sendMessage("§6Gebannte Spieler auf " + claim.name() + ":");
                        claim.banned().forEach(u -> sender.sendMessage("§7- " + nameOf(u)));
                    }
                }
                case "add", "remove" -> {
                    if (args.length < 5) {
                        sender.sendMessage("§cEs fehlt der Zielspieler.");
                        return;
                    }
                    ctx.lookup().resolve(args[4]).thenAccept(opt -> Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
                        if (opt.isEmpty()) {
                            sender.sendMessage("§cSpieler nicht gefunden: " + args[4]);
                            return;
                        }
                        var resolved = opt.get();
                        ctx.claims().ensurePlayerData(resolved.uuid(), resolved.name());
                        Claim fresh = ctx.claims().getById(claim.id());
                        if (fresh == null) {
                            sender.sendMessage("§cDieser Claim existiert nicht mehr.");
                            return;
                        }
                        if (!trust && action.equals("add") && fresh.isOwner(resolved.uuid())) {
                            sender.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("cannot-ban-owner")));
                            return;
                        }
                        if (trust) {
                            if (action.equals("add")) {
                                ctx.claims().setTrust(fresh, resolved.uuid(), ClaimPermission.ALL_MASK);
                                sender.sendMessage("§a" + resolved.name() + " wurde Trust auf " + fresh.name() + " gegeben.");
                            } else {
                                ctx.claims().removeTrust(fresh, resolved.uuid());
                                sender.sendMessage("§a" + resolved.name() + " wurde der Trust entzogen.");
                            }
                        } else {
                            if (action.equals("add")) {
                                ctx.claims().setBan(fresh, resolved.uuid());
                                sender.sendMessage("§a" + resolved.name() + " wurde von " + fresh.name() + " gebannt.");
                                ctx.banEnforcer().handleBan(fresh, resolved.uuid(), sender.getName());
                            } else {
                                ctx.claims().removeBan(fresh, resolved.uuid());
                                sender.sendMessage("§a" + resolved.name() + " wurde entbannt.");
                            }
                        }
                    }));
                }
                default -> sender.sendMessage("§cUnbekannte Aktion: " + action);
            }
        });
    }

    private void handleReload(CommandSender sender) {
        if (!requirePerm(sender, "claims.admin.reload")) return;
        try {
            ctx.config().reload();
            sender.sendMessage("§aKonfiguration und Nachrichten (config.yml, messages.yml) wurden neu geladen.");
        } catch (Exception ex) {
            sender.sendMessage("§cFehler beim Neuladen der Konfiguration: " + ex.getMessage());
            ctx.plugin().getLogger().warning("Fehler beim /staffclaim reload: " + ex.getMessage());
        }
    }

    private String nameOf(UUID uuid) {
        String n = Bukkit.getOfflinePlayer(uuid).getName();
        return n != null ? n : uuid.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            addIfPerm(subs, sender, "addclaims", "claims.admin.addclaims");
            addIfPerm(subs, sender, "removeclaims", "claims.admin.removeclaims");
            addIfPerm(subs, sender, "info", "claims.admin.info");
            addIfPerm(subs, sender, "tp", "claims.admin.teleport");
            addIfPerm(subs, sender, "unclaim", "claims.admin.unclaim");
            addIfPerm(subs, sender, "unclaimhere", "claims.admin.unclaim");
            addIfPerm(subs, sender, "unclaimhereonly", "claims.admin.unclaim");
            addIfPerm(subs, sender, "trust", "claims.admin.trust");
            addIfPerm(subs, sender, "ban", "claims.admin.ban");
            addIfPerm(subs, sender, "bypass", BypassManager.PERMISSION);
            addIfPerm(subs, sender, "pvp", PvPClaimBypassManager.PERMISSION);
            addIfPerm(subs, sender, "pvpclaimbypass", PvPClaimBypassManager.PERMISSION);
            addIfPerm(subs, sender, "claimspvpoverride", PvPClaimBypassManager.PERMISSION);
            addIfPerm(subs, sender, "reload", "claims.admin.reload");
            StringUtil.copyPartialMatches(args[0], subs, out);
            Collections.sort(out);
            return out;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("pvp") || sub.equals("pvpclaimbypass") || sub.equals("claimspvpoverride")) {
            return pvpBypassCommand.onTabComplete(sender, command, alias, Arrays.copyOfRange(args, 1, args.length));
        }

        if (args.length == 2) {
            switch (sub) {
                case "addclaims", "removeclaims", "info", "tp", "unclaim", "trust", "ban" -> {
                    StringUtil.copyPartialMatches(args[1], getKnownPlayerNames(), out);
                    Collections.sort(out);
                }
                case "bypass" -> {
                    if (sender.hasPermission(BypassManager.PERMISSION)) {
                        StringUtil.copyPartialMatches(args[1], List.of("on", "off"), out);
                    }
                }
                default -> {
                }
            }
            return out;
        }

        if (args.length == 3) {
            switch (sub) {
                case "tp", "unclaim" -> StringUtil.copyPartialMatches(args[2], claimNumbersFor(args[1]), out);
                case "trust", "ban" -> {
                    List<String> numbers = new ArrayList<>(claimNumbersFor(args[1]));
                    numbers.add("all");
                    StringUtil.copyPartialMatches(args[2], numbers, out);
                }
                case "bypass" -> {
                    if (sender.hasPermission("claims.admin.bypass.other")) {
                        StringUtil.copyPartialMatches(args[2], getKnownPlayerNames(), out);
                    }
                }
                default -> {
                }
            }
            return out;
        }

        if (args.length == 4) {
            if (sub.equals("trust") || sub.equals("ban")) {
                StringUtil.copyPartialMatches(args[3], List.of("add", "remove", "list"), out);
            }
            return out;
        }

        if (args.length == 5) {
            if ((sub.equals("trust") || sub.equals("ban"))
                    && (args[3].equalsIgnoreCase("add") || args[3].equalsIgnoreCase("remove"))) {
                StringUtil.copyPartialMatches(args[4], getKnownPlayerNames(), out);
                Collections.sort(out);
            }
            return out;
        }

        return out;
    }

    private void addIfPerm(List<String> list, CommandSender sender, String name, String perm) {
        if (sender.hasPermission(perm)) {
            list.add(name);
        }
    }

    private List<String> getKnownPlayerNames() {
        Set<String> nameSet = ctx.claims().getKnownPlayerNames();
        List<String> names = new ArrayList<>(nameSet);
        Collections.sort(names);
        return names;
    }

    private List<String> claimNumbersFor(String playerName) {

        UUID targetUuid = null;
        for (PlayerData pd : ctx.claims().getAllPlayerData()) {
            if (pd.name().equalsIgnoreCase(playerName)) {
                targetUuid = pd.uuid();
                break;
            }
        }
        if (targetUuid == null) {
            return Collections.emptyList();
        }
        List<Claim> claims = ctx.claims().claimsOf(targetUuid);
        List<String> numbers = new ArrayList<>();
        for (int i = 1; i <= claims.size(); i++) {
            numbers.add(String.valueOf(i));
        }
        return numbers;
    }

    private void withClaim(CommandSender sender, String playerName, String claimNrArg, java.util.function.Consumer<Claim> action) {
        int claimNr;
        try {
            claimNr = Integer.parseInt(claimNrArg);
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cUngültige Claim-Nummer: " + claimNrArg);
            return;
        }
        ctx.lookup().resolve(playerName).thenAccept(opt -> Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
            if (opt.isEmpty()) {
                sender.sendMessage("§cSpieler nicht gefunden: " + playerName);
                return;
            }
            var resolved = opt.get();
            List<Claim> claims = ctx.claims().claimsOf(resolved.uuid());
            if (claimNr < 1 || claimNr > claims.size()) {
                sender.sendMessage("§cUngültige Claim-Nummer. " + resolved.name() + " hat " + claims.size() + " Claim(s).");
                return;
            }
            action.accept(claims.get(claimNr - 1));
        }));
    }
}
