package dev.growops;

public enum MiniGameType {
    WATERING("watering", "<aqua>Watering", 3, 3, 3),
    FERTILIZING("fertilizing", "<gold>Fertilizing", 2, 3, 3),
    SPRAYING("spraying", "<red>Pest Spray", 3, 2, 3),
    HARVESTING("harvesting", "<green>Harvesting", 4, 3, 2),
    BAGGING("bagging", "<white>Bagging", 3, 3, 3),
    GRINDING("grinding", "<dark_green>Grinding", 3, 2, 2),
    ROLLING("rolling", "<yellow>Rolling", 3, 2, 2),
    PRESSING("pressing", "<gray>Pressing", 3, 3, 3);

    public final String key;
    public final String title;
    public final int defaultRounds;
    public final int defaultPeriod;
    public final int defaultZone;

    MiniGameType(String key, String title, int rounds, int period, int zone) {
        this.key = key;
        this.title = title;
        this.defaultRounds = rounds;
        this.defaultPeriod = period;
        this.defaultZone = zone;
    }
}
