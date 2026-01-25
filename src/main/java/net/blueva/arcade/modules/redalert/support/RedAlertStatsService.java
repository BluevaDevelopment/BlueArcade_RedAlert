package net.blueva.arcade.modules.redalert.support;

import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.stats.StatDefinition;
import net.blueva.arcade.api.stats.StatScope;
import net.blueva.arcade.api.stats.StatsAPI;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RedAlertStatsService {

    private final StatsAPI statsAPI;
    private final ModuleInfo moduleInfo;
    private final Map<Integer, UUID> arenaWinners = new ConcurrentHashMap<>();

    public RedAlertStatsService(StatsAPI statsAPI, ModuleInfo moduleInfo) {
        this.statsAPI = statsAPI;
        this.moduleInfo = moduleInfo;
    }

    public void registerStats() {
        if (statsAPI == null) {
            return;
        }

        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("wins", "Wins", "Red Alert wins", StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("games_played", "Games Played", "Red Alert games played", StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("tiles_melted", "Tiles melted", "Blocks heated or removed", StatScope.MODULE));
    }

    public void resetArena(int arenaId) {
        arenaWinners.remove(arenaId);
    }

    public void recordWin(Player player, int arenaId) {
        if (statsAPI == null || player == null) {
            return;
        }

        if (!arenaWinners.containsKey(arenaId)) {
            arenaWinners.put(arenaId, player.getUniqueId());
            statsAPI.addModuleStat(player, moduleInfo.getId(), "wins", 1);
            statsAPI.addGlobalStat(player, "wins", 1);
        }
    }

    public void recordTileMelt(Player player) {
        if (statsAPI == null || player == null) {
            return;
        }
        statsAPI.addModuleStat(player, moduleInfo.getId(), "tiles_melted", 1);
    }

    public void recordGamesPlayed(Collection<Player> players) {
        if (statsAPI == null || players == null) {
            return;
        }

        for (Player player : players) {
            statsAPI.addModuleStat(player, moduleInfo.getId(), "games_played", 1);
        }
    }
}
