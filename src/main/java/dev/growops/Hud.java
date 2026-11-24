package dev.growops;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.RayTraceResult;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Optional action-bar readout of the pot the player is looking at. Off by default, toggle with /growops hud. */
public final class Hud implements Listener {
    private final GrowOpsPlugin plugin;
    private final Set<UUID> enabled = new HashSet<>();
    private final Set<UUID> disabled = new HashSet<>();

    public Hud(GrowOpsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean toggle(Player p) {
        UUID id = p.getUniqueId();
        boolean on = !isOn(p);
        if (on) {
            enabled.add(id);
            disabled.remove(id);
        } else {
            enabled.remove(id);
            disabled.add(id);
        }
        return on;
    }

    private boolean isOn(Player p) {
        UUID id = p.getUniqueId();
        if (enabled.contains(id)) {
            return true;
        }
        return !disabled.contains(id) && plugin.getConfig().getBoolean("hud.default-enabled", false);
    }

    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isOn(p) || !p.hasPermission("growops.hud")) {
                continue;
            }
            RayTraceResult r = p.rayTraceBlocks(5, FluidCollisionMode.NEVER);
            if (r == null || r.getHitBlock() == null) {
                continue;
            }
            Block b = r.getHitBlock();
            Station st = plugin.stations().get(b);
            if (st == null) {
                continue;
            }
            if (st.type == StationType.POT) {
                p.sendActionBar(plugin.growth().describe(st));
            } else if (st.type == StationType.WATERER) {
                p.sendActionBar(Text.c("<aqua>Auto-Waterer <gray>tank " + st.tank + "/" + plugin.getConfig().getInt("waterer.max-tank", 40)));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        enabled.remove(e.getPlayer().getUniqueId());
        disabled.remove(e.getPlayer().getUniqueId());
    }
}
