package de.Z7534.ztone.stoneattack.modules.leaderboard.hologram;

import de.Z7534.ztone.stoneattack.Stoneattack;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Erzeugt die Hologramm-Bausteine (TextDisplay / ItemDisplay / Interaction) über die Paper-API.
 */
public class DisplayFactory {

    public static final String ENTITY_TAG = "stoneattack_hologram";

    private final Stoneattack plugin;
    private final Map<Integer, Entity> entities = new HashMap<>();
    private final Map<Integer, Object> lastState = new HashMap<>();

    public DisplayFactory(Stoneattack plugin) {
        this.plugin = plugin;
    }

    // ----------------------------------------------------------------
    // Spawnen
    // ----------------------------------------------------------------

    private <T extends Entity> T spawn(Location loc, Class<T> type, Player viewer, boolean visibleByDefault,
                                       Consumer<T> init) {
        World world = loc.getWorld();
        T entity = world.spawn(loc, type, e -> {
            e.setVisibleByDefault(visibleByDefault);
            e.setPersistent(false);
            e.addScoreboardTag(ENTITY_TAG);
            init.accept(e);
        });
        if (viewer != null) {
            viewer.showEntity(plugin.getPlugin(), entity);
        }
        entities.put(entity.getEntityId(), entity);
        return entity;
    }

