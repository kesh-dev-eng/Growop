package dev.growops;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Logical items (pot, seed_og_kush, bud, ...). Each one can be backed by an ItemsAdder item
 * (configured in items.yml) and falls back to a plain vanilla item when ItemsAdder is absent.
 * ItemsAdder is accessed by reflection, so there is no compile-time dependency.
 */
public final class ItemService {

    public record ItemDef(String key, String itemsAdder, Material material, String name, List<String> lore, String base) {
    }

    private final GrowOpsPlugin plugin;
    private final Map<String, ItemDef> defs = new LinkedHashMap<>();
    private final Map<String, String> iaReverse = new HashMap<>();
    private final Map<String, String> iaKey = new HashMap<>();

    private NamespacedKey keyItem;
    private NamespacedKey keyStrain;
    private NamespacedKey keyQuality;
    private NamespacedKey keyWater;

    private boolean iaPresent;
    private Method iaGetInstance;
    private Method iaByItemStack;
    private Method iaGetItemStack;
    private Method iaGetId;

    public ItemService(GrowOpsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        keyItem = new NamespacedKey(plugin, "item");
        keyStrain = new NamespacedKey(plugin, "strain");
        keyQuality = new NamespacedKey(plugin, "quality");
        keyWater = new NamespacedKey(plugin, "water");

        iaPresent = false;
        if (plugin.getServer().getPluginManager().getPlugin("ItemsAdder") != null) {
            try {
                Class<?> cs = Class.forName("dev.lone.itemsadder.api.CustomStack");
                iaGetInstance = cs.getMethod("getInstance", String.class);
                iaByItemStack = cs.getMethod("byItemStack", ItemStack.class);
                iaGetItemStack = cs.getMethod("getItemStack");
                iaGetId = cs.getMethod("getNamespacedID");
                iaPresent = true;
            } catch (Exception ex) {
                plugin.getLogger().warning("ItemsAdder found but its API could not be hooked: " + ex.getMessage());
            }
        }

        defs.clear();
        iaReverse.clear();
        iaKey.clear();
        File f = new File(plugin.getDataFolder(), "items.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection root = y.getConfigurationSection("items");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                Material m = Material.matchMaterial(s.getString("material", "PAPER"));
                if (m == null) {
                    m = Material.PAPER;
                }
                defs.put(key, new ItemDef(key, s.getString("itemsadder", ""), m, s.getString("name", key), s.getStringList("lore"), s.getString("base")));
            }
        }
        // every strain needs a seed item
        for (Strain st : plugin.strains().all()) {
            String key = "seed_" + st.id();
            if (!defs.containsKey(key)) {
                defs.put(key, new ItemDef(key, "growops:" + key, Material.WHEAT_SEEDS, "<white>" + st.name() + " Seeds", List.of("<gray>Plant in a grow pot."), null));
            }
        }
        for (ItemDef d : defs.values()) {
            if (d.itemsAdder() != null && !d.itemsAdder().isBlank()) {
                iaReverse.put(d.itemsAdder(), d.base() != null ? d.base() : d.key());
                iaKey.put(d.itemsAdder(), d.key());
            }
        }
        plugin.getLogger().info("Loaded " + defs.size() + " items (ItemsAdder " + (iaPresent ? "hooked" : "not present, using vanilla fallbacks") + ").");
    }

    /** Item keys an admin can give (per-strain visual variants are hidden). */
    public Set<String> keys() {
        Set<String> out = new java.util.TreeSet<>();
        for (ItemDef d : defs.values()) {
            if (d.base() == null) {
                out.add(d.key());
            }
        }
        return out;
    }

    public boolean exists(String key) {
        return defs.containsKey(key);
    }

    public ItemStack create(String key, int amount) {
        return build(key, key, amount);
    }

    /** visualKey picks the model/name, logicalKey is what the plugin treats the item as. */
    private ItemStack build(String visualKey, String logicalKey, int amount) {
        ItemDef d = defs.get(visualKey);
        if (d == null) {
            return null;
        }
        ItemStack it = null;
        if (iaPresent && d.itemsAdder() != null && !d.itemsAdder().isBlank()) {
            try {
                Object cs = iaGetInstance.invoke(null, d.itemsAdder());
                if (cs != null) {
                    ItemStack base = (ItemStack) iaGetItemStack.invoke(cs);
                    if (base != null) {
                        it = base.clone();
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (it == null) {
            it = new ItemStack(d.material());
            ItemMeta m = it.getItemMeta();
            m.displayName(Text.c(d.name()));
            if (!d.lore().isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String l : d.lore()) {
                    lore.add(Text.c(l));
                }
                m.lore(lore);
            }
            it.setItemMeta(m);
        }
        it.setAmount(Math.max(1, amount));
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(keyItem, PersistentDataType.STRING, logicalKey);
        it.setItemMeta(m);
        if (logicalKey.equals("watering_can")) {
            setWater(it, plugin.getConfig().getInt("watering-can.capacity", 10));
        }
        return it;
    }

    /** Returns the logical key of an item, or null if it is not a GrowOps item. */
    public String idOf(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) {
            return null;
        }
        ItemMeta m = it.getItemMeta();
        String s = m.getPersistentDataContainer().get(keyItem, PersistentDataType.STRING);
        if (s != null) {
            return s;
        }
        if (iaPresent) {
            try {
                Object cs = iaByItemStack.invoke(null, it);
                if (cs != null) {
                    String ns = (String) iaGetId.invoke(cs);
                    return iaReverse.get(ns);
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public boolean is(ItemStack it, String key) {
        return key.equals(idOf(it));
    }

    /** Product item (bud, bag, ground, joint, brick) that remembers strain and quality. */
    public ItemStack product(String key, int amount, String strain, int quality) {
        String visual = defs.containsKey(key + "_" + strain) ? key + "_" + strain : key;
        ItemStack it = build(visual, key, amount);
        if (it == null) {
            return null;
        }
        ItemMeta m = it.getItemMeta();
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        pdc.set(keyStrain, PersistentDataType.STRING, strain);
        pdc.set(keyQuality, PersistentDataType.INTEGER, quality);
        Strain s = plugin.strains().get(strain);
        String sn = s == null ? strain : s.name();
        ItemDef def = defs.get(key);
        String label = def == null ? key : def.name().replaceAll("<[^>]+>", "");
        String hex = s == null ? "FFFFFF" : String.format("%02X%02X%02X", s.color().getRed(), s.color().getGreen(), s.color().getBlue());
        m.displayName(Text.c("<#" + hex + ">" + sn + " " + label));
        List<Component> lore = new ArrayList<>();
        if (m.lore() != null) {
            lore.addAll(m.lore());
        }
        lore.add(Text.c("<gray>Strain: <white>" + sn));
        lore.add(Text.c("<gray>Quality: " + qualityColor(quality) + quality + "%"));
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    public ItemStack seed(String strain, int amount) {
        return create("seed_" + strain, amount);
    }

    public String strainOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        String s = it.getItemMeta().getPersistentDataContainer().get(keyStrain, PersistentDataType.STRING);
        if (s != null) {
            return s;
        }
        // items taken straight from ItemsAdder (e.g. /iaget growops:joint_og_kush) carry the strain in their id
        if (iaPresent) {
            try {
                Object cs = iaByItemStack.invoke(null, it);
                if (cs != null) {
                    String defKey = iaKey.get((String) iaGetId.invoke(cs));
                    if (defKey != null) {
                        for (Strain st : plugin.strains().all()) {
                            if (defKey.endsWith("_" + st.id())) {
                                return st.id();
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public int qualityOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return 50;
        }
        Integer q = it.getItemMeta().getPersistentDataContainer().get(keyQuality, PersistentDataType.INTEGER);
        return q == null ? 50 : q;
    }

    public int getWater(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return 0;
        }
        Integer w = it.getItemMeta().getPersistentDataContainer().get(keyWater, PersistentDataType.INTEGER);
        return w == null ? 0 : w;
    }

    public void setWater(ItemStack it, int water) {
        int cap = plugin.getConfig().getInt("watering-can.capacity", 10);
        int w = Math.max(0, Math.min(cap, water));
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(keyWater, PersistentDataType.INTEGER, w);
        List<Component> lore = new ArrayList<>();
        lore.add(Text.c("<gray>Water: <aqua>" + w + "<gray>/" + cap));
        lore.add(Text.c("<dark_gray>Right-click water to refill."));
        m.lore(lore);
        it.setItemMeta(m);
    }

    public static String qualityColor(int q) {
        if (q >= 80) {
            return "<green>";
        }
        if (q >= 50) {
            return "<yellow>";
        }
        return "<red>";
    }
}
