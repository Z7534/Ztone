package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class ConfirmGUI extends AbstractGUI {

    private final String question;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    public ConfirmGUI(Ztone plugin, Player player, String question, Runnable onConfirm, Runnable onCancel) {
        super(plugin, player, plugin.getMessageManager().getRaw("gui.confirm"), 27);
        this.question = question;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    public void setup() {

        for (int i = 0; i < 27; i++) {
            setItem(i, new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build());
        }
        for (int i = 9; i < 18; i++) {
            setItem(i, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        }

        setItem(4, new ItemBuilder(Material.PAPER)
                .name("&e&l⚠ " + question)
                .lore(
                        "",
                        "&7Bitte bestätige deine Auswahl."
                )
                .glow()
                .build());

        setItem(11, new ItemBuilder(Material.LIME_DYE)
                .name("&a&l✔ Bestätigen")
                .lore(
                        "&7Klicke hier, um",
                        "&adie Aktion zu bestätigen."
                )
                .glow()
                .build(), () -> {
            if (onConfirm != null) {
                onConfirm.run();
            }
        });

        setItem(15, new ItemBuilder(Material.RED_DYE)
                .name("&c&l✘ Abbrechen")
                .lore(
                        "&7Klicke hier, um",
                        "&cabzubrechen."
                )
                .build(), () -> {
            if (onCancel != null) {
                onCancel.run();
            }
        });
    }
}
