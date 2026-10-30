package de.Z7534.ztone.dragonegg.config;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.util.ConfigUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class DragonEggConfig {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Ztone plugin;
    private File configFile;
    private FileConfiguration config;

    private boolean matchAnyEgg;
    private boolean permanentPvp;
    private boolean allowClaimEntry;

    private String securedName;
    private List<String> securedLore;
    private String securedDateFormat;
    private DateTimeFormatter dateFormatter;

    private String prefix;
    private Pattern nameMatcherPattern;
    private List<Pattern> loreMatcherPatterns;

    public DragonEggConfig(Ztone plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "dragonegg/config.yml");
    }

    public void load() {
        this.config = ConfigUtil.loadAndSync(plugin, configFile, "dragonegg/config.yml");

        this.matchAnyEgg = config.getBoolean("match-any-egg", false);
        this.permanentPvp = config.getBoolean("permanent-pvp", true);
        this.allowClaimEntry = config.getBoolean("allow-claim-entry", false);

        this.securedName = config.getString("egg.secured.name", "<light_purple><bold>Drachenei</bold></light_purple>");
        this.securedLore = config.getStringList("egg.secured.lore");
        if (securedLore == null || securedLore.isEmpty()) {
            securedLore = List.of(
                    "<gray>Gesichert von <yellow><player></yellow></gray>",
                    "<gray>Datum: <white><date></white></gray>"
            );
        }
        this.securedDateFormat = config.getString("egg.secured.date-format", "dd.MM.yyyy");
        try {
            this.dateFormatter = DateTimeFormatter.ofPattern(securedDateFormat);
        } catch (Exception e) {
            this.dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        }

        this.prefix = config.getString("messages.prefix",
                "<gradient:#DDA0DD:#BA55D3><bold>DRAGONEGG</bold></gradient> <dark_gray>»</dark_gray>");

        buildMatcherPatterns();
    }

    private void buildMatcherPatterns() {
        // Name-Pattern
        if (securedName != null && !securedName.isBlank() && !"*".equals(securedName.trim())) {
            String plainName = MM.stripTags(securedName);
            String regex = replacePlaceholdersRegex(plainName);
            this.nameMatcherPattern = Pattern.compile("^\\s*" + regex + "\\s*$", Pattern.CASE_INSENSITIVE);
        } else {
            this.nameMatcherPattern = null;
        }

        this.loreMatcherPatterns = new ArrayList<>();
        if (securedLore != null) {
            for (String rawLine : securedLore) {
                if (rawLine == null || rawLine.isBlank()) continue;
                String plainLine = MM.stripTags(rawLine);
                String regex = replacePlaceholdersRegex(plainLine);
                loreMatcherPatterns.add(Pattern.compile(".*" + regex + ".*", Pattern.CASE_INSENSITIVE));
            }
        }
    }

    private String replacePlaceholdersRegex(String text) {
        String quoted = Pattern.quote(text);
        // Platzhalter innerhalb des Pattern.quote-Blocks für Regex freigeben
        quoted = quoted.replace("<player>", "\\E.+?\\Q")
                .replace("{player}", "\\E.+?\\Q")
                .replace("%player%", "\\E.+?\\Q")
                .replace("<date>", "\\E.+?\\Q")
                .replace("{date}", "\\E.+?\\Q")
                .replace("%date%", "\\E.+?\\Q");
        return quoted;
    }

    private Component itemText(String miniMessageTemplate, TagResolver placeholders) {
        if (miniMessageTemplate == null || miniMessageTemplate.isBlank()) {
            return Component.empty();
        }
        return MM.deserialize(miniMessageTemplate, placeholders).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Erstellt das gesicherte Drachenei gemäß der Vorgabe des Benutzers.
     */
    public ItemStack createSecuredEgg(Player winner) {
        String playerName = winner != null ? winner.getName() : "Unbekannt";
        String dateStr = LocalDate.now().format(dateFormatter);

        TagResolver placeholders = TagResolver.resolver(
                Placeholder.unparsed("player", playerName),
                Placeholder.unparsed("date", dateStr)
        );

        ItemStack item = ItemStack.of(Material.DRAGON_EGG);
        item.editMeta(meta -> {
            if (securedName != null && !securedName.isBlank()) {
                Component nameComp = itemText(securedName, placeholders);
                meta.customName(nameComp);
                meta.displayName(nameComp);
            }
            if (!securedLore.isEmpty()) {
                meta.lore(securedLore.stream().map(line -> itemText(line, placeholders)).toList());
            }
        });
        return item;
    }

    /**
     * Prüft, ob ein gegebener ItemStack das gesicherte Drachenei ist.
     */
    public boolean isSecuredEgg(ItemStack item) {
        if (item == null || item.getType() != Material.DRAGON_EGG) {
            return false;
        }

        // Falls matchAnyEgg aktiv ist, zählt jedes Drachenei
        if (matchAnyEgg) {
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return nameMatcherPattern == null && loreMatcherPatterns.isEmpty();
        }

        // 1. Name prüfen
        if (nameMatcherPattern != null) {
            String plainName = extractPlainName(meta);
            if (plainName == null || !nameMatcherPattern.matcher(plainName.trim()).matches()) {
                return false;
            }
        }

        // 2. Lore prüfen
        if (loreMatcherPatterns != null && !loreMatcherPatterns.isEmpty()) {
            List<String> plainLore = extractPlainLore(meta);
            if (plainLore.isEmpty()) {
                return false;
            }
            for (Pattern pattern : loreMatcherPatterns) {
                boolean matched = false;
                for (String line : plainLore) {
                    if (pattern.matcher(line).matches()) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    return false;
                }
            }
        }

        return true;
    }

    private String extractPlainName(ItemMeta meta) {
        if (meta == null) return null;
        try {
            if (meta.hasCustomName() && meta.customName() != null) {
                return PLAIN.serialize(meta.customName());
            }
        } catch (Throwable ignored) {}
        try {
            if (meta.hasDisplayName() && meta.displayName() != null) {
                return PLAIN.serialize(meta.displayName());
            }
        } catch (Throwable ignored) {}
        try {
            if (meta.hasItemName() && meta.itemName() != null) {
                return PLAIN.serialize(meta.itemName());
            }
        } catch (Throwable ignored) {}
        try {
            if (meta.getDisplayName() != null && !meta.getDisplayName().isEmpty()) {
                return ChatColor.stripColor(meta.getDisplayName());
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private List<String> extractPlainLore(ItemMeta meta) {
        if (meta == null) return List.of();
        List<String> result = new ArrayList<>();
        try {
            if (meta.hasLore()) {
                List<Component> compLore = meta.lore();
                if (compLore != null && !compLore.isEmpty()) {
                    for (Component comp : compLore) {
                        result.add(PLAIN.serialize(comp));
                    }
                    return result;
                }
                List<String> strLore = meta.getLore();
                if (strLore != null && !strLore.isEmpty()) {
                    for (String line : strLore) {
                        result.add(ChatColor.stripColor(line));
                    }
                    return result;
                }
            }
        } catch (Throwable ignored) {}
        return result;
    }

    public Component getMessage(String key, Map<String, String> placeholders) {
        String template = config.getString("messages." + key, "");
        template = template.replace("<prefix>", prefix);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            template = template.replace("{" + e.getKey() + "}", MM.escapeTags(e.getValue()));
        }
        return MM.deserialize(template).decoration(TextDecoration.ITALIC, false);
    }

    public Component getMessage(String key) {
        return getMessage(key, Map.of());
    }

    public boolean isMatchAnyEgg() { return matchAnyEgg; }
    public boolean isPermanentPvp() { return permanentPvp; }
    public boolean isAllowClaimEntry() { return allowClaimEntry; }
    public String getSecuredName() { return securedName; }
    public List<String> getSecuredLore() { return securedLore; }
    public String getSecuredDateFormat() { return securedDateFormat; }
    public DateTimeFormatter getDateFormatter() { return dateFormatter; }
    public String getPrefix() { return prefix; }
}
