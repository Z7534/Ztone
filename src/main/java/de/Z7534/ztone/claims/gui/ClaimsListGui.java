package de.Z7534.ztone.claims.gui;

import de.Z7534.ztone.claims.ClaimsContext;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.util.GuiUtil;
import de.Z7534.ztone.claims.util.ItemBuilder;
import de.Z7534.ztone.claims.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;

public final class ClaimsListGui extends Gui {

    private static final int MAX_CLAIM_SLOTS = 36;
    private static final int SLOT_BACK = 40;
    private static final int SLOT_PREV = 38;
    private static final int SLOT_NEXT = 42;

    private final ClaimsContext ctx;
    private final List<Claim> claims;
    private final List<Claim> pageClaims = new ArrayList<>();
    private final Player viewer;
    private final int page;

    public ClaimsListGui(ClaimsContext ctx, Player player) {
        this(ctx, player, 0);
    }

    public ClaimsListGui(ClaimsContext ctx, Player player, int page) {
        super(45, Text.line("<dark_green>Meine Claims"));
        this.ctx = ctx;
        this.viewer = player;
        List<Claim> all = new ArrayList<>(ctx.claims().claimsOf(player.getUniqueId()));
        for (Claim managed : ctx.claims().claimsManagedBy(player.getUniqueId())) {
            if (!all.contains(managed)) {
                all.add(managed);
            }
        }
        this.claims = all;
        this.page = GuiUtil.clampPage(page, claims.size(), MAX_CLAIM_SLOTS);
        build();
    }

    private void build() {
        int pages = GuiUtil.pageCount(claims.size(), MAX_CLAIM_SLOTS);
        int from = page * MAX_CLAIM_SLOTS;
        int to = Math.min(claims.size(), from + MAX_CLAIM_SLOTS);
        int slot = 0;
        for (Claim claim : claims.subList(from, to)) {
            pageClaims.add(claim);
            int[] box = claim.boundingBox();
            int[] corner = claim.cornerBlockCoords();

            List<Component> lore = new ArrayList<>();
            if (claim.isManager(viewer.getUniqueId()) && !claim.isOwner(viewer.getUniqueId())) {
                String ownerName = Bukkit.getOfflinePlayer(claim.owner()).getName();
                if (ownerName == null) ownerName = claim.owner().toString().substring(0, 8);
                lore.add(Text.line("<gold><bold>★ Truster (Verwalter)</bold></gold>"));
                lore.add(Text.line("<gray>Besitzer: <white>" + ownerName));
            } else {
                lore.add(Text.line("<green><bold>✔ Besitzer</bold></green>"));
            }
            lore.add(Text.line("<gray>Dimension: <white>" + claim.world()));
            lore.add(Text.line("<gray>Chunks: <white>" + claim.chunks().size() + " - " + box[4] + "x" + box[5]));
            lore.add(Text.line("<gray>Fläche: <white>" + claim.areaBlocks() + " Blöcke"));
            lore.add(Text.line("<gray>Ecke: <white>" + corner[0] + ", " + corner[1]));
            lore.add(Component.empty());
            lore.add(Text.line("<gray>Linksklick, Einstellungen öffnen."));

            inventory.setItem(slot, ItemBuilder.of(Material.GRASS_BLOCK)
                    .name(Text.line("<green>" + Text.escape(claim.name())))
                    .lore(lore)
                    .model(ctx.config().itemModel("claims-list-entry"))
                    .build());
            slot++;
        }

        inventory.setItem(SLOT_BACK, GuiUtil.backButton(ctx.config().itemModel("claims-list-back")));
        if (page > 0) {
            inventory.setItem(SLOT_PREV, GuiUtil.prevPageButton(page + 1, pages, ctx.config().itemModel("page-prev")));
        }
        if (page < pages - 1) {
            inventory.setItem(SLOT_NEXT, GuiUtil.nextPageButton(page + 1, pages, ctx.config().itemModel("page-next")));
        }
    }

    @Override
    public void onClick(Player player, int slot, ClickType clickType) {
        if (slot == SLOT_BACK) {
            new MainMenuGui(ctx, player).open(player);
            return;
        }
        if (slot == SLOT_PREV && page > 0) {
            new ClaimsListGui(ctx, player, page - 1).open(player);
            return;
        }
        if (slot == SLOT_NEXT && page < GuiUtil.pageCount(claims.size(), MAX_CLAIM_SLOTS) - 1) {
            new ClaimsListGui(ctx, player, page + 1).open(player);
            return;
        }
        if (slot < 0 || slot >= MAX_CLAIM_SLOTS || slot >= pageClaims.size()) return;
        Claim claim = pageClaims.get(slot);
        if (ctx.claims().getOwned(player.getUniqueId(), claim.id()) == null) return;
        new ClaimSettingsGui(ctx, claim).open(player);
    }
}
