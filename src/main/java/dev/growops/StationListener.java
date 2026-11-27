package dev.growops;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

public final class StationListener implements Listener {
    private final GrowOpsPlugin plugin;
    private final Random rnd = new Random();

    public static final class LockupHolder implements InventoryHolder {
        public final Station station;
        public Inventory inv;

        LockupHolder(Station station) {
            this.station = station;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    public StationListener(GrowOpsPlugin plugin) {
        this.plugin = plugin;
    }

    // ================================================================== interaction

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_BLOCK && a != Action.RIGHT_CLICK_AIR) {
            return;
        }
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        String key = plugin.items().idOf(hand);

        // light up a joint
        if ("joint".equals(key) || "joint_lit".equals(key)) {
            e.setCancelled(true);
            plugin.smoking().light(p, hand);
            return;
        }

        // refill the watering can from a water source
        if ("watering_can".equals(key)) {
            Block target = p.getTargetBlockExact(5, FluidCollisionMode.ALWAYS);
            if (target != null && target.getType() == Material.WATER) {
                e.setCancelled(true);
                int cap = plugin.getConfig().getInt("watering-can.capacity", 10);
                plugin.items().setWater(hand, cap);
                p.getInventory().setItemInMainHand(hand);
                p.playSound(p.getLocation(), Sound.ITEM_BUCKET_FILL, 0.8f, 1f);
                Util.bar(p, "<aqua>Watering can refilled.");
                return;
            }
        }
        if (a != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block b = e.getClickedBlock();
        if (b == null) {
            return;
        }

        Station st = plugin.stations().get(b);
        if (st != null) {
            e.setCancelled(true);
            useStation(p, st, hand, key);
            return;
        }

        GrowOp door = plugin.ops().atDoor(b);
        if (door != null) {
            e.setCancelled(true);
            plugin.ops().useDoor(p, door);
            return;
        }
        GrowOp sw = plugin.ops().atSwitch(b);
        if (sw != null) {
            e.setCancelled(true);
            plugin.ops().useSwitch(p, sw);
            return;
        }

        StationType type = StationType.byItemKey(key);
        if (type != null) {
            e.setCancelled(true);
            place(p, b, e.getBlockFace(), hand, type);
        }
    }

    private void useStation(Player p, Station st, ItemStack hand, String key) {
        if (!p.hasPermission("growops.use")) {
            Util.bar(p, "<red>You can't use this.");
            return;
        }
        switch (st.type) {
            case POT -> usePot(p, st, key);
            case TENT -> Util.bar(p, "<green>Grow Tent <gray>- pots within " + plugin.getConfig().getInt("tent.radius", 6)
                    + " blocks grow in optimal conditions.");
            case WATERER -> useWaterer(p, st, hand);
            case PROCESSING -> plugin.processing().openMenu(p, st);
            case PRESS -> plugin.processing().press(p, st);
            case LOCKUP -> useLockup(p, st);
        }
    }

    // ------------------------------------------------------------------ pot

    private boolean failed(double perf) {
        return perf < plugin.getConfig().getDouble("minigame-fail-threshold", 0.2);
    }

    private void usePot(Player p, Station st, String key) {
        Plant pl = st.plant;
        if (pl == null) {
            if (key != null && key.startsWith("seed_")) {
                Strain s = plugin.strains().get(key.substring(5));
                if (s == null) {
                    Util.bar(p, "<red>Unknown strain.");
                    return;
                }
                Util.consumeMainHand(p, 1);
                st.plant = new Plant(s.id());
                st.lastTick = System.currentTimeMillis();
                plugin.displays().refresh(st);
                Location l = st.location().add(0.5, 0.5, 0.5);
                l.getWorld().playSound(l, Sound.ITEM_CROP_PLANT, 1f, 1f);
                Util.bar(p, "<green>Planted <white>" + s.name());
            } else {
                Util.bar(p, "<gray>Empty pot. Right-click with seeds to plant.");
            }
            return;
        }
        if (pl.dead) {
            if (p.isSneaking()) {
                st.plant = null;
                plugin.displays().refresh(st);
                Util.bar(p, "<gray>Pot cleared.");
            } else {
                p.sendActionBar(plugin.growth().describe(st));
            }
            return;
        }
        Strain strain = plugin.strains().get(pl.strain);
        if (strain == null) {
            Util.bar(p, "<red>This strain no longer exists. Sneak + click to clear.");
            if (p.isSneaking()) {
                st.plant = null;
                plugin.displays().refresh(st);
            }
            return;
        }
        if ("watering_can".equals(key)) {
            water(p, st, pl);
        } else if (key != null && key.startsWith("fertilizer_")) {
            fertilize(p, st, pl, strain, key);
        } else if ("pesticide".equals(key)) {
            spray(p, st, pl);
        } else if (pl.ready) {
            harvest(p, st, pl, strain);
        } else {
            p.sendActionBar(plugin.growth().describe(st));
        }
    }

