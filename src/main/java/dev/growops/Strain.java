package dev.growops;

import org.bukkit.Color;

import java.util.List;

public record Strain(
        String id,
        String name,
        Color color,
        int growMinutes,
        double waterPerMinute,
        int yieldMin,
        int yieldMax,
        int seedMin,
        int seedMax,
        double mutationChance,
        String mutationStrain,
        int decayMinutes,
        boolean requiresOptimal,
        int baseQuality,
        List<String> stageItems) {
}
