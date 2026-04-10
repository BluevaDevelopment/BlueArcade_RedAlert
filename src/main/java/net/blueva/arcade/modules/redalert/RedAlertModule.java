package net.blueva.arcade.modules.redalert;

import net.blueva.arcade.api.ModuleAPI;
import net.blueva.arcade.api.achievements.AchievementsAPI;
import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.events.CustomEventRegistry;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GameModule;
import net.blueva.arcade.api.game.GameResult;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.stats.StatsAPI;
import net.blueva.arcade.api.ui.VoteMenuAPI;
import net.blueva.arcade.modules.redalert.game.RedAlertGameManager;
import net.blueva.arcade.modules.redalert.listener.RedAlertListener;
import net.blueva.arcade.modules.redalert.setup.RedAlertSetup;
import net.blueva.arcade.modules.redalert.support.RedAlertBlockService;
import net.blueva.arcade.modules.redalert.support.RedAlertFallingBlockService;
import net.blueva.arcade.modules.redalert.support.RedAlertLoadoutService;
import net.blueva.arcade.modules.redalert.support.RedAlertMessagingService;
import net.blueva.arcade.modules.redalert.support.RedAlertSettings;
import net.blueva.arcade.modules.redalert.support.RedAlertStatsService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class RedAlertModule implements GameModule<Player, Location, World, Material, ItemStack, Sound, Block, Entity, Listener, EventPriority> {

    private ModuleConfigAPI moduleConfig;
    private CoreConfigAPI coreConfig;
    private ModuleInfo moduleInfo;
    private RedAlertGameManager gameManager;

    @Override
    public void onLoad() {
        moduleInfo = ModuleAPI.getModuleInfo("red_alert");

        if (moduleInfo == null) {
            throw new IllegalStateException("ModuleInfo not available for Red Alert module");
        }

        moduleConfig = ModuleAPI.getModuleConfig(moduleInfo.getId());
        coreConfig = ModuleAPI.getCoreConfig();
        StatsAPI statsAPI = ModuleAPI.getStatsAPI();
        VoteMenuAPI voteMenu = ModuleAPI.getVoteMenuAPI();
        AchievementsAPI achievementsAPI = ModuleAPI.getAchievementsAPI();

        moduleConfig.register("language.yml", 1);
        moduleConfig.register("settings.yml", 1);
        moduleConfig.register("achievements.yml", 1);

        RedAlertSettings settings = new RedAlertSettings();
        settings.load(moduleConfig);

        RedAlertStatsService statsService = new RedAlertStatsService(statsAPI, moduleInfo, moduleConfig);
        statsService.registerStats();

        RedAlertLoadoutService loadoutService = new RedAlertLoadoutService(moduleConfig);
        RedAlertMessagingService messagingService = new RedAlertMessagingService(moduleConfig, coreConfig, moduleInfo);
        RedAlertFallingBlockService fallingBlockService = new RedAlertFallingBlockService(settings);
        RedAlertBlockService blockService = new RedAlertBlockService(settings, statsService, fallingBlockService);

        gameManager = new RedAlertGameManager(moduleInfo, moduleConfig, coreConfig, statsService,
                loadoutService, messagingService, fallingBlockService, blockService);

        if (achievementsAPI != null) {
            achievementsAPI.registerModuleAchievements(moduleInfo.getId(), "achievements.yml");
        }

        ModuleAPI.getSetupAPI().registerHandler(moduleInfo.getId(), new RedAlertSetup(moduleConfig, coreConfig));

        if (moduleConfig != null && voteMenu != null) {
            voteMenu.registerGame(
                    moduleInfo.getId(),
                    Material.valueOf(moduleConfig.getString("menus.vote.item")),
                    moduleConfig.getStringFrom("language.yml", "vote_menu.name"),
                    moduleConfig.getStringListFrom("language.yml", "vote_menu.lore")
            );
        }
    }

    @Override
    public void onStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        gameManager.handleStart(context);
    }

    @Override
    public void onCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                int secondsLeft) {
        gameManager.handleCountdownTick(context, secondsLeft);
    }

    @Override
    public void onCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        gameManager.handleCountdownFinish(context);
    }

    @Override
    public boolean freezePlayersOnCountdown() {
        return gameManager.freezePlayersOnCountdown();
    }

    @Override
    public void onGameStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        gameManager.handleGameStart(context);
    }

    @Override
    public void onEnd(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                      GameResult<Player> result) {
        gameManager.handleEnd(context);
    }

    @Override
    public void onDisable() {
        gameManager.handleDisable();
    }

    @Override
    public void registerEvents(CustomEventRegistry<Listener, EventPriority> registry) {
        registry.register(new RedAlertListener(gameManager));
    }

    @Override
    public Map<String, String> getCustomPlaceholders(Player player) {
        return gameManager.getCustomPlaceholders(player);
    }
}