    private void water(Player p, Station st, Plant pl) {
        if (plugin.items().getWater(p.getInventory().getItemInMainHand()) <= 0) {
            Util.bar(p, "<red>The can is empty. Right-click water to refill it.");
            return;
        }
        if (pl.water >= 95) {
            Util.bar(p, "<aqua>The soil is already soaked.");
            return;
        }
        plugin.minigames().startAt(p, st, MiniGameType.WATERING, perf -> {
            if (failed(perf)) {
                Util.bar(p, "<red>You spilled it. Try again.");
                return;
            }
            ItemStack cur = p.getInventory().getItemInMainHand();
            if (!"watering_can".equals(plugin.items().idOf(cur)) || plugin.items().getWater(cur) <= 0 || st.plant != pl) {
                return;
            }
            plugin.items().setWater(cur, plugin.items().getWater(cur) - 1);
            p.getInventory().setItemInMainHand(cur);
            double add = plugin.getConfig().getDouble("watering-can.water-per-use", 45) * (0.6 + 0.4 * perf);
            pl.water = Math.min(100, pl.water + add);
            plugin.displays().refresh(st);
            Util.bar(p, "<aqua>Watered. <gray>Soil moisture " + (int) pl.water + "%");
        });
    }

    private void fertilize(Player p, Station st, Plant pl, Strain strain, String key) {
        if (pl.fertilizer != null) {
            Util.bar(p, "<red>This plant is already fertilized.");
            return;
        }
        double max = plugin.getConfig().getDouble("fertilizer-max-growth", 0.30);
        if (plugin.growth().growthFraction(pl, strain) > max) {
            Util.bar(p, "<red>Too late. Fertilizer only works in the first " + (int) (max * 100) + "% of growth.");
            return;
        }
        plugin.minigames().startAt(p, st, MiniGameType.FERTILIZING, perf -> {
            if (failed(perf)) {
                Util.bar(p, "<red>You fumbled it. Try again.");
                return;
            }
            if (!key.equals(plugin.items().idOf(p.getInventory().getItemInMainHand())) || st.plant != pl || pl.fertilizer != null || pl.dead) {
                return;
            }
            Util.consumeMainHand(p, 1);
            pl.fertilizer = key;
            plugin.displays().refresh(st);
            Util.bar(p, "<gold>Fertilizer applied.");
        });
    }

    private void spray(Player p, Station st, Plant pl) {
        if (!pl.infested) {
            Util.bar(p, "<green>No pests here.");
            return;
        }
        plugin.minigames().startAt(p, st, MiniGameType.SPRAYING, perf -> {
            if (failed(perf)) {
                Util.bar(p, "<red>You missed the bugs. Try again.");
                return;
            }
            if (!"pesticide".equals(plugin.items().idOf(p.getInventory().getItemInMainHand())) || st.plant != pl) {
                return;
            }
            Util.consumeMainHand(p, 1);
            pl.infested = false;
            plugin.displays().refresh(st);
            Util.bar(p, "<green>The pests are gone.");
        });
    }

    private void harvest(Player p, Station st, Plant pl, Strain strain) {
        plugin.minigames().startAt(p, st, MiniGameType.HARVESTING, perf -> {
            if (st.plant != pl) {
                return;
            }
            boolean optimal = plugin.stations().inTent(st);
            int quality = plugin.growth().quality(pl, strain, optimal, perf);
            double yieldMult = plugin.growth().fert(pl.fertilizer, "yield", 1.0) * (0.5 + 0.5 * perf);
            int base = strain.yieldMin() + rnd.nextInt(strain.yieldMax() - strain.yieldMin() + 1);
            int amount = Math.max(1, (int) Math.round(base * yieldMult));
            Util.give(p, plugin.items().product("bud", amount, strain.id(), quality));

            int seeds = strain.seedMin() + rnd.nextInt(strain.seedMax() - strain.seedMin() + 1);
            int own = 0;
            int mutated = 0;
            boolean canMutate = strain.mutationStrain() != null && plugin.strains().get(strain.mutationStrain()) != null;
            for (int i = 0; i < seeds; i++) {
                if (canMutate && rnd.nextDouble() < strain.mutationChance()) {
                    mutated++;
                } else {
                    own++;
                }
            }
            if (own > 0) {
                Util.give(p, plugin.items().seed(strain.id(), own));
            }
            if (mutated > 0) {
                Util.give(p, plugin.items().seed(strain.mutationStrain(), mutated));
            }
            st.plant = null;
            plugin.displays().refresh(st);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
            String msg = "<green>Harvested " + amount + " buds <gray>(quality " + quality + "%)";
            if (mutated > 0) {
                msg += " <light_purple>+ mutated seeds!";
            }
            Util.bar(p, msg);
        });
    }

