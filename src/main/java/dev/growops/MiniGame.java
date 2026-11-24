package dev.growops;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;
import java.util.function.Consumer;

/**
 * Timing mini-game in a 27 slot GUI: a cursor sweeps the middle row and the player clicks
 * when it is over the green zone. While it runs, the player's arm swings and particles/sounds
 * play at the station, so everyone nearby sees the action. The callback receives a performance
 * between 0 and 1, or -1 if the player closed the GUI early.
 */
public final class MiniGame implements InventoryHolder {
    private static final int TRACK_START = 9;
    private static final int TRACK_LEN = 9;

    private final GrowOpsPlugin plugin;
    private final Player player;
    private final MiniGameType type;
    private final Location fx;
    private final Consumer<Double> callback;
    private final Inventory inv;
    private final Random rnd = new Random();

    private final int rounds;
    private final int period;
    private final int zoneWidth;

    private int round = 0;
    private int zoneStart;
    private int cursor = 0;
    private int dir = 1;
    private double scoreSum = 0;
    private int pause = 0;
    private boolean waiting = false;
    private boolean finished = false;
    private int animTicks = 0;
    private String lastResult = "<gray>Click when the marker is on the green zone!";
    private BukkitTask task;

    public MiniGame(GrowOpsPlugin plugin, Player player, MiniGameType type, Location fx, Consumer<Double> callback) {
        this.plugin = plugin;
        this.player = player;
        this.type = type;
        this.fx = fx;
        this.callback = callback;
        String base = "minigames." + type.key + ".";
        this.rounds = Math.max(1, plugin.getConfig().getInt(base + "rounds", type.defaultRounds));
        this.period = Math.max(1, plugin.getConfig().getInt(base + "period-ticks", type.defaultPeriod));
        this.zoneWidth = Math.max(1, Math.min(5, plugin.getConfig().getInt(base + "zone", type.defaultZone)));
        this.inv = Bukkit.createInventory(this, 27, Text.c(type.title));
        ItemStack filler = named(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(22, named(Material.EMERALD, "<green><bold>CLICK!"));
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void start() {
        newRound();
        render();
        player.openInventory(inv);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
    }

    private void newRound() {
        round++;
        zoneStart = rnd.nextInt(TRACK_LEN - zoneWidth + 1);
        cursor = 0;
        dir = 1;
        waiting = false;
    }

    private void tick() {
        if (finished) {
            return;
        }
        if (!player.isOnline()) {
            abort();
            return;
        }
        animTicks += period;
        if (animTicks >= 8) {
            animTicks = 0;
            animate();
        }
        if (waiting) {
            pause -= period;
            if (pause <= 0) {
                if (round >= rounds) {
                    finish();
                    return;
                }
                newRound();
                lastResult = "<gray>Round " + round + "/" + rounds;
                render();
            }
            return;
        }
        cursor += dir;
        if (cursor >= TRACK_LEN - 1) {
            cursor = TRACK_LEN - 1;
            dir = -1;
        } else if (cursor <= 0) {
            cursor = 0;
            dir = 1;
        }
        render();
    }

    public void click(int rawSlot) {
        if (finished || waiting) {
            return;
        }
        int zoneEnd = zoneStart + zoneWidth - 1;
        int dist = cursor < zoneStart ? zoneStart - cursor : Math.max(0, cursor - zoneEnd);
        double score = dist == 0 ? 1.0 : (dist == 1 ? 0.5 : (dist == 2 ? 0.2 : 0.0));
        scoreSum += score;
        waiting = true;
        pause = 12;
        if (score >= 1.0) {
            lastResult = "<green><bold>Perfect!";
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);
        } else if (score > 0) {
            lastResult = "<yellow>Almost...";
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
        } else {
            lastResult = "<red>Miss!";
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
        }
        render();
    }

    private void render() {
        for (int i = 0; i < TRACK_LEN; i++) {
            Material m;
            String name;
            if (i == cursor) {
                m = Material.YELLOW_CONCRETE;
                name = "<yellow>|";
            } else if (i >= zoneStart && i < zoneStart + zoneWidth) {
                m = Material.LIME_STAINED_GLASS_PANE;
                name = "<green>Zone";
            } else {
                m = Material.GRAY_STAINED_GLASS_PANE;
                name = " ";
            }
            inv.setItem(TRACK_START + i, named(m, name));
        }
        inv.setItem(4, named(Material.PAPER, "<white>Round " + Math.min(round, rounds) + "/" + rounds + " " + lastResult));
    }

    private void animate() {
        player.swingMainHand();
        World w = fx.getWorld();
        if (w == null) {
            return;
        }
        Sound sound;
        switch (type) {
            case WATERING -> {
                w.spawnParticle(Particle.SPLASH, fx, 10, 0.15, 0.1, 0.15, 0.0);
                sound = Sound.ITEM_BUCKET_EMPTY;
            }
            case FERTILIZING -> {
                w.spawnParticle(Particle.HAPPY_VILLAGER, fx, 4, 0.2, 0.2, 0.2, 0.0);
                sound = Sound.ITEM_BONE_MEAL_USE;
            }
            case SPRAYING -> {
                w.spawnParticle(Particle.CLOUD, fx, 6, 0.2, 0.15, 0.2, 0.01);
                sound = Sound.BLOCK_FIRE_EXTINGUISH;
            }
            case HARVESTING -> {
                w.spawnParticle(Particle.HAPPY_VILLAGER, fx, 5, 0.25, 0.25, 0.25, 0.0);
                sound = Sound.BLOCK_GRASS_BREAK;
            }
            case BAGGING -> {
                w.spawnParticle(Particle.CRIT, fx, 5, 0.2, 0.1, 0.2, 0.05);
                sound = Sound.ITEM_ARMOR_EQUIP_LEATHER;
            }
            case GRINDING -> {
                w.spawnParticle(Particle.CRIT, fx, 6, 0.2, 0.1, 0.2, 0.05);
                sound = Sound.BLOCK_GRINDSTONE_USE;
            }
            case ROLLING -> {
                w.spawnParticle(Particle.CRIT, fx, 4, 0.2, 0.1, 0.2, 0.05);
                sound = Sound.ITEM_BOOK_PAGE_TURN;
            }
            default -> {
                w.spawnParticle(Particle.CRIT, fx, 6, 0.2, 0.1, 0.2, 0.05);
                sound = Sound.BLOCK_PISTON_EXTEND;
            }
        }
        w.playSound(fx, sound, 0.6f, 1.2f);
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        if (task != null) {
            task.cancel();
        }
        double perf = scoreSum / rounds;
        player.closeInventory();
        callback.accept(perf);
    }

    /** Player closed the GUI (or left): the game is cancelled. */
    public void onClose() {
        if (finished) {
            return;
        }
        finished = true;
        if (task != null) {
            task.cancel();
        }
        callback.accept(-1.0);
    }

    public void abort() {
        if (finished) {
            return;
        }
        player.closeInventory();
        onClose();
    }

    private static ItemStack named(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.c(name));
        it.setItemMeta(meta);
        return it;
    }
}
