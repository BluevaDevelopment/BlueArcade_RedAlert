package net.blueva.arcade.modules.redalert.support;

import net.blueva.arcade.api.arena.FloorRegion;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.modules.redalert.game.RedAlertMode;
import net.blueva.arcade.modules.redalert.state.BlockKey;
import net.blueva.arcade.modules.redalert.state.FloorBounds;
import net.blueva.arcade.modules.redalert.state.ProgressResult;
import net.blueva.arcade.modules.redalert.state.RedAlertArenaState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class RedAlertBlockService {

    private final RedAlertSettings settings;
    private final RedAlertStatsService statsService;
    private final RedAlertFallingBlockService fallingBlockService;

    public RedAlertBlockService(RedAlertSettings settings, RedAlertStatsService statsService, RedAlertFallingBlockService fallingBlockService) {
        this.settings = settings;
        this.statsService = statsService;
        this.fallingBlockService = fallingBlockService;
    }

    public void cacheFloorBounds(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                 RedAlertArenaState state) {
        FloorBounds bounds = findFloorBounds(context);
        state.setFloorBounds(bounds);
    }

    public void resetFloor(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                           RedAlertArenaState state) {
        FloorBounds floor = state.getFloorBounds();
        if (floor == null) {
            return;
        }
        List<Material> stageMaterials = settings.getStageMaterials();
        if (stageMaterials.isEmpty()) {
            return;
        }

        context.getBlocksAPI().setRegion(floor.min(), floor.max(), stageMaterials.get(0));
    }

    public boolean shouldEliminate(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                   RedAlertArenaState state,
                                   Location to) {
        if (context == null || to == null) {
            return false;
        }

        if (!context.isInsideBounds(to)) {
            return true;
        }

        FloorBounds floor = state.getFloorBounds();
        if (floor == null) {
            return false;
        }

        double minY = Math.min(floor.min().getY(), floor.max().getY());
        return to.getY() < minY - settings.getEliminationMargin();
    }

    public void handlePlayerStep(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                 RedAlertArenaState state,
                                 Player player,
                                 Location to) {
        if (context == null || state == null) {
            return;
        }
        if (context.getPhase() != GamePhase.PLAYING) {
            return;
        }

        if (state.getMode() != RedAlertMode.TRAIL) {
            return;
        }

        FloorBounds floor = state.getFloorBounds();
        if (floor == null) {
            return;
        }

        Set<Block> touchedBlocks = detectTouchedBlocks(floor, to);
        if (touchedBlocks.isEmpty()) {
            return;
        }

        for (Block touched : touchedBlocks) {
            long currentTick = touched.getWorld() != null ? touched.getWorld().getFullTime() : 0L;
            boolean rescued = ensureTrackedAndRescue(context, state, touched, currentTick);
            boolean expedite = settings.isAccelerateHotBlockTouches() && getStageIndex(touched.getType()) > 0;
            if (!rescued) {
                progressTrailTouch(context, state, touched, player.getUniqueId(), false, expedite);
            }
        }
    }

    public void startModeTasks(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                               RedAlertArenaState state) {
        int arenaId = context.getArenaId();
        String taskId = "arena_" + arenaId + "_red_alert_mode";

        if (state.getMode() == RedAlertMode.CHAOS) {
            startChaosTask(context, state, taskId);
        } else {
            startTrailHoldTask(context, state, taskId);
        }
    }

    private void startChaosTask(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                RedAlertArenaState state,
                                String taskId) {
        int arenaId = context.getArenaId();
        FloorBounds floor = state.getFloorBounds();
        if (floor == null) {
            return;
        }

        double[] chance = {settings.getChaosBaseChance()};
        double incrementPerRun = settings.getChaosChanceIncrease() * (settings.getChaosTickRate() / 20.0);

        context.getSchedulerAPI().runTimer(taskId, () -> {
            if (state.isEnded()) {
                context.getSchedulerAPI().cancelTask(taskId);
                return;
            }

            applyChaosTick(context, state, floor, chance[0]);
            chance[0] = Math.min(100.0, chance[0] + incrementPerRun);
        }, 0L, settings.getChaosTickRate());
    }

    private void applyChaosTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                RedAlertArenaState state,
                                FloorBounds floor,
                                double chance) {
        Location min = floor.min();
        Location max = floor.max();
        World world = min.getWorld();
        if (world == null) {
            return;
        }

        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (random.nextDouble(100) < chance) {
                        Block block = world.getBlockAt(x, y, z);
                        int stage = getStageIndex(block.getType());
                        if (stage < settings.getStageMaterials().size()) {
                            BlockKey key = BlockKey.from(block.getLocation());
                            long tick = world.getFullTime();
                            ProgressResult result = progressBlock(context, state, block.getLocation(), key, tick, stage + 1, null, false);
                            updateProgressTracking(state, key, tick, result, stage + 1);
                        }
                    }
                }
            }
        }
    }

    private void startTrailHoldTask(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    RedAlertArenaState state,
                                    String taskId) {
        int arenaId = context.getArenaId();

        context.getSchedulerAPI().runTimer(taskId, () -> {
            if (state.isEnded()) {
                context.getSchedulerAPI().cancelTask(taskId);
                return;
            }

            long currentTick = resolveCurrentTick(state);

            for (Player player : context.getAlivePlayers()) {
                Location location = player.getLocation();
                if (location.getWorld() == null) {
                    continue;
                }

                FloorBounds floor = state.getFloorBounds();
                if (floor == null) {
                    continue;
                }

                Set<Block> touchedBlocks = detectTouchedBlocks(floor, location);
                for (Block block : touchedBlocks) {
                    long blockTick = block.getWorld() != null ? block.getWorld().getFullTime() : 0L;
                    boolean rescued = ensureTrackedAndRescue(context, state, block, blockTick);
                    boolean expedite = settings.isAccelerateHotBlockTouches() && getStageIndex(block.getType()) > 0;
                    if (!rescued) {
                        progressTrailTouch(context, state, block, player.getUniqueId(), false, expedite);
                    }
                }
            }

            FloorBounds floor = state.getFloorBounds();
            if (floor != null) {
                resyncUntrackedBlocks(state, floor, currentTick);
            }
            processStaleBlocks(context, state, currentTick);
        }, settings.getTrailHoldInterval(), settings.getTrailHoldInterval());
    }

    private ProgressResult progressBlock(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                         RedAlertArenaState state,
                                         Location location,
                                         BlockKey key,
                                         long currentTick, int targetStage, UUID trigger, boolean ignoreCriticalDelay) {
        World world = location.getWorld();
        if (world == null) {
            return ProgressResult.NO_CHANGE;
        }

        Block block = world.getBlockAt(location);
        org.bukkit.block.data.BlockData originalData = block.getBlockData();
        int currentStage = resolveTrackedStage(state, key, block.getType());
        if (currentStage >= settings.getStageMaterials().size() && targetStage <= currentStage) {
            return ProgressResult.NO_CHANGE;
        }
        if (targetStage <= currentStage) {
            return ProgressResult.NO_CHANGE;
        }

        int cappedStage = Math.min(targetStage, settings.getStageMaterials().size());
        Material nextMaterial = cappedStage >= settings.getStageMaterials().size()
                ? Material.AIR
                : settings.getStageMaterials().get(cappedStage);

        if (cappedStage >= settings.getStageMaterials().size()) {
            if (!ignoreCriticalDelay && settings.getCriticalBreakDelayTicks() > 0
                    && currentStage == settings.getStageMaterials().size() - 1) {
                Long lastTick = state.getBlockProgressTimestamps().get(key);
                if (lastTick != null && currentTick - lastTick < settings.getCriticalBreakDelayTicks()) {
                    return ProgressResult.NO_CHANGE;
                }
            }

            block.setType(Material.AIR);
            if (settings.isFallingBlocksEnabled() && originalData.getMaterial() != Material.AIR) {
                fallingBlockService.spawnFallingShard(context, block.getLocation(), originalData);
            }
            if (settings.isParticlesEnabled()) {
                Particle particle = settings.getDecayParticle();
                block.getWorld().spawnParticle(particle, block.getLocation().add(0.5, 0.5, 0.5),
                        settings.getParticleCount(), settings.getParticleSpread(), settings.getParticleSpread() * 0.6,
                        settings.getParticleSpread(), settings.getParticleSpeed());
            }
            if (trigger != null) {
                Player player = Bukkit.getPlayer(trigger);
                if (player != null) {
                    statsService.recordTileMelt(player);
                }
            }
            return ProgressResult.REMOVED;
        }

        block.setType(nextMaterial);
        return ProgressResult.ADVANCED;
    }

    private void progressTrailTouch(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    RedAlertArenaState state,
                                    Block block,
                                    UUID trigger,
                                    boolean checkCooldown,
                                    boolean expedite) {
        World world = block.getWorld();
        BlockKey key = BlockKey.from(block.getLocation());
        if (world == null || key == null) {
            return;
        }

        int currentStage = resolveTrackedStage(state, key, block.getType());
        if (currentStage >= settings.getStageMaterials().size()) {
            return;
        }

        long currentTick = world.getFullTime();
        Long lastTick = getLastProgressTick(state, key);
        long requiredDelay = getStageDelay(currentStage);

        if (!expedite && checkCooldown && currentStage < settings.getStageMaterials().size() - 1
                && isOnCooldown(state, key, currentTick)) {
            return;
        }
        if (!expedite && settings.getCriticalBreakDelayTicks() > 0
                && currentStage == settings.getStageMaterials().size() - 1 && lastTick != null) {
            if (currentTick - lastTick < settings.getCriticalBreakDelayTicks()) {
                return;
            }
        }

        if (lastTick != null) {
            long adjustedDelay = expedite ? Math.max(1L, requiredDelay / 2) : requiredDelay;
            if (currentTick - lastTick < adjustedDelay) {
                return;
            }
        }

        int targetStage = currentStage + 1;
        boolean ignoreCriticalDelay = expedite;
        ProgressResult result = progressBlock(context, state, block.getLocation(), key, currentTick, targetStage, trigger, ignoreCriticalDelay);
        updateProgressTracking(state, key, currentTick, result, targetStage);
    }

    private boolean isOnCooldown(RedAlertArenaState state, BlockKey key, long currentTick) {
        Long lastTick = state.getBlockProgressTimestamps().get(key);
        return lastTick != null && currentTick - lastTick < settings.getTrailHoldInterval();
    }

    private Long getLastProgressTick(RedAlertArenaState state, BlockKey key) {
        return state.getBlockProgressTimestamps().get(key);
    }

    private void updateProgressTracking(RedAlertArenaState state, BlockKey key, long currentTick, ProgressResult result, int resultingStage) {
        if (result == ProgressResult.NO_CHANGE) {
            return;
        }
        if (result == ProgressResult.REMOVED) {
            removeBlockTracking(state, key);
            return;
        }

        registerBlockTracking(state, key, currentTick, resultingStage);
    }

    private void registerBlockTracking(RedAlertArenaState state, BlockKey key, long currentTick, int stage) {
        registerBlockTracking(state, key, currentTick, stage, false);
    }

    private void registerBlockTracking(RedAlertArenaState state, BlockKey key, long currentTick, int stage, boolean allowImmediateAdvance) {
        int clampedStage = clampStage(stage);
        long storedTick = allowImmediateAdvance ? currentTick - getStageDelay(clampedStage) : currentTick;
        state.getBlockProgressTimestamps().put(key, storedTick);
        if (isTrailMode(state)) {
            state.getTrackedTrailBlocks().add(key);
            state.getTrackedTrailStages().put(key, clampedStage);
        }
    }

    private void removeBlockTracking(RedAlertArenaState state, BlockKey key) {
        state.getBlockProgressTimestamps().remove(key);
        state.getTrackedTrailBlocks().remove(key);
        state.getTrackedTrailStages().remove(key);
    }

    private boolean isTrailMode(RedAlertArenaState state) {
        return state.getMode() == RedAlertMode.TRAIL;
    }

    private int resolveTrackedStage(RedAlertArenaState state, BlockKey key, Material fallbackMaterial) {
        Map<BlockKey, Integer> stageMap = state.getTrackedTrailStages();
        if (stageMap.containsKey(key)) {
            return stageMap.get(key);
        }
        return clampStage(getStageIndex(fallbackMaterial));
    }

    private long getStageDelay(int stage) {
        List<Long> stageAdvanceTicks = settings.getStageAdvanceTicks();
        if (stageAdvanceTicks.isEmpty()) {
            return settings.getTrailHoldInterval();
        }
        if (stage >= 0 && stage < stageAdvanceTicks.size()) {
            return stageAdvanceTicks.get(stage);
        }
        return stageAdvanceTicks.get(stageAdvanceTicks.size() - 1);
    }

    private int clampStage(int stage) {
        if (stage < 0) {
            return 0;
        }
        if (stage >= settings.getStageMaterials().size()) {
            return 0;
        }
        return stage;
    }

    private void processStaleBlocks(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    RedAlertArenaState state,
                                    long currentTick) {
        Map<BlockKey, Long> arenaMap = state.getBlockProgressTimestamps();
        Set<BlockKey> trackedBlocks = state.getTrackedTrailBlocks();
        if (trackedBlocks.isEmpty()) {
            trackedBlocks.addAll(arenaMap.keySet());
        }

        Map<BlockKey, Integer> stageMap = state.getTrackedTrailStages();

        List<BlockKey> keys = new ArrayList<>(trackedBlocks);
        for (BlockKey key : keys) {
            Long lastTick = arenaMap.get(key);
            Location location = key.toLocation();
            if (location == null) {
                removeBlockTracking(state, key);
                continue;
            }

            stageMap.computeIfAbsent(key, missing -> clampStage(getStageIndex(location.getBlock().getType())));
            int stage = resolveTrackedStage(state, key, location.getBlock().getType());
            int cappedStage = Math.min(stage, settings.getStageMaterials().size() - 1);

            if (lastTick == null) {
                lastTick = currentTick;
                arenaMap.put(key, lastTick);
            }

            long requiredDelay = getStageDelay(cappedStage);
            if (currentTick - lastTick < requiredDelay) {
                continue;
            }

            ProgressResult result = progressBlock(context, state, location, key, currentTick,
                    cappedStage + 1, null, false);
            updateProgressTracking(state, key, currentTick, result, cappedStage + 1);
        }
    }

    private long resolveCurrentTick(RedAlertArenaState state) {
        FloorBounds bounds = state.getFloorBounds();
        if (bounds != null && bounds.min().getWorld() != null) {
            return bounds.min().getWorld().getFullTime();
        }
        List<World> worlds = Bukkit.getWorlds();
        if (!worlds.isEmpty()) {
            return worlds.get(0).getFullTime();
        }
        return System.currentTimeMillis() / 50L;
    }

    private int getStageIndex(Material material) {
        List<Material> stageMaterials = settings.getStageMaterials();
        for (int i = 0; i < stageMaterials.size(); i++) {
            if (stageMaterials.get(i) == material) {
                return i;
            }
        }
        return stageMaterials.size();
    }

    private FloorBounds findFloorBounds(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        Location storedMin = context.getDataAccess().getGameLocation("game.floor.bounds.min");
        Location storedMax = context.getDataAccess().getGameLocation("game.floor.bounds.max");

        if (storedMin != null && storedMax != null) {
            return new FloorBounds(storedMin, storedMax);
        }

        List<FloorRegion<Location>> floors = context.getArenaAPI().getFloors();
        if (floors != null && !floors.isEmpty()) {
            FloorRegion<Location> region = floors.get(0);
            return new FloorBounds(region.getMin(), region.getMax());
        }

        return null;
    }

    private Set<Block> detectTouchedBlocks(FloorBounds floor, Location playerLocation) {
        Set<Block> blocks = new HashSet<>();
        if (playerLocation.getWorld() == null) {
            return blocks;
        }

        Block baseBlock = findBaseBlock(floor, playerLocation);
        if (baseBlock == null) {
            return blocks;
        }

        World world = baseBlock.getWorld();
        int baseX = baseBlock.getX();
        int baseY = baseBlock.getY();
        int baseZ = baseBlock.getZ();

        addBlockIfValid(blocks, floor, world, baseX, baseY, baseZ);
        for (int i = 1; i <= settings.getDetectionAdditionalBelow(); i++) {
            addBlockIfValid(blocks, floor, world, baseX, baseY - i, baseZ);
        }

        double moveX = playerLocation.getX() - Math.floor(playerLocation.getX()) - 0.5;
        double moveZ = playerLocation.getZ() - Math.floor(playerLocation.getZ()) - 0.5;

        int xDir = moveX > settings.getDetectionEdgeThreshold() ? 1 : (moveX < -settings.getDetectionEdgeThreshold() ? -1 : 0);
        int zDir = moveZ > settings.getDetectionEdgeThreshold() ? 1 : (moveZ < -settings.getDetectionEdgeThreshold() ? -1 : 0);

        if (xDir != 0) {
            addBlockIfValid(blocks, floor, world, baseX + xDir, baseY, baseZ);
            for (int i = 1; i <= settings.getDetectionAdditionalBelow(); i++) {
                addBlockIfValid(blocks, floor, world, baseX + xDir, baseY - i, baseZ);
            }
        }

        if (zDir != 0) {
            addBlockIfValid(blocks, floor, world, baseX, baseY, baseZ + zDir);
            for (int i = 1; i <= settings.getDetectionAdditionalBelow(); i++) {
                addBlockIfValid(blocks, floor, world, baseX, baseY - i, baseZ + zDir);
            }
        }

        if (xDir != 0 && zDir != 0) {
            addBlockIfValid(blocks, floor, world, baseX + xDir, baseY, baseZ + zDir);
            for (int i = 1; i <= settings.getDetectionAdditionalBelow(); i++) {
                addBlockIfValid(blocks, floor, world, baseX + xDir, baseY - i, baseZ + zDir);
            }
        }

        return blocks;
    }

    private Block findBaseBlock(FloorBounds floor, Location playerLocation) {
        World world = playerLocation.getWorld();
        if (world == null) {
            return null;
        }

        int playerX = playerLocation.getBlockX();
        int playerZ = playerLocation.getBlockZ();

        for (int depth = 1; depth <= settings.getDetectionScanDepth(); depth++) {
            Block candidate = world.getBlockAt(playerX, playerLocation.getBlockY() - depth, playerZ);
            if (isFloorCandidate(floor, candidate)) {
                return candidate;
            }
        }

        Block standing = world.getBlockAt(playerX, playerLocation.getBlockY(), playerZ);
        if (isFloorCandidate(floor, standing)) {
            return standing;
        }

        return null;
    }

    private void addBlockIfValid(Set<Block> blocks, FloorBounds floor, World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        if (isFloorCandidate(floor, block)) {
            blocks.add(block);
        }
    }

    private boolean isFloorCandidate(FloorBounds floor, Block block) {
        if (block == null || block.getWorld() == null) {
            return false;
        }
        if (!floor.contains(block.getLocation())) {
            return false;
        }
        Material type = block.getType();
        return type != Material.AIR && type != Material.BARRIER;
    }

    private boolean ensureTrackedAndRescue(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                           RedAlertArenaState state,
                                           Block block,
                                           long currentTick) {
        BlockKey key = BlockKey.from(block.getLocation());
        if (key == null || block.getWorld() == null) {
            return false;
        }

        int stage = clampStage(getStageIndex(block.getType()));

        Map<BlockKey, Long> arenaMap = state.getBlockProgressTimestamps();
        if (arenaMap.containsKey(key)) {
            return false;
        }

        registerBlockTracking(state, key, currentTick, stage, true);
        if (stage > 0) {
            progressTrailTouch(context, state, block, null, false, true);
            return true;
        }
        return false;
    }

    private void resyncUntrackedBlocks(RedAlertArenaState state, FloorBounds floor, long currentTick) {
        if (!isTrailMode(state)) {
            return;
        }

        Location min = floor.min();
        Location max = floor.max();
        World world = min.getWorld();
        if (world == null) {
            return;
        }

        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

        Set<BlockKey> tracked = state.getTrackedTrailBlocks();
        Map<BlockKey, Long> timestamps = state.getBlockProgressTimestamps();
        Map<BlockKey, Integer> stageMap = state.getTrackedTrailStages();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    BlockKey key = BlockKey.from(block.getLocation());
                    if (key == null) {
                        continue;
                    }

                    Material type = block.getType();
                    if ((type == Material.AIR || type == Material.BARRIER) && tracked.contains(key)) {
                        removeBlockTracking(state, key);
                        continue;
                    }

                    int stage = getStageIndex(type);
                    if (stage <= 0 || stage >= settings.getStageMaterials().size()) {
                        continue;
                    }

                    tracked.add(key);
                    stageMap.put(key, stage);

                    long stageDelay = getStageDelay(stage);
                    timestamps.merge(key, currentTick - stageDelay, Math::min);
                }
            }
        }
    }
}
