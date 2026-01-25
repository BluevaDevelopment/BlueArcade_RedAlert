package net.blueva.arcade.modules.redalert.state;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.modules.redalert.game.RedAlertMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class RedAlertArenaState {

    private final GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context;
    private final Map<BlockKey, Long> blockProgressTimestamps = new ConcurrentHashMap<>();
    private final Set<BlockKey> trackedTrailBlocks = ConcurrentHashMap.newKeySet();
    private final Map<BlockKey, Integer> trackedTrailStages = new ConcurrentHashMap<>();
    private final Set<UUID> eliminatedPlayers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean ended = new AtomicBoolean(false);
    private RedAlertMode mode;
    private FloorBounds floorBounds;

    public RedAlertArenaState(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                              RedAlertMode mode,
                              FloorBounds floorBounds) {
        this.context = context;
        this.mode = mode;
        this.floorBounds = floorBounds;
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getContext() {
        return context;
    }

    public Map<BlockKey, Long> getBlockProgressTimestamps() {
        return blockProgressTimestamps;
    }

    public Set<BlockKey> getTrackedTrailBlocks() {
        return trackedTrailBlocks;
    }

    public Map<BlockKey, Integer> getTrackedTrailStages() {
        return trackedTrailStages;
    }

    public Set<UUID> getEliminatedPlayers() {
        return eliminatedPlayers;
    }

    public RedAlertMode getMode() {
        return mode;
    }

    public void setMode(RedAlertMode mode) {
        this.mode = mode;
    }

    public FloorBounds getFloorBounds() {
        return floorBounds;
    }

    public void setFloorBounds(FloorBounds floorBounds) {
        this.floorBounds = floorBounds;
    }

    public boolean isEnded() {
        return ended.get();
    }

    public boolean markEnded() {
        return !ended.compareAndSet(false, true);
    }
}
