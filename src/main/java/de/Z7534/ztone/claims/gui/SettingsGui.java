package de.Z7534.ztone.claims.gui;

import de.Z7534.ztone.claims.ClaimsContext;
import de.Z7534.ztone.claims.model.PlayerData;
import de.Z7534.ztone.claims.util.GuiUtil;
import de.Z7534.ztone.claims.util.ItemBuilder;
import de.Z7534.ztone.claims.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

public final class SettingsGui extends Gui {

    private static final int SLOT_BACK = 1;
    private static final int SLOT_CLAIM_MAP = 3;
    private static final int SLOT_BOARD_LINE = 4;
    private static final int SLOT_FENCE_LINE = 5;
    private static final int SLOT_ENTRY_HINT = 6;
    private static final int SLOT_EXPLOSIONS_TOGGLE = 7;

    private final ClaimsContext ctx;

    public SettingsGui(ClaimsContext ctx, Player player) {
        super(9, Text.line("<dark_green>Einstellungen"));
        this.ctx = ctx;
        build(player);
    }

    private void build(Player player) {
        PlayerData pd = ctx.claims().getOrCreatePlayerData(player.getUniqueId(), player.getName());

        inventory.setItem(SLOT_BACK, GuiUtil.backButton(ctx.config().itemModel("settings-back")));

        inventory.setItem(SLOT_CLAIM_MAP, ItemBuilder.of(Material.MAP)
                .name(Text.line("<yellow>Claim-Karte" + GuiUtil.toggleSuffix(pd.claimMapEnabled())))
                .lore(Text.line("<gray>Hält beim Claimen eine Übersichtskarte in der Nebenhand."),
                        Text.line("<gray>Blau: dein Claim, Rot: fremd, Grün: frei, Gelb: markiert."),
                        Text.line("<gray>Linksklick, umschalten."))
                .model(ctx.config().itemModel("settings-claimmap"))
                .build());

        inventory.setItem(SLOT_BOARD_LINE, ItemBuilder.of(Material.DIRT_PATH)
                .name(Text.line("<yellow>Bodenlinie" + GuiUtil.toggleSuffix(pd.boardLineEnabled())))
                .lore(Text.line("<gray>Zeigt Claim-Grenzen am Boden an."),
                        Text.line("<gray>Grün: du, Blau: getrustet, Rot: fremd."),
                        Text.line("<gray>Linksklick, umschalten."))
                .model(ctx.config().itemModel("settings-boardline"))
                .build());

        inventory.setItem(SLOT_FENCE_LINE, ItemBuilder.of(Material.OAK_FENCE)
                .name(Text.line("<yellow>Zaunlinie" + GuiUtil.toggleSuffix(pd.fenceLineEnabled())))
                .lore(Text.line("<gray>Zeigt Claim-Grenzen erhöht (zaunartig) an."),
                        Text.line("<gray>Grün: du, Blau: getrustet, Rot: fremd."),
                        Text.line("<gray>Linksklick, umschalten."))
                .model(ctx.config().itemModel("settings-fenceline"))
                .build());

        inventory.setItem(SLOT_ENTRY_HINT, ItemBuilder.of(Material.OAK_SIGN)
                .name(Text.line("<yellow>Eintrittshinweis" + GuiUtil.toggleSuffix(pd.entryHintEnabled())))
                .lore(Text.line("<gray>Zeigt beim Betreten eines Claims einen Hinweis."),
                        Text.line("<gray>Linksklick, umschalten."))
                .model(ctx.config().itemModel("settings-entryhint"))
                .build());

        boolean explosionsEnabled = pd.explosionsEnabled();
        inventory.setItem(SLOT_EXPLOSIONS_TOGGLE, ItemBuilder.of(Material.TNT)
                .name(Text.line("<yellow>" + ctx.config().message("settings.explosions.title") + GuiUtil.toggleSuffix(explosionsEnabled)))
                .lore(Text.line("<gray>" + ctx.config().message("settings.explosions.enabled") + ": " + (explosionsEnabled ?
                        "<green>" + ctx.config().message("settings.explosions.enabled") + "</green>" :
                        "<red>" + ctx.config().message("settings.explosions.disabled") + "</red>")),
                        Text.line("<gray>Gilt für alle deine Claims."),
                        Text.line("<gray>Linksklick, umschalten."))
                .model(ctx.config().itemModel("settings-explosions"))
                .build());
    }

    @Override
    public void onClick(Player player, int slot, ClickType clickType) {
        PlayerData pd = ctx.claims().getOrCreatePlayerData(player.getUniqueId(), player.getName());
        switch (slot) {
            case SLOT_BACK -> new MainMenuGui(ctx, player).open(player);
            case SLOT_CLAIM_MAP -> {
                pd.setClaimMapEnabled(!pd.claimMapEnabled());
                ctx.claims().savePlayerAsync(pd);
                if (ctx.selection().isActive(player.getUniqueId()) && ctx.claimMap() != null) {
                    if (pd.claimMapEnabled()) {
                        ctx.claimMap().giveMap(player);
                    } else {
                        ctx.claimMap().restoreOffHand(player);
                    }
                }
                new SettingsGui(ctx, player).open(player);
            }
            case SLOT_BOARD_LINE -> {
                pd.setBoardLineEnabled(!pd.boardLineEnabled());
                ctx.claims().savePlayerAsync(pd);
                new SettingsGui(ctx, player).open(player);
            }
            case SLOT_FENCE_LINE -> {
                pd.setFenceLineEnabled(!pd.fenceLineEnabled());
                ctx.claims().savePlayerAsync(pd);
                new SettingsGui(ctx, player).open(player);
            }
            case SLOT_ENTRY_HINT -> {
                pd.setEntryHintEnabled(!pd.entryHintEnabled());
                ctx.claims().savePlayerAsync(pd);
                new SettingsGui(ctx, player).open(player);
            }
            case SLOT_EXPLOSIONS_TOGGLE -> {
                pd.setExplosionsEnabled(!pd.explosionsEnabled());
                ctx.claims().savePlayerAsync(pd);
                new SettingsGui(ctx, player).open(player);
            }
            default -> {
            }
        }
    }
}
