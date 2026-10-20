package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.managers.MessageManager;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.utils.ColorUtils;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Farbauswahl für die Gradient-Start- bzw. Endfarbe. Anders als früher gibt es hier
 * KEINE freie Hex-Eingabe mehr per Chat, sondern nur eine Auswahl aus genau den 15
 * Farben, die auch im normalen Farb-Setting-GUI (ClanColorGUI) zur Verfügung stehen.
 */
public class ClanGradientColorGUI extends AbstractGUI {

    private final Clan clan;
    private final boolean isStart;
    private final boolean adminView;

    public ClanGradientColorGUI(Ztone plugin, Player player, Clan clan, boolean isStart) {
        this(plugin, player, clan, isStart, false);
    }

    public ClanGradientColorGUI(Ztone plugin, Player player, Clan clan, boolean isStart, boolean adminView) {
        super(plugin, player, "&8&l✦ &6" + (isStart ? "Startfarbe" : "Endfarbe"), 45);
        this.clan = clan;
        this.isStart = isStart;
        this.adminView = adminView;
    }

    @Override
    public void setup() {
        fillFancyBorder();

        String currentHex = isStart ? clan.getGradientColorStart() : clan.getGradientColorEnd();

        setItem(4, new ItemBuilder(Material.NAME_TAG)
                .name("&6&l✦ Vorschau")
                .lore(
                        "",
                        clan.getColoredName(),
                        "",
                        "&7Wähle unten die " + (isStart ? "Startfarbe" : "Endfarbe") + " für",
                        "&7deinen Farbverlauf aus."
                )
                .glow()
                .build());

        int slot = 10;
        for (Map.Entry<String, String> entry : ColorUtils.NAMED_COLORS.entrySet()) {
            String code = entry.getKey();
            String colorName = entry.getValue();
            String hex = ColorUtils.hexForNamedColor(code);

            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 35) break;

            int requiredLevel = plugin.getLevelManager().getRequiredLevelForColor(code);
            boolean unlocked = clan.getLevel() >= requiredLevel;

            if (!unlocked) {
                setItem(slot, new ItemBuilder(Material.BARRIER)
                        .name("&7&l✘ " + colorName)
                        .lore(
                                "&cNoch nicht freigeschaltet!",
                                "&7Erfordert Clan-Level &e" + requiredLevel,
                                "&7(aktuell: Level " + clan.getLevel() + ")"
                        )
                        .build());
            } else {
                boolean selected = hex.equalsIgnoreCase(currentHex);

                List<String> lore = new ArrayList<>();
                lore.add(selected ? "&7Aktuell ausgewählt" : "&a▶ &7Klicke zum Auswählen");

                setItem(slot, new ItemBuilder(ClanColorGUI.materialFor(code))
                        .name((selected ? "&a&l✔ " : "&f") + colorName)
                        .lore(lore)
                        .glow(selected)
                        .build(), () -> {
                    if (isStart) {
                        clan.setGradientColorStart(hex);
                    } else {
                        clan.setGradientColorEnd(hex);
                    }

                    plugin.getClanManager().updateClanInDatabase(clan);
                    plugin.getMessageManager().send(player, "clan-color.gradient-set",
                            MessageManager.of("preview", clan.getColoredName()));
                    new ClanGradientGUI(plugin, player, clan, adminView).open();
                });
            }

            slot++;
        }

        addBackButton(36, () -> new ClanGradientGUI(plugin, player, clan, adminView).open());
        addCloseButton(44);

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }
}
