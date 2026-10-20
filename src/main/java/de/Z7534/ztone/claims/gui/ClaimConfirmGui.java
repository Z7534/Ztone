package de.Z7534.ztone.claims.gui;

import de.Z7534.ztone.claims.ClaimsContext;
import de.Z7534.ztone.claims.manager.ClaimManager;
import de.Z7534.ztone.claims.model.SelectionSession;
import de.Z7534.ztone.claims.util.ItemBuilder;
import de.Z7534.ztone.claims.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.Map;

public final class ClaimConfirmGui extends Gui {

    private static final int SLOT_SAVE = 2;
    private static final int SLOT_CANCEL = 6;

    private final ClaimsContext ctx;
    private final SelectionSession session;

    public ClaimConfirmGui(ClaimsContext ctx, SelectionSession session) {
        super(9, Text.line("<dark_green>Änderungen bestätigen"));
        this.ctx = ctx;
        this.session = session;
        build();
    }

    private void build() {
        inventory.setItem(SLOT_SAVE, ItemBuilder.of(Material.EMERALD)
                .name(Text.line("<green>Speichern"))
                .lore(Text.line("<gray>Markiert: <white>" + session.totalMarked() + " <gray>Chunk(s)."),
                        Text.line("<gray>Linksklick, bestätigen."))
                .model(ctx.config().itemModel("claimmode-save"))
                .build());

        inventory.setItem(SLOT_CANCEL, ItemBuilder.of(Material.BARRIER)
                .name(Text.line("<red>Abbrechen"))
                .lore(Text.line("<gray>Verwirft alle Markierungen."), Text.line("<gray>Linksklick, abbrechen."))
                .model(ctx.config().itemModel("claimmode-cancel"))
                .build());
    }

    @Override
    public void onClick(Player player, int slot, ClickType clickType) {
        if (slot == SLOT_SAVE) {
            save(player);
        } else if (slot == SLOT_CANCEL) {
            if (ctx.claimMap() != null) {
                ctx.claimMap().restoreOffHand(player);
            }
            ctx.selection().end(player.getUniqueId());
            player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("claims-cancelled")));
            player.closeInventory();
        }
    }

    private void save(Player player) {
        if (session.totalMarked() == 0) {
            player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("no-claims-selected")));
            player.closeInventory();
            return;
        }
        ClaimManager.Result result = ctx.claims().validate(player, session.toClaim(), session.toUnclaim());
        switch (result) {
            case OK -> {
                int count = session.totalMarked();
                ctx.claims().apply(player, session.toClaim(), session.toUnclaim());
                if (ctx.claimMap() != null) {
                    ctx.claimMap().restoreOffHand(player);
                }
                ctx.selection().end(player.getUniqueId());
                player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("claims-saved"),
                        Map.of("anzahl", String.valueOf(count))));
                player.closeInventory();
            }
            case NOT_ENOUGH_SLOTS -> {
                int used = ctx.claims().getUsedClaimSlots(player.getUniqueId());
                int max = ctx.claims().getMaxClaimSlots(player);
                player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("not-enough-slots"),
                        Map.of("belegt", used + "", "max", max + "")));
                player.closeInventory();
            }
            case SPAWN_PROTECTED -> {
                player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("spawn-protected")));
                player.closeInventory();
            }
            case END_PROTECTED -> {
                player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("end-protected")));
                player.closeInventory();
            }
            case DIMENSION_DISABLED -> {
                player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("dimension-disabled")));
                player.closeInventory();
            }
            case FOREIGN_CHUNK -> {
                player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("cannot-select-foreign")));
                player.closeInventory();
            }
            case NOTHING_SELECTED -> {
                player.sendMessage(Text.line(ctx.config().prefix() + ctx.config().message("no-claims-selected")));
                player.closeInventory();
            }
        }
    }
}
