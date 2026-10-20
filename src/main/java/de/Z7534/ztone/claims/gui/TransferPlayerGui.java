package de.Z7534.ztone.claims.gui;

import de.Z7534.ztone.claims.ClaimsContext;
import de.Z7534.ztone.claims.manager.ClaimManager;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.util.GuiUtil;
import de.Z7534.ztone.claims.util.ItemBuilder;
import de.Z7534.ztone.claims.util.Text;
import de.Z7534.ztone.claims.util.VanishUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Zeigt ausschließlich ONLINE Spieler an (bewusst kein Offline-Suchfeld wie im
 * AddPlayerGui): Um zu prüfen, ob der Zielspieler genug freie Claim-Slots hat,
 * müssen seine aktuellen Berechtigungen (claims.start.<n>) ausgewertet werden -
 * das funktioniert bei Bukkit nur, wenn der Spieler online ist.
 */
public final class TransferPlayerGui extends Gui {

    private static final int MAX_LISTED = 36;
    private static final int SLOT_INFO = 40;
    private static final int SLOT_BACK = 44;
    private static final int SLOT_PREV = 38;
    private static final int SLOT_NEXT = 42;

    private final ClaimsContext ctx;
    private final Claim claim;
    private final List<UUID> listed = new ArrayList<>();
    private final int page;
    private final int pages;

    public TransferPlayerGui(ClaimsContext ctx, Claim claim) {
        this(ctx, claim, 0);
    }

