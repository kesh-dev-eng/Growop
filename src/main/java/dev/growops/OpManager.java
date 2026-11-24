package dev.growops;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OpManager {
    private final GrowOpsPlugin plugin;
    private final Map<String, GrowOp> ops = new LinkedHashMap<>();
    private final File file;

    public OpManager(GrowOpsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ops.yml");
    }

    public Collection<GrowOp> all() {
        return ops.values();
    }

    public GrowOp get(String id) {
        return ops.get(id.toLowerCase());
    }

    public GrowOp create(String id, UUID world) {
        GrowOp op = new GrowOp(id.toLowerCase(), world);
        ops.put(op.id, op);
        return op;
    }

    public boolean delete(String id) {
        GrowOp op = ops.remove(id.toLowerCase());
        if (op != null && op.doorOpen) {
            closeDoor(op);
        }
        return op != null;
    }

    public GrowOp atDoor(Block b) {
        for (GrowOp op : ops.values()) {
            if (op.inDoor(b)) {
                return op;
            }
        }
        return null;
    }

    public GrowOp atSwitch(Block b) {
        BlockKey k = BlockKey.of(b);
        for (GrowOp op : ops.values()) {
            if (k.equals(op.switchPos)) {
                return op;
            }
        }
        return null;
    }

    public boolean canUse(Player p, GrowOp op) {
        return op.permission == null || p.hasPermission(op.permission) || p.hasPermission("growops.admin");
    }

    // ------------------------------------------------------------------ door

    public void useDoor(Player p, GrowOp op) {
        if (!canUse(p, op)) {
            Util.bar(p, "<red>The container is locked.");
            p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.6f, 0.6f);
            return;
        }
        openDoor(op);
    }

    public void openDoor(GrowOp op) {
        if (op.doorOpen || !op.hasDoor()) {
            return;
        }
        World w = Bukkit.getWorld(op.world);
        if (w == null) {
            return;
        }
        op.saved.clear();
        for (int x = op.minX(); x <= op.maxX(); x++) {
            for (int y = op.minY(); y <= op.maxY(); y++) {
                for (int z = op.minZ(); z <= op.maxZ(); z++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType().isAir()) {
                        continue;
                    }
                    op.saved.put(BlockKey.of(b), b.getBlockData().getAsString());
                    b.setType(Material.AIR, false);
                }
            }
        }
        op.doorOpen = true;
        Location center = new Location(w, (op.minX() + op.maxX() + 1) / 2.0, (op.minY() + op.maxY() + 1) / 2.0, (op.minZ() + op.maxZ() + 1) / 2.0);
        w.playSound(center, Sound.BLOCK_IRON_DOOR_OPEN, 1f, 0.7f);

        int seconds = plugin.getConfig().getInt("growop.door-open-seconds", 8);
        new BukkitRunnable() {
            int left = seconds * 20;

            @Override
            public void run() {
                if (!op.doorOpen) {
                    cancel();
                    return;
                }
                left -= 10;
                if (left > 0 || playerInside(op, w)) {
                    return;
                }
                closeDoor(op);
                cancel();
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    private boolean playerInside(GrowOp op, World w) {
        for (Player p : w.getPlayers()) {
            Location l = p.getLocation();
            if (l.getX() >= op.minX() - 0.4 && l.getX() < op.maxX() + 1.4
                    && l.getY() >= op.minY() - 0.4 && l.getY() < op.maxY() + 1.4
                    && l.getZ() >= op.minZ() - 0.4 && l.getZ() < op.maxZ() + 1.4) {
                return true;
            }
        }
        return false;
    }

    public void closeDoor(GrowOp op) {
        for (Map.Entry<BlockKey, String> en : op.saved.entrySet()) {
            Block b = en.getKey().block();
            if (b != null) {
                b.setBlockData(Bukkit.createBlockData(en.getValue()), false);
            }
        }
        if (!op.saved.isEmpty()) {
            Location c = op.door1.location();
            if (c != null) {
                c.getWorld().playSound(c, Sound.BLOCK_IRON_DOOR_CLOSE, 1f, 0.7f);
            }
        }
        op.saved.clear();
        op.doorOpen = false;
    }

    public void closeAll() {
        for (GrowOp op : ops.values()) {
            if (op.doorOpen) {
                closeDoor(op);
            }
        }
    }

    // ------------------------------------------------------------------ lights

    public void useSwitch(Player p, GrowOp op) {
        if (!canUse(p, op)) {
            Util.bar(p, "<red>You are not allowed to use this switch.");
            return;
        }
        setLights(op, !op.lightsOn);
        p.playSound(p.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.8f, op.lightsOn ? 1.2f : 0.8f);
        Util.bar(p, op.lightsOn ? "<yellow>Lights on" : "<gray>Lights off");
    }

    public void setLights(GrowOp op, boolean on) {
        op.lightsOn = on;
        for (BlockKey k : op.lights) {
            Block b = k.block();
            if (b != null && b.getBlockData() instanceof Lightable l) {
                l.setLit(on);
                b.setBlockData(l, false);
            }
        }
    }

    // ------------------------------------------------------------------ persistence

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (GrowOp op : ops.values()) {
            String p = "ops." + op.id;
            y.set(p + ".world", op.world.toString());
            if (op.door1 != null) {
                y.set(p + ".door1", op.door1.serialize());
            }
            if (op.door2 != null) {
                y.set(p + ".door2", op.door2.serialize());
            }
            if (op.switchPos != null) {
                y.set(p + ".switch", op.switchPos.serialize());
            }
            List<String> lights = new ArrayList<>();
            for (BlockKey k : op.lights) {
                lights.add(k.serialize());
            }
            y.set(p + ".lights", lights);
            y.set(p + ".permission", op.permission);
            y.set(p + ".lights-on", op.lightsOn);
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save ops.yml: " + ex.getMessage());
        }
    }

    public void load() {
        ops.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("ops");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            try {
                GrowOp op = new GrowOp(id, UUID.fromString(s.getString("world", "")));
                if (s.getString("door1") != null) {
                    op.door1 = BlockKey.parse(s.getString("door1"));
                }
                if (s.getString("door2") != null) {
                    op.door2 = BlockKey.parse(s.getString("door2"));
                }
                if (s.getString("switch") != null) {
                    op.switchPos = BlockKey.parse(s.getString("switch"));
                }
                for (String l : s.getStringList("lights")) {
                    op.lights.add(BlockKey.parse(l));
                }
                op.permission = s.getString("permission");
                op.lightsOn = s.getBoolean("lights-on", true);
                ops.put(op.id, op);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping broken grow-op " + id + ": " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + ops.size() + " grow-ops.");
    }
}
