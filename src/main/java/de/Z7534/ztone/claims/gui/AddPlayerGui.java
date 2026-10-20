package de.Z7534.ztone.claims.gui;

import de.Z7534.ztone.claims.ClaimsContext;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.util.GuiUtil;
import de.Z7534.ztone.claims.util.ItemBuilder;
import de.Z7534.ztone.claims.util.Text;
import de.Z7534.ztone.claims.util.VanishUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AddPlayerGui extends Gui {

    public enum Mode {TRUST, BAN}

    private static final int SLOT_BACK = 36;
    private static final int SLOT_SEARCH = 44;
    private static final int SLOT_PREV = 38;
    private static final int SLOT_NEXT = 42;
    private static final int PER_PAGE = 36;

    private final ClaimsContext ctx;
    private final Claim claim;
    private final Mode mode;
    private final List<UUID> listed = new ArrayList<>();
    private final int page;
    private final int pages;

    public AddPlayerGui(ClaimsContext ctx, Claim claim, Mode mode) {
        this(ctx, claim, mode, 0);
    }

    public AddPlayerGui(ClaimsContext ctx, Claim claim, Mode mode, int page) {
        super(45, Text.line(mode == Mode.TRUST ? "<dark_green>Spieler trusten" : "<dark_red>Spieler bannen"));
        this.ctx = ctx;
        this.claim = claim;
        this.mode = mode;
        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            UUID uuid = online.getUniqueId();
            if (uuid.equals(claim.owner())) continue;
            if (VanishUtil.isVanished(online)) continue;
            if (mode == Mode.TRUST && claim.trusted().containsKey(uuid)) continue;
            if (mode == Mode.BAN && claim.banned().contains(uuid)) continue;
            candidates.add(online);
        }
        this.page = GuiUtil.clampPage(page, candidates.size(), PER_PAGE);
        this.pages = GuiUtil.pageCount(candidates.size(), PER_PAGE);
        build(candidates);
    }

    private void build(List<Player> candidates) {
        int slot = 0;
        int from = page * PER_PAGE;
        int to = Math.min(candidates.size(), from + PER_PAGE);
        for (Player online : candidates.subList(from, to)) {
            listed.add(online.getUniqueId());
            var head = ItemBuilder.playerHead(online.getUniqueId(), online.getName());
            if (mode == Mode.BAN) {
                var meta = head.getItemMeta();
                meta.lore(List.of(
                        Text.line("<gray>Linksklick: <white>Auf diesem Claim bannen"),
                        Text.line("<yellow>Shift-Klick: <gold>Auf allen deinen Claims bannen (Bulk-Ban)")
                ));
                head.setItemMeta(meta);
            }
            inventory.setItem(slot, head);
            slot++;
        }

        inventory.setItem(SLOT_BACK, GuiUtil.backButton(ctx.config().itemModel("addplayer-back")));
        if (page > 0) {
            inventory.setItem(SLOT_PREV, GuiUtil.prevPageButton(page + 1, pages, ctx.config().itemModel("page-prev")));
        }
        if (page < pages - 1) {
            inventory.setItem(SLOT_NEXT, GuiUtil.nextPageButton(page + 1, pages, ctx.config().itemModel("page-next")));
        }

        inventory.setItem(SLOT_SEARCH, ItemBuilder.of(Material.COMPASS)
                .name(Text.line("<yellow>Spieler suchen"))
                .lore(Text.line("<gray>Auch für Spieler, die gerade nicht online sind."), Text.line("<gray>Linksklick, öffnen."))
                .model(ctx.config().itemModel("addplayer-search"))
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
            openParent(player, current);
            return;
        }
        if (slot == SLOT_SEARCH) {
            startSearch(player, current);
            return;
        }
        if (slot == SLOT_PREV && page > 0) {
            new AddPlayerGui(ctx, current, mode, page - 1).open(player);
            return;
        }
        if (slot == SLOT_NEXT && page < pages - 1) {
            new AddPlayerGui(ctx, current, mode, page + 1).open(player);
            return;
        }
        if (slot >= 0 && slot < listed.size()) {
            UUID target = listed.get(slot);
            String name = Bukkit.getOfflinePlayer(target).getName();
            String resolvedName = name != null ? name : target.toString();
            if (mode == Mode.BAN && clickType.isShiftClick()) {
                applyBulkBan(player, target, resolvedName);
            } else {
                apply(player, current, target, resolvedName);
            }
        }
    }

    private void openParent(Player player, Claim current) {
        if (mode == Mode.TRUST) {
            new TrustGui(ctx, current).open(player);
        } else {
            new BanGui(ctx, current).open(player);
        }
    }

    private void apply(Player player, Claim current, UUID target, String name) {
        if (mode == Mode.BAN && current.isOwner(target)) {
            player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("cannot-ban-owner")));
            openParent(player, current);
            return;
        }
        ctx.claims().ensurePlayerData(target, name);
        if (mode == Mode.TRUST) {
            ctx.claims().setTrust(current, target, de.Z7534.ztone.claims.model.ClaimPermission.ALL_MASK);
            player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("player-trusted"), Map.of("spieler", name)));
        } else {
            ctx.claims().setBan(current, target);
            player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("player-banned"), Map.of("spieler", name)));
            ctx.banEnforcer().handleBan(current, target, player.getName());
        }
        Claim reload = ctx.claims().getOwned(player.getUniqueId(), current.id());
        if (reload != null) {
            openParent(player, reload);
        } else {
            player.closeInventory();
        }
    }

    private void applyBulkBan(Player player, UUID target, String name) {
        if (player.getUniqueId().equals(target)) {
            player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("cannot-ban-owner")));
            return;
        }
        ctx.claims().ensurePlayerData(target, name);
        List<Claim> ownedClaims = ctx.claims().claimsOf(player.getUniqueId());
        for (Claim c : ownedClaims) {
            ctx.claims().setBan(c, target);
            ctx.banEnforcer().handleBan(c, target, player.getName());
        }
        player.sendMessage(Text.line(ctx.config().prefix() + "<green>Spieler <gold>" + name + " <green>wurde auf allen deinen Claims (" + ownedClaims.size() + ") gebannt!"));
        Claim reload = ctx.claims().getOwned(player.getUniqueId(), claim.id());
        if (reload != null) {
            openParent(player, reload);
        } else {
            player.closeInventory();
        }
    }

    private void startSearch(Player player, Claim current) {
        player.closeInventory();
        int seconds = ctx.config().searchInputTimeSeconds();
        player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("search-prompt"),
                Map.of("sekunden", String.valueOf(seconds))));

        ctx.chatInput().await(player, seconds, input -> {
            ctx.lookup().resolve(input).thenAccept(resultOpt -> Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
                Claim c = ctx.claims().getOwned(player.getUniqueId(), current.id());
                if (c == null) {
                    player.closeInventory();
                    return;
                }
                if (resultOpt.isEmpty()) {
                    player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("search-not-found")));
                    openParent(player, c);
                    return;
                }
                var resolved = resultOpt.get();
                apply(player, c, resolved.uuid(), resolved.name());
            }));
        }, () -> {
            player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("search-timeout")));
            Claim c = ctx.claims().getOwned(player.getUniqueId(), current.id());
            if (c != null) openParent(player, c);
        });
    }
}
