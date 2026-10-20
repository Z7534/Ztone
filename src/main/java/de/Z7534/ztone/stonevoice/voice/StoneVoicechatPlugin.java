package de.Z7534.ztone.stonevoice.voice;

import de.Z7534.ztone.stonevoice.StoneVoice;
import de.Z7534.ztone.stonevoice.session.AdminVoiceManager;
import de.Z7534.ztone.stonevoice.session.AdminVoiceSession;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LeaveGroupEvent;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.RemoveGroupEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import de.maxhenkel.voicechat.api.packets.StaticSoundPacket;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

/**
 * SimpleVoiceChat plugin integration for StoneVoice.
 */
public class StoneVoicechatPlugin implements VoicechatPlugin {

    public static final String PLUGIN_ID = "stonevoice";

    private final StoneVoice plugin;
    private VoicechatServerApi serverApi;

    public StoneVoicechatPlugin(StoneVoice plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
        registration.registerEvent(VoicechatServerStoppedEvent.class, this::onServerStopped);
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
        registration.registerEvent(StaticSoundPacketEvent.class, this::onStaticSoundPacket);
        registration.registerEvent(EntitySoundPacketEvent.class, this::onEntitySoundPacket);
        registration.registerEvent(LocationalSoundPacketEvent.class, this::onLocationalSoundPacket);
        registration.registerEvent(LeaveGroupEvent.class, this::onLeaveGroup);
        registration.registerEvent(PlayerDisconnectedEvent.class, this::onPlayerDisconnected);
        registration.registerEvent(RemoveGroupEvent.class, this::onRemoveGroup);
    }

    private void onServerStarted(VoicechatServerStartedEvent event) {
        this.serverApi = event.getVoicechat();
        plugin.getLogger().info("SimpleVoiceChat server API initialized for StoneVoice.");
    }

    private void onServerStopped(VoicechatServerStoppedEvent event) {
        this.serverApi = null;
        plugin.getAdminVoiceManager().clearAll();
    }

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        VoicechatConnection senderConnection = event.getSenderConnection();
        if (senderConnection == null || serverApi == null) {
            return;
        }

        UUID senderUuid = senderConnection.getPlayer().getUuid();
        AdminVoiceManager manager = plugin.getAdminVoiceManager();
        AdminVoiceSession session = manager.getSession(senderUuid);

        if (session != null) {
            if (session.isHidden()) {
                // Cancel proximity/regular group broadcast from hidden admin
                event.cancel();

                // If hidden admin is NOT muted, broadcast to the target group members
                if (!session.isMuted() && !session.isFullMuted()) {
                    UUID targetGroupId = session.getTargetGroupId();
                    StaticSoundPacket packet = event.getPacket().staticSoundPacketBuilder().build();

                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        if (onlinePlayer.getUniqueId().equals(senderUuid)) {
                            continue;
                        }
                        VoicechatConnection conn = serverApi.getConnectionOf(onlinePlayer.getUniqueId());
                        if (conn != null && conn.isConnected() && conn.isInGroup()) {
                            Group grp = conn.getGroup();
                            if (grp != null && grp.getId().equals(targetGroupId)) {
                                serverApi.sendStaticSoundPacketTo(conn, packet);
                            }
                        }
                    }
                }
                return;
            } else {
                // Normal joined admin: check if muted or fullMuted
                if (session.isMuted() || session.isFullMuted()) {
                    event.cancel();
                    return;
                }
            }
        }

        // Check if the sender is speaking inside a group that has hidden listeners
        if (senderConnection.isInGroup()) {
            Group senderGroup = senderConnection.getGroup();
            if (senderGroup != null) {
                Collection<AdminVoiceSession> hiddenListeners = manager.getHiddenListenersForGroup(senderGroup.getId());
                if (!hiddenListeners.isEmpty()) {
                    StaticSoundPacket soundPacket = event.getPacket().staticSoundPacketBuilder().build();
                    for (AdminVoiceSession listenerSession : hiddenListeners) {
                        if (listenerSession.isFullMuted()) {
                            continue; // FullMuted admins should not hear anything
                        }
                        VoicechatConnection listenerConn = serverApi.getConnectionOf(listenerSession.getPlayerUuid());
                        if (listenerConn != null && listenerConn.isConnected()) {
                            serverApi.sendStaticSoundPacketTo(listenerConn, soundPacket);
                        }
                    }
                }
            }
        }
    }

    private void onStaticSoundPacket(StaticSoundPacketEvent event) {
        VoicechatConnection receiverConnection = event.getReceiverConnection();
        if (receiverConnection == null) {
            return;
        }

        AdminVoiceSession session = plugin.getAdminVoiceManager().getSession(receiverConnection.getPlayer().getUuid());
        if (session != null && session.isFullMuted()) {
            event.cancel();
        }
    }

    private void onEntitySoundPacket(EntitySoundPacketEvent event) {
        VoicechatConnection receiverConnection = event.getReceiverConnection();
        if (receiverConnection == null) {
            return;
        }

        AdminVoiceSession session = plugin.getAdminVoiceManager().getSession(receiverConnection.getPlayer().getUuid());
        if (session != null && session.isFullMuted()) {
            event.cancel();
        }
    }

    private void onLocationalSoundPacket(LocationalSoundPacketEvent event) {
        VoicechatConnection receiverConnection = event.getReceiverConnection();
        if (receiverConnection == null) {
            return;
        }

        AdminVoiceSession session = plugin.getAdminVoiceManager().getSession(receiverConnection.getPlayer().getUuid());
        if (session != null && session.isFullMuted()) {
            event.cancel();
        }
    }

    private void onLeaveGroup(LeaveGroupEvent event) {
        VoicechatConnection conn = event.getConnection();
        if (conn != null) {
            AdminVoiceSession session = plugin.getAdminVoiceManager().getSession(conn.getPlayer().getUuid());
            if (session != null && !session.isHidden()) {
                plugin.getAdminVoiceManager().stopSessionSilently(conn.getPlayer().getUuid(), conn);
            }
        }
    }

    private void onPlayerDisconnected(PlayerDisconnectedEvent event) {
        plugin.getAdminVoiceManager().onPlayerQuit(event.getPlayerUuid());
    }

    private void onRemoveGroup(RemoveGroupEvent event) {
        if (event.getGroup() != null) {
            plugin.getAdminVoiceManager().onGroupRemoved(event.getGroup().getId());
        }
    }

    public VoicechatServerApi getServerApi() {
        return serverApi;
    }
}
