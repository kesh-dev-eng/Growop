package dev.growops;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Renders each planted pot with display entities: a soil block that changes colour as it dries
 * and two crossed item displays for the plant, scaled up with every growth stage.
 * The entities are not persistent; they are rebuilt from the saved data on chunk load.
 */
public final class DisplayService {
    private final GrowOpsPlugin plugin;
    private final Map<UUID, Entity[]> displays = new HashMap<>();
    private final Map<UUID, Station> hitboxes = new HashMap<>();

    public DisplayService(GrowOpsPlugin plugin) {
        this.plugin = plugin;
    }

    public void refreshAll() {
        for (Station st : plugin.stations().all()) {
            refresh(st);
        }
    }

    public void refreshChunk(Chunk chunk) {
        for (Station st : plugin.stations().all()) {
            if (st.type == StationType.POT && st.plant != null
                    && st.pos.world().equals(chunk.getWorld().getUID())
                    && (st.pos.x() >> 4) == chunk.getX() && (st.pos.z() >> 4) == chunk.getZ()) {
                refresh(st);
            }
        }
    }

    public void removeChunk(Chunk chunk) {
        for (Station st : plugin.stations().all()) {
            if (st.pos.world().equals(chunk.getWorld().getUID())
                    && (st.pos.x() >> 4) == chunk.getX() && (st.pos.z() >> 4) == chunk.getZ()) {
                remove(st);
            }
        }
    }

    public void refresh(Station st) {
        if (st.type != StationType.POT) {
            return;
        }
        if (st.plant == null) {
            remove(st);
            return;
        }
        Location loc = st.location();
        if (loc == null) {
            return;
        }
        World w = loc.getWorld();
        if (!w.isChunkLoaded(st.pos.x() >> 4, st.pos.z() >> 4)) {
            return;
        }
        Plant p = st.plant;
        Strain strain = plugin.strains().get(p.strain);

        Entity[] e = displays.get(st.id);
        if (e == null || !valid(e)) {
            remove(st);
            e = new Entity[4];
            displays.put(st.id, e);
        }

        // soil
        Material soil = soilMaterial(p);
        if (!(e[0] instanceof BlockDisplay soilDisplay) || !e[0].isValid()) {
            e[0] = w.spawn(loc, BlockDisplay.class, d -> {
                d.setPersistent(false);
                d.setBlock(soil.createBlockData());
                d.setTransformation(new Transformation(new Vector3f(0.3125f, 0.28f, 0.3125f), new AxisAngle4f(), new Vector3f(0.375f, 0.1f, 0.375f), new AxisAngle4f()));
            });
        } else if (soilDisplay.getBlock().getMaterial() != soil) {
            soilDisplay.setBlock(soil.createBlockData());
        }

        // plant
        int stage = plugin.growth().stageOf(p, strain);
        String key;
        if (p.dead) {
            key = "plant_dead";
        } else if (strain != null && strain.stageItems().size() > stage && stage >= 0) {
            key = strain.stageItems().get(stage);
        } else {
            key = "plant_stage_" + Math.max(0, stage);
        }
        ItemStack stack = plugin.items().create(key, 1);
        if (stack == null) {
            stack = new ItemStack(Material.FERN);
        }
        List<Double> scales = plugin.getConfig().getDoubleList("plant.scales");
        double scale = 0.6;
        if (!scales.isEmpty()) {
            scale = scales.get(Math.max(0, Math.min(scales.size() - 1, p.dead ? Math.max(0, stage) : stage))).doubleValue();
        }
        if (p.dead) {
            scale = 0.6;
        }
        float s = (float) scale;
        float ratio = (float) plugin.getConfig().getDouble("plant.width-ratio", 0.7);
        for (int i = 0; i < 2; i++) {
            final ItemStack fs = stack;
            Transformation t = new Transformation(
                    new Vector3f(0f, s / 2f, 0f),
                    new AxisAngle4f((float) Math.toRadians(i * 90 + 45), 0f, 1f, 0f),
                    new Vector3f(s * ratio, s, s),
                    new AxisAngle4f());
            Entity cur = e[1 + i];
            if (cur instanceof ItemDisplay id && cur.isValid()) {
                id.setItemStack(fs);
                id.setTransformation(t);
            } else {
                Location at = loc.clone().add(0.5, 0.33, 0.5);
                e[1 + i] = w.spawn(at, ItemDisplay.class, d -> {
                    d.setPersistent(false);
                    d.setItemStack(fs);
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    d.setBrightness(new Display.Brightness(10, 12));
                    d.setInterpolationDuration(20);
                    d.setTransformation(t);
                });
            }
        }
        float hw = (float) Math.max(0.6, scale * ratio * 0.5);
        float hh = (float) Math.max(0.6, scale);
        if (e[3] instanceof Interaction ia && e[3].isValid()) {
            ia.setInteractionWidth(hw);
            ia.setInteractionHeight(hh);
        } else {
            Location base = loc.clone().add(0.5, 0.3, 0.5);
            Entity spawned = w.spawn(base, Interaction.class, d -> {
                d.setPersistent(false);
                d.setInteractionWidth(hw);
                d.setInteractionHeight(hh);
                d.setResponsive(true);
            });
            e[3] = spawned;
            hitboxes.put(spawned.getUniqueId(), st);
        }
    }

    /** The pot a hitbox entity belongs to, or null. */
    public Station stationOf(Entity entity) {
        return hitboxes.get(entity.getUniqueId());
    }

    private Material soilMaterial(Plant p) {
        String name;
        if (p.water > plugin.getConfig().getDouble("soil.wet-above", 60)) {
            name = plugin.getConfig().getString("soil.wet", "MUD");
        } else if (p.water > plugin.getConfig().getDouble("soil.ok-above", 25)) {
            name = plugin.getConfig().getString("soil.ok", "DIRT");
        } else {
            name = plugin.getConfig().getString("soil.dry", "COARSE_DIRT");
        }
        Material m = Material.matchMaterial(name);
        return m == null ? Material.DIRT : m;
    }

    private boolean valid(Entity[] e) {
        for (Entity en : e) {
            if (en == null || !en.isValid()) {
                return false;
            }
        }
        return true;
    }

    public void remove(Station st) {
        Entity[] e = displays.remove(st.id);
        if (e == null) {
            return;
        }
        if (e.length > 3 && e[3] != null) {
            hitboxes.remove(e[3].getUniqueId());
        }
        for (Entity en : e) {
            if (en != null && en.isValid()) {
                en.remove();
            }
        }
    }

    public void removeAll() {
        for (Entity[] e : displays.values()) {
            for (Entity en : e) {
                if (en != null && en.isValid()) {
                    en.remove();
                }
            }
        }
        displays.clear();
        hitboxes.clear();
    }
}
