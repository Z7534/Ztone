package de.Z7534.ztone.stonevoice;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.stonevoice.command.AdminJoinVoiceCommand;
import de.Z7534.ztone.stonevoice.listener.BukkitEventListener;
import de.Z7534.ztone.stonevoice.session.AdminVoiceManager;
import de.Z7534.ztone.stonevoice.voice.StoneVoicechatPlugin;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;

import java.util.logging.Logger;

public final class StoneVoice {

    private final Ztone plugin;
    private AdminVoiceManager adminVoiceManager;
    private StoneVoicechatPlugin voicechatPlugin;

    public StoneVoice(Ztone plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        this.adminVoiceManager = new AdminVoiceManager(this);

        if (plugin.getServer().getPluginManager().getPlugin("voicechat") == null) {
            plugin.getLogger().severe("SimpleVoiceChat (voicechat) nicht gefunden! StoneVoice-Modul wird uebersprungen.");
            return;
        }

        // Register Voicechat Plugin
        BukkitVoicechatService service = plugin.getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service != null) {
            this.voicechatPlugin = new StoneVoicechatPlugin(this);
            service.registerPlugin(this.voicechatPlugin);
            plugin.getLogger().info("StoneVoice successfully registered with SimpleVoiceChat!");
        } else {
            plugin.getLogger().severe("BukkitVoicechatService not found! SimpleVoiceChat plugin is required for StoneVoice.");
        }

        // Register Command
        PluginCommand joinCmd = plugin.getCommand("adminjoinvoice");
        if (joinCmd != null) {
            AdminJoinVoiceCommand joinExecutor = new AdminJoinVoiceCommand(this);
            joinCmd.setExecutor(joinExecutor);
            joinCmd.setTabCompleter(joinExecutor);
        }

        // Register Bukkit Listeners
        plugin.getServer().getPluginManager().registerEvents(new BukkitEventListener(this), plugin);

        getLogger().info("StoneVoice-Modul erfolgreich aktiviert.");
    }

    public void onDisable() {
        if (adminVoiceManager != null) {
            adminVoiceManager.clearAll();
        }
        getLogger().info("StoneVoice-Modul deaktiviert.");
    }

    public Ztone getPlugin() {
        return plugin;
    }

    public Server getServer() {
        return plugin.getServer();
    }

    public Logger getLogger() {
        return plugin.getLogger();
    }

    public AdminVoiceManager getAdminVoiceManager() {
        return adminVoiceManager;
    }

    public StoneVoicechatPlugin getVoicechatPlugin() {
        return voicechatPlugin;
    }
}
