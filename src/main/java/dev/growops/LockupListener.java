package dev.growops;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class LockupListener implements Listener {
    private final GrowOpsPlugin plugin;

    public LockupListener(GrowOpsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof StationListener.LockupHolder h && e.getPlayer() instanceof Player p) {
            p.playSound(p.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.8f, 1f);
            // keep the cached snapshot in sync for persistence
            if (h.inv != null) {
                h.station.contents = h.inv.getContents();
            }
        }
    }
}
