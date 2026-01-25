package net.blueva.arcade.modules.redalert.game;

import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.modules.redalert.state.RedAlertArenaState;
import net.blueva.arcade.modules.redalert.support.RedAlertBlockService;
import net.blueva.arcade.modules.redalert.support.RedAlertFallingBlockService;
import net.blueva.arcade.modules.redalert.support.RedAlertLoadoutService;
import net.blueva.arcade.modules.redalert.support.RedAlertMessagingService;
import net.blueva.arcade.modules.redalert.support.RedAlertStatsService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RedAlertGameManager {

    private final ModuleInfo moduleInfo;
    private final ModuleConfigAPI moduleConfig;
    private final CoreConfigAPI coreConfig;
    private final RedAlertStatsService statsService;
    private final RedAlertLoadoutService loadoutService;
    private final RedAlertMessagingService messagingService;
    private final RedAlertFallingBlockService fallingBlockService;
    private final RedAlertBlockService blockService;

    private final Map<Integer, RedAlertArenaState> arenaStates = new ConcurrentHashMap<>();
    private final Map<Player, Integer> playerArenas = new ConcurrentHashMap<>();

    public RedAlertGameManager(ModuleInfo moduleInfo,
                               ModuleConfigAPI moduleConfig,
                               CoreConfigAPI coreConfig,
                               RedAlertStatsService statsService,
                               RedAlertLoadoutService loadoutService,
                               RedAlertMessagingService messagingService,
                               RedAlertFallingBlockService fallingBlockService,
                               RedAlertBlockService blockService) {
        this.moduleInfo = moduleInfo;
        this.moduleConfig = moduleConfig;
        this.coreConfig = coreConfig;
        this.statsService = statsService;
        this.loadoutService = loadoutService;
        this.messagingService = messagingService;
        this.fallingBlockService = fallingBlockService;
        this.blockService = blockService;
    }

    public void handleStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();

        context.getSchedulerAPI().cancelArenaTasks(arenaId);
        fallingBlockService.cleanupArena(arenaId);

        RedAlertMode mode = resolveMode(context);
        RedAlertArenaState state = new RedAlertArenaState(context, mode, null);
        blockService.cacheFloorBounds(context, state);
        arenaStates.put(arenaId, state);
        statsService.resetArena(arenaId);

        for (Player player : context.getPlayers()) {
            playerArenas.put(player, arenaId);
        }

        messagingService.sendDescription(context, mode);
    }

    public void handleCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, int secondsLeft) {
        messagingService.sendCountdownTick(context, secondsLeft);
    }

    public void handleCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        messagingService.sendCountdownFinish(context);
    }

    public boolean freezePlayersOnCountdown() {
        return false;
    }

    public void handleGameStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();
        RedAlertArenaState state = arenaStates.get(arenaId);
        if (state == null) {
            state = new RedAlertArenaState(context, RedAlertMode.CHAOS, null);
            blockService.cacheFloorBounds(context, state);
            arenaStates.put(arenaId, state);
        }

        blockService.resetFloor(context, state);
        messagingService.sendStartTitle(context);
        startGameTimer(context, state);
        blockService.startModeTasks(context, state);

        for (Player player : context.getPlayers()) {
            player.setGameMode(GameMode.SURVIVAL);
            loadoutService.giveStartingItems(player);
            loadoutService.applyStartingEffects(player);
            context.getScoreboardAPI().showScoreboard(player, messagingService.getScoreboardPath());
        }
    }

    private void startGameTimer(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, RedAlertArenaState state) {
        int arenaId = context.getArenaId();

        Integer gameTime = context.getDataAccess().getGameData("basic.time", Integer.class);
        if (gameTime == null || gameTime == 0) {
            gameTime = 180;
        }

        final int[] timeLeft = {gameTime};
        String taskId = "arena_" + arenaId + "_red_alert_timer";

        context.getSchedulerAPI().runTimer(taskId, () -> {
            if (state.isEnded()) {
                context.getSchedulerAPI().cancelTask(taskId);
                return;
            }

            timeLeft[0]--;

            List<Player> alivePlayers = context.getAlivePlayers();
            List<Player> allPlayers = context.getPlayers();

            if (alivePlayers.size() <= 1 || timeLeft[0] <= 0) {
                endGameOnce(context, state);
                return;
            }

            String actionBarTemplate = coreConfig.getLanguage("action_bar.in_game.global");
            for (Player player : allPlayers) {
                if (!player.isOnline()) {
                    continue;
                }

                Map<String, String> customPlaceholders = getCustomPlaceholders(player);
                customPlaceholders.put("time", String.valueOf(timeLeft[0]));
                customPlaceholders.put("alive", String.valueOf(alivePlayers.size()));
                customPlaceholders.put("spectators", String.valueOf(context.getSpectators().size()));

                if (actionBarTemplate != null) {
                    String actionBarMessage = actionBarTemplate
                            .replace("{time}", String.valueOf(timeLeft[0]))
                            .replace("{round}", String.valueOf(context.getCurrentRound()))
                            .replace("{round_max}", String.valueOf(context.getMaxRounds()));
                    context.getMessagesAPI().sendActionBar(player, actionBarMessage);
                }

                context.getScoreboardAPI().update(player, messagingService.getScoreboardPath(), customPlaceholders);
            }
        }, 0L, 20L);
    }

    private void endGameOnce(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, RedAlertArenaState state) {
        if (state.markEnded()) {
            return;
        }

        context.getSchedulerAPI().cancelArenaTasks(context.getArenaId());

        List<Player> alivePlayers = new ArrayList<>(context.getAlivePlayers());
        if (alivePlayers.size() == 1) {
            Player winner = alivePlayers.get(0);
            context.setWinner(winner);
            handleWin(winner);
        }

        context.endGame();
    }

    public void handleEnd(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();

        context.getSchedulerAPI().cancelArenaTasks(arenaId);

        RedAlertArenaState state = arenaStates.remove(arenaId);
        if (state != null) {
            blockService.resetFloor(context, state);
        }
        fallingBlockService.cleanupArena(arenaId);
        statsService.resetArena(arenaId);

        for (Player player : context.getPlayers()) {
            playerArenas.remove(player);
        }

        statsService.recordGamesPlayed(context.getPlayers());
    }

    public void handleDisable() {
        if (!arenaStates.isEmpty()) {
            GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> anyContext = arenaStates.values().iterator().next().getContext();
            anyContext.getSchedulerAPI().cancelModuleTasks(moduleInfo.getId());
        }

        arenaStates.clear();
        playerArenas.clear();
        fallingBlockService.cleanupAll();
    }

    public void handlePlayerElimination(Player player) {
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getGameContext(player);
        if (context == null) {
            return;
        }

        int arenaId = context.getArenaId();
        RedAlertArenaState state = arenaStates.get(arenaId);
        if (state == null) {
            return;
        }

        if (!state.getEliminatedPlayers().add(player.getUniqueId())) {
            return;
        }

        messagingService.sendDeathMessage(context, player);
        context.eliminatePlayer(player, moduleConfig.getStringFrom("language.yml", "messages.eliminated"));
        player.getInventory().clear();
        player.setGameMode(GameMode.SPECTATOR);
        context.getSoundsAPI().play(player, coreConfig.getSound("sounds.in_game.respawn"));
    }

    public void handleRespawnEffects(Player player) {
        loadoutService.applyRespawnEffects(player);
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getGameContext(Player player) {
        Integer arenaId = playerArenas.get(player);
        if (arenaId == null) {
            return null;
        }
        RedAlertArenaState state = arenaStates.get(arenaId);
        return state != null ? state.getContext() : null;
    }

    public Map<String, String> getCustomPlaceholders(Player player) {
        Map<String, String> placeholders = new HashMap<>();

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getGameContext(player);
        if (context != null) {
            placeholders.put("alive", String.valueOf(context.getAlivePlayers().size()));
            placeholders.put("spectators", String.valueOf(context.getSpectators().size()));
            RedAlertArenaState state = arenaStates.get(context.getArenaId());
            RedAlertMode mode = state != null ? state.getMode() : RedAlertMode.CHAOS;
            placeholders.put("mode", messagingService.getModeDisplayName(mode));
        }

        return placeholders;
    }

    public void handlePlayerStep(Player player, Location to) {
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getGameContext(player);
        if (context == null) {
            return;
        }

        RedAlertArenaState state = arenaStates.get(context.getArenaId());
        if (state == null) {
            return;
        }

        blockService.handlePlayerStep(context, state, player, to);
    }

    public boolean shouldEliminate(Player player, Location to) {
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getGameContext(player);
        if (context == null) {
            return false;
        }
        RedAlertArenaState state = arenaStates.get(context.getArenaId());
        if (state == null) {
            return false;
        }
        return blockService.shouldEliminate(context, state, to);
    }

    public void handleWin(Player player) {
        Integer arenaId = playerArenas.get(player);
        if (arenaId == null) {
            return;
        }
        statsService.recordWin(player, arenaId);
    }

    public boolean isTrackedFallingBlock(java.util.UUID uuid) {
        return fallingBlockService.isTrackedFallingBlock(uuid);
    }

    public void handleFallingBlockLand(org.bukkit.entity.FallingBlock fallingBlock) {
        fallingBlockService.handleFallingBlockLand(fallingBlock);
    }

    private RedAlertMode resolveMode(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        String dataMode = context.getDataAccess().getGameData("basic.mode", String.class);
        return RedAlertMode.fromString(dataMode != null ? dataMode : "chaos");
    }
}
