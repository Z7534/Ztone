package de.Z7534.ztone.claims.command;

import de.Z7534.ztone.claims.ClaimsContext;
import de.Z7534.ztone.claims.model.PlayerData;
import de.Z7534.ztone.claims.gui.ClaimConfirmGui;
import de.Z7534.ztone.claims.gui.MainMenuGui;
import de.Z7534.ztone.claims.manager.ClaimManager;
import de.Z7534.ztone.claims.manager.PlayerLookupService;
import de.Z7534.ztone.claims.model.SelectionSession;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;

public final class ClaimCommand implements CommandExecutor, TabCompleter {

    private final ClaimsContext ctx;

    /**
     * Offene Pay-Bestätigungen: Spieler muss /claim pay <spieler> <anzahl> mit
     * denselben Angaben innerhalb von confirm-seconds erneut ausführen.
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

    /**
     * Neuer, übersichtlicher Ban/Unban-Befehl:
     * <ul>
     *     <li>{@code /claim ban <spieler|*|alle> [hier|alle|#nr|<claimname>]}</li>
     *     <li>{@code /claim unban <spieler|*|alle> [hier|alle|#nr|<claimname>]}</li>
     * </ul>
     */
    private void handleBanUnban(Player player, String[] args) {
        String action = args[0].toLowerCase(Locale.ROOT);
        if (args.length < 2) {
            player.sendMessage(Text.line(ctx.config().prefix() + "<red>Verwendung: /claim " + action + " <spieler|*|alle> [hier|alle|#nr|<claimname>]"));
            return;
        }

        String targetInput = args[1];
        boolean isAllStrangers = targetInput.equals("*") || targetInput.equalsIgnoreCase("alle") || targetInput.equalsIgnoreCase("all");

        String scope = args.length >= 3 ? args[2] : "hier";
        boolean bulkAllClaims = scope.equalsIgnoreCase("alle") || scope.equalsIgnoreCase("all") || scope.equals("*");

        List<Claim> targets = new ArrayList<>();
        if (bulkAllClaims) {
            targets.addAll(ctx.claims().claimsOf(player.getUniqueId()));
            if (targets.isEmpty()) {
                player.sendMessage(Text.line(ctx.config().prefix() + "<red>Du besitzt noch keine Claims!"));
                return;
            }
        } else if (scope.equalsIgnoreCase("hier") || scope.equalsIgnoreCase("here")) {
            Claim current = ctx.claims().getClaimAt(player.getLocation());
            if (current == null || !current.isOwnerOrManager(player.getUniqueId())) {
                player.sendMessage(Text.line(ctx.config().prefix() + "<red>Du stehst auf keinem Claim, den du verwaltest! " +
                        "<gray>Nutze <yellow>/claim " + action + " " + targetInput + " alle</yellow> für alle deine Claims oder gib einen Claim-Namen/Nummer an."));
                return;
            }
            targets.add(current);
        } else {
            // Spezifischer Claim (#nr oder Name)
            Claim resolvedClaim = resolveClaim(player, scope);
            if (resolvedClaim == null) {
                player.sendMessage(Text.line(ctx.config().prefix() + "<red>Claim '" + scope + "' nicht gefunden oder du hast dort keine Rechte!"));
                return;
            }
            targets.add(resolvedClaim);
        }

        // Fall A: Alle Fremden bannen / entbannen
        if (isAllStrangers) {
            boolean banState = action.equals("ban");
            for (Claim c : targets) {
                ctx.claims().setBanAll(c, banState);
                if (banState) {
                    ctx.banEnforcer().handleBanAll(c, player.getName());
                }
            }
            if (bulkAllClaims) {
                player.sendMessage(Text.line(ctx.config().prefix() + (banState
                        ? "<green>Alle fremden Spieler wurden auf <gold>allen deinen Claims (" + targets.size() + ") <green>gebannt!"
                        : "<yellow>Der Alle-Fremden-Bann wurde auf <gold>allen deinen Claims (" + targets.size() + ") <yellow>aufgehoben.")));
            } else {
                Claim c = targets.get(0);
                player.sendMessage(Text.line(ctx.config().prefix() + (banState
                        ? "<green>Alle fremden Spieler wurden auf Claim '<yellow>" + c.name() + "<green>' gebannt!"
                        : "<yellow>Der Alle-Fremden-Bann auf Claim '<yellow>" + c.name() + "<yellow>' wurde aufgehoben.")));
            }
            return;
        }

        // Fall B: Einzelnen Spieler bannen / entbannen
        ctx.lookup().resolve(targetInput).thenAccept(resultOpt -> Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
            if (!player.isOnline()) return;
            if (resultOpt.isEmpty()) {
                player.sendMessage(Text.line(ctx.config().prefix() + "<red>Spieler '" + targetInput + "' wurde nicht gefunden."));
                return;
            }

            var resolved = resultOpt.get();
            if (player.getUniqueId().equals(resolved.uuid())) {
                player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("cannot-ban-owner")));
                return;
            }

