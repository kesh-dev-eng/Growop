package dev.growops;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class MiniGames implements Listener {
    private final GrowOpsPlugin plugin;
    private final Map<UUID, MiniGame> active = new HashMap<>();

    public MiniGames(GrowOpsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Callback gets performance 0..1, or -1 if cancelled. */
    public boolean start(Player player, MiniGameType type, Location fx, Consumer<Double> callback) {
        if (active.containsKey(player.getUniqueId())) {
            return false;
        }
        UUID id = player.getUniqueId();
        MiniGame g = new MiniGame(plugin, player, type, fx, perf -> {
            active.remove(id);
            callback.accept(perf);
        });
        active.put(id, g);
        g.start();
        return true;
    }

    /** Starts a mini-game tied to a station: blocks other players from using it meanwhile. */
    public void startAt(Player p, Station st, MiniGameType type, Consumer<Double> cb) {
        if (st.busy) {
            Util.bar(p, "<red>Someone is already using this.");
            return;
        }
        Location base = st.location();
        if (base == null) {
            return;
        }
        Location fx = base.add(0.5, 0.8, 0.5);
        st.busy = true;
        boolean ok = start(p, type, fx, perf -> {
            st.busy = false;
            if (perf < 0 || !plugin.stations().isLive(st)) {
                return;
            }
            cb.accept(perf);
        });
        if (!ok) {
            st.busy = false;
        }
    }

    public void closeAll() {
        for (MiniGame g : new ArrayList<>(active.values())) {
            g.abort();
        }
        active.clear();
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof MiniGame g) {
            e.setCancelled(true);
            if (e.getClickedInventory() != null && e.getClickedInventory().equals(e.getInventory())) {
                g.click(e.getRawSlot());
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MiniGame) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof MiniGame g) {
            g.onClose();
        }
    }
}
