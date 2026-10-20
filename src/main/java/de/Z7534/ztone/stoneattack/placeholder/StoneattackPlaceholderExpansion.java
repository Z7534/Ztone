package de.Z7534.ztone.stoneattack.placeholder;

import de.Z7534.ztone.stoneattack.Stoneattack;
import de.Z7534.ztone.stoneattack.modules.leaderboard.LeaderboardEntry;
import de.Z7534.ztone.stoneattack.modules.leaderboard.LeaderboardModule;
import de.Z7534.ztone.stoneattack.modules.leaderboard.LeaderboardType;
import de.Z7534.ztone.stoneattack.modules.leaderboard.TimePeriod;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.utils.ColorUtils;
import de.Z7534.ztone.stoneattack.modules.playerlimit.PlayerLimitModule;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * EINE PlaceholderAPI-Expansion (Identifier "stoneattack") fuer BEIDE
 * Module, die Platzhalter anbieten (PlaceholderAPI erlaubt pro
 * Identifier nur eine registrierte Expansion):
 *
 *  %stoneattack_playerlimit_max%
 *  %stoneattack_playerlimit_current%
 *  %stoneattack_playerlimit_free%
 *
 *  %stoneattack_leaderboard_<typ>_<zeitraum>_rank%          (eigene Platzierung, Betrachter noetig)
 *  %stoneattack_leaderboard_<typ>_<zeitraum>_value%         (eigener Wert, Betrachter noetig)
 *  %stoneattack_leaderboard_<typ>_<zeitraum>_top<N>_name%
 *  %stoneattack_leaderboard_<typ>_<zeitraum>_top<N>_value%
 *
 *  typ: playtime|kills|deaths|money, zeitraum: global|month|week
 */
public class StoneattackPlaceholderExpansion extends PlaceholderExpansion {

    private final Stoneattack plugin;

    public StoneattackPlaceholderExpansion(Stoneattack plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "stoneattack";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Z7534";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPlugin().getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(@Nullable OfflinePlayer player, @NotNull String params) {
        String[] parts = params.toLowerCase().split("_");
        if (parts.length == 0) {
            return null;
        }

        if (parts[0].equals("playerlimit")) {
            return handlePlayerLimit(parts);
        }
        if (parts[0].equals("leaderboard")) {
            return handleLeaderboard(player, parts);
        }
        return null;
    }

    private String handlePlayerLimit(String[] parts) {
        PlayerLimitModule module = plugin.getPlayerLimitModule();
        if (module == null || parts.length < 2) {
            return "";
        }
        int current = Bukkit.getOnlinePlayers().size();
        int max = module.isEnabled() ? module.getLimit() : Bukkit.getMaxPlayers();

        return switch (parts[1]) {
            case "max" -> String.valueOf(max);
            case "current" -> String.valueOf(current);
            case "free" -> String.valueOf(Math.max(0, max - current));
            default -> null;
        };
    }

    private String handleLeaderboard(OfflinePlayer viewer, String[] parts) {
        LeaderboardModule module = plugin.getLeaderboardModule();
        if (module == null || parts.length < 4) {
            return "";
        }
        LeaderboardType type = LeaderboardType.fromKey(parts[1]);
        TimePeriod period = switch (parts[2]) {
            case "global" -> TimePeriod.GLOBAL;
            case "month" -> TimePeriod.MONTH;
            case "week" -> TimePeriod.WEEK;
            default -> null;
        };
        if (type == null || period == null) {
            return "";
        }

        if (parts[3].startsWith("top") && parts.length >= 5) {
            int rank;
            try {
                rank = Integer.parseInt(parts[3].substring(3));
            } catch (NumberFormatException e) {
                return "";
            }
            LeaderboardEntry entry = module.getCache().getEntryForRank(type, period, rank);
            if (entry == null) {
                return plugin.getConfigManager().get().getString("leaderboard.entries.empty-slot-text", "---");
            }
            return switch (parts[4]) {
                case "value" -> entry.formattedValue();
                case "rawvalue", "raw_value" -> String.valueOf(entry.rawValue());
                case "uuid" -> entry.uuid().toString();
                case "clan", "clanname", "clan_name" -> {
                    Clan clan = plugin.getPlugin().getClanManager().getClanByPlayer(entry.uuid());
                    yield clan != null ? ColorUtils.translateColors(clan.getColoredName()) : "";
                }
                case "clan_raw" -> {
                    Clan clan = plugin.getPlugin().getClanManager().getClanByPlayer(entry.uuid());
                    yield clan != null ? clan.getName() : "";
                }
                case "clantag", "clan_tag", "clansuffix", "clan_suffix", "suffix" -> {
                    Clan clan = plugin.getPlugin().getClanManager().getClanByPlayer(entry.uuid());
                    yield (clan != null && clan.getSuffix() != null) ? ColorUtils.translateColors(clan.getColoredSuffix()) : "";
                }
                case "clansuffix_formatted", "clan_suffix_formatted", "suffix_formatted" -> {
                    Clan clan = plugin.getPlugin().getClanManager().getClanByPlayer(entry.uuid());
                    yield (clan != null && clan.getSuffix() != null && !clan.getSuffix().isEmpty())
                            ? ColorUtils.translateColors("&8[&r" + clan.getColoredSuffix() + "&8] ") : "";
                }
                default -> entry.name();
            };
        }

        if (viewer == null) {
            return "";
        }
        int rank = module.getCache().getRank(type, period, viewer.getUniqueId());
        if (parts[3].equals("rank")) {
            return rank > 0 ? String.valueOf(rank) : "-";
        }
        if (parts[3].equals("value")) {
            LeaderboardEntry entry = module.getCache().getEntry(type, period, viewer.getUniqueId());
            return entry != null ? entry.formattedValue() : "-";
        }
        return null;
    }
}
