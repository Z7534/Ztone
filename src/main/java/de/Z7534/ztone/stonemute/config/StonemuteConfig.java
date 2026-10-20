package de.Z7534.ztone.stonemute.config;

import de.Z7534.ztone.stonemute.Stonemute;
import org.bukkit.configuration.file.FileConfiguration;

public final class StonemuteConfig {

    private final int refreshIntervalSeconds;
    private final boolean checkAllServerScopes;
    private final String bypassPermission;
    private final String mutedMessage;
    private final int notifyCooldownSeconds;

    public StonemuteConfig(Stonemute plugin) {
        FileConfiguration config = plugin.getConfig();
        this.refreshIntervalSeconds = Math.max(1, config.getInt("settings.refresh-interval-seconds", 10));
        this.checkAllServerScopes = config.getBoolean("settings.check-all-server-scopes", true);
        this.bypassPermission = config.getString("settings.bypass-permission", "stonemute.bypass");
        this.mutedMessage = config.getString("messages.muted-actionbar",
                "<red>Du bist gemutet und kannst im Voicechat nicht sprechen.");
        this.notifyCooldownSeconds = Math.max(0, config.getInt("messages.notify-cooldown-seconds", 5));
    }

    public int refreshIntervalSeconds() {
        return refreshIntervalSeconds;
    }

    public boolean checkAllServerScopes() {
        return checkAllServerScopes;
    }

    public String bypassPermission() {
        return bypassPermission;
    }

    public String mutedMessage() {
        return mutedMessage;
    }

    public int notifyCooldownSeconds() {
        return notifyCooldownSeconds;
    }
}
