package de.Z7534.ztone.stoneattack.modules.leaderboard.hologram;

import de.Z7534.ztone.stoneattack.modules.leaderboard.LeaderboardType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

/**
 * Platzierung eines Leaderboards: der Wandblock (Welt + Blockkoordinaten)
 * und die Seite dieses Blocks (NORTH/SOUTH/EAST/WEST), an der das Board
 * haengt. Die Ausrichtung ist damit immer fest an eine Himmelsrichtung
 * gebunden und richtet sich nie nach dem Blickwinkel eines Spielers.
 */
public class HologramDefinition {

    private final LeaderboardType type;
    private String world;
    private int x;
    private int y;
    private int z;
    private BlockFace facing;
    /**
     * Gesamthoehe des Boards in GANZEN Bloecken (bestimmt, wie viele Eintraege
     * angezeigt werden). Der Block aus x/y/z ist dabei der unterste Block des
     * Boards - erhoeht man die Hoehe, waechst das Board nach oben.
     */
    private double heightBlocks;

    public HologramDefinition(LeaderboardType type, String world, int x, int y, int z, BlockFace facing) {
        this(type, world, x, y, z, facing, HologramLayout.DEFAULT_HEIGHT);
    }

    public HologramDefinition(LeaderboardType type, String world, int x, int y, int z, BlockFace facing,
                              double heightBlocks) {
        this.type = type;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing;
        this.heightBlocks = clampHeight(heightBlocks);
    }

    /**
     * Rundet die Hoehe auf VOLLE BLOECKE und haelt sie im erlaubten Bereich
     * ({@link HologramLayout#MIN_HEIGHT} .. {@link HologramLayout#MAX_HEIGHT}).
     * Ganze Bloecke sind noetig, damit das Board buendig im Blockraster sitzt
     * und nicht auf halber Blockhoehe endet.
     */
    public static double clampHeight(double height) {
        double whole = Math.rint(height);
        return Math.max(HologramLayout.MIN_HEIGHT, Math.min(HologramLayout.MAX_HEIGHT, whole));
    }

    /** Nur senkrechte Waende (Himmelsrichtungen) sind erlaubt - keine Decke, kein Boden. */
    public static boolean isWallFace(BlockFace face) {
        return face == BlockFace.NORTH || face == BlockFace.SOUTH
                || face == BlockFace.EAST || face == BlockFace.WEST;
    }

    public LeaderboardType getType() {
        return type;
    }

    public String getWorldName() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public BlockFace getFacing() {
        return facing;
    }

    public double getHeightBlocks() {
        return heightBlocks;
    }

    public void setHeightBlocks(double heightBlocks) {
        this.heightBlocks = clampHeight(heightBlocks);
    }

    public void update(String world, int x, int y, int z, BlockFace facing) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing;
    }

    /** null, solange die Welt nicht geladen ist. */
    public BoardFrame frame() {
        World w = Bukkit.getWorld(world);
        if (w == null) {
            return null;
        }
        return new BoardFrame(w, x, y, z, facing);
    }

    /** Mitte des Boards (auf der Wandflaeche) oder null, falls die Welt fehlt. */
    public Location center() {
        BoardFrame frame = frame();
        return frame == null ? null : frame.at(0, 0, 0);
    }

    /** Punkt vor dem Board, an den sich Spieler per /leaderboard tp teleportieren. */
    public Location teleportLocation() {
        BoardFrame frame = frame();
        return frame == null ? null : frame.standingSpot();
    }
}
