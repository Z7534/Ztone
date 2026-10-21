package de.Z7534.ztone.stoneattack.modules.leaderboard.hologram;

import de.Z7534.ztone.stoneattack.Stoneattack;
import de.Z7534.ztone.stoneattack.modules.leaderboard.LeaderboardCache;
import de.Z7534.ztone.stoneattack.modules.leaderboard.LeaderboardEntry;
import de.Z7534.ztone.stoneattack.modules.leaderboard.LeaderboardType;
import de.Z7534.ztone.stoneattack.modules.leaderboard.PlayerSessionManager;
import de.Z7534.ztone.stoneattack.modules.leaderboard.PlayerSessionState;
import de.Z7534.ztone.stoneattack.modules.leaderboard.TimePeriod;
import de.Z7534.ztone.stoneattack.util.ColorUtil;
import de.Z7534.ztone.stoneattack.util.SmallCaps;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet alle Leaderboard-Hologramme.
 *
 * Aufbau (siehe {@link HologramLayout}):
 * <ul>
 *   <li>{@link HologramBoard} - geteilter Teil (Klickflaechen), 1x pro Leaderboard</li>
 *   <li>{@link HologramInstance} - Teil pro Spieler (Liste, Titel, Seite, "Du"-Zeile)</li>
 * </ul>
 * Ein Board wird nur aufgebaut, solange ein Spieler in Sichtweite ist.
 *
 * <h2>Hoehe pro Board</h2>
 * Jedes Leaderboard hat seine eigene Hoehe (siehe {@link HologramDefinition#getHeightBlocks()},
 * aenderbar per {@code /leaderboard height}). Das zugehoerige {@link HologramLayout} haengt
 * deshalb am Board und nicht mehr global am Manager.
 *
 * <h2>Aktualisierung</h2>
 * Der Render-Task laeuft haeufig (Standard alle 10 Ticks), baut den Text aber nur dann neu
 * auf, wenn sich wirklich etwas geaendert hat: Datenstand des Caches
 * ({@link LeaderboardCache#getVersion()}), Seite oder Zeitraum. Dadurch erscheinen neue
 * Werte zuverlaessig kurz nach ihrer Aenderung, ohne dass jede Sekunde unnoetig
 * Komponenten gebaut und Pakete verschickt werden.
 */
public class HologramManager {

    // Abstand der Ebenen von der Wand in Bloecken. Jede Ebene liegt minimal vor der vorherigen,
    // damit sich ueberlappende Flaechen nicht ("Z-Fighting") flackernd durchdruecken.
    private static final double LAYER_BORDER = 0.010;
    private static final double LAYER_BODY = 0.015;
    private static final double LAYER_PLATE = 0.020;
    private static final double LAYER_HIGHLIGHT = 0.025;
    // Die Hover-Ebenen liegen VOR der Aktiv-Markierung und HINTER dem Text.
    private static final double LAYER_CONTENT = 0.0355;

    /** So viel groesser als der Button ist der "Blick-Treffer"-Bereich, solange der Button schon gehovert wird. */
    private static final double HOVER_STICKY_MARGIN = 0.03;

    private static final TimePeriod[] MODE_PERIODS = {TimePeriod.GLOBAL, TimePeriod.MONTH, TimePeriod.WEEK};
    private static final ClickBinding.Action[] MODE_ACTIONS = {ClickBinding.Action.GLOBAL_BUTTON,
            ClickBinding.Action.MONTH_BUTTON, ClickBinding.Action.WEEK_BUTTON};

    /**
     * Text-Display-Hintergruende mit sehr kleinem Alpha werden von Minecraft nicht
     * mehr sauber gezeichnet - deshalb nie durchsichtiger als das hier.
     */
    private static final int MIN_ALPHA = 26;

    private final Stoneattack plugin;
    private final HologramPositionStore positionStore;
    private final LeaderboardCache cache;
    private final PlayerSessionManager sessionManager;
    private final DisplayFactory factory;

    private final Map<LeaderboardType, HologramBoard> boards = new EnumMap<>(LeaderboardType.class);
    private final Map<UUID, Map<LeaderboardType, HologramInstance>> viewerInstances = new ConcurrentHashMap<>();
    private final Map<Integer, ClickBinding> clickBindings = new ConcurrentHashMap<>();

    private BukkitTask renderTask;
    private BukkitTask hoverTask;
    private HoverAnimator animator;

    public HologramManager(Stoneattack plugin, HologramPositionStore positionStore, LeaderboardCache cache,
                           PlayerSessionManager sessionManager) {
        this.plugin = plugin;
        this.positionStore = positionStore;
        this.cache = cache;
        this.sessionManager = sessionManager;
        this.factory = new DisplayFactory(plugin);
    }

    public void start() {
        FileConfiguration cfg = plugin.getConfigManager().get();
        long interval = cfg.getLong("leaderboard.render.update-interval-ticks", 10);
        renderTask = Bukkit.getScheduler().runTaskTimer(plugin.getPlugin(), this::tick, 1L, Math.max(1L, interval));
        // HoverAnimator und hoverTask komplett deaktiviert gegen Laggs (keine zusaetzlichen Entities!)
        this.animator = null;
        this.hoverTask = null;
    }

    public void stop() {
        if (renderTask != null) {
            renderTask.cancel();
            renderTask = null;
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
        factory.removeAll();
    }

    public void reload() {
        stop();
        positionStore.load();
        start();
    }

    /** Wie viele Eintraege bei einer bestimmten Board-Hoehe angezeigt werden koennen. */
    public int entriesForHeight(double heightBlocks) {
        return HologramLayout.entriesForHeight(plugin.getConfigManager().get(), heightBlocks);
    }

    /**
     * Die tatsaechliche Hoehe in Bloecken. Weicht nur dann von der gewuenschten ab,
     * wenn diese zu klein war, um auch nur eine Zeile unterzubringen.
     */
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
            // Entities koennen verschwinden (Chunk entladen, /kill, Clear-Plugins ...) -> dann komplett neu aufbauen.
            // Gleiches gilt, wenn per Command eine andere Hoehe gesetzt wurde.
            if (board != null && (!board.isValid()
                    || Math.abs(board.getLayout().requestedHeight() - def.getHeightBlocks()) > 0.0001)) {
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

            // Spieler, die sich entfernt haben (oder die Welt gewechselt haben), verlieren ihren Teil
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
    // Geteilter Teil: Buttons / Klickflaechen (EXAKT 1 Interaction pro Button)
    // ------------------------------------------------------------

    private HologramBoard buildBoard(LeaderboardType type, BoardFrame f, double heightBlocks) {
        FileConfiguration cfg = plugin.getConfigManager().get();
        HologramLayout l = new HologramLayout(cfg, heightBlocks);
        HologramBoard board = new HologramBoard(type, l);

        // Pro Button genau 1 Interaction-Entity (keine Hintergrund- oder Rahmen-Slices mittendrin im Board):
        // 1) Blaetter-Buttons: Pfeil links & Pfeil rechts
        double leftArrowU = l.left;
        double rightArrowU = l.left + l.width - HologramLayout.ARROW_WIDTH;
        interaction(board, f, type, ClickBinding.Action.ARROW_LEFT,
                leftArrowU, l.pagerBottom, HologramLayout.ARROW_WIDTH, HologramLayout.PAGER_HEIGHT);
        interaction(board, f, type, ClickBinding.Action.ARROW_RIGHT,
                rightArrowU, l.pagerBottom, HologramLayout.ARROW_WIDTH, HologramLayout.PAGER_HEIGHT);

        // 2) Die 3 Modus-Buttons (Global, Monat, Woche): genau 1 Interaction-Entity pro Button
        double modeWidth = l.modeWidth();
        for (int i = 0; i < MODE_PERIODS.length; i++) {
            double u0 = l.modeLeft(i);
            interaction(board, f, type, MODE_ACTIONS[i], u0, l.modeBottom, modeWidth, HologramLayout.MODE_HEIGHT);
        }
        return board;
    }

    /**
     * Unsichtbare Klickflaeche ueber einem Button (Position = linke untere Ecke des Buttons),
     * dazu die Flaechenangabe fuer die Hover-Erkennung.
     */
    private void interaction(HologramBoard board, BoardFrame f, LeaderboardType type, ClickBinding.Action action,
                             double u0, double v0, double width, double height) {
        // Die Hitbox ist in der Tiefe genauso breit wie in der Breite -> nach hinten in die Wand verschieben,
        // sodass sie nur 0,08 Bloecke vor die Wand ragt und keine Nachbarn/Spieler behindert.
        double depth = -width / 2.0 + 0.08;
        Interaction interaction = factory.spawnInteraction(f.at(u0 + width / 2.0, v0, depth), (float) width, (float) height);
        board.getEntities().add(interaction);
        board.getInteractions().add(interaction);
        clickBindings.put(interaction.getEntityId(), new ClickBinding(type, action));

        board.getButtonAreas().put(action, new double[]{u0, v0, width, height});
    }

    // ------------------------------------------------------------
    // Teil pro Spieler (TextDisplays ohne Hintergrundboxen)
    // ------------------------------------------------------------

    private HologramInstance spawnViewerPart(Player viewer, BoardFrame f, HologramLayout l) {
        FileConfiguration cfg = plugin.getConfigManager().get();
        HologramInstance instance = new HologramInstance();
        float s = l.textScale;

        instance.title = factory.spawnText(
                f.at(0, l.textPivot(l.titleCenter, s), LAYER_CONTENT), viewer, false, s);
        factory.setBackground(instance.title, 0);

        // Die komplette Liste ist EIN mehrzeiliges Display; es haengt an der UNTERSTEN Zeile.
        instance.list = factory.spawnText(
                f.at(l.listU, l.textPivot(l.rowCenter(l.perPage - 1), s), LAYER_CONTENT), viewer, false, s);
        factory.setBackground(instance.list, 0);

        instance.self = factory.spawnText(
                f.at(0, l.textPivot(l.selfCenter, s), LAYER_CONTENT), viewer, false, s);
        factory.setBackground(instance.self, 0);

        if (cfg.getBoolean("leaderboard.show-page-indicator", true)) {
            instance.pager = factory.spawnText(
                    f.at(0, l.textPivot(l.pagerCenter(), l.pagerTextScale), LAYER_CONTENT),
                    viewer, false, l.pagerTextScale);
            factory.setBackground(instance.pager, 0);
        }
        double leftArrowCenter = l.left + HologramLayout.ARROW_WIDTH / 2.0;
        double rightArrowCenter = l.left + l.width - HologramLayout.ARROW_WIDTH / 2.0;
        double arrowPivot = l.textPivot(l.pagerCenter(), l.arrowTextScale);
        instance.arrowLeft = factory.spawnText(f.at(leftArrowCenter, arrowPivot, LAYER_CONTENT), viewer, false, l.arrowTextScale);
        factory.setBackground(instance.arrowLeft, 0);

        instance.arrowRight = factory.spawnText(f.at(rightArrowCenter, arrowPivot, LAYER_CONTENT), viewer, false, l.arrowTextScale);
        factory.setBackground(instance.arrowRight, 0);

        instance.labels.put(ClickBinding.Action.ARROW_LEFT,
                new HoverLabel(instance.arrowLeft, l.arrowTextScale, l.centerOffset(), -1.0));
        instance.labels.put(ClickBinding.Action.ARROW_RIGHT,
                new HoverLabel(instance.arrowRight, l.arrowTextScale, l.centerOffset(), 1.0));

        // Beschriftung der 3 Modus-Buttons (Icon + Name)
        double modeWidth = l.modeWidth();
        double modePivot = l.textPivot(l.modeCenter(), l.buttonTextScale);
        for (int i = 0; i < MODE_PERIODS.length; i++) {
            TimePeriod period = MODE_PERIODS[i];
            String icon = cfg.getString("leaderboard.style.icons." + period.key(), "");
            String name = cfg.getString("leaderboard.period-names." + period.key(), period.key());
            String text = icon.isEmpty() ? name : icon + " " + name;
            TextDisplay label = factory.spawnText(f.at(l.modeLeft(i) + modeWidth / 2.0, modePivot, LAYER_CONTENT),
                    viewer, false, l.buttonTextScale);
            factory.setBackground(label, 0);
            factory.setText(label, styled(ColorUtil.parse("<white><b>" + text + "</b></white>")));
            instance.modeLabels[i] = label;
            instance.labels.put(MODE_ACTIONS[i], new HoverLabel(label, l.buttonTextScale, l.centerOffset(), 0.0));
        }
        return instance;
    }

    // ------------------------------------------------------------
    // Inhalt aktualisieren
    // ------------------------------------------------------------

    /**
     * @param force true = auf jeden Fall neu zeichnen (z. B. direkt nach einem Klick),
     *              false = nur, wenn sich Datenstand, Seite oder Zeitraum geaendert haben
     */
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

        // Nichts Neues? Dann sparen wir uns den kompletten Textaufbau.
        String key = cache.getVersion() + "|" + period.key() + "|" + page;
        if (!force && key.equals(instance.renderKey)) {
            applyHighlight(viewer, board, instance, period);
            return;
        }
        instance.renderKey = key;

        // Titel
        String periodName = cfg.getString("leaderboard.period-names." + period.key(), period.key());
        String titleTemplate = cfg.getString("leaderboard.titles." + type.key(), type.key());
        factory.setText(instance.title, styled(ColorUtil.parse(titleTemplate, Map.of("period", periodName))));

        // Eintraege der aktuellen Seite - alle Zeilen in EINEM Text-Display mit schoenen Badges
        List<LeaderboardEntry> pageEntries = cache.getPage(type, period, page, perPage);
        String lineTemplate = cfg.getString("leaderboard.entries.line-format", "%rank_prefix% <white>%player%</white>  <gray>»</gray>  <yellow>%value%</yellow>");
        List<Component> rows = new ArrayList<>(perPage);
        for (int i = 0; i < perPage; i++) {
            int rank = (page - 1) * perPage + i + 1;
            if (i < pageEntries.size() && rank <= maxRank) {
                LeaderboardEntry entry = pageEntries.get(i);
                String prefix = cfg.getString("leaderboard.entries.top-rank-prefix." + rank, null);
                if (prefix == null || prefix.equals("<gold>●</gold>")) {
                    if (rank == 1) prefix = "<gradient:#FFE259:#FFA751><b>➊</b></gradient>";
                }
                if (prefix == null || prefix.equals("<gray>●</gray>")) {
                    if (rank == 2) prefix = "<gradient:#E0EAFC:#CFDEF3><b>➋</b></gradient>";
                }
                if (prefix == null || prefix.equals("<color:#CD7F32>●</color>") || prefix.equals("<#CD7F32>●</#CD7F32>")) {
                    if (rank == 3) prefix = "<gradient:#F3904F:#BA4A00><b>➌</b></gradient>";
                }
                if (prefix == null) {
                    prefix = cfg.getString("leaderboard.entries.default-rank-prefix", "<dark_gray>#%rank%</dark_gray>")
                            .replace("%rank%", String.valueOf(rank));
                }

                String rowTemplate = lineTemplate;
                if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                    try {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(entry.uuid());
                        rowTemplate = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(op, rowTemplate);
                    } catch (Throwable ignored) {}
                }

                try {
                    rows.add(styled(ColorUtil.parse(rowTemplate, Map.of(
                            "rank_prefix", prefix,
                            "player", entry.name(),
                            "value", entry.formattedValue()
                    ))));
                } catch (Throwable t) {
                    rows.add(Component.text(prefix + " " + entry.name() + " " + entry.formattedValue()));
                }
            } else {
                rows.add(styled(ColorUtil.parse("<dark_gray>―  ―  ―</dark_gray>")));
            }
        }
        factory.setText(instance.list, Component.join(JoinConfiguration.newlines(), rows));

        // "Du"-Zeile
        LeaderboardEntry own = cache.getEntry(type, period, viewer.getUniqueId());
        int ownRank = cache.getRank(type, period, viewer.getUniqueId());
        Component selfLine;
        if (own != null && ownRank > 0) {
            String selfTemplate = cfg.getString("leaderboard.self-line.format", "<aqua>Du</aqua>  <gray>#%rank%</gray>  <gray>»</gray>  <yellow>%value%</yellow>");
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                try {
                    selfTemplate = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(viewer, selfTemplate);
                } catch (Throwable ignored) {}
            }
            try {
                selfLine = ColorUtil.parse(selfTemplate,
                        Map.of("rank", String.valueOf(ownRank), "value", own.formattedValue()));
            } catch (Throwable t) {
                selfLine = Component.text("Du #" + ownRank + " " + own.formattedValue());
            }
        } else {
            String fallback = cfg.getString("leaderboard.self-line.fallback-text", "<gray>Du – Noch keine Daten</gray>");
            selfLine = ColorUtil.parse(fallback);
        }
        factory.setText(instance.self, styled(selfLine));

        // Seitenanzeige
        if (instance.pager != null) {
            factory.setText(instance.pager, styled(ColorUtil.parse(
                    cfg.getString("leaderboard.page-indicator-format", "<gray>Seite %page%/%max_page%</gray>"),
                    Map.of("page", String.valueOf(page), "max_page", String.valueOf(maxPage)))));
        }

        // Pfeile: auf der ersten/letzten Seite abgedunkelt, ohne Hintergrund
        factory.setText(instance.arrowLeft, arrowLabel(cfg, "arrow-left", "\\u276E", page <= 1));
        factory.setBackground(instance.arrowLeft, 0);

        factory.setText(instance.arrowRight, arrowLabel(cfg, "arrow-right", "\\u276F", page >= maxPage));
        factory.setBackground(instance.arrowRight, 0);

        applyHighlight(viewer, board, instance, period);
    }

    private Component arrowLabel(FileConfiguration cfg, String key, String fallback, boolean inactive) {
        String icon = cfg.getString("leaderboard.style.icons." + key, fallback);
        return inactive
                ? ColorUtil.parse("<dark_gray>" + icon + "</dark_gray>")
                : ColorUtil.parse("<gradient:#FFFFFF:#B0B0B0><b>" + icon + "</b></gradient>");
    }

    /** Hebt den aktiven Zeitraum hervor mit Farbverlauf und Auswahl-Klammern (ohne Hintergrundbox). */
    private void applyHighlight(Player viewer, HologramBoard board, HologramInstance instance, TimePeriod period) {
        FileConfiguration cfg = plugin.getConfigManager().get();

        for (int i = 0; i < MODE_PERIODS.length; i++) {
            TimePeriod p = MODE_PERIODS[i];
            if (instance.modeLabels[i] != null) {
                String icon = cfg.getString("leaderboard.style.icons." + p.key(), "");
                String name = cfg.getString("leaderboard.period-names." + p.key(), p.key());
                String text = icon.isEmpty() ? name : icon + " " + name;
                boolean active = (p == period);
                factory.setBackground(instance.modeLabels[i], 0);
                if (active) {
                    String formatted = "<gradient:#55FF55:#2E8B57><b>[ " + text + " ]</b></gradient>";
                    factory.setText(instance.modeLabels[i], styled(ColorUtil.parse(formatted)));
                } else {
                    String formatted = "<gray><b>" + text + "</b></gray>";
                    factory.setText(instance.modeLabels[i], styled(ColorUtil.parse(formatted)));
                }
            }
        }
        instance.shownHighlight = period;
    }

    /** Small Caps auf den ganzen Text (inkl. Spielernamen), sofern in der Config aktiv. */
    private Component styled(Component component) {
        FileConfiguration cfg = plugin.getConfigManager().get();
        if (!cfg.getBoolean("leaderboard.small-caps.enabled", true)) {
            return component;
        }
        return SmallCaps.apply(component, cfg.getBoolean("leaderboard.small-caps.convert-uppercase", true));
    }

    /** Farbe aus der Config inkl. der global eingestellten Durchsichtigkeit. */
    private int color(FileConfiguration cfg, String path, String fallback) {
        return (alpha(cfg) << 24) | parseRgb(cfg.getString(path, fallback), fallback);
    }

    /**
     * Deckkraft aller Hintergrundflaechen aus {@code leaderboard.style.transparency}
     * (0 = voll deckend, 1 = unsichtbar).
     */
    private int alpha(FileConfiguration cfg) {
        double transparency = Math.max(0.0, Math.min(1.0, cfg.getDouble("leaderboard.style.transparency", 0.3)));
        int value = (int) Math.round((1.0 - transparency) * 255.0);
        return Math.max(MIN_ALPHA, Math.min(255, value));
    }

    private int parseRgb(String hex, String fallback) {
        try {
            return Integer.parseInt(hex.replace("#", "").trim(), 16) & 0xFFFFFF;
        } catch (Exception e) {
            return Integer.parseInt(fallback.replace("#", ""), 16) & 0xFFFFFF;
        }
    }

    // ------------------------------------------------------------
    // Klicks
    // ------------------------------------------------------------

    public boolean isTrackedEntity(int entityId) {
        return clickBindings.containsKey(entityId);
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

        // Klick = sofortige Rueckmeldung, deshalb erzwungen neu zeichnen.
        render(clicker, binding.getType(), board, instance, true);

        // Cursor und Beschriftung druecken sich kurz ein und federn zurueck.
        if (animator != null) {
            animator.press(instance, binding.getAction());
        }
    }

    // ------------------------------------------------------------
    // Despawnen
    // ------------------------------------------------------------

    /** Entfernt das komplette Board samt aller Spieler-Teile. */
    private void removeBoard(LeaderboardType type) {
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
            factory.destroy(board.getEntities());
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
