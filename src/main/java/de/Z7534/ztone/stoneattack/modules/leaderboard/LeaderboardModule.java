package de.Z7534.ztone.stoneattack.modules.leaderboard;

import de.Z7534.ztone.stoneattack.Stoneattack;
import de.Z7534.ztone.stoneattack.data.Database;
import de.Z7534.ztone.stoneattack.data.dao.ExclusionDao;
import de.Z7534.ztone.stoneattack.data.dao.SnapshotDao;
import de.Z7534.ztone.stoneattack.modules.leaderboard.hologram.HologramClickListener;
import de.Z7534.ztone.stoneattack.modules.leaderboard.hologram.HologramManager;
import de.Z7534.ztone.stoneattack.modules.leaderboard.hologram.HologramPositionStore;
import org.bukkit.event.HandlerList;

public class LeaderboardModule {

    private final Stoneattack plugin;

    private Database database;
    private StatisticsService statisticsService;
    private ExclusionService exclusionService;
    private PeriodResetManager periodResetManager;
    private LeaderboardCache cache;
    private PlayerSessionManager sessionManager;
    private HologramPositionStore positionStore;
    private HologramManager hologramManager;
    private HologramClickListener clickListener;

    public LeaderboardModule(Stoneattack plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        database = new Database(plugin);
        database.connect();

        SnapshotDao snapshotDao = new SnapshotDao(database);
        ExclusionDao exclusionDao = new ExclusionDao(database);

        statisticsService = new StatisticsService(plugin);
        exclusionService = new ExclusionService(exclusionDao);
        exclusionService.loadAll();

        periodResetManager = new PeriodResetManager(plugin, snapshotDao);
        cache = new LeaderboardCache(plugin, statisticsService, periodResetManager, exclusionService);
        sessionManager = new PlayerSessionManager();

        positionStore = new HologramPositionStore(plugin);
        positionStore.load();

        hologramManager = new HologramManager(plugin, positionStore, cache, sessionManager);

        cache.start();
        hologramManager.start();

        clickListener = new HologramClickListener(hologramManager);
        plugin.getServer().getPluginManager().registerEvents(clickListener, plugin.getPlugin());
    }

    public void disable() {
        if (clickListener != null) {
            HandlerList.unregisterAll(clickListener);
        }
        if (hologramManager != null) {
            hologramManager.stop();
        }
        if (cache != null) {
            cache.stop();
        }
        if (database != null) {
            database.close();
        }
    }

    public void reload() {
        positionStore.load();
        exclusionService.loadAll();
        statisticsService.resetWarnings();
        cache.forceRefresh();
        hologramManager.reload();
    }

    public LeaderboardCache getCache() {
        return cache;
    }

    public ExclusionService getExclusionService() {
        return exclusionService;
    }

    public PeriodResetManager getPeriodResetManager() {
        return periodResetManager;
    }

    public PlayerSessionManager getSessionManager() {
        return sessionManager;
    }

    public HologramPositionStore getPositionStore() {
        return positionStore;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }
}
