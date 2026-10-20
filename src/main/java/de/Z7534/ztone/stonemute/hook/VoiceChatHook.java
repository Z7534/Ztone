package de.Z7534.ztone.stonemute.hook;

import de.Z7534.ztone.stonemute.Stonemute;
import de.Z7534.ztone.stonemute.config.StonemuteConfig;
import de.Z7534.ztone.stonemute.mute.MuteService;
import de.Z7534.ztone.util.SmallCapsUtil;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class VoiceChatHook implements VoicechatPlugin {

    private final Stonemute plugin;
    private final StonemuteConfig settings;
    private final MuteService muteService;
    private final LuckPermsHook luckPermsHook;

    public VoiceChatHook(Stonemute plugin, StonemuteConfig settings,
                         MuteService muteService, LuckPermsHook luckPermsHook) {
        this.plugin = plugin;
        this.settings = settings;
        this.muteService = muteService;
        this.luckPermsHook = luckPermsHook;
    }

    @Override
    public String getPluginId() {
        return "stonemute";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
    }

    // Wird auf dem Voicechat-Thread aufgerufen: nur Cache-Zugriffe, keine Datenbank!
    private void onMicrophonePacket(MicrophonePacketEvent event) {
        VoicechatConnection connection = event.getSenderConnection();
        if (connection == null) {
            return;
        }

        UUID uuid = connection.getPlayer().getUuid();
        if (!muteService.isMuted(uuid)) {
            return;
        }
        if (luckPermsHook.hasBypass(uuid)) {
            return;
        }

        event.cancel();
        notifyMuted(uuid);
    }

    private void notifyMuted(UUID uuid) {
        if (!muteService.shouldNotify(uuid) || !plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin.getPlugin(), () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendActionBar(MiniMessage.miniMessage().deserialize(SmallCapsUtil.toSmallCaps(settings.mutedMessage())));
            }
        });
    }
}
