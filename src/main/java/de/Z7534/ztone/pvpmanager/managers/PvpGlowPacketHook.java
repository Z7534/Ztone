package de.Z7534.ztone.pvpmanager.managers;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import de.Z7534.ztone.pvpmanager.Pvpmanager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PvpGlowPacketHook {

    private static final int SHARED_FLAGS_INDEX = 0;
    private static final byte GLOWING_BIT = 0x40;

    private final Pvpmanager plugin;
    private final GlowManager glowManager;
    private final ProtocolManager protocolManager;

    public PvpGlowPacketHook(Pvpmanager plugin, GlowManager glowManager) {
        this.plugin = plugin;
        this.glowManager = glowManager;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.protocolManager.addPacketListener(new PacketAdapter(plugin.getPlugin(), ListenerPriority.HIGHEST,
                PacketType.Play.Server.ENTITY_METADATA) {
            @Override
            public void onPacketSending(PacketEvent event) {
                forceGlowBit(event);
            }
        });
    }

    public void unregister() {
        if (protocolManager != null) {
            protocolManager.removePacketListeners(plugin.getPlugin());
        }
    }

    private void forceGlowBit(PacketEvent event) {
        if (glowManager.getGlowing().isEmpty()) {
            return;
        }
        try {
            PacketContainer packet = event.getPacket();
            if (packet == null) {
                return;
            }

            List<Integer> integers = packet.getIntegers().getValues();
            if (integers == null || integers.isEmpty()) {
                return;
            }
            int entityId = integers.get(0);

            Player targetPlayer = null;
            if (plugin.getPlugin().getGlowManager() != null) {
                targetPlayer = plugin.getPlugin().getGlowManager().getPlayerByEntityId(entityId);
            }
            if (targetPlayer == null) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getEntityId() == entityId) {
                        targetPlayer = p;
                        break;
                    }
                }
            }

            if (targetPlayer == null || !glowManager.isGlowing(targetPlayer.getUniqueId())) {
                return;
            }

            List<WrappedDataValue> values = packet.getDataValueCollectionModifier().read(0);
            if (values == null || values.isEmpty()) {
                return;
            }

            boolean changed = false;
            List<WrappedDataValue> updated = new ArrayList<>(values);
            for (int i = 0; i < updated.size(); i++) {
                WrappedDataValue value = updated.get(i);
                if (value.getIndex() != SHARED_FLAGS_INDEX || !(value.getValue() instanceof Byte flags)) {
                    continue;
                }
                byte forced = (byte) (flags | GLOWING_BIT);
                if (forced != flags) {
                    updated.set(i, new WrappedDataValue(value.getIndex(), value.getSerializer(), forced));
                    changed = true;
                }
            }

            if (changed) {
                packet.getDataValueCollectionModifier().write(0, updated);
            }
        } catch (Throwable throwable) {
            plugin.getLogger().fine("Glow-Paket konnte nicht angepasst werden: " + throwable.getMessage());
        }
    }
}
