package de.Z7534.ztone.claims.gui;

import de.Z7534.ztone.claims.ClaimsContext;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.model.TrustedPlayer;
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

public final class TrustGui extends Gui {

    public static final int SLOT_BACK = 27;
    public static final int SLOT_ADD = 30;
    public static final int SLOT_VISITORS = 33;
    public static final int SLOT_CLAN_TRUST = 31;
    public static final int SLOT_ALLY_TRUST = 32;
    private static final int SLOT_PREV = 28;
    private static final int SLOT_NEXT = 34;
    private static final int PER_PAGE = 27;

    private final ClaimsContext ctx;
    private final Claim claim;
    private final List<UUID> trustedOrder = new ArrayList<>();
    private final int page;
    private final int pages;

    public TrustGui(ClaimsContext ctx, Claim claim) {
        this(ctx, claim, 0);
    }

    public TrustGui(ClaimsContext ctx, Claim claim, int page) {
        super(36, Text.line("<dark_green>Trust - " + Text.escape(claim.name())));
        this.ctx = ctx;
        this.claim = claim;
        this.page = GuiUtil.clampPage(page, claim.trusted().size(), PER_PAGE);
        this.pages = GuiUtil.pageCount(claim.trusted().size(), PER_PAGE);
        build();
    }

    private void build() {
        int slot = 0;
        List<Map.Entry<UUID, TrustedPlayer>> all = new ArrayList<>(claim.trusted().entrySet());
        int from = page * PER_PAGE;
        int to = Math.min(all.size(), from + PER_PAGE);
        for (Map.Entry<UUID, TrustedPlayer> entry : all.subList(from, to)) {
            UUID uuid = entry.getKey();
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name == null) name = uuid.toString().substring(0, 8);
            trustedOrder.add(uuid);

            boolean isManager = claim.isManager(uuid);
            var head = de.Z7534.ztone.claims.util.ItemBuilder.playerHead(uuid, name);
            var meta = head.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            if (isManager) {
                lore.add(Text.line("<gold><bold>★ TRUSTER (CO-OWNER)</bold></gold>"));
                lore.add(Text.line("<gray>Besitzt volle Verwalter- & Baurechte."));
                lore.add(Text.line("<gray>Kann Spieler trusten & bannen."));
                lore.add(net.kyori.adventure.text.Component.empty());
            }
            lore.add(Text.line("<gray>Linksklick: <white>Rechte bearbeiten"));
            lore.add(Text.line("<yellow>Shift-Klick: <white>" + (isManager ? "<gold>Truster-Status entfernen" : "<gold>Zum Truster (Co-Owner) befördern")));
            lore.add(Text.line("<red>Rechtsklick: <white>Trust entziehen"));
            meta.lore(lore);
            head.setItemMeta(meta);
            inventory.setItem(slot, head);
            slot++;
        }

        inventory.setItem(SLOT_BACK, GuiUtil.backButton(ctx.config().itemModel("trust-back")));
        if (page > 0) {
            inventory.setItem(SLOT_PREV, GuiUtil.prevPageButton(page + 1, pages, ctx.config().itemModel("page-prev")));
        }
        if (page < pages - 1) {
            inventory.setItem(SLOT_NEXT, GuiUtil.nextPageButton(page + 1, pages, ctx.config().itemModel("page-next")));
        }

        inventory.setItem(SLOT_ADD, ItemBuilder.of(Material.LIME_DYE)
                .name(Text.line("<green>Spieler hinzufügen"))
                .lore(Text.line("<gray>Gib einem Spieler Trust auf diesem Claim."), Text.line("<gray>Linksklick, öffnen."))
                .model(ctx.config().itemModel("trust-add-player"))
                .build());

        inventory.setItem(SLOT_VISITORS, ItemBuilder.of(Material.OAK_FENCE_GATE)
                .name(Text.line("<yellow>Besucher-Einstellungen"))
                .lore(Text.line("<gray>Rechte für alle Spieler ohne Trust."), Text.line("<gray>Linksklick, öffnen."))
                .model(ctx.config().itemModel("trust-visitor-settings"))
                .build());

        boolean clanTrust = claim.isClanTrustEnabled();
        inventory.setItem(SLOT_CLAN_TRUST, ItemBuilder.of(clanTrust ? Material.EMERALD_BLOCK : Material.GRAY_CONCRETE)
                .name(Text.line(clanTrust ? "<green><bold>Clan-Trust: AN</bold></green>" : "<gray>Clan-Trust: <red>AUS</red></gray>"))
                .lore(
                        Text.line("<gray>Vertraut automatisch allen Mitgliedern"),
                        Text.line("<gray>deines Clans auf diesem Claim."),
                        net.kyori.adventure.text.Component.empty(),
                        Text.line("<yellow>Linksklick: <white>Umschalten")
                )
                .build());

