package dev.growops;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lighting up a joint: needs a lighter in the inventory, plays a short smoking animation
 * (flame, puffs of smoke, sounds) and then applies the strain's potion effects.
 * Effect length scales with the joint's quality.
 */
public final class Smoking {
    private final GrowOpsPlugin plugin;
    private final Set<UUID> smoking = new HashSet<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public Smoking(GrowOpsPlugin plugin) {
        this.plugin = plugin;
    }

    public void light(Player p, ItemStack joint) {
        FileConfiguration cfg = plugin.getConfig();
        UUID id = p.getUniqueId();
        if ("joint_lit".equals(plugin.items().idOf(joint)) && !smoking.contains(id)) {
            String sid = plugin.items().strainOf(joint);
            ItemStack out = sid == null ? plugin.items().create("joint", 1)
                    : plugin.items().product("joint", 1, sid, plugin.items().qualityOf(joint));
            p.getInventory().setItemInMainHand(out);
            Util.bar(p, "<gray>You put it out.");
            return;
        }
        if (smoking.contains(id)) {
            return;
        }
        long now = System.currentTimeMillis();
        long until = cooldown.getOrDefault(id, 0L);
        if (now < until) {
            Util.bar(p, "<gray>Give it a minute... (" + ((until - now) / 1000 + 1) + "s)");
            return;
        }
        if (cfg.getBoolean("joint.require-lighter", true) && !hasLighter(p)) {
            Util.bar(p, "<red>You need a lighter to light this.");
            return;
        }
        Strain strain = plugin.strains().get(plugin.items().strainOf(joint));
        int quality = plugin.items().qualityOf(joint);
        if (strain == null) {
            java.util.List<Strain> all = new java.util.ArrayList<>(plugin.strains().all());
            if (all.isEmpty()) {
                return;
            }
            strain = all.get(new java.util.Random().nextInt(all.size()));
        }
        final Strain fStrain = strain;
        final int slot = p.getInventory().getHeldItemSlot();
        ItemStack litItem = plugin.items().product("joint_lit", 1, strain.id(), quality);
        if (litItem != null) {
            ItemStack rest = joint.clone();
            rest.setAmount(joint.getAmount() - 1);
            p.getInventory().setItem(slot, litItem);
            if (rest.getAmount() > 0) {
                Util.give(p, rest);
            }
        } else {
            Util.consumeMainHand(p, 1);
        }
        smoking.add(id);

        Location eye = p.getEyeLocation();
        p.getWorld().playSound(eye, Sound.ITEM_FLINTANDSTEEL_USE, 0.7f, 1.6f);
        p.getWorld().spawnParticle(Particle.FLAME, eye.clone().add(eye.getDirection().multiply(0.5)).add(0, -0.15, 0), 4, 0.03, 0.03, 0.03, 0.0);
        Util.bar(p, "<gold>You light up some <white>" + fStrain.name() + "<gold>...");

        int puffs = Math.max(2, cfg.getInt("joint.puffs", 5));
        new BukkitRunnable() {
            int n = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    if (p.isOnline()) {
                        removeLit(p, slot);
                    }
                    smoking.remove(id);
                    cancel();
                    return;
                }
                n++;
                Location e = p.getEyeLocation();
                Vector dir = e.getDirection();
                Location mouth = e.clone().add(dir.clone().multiply(0.45)).add(0, -0.2, 0);
                p.swingMainHand();
                if (n < puffs) {
                    p.getWorld().playSound(e, Sound.BLOCK_CAMPFIRE_CRACKLE, 0.5f, 1.4f);
                    p.getWorld().spawnParticle(Particle.SMOKE, mouth, 3, 0.03, 0.03, 0.03, 0.005);
                    p.getWorld().spawnParticle(Particle.FLAME, mouth, 1, 0.01, 0.01, 0.01, 0.0);
                    return;
                }
                // exhale
                p.getWorld().playSound(e, Sound.ENTITY_PLAYER_BREATH, 0.8f, 0.8f);
                p.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, mouth, 12, 0.12, 0.08, 0.12, 0.02);
                p.getWorld().spawnParticle(Particle.CLOUD, mouth, 10, 0.15, 0.1, 0.15, 0.01);
                removeLit(p, slot);
                apply(p, fStrain, quality);
                smoking.remove(id);
                cooldown.put(id, System.currentTimeMillis() + cfg.getLong("joint.cooldown-seconds", 20) * 1000L);
                cancel();
            }
        }.runTaskTimer(plugin, 12L, 12L);
    }

    private void removeLit(Player p, int slot) {
        ItemStack cur = p.getInventory().getItem(slot);
        if (cur != null && plugin.items().is(cur, "joint_lit")) {
            p.getInventory().setItem(slot, null);
            return;
        }
        ItemStack[] arr = p.getInventory().getStorageContents();
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] != null && plugin.items().is(arr[i], "joint_lit")) {
                p.getInventory().setItem(i, null);
                return;
            }
        }
    }

    private boolean hasLighter(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null) {
                continue;
            }
            if (it.getType() == Material.FLINT_AND_STEEL || plugin.items().is(it, "lighter")) {
                return true;
            }
        }
        return false;
    }

    private void apply(Player p, Strain strain, int quality) {
        FileConfiguration cfg = plugin.getConfig();
        double base = cfg.getDouble("joint.duration-seconds", 60);
        double factor = 0.5 + quality / 100.0;
        boolean levitated = false;
        StringBuilder names = new StringBuilder();

        for (String raw : strain.effects()) {
            String[] part = raw.trim().split(":");
            String name = part[0].toUpperCase(Locale.ROOT);
            int level = 1;
            double cap = cfg.getDouble("joint.max-seconds." + name, -1);
            try {
                if (part.length > 1) {
                    level = Math.max(1, Integer.parseInt(part[1]));
                }
                if (part.length > 2) {
                    cap = Double.parseDouble(part[2]);
                }
            } catch (NumberFormatException ignored) {
            }
            PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
            if (type == null) {
                plugin.getLogger().warning("Unknown effect '" + name + "' in strain " + strain.id());
                continue;
            }
            double seconds = base * factor;
            if (cap > 0) {
                seconds = Math.min(seconds, cap);
            }
            p.addPotionEffect(new PotionEffect(type, (int) Math.max(20, seconds * 20), level - 1, true, true, true));
            if (name.equals("LEVITATION")) {
                levitated = true;
            }
            if (names.length() > 0) {
                names.append("<gray>, ");
            }
            names.append("<white>").append(prettify(name)).append(level > 1 ? " " + roman(level) : "");
        }
        if (levitated && cfg.getBoolean("joint.levitation-safety", true)) {
            // so nobody falls to their death when levitation ends
            double safe = cfg.getDouble("joint.levitation-safety-seconds", 20);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, (int) (safe * 20), 0, true, false, false));
        }
        Bukkit.getScheduler().runTask(plugin, () -> Util.bar(p, "<green>" + strain.name() + " <gray>(" + quality + "%): " + names));
    }

    private static String prettify(String s) {
        StringBuilder out = new StringBuilder();
        for (String w : s.toLowerCase(Locale.ROOT).split("_")) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return out.toString();
    }

    private static String roman(int n) {
        return switch (n) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }
}
