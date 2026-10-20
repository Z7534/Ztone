package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.utils.ColorUtils;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class ClanGradientGUI extends AbstractGUI {

    private final Clan clan;
    private final boolean adminView;

    public ClanGradientGUI(Ztone plugin, Player player, Clan clan) {
        this(plugin, player, clan, false);
    }

    public ClanGradientGUI(Ztone plugin, Player player, Clan clan, boolean adminView) {
        super(plugin, player, "&8&l✦ &6Farbverlauf", 27);
        this.clan = clan;
        this.adminView = adminView;
    }

    @Override
    public void setup() {
        fillFancyBorder();

        boolean gradientEnabled = clan.isGradientEnabled();
        boolean gradientUnlocked = plugin.getLevelManager().isGradientUnlocked(clan.getLevel());
        int gradientRequiredLevel = plugin.getLevelManager().getGradientUnlockLevel();

        setItem(4, new ItemBuilder(Material.NAME_TAG)
                .name("&6&l✦ Vorschau")
                .lore(
                        "",
                        clan.getColoredName(),
                        "",
                        gradientEnabled
                                ? "&7Der Farbverlauf ist aktuell aktiv."
                                : "&7Aktiviere den Farbverlauf, damit",
                        gradientEnabled ? "" : "&7er auf Namen & Suffix wirkt."
                )
                .glow()
                .build());

        if (!gradientUnlocked) {
            setItem(13, new ItemBuilder(Material.BARRIER)
                    .name("&7&l✘ Farbverlauf (Gradient)")
                    .lore(
                            "&cNoch nicht freigeschaltet!",
                            "&7Erfordert Clan-Level &e" + gradientRequiredLevel,
                            "&7(aktuell: Level " + clan.getLevel() + ")"
                    )
                    .build());
        } else {
            setItem(13, new ItemBuilder(Material.FIREWORK_STAR)
                    .name((gradientEnabled ? "&a&l✔ " : "&7") + "Farbverlauf (Gradient)")
                    .lore(
                            "&7Status: " + (gradientEnabled ? "&aAktiviert" : "&cDeaktiviert"),
                            "&7Färbt Clan-Name und Suffix in",
                            "&7einem Verlauf von einer Start-",
                            "&7zu einer Endfarbe ein.",
                            "",
                            "&a▶ &7Klicke zum Umschalten"
                    )
                    .glow(gradientEnabled)
                    .build(), () -> {
                clan.setGradientEnabled(!clan.isGradientEnabled());
                plugin.getClanManager().updateClanInDatabase(clan);
                refresh();
                player.updateInventory();
            });
        }

        Material startMaterial = ClanColorGUI.materialFor(ColorUtils.nearestNamedColorCode(clan.getGradientColorStart()));
        Material endMaterial = ClanColorGUI.materialFor(ColorUtils.nearestNamedColorCode(clan.getGradientColorEnd()));

        setItem(11, new ItemBuilder(startMaterial)
                .name("&6&l➤ Startfarbe")
                .lore(
                        "&7Aktuell: &f" + clan.getGradientColorStart(),
                        "",
                        gradientEnabled ? "&a▶ &7Klicke zum Auswählen" : "&8Aktiviere zuerst den Gradient"
                )
                .glow(gradientEnabled)
                .build(), () -> {
            if (!gradientEnabled) {
                plugin.getMessageManager().send(player, "clan-color.gradient-disabled");
                return;
            }
            new ClanGradientColorGUI(plugin, player, clan, true, adminView).open();
        });

        setItem(15, new ItemBuilder(endMaterial)
                .name("&6&l➤ Endfarbe")
                .lore(
                        "&7Aktuell: &f" + clan.getGradientColorEnd(),
                        "",
                        gradientEnabled ? "&a▶ &7Klicke zum Auswählen" : "&8Aktiviere zuerst den Gradient"
                )
                .glow(gradientEnabled)
                .build(), () -> {
            if (!gradientEnabled) {
                plugin.getMessageManager().send(player, "clan-color.gradient-disabled");
                return;
            }
            new ClanGradientColorGUI(plugin, player, clan, false, adminView).open();
        });

        addBackButton(18, () -> new ClanColorGUI(plugin, player, clan, adminView).open());
        addCloseButton(26);

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }
}