    public TransferPlayerGui(ClaimsContext ctx, Claim claim, int page) {
        super(45, Text.line("<gold>Übertragen - " + Text.escape(claim.name())));
        this.ctx = ctx;
        this.claim = claim;
        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(claim.owner()) && !VanishUtil.isVanished(online)) {
                candidates.add(online);
            }
        }
        this.page = GuiUtil.clampPage(page, candidates.size(), MAX_LISTED);
        this.pages = GuiUtil.pageCount(candidates.size(), MAX_LISTED);
        build(candidates);
    }

    private void build(List<Player> candidates) {
        int needed = claim.chunks().size();
        int slot = 0;
        int from = page * MAX_LISTED;
        int to = Math.min(candidates.size(), from + MAX_LISTED);
        for (Player online : candidates.subList(from, to)) {
            listed.add(online.getUniqueId());
            int free = ctx.claims().getFreeClaimSlots(online);
            boolean enough = free == Integer.MAX_VALUE || free >= needed;
            String freeText = free == Integer.MAX_VALUE ? "∞" : String.valueOf(free);

            var head = ItemBuilder.playerHead(online.getUniqueId(), online.getName());
            ItemMeta meta = head.getItemMeta();
            List<Component> lore = new ArrayList<>();
            lore.add(Text.line("<gray>Freie Claim-Slots: " + (enough ? "<green>" : "<red>") + freeText
                    + " <gray>/ <white>" + needed + " <gray>benötigt"));
            lore.add(Component.empty());
            if (enough) {
                lore.add(Text.line("<gray>Linksklick, Claim übertragen."));
            } else {
                lore.add(Text.line("<red>Nicht genug freie Claim-Slots!"));
            }
            meta.lore(lore);
            head.setItemMeta(meta);
            inventory.setItem(slot, head);
            slot++;
        }

        if (listed.isEmpty()) {
            inventory.setItem(SLOT_INFO, ItemBuilder.of(Material.BARRIER)
                    .name(Text.line("<red>Keine Spieler online"))
                    .lore(
                            Text.line("<gray>Es muss ein anderer Spieler online sein,"),
                            Text.line("<gray>um einen Claim übertragen zu können.")
                    )
                    .build());
        }

        inventory.setItem(SLOT_BACK, GuiUtil.backButton(ctx.config().itemModel("transfer-back")));
        if (page > 0) {
            inventory.setItem(SLOT_PREV, GuiUtil.prevPageButton(page + 1, pages, ctx.config().itemModel("page-prev")));
        }
        if (page < pages - 1) {
            inventory.setItem(SLOT_NEXT, GuiUtil.nextPageButton(page + 1, pages, ctx.config().itemModel("page-next")));
        }
    }

    @Override
    public void onClick(Player player, int slot, ClickType clickType) {
        Claim current = ctx.claims().getOwned(player.getUniqueId(), claim.id());
        if (current == null) {
            player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("transfer-claim-gone")));
            player.closeInventory();
            return;
        }
        if (slot == SLOT_BACK) {
            new ClaimSettingsGui(ctx, current).open(player);
            return;
        }
        if (slot == SLOT_PREV && page > 0) {
            new TransferPlayerGui(ctx, current, page - 1).open(player);
            return;
        }
        if (slot == SLOT_NEXT && page < pages - 1) {
            new TransferPlayerGui(ctx, current, page + 1).open(player);
            return;
        }
        if (slot < 0 || slot >= listed.size()) {
            return;
        }

        UUID targetUuid = listed.get(slot);
        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null || !target.isOnline()) {
            String name = Bukkit.getOfflinePlayer(targetUuid).getName();
            player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("transfer-target-offline"),
                    Map.of("spieler", name != null ? name : targetUuid.toString())));
            new TransferPlayerGui(ctx, current).open(player);
            return;
        }

        ClaimManager.TransferResult result = ctx.claims().validateTransfer(current, target);
        if (result != ClaimManager.TransferResult.OK) {
            sendError(player, result, current, target);
            new TransferPlayerGui(ctx, current).open(player);
            return;
        }

        openConfirm(player, current, target);
    }

    private void openConfirm(Player player, Claim current, Player target) {
        String targetName = target.getName();
        UUID targetUuid = target.getUniqueId();

        new ConfirmGui(ctx,
                Text.line("<gold>Claim wirklich übertragen?"),
                List.of(
                        Text.line("<gray>" + Text.escape(current.name()) + " <gray>geht komplett an <white>" + targetName + "<gray>."),
                        Text.line("<gray>Du verlierst danach alle Rechte an diesem Claim!")
                ),
                () -> {
                    // Alles wird direkt vor der Ausführung erneut gegen den aktuellen
                    // Stand geprüft (Fallback für: Claim inzwischen gelöscht, Spieler
                    // ist offline gegangen, oder hat inzwischen keinen Platz mehr frei).
                    Claim fresh = ctx.claims().getOwned(player.getUniqueId(), current.id());
                    if (fresh == null) {
                        player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("transfer-claim-gone")));
                        player.closeInventory();
                        return;
                    }
                    Player freshTarget = Bukkit.getPlayer(targetUuid);
                    if (freshTarget == null || !freshTarget.isOnline()) {
                        player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("transfer-target-offline"),
                                Map.of("spieler", targetName)));
                        new TransferPlayerGui(ctx, fresh).open(player);
                        return;
                    }
                    ClaimManager.TransferResult result = ctx.claims().validateTransfer(fresh, freshTarget);
                    if (result != ClaimManager.TransferResult.OK) {
                        sendError(player, result, fresh, freshTarget);
                        new TransferPlayerGui(ctx, fresh).open(player);
                        return;
                    }

                    ctx.claims().transferClaim(fresh, freshTarget.getUniqueId(), freshTarget.getName());
                    player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("transfer-success-sender"),
                            Map.of("claim", fresh.name(), "spieler", freshTarget.getName())));
                    freshTarget.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("transfer-success-receiver"),
                            Map.of("claim", fresh.name(), "spieler", player.getName())));
                    player.closeInventory();
                },
                () -> {
                    Claim fresh = ctx.claims().getOwned(player.getUniqueId(), current.id());
                    if (fresh != null) {
                        new TransferPlayerGui(ctx, fresh).open(player);
                    } else {
                        player.closeInventory();
                    }
                }
        ).open(player);
    }

    private void sendError(Player player, ClaimManager.TransferResult result, Claim current, Player target) {
        switch (result) {
            case NOT_ENOUGH_SPACE -> {
                int free = Math.max(0, ctx.claims().getFreeClaimSlots(target));
                player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("transfer-not-enough-space"),
                        Map.of("spieler", target.getName(), "frei", String.valueOf(free),
                                "benoetigt", String.valueOf(current.chunks().size()))));
            }
            case SELF -> player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("transfer-self")));
            case CLAIM_GONE -> player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("transfer-claim-gone")));
            default -> {
            }
        }
    }
}
