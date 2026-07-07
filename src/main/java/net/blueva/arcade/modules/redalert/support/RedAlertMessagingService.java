package net.blueva.arcade.modules.redalert.support;

import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.modules.redalert.game.RedAlertMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RedAlertMessagingService {

    private final ModuleConfigAPI moduleConfig;
    private final CoreConfigAPI coreConfig;
    private final ModuleInfo moduleInfo;

    public RedAlertMessagingService(ModuleConfigAPI moduleConfig, CoreConfigAPI coreConfig, ModuleInfo moduleInfo) {
        this.moduleConfig = moduleConfig;
        this.coreConfig = coreConfig;
        this.moduleInfo = moduleInfo;
    }

    public void sendDescription(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                RedAlertMode mode) {
        for (Player player : context.getPlayers()) {
            List<String> description = moduleConfig.getTranslationList(player, "description." + mode.getKey());
            for (String line : description) {
                context.getMessagesAPI().sendRaw(player, line);
            }
        }
    }

    public void sendCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                  int secondsLeft) {
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) {
                continue;
            }

            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.countdown"));

            String title = coreConfig.getLanguage(player, "titles.starting_game.title")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));

            String subtitle = coreConfig.getLanguage(player, "titles.starting_game.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));

            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 5);
        }
    }

    public void sendCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) {
                continue;
            }

            String title = coreConfig.getLanguage(player, "titles.game_started.title")
                    .replace("{game_display_name}", moduleInfo.getName());

            String subtitle = coreConfig.getLanguage(player, "titles.game_started.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName());

            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 20);
            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.start"));
        }
    }

    public void sendStartTitle(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        for (Player player : context.getPlayers()) {
            String title = coreConfig.getLanguage(player, "titles.game_started.title")
                    .replace("{game_display_name}", moduleInfo.getName());
            String subtitle = coreConfig.getLanguage(player, "titles.game_started.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName());
            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 10);
        }
    }

    public void sendDeathMessage(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                 Player victim) {
        // Don't broadcast death messages for spectators
        if (context.getSpectators().contains(victim)) {
            return;
        }

        String message = getRandomMessage("messages.deaths.generic");
        if (message == null) {
            return;
        }

        message = message.replace("{victim}", victim.getName());

        for (Player player : context.getPlayers()) {
            context.getMessagesAPI().sendRaw(player, message);
        }
    }

    public String getModeDisplayName(Player player, RedAlertMode mode) {
        return moduleConfig.getTranslation(player, "scoreboard.mode_labels." + mode.getKey());
    }

    public String getScoreboardPath() {
        return "scoreboard.main";
    }

    public CoreConfigAPI getCoreConfig() {
        return coreConfig;
    }

    private String getRandomMessage(String path) {
        List<String> messages = moduleConfig.getTranslationList(null, path);
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        int index = ThreadLocalRandom.current().nextInt(messages.size());
        return messages.get(index);
    }

    private static String formatCountdownTime(int seconds) {
        int safeSeconds = Math.max(0, seconds);
        return String.format("%02d:%02d", safeSeconds / 60, safeSeconds % 60);
    }

}
