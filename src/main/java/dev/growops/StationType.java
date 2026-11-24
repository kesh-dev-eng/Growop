package dev.growops;

import org.bukkit.Material;

public enum StationType {
    POT("pot", Material.FLOWER_POT, "Grow Pot"),
    TENT("tent", Material.GREEN_STAINED_GLASS, "Grow Tent"),
    WATERER("waterer", Material.CAULDRON, "Auto-Waterer"),
    PROCESSING("processing_table", Material.SMITHING_TABLE, "Processing Table"),
    PRESS("press", Material.STONECUTTER, "Weed Press"),
    LOCKUP("lockup", Material.BARREL, "Lockup");

    public final String itemKey;
    public final Material defaultBlock;
    public final String label;

    StationType(String itemKey, Material defaultBlock, String label) {
        this.itemKey = itemKey;
        this.defaultBlock = defaultBlock;
        this.label = label;
    }

    public static StationType byItemKey(String key) {
        if (key == null) {
            return null;
        }
        for (StationType t : values()) {
            if (t.itemKey.equals(key)) {
                return t;
            }
        }
        return null;
    }
}