    // ------------------------------------------------------------------ waterer / lockup

    private void useWaterer(Player p, Station st, ItemStack hand) {
        FileConfiguration cfg = plugin.getConfig();
        int max = cfg.getInt("waterer.max-tank", 40);
        if (hand.getType() == Material.WATER_BUCKET) {
            if (st.tank >= max) {
                Util.bar(p, "<red>The tank is full.");
                return;
            }
            st.tank = Math.min(max, st.tank + cfg.getInt("waterer.bucket-fill", 10));
            if (p.getGameMode() != GameMode.CREATIVE) {
                p.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
            }
            p.playSound(p.getLocation(), Sound.ITEM_BUCKET_EMPTY, 1f, 1f);
            Util.bar(p, "<aqua>Tank filled <gray>(" + st.tank + "/" + max + ")");
        } else {
            Util.bar(p, "<aqua>Auto-Waterer <gray>- tank " + st.tank + "/" + max + ", range "
                    + cfg.getInt("waterer.radius", 4) + " blocks. Use a water bucket to fill.");
        }
    }

    private void useLockup(Player p, Station st) {
        if (plugin.getConfig().getBoolean("lockup.require-sneak", true) && !p.isSneaking()) {
            return;
        }
        if (!st.canOpenLockup(p.getUniqueId()) && !p.hasPermission("growops.admin")) {
            Util.bar(p, "<red>It's locked.");
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.8f, 1f);
            return;
        }
        if (st.inventory == null) {
            LockupHolder holder = new LockupHolder(st);
            Inventory inv = Bukkit.createInventory(holder, 27, Text.c("<dark_gray>Lockup"));
            holder.inv = inv;
            inv.setContents(st.contents.clone());
            st.inventory = inv;
        }
        p.openInventory(st.inventory);
        p.playSound(p.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.8f, 1f);
    }

    // ================================================================== placement

    private void place(Player p, Block clicked, BlockFace face, ItemStack hand, StationType type) {
        if (!p.hasPermission("growops.place")) {
            Util.bar(p, "<red>You can't place this.");
            return;
        }
        FileConfiguration cfg = plugin.getConfig();
        Block at = clicked.getRelative(face);
        if (!at.getType().isAir() && !at.isReplaceable()) {
            Util.bar(p, "<red>No room here.");
            return;
        }
        if (plugin.stations().get(at) != null) {
            return;
        }
        var worlds = cfg.getStringList("placement.allowed-worlds");
        if (!worlds.isEmpty() && !worlds.contains(at.getWorld().getName())) {
            Util.bar(p, "<red>You can't place this in this world.");
            return;
        }
        if (cfg.getBoolean("placement.indoor-only", false)
                && (type == StationType.POT || type == StationType.TENT)
                && at.getWorld().getHighestBlockYAt(at.getX(), at.getZ()) <= at.getY()) {
            Util.bar(p, "<red>This must be placed indoors (something needs to be above it).");
            return;
        }
        if (type == StationType.POT && plugin.stations().countPots(p.getUniqueId()) >= cfg.getInt("placement.max-pots-per-player", 50)) {
            Util.bar(p, "<red>You have reached the pot limit.");
            return;
        }
        BlockPlaceEvent check = new BlockPlaceEvent(at, at.getState(), clicked, hand, p, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(check);
        if (check.isCancelled() || !check.canBuild()) {
            return;
        }
        plugin.stations().create(type, at, p.getUniqueId(), false);
        if (p.getGameMode() != GameMode.CREATIVE) {
            Util.consumeMainHand(p, 1);
        }
        Location c = at.getLocation().add(0.5, 0.5, 0.5);
        c.getWorld().playSound(c, Sound.BLOCK_STONE_PLACE, 1f, 1f);
        Util.bar(p, "<green>" + type.label + " placed.");
    }

    // ================================================================== protection

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Station st = plugin.stations().get(e.getBlock());
        if (st == null) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        boolean admin = p.hasPermission("growops.admin");
        if (st.fixed && !admin) {
            Util.bar(p, "<red>This is part of the world and can't be removed.");
            return;
        }
        boolean owner = st.owner != null && st.owner.equals(p.getUniqueId());
        if (!admin && !owner && st.owner != null && !plugin.getConfig().getBoolean("placement.others-can-break", false)) {
            Util.bar(p, "<red>This isn't yours.");
            return;
        }
        plugin.stations().remove(st, !st.fixed && p.getGameMode() != GameMode.CREATIVE);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.stations().get(b) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.stations().get(b) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) {
            if (plugin.stations().get(b) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) {
            if (plugin.stations().get(b) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }

    // ================================================================== chunks

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        Chunk chunk = e.getChunk();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (chunk.isLoaded()) {
                plugin.displays().refreshChunk(chunk);
            }
        });
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        plugin.displays().removeChunk(e.getChunk());
    }
}
