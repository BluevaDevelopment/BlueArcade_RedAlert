package net.blueva.arcade.modules.redalert.support;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import org.bukkit.Material;
import org.bukkit.Particle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RedAlertSettings {

    private List<Material> stageMaterials = new ArrayList<>();
    private Particle decayParticle = Particle.FLAME;
    private int particleCount = 12;
    private double particleSpread = 0.35;
    private double particleSpeed = 0.15;
    private boolean particlesEnabled = true;
    private double eliminationMargin = 1.0;
    private double chaosBaseChance = 1.5;
    private double chaosChanceIncrease = 1.0;
    private long chaosTickRate = 20L;
    private long trailHoldInterval = 12L;
    private long criticalBreakDelayTicks = 0L;
    private int detectionScanDepth = 3;
    private int detectionAdditionalBelow = 0;
    private double detectionEdgeThreshold = 0.25;
    private boolean accelerateHotBlockTouches = true;
    private final List<Long> stageAdvanceTicks = new ArrayList<>();
    private boolean fallingBlocksEnabled = true;
    private double fallingDownwardVelocity = 0.3;
    private double fallingHorizontalRandomness = 0.08;

    public void load(ModuleConfigAPI moduleConfig) {
        stageMaterials = new ArrayList<>();
        stageMaterials.add(parseMaterial(moduleConfig.getString("blocks.stages.safe", "WHITE_CONCRETE"), Material.WHITE_CONCRETE));
        stageMaterials.add(parseMaterial(moduleConfig.getString("blocks.stages.warn", "YELLOW_CONCRETE"), Material.YELLOW_CONCRETE));
        stageMaterials.add(parseMaterial(moduleConfig.getString("blocks.stages.danger", "ORANGE_CONCRETE"), Material.ORANGE_CONCRETE));
        stageMaterials.add(parseMaterial(moduleConfig.getString("blocks.stages.critical", "RED_CONCRETE"), Material.RED_CONCRETE));

        particlesEnabled = moduleConfig.getBoolean("blocks.particles.enabled", true);
        decayParticle = parseParticle(moduleConfig.getString("blocks.particles.type", "FLAME"));
        particleCount = moduleConfig.getInt("blocks.particles.count", 12);
        particleSpread = moduleConfig.getDouble("blocks.particles.spread", 0.35);
        particleSpeed = moduleConfig.getDouble("blocks.particles.speed", 0.15);

        fallingBlocksEnabled = moduleConfig.getBoolean("blocks.falling.enabled", true);
        fallingDownwardVelocity = moduleConfig.getDouble("blocks.falling.downward_velocity", 0.3);
        fallingHorizontalRandomness = moduleConfig.getDouble("blocks.falling.horizontal_randomness", 0.08);

        trailHoldInterval = Math.max(1L, moduleConfig.getInt("trail_mode.hold_interval_ticks", 12));
        stageAdvanceTicks.clear();
        stageAdvanceTicks.add((long) Math.max(1, moduleConfig.getInt("trail_mode.stage_advance_ticks.safe_to_warn", 12)));
        stageAdvanceTicks.add((long) Math.max(1, moduleConfig.getInt("trail_mode.stage_advance_ticks.warn_to_danger", 12)));
        stageAdvanceTicks.add((long) Math.max(1, moduleConfig.getInt("trail_mode.stage_advance_ticks.danger_to_critical", 12)));
        stageAdvanceTicks.add((long) Math.max(1, moduleConfig.getInt("trail_mode.stage_advance_ticks.critical_to_air", 12)));
        while (stageAdvanceTicks.size() < stageMaterials.size()) {
            long fallbackDelay = stageAdvanceTicks.isEmpty()
                    ? trailHoldInterval
                    : stageAdvanceTicks.get(stageAdvanceTicks.size() - 1);
            stageAdvanceTicks.add(fallbackDelay);
        }
        criticalBreakDelayTicks = Math.max(0L, moduleConfig.getInt("trail_mode.critical_break_delay_ticks", 0));
        detectionScanDepth = Math.max(1, moduleConfig.getInt("trail_mode.detection.scan_depth", 3));
        detectionAdditionalBelow = Math.max(0, moduleConfig.getInt("trail_mode.detection.additional_blocks_below", 0));
        detectionEdgeThreshold = Math.max(0.0, moduleConfig.getDouble("trail_mode.detection.edge_threshold", 0.25));
        accelerateHotBlockTouches = moduleConfig.getBoolean("trail_mode.accelerate_hot_block_touches", true);

        eliminationMargin = moduleConfig.getDouble("gameplay.elimination_margin", 1.0);
        chaosBaseChance = moduleConfig.getDouble("random_mode.change_color_chance", 1.5);
        chaosChanceIncrease = moduleConfig.getDouble("random_mode.increase_chance_every_second", 1.0);
        chaosTickRate = Math.max(1L, moduleConfig.getInt("random_mode.tick_rate", 20));
    }

    public List<Material> getStageMaterials() {
        return Collections.unmodifiableList(stageMaterials);
    }

    public Particle getDecayParticle() {
        return decayParticle;
    }

    public int getParticleCount() {
        return particleCount;
    }

    public double getParticleSpread() {
        return particleSpread;
    }

    public double getParticleSpeed() {
        return particleSpeed;
    }

    public boolean isParticlesEnabled() {
        return particlesEnabled;
    }

    public double getEliminationMargin() {
        return eliminationMargin;
    }

    public double getChaosBaseChance() {
        return chaosBaseChance;
    }

    public double getChaosChanceIncrease() {
        return chaosChanceIncrease;
    }

    public long getChaosTickRate() {
        return chaosTickRate;
    }

    public long getTrailHoldInterval() {
        return trailHoldInterval;
    }

    public long getCriticalBreakDelayTicks() {
        return criticalBreakDelayTicks;
    }

    public int getDetectionScanDepth() {
        return detectionScanDepth;
    }

    public int getDetectionAdditionalBelow() {
        return detectionAdditionalBelow;
    }

    public double getDetectionEdgeThreshold() {
        return detectionEdgeThreshold;
    }

    public boolean isAccelerateHotBlockTouches() {
        return accelerateHotBlockTouches;
    }

    public List<Long> getStageAdvanceTicks() {
        return Collections.unmodifiableList(stageAdvanceTicks);
    }

    public boolean isFallingBlocksEnabled() {
        return fallingBlocksEnabled;
    }

    public double getFallingDownwardVelocity() {
        return fallingDownwardVelocity;
    }

    public double getFallingHorizontalRandomness() {
        return fallingHorizontalRandomness;
    }

    private Material parseMaterial(String name, Material fallback) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return fallback;
        }
    }

    private Particle parseParticle(String name) {
        try {
            return Particle.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return Particle.FLAME;
        }
    }
}
