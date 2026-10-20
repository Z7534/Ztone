package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.managers.MessageManager;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.utils.ColorUtils;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class ClanSuffixGUI extends AbstractGUI {

    private final Clan clan;
    private final boolean adminView;

    private String text;
    private boolean bold;

    public ClanSuffixGUI(Ztone plugin, Player player, Clan clan) {
        this(plugin, player, clan, false);
    }

    public ClanSuffixGUI(Ztone plugin, Player player, Clan clan, boolean adminView) {
        super(plugin, player, plugin.getMessageManager().getRaw("gui.suffix-edit"), 45);
        this.clan = clan;
        this.adminView = adminView;
        parseCurrentSuffix();
    }

    private void parseCurrentSuffix() {
        String suffix = clan.getSuffix() == null ? "" : clan.getSuffix();

        this.text = ColorUtils.stripColors(suffix);
        this.bold = ColorUtils.containsBold(suffix);
    }

    private String composeSuffix() {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (bold) sb.append("&l");
        sb.append(text);
        return sb.toString();
    }

    @Override
    public void setup() {
        fillFancyBorder();

        String preview = clan.getColoredSuffix();

        setItem(4, new ItemBuilder(Material.NAME_TAG)
                .name("&6&l✦ Vorschau")
                .lore(
                        "",
                        composeSuffix().isEmpty() ? "&8(kein Suffix)" : "&f" + player.getName() + " " + preview,
                        "",
                        "&7So wird dein Suffix im Chat",
                        "&7und in der Tab-Liste aussehen.",
                        clan.isGradientEnabled()
                                ? "&7(Farbverlauf aktiv, siehe Clan-Farbe)"
                                : "&7(Farbe = Clan-Farbe, siehe Clan-Farbe)",
                        "&7(Durchgestrichen wird ebenfalls",
                        "&7in der Clan-Farbe eingestellt)"
                )
                .glow()
                .build());

        setItem(21, new ItemBuilder(Material.WRITABLE_BOOK)
                .name("&e&l➤ Text ändern")
                .lore(
                        "&7Aktuell: &f" + (text.isEmpty() ? "&8(leer)" : text),
                        "&7Maximal " + plugin.getConfigManager().getSuffixMaxLength() + " Zeichen",
                        "",
                        "&a▶ &7Klicke zum Ändern"
                )
                .build(), () -> plugin.getChatInputManager().request(player,
                "Bitte gib den gewünschten Suffix-Text im Chat ein (ohne Farbcodes):",
                input -> {
                    String stripped = ColorUtils.stripColors(input);

                    if (stripped.length() > plugin.getConfigManager().getSuffixMaxLength()) {
                        plugin.getMessageManager().send(player, "suffix.too-long",
                                MessageManager.of("max", String.valueOf(plugin.getConfigManager().getSuffixMaxLength())));
                        new ClanSuffixGUI(plugin, player, clan, adminView).open();
                        return;
                    }

                    boolean blocked = false;
                    for (String word : plugin.getConfigManager().getSuffixBlacklist()) {
                        if (stripped.toLowerCase().contains(word.toLowerCase())) {
                            blocked = true;
                            break;
                        }
                    }

                    if (blocked) {
                        plugin.getMessageManager().send(player, "suffix.blacklisted");
                        new ClanSuffixGUI(plugin, player, clan, adminView).open();
                        return;
                    }

                    this.text = stripped;
                    clan.setSuffix(composeSuffix());
                    plugin.getClanManager().updateClanInDatabase(clan);
                    plugin.getMessageManager().send(player, "suffix.set", MessageManager.of("suffix", clan.getColoredSuffix()));
                    new ClanSuffixGUI(plugin, player, clan, adminView).open();
                }));

        setItem(23, new ItemBuilder(Material.BARRIER)
                .name("&c&l✘ Suffix entfernen")
                .lore("&a▶ &7Klicke zum Entfernen")
                .build(), () -> {
            text = "";
            clan.setSuffix("");
            plugin.getClanManager().updateClanInDatabase(clan);
            plugin.getMessageManager().send(player, "suffix.removed");
            refresh();
            player.updateInventory();
        });

        addBackButton(36, () -> new ClanSettingsGUI(plugin, player, clan, adminView).open());
        addCloseButton(44);

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }
}
