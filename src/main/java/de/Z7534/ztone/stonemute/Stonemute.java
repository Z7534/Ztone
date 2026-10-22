package de.Z7534.ztone.stonemute;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.stonemute.config.StonemuteConfig;
import de.Z7534.ztone.stonemute.hook.LiteBansHook;
import de.Z7534.ztone.stonemute.hook.LuckPermsHook;
import de.Z7534.ztone.stonemute.hook.VoiceChatHook;
import de.Z7534.ztone.stonemute.listener.PlayerConnectionListener;
import de.Z7534.ztone.stonemute.mute.MuteService;
import de.Z7534.ztone.util.ConfigUtil;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

public final class Stonemute {

    private final Ztone plugin;
    private MuteService muteService;
    private LiteBansHook liteBansHook;
    private FileConfiguration config;

    public Stonemute(Ztone plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("voicechat") == null) {
            getLogger().severe("SimpleVoiceChat (voicechat) nicht gefunden - Stonemute-Modul wird uebersprungen.");
            return;
        }
        if (getServer().getPluginManager().getPlugin("LiteBans") == null) {
            getLogger().severe("LiteBans nicht gefunden - Stonemute-Modul wird uebersprungen.");
            return;
        }

        saveDefaultConfig();
        StonemuteConfig settings = new StonemuteConfig(this);

        BukkitVoicechatService voicechatService =
                getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (voicechatService == null) {
            getLogger().severe("SimpleVoiceChat-Service nicht gefunden - Stonemute-Modul wird uebersprungen.");
            return;
        }

        LuckPermsHook luckPermsHook;
        try {
            luckPermsHook = new LuckPermsHook(settings);
        } catch (IllegalStateException e) {
            getLogger().severe("LuckPerms-API nicht verfuegbar - Stonemute-Modul wird uebersprungen.");
            return;
        }

        muteService = new MuteService(this, settings);

        liteBansHook = new LiteBansHook(this, muteService);
        liteBansHook.register();

        voicechatService.registerPlugin(new VoiceChatHook(this, settings, muteService, luckPermsHook));

        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(muteService), plugin);

        muteService.start();
        getLogger().info("Stonemute aktiv: LiteBans-Mutes werden auf SimpleVoiceChat uebertragen.");
    }

    public void onDisable() {
        if (liteBansHook != null) {
            liteBansHook.unregister();
        }
        if (muteService != null) {
            muteService.shutdown();
        }
    }

    public void reload() {
        reloadConfig();
        getLogger().info("Stonemute-Konfiguration neu geladen.");
    }

    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    public void reloadConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        config = ConfigUtil.loadAndSync(plugin, configFile, "stonemute/config.yml");
    }

    public void saveDefaultConfig() {
        reloadConfig();
    }

    public File getDataFolder() {
        return new File(plugin.getDataFolder(), "stonemute");
    }

    public InputStream getResource(String filename) {
        return plugin.getResource("stonemute/" + filename);
    }

    public void saveResource(String resourcePath, boolean replace) {
        File out = new File(getDataFolder(), resourcePath);
        if (out.exists() && !replace) {
            return;
        }
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (InputStream in = getResource(resourcePath)) {
            if (in == null) {
                getLogger().warning("Ressource 'stonemute/" + resourcePath + "' nicht im Jar gefunden!");
                return;
            }
            Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            getLogger().severe("Konnte Ressource 'stonemute/" + resourcePath + "' nicht speichern: " + e.getMessage());
        }
    }

    public Server getServer() {
        return plugin.getServer();
    }

    public Logger getLogger() {
        return plugin.getLogger();
    }

    public Ztone getPlugin() {
        return plugin;
    }
}
