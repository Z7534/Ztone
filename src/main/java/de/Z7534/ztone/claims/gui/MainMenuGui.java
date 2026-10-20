package de.Z7534.ztone.claims.gui;

import de.Z7534.ztone.claims.ClaimsContext;
import de.Z7534.ztone.claims.model.PlayerData;
import de.Z7534.ztone.claims.util.ItemBuilder;
import de.Z7534.ztone.claims.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

public final class MainMenuGui extends Gui {

    private static final int PROGRESS_BAR_SEGMENTS = 20;

    private final ClaimsContext ctx;

    public MainMenuGui(ClaimsContext ctx, Player player) {
        super(18, Text.line("<dark_green>Claims"));
        this.ctx = ctx;
        build(player);
    }

    private void build(Player player) {
        int used = ctx.claims().getUsedClaimSlots(player.getUniqueId());
        int max = ctx.claims().getMaxClaimSlots(player);
        PlayerData pd = ctx.claims().getOrCreatePlayerData(player.getUniqueId(), player.getName());

        long intervalSeconds = (long) ctx.config().awardIntervalMinutes() * 60L;
        long remainingSeconds = Math.max(0L, intervalSeconds - pd.playtimeSeconds());
        long remainingMinutes = Math.max(1L, (remainingSeconds + 59) / 60);
        double progress = intervalSeconds <= 0 ? 1.0 : Math.min(1.0, pd.playtimeSeconds() / (double) intervalSeconds);

        inventory.setItem(4, ItemBuilder.of(Material.BOOK)
                .name(Text.line("<gold>Claim-Übersicht"))
                .lore(
                        Text.line("<gray>Belegte Slots: <white>" + used + " <gray>/ <white>" + (max == Integer.MAX_VALUE ? "∞" : String.valueOf(max))),
                        Component.empty(),
                        Text.line("<gray>Nächster Claim in <white>" + remainingMinutes
                                + " <gray>Minute" + (remainingMinutes == 1 ? "" : "n")),
                        progressBar(progress),
                        Component.empty(),
                        Text.line("<gray>Du erhältst neue Slots durch:"),
                        Text.line("<gray>- Spielzeit (regelmäßig automatisch)"),
                        Text.line("<gray>- Berechtigungen (claims.start.<zahl>)"),
                        Text.line("<gray>- Team/Admins (/staffclaim addclaims)")
                )
                .model(ctx.config().itemModel("main-menu-info"))
                .build());

        inventory.setItem(9, ItemBuilder.of(Material.MAP)
                .name(Text.line("<aqua>Meine Claims"))
                .lore(Text.line("<gray>Hier siehst du alle deine Claims."), Text.line("<gray>Linksklick, öffnen."))
                .model(ctx.config().itemModel("main-menu-claims"))
                .build());

        inventory.setItem(13, ItemBuilder.of(Material.GOLDEN_SHOVEL)
                .name(Text.line("<yellow>Claimen"))
                .lore(Text.line("<gray>Hier erstellst du deine Claims."), Text.line("<gray>Linksklick, öffnen."))
                .model(ctx.config().itemModel("main-menu-create"))
                .build());

        inventory.setItem(16, ItemBuilder.of(Material.COMPARATOR)
                .name(Text.line("<gray>Einstellungen"))
                .lore(Text.line("<gray>Hier stellst du deine Plugin-Einstellungen ein."), Text.line("<gray>Linksklick, öffnen."))
                .model(ctx.config().itemModel("main-menu-settings"))
                .build());
    }

    private Component progressBar(double progress) {
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        int filled = (int) Math.round(clamped * PROGRESS_BAR_SEGMENTS);
        int empty = PROGRESS_BAR_SEGMENTS - filled;

        int r = (int) Math.round(255 + (85 - 255) * clamped);
        int g = (int) Math.round(85 + (255 - 85) * clamped);
        int b = 85;
        String color = String.format("#%02X%02X%02X", r, g, b);

        StringBuilder sb = new StringBuilder();
        if (filled > 0) {
            sb.append('<').append(color).append('>').append("|".repeat(filled));
        }
        if (empty > 0) {
            sb.append("<dark_gray>").append("|".repeat(empty));
        }
        return Text.line(sb.toString());
    }

    @Override
    public void onClick(Player player, int slot, ClickType clickType) {
        switch (slot) {
            case 9 -> new ClaimsListGui(ctx, player).open(player);
            case 13 -> {
                ctx.selection().start(player.getUniqueId());
                if (ctx.claimMap() != null) {
                    ctx.claimMap().giveMap(player);
                }
                player.closeInventory();
                player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("claim-mode-started")));
            }
            case 16 -> new SettingsGui(ctx, player).open(player);
            default -> {
            }
        }
    }
}
