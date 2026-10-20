package de.Z7534.ztone.claims.gui;

import de.Z7534.ztone.claims.ClaimsContext;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.util.GuiUtil;
import de.Z7534.ztone.claims.util.ItemBuilder;
import de.Z7534.ztone.claims.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.Map;

public final class ClaimSettingsGui extends Gui {

    private final ClaimsContext ctx;
    private final Claim claim;

    private static final int SLOT_BACK = 0;
    private static final int SLOT_RENAME = 2;
    private static final int SLOT_DELETE = 4;
    private static final int SLOT_TRUST = 6;
    private static final int SLOT_BAN = 8;
    private static final int SLOT_TRANSFER = 13;

    public ClaimSettingsGui(ClaimsContext ctx, Claim claim) {
        super(18, Text.line("<dark_green>" + Text.escape(claim.name())));
        this.ctx = ctx;
        this.claim = claim;
        build();
    }

    private void build() {
        inventory.setItem(SLOT_BACK, GuiUtil.backButton(ctx.config().itemModel("claim-settings-back")));

        inventory.setItem(SLOT_RENAME, ItemBuilder.of(Material.NAME_TAG)
                .name(Text.line("<yellow>Umbenennen"))
                .lore(Text.line("<gray>Vergib einen neuen Namen für diesen Claim."), Text.line("<gray>Linksklick, umbenennen."))
                .model(ctx.config().itemModel("claim-settings-rename"))
                .build());

        inventory.setItem(SLOT_DELETE, ItemBuilder.of(Material.TNT)
                .name(Text.line("<red>Löschen"))
                .lore(Text.line("<gray>Gibt diesen Claim wieder frei."), Text.line("<gray>Nur für den Besitzer."), Text.line("<gray>Linksklick, löschen."))
                .model(ctx.config().itemModel("claim-settings-delete"))
                .build());

        inventory.setItem(SLOT_TRUST, ItemBuilder.of(Material.SHIELD)
                .name(Text.line("<aqua>Trust"))
                .lore(Text.line("<gray>Verwalte Rechte von Freunden, Trustern & Besuchern."), Text.line("<gray>Linksklick, öffnen."))
                .model(ctx.config().itemModel("claim-settings-trust"))
                .build());

        inventory.setItem(SLOT_BAN, ItemBuilder.of(Material.BARRIER)
                .name(Text.line("<dark_red>Bannen"))
                .lore(Text.line("<gray>Verbanne Spieler von diesem Claim."), Text.line("<gray>Linksklick, öffnen."))
                .model(ctx.config().itemModel("claim-settings-ban"))
                .build());

        inventory.setItem(SLOT_TRANSFER, ItemBuilder.of(Material.ENDER_PEARL)
                .name(Text.line("<gold>Übertragen"))
                .lore(
                        Text.line("<gray>Übergib diesen Claim komplett an einen"),
                        Text.line("<gray>anderen Spieler (inkl. Trust & Bans)."),
                        Text.line("<gray>Nur für den Besitzer."),
                        Text.line("<gray>Linksklick, öffnen.")
                )
                .model(ctx.config().itemModel("claim-settings-transfer"))
                .build());
    }

    @Override
    public void onClick(Player player, int slot, ClickType clickType) {
        switch (slot) {
            case SLOT_BACK -> new ClaimsListGui(ctx, player).open(player);
            case SLOT_RENAME -> startRename(player);
            case SLOT_DELETE -> openDeleteConfirm(player);
            case SLOT_TRUST -> new TrustGui(ctx, claim).open(player);
            case SLOT_BAN -> new BanGui(ctx, claim).open(player);
            case SLOT_TRANSFER -> openTransfer(player);
            default -> {
            }
        }
    }

    private void openTransfer(Player player) {
        if (!player.hasPermission("claims.transfer")) {
            player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("no-permission")));
            return;
        }
        Claim current = ctx.claims().getOwned(player.getUniqueId(), claim.id());
        if (current == null) {
            player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("transfer-claim-gone")));
            player.closeInventory();
            return;
        }
        if (!current.isOwner(player.getUniqueId())) {
            player.sendMessage(Text.line(ctx.config().prefix() + "<red>Nur der eigentliche Claim-Besitzer kann diesen Claim übertragen."));
            return;
        }
        new TransferPlayerGui(ctx, current).open(player);
    }

    private void startRename(Player player) {
        player.closeInventory();
        int seconds = ctx.config().renameInputTimeSeconds();
        player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("rename-prompt"),
                Map.of("sekunden", String.valueOf(seconds))));

        ctx.chatInput().await(player, seconds, input -> {
            Claim current = ctx.claims().getOwned(player.getUniqueId(), claim.id());
            if (current == null) {
                player.sendMessage(Text.line(ctx.config().prefix() + "<red>Dieser Claim existiert nicht mehr."));
                return;
            }
            String error = validateName(input);
            if (error != null) {
                player.sendMessage(Text.line(ctx.config().prefix() + error));
            } else {
                ctx.claims().renameClaim(current, input);
                player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("claim-renamed"),
                        Map.of("name", input)));
            }
            Claim reload = ctx.claims().getOwned(player.getUniqueId(), claim.id());
            if (reload != null) {
                new ClaimSettingsGui(ctx, reload).open(player);
            }
        }, () -> {
            player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("rename-timeout")));
            Claim reload = ctx.claims().getOwned(player.getUniqueId(), claim.id());
            if (reload != null) {
                new ClaimSettingsGui(ctx, reload).open(player);
            }
        });
    }

    private String validateName(String input) {
        if (input.length() < ctx.config().renameMinLength()) {
            return ctx.config().message("rename-too-short").replace("{min}", String.valueOf(ctx.config().renameMinLength()));
        }
        if (input.length() > ctx.config().renameMaxLength()) {
            return ctx.config().message("rename-too-long").replace("{max}", String.valueOf(ctx.config().renameMaxLength()));
        }
        String forbidden = ctx.config().renameForbiddenCharacters();
        for (char c : forbidden.toCharArray()) {
            if (input.indexOf(c) >= 0) {
                return ctx.config().message("rename-forbidden-chars");
            }
        }
        return null;
    }

    private void openDeleteConfirm(Player player) {
        if (!claim.isOwner(player.getUniqueId())) {
            player.sendMessage(Text.line(ctx.config().prefix() + "<red>Nur der eigentliche Claim-Besitzer kann diesen Claim löschen."));
            return;
        }
        new ConfirmGui(ctx, Text.line("<red>Claim wirklich löschen?"),
                List.of(Text.line("<gray>" + Text.escape(claim.name()) + " wird unwiderruflich freigegeben.")),
                () -> {
                    Claim current = ctx.claims().getOwned(player.getUniqueId(), claim.id());
                    if (current != null && current.isOwner(player.getUniqueId())) {
                        ctx.claims().deleteClaim(current);
                        player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("claim-deleted")));
                    }
                    player.closeInventory();
                },
                () -> new ClaimSettingsGui(ctx, claim).open(player)
        ).open(player);
    }
}
