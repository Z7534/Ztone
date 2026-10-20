package de.Z7534.ztone.claims.gui;

import de.Z7534.ztone.claims.ClaimsContext;
import de.Z7534.ztone.claims.util.ItemBuilder;
import de.Z7534.ztone.claims.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

public final class ConfirmGui extends Gui {

    private final Runnable onConfirm;
    private final Runnable onCancel;

    public ConfirmGui(ClaimsContext ctx, Component title, List<Component> description,
                       Runnable onConfirm, Runnable onCancel) {
        super(9, title);
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;

        inventory.setItem(2, ItemBuilder.of(Material.LIME_DYE)
                .name(Text.line("<green>Bestätigen"))
                .lore(description.isEmpty() ? List.of(Text.line("<gray>Linksklick, bestätigen."))
                        : concat(description, Text.line("<gray>Linksklick, bestätigen.")))
                .model(ctx.config().itemModel("confirm-yes"))
                .build());

        inventory.setItem(6, ItemBuilder.of(Material.RED_DYE)
                .name(Text.line("<red>Abbrechen"))
                .lore(Text.line("<gray>Linksklick, abbrechen."))
                .model(ctx.config().itemModel("confirm-no"))
                .build());
    }

    private static List<Component> concat(List<Component> base, Component extra) {
        java.util.List<Component> result = new java.util.ArrayList<>(base);
        result.add(net.kyori.adventure.text.Component.empty());
        result.add(extra);
        return result;
    }

    @Override
    public void onClick(Player player, int slot, ClickType clickType) {
        if (slot == 2) {
            onConfirm.run();
        } else if (slot == 6) {
            onCancel.run();
        }
    }
}
