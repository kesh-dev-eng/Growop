package dev.growops;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class GrowOpsPlugin extends JavaPlugin {
    private StrainRegistry strains;
    private ItemService items;
    private StationManager stations;
    private DisplayService displays;
    private GrowthService growth;
    private MiniGames minigames;
    private Processing processing;
    private OpManager ops;
    private Hud hud;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("strains.yml");
        saveResourceIfMissing("items.yml");

        strains = new StrainRegistry(this);
        strains.load();
        items = new ItemService(this);
        stations = new StationManager(this);
        displays = new DisplayService(this);
        growth = new GrowthService(this);
        minigames = new MiniGames(this);
        processing = new Processing(this);
        ops = new OpManager(this);
        hud = new Hud(this);

        // ItemsAdder loads its items a bit after the server starts, so everything that needs
        // item definitions waits for that when ItemsAdder is installed.
        if (getServer().getPluginManager().getPlugin("ItemsAdder") != null) {
            getServer().getPluginManager().registerEvents(new IaLoadListener(this), this);
        } else {
            Bukkit.getScheduler().runTask(this, this::startup);
        }

        var pm = getServer().getPluginManager();
        pm.registerEvents(new StationListener(this), this);
        pm.registerEvents(minigames, this);
        pm.registerEvents(processing, this);
        pm.registerEvents(hud, this);
        pm.registerEvents(new LockupListener(this), this);

        GrowOpsCommand cmd = new GrowOpsCommand(this);
        PluginCommand pc = getCommand("growops");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }
    }

    private boolean started;

    /** Called once items can be created (right away, or after ItemsAdder finished loading). */
    void startup() {
        if (started) {
            return;
        }
        started = true;
        items.load();
        stations.load();
        ops.load();
        displays.refreshAll();

        long tickSeconds = Math.max(1, getConfig().getLong("growth.tick-seconds", 10));
        Bukkit.getScheduler().runTaskTimer(this, growth::tick, 20L * tickSeconds, 20L * tickSeconds);
        Bukkit.getScheduler().runTaskTimer(this, growth::particles, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, hud::tick, 10L, 10L);
        long save = Math.max(1, getConfig().getLong("save-interval-minutes", 5)) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(this, this::saveAll, save, save);
        growth.tick();
    }

    @Override
    public void onDisable() {
        if (!started) {
            return;
        }
        minigames.closeAll();
        ops.closeAll();
        saveAll();
        displays.removeAll();
    }

    public void saveAll() {
        stations.save();
        ops.save();
    }

    public void reloadAll() {
        reloadConfig();
        strains.load();
        items.load();
        displays.removeAll();
        displays.refreshAll();
    }

    private void saveResourceIfMissing(String name) {
        if (!new File(getDataFolder(), name).exists()) {
            saveResource(name, false);
        }
    }

    public StrainRegistry strains() { return strains; }
    public ItemService items() { return items; }
    public StationManager stations() { return stations; }
    public DisplayService displays() { return displays; }
    public GrowthService growth() { return growth; }
    public MiniGames minigames() { return minigames; }
    public Processing processing() { return processing; }
    public OpManager ops() { return ops; }
    public Hud hud() { return hud; }
}
