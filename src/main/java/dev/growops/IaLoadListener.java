package dev.growops;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;

import java.lang.reflect.Method;

/**
 * Waits for ItemsAdder to finish loading its items. The event class is resolved by reflection
 * so the plugin does not need the ItemsAdder API at compile time.
 */
public final class IaLoadListener implements Listener {
    private final GrowOpsPlugin plugin;

    public IaLoadListener(GrowOpsPlugin plugin) {
        this.plugin = plugin;
        try {
            @SuppressWarnings("unchecked")
            Class<? extends org.bukkit.event.Event> cls =
                    (Class<? extends org.bukkit.event.Event>) Class.forName("dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent");
            Bukkit.getPluginManager().registerEvent(cls, this, org.bukkit.event.EventPriority.NORMAL,
                    (l, ev) -> plugin.startup(), plugin);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not listen for ItemsAdder load event, starting after a delay instead.");
        }
        // safety net: if the event never fires (or ItemsAdder was already loaded), start after 30 s
        Bukkit.getScheduler().runTaskLater(plugin, plugin::startup, 20L * 30);
    }

    @EventHandler
    public void unused(PluginEnableEvent e) {
    }
}
