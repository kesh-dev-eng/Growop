package dev.growops;

import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A hidden grow-op: a container door that opens for permitted players, a light switch and lights. */
public final class GrowOp {
    public final String id;
    public UUID world;
    public BlockKey door1;
    public BlockKey door2;
    public BlockKey switchPos;
    public final List<BlockKey> lights = new ArrayList<>();
    public String permission;
    public boolean lightsOn = true;

    // runtime only
    public boolean doorOpen;
    public final Map<BlockKey, String> saved = new HashMap<>();

    public GrowOp(String id, UUID world) {
        this.id = id;
        this.world = world;
    }

    public boolean hasDoor() {
        return door1 != null && door2 != null;
    }

    public int minX() { return Math.min(door1.x(), door2.x()); }
    public int minY() { return Math.min(door1.y(), door2.y()); }
    public int minZ() { return Math.min(door1.z(), door2.z()); }
    public int maxX() { return Math.max(door1.x(), door2.x()); }
    public int maxY() { return Math.max(door1.y(), door2.y()); }
    public int maxZ() { return Math.max(door1.z(), door2.z()); }

    public int doorVolume() {
        return hasDoor() ? (maxX() - minX() + 1) * (maxY() - minY() + 1) * (maxZ() - minZ() + 1) : 0;
    }

    public boolean inDoor(Block b) {
        if (!hasDoor() || !b.getWorld().getUID().equals(world)) {
            return false;
        }
        return b.getX() >= minX() && b.getX() <= maxX()
                && b.getY() >= minY() && b.getY() <= maxY()
                && b.getZ() >= minZ() && b.getZ() <= maxZ();
    }
}
