package de.Z7534.ztone.stoneattack.modules.leaderboard.hologram;

import de.Z7534.ztone.stoneattack.Stoneattack;
import de.Z7534.ztone.stoneattack.modules.leaderboard.LeaderboardCache;
import de.Z7534.ztone.stoneattack.modules.leaderboard.LeaderboardEntry;
import de.Z7534.ztone.stoneattack.modules.leaderboard.LeaderboardType;
import de.Z7534.ztone.stoneattack.modules.leaderboard.PlayerSessionManager;
import de.Z7534.ztone.stoneattack.modules.leaderboard.PlayerSessionState;
import de.Z7534.ztone.stoneattack.modules.leaderboard.TimePeriod;
import de.Z7534.ztone.stoneattack.util.ColorUtil;
import de.Z7534.ztone.stoneattack.util.FormatUtil;
import de.Z7534.ztone.stoneattack.util.SmallCaps;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet alle Leaderboard-Hologramme mit intelligentem Dual-Mode-Rendering:
 *
 * 1. Modus A (Resourcepack aktiv):
 *    - Custom-Background ItemDisplay (CustomModelData: 1000) im perfekten 1:1 Blockraster (3.0 x 3.0 Blöcke).
 *    - 10 separat platzierte Textzeilen, exakt linksbündig auf die 10 Slots der PNG-Textur ausgerichtet.
 *    - Titel im Header über der Leuchtlinie, Du-Zeile im Footer, integriertes Controls-Deck für Pager und Modus-Buttons.
 *    - Custom-Button ItemDisplays (Idle: 1001, Hover: 1002, Active: 1003) im exakten 4:1 Seitenverhältnis.
 *    - TextDisplays mit garantiert transparentem Hintergrund.
 *
 * 2. Modus B (Fallback / Kein Resourcepack geladen / Bedrock):
 *    - ItemDisplays des Resourcepacks werden für diesen Spieler ausgeblendet.
 *    - Prozedurales Hintergrundsystem (#1c222b Hintergrund mit #00e5ff Akzentrahmen).
 *    - Dynamische Farb- und Skalierungs-Hover-Animation der Buttons.
 */
public class HologramManager {

    private static final double LAYER_BORDER = 0.010;
    private static final double LAYER_BODY = 0.015;
    private static final double LAYER_CONTENT = 0.040;

    private static final double HOVER_STICKY_MARGIN = 0.02;

    private static final TimePeriod[] MODE_PERIODS = {TimePeriod.GLOBAL, TimePeriod.MONTH, TimePeriod.WEEK};
    private static final ClickBinding.Action[] MODE_ACTIONS = {
            ClickBinding.Action.GLOBAL_BUTTON,
            ClickBinding.Action.MONTH_BUTTON,
            ClickBinding.Action.WEEK_BUTTON
    };

    private final Stoneattack plugin;
    private final HologramPositionStore positionStore;
    private final LeaderboardCache cache;
    private final PlayerSessionManager sessionManager;
    private final DisplayFactory factory;
    private final ResourcePackStatusTracker tracker;
    private final HoverAnimator animator;

    private final Map<LeaderboardType, HologramBoard> boards = new EnumMap<>(LeaderboardType.class);
    private final Map<UUID, Map<LeaderboardType, HologramInstance>> viewerInstances = new ConcurrentHashMap<>();
    private final Map<Integer, ClickBinding> clickBindings = new ConcurrentHashMap<>();

    private BukkitTask tickTask;
    private BukkitTask hoverTask;

    public HologramManager(Stoneattack plugin, HologramPositionStore positionStore,
                           LeaderboardCache cache, PlayerSessionManager sessionManager) {
        this.plugin = plugin;
        this.positionStore = positionStore;
        this.cache = cache;
        this.sessionManager = sessionManager;
        this.factory = new DisplayFactory(plugin);
        this.tracker = new ResourcePackStatusTracker(plugin, this);
        this.animator = new HoverAnimator(plugin, factory, tracker);
        plugin.getServer().getPluginManager().registerEvents(tracker, plugin.getPlugin());
    }

    public HologramManager(Stoneattack plugin, HologramPositionStore positionStore,
                           LeaderboardCache cache, PlayerSessionManager sessionManager,
                           DisplayFactory factory, ResourcePackStatusTracker tracker,
                           HoverAnimator animator) {
        this.plugin = plugin;
        this.positionStore = positionStore;
        this.cache = cache;
        this.sessionManager = sessionManager;
        this.factory = factory;
        this.tracker = tracker;
        this.animator = animator;
    }

    public ResourcePackStatusTracker getTracker() {
        return tracker;
    }

    public HologramPositionStore getPositionStore() {
        return positionStore;
    }

    public void reload() {
        onPositionsReloaded();
    }

    public void start() {
        factory.purgeStaleEntities();
        long tickInterval = Math.max(1L, plugin.getConfigManager().get().getLong("leaderboard.render.tick-interval-ticks", 10L));
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin.getPlugin(), this::tick, 20L, tickInterval);

        long hoverInterval = Math.max(1L, plugin.getConfigManager().get().getLong("leaderboard.render.hover-interval-ticks", 1L));
        hoverTask = Bukkit.getScheduler().runTaskTimer(plugin.getPlugin(), this::tickHover, 20L, hoverInterval);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (hoverTask != null) {
            hoverTask.cancel();
            hoverTask = null;
        }
        for (LeaderboardType type : LeaderboardType.values()) {
            removeBoard(type);
        }
        viewerInstances.clear();
        clickBindings.clear();
        factory.purgeStaleEntities();
    }

    public void onPositionsReloaded() {
        for (LeaderboardType type : LeaderboardType.values()) {
            removeBoard(type);
        }
        factory.purgeStaleEntities();
    }

    public void onResourcePackStatusChanged(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin.getPlugin(), () -> {
            for (Map.Entry<LeaderboardType, HologramBoard> entry : boards.entrySet()) {
                LeaderboardType type = entry.getKey();
                HologramBoard board = entry.getValue();
                Map<LeaderboardType, HologramInstance> map = viewerInstances.get(player.getUniqueId());
                HologramInstance instance = map != null ? map.get(type) : null;
                if (board != null && instance != null) {
                    render(player, type, board, instance, true);
                }
            }
        });
    }

    public int entriesForHeight(double heightBlocks) {
        return HologramLayout.entriesForHeight(plugin.getConfigManager().get(), heightBlocks);
    }

    public double effectiveHeight(double heightBlocks) {
        return new HologramLayout(plugin.getConfigManager().get(), heightBlocks).effectiveHeight();
    }

    // ------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------

    private void tick() {
        double viewDistance = plugin.getConfigManager().get().getDouble("leaderboard.render.view-distance-blocks", 32);
        double maxDistanceSquared = viewDistance * viewDistance;

        for (LeaderboardType type : LeaderboardType.values()) {
            HologramDefinition def = positionStore.get(type);
            BoardFrame frame = def == null ? null : def.frame();
            if (frame == null) {
                removeBoard(type);
                continue;
            }

            Location center = frame.at(0, 0, 0);
            List<Player> nearby = new ArrayList<>();
            for (Player player : frame.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(center) <= maxDistanceSquared) {
                    nearby.add(player);
                }
            }
            if (nearby.isEmpty()) {
                removeBoard(type);
                continue;
            }

            HologramBoard board = boards.get(type);
            if (board != null && !board.isValid()) {
                removeBoard(type);
                board = null;
            }
            if (board == null) {
                if (!frame.getWorld().isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) {
                    continue;
                }
                board = buildBoard(type, frame, def.getHeightBlocks());
                boards.put(type, board);
            }

            Set<UUID> nearbyIds = new HashSet<>();
            for (Player player : nearby) {
                nearbyIds.add(player.getUniqueId());
                ensureViewer(player, type, board, frame);
            }

            for (Map.Entry<UUID, Map<LeaderboardType, HologramInstance>> entry : viewerInstances.entrySet()) {
                if (nearbyIds.contains(entry.getKey())) {
                    continue;
                }
                HologramInstance gone = entry.getValue().remove(type);
                if (gone != null) {
                    factory.destroy(gone.all());
                }
            }
        }
    }

    private void tickHover() {
        if (animator == null) {
            return;
        }

        double maxDist = plugin.getConfigManager().get().getDouble("leaderboard.placement.max-distance", 8.0);

        for (Map.Entry<UUID, Map<LeaderboardType, HologramInstance>> entry : viewerInstances.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }

            Location eye = player.getEyeLocation();
            Vector dir = eye.getDirection().normalize();

            for (Map.Entry<LeaderboardType, HologramInstance> instEntry : entry.getValue().entrySet()) {
                LeaderboardType type = instEntry.getKey();
                HologramInstance inst = instEntry.getValue();
                HologramBoard board = boards.get(type);
                HologramDefinition def = positionStore.get(type);
                if (board == null || def == null || def.frame() == null) {
                    continue;
                }

                BoardFrame frame = def.frame();
                double[] hit = frame.lookTarget(eye, dir, maxDist);

                ClickBinding.Action hovered = null;
                if (hit != null) {
                    double u = hit[0];
                    double v = hit[1];

                    for (Map.Entry<ClickBinding.Action, double[]> areaEntry : board.getButtonAreas().entrySet()) {
                        ClickBinding.Action action = areaEntry.getKey();
                        double[] a = areaEntry.getValue(); // {u0, v0, w, h}
                        double margin = (action == inst.hoveredButton) ? HOVER_STICKY_MARGIN : 0.0;

                        if (u >= a[0] - margin && u <= a[0] + a[2] + margin
                                && v >= a[1] - margin && v <= a[1] + a[3] + margin) {
                            hovered = action;
                            break;
                        }
                    }
                }

                animator.update(player, frame, board, inst, hovered);
            }
        }
    }

    private void ensureViewer(Player player, LeaderboardType type, HologramBoard board, BoardFrame frame) {
        Map<LeaderboardType, HologramInstance> map = viewerInstances.computeIfAbsent(player.getUniqueId(),
                k -> new EnumMap<>(LeaderboardType.class));
        HologramInstance instance = map.get(type);
        if (instance != null && !instance.isValid()) {
            factory.destroy(instance.all());
            map.remove(type);
            instance = null;
        }
        if (instance == null) {
            instance = spawnViewerPart(player, frame, board.getLayout());
            map.put(type, instance);
        }
        render(player, type, board, instance, false);
    }

    // ------------------------------------------------------------
    // Geteilter Teil: Hintergrund & Klickflächen
    // ------------------------------------------------------------

    private void addBackgroundPiece(HologramBoard board, Location loc, int model, String key,
                                    float widthBlocks, float heightBlocks) {
        ItemDisplay piece = factory.spawnBackgroundDisplay(loc, Material.PAPER, model, key, widthBlocks, heightBlocks);
        board.addPackBackground(piece);
        board.getEntities().add(piece);
    }

    private HologramBoard buildBoard(LeaderboardType type, BoardFrame f, double heightBlocks) {
        FileConfiguration cfg = plugin.getConfigManager().get();
        HologramLayout l = new HologramLayout(cfg, heightBlocks);
        HologramBoard board = new HologramBoard(type, l, f);

        // 1. Modus A: Custom-Background ItemDisplay (CustomModelData 1000 / stoneattack:leaderboard_bg)
        // Exakt auf 3.0 x 3.0 Blöcke skaliert (1:1 Blockraster) mit flacher Z-Achse (0.05f)
        // Z-Offset: -0.020f hinter der Text-Ebene (LAYER_CONTENT - 0.020)
        // Modularer Aufbau: Kopf (inkl. Reihe 1-3) + N Reihen + Fueller + Fuss.
        // Jedes Stueck wird 1:1 im 256-px-pro-Block-Raster platziert, damit jede
        // Text-Reihe exakt in ihrem Textur-Slot sitzt - bei jeder Board-Hoehe.
        double bgLayer = LAYER_CONTENT - 0.020;
        float bgWidth = (float) HologramLayout.BACKGROUND_WIDTH;

        addBackgroundPiece(board, f.at(0, l.bgTopCenter(), bgLayer),
                positionStore.getModelBgTop(), positionStore.getModelBgTopKey(),
                bgWidth, (float) l.bgTopHeight());

        for (int r = 0; r < l.middleRowCount(); r++) {
            addBackgroundPiece(board, f.at(0, l.bgRowCenter(r), bgLayer),
                    positionStore.getModelBgRow(), positionStore.getModelBgRowKey(),
                    bgWidth, (float) l.bgRowHeight());
        }

        if (l.fillerPx > 0.05) {
            addBackgroundPiece(board, f.at(0, l.bgFillerCenter(), bgLayer),
                    positionStore.getModelBgFiller(), positionStore.getModelBgFillerKey(),
                    bgWidth, (float) l.bgFillerHeight());
        }

        addBackgroundPiece(board, f.at(0, l.bgFooterCenter(), bgLayer),
                positionStore.getModelBgFooter(), positionStore.getModelBgFooterKey(),
                bgWidth, (float) l.bgFooterHeight());

        // 2. Modus B: Fallback für Spieler ohne Resourcepack (Dunkelgrau #1c222b)
        // Nur exakt 1 TextDisplay für Fallback - keine prozeduralen Multi-Slice-Segmente!
        int colorDarkGrey = Color.fromARGB(220, 24, 28, 36).asARGB(); // #181c24
        TextDisplay fallbackPanel = factory.spawnRect(
                f.at(0, l.panelCenter, LAYER_BODY),
                l.width, l.panelHeight, colorDarkGrey, false
        );
        board.getFallbackEntities().add(fallbackPanel);
        board.getEntities().add(fallbackPanel);

        // 3. Klickflächen (Interaction-Entities sauber und exakt auf den Buttons positioniert)
        interaction(board, f, type, ClickBinding.Action.ARROW_LEFT,
                l.arrowLeftCenter() - HologramLayout.ARROW_WIDTH / 2.0, l.pagerBottom,
                HologramLayout.ARROW_WIDTH, HologramLayout.ARROW_HEIGHT);

        interaction(board, f, type, ClickBinding.Action.ARROW_RIGHT,
                l.arrowRightCenter() - HologramLayout.ARROW_WIDTH / 2.0, l.pagerBottom,
                HologramLayout.ARROW_WIDTH, HologramLayout.ARROW_HEIGHT);

        for (int i = 0; i < MODE_PERIODS.length; i++) {
            double u0 = l.modeLeft(i);
            interaction(board, f, type, MODE_ACTIONS[i], u0, l.modeBottom,
                    HologramLayout.MODE_WIDTH, HologramLayout.MODE_HEIGHT);
        }

        return board;
    }

    private void interaction(HologramBoard board, BoardFrame f, LeaderboardType type, ClickBinding.Action action,
                             double u0, double v0, double width, double height) {
        float generousWidth = (float) width;
        float generousHeight = (float) height;
        double depth = LAYER_CONTENT;

        Interaction interaction = factory.spawnInteraction(
                f.at(u0 + width / 2.0, v0, depth),
                generousWidth,
                generousHeight
        );
        board.getEntities().add(interaction);
        board.getInteractions().add(interaction);
        clickBindings.put(interaction.getEntityId(), new ClickBinding(type, action));

        board.getButtonAreas().put(action, new double[]{u0, v0, width, height});
    }

    // ------------------------------------------------------------
    // Teil pro Spieler (TextDisplays & Button-ItemDisplays)
    // ------------------------------------------------------------

    private HologramInstance spawnViewerPart(Player viewer, BoardFrame f, HologramLayout l) {
        FileConfiguration cfg = plugin.getConfigManager().get();
        HologramInstance instance = new HologramInstance();

        // 1. Titel im Header (über der Leuchtlinie auf der Hintergrund-Textur)
        instance.title = factory.spawnText(
                f.at(0, l.textPivot(l.titleCenter, l.textScale), LAYER_CONTENT),
                viewer, false, l.textScale, TextDisplay.TextAlignment.CENTER);
        factory.setBackground(instance.title, 0);
        instance.title.setLineWidth(2000);

        // 2. 4-Spalten-Displays pro Slot (Rang, Name, Arrow, Wert)
        for (int i = 0; i < l.perPage; i++) {
            double rowY = l.slotCenter(i);
            double pivot = l.textPivot(rowY, l.rowTextScale);

            // Spalte 1: Rang-Badge
            TextDisplay rowDisplay = factory.spawnText(
                    f.at(HologramLayout.COL_RANK_LEFT + 0.08, pivot, LAYER_CONTENT),
                    viewer, false, l.rowTextScale, TextDisplay.TextAlignment.CENTER);
            factory.setBackground(rowDisplay, 0);
            rowDisplay.setLineWidth(2000);
            instance.rowDisplays.add(rowDisplay);

            // Spalte 2: Spielername (eigenes Display für dynamische Größenskalierung)
            TextDisplay nameDisplay = factory.spawnText(
                    f.at(HologramLayout.COL_NAME_LEFT + 0.44, pivot, LAYER_CONTENT),
                    viewer, false, l.rowTextScale, TextDisplay.TextAlignment.CENTER);
            factory.setBackground(nameDisplay, 0);
            nameDisplay.setLineWidth(2000);
            instance.rowNameDisplays.add(nameDisplay);

            // Spalte 3: Trennpfeil »
            TextDisplay arrowDisplay = factory.spawnText(
                    f.at(HologramLayout.COL_ARROW_CENTER, pivot, LAYER_CONTENT),
                    viewer, false, l.rowTextScale, TextDisplay.TextAlignment.CENTER);
            factory.setBackground(arrowDisplay, 0);
            arrowDisplay.setLineWidth(2000);
            instance.rowArrowDisplays.add(arrowDisplay);

            // Spalte 4: Wert & Icon
            TextDisplay valueDisplay = factory.spawnText(
                    f.at(HologramLayout.COL_VAL_RIGHT - 0.40, pivot, LAYER_CONTENT),
                    viewer, false, l.rowTextScale, TextDisplay.TextAlignment.CENTER);
            factory.setBackground(valueDisplay, 0);
            valueDisplay.setLineWidth(2000);
            instance.rowValueDisplays.add(valueDisplay);
        }

        // 3. "Du"-Zeile im Footerbereich des Panels (identische 4 Spalten)
        double selfPivot = l.textPivot(l.selfCenter, l.selfTextScale);
        instance.self = factory.spawnText(
                f.at(HologramLayout.COL_RANK_LEFT + 0.08, selfPivot, LAYER_CONTENT),
                viewer, false, l.selfTextScale, TextDisplay.TextAlignment.CENTER);
        factory.setBackground(instance.self, 0);
        instance.self.setLineWidth(2000);

        instance.selfName = factory.spawnText(
                f.at(HologramLayout.COL_NAME_LEFT + 0.44, selfPivot, LAYER_CONTENT),
                viewer, false, l.selfTextScale, TextDisplay.TextAlignment.CENTER);
        factory.setBackground(instance.selfName, 0);
        instance.selfName.setLineWidth(2000);

        instance.selfArrow = factory.spawnText(
                f.at(HologramLayout.COL_ARROW_CENTER, selfPivot, LAYER_CONTENT),
                viewer, false, l.selfTextScale, TextDisplay.TextAlignment.CENTER);
        factory.setBackground(instance.selfArrow, 0);
        instance.selfArrow.setLineWidth(2000);

        instance.selfValue = factory.spawnText(
                f.at(HologramLayout.COL_VAL_RIGHT - 0.40, selfPivot, LAYER_CONTENT),
                viewer, false, l.selfTextScale, TextDisplay.TextAlignment.CENTER);
        factory.setBackground(instance.selfValue, 0);
        instance.selfValue.setLineWidth(2000);

        // 4. Seitenanzeige
        if (cfg.getBoolean("leaderboard.show-page-indicator", true)) {
            instance.pager = factory.spawnText(
                    f.at(0, l.textPivot(l.pagerCenter, l.pagerTextScale), LAYER_CONTENT),
                    viewer, false, l.pagerTextScale, TextDisplay.TextAlignment.CENTER);
            factory.setBackground(instance.pager, 0);
            instance.pager.setLineWidth(2000);
        }

        // 5. Pfeile
        double leftArrowCenter = l.arrowLeftCenter();
        double rightArrowCenter = l.arrowRightCenter();
        double arrowPivot = l.textPivot(l.pagerCenter, l.arrowTextScale);

        instance.arrowLeft = factory.spawnText(
                f.at(leftArrowCenter, arrowPivot, LAYER_CONTENT),
                viewer, false, l.arrowTextScale, TextDisplay.TextAlignment.CENTER);
        factory.setBackground(instance.arrowLeft, 0);
        instance.arrowLeft.setLineWidth(2000);

        instance.arrowRight = factory.spawnText(
                f.at(rightArrowCenter, arrowPivot, LAYER_CONTENT),
                viewer, false, l.arrowTextScale, TextDisplay.TextAlignment.CENTER);
        factory.setBackground(instance.arrowRight, 0);
        instance.arrowRight.setLineWidth(2000);

        instance.labels.put(ClickBinding.Action.ARROW_LEFT,
                new HoverLabel(instance.arrowLeft, l.arrowTextScale, l.centerOffset(), -1.0));
        instance.labels.put(ClickBinding.Action.ARROW_RIGHT,
                new HoverLabel(instance.arrowRight, l.arrowTextScale, l.centerOffset(), 1.0));

        // Pfeil-ItemDisplays für Modus A (Resourcepack): LAYER_CONTENT - 0.010f (+0.010f vor Background)
        int btnIdle = positionStore.getModelButtonIdle();
        String btnIdleKey = positionStore.getModelButtonIdleKey();
        ItemDisplay arrowLeftDisplay = factory.spawnItem(
                f.at(leftArrowCenter, l.pagerCenter, LAYER_CONTENT - 0.010),
                viewer, false, Material.PAPER, btnIdle, btnIdleKey,
                (float) HologramLayout.ARROW_WIDTH, (float) HologramLayout.ARROW_HEIGHT, 0.05f);
        instance.buttonItemDisplays.put(ClickBinding.Action.ARROW_LEFT, arrowLeftDisplay);

        ItemDisplay arrowRightDisplay = factory.spawnItem(
                f.at(rightArrowCenter, l.pagerCenter, LAYER_CONTENT - 0.010),
                viewer, false, Material.PAPER, btnIdle, btnIdleKey,
                (float) HologramLayout.ARROW_WIDTH, (float) HologramLayout.ARROW_HEIGHT, 0.05f);
        instance.buttonItemDisplays.put(ClickBinding.Action.ARROW_RIGHT, arrowRightDisplay);

        // 6. Modus-Buttons mit exaktem 4:1 Seitenverhältnis (0.852 x 0.211 Blöcke)
        double modePivot = l.textPivot(l.modeCenter, l.buttonTextScale);
        for (int i = 0; i < MODE_PERIODS.length; i++) {
            TimePeriod period = MODE_PERIODS[i];
            String icon = cfg.getString("leaderboard.style.icons." + period.key(), "");
            String name = cfg.getString("leaderboard.period-names." + period.key(), period.key());
            String text = icon.isEmpty() ? name : icon + " " + name;
            double uCenter = l.modeCenter(i);

            TextDisplay label = factory.spawnText(
                    f.at(uCenter, modePivot, LAYER_CONTENT),
                    viewer, false, l.buttonTextScale, TextDisplay.TextAlignment.CENTER);
            factory.setBackground(label, 0);
            label.setLineWidth(2000);
            factory.setText(label, styled(viewer, ColorUtil.parse("<white><b>" + text + "</b></white>")));
            instance.modeLabels[i] = label;
            instance.labels.put(MODE_ACTIONS[i], new HoverLabel(label, l.buttonTextScale, l.centerOffset(), 0.0));

            ItemDisplay modeDisplay = factory.spawnItem(
                    f.at(uCenter, l.modeCenter, LAYER_CONTENT - 0.010),
                    viewer, false, Material.PAPER, btnIdle, btnIdleKey,
                    (float) HologramLayout.MODE_WIDTH, (float) HologramLayout.MODE_HEIGHT, 0.05f);
            instance.buttonItemDisplays.put(MODE_ACTIONS[i], modeDisplay);
        }

        return instance;
    }

    // ------------------------------------------------------------
    // Inhalt aktualisieren
    // ------------------------------------------------------------

    private void render(Player viewer, LeaderboardType type, HologramBoard board, HologramInstance instance,
                        boolean force) {
        FileConfiguration cfg = plugin.getConfigManager().get();
        HologramLayout l = board.getLayout();
        int perPage = l.perPage;
        int maxRank = cfg.getInt("leaderboard.max-rank", 50);

        PlayerSessionState session = sessionManager.getState(viewer.getUniqueId(), type);
        TimePeriod period = session.getPeriod();
        int maxPage = Math.max(1, (int) Math.ceil(Math.min(maxRank, cache.getEntryCount(type, period)) / (double) perPage));
        if (session.getPage() > maxPage) {
            session.setPage(maxPage);
        }
        int page = session.getPage();
        instance.page = page;
        instance.maxPage = maxPage;

        boolean hasResourcePack = tracker != null && tracker.hasResourcePack(viewer);

        // Dual-Mode Sichtbarkeits-Umschaltung
        if (hasResourcePack) {
            for (ItemDisplay piece : board.getPackBackgrounds()) {
                viewer.showEntity(plugin.getPlugin(), piece);
            }
            for (Entity fallback : board.getFallbackEntities()) {
                viewer.hideEntity(plugin.getPlugin(), fallback);
            }
            for (ItemDisplay btn : instance.buttonItemDisplays.values()) {
                viewer.hideEntity(plugin.getPlugin(), btn);
            }
        } else {
            for (ItemDisplay piece : board.getPackBackgrounds()) {
                viewer.hideEntity(plugin.getPlugin(), piece);
            }
            for (Entity fallback : board.getFallbackEntities()) {
                viewer.showEntity(plugin.getPlugin(), fallback);
            }
            for (ItemDisplay btn : instance.buttonItemDisplays.values()) {
                viewer.hideEntity(plugin.getPlugin(), btn);
            }
        }

        String key = cache.getVersion() + "|" + period.key() + "|" + page + "|" + hasResourcePack;
        if (!force && key.equals(instance.renderKey)) {
            applyHighlight(viewer, board, instance, period, hasResourcePack);
            return;
        }
        instance.renderKey = key;

        // 1. Titel im Headerbereich (ohne eckige Klammern [ ])
        String periodName = cfg.getString("leaderboard.period-names." + period.key(), period.key());
        String titleTemplate = cfg.getString("leaderboard.titles." + type.key(), type.key());
        titleTemplate = titleTemplate.replace("[%period%]", "%period%").replace("[ %period% ]", "%period%").replace("[", "").replace("]", "");
        factory.setText(instance.title, styled(viewer, ColorUtil.parse(titleTemplate, Map.of("period", periodName))));
        factory.setBackground(instance.title, 0);

        // 2. Einträge der aktuellen Seite auf die Slots verteilen (4 exakt ausgerichtete Spalten)
        List<LeaderboardEntry> pageEntries = cache.getPage(type, period, page, perPage);
        BoardFrame f = board.getFrame();

        boolean useSmallCaps = cfg.getBoolean("leaderboard.small-caps.enabled", true)
                && !(tracker != null && tracker.isBedrockPlayer(viewer) && positionStore.isBedrockSafeText());
        boolean convertUpper = cfg.getBoolean("leaderboard.small-caps.convert-uppercase", true);

        for (int i = 0; i < l.perPage; i++) {
            if (i >= instance.rowDisplays.size() || i >= instance.rowNameDisplays.size()
                    || i >= instance.rowArrowDisplays.size() || i >= instance.rowValueDisplays.size()) {
                break;
            }
            TextDisplay rankDisplay = instance.rowDisplays.get(i);
            TextDisplay nameDisplay = instance.rowNameDisplays.get(i);
            TextDisplay arrowDisplay = instance.rowArrowDisplays.get(i);
            TextDisplay valDisplay = instance.rowValueDisplays.get(i);

            if (rankDisplay == null || !rankDisplay.isValid()) {
                continue;
            }
            double rowY = l.slotCenter(i);
            double pivot = l.textPivot(rowY, l.rowTextScale);

            int rank = (page - 1) * perPage + i + 1;
            if (i < pageEntries.size() && rank <= maxRank) {
                LeaderboardEntry entry = pageEntries.get(i);

                // Spalte 1: Rang-Badge (Linksbündig bündig bei COL_RANK_LEFT)
                String rankBadge;
                if (rank == 1) {
                    rankBadge = "<gradient:#FFE259:#FFA751><b>➊</b></gradient>";
                } else if (rank == 2) {
                    rankBadge = "<gradient:#E0EAFC:#CFDEF3><b>➋</b></gradient>";
                } else if (rank == 3) {
                    rankBadge = "<gradient:#F3904F:#BA4A00><b>➌</b></gradient>";
                } else {
                    rankBadge = "<color:#00e5ff><b>#" + rank + "</b></color>";
                }
                int badgePixels = FormatUtil.getStringPixelWidth(rankBadge);
                double badgeWidthBlocks = badgePixels * 0.025 * l.rowTextScale;
                double badgeCenterU = HologramLayout.COL_RANK_LEFT + (badgeWidthBlocks / 2.0);
                rankDisplay.teleport(f.at(badgeCenterU, pivot, LAYER_CONTENT));
                factory.setText(rankDisplay, styled(viewer, ColorUtil.parse(rankBadge)));
                factory.setBackground(rankDisplay, 0);

                // Spalte 2: Spielername (Linksbündig bündig bei COL_NAME_LEFT, skaliert bei Überlänge)
                String playerName = entry.name();
                if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                    try {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(entry.uuid());
                        playerName = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(op, playerName);
                    } catch (Throwable ignored) {}
                }
                String nameMeasure = useSmallCaps ? SmallCaps.convert(playerName, convertUpper) : playerName;
                int namePixels = FormatUtil.getStringPixelWidth("<b>" + nameMeasure + "</b>");
                double unscaledNameWidth = namePixels * 0.025 * l.rowTextScale;

                float nameScale = l.rowTextScale;
                double finalNameWidth = unscaledNameWidth;
                if (unscaledNameWidth > HologramLayout.NAME_MAX_WIDTH) {
                    float factor = (float) (HologramLayout.NAME_MAX_WIDTH / unscaledNameWidth);
                    nameScale = l.rowTextScale * factor;
                    finalNameWidth = HologramLayout.NAME_MAX_WIDTH;
                }
                factory.transform(nameDisplay, new Transformation(
                        new Vector3f(), new AxisAngle4f(),
                        new Vector3f(nameScale, nameScale, 0.05f), new AxisAngle4f()));

                double namePivot = l.textPivot(rowY, nameScale);
                double nameCenterU = HologramLayout.COL_NAME_LEFT + (finalNameWidth / 2.0);
                nameDisplay.teleport(f.at(nameCenterU, namePivot, LAYER_CONTENT));
                factory.setText(nameDisplay, styled(viewer, ColorUtil.parse("<white><b>" + playerName + "</b></white>")));
                factory.setBackground(nameDisplay, 0);

                // Spalte 3: Trennpfeil » (Fix bei COL_ARROW_CENTER zentriert)
                arrowDisplay.teleport(f.at(HologramLayout.COL_ARROW_CENTER, pivot, LAYER_CONTENT));
                factory.setText(arrowDisplay, ColorUtil.parse("<gray>»</gray>"));
                factory.setBackground(arrowDisplay, 0);

                // Spalte 4: Wert & Icon (Rechtsbündig bündig an COL_VAL_RIGHT)
                String formattedVal = entry.formattedValue();
                String valMeasure = useSmallCaps ? SmallCaps.convert(formattedVal, convertUpper) : formattedVal;
                int valPixels = FormatUtil.getStringPixelWidth(valMeasure);
                double valWidthBlocks = valPixels * 0.025 * l.rowTextScale;
                double maxValWidth = HologramLayout.COL_VAL_RIGHT - (HologramLayout.COL_ARROW_CENTER + 0.08);

                if (valWidthBlocks > maxValWidth) {
                    formattedVal = FormatUtil.formatCompactValue(formattedVal, entry.rawValue());
                    valMeasure = useSmallCaps ? SmallCaps.convert(formattedVal, convertUpper) : formattedVal;
                    valPixels = FormatUtil.getStringPixelWidth(valMeasure);
                    valWidthBlocks = valPixels * 0.025 * l.rowTextScale;
                }

                double valCenterU = HologramLayout.COL_VAL_RIGHT - (valWidthBlocks / 2.0);
                valDisplay.teleport(f.at(valCenterU, pivot, LAYER_CONTENT));
                factory.setText(valDisplay, styled(viewer, ColorUtil.parse("<color:#FFE259><b>" + formattedVal + "</b></color>")));
                factory.setBackground(valDisplay, 0);
            } else {
                int emptyRank = (page - 1) * perPage + i + 1;
                String emptyBadge = "<dark_gray>#" + emptyRank + "</dark_gray>";
                int emptyBadgePixels = FormatUtil.getStringPixelWidth(emptyBadge);
                double emptyBadgeWidth = emptyBadgePixels * 0.025 * l.rowTextScale;
                double emptyBadgeCenter = HologramLayout.COL_RANK_LEFT + (emptyBadgeWidth / 2.0);
                rankDisplay.teleport(f.at(emptyBadgeCenter, pivot, LAYER_CONTENT));
                factory.setText(rankDisplay, styled(viewer, ColorUtil.parse(emptyBadge)));
                factory.setBackground(rankDisplay, 0);

                String emptyDashes = "<dark_gray>―  ―  ―</dark_gray>";
                int dashesPixels = FormatUtil.getStringPixelWidth(emptyDashes);
                double dashesWidth = dashesPixels * 0.025 * l.rowTextScale;
                double dashesCenter = HologramLayout.COL_NAME_LEFT + (dashesWidth / 2.0);
                factory.transform(nameDisplay, new Transformation(
                        new Vector3f(), new AxisAngle4f(),
                        new Vector3f(l.rowTextScale, l.rowTextScale, 0.05f), new AxisAngle4f()));
                nameDisplay.teleport(f.at(dashesCenter, pivot, LAYER_CONTENT));
                factory.setText(nameDisplay, styled(viewer, ColorUtil.parse(emptyDashes)));
                factory.setBackground(nameDisplay, 0);

                arrowDisplay.teleport(f.at(HologramLayout.COL_ARROW_CENTER, pivot, LAYER_CONTENT));
                factory.setText(arrowDisplay, Component.empty());

                valDisplay.teleport(f.at(HologramLayout.COL_VAL_RIGHT - 0.20, pivot, LAYER_CONTENT));
                factory.setText(valDisplay, Component.empty());
            }
        }

        // 3. "Du"-Zeile im Footerbereich (exakt selbe 4 Spalten)
        LeaderboardEntry own = cache.getEntry(type, period, viewer.getUniqueId());
        int ownRank = cache.getRank(type, period, viewer.getUniqueId());
        double selfPivot = l.textPivot(l.selfCenter, l.selfTextScale);

        String selfBadge = "<gradient:#00e5ff:#00a3cc><b>DU</b></gradient>";
        int selfBadgePixels = FormatUtil.getStringPixelWidth("DU");
        double selfBadgeWidth = selfBadgePixels * 0.025 * l.selfTextScale;
        double selfBadgeCenter = HologramLayout.COL_RANK_LEFT + (selfBadgeWidth / 2.0);
        instance.self.teleport(f.at(selfBadgeCenter, selfPivot, LAYER_CONTENT));
        factory.setText(instance.self, styled(viewer, ColorUtil.parse(selfBadge)));
        factory.setBackground(instance.self, 0);

        if (own != null && ownRank > 0) {
            String ownRankStr = "<color:#00e5ff><b>#" + ownRank + "</b></color>";
            int rankPixels = FormatUtil.getStringPixelWidth("#" + ownRank);
            double rankWidth = rankPixels * 0.025 * l.selfTextScale;
            double rankCenter = HologramLayout.COL_NAME_LEFT + (rankWidth / 2.0);
            factory.transform(instance.selfName, new Transformation(
                    new Vector3f(), new AxisAngle4f(),
                    new Vector3f(l.selfTextScale, l.selfTextScale, 0.05f), new AxisAngle4f()));
            instance.selfName.teleport(f.at(rankCenter, selfPivot, LAYER_CONTENT));
            factory.setText(instance.selfName, styled(viewer, ColorUtil.parse(ownRankStr)));
            factory.setBackground(instance.selfName, 0);

            instance.selfArrow.teleport(f.at(HologramLayout.COL_ARROW_CENTER, selfPivot, LAYER_CONTENT));
            factory.setText(instance.selfArrow, ColorUtil.parse("<gray>»</gray>"));
            factory.setBackground(instance.selfArrow, 0);

            String ownVal = own.formattedValue();
            String valMeasure = useSmallCaps ? SmallCaps.convert(ownVal, convertUpper) : ownVal;
            int ownValPixels = FormatUtil.getStringPixelWidth(valMeasure);
            double ownValWidth = ownValPixels * 0.025 * l.selfTextScale;
            double maxValWidth = HologramLayout.COL_VAL_RIGHT - (HologramLayout.COL_ARROW_CENTER + 0.08);

            if (ownValWidth > maxValWidth) {
                ownVal = FormatUtil.formatCompactValue(ownVal, own.rawValue());
                valMeasure = useSmallCaps ? SmallCaps.convert(ownVal, convertUpper) : ownVal;
                ownValPixels = FormatUtil.getStringPixelWidth(valMeasure);
                ownValWidth = ownValPixels * 0.025 * l.selfTextScale;
            }

            double ownValCenter = HologramLayout.COL_VAL_RIGHT - (ownValWidth / 2.0);
            instance.selfValue.teleport(f.at(ownValCenter, selfPivot, LAYER_CONTENT));
            factory.setText(instance.selfValue, styled(viewer, ColorUtil.parse("<color:#FFE259><b>" + ownVal + "</b></color>")));
            factory.setBackground(instance.selfValue, 0);
        } else {
            String fallback = "<gray>―  Keine Daten</gray>";
            int fbPixels = FormatUtil.getStringPixelWidth("―  Keine Daten");
            double fbWidth = fbPixels * 0.025 * l.selfTextScale;
            double fbCenter = HologramLayout.COL_NAME_LEFT + (fbWidth / 2.0);
            factory.transform(instance.selfName, new Transformation(
                    new Vector3f(), new AxisAngle4f(),
                    new Vector3f(l.selfTextScale, l.selfTextScale, 0.05f), new AxisAngle4f()));
            instance.selfName.teleport(f.at(fbCenter, selfPivot, LAYER_CONTENT));
            factory.setText(instance.selfName, styled(viewer, ColorUtil.parse(fallback)));
            factory.setBackground(instance.selfName, 0);

            instance.selfArrow.teleport(f.at(HologramLayout.COL_ARROW_CENTER, selfPivot, LAYER_CONTENT));
            factory.setText(instance.selfArrow, Component.empty());

            instance.selfValue.teleport(f.at(HologramLayout.COL_VAL_RIGHT - 0.20, selfPivot, LAYER_CONTENT));
            factory.setText(instance.selfValue, Component.empty());
        }

        // 4. Seitenanzeige
        if (instance.pager != null) {
            factory.setText(instance.pager, styled(viewer, ColorUtil.parse(
                    cfg.getString("leaderboard.page-indicator-format", "<gray>Seite %page%/%max_page%</gray>"),
                    Map.of("page", String.valueOf(page), "max_page", String.valueOf(maxPage)))));
            factory.setBackground(instance.pager, 0);
        }

        // 5. Pfeile
        factory.setText(instance.arrowLeft, arrowLabel(cfg, "arrow-left", "\\u276E", page <= 1));
        factory.setText(instance.arrowRight, arrowLabel(cfg, "arrow-right", "\\u276F", page >= maxPage));

        applyHighlight(viewer, board, instance, period, hasResourcePack);
    }

    private Component arrowLabel(FileConfiguration cfg, String key, String fallback, boolean inactive) {
        String icon = cfg.getString("leaderboard.style.icons." + key, fallback);
        return inactive
                ? ColorUtil.parse("<dark_gray>" + icon + "</dark_gray>")
                : ColorUtil.parse("<gradient:#FFFFFF:#B0B0B0><b>" + icon + "</b></gradient>");
    }

    private void applyHighlight(Player viewer, HologramBoard board, HologramInstance instance, TimePeriod period, boolean hasResourcePack) {
        FileConfiguration cfg = plugin.getConfigManager().get();

        for (int i = 0; i < MODE_PERIODS.length; i++) {
            TimePeriod p = MODE_PERIODS[i];
            if (instance.modeLabels[i] != null) {
                String icon = cfg.getString("leaderboard.style.icons." + p.key(), "");
                String name = cfg.getString("leaderboard.period-names." + p.key(), period.key());
                String text = icon.isEmpty() ? name : icon + " " + name;
                boolean active = (p == period);

                if (active) {
                    String formatted = "<gradient:#00e5ff:#00a3cc><b>" + text + "</b></gradient>";
                    factory.setText(instance.modeLabels[i], styled(viewer, ColorUtil.parse(formatted)));
                } else {
                    String formatted = "<gray><b>" + text + "</b></gray>";
                    factory.setText(instance.modeLabels[i], styled(viewer, ColorUtil.parse(formatted)));
                }
                if (hasResourcePack) {
                    factory.setBackground(instance.modeLabels[i], 0);
                }
            }
        }
        instance.shownHighlight = period;
    }

    private Component styled(Player viewer, Component component) {
        FileConfiguration cfg = plugin.getConfigManager().get();
        if (!cfg.getBoolean("leaderboard.small-caps.enabled", true)) {
            return component;
        }

        if (tracker != null && tracker.isBedrockPlayer(viewer) && positionStore.isBedrockSafeText()) {
            return component;
        }

        return SmallCaps.apply(component, cfg.getBoolean("leaderboard.small-caps.convert-uppercase", true));
    }

    // ------------------------------------------------------------
    // Klicks
    // ------------------------------------------------------------

    public boolean isTrackedEntity(int entityId) {
        return clickBindings.containsKey(entityId);
    }

    /**
     * Prüft per Raycast, ob der Spieler gerade einen Button auf einem Board anschaut,
     * und löst den Klick sofort aus (auch aus bis zu 8 Blöcken Entfernung).
     */
    public boolean tryRaycastClick(Player clicker) {
        if (clicker == null || !clicker.isOnline()) {
            return false;
        }
        double maxDist = plugin.getConfigManager().get().getDouble("leaderboard.placement.max-distance", 8.0);
        Location eye = clicker.getEyeLocation();
        Vector dir = eye.getDirection().normalize();

        Map<LeaderboardType, HologramInstance> map = viewerInstances.get(clicker.getUniqueId());
        if (map == null) {
            return false;
        }

        for (Map.Entry<LeaderboardType, HologramInstance> instEntry : map.entrySet()) {
            LeaderboardType type = instEntry.getKey();
            HologramInstance inst = instEntry.getValue();
            HologramBoard board = boards.get(type);
            HologramDefinition def = positionStore.get(type);
            if (board == null || def == null || def.frame() == null) {
                continue;
            }
            BoardFrame frame = def.frame();
            if (!frame.getWorld().equals(eye.getWorld())) {
                continue;
            }
            double[] hit = frame.lookTarget(eye, dir, maxDist);
            if (hit == null) {
                continue;
            }
            double u = hit[0];
            double v = hit[1];
            for (Map.Entry<ClickBinding.Action, double[]> areaEntry : board.getButtonAreas().entrySet()) {
                ClickBinding.Action action = areaEntry.getKey();
                double[] a = areaEntry.getValue();
                double margin = 0.04;
                if (u >= a[0] - margin && u <= a[0] + a[2] + margin
                        && v >= a[1] - margin && v <= a[1] + a[3] + margin) {
                    executeAction(clicker, type, action, board, inst);
                    return true;
                }
            }
        }
        return false;
    }

    private void executeAction(Player clicker, LeaderboardType type, ClickBinding.Action action,
                               HologramBoard board, HologramInstance instance) {
        PlayerSessionState session = sessionManager.getState(clicker.getUniqueId(), type);
        switch (action) {
            case GLOBAL_BUTTON -> { session.setPeriod(TimePeriod.GLOBAL); session.setPage(1); }
            case MONTH_BUTTON -> { session.setPeriod(TimePeriod.MONTH); session.setPage(1); }
            case WEEK_BUTTON -> { session.setPeriod(TimePeriod.WEEK); session.setPage(1); }
            case ARROW_LEFT -> session.setPage(session.getPage() - 1);
            case ARROW_RIGHT -> session.setPage(session.getPage() + 1);
        }

        FileConfiguration cfg = plugin.getConfigManager().get();
        if (cfg.getBoolean("leaderboard.click-sound-enabled", true)) {
            String soundKey = cfg.getString("leaderboard.buttons.click-sound", "ui.button.click");
            float volume = (float) cfg.getDouble("leaderboard.buttons.click-sound-volume", 0.6);
            float pitch = (float) cfg.getDouble("leaderboard.buttons.click-sound-pitch", 1.2);
            clicker.playSound(clicker.getLocation(), soundKey, volume, pitch);
        }

        render(clicker, type, board, instance, true);

        if (animator != null) {
            animator.press(instance, action);
            animator.update(clicker, board.getFrame(), board, instance, action);
        }
    }

    public void handleClick(Player clicker, int entityId) {
        ClickBinding binding = clickBindings.get(entityId);
        if (binding == null) {
            return;
        }
        HologramBoard board = boards.get(binding.getType());
        Map<LeaderboardType, HologramInstance> map = viewerInstances.get(clicker.getUniqueId());
        HologramInstance instance = map == null ? null : map.get(binding.getType());
        if (board == null || instance == null) {
            return;
        }

        PlayerSessionState session = sessionManager.getState(clicker.getUniqueId(), binding.getType());
        switch (binding.getAction()) {
            case GLOBAL_BUTTON -> { session.setPeriod(TimePeriod.GLOBAL); session.setPage(1); }
            case MONTH_BUTTON -> { session.setPeriod(TimePeriod.MONTH); session.setPage(1); }
            case WEEK_BUTTON -> { session.setPeriod(TimePeriod.WEEK); session.setPage(1); }
            case ARROW_LEFT -> session.setPage(session.getPage() - 1);
            case ARROW_RIGHT -> session.setPage(session.getPage() + 1);
        }

        FileConfiguration cfg = plugin.getConfigManager().get();
        if (cfg.getBoolean("leaderboard.click-sound-enabled", true)) {
            String soundKey = cfg.getString("leaderboard.buttons.click-sound", "ui.button.click");
            float volume = (float) cfg.getDouble("leaderboard.buttons.click-sound-volume", 0.6);
            float pitch = (float) cfg.getDouble("leaderboard.buttons.click-sound-pitch", 1.2);
            clicker.playSound(clicker.getLocation(), soundKey, volume, pitch);
        }

        render(clicker, binding.getType(), board, instance, true);

        if (animator != null) {
            animator.press(instance, binding.getAction());
            animator.update(clicker, board.getFrame(), board, instance, binding.getAction());
        }
    }

    // ------------------------------------------------------------
    // Despawnen
    // ------------------------------------------------------------

    public void removeBoard(LeaderboardType type) {
        for (Map<LeaderboardType, HologramInstance> map : viewerInstances.values()) {
            HologramInstance instance = map.remove(type);
            if (instance != null) {
                factory.destroy(instance.all());
            }
        }
        HologramBoard board = boards.remove(type);
        if (board != null) {
            for (Entity interaction : board.getInteractions()) {
                clickBindings.remove(interaction.getEntityId());
            }
            factory.destroy(board.getPackBackgrounds());
            factory.destroy(board.getFallbackEntities());
            factory.destroy(board.getEntities());
        }
        if (animator != null) {
            animator.clear();
        }
    }

    public void despawnAllForPlayer(Player player) {
        Map<LeaderboardType, HologramInstance> map = viewerInstances.remove(player.getUniqueId());
        if (map == null) {
            return;
        }
        for (HologramInstance instance : map.values()) {
            factory.destroy(instance.all());
        }
    }

    public void despawnType(LeaderboardType type) {
        removeBoard(type);
    }

    public void teleportPlayer(Player player, LeaderboardType type) {
        HologramDefinition def = positionStore.get(type);
        Location target = def == null ? null : def.teleportLocation();
        if (target != null) {
            player.teleport(target);
        }
    }
}
