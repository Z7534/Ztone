package de.Z7534.ztone.claims.command;

import de.Z7534.ztone.claims.ClaimsContext;
import de.Z7534.ztone.claims.manager.PvPClaimBypassManager;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.model.PlayerData;
import de.Z7534.ztone.claims.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;

public final class PvPClaimBypassCommand implements CommandExecutor, TabCompleter {

    private final ClaimsContext ctx;

    public PvPClaimBypassCommand(ClaimsContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PvPClaimBypassManager.PERMISSION)) {
            sender.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("no-permission")));
            return true;
        }

        if (args.length == 0) {
            handleToggleCurrentClaim(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("on") || sub.equals("an") || sub.equals("off") || sub.equals("aus") || sub.equals("toggle")) {
            handleSetCurrentClaim(sender, sub);
            return true;
        }

        if (sub.equals("list")) {
            handleList(sender);
            return true;
        }

        if (sub.equals("player") || sub.equals("spieler")) {
            handlePlayerBypass(sender, args);
            return true;
        }

        // /claimspvpoverride <spieler> <claimnr> [on|off|toggle]
        if (args.length >= 2) {
            handleSpecificClaim(sender, args);
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== Claims PvP-Override Hilfe ===");
        sender.sendMessage("§e/claimspvpoverride §7- Schaltet PvP auf dem aktuellen Claim um");
        sender.sendMessage("§e/claimspvpoverride <on|off> §7- Aktiviert/Deaktiviert PvP auf dem aktuellen Claim");
        sender.sendMessage("§e/claimspvpoverride <spieler> <claimnr> [on|off] §7- Setzt PvP-Override für einen spezifischen Claim");
        sender.sendMessage("§e/claimspvpoverride player <spieler> [on|off] §7- Schaltet Spieler-Bypass um (darf überall PvP machen)");
        sender.sendMessage("§e/claimspvpoverride list §7- Listet alle Claims mit aktivem PvP-Override auf");
    }

    private void handleToggleCurrentClaim(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cKonsole muss einen Claim angeben: /claimspvpoverride <spieler> <claimnr> [on|off]");
            return;
        }
        Claim claim = ctx.claims().getClaimAt(player.getLocation());
        if (claim == null) {
            player.sendMessage("§cDu stehst in keinem Claim! Gib einen Claim an oder stelle dich auf einen Claim.");
            return;
        }
        boolean active = ctx.pvpClaimBypass().toggleClaimBypass(claim.id());
        notifyClaimStatus(sender, claim, active);
    }

    private void handleSetCurrentClaim(CommandSender sender, String mode) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cKonsole muss einen Claim angeben: /claimspvpoverride <spieler> <claimnr> [on|off]");
            return;
        }
        Claim claim = ctx.claims().getClaimAt(player.getLocation());
        if (claim == null) {
            player.sendMessage("§cDu stehst in keinem Claim!");
            return;
        }
        boolean active;
        if (mode.equals("on") || mode.equals("an")) {
            active = true;
            ctx.pvpClaimBypass().setClaimBypassed(claim.id(), true);
        } else if (mode.equals("off") || mode.equals("aus")) {
            active = false;
            ctx.pvpClaimBypass().setClaimBypassed(claim.id(), false);
        } else {
            active = ctx.pvpClaimBypass().toggleClaimBypass(claim.id());
        }
        notifyClaimStatus(sender, claim, active);
    }

    private void handleSpecificClaim(CommandSender sender, String[] args) {
        String playerName = args[0];
        String claimNrStr = args[1];
        int claimNr;
        try {
            claimNr = Integer.parseInt(claimNrStr);
        } catch (NumberFormatException e) {
            sendHelp(sender);
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
            Claim claim = claims.get(claimNr - 1);
            boolean active;
            if (args.length >= 3) {
                String mode = args[2].toLowerCase(Locale.ROOT);
                if (mode.equals("on") || mode.equals("an")) {
                    active = true;
                    ctx.pvpClaimBypass().setClaimBypassed(claim.id(), true);
                } else if (mode.equals("off") || mode.equals("aus")) {
                    active = false;
                    ctx.pvpClaimBypass().setClaimBypassed(claim.id(), false);
                } else {
                    active = ctx.pvpClaimBypass().toggleClaimBypass(claim.id());
                }
            } else {
                active = ctx.pvpClaimBypass().toggleClaimBypass(claim.id());
            }
            notifyClaimStatus(sender, claim, active);
        }));
    }

    private void handlePlayerBypass(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cVerwendung: /claimspvpoverride player <spieler> [on|off]");
                return;
            }
            boolean active = ctx.pvpClaimBypass().togglePlayerBypass(player);
            sender.sendMessage("§aPvP-Claim-Bypass für dich selbst " + (active ? "§2aktiviert" : "§4deaktiviert") + "§a.");
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage("§cSpieler nicht online: " + targetName);
            return;
        }

        boolean active;
        if (args.length >= 3) {
            String mode = args[2].toLowerCase(Locale.ROOT);
            if (mode.equals("on") || mode.equals("an")) {
                active = true;
                ctx.pvpClaimBypass().setPlayerBypassed(target.getUniqueId(), true);
            } else if (mode.equals("off") || mode.equals("aus")) {
                active = false;
                ctx.pvpClaimBypass().setPlayerBypassed(target.getUniqueId(), false);
            } else {
                active = ctx.pvpClaimBypass().togglePlayerBypass(target);
            }
        } else {
            active = ctx.pvpClaimBypass().togglePlayerBypass(target);
        }

        sender.sendMessage("§aPvP-Claim-Bypass für " + target.getName() + " wurde " + (active ? "§2aktiviert" : "§4deaktiviert") + "§a.");
        target.sendMessage("§aDein PvP-Claim-Bypass wurde " + (active ? "§2aktiviert" : "§4deaktiviert") + "§a.");
    }

    private void handleList(CommandSender sender) {
        Set<Long> ids = ctx.pvpClaimBypass().getBypassedClaimIds();
        if (ids.isEmpty()) {
            sender.sendMessage("§7Es gibt derzeit keine Claims mit aktivem PvP-Override.");
            return;
        }
        sender.sendMessage("§6=== Claims mit PvP-Override (" + ids.size() + ") ===");
        for (Long id : ids) {
            Claim claim = ctx.claims().getById(id);
            if (claim != null) {
                String ownerName = Bukkit.getOfflinePlayer(claim.owner()).getName();
                if (ownerName == null) ownerName = claim.owner().toString();
                sender.sendMessage("§7- ID: §f" + claim.id() + " §7| Name: §e" + claim.name() + " §7| Besitzer: §a" + ownerName + " §7| Welt: §b" + claim.world());
            } else {
                sender.sendMessage("§7- ID: §f" + id + " §c(Claim inaktiv/gelöscht)");
            }
        }
    }

    private void notifyClaimStatus(CommandSender sender, Claim claim, boolean active) {
        String ownerName = Bukkit.getOfflinePlayer(claim.owner()).getName();
        if (ownerName == null) ownerName = claim.owner().toString();
        if (active) {
            sender.sendMessage("§aPvP-Override für Claim §e" + claim.name() + " §7(Besitzer: " + ownerName + ") §2AKTIVIERT§a! Auf diesem Claim ist PvP nun wie im Unclaimed-Terrain aktiv.");
        } else {
            sender.sendMessage("§cPvP-Override für Claim §e" + claim.name() + " §7(Besitzer: " + ownerName + ") §4DEAKTIVIERT§c! PvP ist auf diesem Claim komplett deaktiviert.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PvPClaimBypassManager.PERMISSION)) {
            return Collections.emptyList();
        }

        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("on", "off", "toggle", "list", "player"));
            subs.addAll(ctx.claims().getKnownPlayerNames());
            StringUtil.copyPartialMatches(args[0], subs, out);
            Collections.sort(out);
            return out;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            if (sub.equals("player") || sub.equals("spieler")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    out.add(p.getName());
                }
                StringUtil.copyPartialMatches(args[1], out, out);
                Collections.sort(out);
                return out;
            }

            // Wenn arg0 ein Spielername ist: Claim-Nummern vorschlagen
            UUID targetUuid = null;
            for (PlayerData pd : ctx.claims().getAllPlayerData()) {
                if (pd.name().equalsIgnoreCase(args[0])) {
                    targetUuid = pd.uuid();
                    break;
                }
            }
            if (targetUuid != null) {
                List<Claim> claims = ctx.claims().claimsOf(targetUuid);
                for (int i = 1; i <= claims.size(); i++) {
                    out.add(String.valueOf(i));
                }
                StringUtil.copyPartialMatches(args[1], out, out);
                Collections.sort(out);
                return out;
            }
        }

        if (args.length == 3) {
            StringUtil.copyPartialMatches(args[2], List.of("on", "off", "toggle"), out);
            return out;
        }

        return out;
    }
}
