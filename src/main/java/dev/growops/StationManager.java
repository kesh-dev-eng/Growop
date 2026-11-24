package dev.growops;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StationManager {
    private final GrowOpsPlugin plugin;
    private final Map<UUID, Station> byId = new LinkedHashMap<>();
    private final Map<BlockKey, Station> byPos = new HashMap<>();
    private final File file;

    public StationManager(GrowOpsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public Station get(Block b) {
        return byPos.get(BlockKey.of(b));
    }

    public Collection<Station> all() {
        return byId.values();
    }

    public boolean isLive(Station st) {
        return byId.get(st.id) == st;
    }

    public Material blockMaterial(StationType t) {
        String s = plugin.getConfig().getString("stations." + t.name() + ".block");
        Material m = s == null ? null : Material.matchMaterial(s);
        return m == null ? t.defaultBlock : m;
    }

    public Station create(StationType type, Block block, UUID owner, boolean fixed) {
        block.setType(blockMaterial(type), false);
        Station st = new Station(UUID.randomUUID(), type, BlockKey.of(block));
        st.owner = owner;
        st.fixed = fixed;
        byId.put(st.id, st);
        byPos.put(st.pos, st);
        return st;
    }

    public int countPots(UUID owner) {
        int n = 0;
        for (Station st : byId.values()) {
            if (st.type == StationType.POT && owner.equals(st.owner)) {
                n++;
            }
        }
        return n;
    }

    public List<ItemStack> contentsOf(Station st) {
        List<ItemStack> out = new ArrayList<>();
        ItemStack[] arr = st.inventory != null ? st.inventory.getContents() : st.contents;
        for (ItemStack it : arr) {
            if (it != null && !it.getType().isAir()) {
                out.add(it);
            }
        }
        return out;
    }

    public void remove(Station st, boolean drop) {
        plugin.displays().remove(st);
        if (st.inventory != null) {
            for (HumanEntity viewer : new ArrayList<>(st.inventory.getViewers())) {
                viewer.closeInventory();
            }
        }
        byId.remove(st.id);
        byPos.remove(st.pos);
        Location loc = st.location();
        if (loc == null) {
            return;
        }
        World w = loc.getWorld();
        Location center = loc.clone().add(0.5, 0.5, 0.5);
        if (drop) {
            if (st.type == StationType.LOCKUP) {
                for (ItemStack it : contentsOf(st)) {
                    w.dropItemNaturally(center, it);
                }
            }
            ItemStack item = plugin.items().create(st.type.itemKey, 1);
            if (item != null) {
                w.dropItemNaturally(center, item);
            }
        }
        loc.getBlock().setType(Material.AIR, false);
    }

    public boolean inTent(Station pot) {
        int r = plugin.getConfig().getInt("tent.radius", 6);
        for (Station st : byId.values()) {
            if (st.type == StationType.TENT && st.pos.world().equals(pot.pos.world())) {
                long dx = st.pos.x() - pot.pos.x();
                long dy = st.pos.y() - pot.pos.y();
                long dz = st.pos.z() - pot.pos.z();
                if (dx * dx + dy * dy + dz * dz <= (long) r * r) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- persistence

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Station st : byId.values()) {
            String p = "stations." + st.id;
            y.set(p + ".type", st.type.name());
            y.set(p + ".pos", st.pos.serialize());
            if (st.owner != null) {
                y.set(p + ".owner", st.owner.toString());
            }
            y.set(p + ".fixed", st.fixed);
            y.set(p + ".tank", st.tank);
            y.set(p + ".last-tick", st.lastTick);
            if (st.plant != null) {
                Plant pl = st.plant;
                y.set(p + ".plant.strain", pl.strain);
                y.set(p + ".plant.progress", pl.progress);
                y.set(p + ".plant.water", pl.water);
                y.set(p + ".plant.health", pl.health);
                y.set(p + ".plant.infested", pl.infested);
                y.set(p + ".plant.fertilizer", pl.fertilizer);
                y.set(p + ".plant.care-sum", pl.careSum);
                y.set(p + ".plant.care-time", pl.careTime);
                y.set(p + ".plant.decay", pl.decay);
                y.set(p + ".plant.ready", pl.ready);
                y.set(p + ".plant.dead", pl.dead);
            }
            if (st.type == StationType.LOCKUP) {
                List<String> members = new ArrayList<>();
                for (UUID m : st.members) {
                    members.add(m.toString());
                }
                y.set(p + ".members", members);
                ItemStack[] arr = st.inventory != null ? st.inventory.getContents() : st.contents;
                for (int i = 0; i < arr.length; i++) {
                    if (arr[i] != null && !arr[i].getType().isAir()) {
                        y.set(p + ".contents." + i, arr[i]);
                    }
                }
            }
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save data.yml: " + ex.getMessage());
        }
    }

    public void load() {
        byId.clear();
        byPos.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("stations");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            try {
                StationType type = StationType.valueOf(s.getString("type", ""));
                Station st = new Station(UUID.fromString(id), type, BlockKey.parse(s.getString("pos", "")));
                String owner = s.getString("owner");
                st.owner = owner == null ? null : UUID.fromString(owner);
                st.fixed = s.getBoolean("fixed");
                st.tank = s.getInt("tank");
                st.lastTick = s.getLong("last-tick", System.currentTimeMillis());
                ConfigurationSection ps = s.getConfigurationSection("plant");
                if (ps != null) {
                    Plant pl = new Plant(ps.getString("strain", ""));
                    pl.progress = ps.getDouble("progress");
                    pl.water = ps.getDouble("water", 60);
                    pl.health = ps.getDouble("health", 100);
                    pl.infested = ps.getBoolean("infested");
                    pl.fertilizer = ps.getString("fertilizer");
                    pl.careSum = ps.getDouble("care-sum");
                    pl.careTime = ps.getDouble("care-time");
                    pl.decay = ps.getDouble("decay");
                    pl.ready = ps.getBoolean("ready");
                    pl.dead = ps.getBoolean("dead");
                    st.plant = pl;
                }
                for (String m : s.getStringList("members")) {
                    st.members.add(UUID.fromString(m));
                }
                ConfigurationSection cs = s.getConfigurationSection("contents");
                if (cs != null) {
                    for (String slot : cs.getKeys(false)) {
                        int i = Integer.parseInt(slot);
                        if (i >= 0 && i < st.contents.length) {
                            st.contents[i] = cs.getItemStack(slot);
                        }
                    }
                }
                byId.put(st.id, st);
                byPos.put(st.pos, st);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping broken station " + id + ": " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + byId.size() + " stations.");
    }
}