            ctx.claims().ensurePlayerData(resolved.uuid(), resolved.name());

            if (action.equals("ban")) {
                for (Claim c : targets) {
                    if (c.isOwner(resolved.uuid())) continue;
                    ctx.claims().setBan(c, resolved.uuid());
                    ctx.banEnforcer().handleBan(c, resolved.uuid(), player.getName());
                }
                if (bulkAllClaims) {
                    player.sendMessage(Text.line(ctx.config().prefix() + "<green>Spieler <gold>" + resolved.name()
                            + " <green>wurde auf allen deinen Claims (" + targets.size() + ") gebannt!"));
                } else {
                    player.sendMessage(Text.line(ctx.config().prefix() + "<green>Spieler <gold>" + resolved.name()
                            + " <green>wurde auf Claim '<yellow>" + targets.get(0).name() + "<green>' gebannt!"));
                }
            } else {
                for (Claim c : targets) {
                    ctx.claims().removeBan(c, resolved.uuid());
                }
                if (bulkAllClaims) {
                    player.sendMessage(Text.line(ctx.config().prefix() + "<green>Spieler <gold>" + resolved.name()
                            + " <green>wurde auf allen deinen Claims (" + targets.size() + ") entbannt!"));
                } else {
                    player.sendMessage(Text.line(ctx.config().prefix() + "<green>Spieler <gold>" + resolved.name()
                            + " <green>wurde auf Claim '<yellow>" + targets.get(0).name() + "<green>' entbannt!"));
                }
            }
        }));
    }

    private Claim resolveClaim(Player player, String input) {
        List<Claim> owned = ctx.claims().claimsOf(player.getUniqueId());
        // 1. Per Index "#1", "#2" oder "1", "2"
        String numStr = input.startsWith("#") ? input.substring(1) : input;
        try {
            int idx = Integer.parseInt(numStr);
            if (idx >= 1 && idx <= owned.size()) {
                return owned.get(idx - 1);
            }
        } catch (NumberFormatException ignored) {}

        // 2. Per Exakter Name oder Teilname
        for (Claim c : owned) {
            if (c.name().equalsIgnoreCase(input)) {
                return c;
            }
        }
        for (Claim c : owned) {
            if (c.name().toLowerCase(Locale.ROOT).startsWith(input.toLowerCase(Locale.ROOT))) {
                return c;
            }
        }
        return null;
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
            default -> {}
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

        if (args.length == 2 && args[0].equalsIgnoreCase("pay")) {
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

        if (args.length == 2 && args[0].equalsIgnoreCase("ban")) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("*");
            suggestions.add("alle");
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(player.getUniqueId())) {
                    suggestions.add(online.getName());
                }
            }
            StringUtil.copyPartialMatches(args[1], suggestions, out);
            return out;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("unban")) {
            Set<String> bannedNames = new LinkedHashSet<>();
            bannedNames.add("*");
            bannedNames.add("alle");
            // Gespeicherte Namen von gebannten Spielern vorschlagen
            Claim here = ctx.claims().getClaimAt(player.getLocation());
            Set<UUID> checkUuids = new HashSet<>();
            if (here != null && here.isOwnerOrManager(player.getUniqueId())) {
                checkUuids.addAll(here.banned());
            } else {
                for (Claim c : ctx.claims().claimsOf(player.getUniqueId())) {
                    checkUuids.addAll(c.banned());
                }
            }
            for (UUID u : checkUuids) {
                String n = Bukkit.getOfflinePlayer(u).getName();
                if (n != null) bannedNames.add(n);
            }
            StringUtil.copyPartialMatches(args[1], bannedNames, out);
            return out;
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("ban") || args[0].equalsIgnoreCase("unban"))) {
            List<String> scopes = new ArrayList<>();
            scopes.add("hier");
            scopes.add("alle");
            List<Claim> owned = ctx.claims().claimsOf(player.getUniqueId());
            for (int i = 0; i < owned.size(); i++) {
                scopes.add("#" + (i + 1));
                String name = owned.get(i).name();
                if (!name.contains(" ")) {
                    scopes.add(name);
                }
            }
            StringUtil.copyPartialMatches(args[2], scopes, out);
            return out;
        }

        return out;
    }
}
