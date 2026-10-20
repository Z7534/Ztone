package de.Z7534.ztone.claims.command;

import de.Z7534.ztone.claims.ClaimsContext;
import de.Z7534.ztone.claims.gui.ClaimConfirmGui;
import de.Z7534.ztone.claims.gui.MainMenuGui;
import de.Z7534.ztone.claims.manager.ClaimManager;
import de.Z7534.ztone.claims.manager.PlayerLookupService;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.model.PlayerData;
import de.Z7534.ztone.claims.model.SelectionSession;
import de.Z7534.ztone.claims.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClaimCommand implements CommandExecutor, TabCompleter {

    private final ClaimsContext ctx;

    /**
     * Offene Pay-Bestätigungen: Spieler muss /claim pay <spieler> <anzahl> mit
     * denselben Angaben innerhalb von confirm-seconds erneut ausführen, damit die
     * Zahlung tatsächlich durchgeführt wird. Lebt nur im Arbeitsspeicher (kein
     * Server-Neustart-Schutz nötig, es ist ja noch nichts passiert).
     */
    private final Map<UUID, PendingPayment> pendingPayments = new HashMap<>();

    private record PendingPayment(UUID targetUuid, String targetName, int amount, long expiresAtMillis) {
    }

    public ClaimCommand(ClaimsContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("player-only")));
            return true;
        }
        if (!player.hasPermission("claims.use")) {
            player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("no-permission")));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("pay")) {
            handlePay(player, args);
            return true;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("ban") || args[0].equalsIgnoreCase("unban"))) {
            handleBanUnban(player, args);
            return true;
        }

        SelectionSession session = ctx.selection().get(player.getUniqueId());
        if (session != null) {
            new ClaimConfirmGui(ctx, session).open(player);
        } else {
            new MainMenuGui(ctx, player).open(player);
        }
        return true;
    }

    private void handleBanUnban(Player player, String[] args) {
        String action = args[0].toLowerCase();
        if (args.length < 2) {
            player.sendMessage(Text.line(ctx.config().prefix() + "<red>Verwendung: /claim " + action + " <spieler> [all]"));
            return;
        }

        String targetName = args[1];
        boolean bulk = args.length >= 3 && (args[2].equalsIgnoreCase("all") || args[2].equals("*"));

        Claim standingClaim = null;
        if (!bulk) {
            standingClaim = ctx.claims().getClaimAt(player.getLocation());
            if (standingClaim == null || !standingClaim.isOwnerOrManager(player.getUniqueId())) {
                player.sendMessage(Text.line(ctx.config().prefix() + "<red>Du befindest dich auf keinem Claim, den du verwaltest! Nutze '/claim " + action + " " + targetName + " all' für alle deine Claims."));
                return;
            }
        }

        final Claim targetClaim = standingClaim;
        ctx.lookup().resolve(targetName).thenAccept(resultOpt -> Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
            if (!player.isOnline()) return;
            if (resultOpt.isEmpty()) {
                player.sendMessage(Text.line(ctx.config().prefix() + "<red>Spieler '" + targetName + "' nicht gefunden."));
                return;
            }

            var resolved = resultOpt.get();
            if (player.getUniqueId().equals(resolved.uuid())) {
                player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("cannot-ban-owner")));
                return;
            }
            if (targetClaim != null && targetClaim.isOwner(resolved.uuid())) {
                player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("cannot-ban-owner")));
                return;
            }

            ctx.claims().ensurePlayerData(resolved.uuid(), resolved.name());

            if (action.equals("ban")) {
                if (bulk) {
                    List<Claim> owned = ctx.claims().claimsOf(player.getUniqueId());
                    for (Claim c : owned) {
                        ctx.claims().setBan(c, resolved.uuid());
                        ctx.banEnforcer().handleBan(c, resolved.uuid(), player.getName());
                    }
                    player.sendMessage(Text.line(ctx.config().prefix() + "<green>Spieler <gold>" + resolved.name() + " <green>wurde auf allen deinen Claims (" + owned.size() + ") gebannt!"));
                } else {
                    ctx.claims().setBan(targetClaim, resolved.uuid());
                    ctx.banEnforcer().handleBan(targetClaim, resolved.uuid(), player.getName());
                    player.sendMessage(Text.line(ctx.config().prefix() + "<green>Spieler <gold>" + resolved.name() + " <green>wurde auf Claim '<yellow>" + targetClaim.name() + "<green>' gebannt!"));
                }
            } else {
                if (bulk) {
                    List<Claim> owned = ctx.claims().claimsOf(player.getUniqueId());
                    for (Claim c : owned) {
                        ctx.claims().removeBan(c, resolved.uuid());
                    }
                    player.sendMessage(Text.line(ctx.config().prefix() + "<green>Spieler <gold>" + resolved.name() + " <green>wurde auf allen deinen Claims (" + owned.size() + ") entbannt!"));
                } else {
                    ctx.claims().removeBan(targetClaim, resolved.uuid());
                    player.sendMessage(Text.line(ctx.config().prefix() + "<green>Spieler <gold>" + resolved.name() + " <green>wurde auf Claim '<yellow>" + targetClaim.name() + "<green>' entbannt!"));
                }
            }
        }));
    }

    private void handlePay(Player player, String[] args) {
        if (!player.hasPermission("claims.pay")) {
            player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("no-permission")));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("pay-usage")));
            return;
        }

        String targetName = args[1];

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("pay-invalid-amount")));
            return;
        }
        if (amount <= 0) {
            player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("pay-invalid-amount")));
            return;
        }
        int maxAmount = ctx.config().payMaxAmount();
        if (amount > maxAmount) {
            player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("pay-amount-too-large"),
                    Map.of("max", String.valueOf(maxAmount))));
            return;
        }

        // Schneller Vorab-Check per Name, damit man sich nicht selbst tippen muss,
        // um die offensichtliche Selbst-Zahlung zu erkennen (spart den Lookup-Roundtrip).
        if (targetName.equalsIgnoreCase(player.getName())) {
            player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("pay-self")));
            pendingPayments.remove(player.getUniqueId());
            return;
        }

        final int finalAmount = amount;
        ctx.lookup().resolve(targetName).thenAccept(resultOpt -> Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
            if (!player.isOnline()) {
                return;
            }
            if (resultOpt.isEmpty()) {
                player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("pay-target-not-found"),
                        Map.of("spieler", targetName)));
                pendingPayments.remove(player.getUniqueId());
                return;
            }

            PlayerLookupService.Resolved resolved = resultOpt.get();

            // Fallback: Alles wird gegen den AKTUELLEN Stand geprüft - sowohl beim
            // ersten Aufruf (Bestätigungsanfrage) als auch beim zweiten Aufruf
            // (Bestätigung), damit sich zwischenzeitliche Änderungen (z.B. neue
            // Claims gesetzt, bereits an jemand anderen gezahlt) nicht umgehen lassen.
            ClaimManager.PayResult result = ctx.claims().validatePay(player, resolved.uuid(), finalAmount);
            if (result != ClaimManager.PayResult.OK) {
                sendPayError(player, result, finalAmount);
                pendingPayments.remove(player.getUniqueId());
                return;
            }

            PendingPayment pending = pendingPayments.get(player.getUniqueId());
            long now = System.currentTimeMillis();
            boolean isConfirmation = pending != null
                    && pending.expiresAtMillis() > now
                    && pending.targetUuid().equals(resolved.uuid())
                    && pending.amount() == finalAmount;

            if (isConfirmation) {
                pendingPayments.remove(player.getUniqueId());
                ctx.claims().executePay(player, resolved.uuid(), resolved.name(), finalAmount);

                int senderNewBonus = ctx.claims().findPlayerData(player.getUniqueId())
                        .map(PlayerData::bonusClaimSlots).orElse(0);
                player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("pay-success-sender"),
                        Map.of("anzahl", String.valueOf(finalAmount), "spieler", resolved.name(),
                                "neu", String.valueOf(senderNewBonus))));

                Player onlineTarget = Bukkit.getPlayer(resolved.uuid());
                if (onlineTarget != null) {
                    int receiverNewBonus = ctx.claims().findPlayerData(resolved.uuid())
                            .map(PlayerData::bonusClaimSlots).orElse(0);
                    onlineTarget.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("pay-success-receiver"),
                            Map.of("anzahl", String.valueOf(finalAmount), "spieler", player.getName(),
                                    "neu", String.valueOf(receiverNewBonus))));
                }
            } else {
                int seconds = ctx.config().payConfirmSeconds();
                pendingPayments.put(player.getUniqueId(),
                        new PendingPayment(resolved.uuid(), resolved.name(), finalAmount, now + seconds * 1000L));
                player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("pay-confirm-prompt"),
                        Map.of("anzahl", String.valueOf(finalAmount), "spieler", resolved.name(),
                                "sekunden", String.valueOf(seconds))));
            }
        }));
    }

    private void sendPayError(Player player, ClaimManager.PayResult result, int amount) {
        switch (result) {
            case INVALID_AMOUNT -> player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("pay-invalid-amount")));
            case SELF -> player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("pay-self")));
            case NOT_ENOUGH_FREE -> {
                int free = Math.max(0, ctx.claims().getFreeClaimSlots(player));
                player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("pay-not-enough-free"),
                        Map.of("frei", String.valueOf(free), "anzahl", String.valueOf(amount))));
            }
            case NOT_ENOUGH_OWN -> {
                int own = ctx.claims().findPlayerData(player.getUniqueId())
                        .map(PlayerData::bonusClaimSlots).orElse(0);
                player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("pay-not-enough-own"),
                        Map.of("verfuegbar", String.valueOf(own), "anzahl", String.valueOf(amount))));
            }
            default -> {
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!(sender instanceof Player player) || !player.hasPermission("claims.use")) {
            return out;
        }

        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (player.hasPermission("claims.pay")) {
                subs.add("pay");
            }
            subs.add("ban");
            subs.add("unban");
            StringUtil.copyPartialMatches(args[0], subs, out);
            return out;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("pay") || args[0].equalsIgnoreCase("ban") || args[0].equalsIgnoreCase("unban"))) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(player.getUniqueId())) {
                    names.add(online.getName());
                }
            }
            Collections.sort(names);
            StringUtil.copyPartialMatches(args[1], names, out);
            return out;
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("ban") || args[0].equalsIgnoreCase("unban"))) {
            StringUtil.copyPartialMatches(args[2], List.of("all"), out);
            return out;
        }

        return out;
    }
}