        boolean allyTrust = claim.isAllyTrustEnabled();
        inventory.setItem(SLOT_ALLY_TRUST, ItemBuilder.of(allyTrust ? Material.DIAMOND_BLOCK : Material.GRAY_CONCRETE)
                .name(Text.line(allyTrust ? "<aqua><bold>Allianz-Trust: AN</bold></aqua>" : "<gray>Allianz-Trust: <red>AUS</red></gray>"))
                .lore(
                        Text.line("<gray>Vertraut automatisch allen Mitgliedern"),
                        Text.line("<gray>befreundeter Allianzen deines Clans."),
                        net.kyori.adventure.text.Component.empty(),
                        Text.line("<yellow>Linksklick: <white>Umschalten")
                )
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
            new AddPlayerGui(ctx, current, AddPlayerGui.Mode.TRUST).open(player);
            return;
        }
        if (slot == SLOT_PREV && page > 0) {
            new TrustGui(ctx, current, page - 1).open(player);
            return;
        }
        if (slot == SLOT_NEXT && page < pages - 1) {
            new TrustGui(ctx, current, page + 1).open(player);
            return;
        }
        if (slot == SLOT_VISITORS) {
            new PermissionsGui(ctx, current, null).open(player);
            return;
        }
        if (slot == SLOT_CLAN_TRUST) {
            ctx.claims().setClanTrust(current, !current.isClanTrustEnabled());
            player.sendMessage(Text.line(ctx.config().prefix() + "<green>Clan-Trust ist nun " + (current.isClanTrustEnabled() ? "<bold>aktiviert</bold>" : "<red>deaktiviert</red>") + "."));
            new TrustGui(ctx, current, page).open(player);
            return;
        }
        if (slot == SLOT_ALLY_TRUST) {
            ctx.claims().setAllyTrust(current, !current.isAllyTrustEnabled());
            player.sendMessage(Text.line(ctx.config().prefix() + "<aqua>Allianz-Trust ist nun " + (current.isAllyTrustEnabled() ? "<bold>aktiviert</bold>" : "<red>deaktiviert</red>") + "."));
            new TrustGui(ctx, current, page).open(player);
            return;
        }
        if (slot >= 0 && slot < trustedOrder.size()) {
            UUID target = trustedOrder.get(slot);
            if (clickType.isShiftClick()) {
                if (!current.isOwner(player.getUniqueId())) {
                    player.sendMessage(Text.line(ctx.config().prefix() + "<red>Nur der Eigentümer des Claims kann Truster ernennen oder entfernen!"));
                    return;
                }
                boolean nowManager = ctx.claims().toggleManager(current, target);
                String targetName = Bukkit.getOfflinePlayer(target).getName();
                String displayName = targetName != null ? targetName : target.toString();
                if (nowManager) {
                    player.sendMessage(Text.line(ctx.config().prefix() + "<gold>Spieler <yellow>" + displayName + " <gold>ist nun ein <bold>Truster (Co-Owner)</bold>!"));
                } else {
                    player.sendMessage(Text.line(ctx.config().prefix() + "<yellow>Truster-Status von <gold>" + displayName + " <yellow>wurde entfernt."));
                }
                new TrustGui(ctx, current, page).open(player);
                return;
            }
            if (clickType == ClickType.RIGHT) {
                confirmUntrust(player, current, target);
            } else {
                new PermissionsGui(ctx, current, target).open(player);
            }
        }
    }

    private void confirmUntrust(Player player, Claim current, UUID target) {
        String name = Bukkit.getOfflinePlayer(target).getName();
        new ConfirmGui(ctx, Text.line("<red>Trust wirklich entziehen?"),
                List.of(Text.line("<gray>" + (name != null ? name : target) + " wird zum Besucher.")),
                () -> {
                    Claim c = ctx.claims().getOwned(player.getUniqueId(), current.id());
                    if (c != null) {
                        ctx.claims().removeTrust(c, target);
                        player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("player-untrusted"),
                                Map.of("spieler", name != null ? name : target.toString())));
                        new TrustGui(ctx, c, page).open(player);
                    } else {
                        player.closeInventory();
                    }
                },
                () -> new TrustGui(ctx, current, page).open(player)
        ).open(player);
    }
}
