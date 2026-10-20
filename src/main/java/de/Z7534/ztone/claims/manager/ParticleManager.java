package de.Z7534.ztone.claims.manager;

import de.Z7534.ztone.claims.config.ConfigManager;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.model.ChunkPos;
import de.Z7534.ztone.claims.model.PlayerData;
import de.Z7534.ztone.claims.model.SelectionSession;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class ParticleManager {

    private final ClaimManager claimManager;
    private final SelectionManager selectionManager;
    private final ConfigManager config;

    private static final String PENDING_CLAIM_COLOR = "#FFA500";
    private static final String PENDING_UNCLAIM_COLOR = "#FF3333";

    public ParticleManager(ClaimManager claimManager, SelectionManager selectionManager, ConfigManager config) {
        this.claimManager = claimManager;
        this.selectionManager = selectionManager;
        this.config = config;
    }

    public void tick(Player player) {
        PlayerData pd = claimManager.getOrCreatePlayerData(player.getUniqueId(), player.getName());
        SelectionSession session = selectionManager.get(player.getUniqueId());
        boolean selecting = session != null;
        boolean boardLine = pd.boardLineEnabled();
        boolean fenceLine = pd.fenceLineEnabled();
        if (!boardLine && !fenceLine && !selecting) return;

        World world = player.getWorld();
        int radius = Math.max(1, Math.min(4, config.particleRenderRadiusChunks()));
        int pcx = player.getLocation().getBlockX() >> 4;
        int pcz = player.getLocation().getBlockZ() >> 4;

        boolean drawFloor = boardLine || selecting;
        boolean drawFence = fenceLine || selecting;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos pos = new ChunkPos(world.getName(), pcx + dx, pcz + dz);
                String key = groupKey(pos, session);

                for (ChunkPos neighbour : pos.neighbours()) {
                    // Verhindere doppeltes Zeichnen derselben Kante: Nur zeichnen, wenn pos < neighbour
                    if (pos.x() > neighbour.x() || (pos.x() == neighbour.x() && pos.z() > neighbour.z())) {
                        continue;
                    }

                    String neighbourKey = groupKey(neighbour, session);
                    if (key == null && neighbourKey == null) continue;
                    if (key != null && key.equals(neighbourKey)) continue;

                    // Kantenfarbe bestimmen: Entweder von pos oder von neighbour
                    String activeKey = key != null ? key : neighbourKey;
                    drawEdge(player, world, pos, neighbour, activeKey, drawFloor, drawFence);
                }
            }
        }
    }

    private String groupKey(ChunkPos pos, SelectionSession session) {
        if (session != null) {
            if (session.toClaim().contains(pos)) return "PENDING_CLAIM";
            if (session.toUnclaim().contains(pos)) return "PENDING_UNCLAIM";
        }
        Claim claim = claimManager.getClaimAt(pos);
        return claim != null ? ("CLAIM_" + claim.id()) : null;
    }

    private void drawEdge(Player viewer, World world, ChunkPos pos, ChunkPos neighbour, String key,
                           boolean drawFloor, boolean drawFence) {
        String colorHex = colorFor(key, viewer);
        if (colorHex == null) return;

        int x0 = pos.x() << 4, x1 = x0 + 16;
        int z0 = pos.z() << 4, z1 = z0 + 16;
        int xa, za, xb, zb;
        if (neighbour.x() < pos.x()) {
            xa = x0; za = z0; xb = x0; zb = z1;
        } else if (neighbour.x() > pos.x()) {
            xa = x1; za = z0; xb = x1; zb = z1;
        } else if (neighbour.z() < pos.z()) {
            xa = x0; za = z0; xb = x1; zb = z0;
        } else {
            xa = x0; za = z1; xb = x1; zb = z1;
        }

        // Nicht geladene Chunks überspringen: Verhindert asynchrone Chunk-Lags
        int chunkX1 = xa >> 4;
        int chunkZ1 = za >> 4;
        if (!world.isChunkLoaded(chunkX1, chunkZ1)) {
            return;
        }

        int points = Math.max(2, Math.min(16, config.particlePointsPerChunkEdge()));
        Color color = parseColor(colorHex);
        boolean nether = world.getEnvironment() == World.Environment.NETHER;

        Particle.DustOptions dust = new Particle.DustOptions(color, 1.8f);

        int lastGroundY = nether ? viewer.getLocation().getBlockY() - 1 : world.getHighestBlockYAt(xa, za);

        for (int i = 0; i <= points; i++) {
            double t = (double) i / points;
            double x = xa + (xb - xa) * t;
            double z = za + (zb - za) * t;
            int blockX = (int) Math.floor(x);
            int blockZ = (int) Math.floor(z);

            // Nur alle 2-3 Punkte Höhe nachschlagen, sonst interpolieren (spart ~70% getHighestBlockYAt Aufrufe)
            int groundY;
            if (nether) {
                groundY = viewer.getLocation().getBlockY() - 1;
            } else if (i % 2 == 0) {
                groundY = world.getHighestBlockYAt(blockX, blockZ);
                lastGroundY = groundY;
            } else {
                groundY = lastGroundY;
            }

            if (drawFloor) {
                spawn(viewer, x, groundY + 0.15, z, dust);
            }
            if (drawFence) {
                spawn(viewer, x, groundY + 1.25, z, dust);
            }
        }
    }

    private String colorFor(String key, Player viewer) {
        if (key.equals("PENDING_CLAIM")) return PENDING_CLAIM_COLOR;
        if (key.equals("PENDING_UNCLAIM")) return PENDING_UNCLAIM_COLOR;
        if (key.startsWith("CLAIM_")) {
            long id = Long.parseLong(key.substring("CLAIM_".length()));
            Claim claim = claimManager.getById(id);
            if (claim == null) return null;
            if (claim.owner().equals(viewer.getUniqueId())) return config.ownClaimColor();
            if (claim.isTrusted(viewer.getUniqueId())) return config.trustedClaimColor();
            return config.foreignClaimColor();
        }
        return null;
    }

    private Color parseColor(String hex) {
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            int r = Integer.parseInt(h.substring(0, 2), 16);
            int g = Integer.parseInt(h.substring(2, 4), 16);
            int b = Integer.parseInt(h.substring(4, 6), 16);
            return Color.fromRGB(r, g, b);
        } catch (RuntimeException ex) {
            return Color.WHITE;
        }
    }

    private void spawn(Player viewer, double x, double y, double z, Particle.DustOptions dust) {
        // Direkte Übergabe von Koordinaten vermeidet teure Location-Objekt-Allokationen pro Partikel
        viewer.spawnParticle(Particle.DUST, x, y, z, 1, 0.02, 0.02, 0.02, 0.0, dust);
    }
}
