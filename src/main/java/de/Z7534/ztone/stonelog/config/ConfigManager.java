package de.Z7534.ztone.stonelog.config;

import de.Z7534.ztone.stonelog.Stonelog;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConfigManager {

    private final Stonelog plugin;

    private WebhookEntry commands;
    private WebhookEntry chat;
    private WebhookEntry deaths;
    private WebhookEntry joinLeave;
    private String avatarBaseUrl;

    public ConfigManager(Stonelog plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        var cfg = plugin.getConfig();

        this.commands = readEntry(cfg.getConfigurationSection("webhooks.commands"));
        this.chat = readEntry(cfg.getConfigurationSection("webhooks.chat"));
        this.deaths = readEntry(cfg.getConfigurationSection("webhooks.deaths"));
        this.joinLeave = readEntry(cfg.getConfigurationSection("webhooks.join-leave"));
        this.avatarBaseUrl = cfg.getString("avatar.base-url", "https://mc-heads.net/avatar/{uuid}/64");
    }

    private WebhookEntry readEntry(ConfigurationSection section) {
        if (section == null) {
            return new WebhookEntry(false, Collections.emptyList(), null, null);
        }
        boolean enabled = section.getBoolean("enabled", false);

        List<String> urls = new ArrayList<>();
        if (section.isList("urls")) {
            for (String u : section.getStringList("urls")) {
                if (u != null && !u.isBlank() && !u.contains("XXXXXXXX")) {
                    String trimmed = u.trim();
                    if (!urls.contains(trimmed)) urls.add(trimmed);
                }
            }
        }
        String singleUrl = section.getString("url", "");
        if (singleUrl != null && !singleUrl.isBlank() && !singleUrl.contains("XXXXXXXX")) {
            String trimmed = singleUrl.trim();
            if (!urls.contains(trimmed)) {
                urls.add(trimmed);
            }
        }

        String username = section.getString("username", null);
        return new WebhookEntry(enabled, urls, username, section.getConfigurationSection("messages"));
    }

    public WebhookEntry getCommands() { return commands; }
    public WebhookEntry getChat() { return chat; }
    public WebhookEntry getDeaths() { return deaths; }
    public WebhookEntry getJoinLeave() { return joinLeave; }
    public String getAvatarBaseUrl() { return avatarBaseUrl; }

    public static class WebhookEntry {
        private final boolean enabled;
        private final List<String> urls;
        private final String username;
        private final ConfigurationSection messages;

        public WebhookEntry(boolean enabled, List<String> urls, String username, ConfigurationSection messages) {
            this.enabled = enabled;
            this.urls = urls != null ? List.copyOf(urls) : Collections.emptyList();
            this.username = username;
            this.messages = messages;
        }

        /**
         * true nur wenn: in der Config aktiviert UND mindestens eine gueltige URL hinterlegt ist.
         */
        public boolean isEnabled() {
            return enabled && !urls.isEmpty();
        }

        public List<String> getUrls() {
            return urls;
        }

        public String getUrl() {
            return urls.isEmpty() ? "" : urls.get(0);
        }

        public String getUsername() {
            return username;
        }

        public String msg(String key, String def) {
            if (messages == null) return def;
            return messages.getString(key, def);
        }
    }
}
