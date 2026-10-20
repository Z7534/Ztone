package de.Z7534.ztone.clansystem.managers;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.models.Clan;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class GlowPacketHook {

    private static final int FLAGS_METADATA_INDEX = 0;
    private static final byte GLOWING_BIT = 0x40;

    private final Ztone plugin;
    private final GlowManager glowManager;
    private PacketAdapter listener;

    public GlowPacketHook(Ztone plugin, GlowManager glowManager) {
        this.plugin = plugin;
        this.glowManager = glowManager;
        register();
    }

    private void register() {
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        this.listener = new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Server.ENTITY_METADATA) {
            @Override
            public void onPacketSending(PacketEvent event) {
                try {
                    handle(event);
                } catch (Throwable t) {
                    plugin.getLogger().fine("Fehler im Glow-Paket-Filter: " + t.getMessage());
                }
            }
        };
        manager.addPacketListener(listener);
    }

    public void unregister() {
        if (listener != null) {
            try {
                ProtocolLibrary.getProtocolManager().removePacketListener(listener);
            } catch (Throwable ignored) {}
            listener = null;
        }
    }

    private void handle(PacketEvent event) {
        PacketContainer packet = event.getPacket();
        if (packet == null) {
            return;
        }

        // Entity-ID direkt aus dem Paket lesen (thread-sicher, kein fehleranfaelliger World-Lookup auf Netty-Threads)
        List<Integer> integers = packet.getIntegers().getValues();
        if (integers == null || integers.isEmpty()) {
            return;
        }
        int entityId = integers.get(0);

        Player target = glowManager.getPlayerByEntityId(entityId);
        if (target == null) {
            // Kein bekannter Online-Spieler (z.B. Monster, Item, ArmorStand) -> ignorieren
            return;
        }

        Player viewer = event.getPlayer();
        if (viewer == null || target.getUniqueId().equals(viewer.getUniqueId())) {
            return;
        }

        // 1. Hat der Spieler einen externen Glow (Combat Tag im PvPManager oder Glowing-Potion-Effekt)?
        // Falls ja: Der Spieler SOLL fuer alle sichtbar leuchten -> Bit keinesfalls entfernen!
        if (glowManager.isExternalGlowActive(target)) {
            return;
        }

        // 2. Hat der Clan des Spielers Glow ueberhaupt aktiviert und freigeschaltet?
        if (!glowManager.isClanGlowActive(target)) {
            return;
        }

        // 3. Pruefen, ob der Betrachter im selben Clan ist
        Clan targetClan = plugin.getClanManager().getClanByPlayer(target);
        if (targetClan == null) {
            return;
        }

        Clan viewerClan = plugin.getClanManager().getClanByPlayer(viewer);
        boolean sameClan = viewerClan != null && viewerClan.getId() == targetClan.getId();
        if (sameClan) {
            // Betrachter ist im selben Clan -> darf den Clan-Glow sehen!
            return;
        }

        // Betrachter ist NICHT im selben Clan -> Glow-Bit (0x40) aus den Metadaten entfernen
        stripGlowBit(packet);
    }

    private void stripGlowBit(PacketContainer packet) {
        List<WrappedDataValue> values = packet.getDataValueCollectionModifier().read(0);
        if (values == null || values.isEmpty()) {
            return;
        }

        boolean changed = false;
        List<WrappedDataValue> newValues = new ArrayList<>(values.size());

        for (WrappedDataValue value : values) {
            if (value.getIndex() == FLAGS_METADATA_INDEX && value.getValue() instanceof Byte flagsValue) {
                byte flags = flagsValue;
                byte stripped = (byte) (flags & ~GLOWING_BIT);
                if (stripped != flags) {
                    newValues.add(new WrappedDataValue(value.getIndex(), value.getSerializer(), stripped));
                    changed = true;
                    continue;
                }
            }
            newValues.add(value);
        }

        if (changed) {
            packet.getDataValueCollectionModifier().write(0, newValues);
        }
    }
}
