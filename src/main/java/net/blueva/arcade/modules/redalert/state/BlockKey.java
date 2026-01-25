package net.blueva.arcade.modules.redalert.state;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record BlockKey(String world, int x, int y, int z) {
    public static BlockKey from(Location location) {
        if (location.getWorld() == null) {
            return null;
        }
        return new BlockKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        if (w == null) {
            return null;
        }
        return new Location(w, x, y, z);
    }
}
