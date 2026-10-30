package de.Z7534.ztone.claims.manager;

import de.Z7534.ztone.claims.model.ChunkPos;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.model.SelectionSession;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClaimMapRenderer extends MapRenderer {

    private static final int CHUNKS_RADIUS = 7;
    private static final int CHUNKS_COUNT = 15;
    private static final int CHUNK_PIXELS = 8;
    private static final int OFFSET_X = 4;
    private static final int OFFSET_Y = 4;

    // Palette Colors (Minecraft Map Color Bytes)
    // Blau: eigene Chunks (RGB 30, 144, 255)
    private static final byte COLOR_OWN = (byte) -126;
    // Rot: fremde Chunks (RGB 220, 20, 60)
    private static final byte COLOR_OTHER = (byte) -46;
    // Grün: freie Chunks (RGB 50, 180, 50)
    private static final byte COLOR_FREE = (byte) -123;
    // Gelb: ausgewählte Chunks (RGB 255, 215, 0)
    private static final byte COLOR_SELECTED = (byte) 74;

    // Rahmen & Hilfslinien
    private static final byte COLOR_GRID = (byte) 87;      // RGB 35, 35, 35
    private static final byte COLOR_BORDER = (byte) 116;   // RGB 15, 15, 15
    private static final byte COLOR_WHITE = (byte) 34;     // RGB 255, 255, 255
    private static final byte COLOR_COMPASS = (byte) 14;   // RGB 200, 200, 200

    private final ClaimManager claimManager;
    private final SelectionManager selectionManager;

    private static final class RenderCache {
        int chunkX;
        int chunkZ;
        String world;
        int toClaimSize;
        int toUnclaimSize;
        byte direction;
    }

    private final Map<UUID, RenderCache> cache = new HashMap<>();

    public ClaimMapRenderer(ClaimManager claimManager, SelectionManager selectionManager) {
        super(false);
        this.claimManager = claimManager;
        this.selectionManager = selectionManager;
    }

    @Override
    public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {
        if (!player.isOnline() || player.getWorld() == null) {
            return;
        }

        int centerChunkX = player.getLocation().getBlockX() >> 4;
        int centerChunkZ = player.getLocation().getBlockZ() >> 4;
        String worldName = player.getWorld().getName();
        byte dir = yawToCursorDirection(player.getLocation().getYaw());

        SelectionSession session = selectionManager.get(player.getUniqueId());
        int toClaimSize = session != null ? session.toClaim().size() : 0;
        int toUnclaimSize = session != null ? session.toUnclaim().size() : 0;

        RenderCache state = cache.get(player.getUniqueId());
        boolean needsFullRedraw = state == null
                || state.chunkX != centerChunkX
                || state.chunkZ != centerChunkZ
                || !worldName.equals(state.world)
                || state.toClaimSize != toClaimSize
                || state.toUnclaimSize != toUnclaimSize;

        if (needsFullRedraw) {
            drawMap(canvas, player, centerChunkX, centerChunkZ, worldName, session);
            if (state == null) {
                state = new RenderCache();
                cache.put(player.getUniqueId(), state);
            }
            state.chunkX = centerChunkX;
            state.chunkZ = centerChunkZ;
            state.world = worldName;
            state.toClaimSize = toClaimSize;
            state.toUnclaimSize = toUnclaimSize;
            state.direction = dir;
        }

        if (needsFullRedraw || state.direction != dir) {
            state.direction = dir;
            updateCursor(canvas, dir);
        }
    }

    private void drawMap(MapCanvas canvas, Player player, int centerChunkX, int centerChunkZ, String worldName, SelectionSession session) {
        UUID playerUuid = player.getUniqueId();

        // 1. Äußeren Rand zeichnen
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                if (x < OFFSET_X || x >= OFFSET_X + CHUNKS_COUNT * CHUNK_PIXELS
                        || y < OFFSET_Y || y >= OFFSET_Y + CHUNKS_COUNT * CHUNK_PIXELS) {
                    canvas.setPixel(x, y, COLOR_BORDER);
                }
            }
        }

        // Trennlinie Rahmen/Grid
        for (int i = OFFSET_X - 1; i <= OFFSET_X + CHUNKS_COUNT * CHUNK_PIXELS; i++) {
            canvas.setPixel(i, OFFSET_Y - 1, COLOR_GRID);
            canvas.setPixel(i, OFFSET_Y + CHUNKS_COUNT * CHUNK_PIXELS, COLOR_GRID);
        }
        for (int i = OFFSET_Y - 1; i <= OFFSET_Y + CHUNKS_COUNT * CHUNK_PIXELS; i++) {
            canvas.setPixel(OFFSET_X - 1, i, COLOR_GRID);
            canvas.setPixel(OFFSET_X + CHUNKS_COUNT * CHUNK_PIXELS, i, COLOR_GRID);
        }

        // Himmelsrichtungs-Punkte (N oben, S unten, W links, O rechts)
        canvas.setPixel(63, 1, COLOR_WHITE);
        canvas.setPixel(64, 1, COLOR_WHITE);
        canvas.setPixel(63, 2, COLOR_COMPASS);
        canvas.setPixel(64, 2, COLOR_COMPASS);

        canvas.setPixel(63, 126, COLOR_COMPASS);
        canvas.setPixel(64, 126, COLOR_COMPASS);

        canvas.setPixel(1, 63, COLOR_COMPASS);
        canvas.setPixel(1, 64, COLOR_COMPASS);

        canvas.setPixel(126, 63, COLOR_COMPASS);
        canvas.setPixel(126, 64, COLOR_COMPASS);

        // 2. Chunks 15x15 zeichnen
        for (int dz = -CHUNKS_RADIUS; dz <= CHUNKS_RADIUS; dz++) {
            for (int dx = -CHUNKS_RADIUS; dx <= CHUNKS_RADIUS; dx++) {
                int cx = centerChunkX + dx;
                int cz = centerChunkZ + dz;
                ChunkPos pos = new ChunkPos(worldName, cx, cz);

                byte color = COLOR_FREE;

                if (session != null && (session.toClaim().contains(pos) || session.toUnclaim().contains(pos))) {
                    color = COLOR_SELECTED;
                } else {
                    Claim claim = claimManager.getClaimAt(pos);
                    if (claim != null) {
                        if (claim.isOwner(playerUuid)) {
                            color = COLOR_OWN;
                        } else {
                            color = COLOR_OTHER;
                        }
                    }
                }

                int px = OFFSET_X + (dx + CHUNKS_RADIUS) * CHUNK_PIXELS;
                int py = OFFSET_Y + (dz + CHUNKS_RADIUS) * CHUNK_PIXELS;

                // 8x8 Pixel für diesen Chunk zeichnen
                // Zeile 0 und Spalte 0 sind Gitternetz (dunkelgrau)
                for (int y = 0; y < CHUNK_PIXELS; y++) {
                    for (int x = 0; x < CHUNK_PIXELS; x++) {
                        if (x == 0 || y == 0) {
                            canvas.setPixel(px + x, py + y, COLOR_GRID);
                        } else {
                            canvas.setPixel(px + x, py + y, color);
                        }
                    }
                }
            }
        }
    }

    private void updateCursor(MapCanvas canvas, byte dir) {
        MapCursorCollection cursors = canvas.getCursors();
        while (cursors.size() > 0) {
            cursors.removeCursor(cursors.getCursor(0));
        }

        // Spieler-Cursor exakt in der Mitte der Karte (64, 64)
        cursors.addCursor(new MapCursor(
                (byte) 0,
                (byte) 0,
                dir,
                MapCursor.Type.PLAYER,
                true
        ));
    }

    private byte yawToCursorDirection(float yaw) {
        // Minecraft Yaw: 0=S (+Z), 90=W (-X), 180=N (-Z), 270=O (+X)
        // MapCursor Direction: 0..15 (0=N, 4=O, 8=S, 12=W)
        float normalized = (yaw % 360 + 360) % 360;
        int dir = Math.round((normalized + 180f) / 22.5f) % 16;
        return (byte) dir;
    }

    public void invalidate(UUID uuid) {
        if (uuid != null) {
            cache.remove(uuid);
        }
    }

    public void invalidate(Player player) {
        if (player != null) {
            cache.remove(player.getUniqueId());
        }
    }

    public void clearCache() {
        cache.clear();
    }

    public void invalidateAll() {
        cache.clear();
    }
}