    private void applyCommon(Display display, float scaleX, float scaleY, float scaleZ) {
        FileConfiguration cfg = plugin.getConfigManager().get();
        display.setBillboard(Display.Billboard.FIXED);
        display.setViewRange(viewRange(cfg));
        if (cfg.getBoolean("leaderboard.render.fullbright", true)) {
            display.setBrightness(new Display.Brightness(15, 15));
        }
        display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                new Vector3f(scaleX, scaleY, scaleZ), new AxisAngle4f()));
    }

    private float viewRange(FileConfiguration cfg) {
        double blocks = cfg.getDouble("leaderboard.render.view-distance-blocks", 32);
        return (float) Math.max(1.0, blocks / 64.0);
    }

    /**
     * Ein Text-Display mit garantiert 100% transparentem Hintergrund (Color.fromARGB(0, 0, 0, 0)),
     * damit niemals graue Vanilla-Boxen vor oder hinter Custom-PNG-Texturen erscheinen.
     */
    public TextDisplay spawnText(Location loc, Player viewer, boolean visibleByDefault, float scale) {
        return spawnText(loc, viewer, visibleByDefault, scale, TextDisplay.TextAlignment.CENTER);
    }

    /**
     * Ein Text-Display mit expliziter Textausrichtung (LEFT für Eintragszeilen, CENTER für Titel/Buttons).
     */
    public TextDisplay spawnText(Location loc, Player viewer, boolean visibleByDefault, float scale, TextDisplay.TextAlignment alignment) {
        boolean shadow = plugin.getConfigManager().get().getBoolean("leaderboard.render.text-shadow", true);
        return spawn(loc, TextDisplay.class, viewer, visibleByDefault, d -> {
            applyCommon(d, scale, scale, scale);
            d.setSeeThrough(false);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(shadow);
            d.setAlignment(alignment);
            d.setLineWidth(2000);
        });
    }

    /**
     * Ein Item-Display für Custom-Model-Data (z. B. Custom-Textur Buttons oder Hintergründe).
     */
    public ItemDisplay spawnItem(Location loc, Player viewer, boolean visibleByDefault,
                                 Material mat, int customModelData, String itemModelKey,
                                 float scaleX, float scaleY, float scaleZ) {
        return spawn(loc, ItemDisplay.class, viewer, visibleByDefault, d -> {
            applyCommon(d, scaleX, scaleY, scaleZ);
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                applyModel(meta, customModelData, itemModelKey);
                item.setItemMeta(meta);
            }
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        });
    }

    public ItemDisplay spawnBackgroundDisplay(Location loc, Material mat, int customModelData, String itemModelKey) {
        return spawnBackgroundDisplay(loc, mat, customModelData, itemModelKey, 3.0f, 3.0f);
    }

    /** Ein Hintergrund-Stueck mit exakter Breite/Hoehe in Bloecken (flache Z-Achse). */
    public ItemDisplay spawnBackgroundDisplay(Location loc, Material mat, int customModelData, String itemModelKey,
                                              float widthBlocks, float heightBlocks) {
        return spawn(loc, ItemDisplay.class, null, true, d -> {
            applyCommon(d, widthBlocks, heightBlocks, 0.05f);
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                applyModel(meta, customModelData, itemModelKey);
                item.setItemMeta(meta);
            }
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        });
    }

    private void applyModel(ItemMeta meta, int customModelData, String itemModelKey) {
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        if (itemModelKey != null && !itemModelKey.trim().isEmpty()) {
            String keyStr = itemModelKey.trim();
            String ns = "stoneattack";
            String path = keyStr;
            if (keyStr.contains(":")) {
                String[] parts = keyStr.split(":", 2);
                ns = parts[0];
                path = parts[1];
            }
            if (path.startsWith("item/")) {
                path = path.substring(5);
            } else if (path.startsWith("items/")) {
                path = path.substring(6);
            }
            try {
                NamespacedKey key = NamespacedKey.fromString(ns.toLowerCase(Locale.ROOT) + ":" + path.toLowerCase(Locale.ROOT));
                if (key != null) {
                    Method m = meta.getClass().getMethod("setItemModel", NamespacedKey.class);
                    m.invoke(meta, key);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Ein prozedurales Rechteck für den Fallback-Modus ohne Resourcepack.
     */
    public TextDisplay spawnRect(Location loc, double widthBlocks, double heightBlocks, int argbColor,
                                 boolean visibleByDefault) {
        float scaleX = (float) (widthBlocks * 40.0);
        float scaleY = (float) (heightBlocks * 40.0);
        float scaleZ = 0.05f;

        return spawn(loc, TextDisplay.class, null, visibleByDefault, d -> {
            applyCommon(d, scaleX, scaleY, scaleZ);
            d.setSeeThrough(false);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(argbColor));
            d.text(Component.text(" "));
            d.setShadowed(false);
        });
    }

    public Interaction spawnInteraction(Location loc, float width, float height) {
        return spawn(loc, Interaction.class, null, true, i -> {
            i.setInteractionWidth(width);
            i.setInteractionHeight(height);
            i.setResponsive(true);
        });
    }

    // ----------------------------------------------------------------
    // Laufende Updates
    // ----------------------------------------------------------------

    public void setItemModel(ItemDisplay display, int customModelData, String itemModelKey) {
        setItemModel(display, Material.PAPER, customModelData, itemModelKey);
    }

    public void setItemModel(ItemDisplay display, Material material, int customModelData, String itemModelKey) {
        if (display == null || !display.isValid()) {
            return;
        }
        Object old = lastState.get(display.getEntityId());
        String stateKey = customModelData + "|" + itemModelKey;
        if (stateKey.equals(old)) {
            return;
        }
        lastState.put(display.getEntityId(), stateKey);

        ItemStack item = display.getItemStack();
        if (item == null || (material != null && item.getType() != material)) {
            item = new ItemStack(material != null ? material : Material.PAPER);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            applyModel(meta, customModelData, itemModelKey);
            item.setItemMeta(meta);
        }
        display.setItemStack(item);
    }

    public void setBackground(TextDisplay display, int argb) {
        if (display == null || !display.isValid()) {
            return;
        }
        Object old = lastState.get(display.getEntityId());
        if (old instanceof Integer i && i == argb) {
            return;
        }
        lastState.put(display.getEntityId(), argb);
        display.setBackgroundColor(Color.fromARGB(argb));
    }

    public void setText(TextDisplay display, Component text) {
        if (display == null || !display.isValid()) {
            return;
        }
        Object old = lastState.get(display.getEntityId());
        if (text != null && text.equals(old)) {
            return;
        }
        lastState.put(display.getEntityId(), text);
        display.text(text);
    }

    public void transform(Display display, Transformation transformation) {
        transform(display, transformation, 0);
    }

    public void transform(Display display, Transformation transformation, int interpolationDuration) {
        if (display == null || !display.isValid()) {
            return;
        }
        Transformation cur = display.getTransformation();
        if (cur.equals(transformation)) {
            return;
        }
        display.setInterpolationDuration(interpolationDuration);
        display.setInterpolationDelay(0);
        display.setTransformation(transformation);
    }

    /**
     * Sanfte Skalierungs- & Z-Offset-Animation auf einem ItemDisplay mit flacher Z-Achse (0.05f).
     */
    public void applyScaleAndOffset(ItemDisplay display, float baseWidth, float baseHeight, float baseDepth,
                                    float scaleFactor, float offsetX, float offsetY, float offsetZ,
                                    int interpolationDuration) {
        if (display == null || !display.isValid()) {
            return;
        }
        Transformation t = new Transformation(
                new Vector3f(offsetX, offsetY, offsetZ),
                new AxisAngle4f(),
                new Vector3f(baseWidth * scaleFactor, baseHeight * scaleFactor, baseDepth),
                new AxisAngle4f()
        );
        transform(display, t, interpolationDuration);
    }

    // ----------------------------------------------------------------
    // Aufraeumen
    // ----------------------------------------------------------------

    public void destroy(Entity entity) {
        if (entity == null) {
            return;
        }
        entities.remove(entity.getEntityId());
        lastState.remove(entity.getEntityId());
        if (entity.isValid()) {
            entity.remove();
        }
    }

    public void destroy(Collection<? extends Entity> list) {
        for (Entity e : list) {
            destroy(e);
        }
    }

    public void destroyAll() {
        for (Entity e : entities.values()) {
            if (e != null && e.isValid()) {
                e.remove();
            }
        }
        entities.clear();
        lastState.clear();
    }

    /**
     * Bereinigt restlos alle verwaisten Hologramm-Entities auf dem Server
     * (auch nach Reloads, Abstürzen oder /kill).
     */
    public void purgeStaleEntities() {
        try {
            for (World world : plugin.getServer().getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity.getScoreboardTags().contains(ENTITY_TAG)) {
                        entity.remove();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        entities.clear();
        lastState.clear();
    }
}
