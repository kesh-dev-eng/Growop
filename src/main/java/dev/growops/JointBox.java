package dev.growops;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Portable storage for joints. The contents live inside the item itself (its persistent data),
 * so the box can be carried, dropped, traded or put in a chest with its joints. Holds at most
 * joint-box.capacity joints (64 by default); only joints are accepted.
 */
public final class JointBox implements Listener {
    private static final int SLOTS = 27;

    private final GrowOpsPlugin plugin;
    private final NamespacedKey keyId;
    private final NamespacedKey keyData;
    private final Map<UUID, UUID> open = new HashMap<>();

    public static final class Holder implements InventoryHolder {
        final UUID boxId;
        Inventory inv;

        Holder(UUID boxId) {
            this.boxId = boxId;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    public JointBox(GrowOpsPlugin plugin) {
        this.plugin = plugin;
        this.keyId = new NamespacedKey(plugin, "boxid");
        this.keyData = new NamespacedKey(plugin, "boxdata");
    }

    private int capacity() {
        return Math.max(1, plugin.getConfig().getInt("joint-box.capacity", 64));
    }

    // ------------------------------------------------------------------ use

    /** Right-click with the box in hand: open it, or take one joint out when sneaking. */
    public void use(Player p, ItemStack box) {
        ItemMeta meta = box.getItemMeta();
        UUID id = null;
        String raw = meta.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
        if (raw != null) {
            try {
                id = UUID.fromString(raw);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (id == null) {
            id = UUID.randomUUID();
            meta.getPersistentDataContainer().set(keyId, PersistentDataType.STRING, id.toString());
            box.setItemMeta(meta);
            p.getInventory().setItemInMainHand(box);
        }
        if (open.containsKey(p.getUniqueId())) {
            return;
        }
        ItemStack[] contents = read(box);
        if (p.isSneaking()) {
            pullOne(p, box, contents);
            return;
        }
        Holder holder = new Holder(id);
        Inventory inv = Bukkit.createInventory(holder, SLOTS, Text.c("<gold>Joint Box <gray>(max " + capacity() + ")"));
        holder.inv = inv;
        inv.setContents(contents);
        open.put(p.getUniqueId(), id);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 1.2f);
    }

    private void pullOne(Player p, ItemStack box, ItemStack[] contents) {
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType().isAir()) {
                continue;
            }
            ItemStack one = it.clone();
            one.setAmount(1);
            if (it.getAmount() <= 1) {
                contents[i] = null;
            } else {
                it.setAmount(it.getAmount() - 1);
            }
            write(box, contents);
            p.getInventory().setItemInMainHand(box);
            Util.give(p, one);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.4f);
            Util.bar(p, "<gold>Took out a joint. <gray>" + count(contents) + "/" + capacity() + " left");
            return;
        }
        Util.bar(p, "<gray>The box is empty.");
    }

    // ------------------------------------------------------------------ storage in the item

