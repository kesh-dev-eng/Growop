package dev.growops;

import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StrainRegistry {
    private final GrowOpsPlugin plugin;
    private final Map<String, Strain> map = new LinkedHashMap<>();

    public StrainRegistry(GrowOpsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        map.clear();
        File f = new File(plugin.getDataFolder(), "strains.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection root = y.getConfigurationSection("strains");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            Color color = Color.fromRGB(76, 175, 80);
            try {
                color = Color.fromRGB(Integer.parseInt(s.getString("color", "4CAF50").replace("#", ""), 16));
            } catch (NumberFormatException ignored) {
            }
            List<String> stageItems = new ArrayList<>(s.getStringList("stage-items"));
            map.put(id, new Strain(
                    id,
                    s.getString("name", id),
                    color,
                    Math.max(1, s.getInt("grow-minutes", 20)),
                    s.getDouble("water-per-minute", 3),
                    s.getInt("yield.min", 3),
                    Math.max(s.getInt("yield.min", 3), s.getInt("yield.max", 6)),
                    s.getInt("seeds.min", 0),
                    Math.max(s.getInt("seeds.min", 0), s.getInt("seeds.max", 2)),
                    s.getDouble("mutation.chance", 0),
                    s.getString("mutation.strain", null),
                    Math.max(1, s.getInt("decay-minutes", 30)),
                    s.getBoolean("requires-optimal", false),
                    s.getInt("base-quality", 50),
                    stageItems));
        }
        plugin.getLogger().info("Loaded " + map.size() + " strains.");
    }

    public Strain get(String id) {
        return id == null ? null : map.get(id);
    }

    public Collection<Strain> all() {
        return map.values();
    }
}
