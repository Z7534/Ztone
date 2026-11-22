package de.Z7534.ztone.claims.command;

import de.Z7534.ztone.claims.ClaimsContext;
import de.Z7534.ztone.claims.manager.ClaimNametagManager;
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

public final class ClaimNametagCommand implements CommandExecutor, TabCompleter {

    private final ClaimsContext ctx;

    public ClaimNametagCommand(ClaimsContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ClaimNametagManager.PERMISSION)) {
            sender.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("no-permission")));
            return true;
        }

        if (args.length == 0) {
            handleToggleCurrentClaim(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("on") || sub.equals("an") || sub.equals("show")
                || sub.equals("off") || sub.equals("aus") || sub.equals("hide")
                || sub.equals("toggle")) {
            handleSetCurrentClaim(sender, sub);
            return true;
        }

        if (sub.equals("list")) {
            handleList(sender);
            return true;
        }

        // /claimnametags <spieler> <claimnr> [on|off|toggle]
        if (args.length >= 2) {
            handleSpecificClaim(sender, args);
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== Claims Nametag-Verwaltung Hilfe ===");
        sender.sendMessage("§e/claimnametags §7- Schaltet Nametags auf dem aktuellen Claim an/aus");
        sender.sendMessage("§e/claimnametags <off|on> §7- Deaktiviert (off) / Aktiviert (on) Nametags auf dem aktuellen Claim");
        sender.sendMessage("§e/claimnametags <spieler> <claimnr> [off|on] §7- Schaltet Nametags für einen spezifischen Claim um");
        sender.sendMessage("§e/claimnametags list §7- Listet alle Claims mit deaktivierten Nametags auf");
    }

    private void handleToggleCurrentClaim(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cKonsole muss einen Claim angeben: /claimnametags <spieler> <claimnr> [off|on]");
            return;
        }
        Claim claim = ctx.claims().getClaimAt(player.getLocation());
        if (claim == null) {
            player.sendMessage("§cDu stehst in keinem Claim! Gib einen Claim an oder stelle dich auf einen Claim.");
            return;
        }
        boolean disabled = ctx.nametags().toggleNametagDisabled(claim.id());
        notifyClaimStatus(sender, claim, disabled);
    }

    private void handleSetCurrentClaim(CommandSender sender, String mode) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cKonsole muss einen Claim angeben: /claimnametags <spieler> <claimnr> [off|on]");
            return;
        }
        Claim claim = ctx.claims().getClaimAt(player.getLocation());
        if (claim == null) {
            player.sendMessage("§cDu stehst in keinem Claim!");
            return;
        }
        boolean disabled = applyMode(claim.id(), mode);
        notifyClaimStatus(sender, claim, disabled);
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
            boolean disabled;
            if (args.length >= 3) {
                disabled = applyMode(claim.id(), args[2].toLowerCase(Locale.ROOT));
            } else {
                disabled = ctx.nametags().toggleNametagDisabled(claim.id());
            }
            notifyClaimStatus(sender, claim, disabled);
        }));
    }

    /**
     * "off" / "aus" / "hide" = Nametags auf dem Claim deaktivieren (disabled = true).
     * "on" / "an" / "show"   = Nametags auf dem Claim wieder aktivieren (disabled = false).
     */
    private boolean applyMode(long claimId, String mode) {
        if (mode.equals("off") || mode.equals("aus") || mode.equals("hide")) {
            ctx.nametags().setNametagDisabled(claimId, true);
            return true;
        } else if (mode.equals("on") || mode.equals("an") || mode.equals("show")) {
            ctx.nametags().setNametagDisabled(claimId, false);
            return false;
        } else {
            return ctx.nametags().toggleNametagDisabled(claimId);
        }
    }

    private void handleList(CommandSender sender) {
        Set<Long> ids = ctx.nametags().getDisabledClaimIds();
        if (ids.isEmpty()) {
            sender.sendMessage("§7Es gibt derzeit keine Claims mit deaktivierten Nametags.");
            return;
        }
        sender.sendMessage("§6=== Claims mit deaktivierten Nametags (" + ids.size() + ") ===");
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

    private void notifyClaimStatus(CommandSender sender, Claim claim, boolean disabled) {
        String ownerName = Bukkit.getOfflinePlayer(claim.owner()).getName();
        if (ownerName == null) ownerName = claim.owner().toString();
        if (disabled) {
            sender.sendMessage("§cNametags für Claim §e" + claim.name() + " §7(Besitzer: " + ownerName + ") §4DEAKTIVIERT§c! Spielernamen über den Köpfen sind auf diesem Claim nun ausgeblendet.");
        } else {
            sender.sendMessage("§aNametags für Claim §e" + claim.name() + " §7(Besitzer: " + ownerName + ") §2AKTIVIERT§a! Spielernamen über den Köpfen sind auf diesem Claim wieder sichtbar.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ClaimNametagManager.PERMISSION)) {
            return Collections.emptyList();
        }

        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("off", "on", "toggle", "list"));
            subs.addAll(ctx.claims().getKnownPlayerNames());
            StringUtil.copyPartialMatches(args[0], subs, out);
            Collections.sort(out);
            return out;
        }

        if (args.length == 2) {
            UUID targetUuid = null;
            for (PlayerData pd : ctx.claims().getAllPlayerData()) {
                if (pd.name().equalsIgnoreCase(args[0])) {
                    targetUuid = pd.uuid();
                    break;
                }
            }
            if (targetUuid != null) {
                List<Claim> claims = ctx.claims().claimsOf(targetUuid);
                List<String> numbers = new ArrayList<>();
                for (int i = 1; i <= claims.size(); i++) {
                    numbers.add(String.valueOf(i));
                }
                StringUtil.copyPartialMatches(args[1], numbers, out);
                Collections.sort(out);
                return out;
            }
        }

        if (args.length == 3) {
            StringUtil.copyPartialMatches(args[2], List.of("off", "on", "toggle"), out);
            return out;
        }

        return out;
    }
}