    private ItemStack[] read(ItemStack box) {
        ItemStack[] out = new ItemStack[SLOTS];
        String data = box.getItemMeta().getPersistentDataContainer().get(keyData, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) {
            return out;
        }
        try {
            YamlConfiguration y = new YamlConfiguration();
            y.loadFromString(new String(Base64.getDecoder().decode(data), java.nio.charset.StandardCharsets.UTF_8));
            List<?> list = y.getList("items");
            if (list != null) {
                for (int i = 0; i < Math.min(SLOTS, list.size()); i++) {
                    Object o = list.get(i);
                    out[i] = o instanceof ItemStack is ? is : null;
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not read a joint box: " + ex.getMessage());
        }
        return out;
    }

    private void write(ItemStack box, ItemStack[] contents) {
        YamlConfiguration y = new YamlConfiguration();
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            list.add(i < contents.length ? contents[i] : null);
        }
        y.set("items", list);
        String data = Base64.getEncoder().encodeToString(y.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ItemMeta m = box.getItemMeta();
        m.getPersistentDataContainer().set(keyData, PersistentDataType.STRING, data);

        int total = count(contents);
        Map<String, Integer> byStrain = new TreeMap<>();
        for (ItemStack it : contents) {
            if (it == null || it.getType().isAir()) {
                continue;
            }
            Strain s = plugin.strains().get(plugin.items().strainOf(it));
            byStrain.merge(s == null ? "Plain" : s.name(), it.getAmount(), Integer::sum);
        }
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Text.c("<gray>Joints: <white>" + total + "<gray>/" + capacity()));
        for (Map.Entry<String, Integer> en : byStrain.entrySet()) {
            lore.add(Text.c("<dark_gray> - <gray>" + en.getKey() + ": <white>" + en.getValue()));
        }
        lore.add(Text.c("<dark_gray>Right-click: open. Sneak + right-click: take one."));
        m.lore(lore);
        box.setItemMeta(m);
    }

    private int count(ItemStack[] contents) {
        int n = 0;
        for (ItemStack it : contents) {
            if (it != null && !it.getType().isAir()) {
                n += it.getAmount();
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ GUI rules

    private boolean isBox(ItemStack it) {
        return it != null && plugin.items().is(it, "joint_box");
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        boolean ours = e.getInventory().getHolder() instanceof Holder;
        if (!ours) {
            return;
        }
        // boxes can't be moved around while one is open (no putting a box in a box, no dropping the open one)
        if (isBox(e.getCurrentItem()) || isBox(e.getCursor())) {
            e.setCancelled(true);
            return;
        }
        if (e.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY || e.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND) {
            e.setCancelled(true);
            return;
        }
        if (e.getWhoClicked() instanceof Player p) {
            sanitizeLater(p, e.getInventory());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder && e.getWhoClicked() instanceof Player p) {
            if (isBox(e.getOldCursor())) {
                e.setCancelled(true);
                return;
            }
            sanitizeLater(p, e.getInventory());
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (open.containsKey(e.getPlayer().getUniqueId()) && isBox(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h) || !(e.getPlayer() instanceof Player p)) {
            return;
        }
        sanitize(p, e.getInventory());
        open.remove(p.getUniqueId());
        ItemStack[] contents = e.getInventory().getContents();
        ItemStack box = findBox(p, h.boxId);
        if (box == null) {
            // the box is gone (should not happen), so the joints are returned instead of lost
            for (ItemStack it : contents) {
                if (it != null && !it.getType().isAir()) {
                    Util.give(p, it);
                }
            }
            return;
        }
        write(box, contents);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 0.9f);
        Util.bar(p, "<gold>Joint Box <gray>" + count(contents) + "/" + capacity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (open.containsKey(e.getPlayer().getUniqueId())) {
            e.getPlayer().closeInventory();
        }
    }

    private ItemStack findBox(Player p, UUID id) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || !isBox(it)) {
                continue;
            }
            String raw = it.getItemMeta().getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
            if (id.toString().equals(raw)) {
                return it;
            }
        }
        return null;
    }

    private void sanitizeLater(Player p, Inventory inv) {
        Bukkit.getScheduler().runTask(plugin, () -> sanitize(p, inv));
    }

    /** Anything that is not a joint, or goes over the capacity, goes back to the player. */
    private void sanitize(Player p, Inventory inv) {
        int total = 0;
        int cap = capacity();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) {
                continue;
            }
            if (!"joint".equals(plugin.items().idOf(it))) {
                inv.setItem(i, null);
                Util.give(p, it);
                continue;
            }
            int allowed = Math.max(0, cap - total);
            if (it.getAmount() > allowed) {
                ItemStack back = it.clone();
                back.setAmount(it.getAmount() - allowed);
                if (allowed == 0) {
                    inv.setItem(i, null);
                } else {
                    it.setAmount(allowed);
                    inv.setItem(i, it);
                }
                Util.give(p, back);
                Util.bar(p, "<red>The box only holds " + cap + " joints.");
                total += allowed;
            } else {
                total += it.getAmount();
            }
        }
    }
}
