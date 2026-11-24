package dev.growops;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.UUID;

public record BlockKey(UUID world, int x, int y, int z) {

    public static BlockKey of(Block b) {
        return new BlockKey(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
    }

    public Block block() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : w.getBlockAt(x, y, z);
    }

    public Location location() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z);
    }

    public String serialize() {
        return world + "," + x + "," + y + "," + z;
    }

    public static BlockKey parse(String s) {
        String[] p = s.split(",");
        return new BlockKey(UUID.fromString(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
    }
}
