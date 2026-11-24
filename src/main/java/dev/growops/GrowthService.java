package dev.growops;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class GrowthService {
    private final GrowOpsPlugin plugin;
    private final Random rnd = new Random();

    public GrowthService(GrowOpsPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ main tick

    public void tick() {
        FileConfiguration cfg = plugin.getConfig();
        StationManager sm = plugin.stations();
        long now = System.currentTimeMillis();
        double maxDt = cfg.getDouble("growth.max-catchup-minutes", 30) * 60.0;
        List<Station> list = new ArrayList<>(sm.all());

        for (Station st : list) {
            if (st.type == StationType.WATERER && st.tank > 0) {
                runWaterer(st, list);
            }
        }
        for (Station st : list) {
            if (st.type != StationType.POT) {
                continue;
            }
            if (st.plant == null) {
                st.lastTick = now;
                continue;
            }
            double dt = Math.min(maxDt, (now - st.lastTick) / 1000.0);
            st.lastTick = now;
            if (dt <= 0) {
                continue;
            }
            update(st, dt);
            plugin.displays().refresh(st);
        }
    }

    private void runWaterer(Station waterer, List<Station> all) {
        FileConfiguration cfg = plugin.getConfig();
        int radius = cfg.getInt("waterer.radius", 4);
        double below = cfg.getDouble("waterer.refill-below", 50);
        for (Station pot : all) {
            if (waterer.tank <= 0) {
                return;
            }
            if (pot.type != StationType.POT || pot.plant == null || pot.plant.dead) {
                continue;
            }
            if (!pot.pos.world().equals(waterer.pos.world())) {
                continue;
            }
            long dx = pot.pos.x() - waterer.pos.x();
            long dy = pot.pos.y() - waterer.pos.y();
            long dz = pot.pos.z() - waterer.pos.z();
            if (dx * dx + dy * dy + dz * dz > (long) radius * radius) {
                continue;
            }
            if (pot.plant.water < below) {
                pot.plant.water = 100;
                waterer.tank--;
                Location loc = pot.location();
                if (loc != null && loc.getWorld().isChunkLoaded(pot.pos.x() >> 4, pot.pos.z() >> 4)) {
                    loc.getWorld().spawnParticle(Particle.SPLASH, loc.clone().add(0.5, 0.8, 0.5), 14, 0.2, 0.1, 0.2, 0.0);
                }
            }
        }
    }

    private void update(Station st, double dt) {
        FileConfiguration cfg = plugin.getConfig();
        Plant p = st.plant;
        Strain s = plugin.strains().get(p.strain);
        if (s == null || p.dead) {
            return;
        }
        boolean optimal = plugin.stations().inTent(st);
        double waterMult = optimal ? cfg.getDouble("tent.water-multiplier", 0.8) : 1.0;

        p.water = Math.max(0, p.water - s.waterPerMinute() / 60.0 * dt * waterMult);
        if (p.water <= 0) {
            p.health -= cfg.getDouble("plant.dehydrate-damage-per-minute", 4) / 60.0 * dt;
        }
        if (p.infested) {
            p.health -= cfg.getDouble("pests.damage-per-minute", 4) / 60.0 * dt;
        }
        if (p.water >= 30 && !p.infested) {
            p.health = Math.min(100, p.health + cfg.getDouble("plant.regen-per-minute", 2) / 60.0 * dt);
        }
        if (p.health <= 0) {
            p.health = 0;
            p.dead = true;
            p.infested = false;
            return;
        }

        double growTime = s.growMinutes() * 60.0;
        if (!p.ready) {
            boolean canGrow = !(s.requiresOptimal() && !optimal);
            if (canGrow && p.water >= 20) {
                double mult = fert(p.fertilizer, "speed", 1.0)
                        * (optimal ? cfg.getDouble("tent.speed-multiplier", 1.25) : 1.0)
                        * (p.infested ? cfg.getDouble("pests.growth-multiplier", 0.5) : 1.0);
                p.progress += dt * mult;
            }
            double waterScore = (p.water >= 40 && p.water <= 95) ? 1.0 : (p.water >= 20 ? 0.6 : 0.2);
            double care = waterScore * (p.health / 100.0) * (p.infested ? 0.5 : 1.0);
            p.careSum += care * dt;
            p.careTime += dt;

            if (!p.infested && p.progress / growTime > 0.1) {
                double chance = cfg.getDouble("pests.chance-per-minute", 0.04)
                        * fert(p.fertilizer, "pest-chance", 1.0)
                        * (optimal ? 0.5 : 1.0) * dt / 60.0;
                if (rnd.nextDouble() < chance) {
                    p.infested = true;
                }
            }
            if (p.progress >= growTime) {
                p.progress = growTime;
                p.ready = true;
                p.decay = 0;
            }
        } else {
            p.decay += dt;
            if (p.decay >= s.decayMinutes() * 60.0) {
                p.dead = true;
                p.infested = false;
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    public double fert(String key, String field, double def) {
        if (key == null) {
            return def;
        }
        return plugin.getConfig().getDouble("fertilizers." + key + "." + field, def);
    }

    /** -1 for dead, 0 (seed) up to stages-1 (ready to harvest). */
    public int stageOf(Plant p, Strain s) {
        int stages = plugin.getConfig().getInt("growth.stages", 5);
        if (p.dead) {
            return -1;
        }
        if (p.ready || s == null) {
            return stages - 1;
        }
        double frac = Math.min(0.999, p.progress / (s.growMinutes() * 60.0));
        return Math.min(stages - 2, (int) (frac * (stages - 1)));
    }

    public double growthFraction(Plant p, Strain s) {
        if (p.ready) {
            return 1.0;
        }
        return s == null ? 0 : Math.min(1.0, p.progress / (s.growMinutes() * 60.0));
    }

    public int quality(Plant p, Strain s, boolean optimal, double perf) {
        FileConfiguration cfg = plugin.getConfig();
        double careAvg = p.careTime > 0 ? p.careSum / p.careTime : 0.5;
        double q = s.baseQuality() + (careAvg - 0.6) * 50.0 + (perf - 0.5) * 20.0;
        if (optimal) {
            q += cfg.getDouble("tent.quality-bonus", 8);
        }
        q += fert(p.fertilizer, "quality-bonus", 0);
        q -= Math.min(1.0, p.decay / (s.decayMinutes() * 60.0)) * 30.0;
        return (int) Math.max(1, Math.min(100, Math.round(q)));
    }

    public Component describe(Station st) {
        Plant p = st.plant;
        if (p == null) {
            return Text.c("<gray>Empty pot");
        }
        Strain s = plugin.strains().get(p.strain);
        String name = s == null ? p.strain : s.name();
        if (p.dead) {
            return Text.c("<dark_gray>" + name + " - withered <gray>(sneak + click to clear)");
        }
        int stages = plugin.getConfig().getInt("growth.stages", 5);
        int stage = stageOf(p, s);
        StringBuilder sb = new StringBuilder();
        sb.append("<green>").append(name);
        sb.append(" <dark_gray>| <white>").append(p.ready ? "Ready to harvest" : "Stage " + (stage + 1) + "/" + stages
                + " (" + (int) (growthFraction(p, s) * 100) + "%)");
        sb.append(" <dark_gray>| <aqua>Water ").append((int) p.water).append("%");
        sb.append(" <dark_gray>| <red>Health ").append((int) p.health).append("%");
        if (p.infested) {
            sb.append(" <dark_gray>| <dark_red>Pests!");
        }
        if (s != null && s.requiresOptimal() && !plugin.stations().inTent(st)) {
            sb.append(" <dark_gray>| <yellow>Needs a grow tent");
        }
        if (p.fertilizer != null) {
            sb.append(" <dark_gray>| <gold>Fertilized");
        }
        return Text.c(sb.toString());
    }

    // ------------------------------------------------------------------ visual cues

    /** Called every second: pests smoke, harvest-ready plants sparkle in the strain colour. */
    public void particles() {
        for (Station st : plugin.stations().all()) {
            if (st.type != StationType.POT || st.plant == null || st.plant.dead) {
                continue;
            }
            Location loc = st.location();
            if (loc == null) {
                continue;
            }
            World w = loc.getWorld();
            if (!w.isChunkLoaded(st.pos.x() >> 4, st.pos.z() >> 4)) {
                continue;
            }
            Plant p = st.plant;
            Location top = loc.clone().add(0.5, 0.7, 0.5);
            if (p.infested) {
                w.spawnParticle(Particle.SMOKE, top, 4, 0.2, 0.2, 0.2, 0.01);
            }
            if (p.ready) {
                Strain s = plugin.strains().get(p.strain);
                if (s != null) {
                    w.spawnParticle(Particle.DUST, top.clone().add(0, 0.3, 0), 3, 0.25, 0.3, 0.25, 0.0, new Particle.DustOptions(s.color(), 1.2f));
                }
            }
        }
    }
}
