package de.Z7534.ztone.claims.manager;

import de.Z7534.ztone.claims.model.ChunkPos;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.model.SelectionSession;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ClaimMapRenderer extends MapRenderer {

    private static final int CHUNKS_RADIUS = 7;
    private static final int CHUNKS_COUNT = 15;
    private static final int CHUNK_PIXELS = 8;
    private static final int OFFSET_X = 4;
    private static final int OFFSET_Y = 4;

    // Palette Colors (nach Benutzervorgabe)
    // Blau: eigene Chunks
    private static final byte COLOR_OWN = MapPalette.matchColor(new Color(30, 144, 255));
    // Rot: fremde Chunks
    private static final byte COLOR_OTHER = MapPalette.matchColor(new Color(220, 20, 60));
    // Grün: freie Chunks
    private static final byte COLOR_FREE = MapPalette.matchColor(new Color(50, 180, 50));
    // Gelb: ausgewählte Chunks
    private static final byte COLOR_SELECTED = MapPalette.matchColor(new Color(255, 215, 0));

    // Rahmen & Hilfslinien
    private static final byte COLOR_GRID = MapPalette.matchColor(new Color(35, 35, 35));
    private static final byte COLOR_BORDER = MapPalette.matchColor(new Color(15, 15, 15));
    private static final byte COLOR_WHITE = MapPalette.matchColor(new Color(255, 255, 255));
    private static final byte COLOR_COMPASS = MapPalette.matchColor(new Color(200, 200, 200));

    private final ClaimManager claimManager;
    private final SelectionManager selectionManager;

    private static final class RenderCache {
        int chunkX;
        int chunkZ;
        String world;
        int sessionRevision;
        byte direction;
    }

    private final Map<UUID, RenderCache> cache = new HashMap<>();

    public ClaimMapRenderer(ClaimManager claimManager, SelectionManager selectionManager) {
        super(true); // Contextual: rendert individuell pro Spieler
        this.claimManager = claimManager;
        this.selectionManager = selectionManager;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (player == null || !player.isOnline()) return;

        var loc = player.getLocation();
        int centerChunkX = loc.getBlockX() >> 4;
        int centerChunkZ = loc.getBlockZ() >> 4;
        String worldName = loc.getWorld().getName();

        SelectionSession session = selectionManager.get(player.getUniqueId());
        int sessionRev = session != null ? Objects.hash(session.toClaim(), session.toUnclaim()) : 0;

        float rot = (loc.getYaw() + 180.0f) % 360.0f;
        if (rot < 0) rot += 360.0f;
        byte dir = (byte) ((Math.round((rot / 360.0f) * 16.0f)) % 16);

        RenderCache state = cache.get(player.getUniqueId());
        boolean needsFullRedraw = state == null
                || state.chunkX != centerChunkX
                || state.chunkZ != centerChunkZ
                || !worldName.equals(state.world)
                || state.sessionRevision != sessionRev;

        if (needsFullRedraw) {
            drawMap(canvas, player, centerChunkX, centerChunkZ, worldName, session);
            if (state == null) {
                state = new RenderCache();
                cache.put(player.getUniqueId(), state);
            }
            state.chunkX = centerChunkX;
            state.chunkZ = centerChunkZ;
            state.world = worldName;
            state.sessionRevision = sessionRev;
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

        // 2. Chunks zeichnen
        for (int dz = -CHUNKS_RADIUS; dz <= CHUNKS_RADIUS; dz++) {
            int row = dz + CHUNKS_RADIUS;
            int startY = OFFSET_Y + row * CHUNK_PIXELS;
            int chunkZ = centerChunkZ + dz;

            for (int dx = -CHUNKS_RADIUS; dx <= CHUNKS_RADIUS; dx++) {
                int col = dx + CHUNKS_RADIUS;
                int startX = OFFSET_X + col * CHUNK_PIXELS;
                int chunkX = centerChunkX + dx;

                ChunkPos pos = new ChunkPos(worldName, chunkX, chunkZ);

                // Farbzuweisung:
                // Gelb: aktuell in Auswahl vorgemerkt
                // Blau: gehört dem Spieler
                // Rot: gehört jemand anderem
                // Grün: noch frei
                byte chunkColor;
                if (session != null && (session.toClaim().contains(pos) || session.toUnclaim().contains(pos))) {
                    chunkColor = COLOR_SELECTED;
                } else {
                    Claim claim = claimManager.getClaimAt(pos);
                    if (claim != null) {
                        if (claim.owner().equals(playerUuid)) {
                            chunkColor = COLOR_OWN;
                        } else {
                            chunkColor = COLOR_OTHER;
                        }
                    } else {
                        chunkColor = COLOR_FREE;
                    }
                }

                boolean isCurrentChunk = (dx == 0 && dz == 0);

                for (int py = 0; py < CHUNK_PIXELS; py++) {
                    for (int px = 0; px < CHUNK_PIXELS; px++) {
                        int pxX = startX + px;
                        int pxY = startY + py;

                        if (px == CHUNK_PIXELS - 1 || py == CHUNK_PIXELS - 1) {
                            canvas.setPixel(pxX, pxY, COLOR_GRID);
                        } else if (isCurrentChunk && (px == 0 && py == 0 || px == 6 && py == 0 || px == 0 && py == 6 || px == 6 && py == 6 || px == 3 && py == 3)) {
                            canvas.setPixel(pxX, pxY, COLOR_WHITE);
                        } else {
                            canvas.setPixel(pxX, pxY, chunkColor);
                        }
                    }
                }
            }
        }
    }

    private void updateCursor(MapCanvas canvas, byte dir) {
        MapCursorCollection cursors = new MapCursorCollection();
        cursors.addCursor(new MapCursor((byte) 0, (byte) 0, dir, MapCursor.Type.PLAYER, true));
        canvas.setCursors(cursors);
    }

    public void invalidate(UUID playerUuid) {
        cache.remove(playerUuid);
    }

    public void clearCache() {
        cache.clear();
    }
}
