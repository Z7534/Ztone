package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.utils.ColorUtils;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ClanColorGUI extends AbstractGUI {

    private final Clan clan;
    private final boolean adminView;

    public ClanColorGUI(Ztone plugin, Player player, Clan clan) {
        this(plugin, player, clan, false);
    }

    public ClanColorGUI(Ztone plugin, Player player, Clan clan, boolean adminView) {
        super(plugin, player, "&8&l✦ &6Clan-Farbe", 45);
        this.clan = clan;
        this.adminView = adminView;
    }

    @Override
    public void setup() {
        fillFancyBorder();

        boolean gradientEnabled = clan.isGradientEnabled();

        String preview = clan.getColoredName();
        setItem(4, new ItemBuilder(Material.NAME_TAG)
                .name("&6&l✦ Vorschau")
                .lore(
                        "",
                        preview,
                        "",
                        gradientEnabled ? "&7Dieser Farbverlauf gilt für deinen" : "&7Diese Farbe gilt für deinen",
                        "&7Clan-Namen in Nachrichten",
                        "&7UND für deinen Clan-Suffix."
                )
                .glow()
                .build());

        int slot = 10;
        for (Map.Entry<String, String> entry : ColorUtils.NAMED_COLORS.entrySet()) {
            String code = entry.getKey();
            String colorName = entry.getValue();

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
                boolean selected = !gradientEnabled && code.equalsIgnoreCase(clan.getNameColor());

                List<String> lore = new ArrayList<>();
                lore.add(selected ? "&7Aktuell ausgewählt" : "&a▶ &7Klicke zum Auswählen");
                if (gradientEnabled) {
                    lore.add("&8(Ohne Wirkung, solange der");
                    lore.add("&8Gradient aktiviert ist)");
                }

                setItem(slot, new ItemBuilder(materialFor(code))
                        .name((selected ? "&a&l✔ " : "&f") + colorName)
                        .lore(lore)
                        .glow(selected)
                        .build(), () -> {
                    clan.setNameColor(code);
                    plugin.getClanManager().updateClanInDatabase(clan);
                    refresh();
                    player.updateInventory();
                });
            }

            slot++;
        }

        boolean strikethrough = clan.isNameStrikethrough();
        int strikethroughRequiredLevel = plugin.getLevelManager().getStrikethroughUnlockLevel();
        boolean strikethroughUnlocked = plugin.getLevelManager().isStrikethroughUnlocked(clan.getLevel());

        if (!strikethroughUnlocked) {
            setItem(39, new ItemBuilder(Material.BARRIER)
                    .name("&7&l✘ Durchgestrichen")
                    .lore(
                            "&cNoch nicht freigeschaltet!",
                            "&7Erfordert Clan-Level &e" + strikethroughRequiredLevel,
                            "&7(aktuell: Level " + clan.getLevel() + ")"
                    )
                    .build());
        } else {
            setItem(39, new ItemBuilder(Material.TRIPWIRE_HOOK)
                    .name((strikethrough ? "&a&l✔ " : "&7") + "Durchgestrichen")
                    .lore(
                            "&7Status: " + (strikethrough ? "&aAktiviert" : "&cDeaktiviert"),
                            "&7Gilt für Clan-Name und Suffix.",
                            "",
                            "&a▶ &7Klicke zum Umschalten"
                    )
                    .glow(strikethrough)
                    .build(), () -> {
                clan.setNameStrikethrough(!clan.isNameStrikethrough());
                plugin.getClanManager().updateClanInDatabase(clan);
                refresh();
                player.updateInventory();
            });
        }

        int gradientRequiredLevel = plugin.getLevelManager().getGradientUnlockLevel();
        boolean gradientUnlocked = plugin.getLevelManager().isGradientUnlocked(clan.getLevel());

        if (!gradientUnlocked) {
            setItem(41, new ItemBuilder(Material.BARRIER)
                    .name("&7&l✘ Farbverlauf (Gradient)")
                    .lore(
                            "&cNoch nicht freigeschaltet!",
                            "&7Erfordert Clan-Level &e" + gradientRequiredLevel,
                            "&7(aktuell: Level " + clan.getLevel() + ")"
                    )
                    .build());
        } else {
            setItem(41, new ItemBuilder(Material.FIREWORK_STAR)
                    .name((gradientEnabled ? "&a&l✔ " : "&7") + "Farbverlauf (Gradient)")
                    .lore(
                            "&7Status: " + (gradientEnabled ? "&aAktiviert" : "&cDeaktiviert"),
                            "&7Färbt Clan-Name und Suffix in",
                            "&7einem Verlauf von einer Start-",
                            "&7zu einer Endfarbe ein.",
                            "",
                            "&a▶ &7Klicke, um die Gradient-",
                            "&a  Einstellungen zu öffnen"
                    )
                    .glow(gradientEnabled)
                    .build(), () -> new ClanGradientGUI(plugin, player, clan, adminView).open());
        }

        addBackButton(36, () -> new ClanSettingsGUI(plugin, player, clan, adminView).open());
        addCloseButton(44);

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }

    public static Material materialFor(String code) {
        return switch (code.toLowerCase()) {
            case "&0" -> Material.BLACK_DYE;
            case "&1" -> Material.LAPIS_LAZULI;
            case "&2" -> Material.GREEN_DYE;
            case "&3" -> Material.CYAN_DYE;
            case "&4" -> Material.BRICK;
            case "&5" -> Material.PURPLE_DYE;
            case "&6" -> Material.GOLD_INGOT;
            case "&7" -> Material.LIGHT_GRAY_DYE;
            case "&8" -> Material.GRAY_DYE;
            case "&9" -> Material.BLUE_DYE;
            case "&a" -> Material.LIME_DYE;
            case "&b" -> Material.LIGHT_BLUE_DYE;
            case "&c" -> Material.RED_DYE;
            case "&e" -> Material.YELLOW_DYE;
            case "&f" -> Material.WHITE_DYE;
            default -> Material.GRAY_DYE;
        };
    }
}
