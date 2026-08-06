package dev.ncn.worlddegrade.degrade;

import net.minecraft.util.Mth;

public record DegradeChances(
        int levelId,
        boolean corruptComputers,
        float roofCollapseChance,
        float wallCollapseChance,
        float glassBreakChance,
        float brickWeatherChance,
        float doorBreakChance,
        float woodRotChance,
        float containerKeepFraction,
        float vineChance,
        float cobwebChance,
        float machineBreakChance,
        float envelopeBreakChance,
        float campfireExtinguishChance,
        float filmDamageChance,
        float unknownBreakChance) {

    public static final int VALUE_COUNT = 14;

    public static DegradeChances of(DegradeLevel level) {
        return of(level, true);
    }

    public static DegradeChances of(DegradeLevel level, boolean corruptComputers) {
        return new DegradeChances(level.id(), corruptComputers,
                level.roofCollapseChance, level.wallCollapseChance,
                level.glassBreakChance, level.brickWeatherChance,
                level.doorBreakChance, level.woodRotChance, level.containerKeepFraction,
                level.vineChance, level.cobwebChance,
                level.machineBreakChance, level.envelopeBreakChance, level.campfireExtinguishChance,
                level.filmDamageChance, level.unknownBreakChance);
    }

    public static DegradeChances custom(int levelId, boolean corruptComputers, float[] values) {
        float[] clamped = new float[VALUE_COUNT];
        for (int i = 0; i < VALUE_COUNT; i++) {
            clamped[i] = Mth.clamp(values[i], 0.0f, 1.0f);
        }
        return new DegradeChances(levelId, corruptComputers,
                clamped[0], clamped[1], clamped[2], clamped[3], clamped[4],
                clamped[5], clamped[6], clamped[7], clamped[8], clamped[9], clamped[10], clamped[11],
                clamped[12], clamped[13]);
    }

    public float[] toArray() {
        return new float[]{roofCollapseChance, wallCollapseChance, glassBreakChance, brickWeatherChance,
                doorBreakChance, woodRotChance, containerKeepFraction, vineChance, cobwebChance,
                machineBreakChance, envelopeBreakChance, campfireExtinguishChance, filmDamageChance,
                unknownBreakChance};
    }
}
