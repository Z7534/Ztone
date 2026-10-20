package de.Z7534.ztone.claims.gui;

import de.Z7534.ztone.claims.ClaimsContext;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.util.GuiUtil;
import de.Z7534.ztone.claims.util.ItemBuilder;
import de.Z7534.ztone.claims.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BanGui extends Gui {

    public static final int SLOT_BACK = 27;
    public static final int SLOT_ADD = 31;
    private static final int SLOT_PREV = 29;
    private static final int SLOT_NEXT = 33;
    private static final int PER_PAGE = 27;

    private final ClaimsContext ctx;
    private final Claim claim;
    private final List<UUID> bannedOrder = new ArrayList<>();
    private final int page;
    private final int pages;

    public BanGui(ClaimsContext ctx, Claim claim) {
        this(ctx, claim, 0);
    }

    public BanGui(ClaimsContext ctx, Claim claim, int page) {
        super(36, Text.line("<dark_red>Bans - " + Text.escape(claim.name())));
        this.ctx = ctx;
        this.claim = claim;
        this.page = GuiUtil.clampPage(page, claim.banned().size(), PER_PAGE);
        this.pages = GuiUtil.pageCount(claim.banned().size(), PER_PAGE);
        build();
    }

    private void build() {
        int slot = 0;
        List<UUID> all = new ArrayList<>(claim.banned());
        int from = page * PER_PAGE;
        int to = Math.min(all.size(), from + PER_PAGE);
        for (UUID uuid : all.subList(from, to)) {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name == null) name = uuid.toString().substring(0, 8);
            bannedOrder.add(uuid);

            var head = ItemBuilder.playerHead(uuid, name);
            var meta = head.getItemMeta();
            meta.lore(List.of(
                    Text.line("<gray>Rechtsklick: <white>Auf diesem Claim entbannen"),
                    Text.line("<yellow>Shift-Rechtsklick: <gold>Auf allen deinen Claims entbannen (Bulk-Unban)")
            ));
            head.setItemMeta(meta);
            inventory.setItem(slot, head);
            slot++;
        }

        inventory.setItem(SLOT_BACK, GuiUtil.backButton(ctx.config().itemModel("ban-back")));
        if (page > 0) {
            inventory.setItem(SLOT_PREV, GuiUtil.prevPageButton(page + 1, pages, ctx.config().itemModel("page-prev")));
        }
        if (page < pages - 1) {
            inventory.setItem(SLOT_NEXT, GuiUtil.nextPageButton(page + 1, pages, ctx.config().itemModel("page-next")));
        }

        inventory.setItem(SLOT_ADD, ItemBuilder.of(Material.RED_DYE)
                .name(Text.line("<red>Spieler bannen"))
                .lore(Text.line("<gray>Verbanne einen Spieler von diesem Claim."), Text.line("<gray>Linksklick, öffnen."))
                .model(ctx.config().itemModel("ban-add-player"))
                .build());
    }

    @Override
    public void onClick(Player player, int slot, ClickType clickType) {
        Claim current = ctx.claims().getOwned(player.getUniqueId(), claim.id());
        if (current == null) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_BACK) {
            new ClaimSettingsGui(ctx, current).open(player);
            return;
        }
        if (slot == SLOT_ADD) {
            new AddPlayerGui(ctx, current, AddPlayerGui.Mode.BAN).open(player);
            return;
        }
        if (slot == SLOT_PREV && page > 0) {
            new BanGui(ctx, current, page - 1).open(player);
            return;
        }
        if (slot == SLOT_NEXT && page < pages - 1) {
            new BanGui(ctx, current, page + 1).open(player);
            return;
        }
        if (slot >= 0 && slot < bannedOrder.size()) {
            UUID target = bannedOrder.get(slot);
            if (clickType == ClickType.SHIFT_RIGHT) {
                confirmBulkUnban(player, current, target);
            } else if (clickType == ClickType.RIGHT) {
                confirmUnban(player, current, target);
            }
        }
    }

    private void confirmUnban(Player player, Claim current, UUID target) {
        String name = Bukkit.getOfflinePlayer(target).getName();
        new ConfirmGui(ctx, Text.line("<green>Spieler wirklich entbannen?"),
                List.of(Text.line("<gray>" + (name != null ? name : target) + " darf den Claim wieder betreten (als Besucher).")),
                () -> {
                    Claim c = ctx.claims().getOwned(player.getUniqueId(), current.id());
                    if (c != null) {
                        ctx.claims().removeBan(c, target);
                        player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("player-unbanned"),
                                Map.of("spieler", name != null ? name : target.toString())));
                        new BanGui(ctx, c, page).open(player);
                    } else {
                        player.closeInventory();
                    }
                },
                () -> new BanGui(ctx, current, page).open(player)
        ).open(player);
    }

    private void confirmBulkUnban(Player player, Claim current, UUID target) {
        String name = Bukkit.getOfflinePlayer(target).getName();
        String displayName = name != null ? name : target.toString();
        new ConfirmGui(ctx, Text.line("<green>Spieler auf ALLEN Claims entbannen?"),
                List.of(Text.line("<gray>" + displayName + " wird auf allen deinen Claims entbannt.")),
                () -> {
                    List<Claim> ownedClaims = ctx.claims().claimsOf(player.getUniqueId());
                    for (Claim c : ownedClaims) {
                        ctx.claims().removeBan(c, target);
                    }
                    player.sendMessage(Text.line(ctx.config().prefix() + "<green>Spieler <gold>" + displayName + " <green>wurde auf allen deinen Claims (" + ownedClaims.size() + ") entbannt!"));
                    Claim reload = ctx.claims().getOwned(player.getUniqueId(), current.id());
                    if (reload != null) {
                        new BanGui(ctx, reload, page).open(player);
                    } else {
                        player.closeInventory();
                    }
                },
                () -> new BanGui(ctx, current, page).open(player)
        ).open(player);
    }
}
