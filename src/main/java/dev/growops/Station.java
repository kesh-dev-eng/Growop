package dev.growops;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class Station {
    public final UUID id;
    public final StationType type;
    public final BlockKey pos;
    public UUID owner;
    public boolean fixed;
    public Plant plant;
    public int tank;
    public long lastTick = System.currentTimeMillis();
    public final Set<UUID> members = new HashSet<>();
    public ItemStack[] contents = new ItemStack[27];

    // runtime only
    public Inventory inventory;
    public boolean busy;

    public Station(UUID id, StationType type, BlockKey pos) {
        this.id = id;
        this.type = type;
        this.pos = pos;
    }

    public Location location() {
        World w = Bukkit.getWorld(pos.world());
        return w == null ? null : new Location(w, pos.x(), pos.y(), pos.z());
    }

    public boolean canOpenLockup(UUID player) {
        return owner == null || owner.equals(player) || members.contains(player);
    }
}
