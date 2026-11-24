package dev.growops;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Processing chain: bud -> bag / ground weed -> joint / brick.
 * Strain and quality are carried from the inputs to the outputs.
 */
public final class Processing implements Listener {
    private final GrowOpsPlugin plugin;

    public static final class Menu implements InventoryHolder {
        final Station station;
        Inventory inv;

        Menu(Station station) {
            this.station = station;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private record Taken(int avgQuality, String strain) {
    }

    public Processing(GrowOpsPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ menu

    public void openMenu(Player p, Station st) {
        Menu menu = new Menu(st);
        Inventory inv = Bukkit.createInventory(menu, 27, Text.c("<gold>Processing Table"));
        menu.inv = inv;
        ItemStack filler = icon(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }
        int bag = plugin.getConfig().getInt("processing.bag.buds-per-bag", 5);
        int grindBuds = plugin.getConfig().getInt("processing.grind.buds", 1);
        int grindYield = plugin.getConfig().getInt("processing.grind.yield", 2);
        int rollGround = plugin.getConfig().getInt("processing.roll.ground", 1);
        inv.setItem(11, icon(Material.PAPER, "<white>Bag buds",
                List.of("<gray>Needs: <white>" + bag + " buds + 1 empty bag", "<gray>Makes: <white>1 bagged weed")));
        inv.setItem(13, icon(Material.GRINDSTONE, "<dark_green>Grind buds",
                List.of("<gray>Needs: <white>" + grindBuds + " bud", "<gray>Makes: <white>" + grindYield + " ground weed")));
        inv.setItem(15, icon(Material.STICK, "<yellow>Roll joint",
                List.of("<gray>Needs: <white>" + rollGround + " ground weed + 1 rolling paper", "<gray>Makes: <white>1 joint")));
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu menu)) {
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getInventory())) {
            return;
        }
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        int slot = e.getRawSlot();
        if (slot != 11 && slot != 13 && slot != 15) {
            return;
        }
        p.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.stations().isLive(menu.station)) {
                return;
            }
            switch (slot) {
                case 11 -> bag(p, menu.station);
                case 13 -> grind(p, menu.station);
                default -> roll(p, menu.station);
            }
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ recipes

    public void bag(Player p, Station st) {
        int need = plugin.getConfig().getInt("processing.bag.buds-per-bag", 5);
        process(p, st, MiniGameType.BAGGING, "bud", need, "bag_empty", 1, "weed_bag", 1);
    }

    public void grind(Player p, Station st) {
        int need = plugin.getConfig().getInt("processing.grind.buds", 1);
        int out = plugin.getConfig().getInt("processing.grind.yield", 2);
        process(p, st, MiniGameType.GRINDING, "bud", need, null, 0, "ground", out);
    }

    public void roll(Player p, Station st) {
        int need = plugin.getConfig().getInt("processing.roll.ground", 1);
        process(p, st, MiniGameType.ROLLING, "ground", need, "rolling_paper", 1, "joint", 1);
    }

    public void press(Player p, Station st) {
        int need = plugin.getConfig().getInt("processing.press.ground-per-brick", 4);
        process(p, st, MiniGameType.PRESSING, "ground", need, null, 0, "brick", 1);
    }

    private void process(Player p, Station st, MiniGameType type, String mainKey, int mainNeed,
                         String extraKey, int extraNeed, String outKey, int outAmount) {
        if (peek(p, mainKey, mainNeed) == null) {
            Util.bar(p, "<red>You need " + mainNeed + "x " + label(mainKey) + ".");
            return;
        }
        if (extraKey != null && peek(p, extraKey, extraNeed) == null) {
            Util.bar(p, "<red>You need " + extraNeed + "x " + label(extraKey) + ".");
            return;
        }
        plugin.minigames().startAt(p, st, type, perf -> {
            if (perf < plugin.getConfig().getDouble("minigame-fail-threshold", 0.2)) {
                Util.bar(p, "<red>You fumbled it. Nothing was used, try again.");
                return;
            }
            Taken main = peek(p, mainKey, mainNeed);
            if (main == null || (extraKey != null && peek(p, extraKey, extraNeed) == null)) {
                Util.bar(p, "<red>The ingredients are gone.");
                return;
            }
            remove(p, mainKey, mainNeed);
            if (extraKey != null) {
                remove(p, extraKey, extraNeed);
            }
            int q = (int) Math.max(1, Math.min(100, Math.round(main.avgQuality() * (0.9 + 0.2 * perf))));
            ItemStack out = plugin.items().product(outKey, outAmount, main.strain(), q);
            Util.give(p, out);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
            Util.bar(p, "<green>Made " + outAmount + "x " + label(outKey) + " <gray>(quality " + q + "%)");
        });
    }

    // ------------------------------------------------------------------ inventory helpers

    private Taken peek(Player p, String key, int need) {
        int have = 0;
        long qsum = 0;
        String strain = null;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it == null || !plugin.items().is(it, key)) {
                continue;
            }
            int take = Math.min(it.getAmount(), need - have);
            have += take;
            qsum += (long) plugin.items().qualityOf(it) * take;
            if (strain == null) {
                strain = plugin.items().strainOf(it);
            }
            if (have >= need) {
                break;
            }
        }
        if (have < need || need <= 0) {
            return need <= 0 ? new Taken(50, "unknown") : null;
        }
        return new Taken((int) Math.round(qsum / (double) need), strain == null ? "unknown" : strain);
    }

    private void remove(Player p, String key, int need) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] arr = inv.getStorageContents();
        int left = need;
        for (int i = 0; i < arr.length && left > 0; i++) {
            ItemStack it = arr[i];
            if (it == null || !plugin.items().is(it, key)) {
                continue;
            }
            int t = Math.min(left, it.getAmount());
            if (t >= it.getAmount()) {
                arr[i] = null;
            } else {
                it.setAmount(it.getAmount() - t);
            }
            left -= t;
        }
        inv.setStorageContents(arr);
    }

    private static String label(String key) {
        return key.replace('_', ' ');
    }

    private static ItemStack icon(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.c(name));
        if (!lore.isEmpty()) {
            meta.lore(lore.stream().map(Text::c).toList());
        }
        it.setItemMeta(meta);
        return it;
    }
}
