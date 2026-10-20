package de.Z7534.ztone.claims;

import de.Z7534.ztone.claims.config.ConfigManager;
import de.Z7534.ztone.claims.manager.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClaimsContext {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final ClaimManager claimManager;
    private final SelectionManager selectionManager;
    private final ChatInputManager chatInputManager;
    private final PlayerLookupService playerLookupService;
    private final SafeSpotFinder safeSpotFinder;
    private final ActivityManager activityManager;
    private final ParticleManager particleManager;
    private final BanEnforcer banEnforcer;
    private final BypassManager bypassManager;
    private final ClaimMapManager claimMapManager;
    private final PvPClaimBypassManager pvpClaimBypassManager;

    public ClaimsContext(JavaPlugin plugin, ConfigManager config, ClaimManager claimManager,
                          SelectionManager selectionManager, ChatInputManager chatInputManager,
                          PlayerLookupService playerLookupService, SafeSpotFinder safeSpotFinder,
                          ActivityManager activityManager, ParticleManager particleManager,
                          BanEnforcer banEnforcer, BypassManager bypassManager,
                          ClaimMapManager claimMapManager, PvPClaimBypassManager pvpClaimBypassManager) {
        this.bypassManager = bypassManager;
        this.plugin = plugin;
        this.config = config;
        this.claimManager = claimManager;
        this.selectionManager = selectionManager;
        this.chatInputManager = chatInputManager;
        this.playerLookupService = playerLookupService;
        this.safeSpotFinder = safeSpotFinder;
        this.activityManager = activityManager;
        this.particleManager = particleManager;
        this.banEnforcer = banEnforcer;
        this.claimMapManager = claimMapManager;
        this.pvpClaimBypassManager = pvpClaimBypassManager;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public ConfigManager config() {
        return config;
    }

    public ClaimManager claims() {
        return claimManager;
    }

    public SelectionManager selection() {
        return selectionManager;
    }

    public ChatInputManager chatInput() {
        return chatInputManager;
    }

    public PlayerLookupService lookup() {
        return playerLookupService;
    }

    public SafeSpotFinder safeSpot() {
        return safeSpotFinder;
    }

    public ActivityManager activity() {
        return activityManager;
    }

    public ParticleManager particles() {
        return particleManager;
    }

    public BanEnforcer banEnforcer() {
        return banEnforcer;
    }

    public BypassManager bypass() {
        return bypassManager;
    }

    public ClaimMapManager claimMap() {
        return claimMapManager;
    }

    public PvPClaimBypassManager pvpClaimBypass() {
        return pvpClaimBypassManager;
    }
}
