package de.Z7534.ztone.stoneattack.modules.leaderboard.hologram;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/**
 * Koordinatensystem eines an einer Blockwand haengenden Leaderboards.
 *
 * <pre>
 *   u = nach rechts (aus Sicht eines Spielers, der auf das Board schaut)
 *   v = nach oben
 *   w = von der Wand weg zum Spieler hin
 * </pre>
 *
 * Der Ursprung (0/0/0) ist die Mitte der angeklickten Wandflaeche. Das
 * Board ist immer exakt in eine der vier Himmelsrichtungen ausgerichtet
 * (NORTH/SOUTH/EAST/WEST) - "facing" ist die Richtung, in die der Text
 * zeigt, also die Blockseite, auf der das Board haengt.
 *
 * Senkrecht gilt: v = -0,5 ist die UNTERE Kante des angeklickten Blocks.
 * Genau dort sitzt die Unterkante des Boards (siehe {@link HologramLayout}),
 * es waechst also ausschliesslich nach oben und bleibt dadurch im Blockraster.
 */
public final class BoardFrame {

    private final World world;
    private final BlockFace facing;
    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private final double normalX;
    private final double normalZ;
    private final double rightX;
    private final double rightZ;
    private final float yaw;

    /**
     * @param blockX/Y/Z der Wandblock, an dessen Seite das Board haengt
     * @param facing     Seite des Blocks (NORTH/SOUTH/EAST/WEST), auf der das Board haengt
     */
    public BoardFrame(World world, int blockX, int blockY, int blockZ, BlockFace facing) {
        this.world = world;
        this.facing = facing;
        this.normalX = facing.getModX();
        this.normalZ = facing.getModZ();
        // Blickrichtung des Betrachters = -normal; sein "rechts" ist dann (normal.z, -normal.x)
        this.rightX = normalZ;
        this.rightZ = -normalX;
        this.centerX = blockX + 0.5 + normalX * 0.5;
        this.centerY = blockY + 0.5;
        this.centerZ = blockZ + 0.5 + normalZ * 0.5;
        this.yaw = yawOf(facing);
    }

    /** Yaw, mit dem ein Display in die Richtung {@code face} "schaut" (Text liest man dann von dieser Seite). */
    public static float yawOf(BlockFace face) {
        return switch (face) {
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> -90f;
            default -> 0f; // SOUTH
        };
    }

    public World getWorld() {
        return world;
    }

    public BlockFace getFacing() {
        return facing;
    }

    public float getYaw() {
        return yaw;
    }

    /** Weltposition eines Punkts im Board-Koordinatensystem. */
    public Location at(double u, double v, double w) {
        return new Location(world,
                centerX + rightX * u + normalX * w,
                centerY + v,
                centerZ + rightZ * u + normalZ * w,
                yaw, 0f);
    }

    /**
     * Schneidet den Blickstrahl eines Spielers mit der Board-Flaeche und liefert den
     * Treffer als {@code {u, v}} im Board-Koordinatensystem - oder null, wenn der
     * Spieler an der Flaeche vorbei, von hinten oder zu weit weg schaut.
     *
     * Minecraft kennt kein "Hover"-Event fuer Entities, deshalb rechnen wir das selbst
     * aus. Das ist deutlich guenstiger als ein Entity-Raytrace: ein paar Skalarprodukte
     * pro Spieler, ganz ohne Kollisionsabfragen.
     *
     * @param eye          Augenposition des Spielers
     * @param direction    normalisierte Blickrichtung
     * @param maxDistance  maximale Entfernung, bis zu der ein Treffer zaehlt
     */
    public double[] lookTarget(Location eye, Vector direction, double maxDistance) {
        // Die Flaechennormale zeigt von der Wand weg zum Spieler. Wer das Board ansieht,
        // blickt ihr entgegen -> das Skalarprodukt muss deutlich negativ sein.
        double denominator = direction.getX() * normalX + direction.getZ() * normalZ;
        if (denominator > -1.0E-4) {
            return null;
        }
        double distance = ((centerX - eye.getX()) * normalX + (centerZ - eye.getZ()) * normalZ) / denominator;
        if (distance < 0 || distance > maxDistance) {
            return null;
        }
        double hitX = eye.getX() + direction.getX() * distance;
        double hitY = eye.getY() + direction.getY() * distance;
        double hitZ = eye.getZ() + direction.getZ() * distance;
        double u = (hitX - centerX) * rightX + (hitZ - centerZ) * rightZ;
        double v = hitY - centerY;
        return new double[]{u, v};
    }

    /** Stehplatz 2 Bloecke vor dem Board (auf Hoehe seiner Unterkante), Blick zur Wand. */
    public Location standingSpot() {
        Location loc = at(0, -0.5, 2.0);
        loc.setYaw(yawOf(facing.getOppositeFace()));
        loc.setPitch(0f);
        return loc;
    }
}
