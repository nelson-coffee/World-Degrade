package dev.ncn.worlddegrade.degrade;

import net.minecraft.util.Mth;

public enum DegradeLevel {
    WEATHERED(0.00f, 0.00f, 0.10f, 0.15f, 0.05f, 0.05f, 0.50f, 0.00f, 0.00f, 0.04f, 0.10f, 0.25f, 0.10f, 0.02f),
    WORN     (0.00f, 0.00f, 0.25f, 0.30f, 0.10f, 0.12f, 0.35f, 0.00f, 0.00f, 0.10f, 0.22f, 0.50f, 0.20f, 0.05f),
    DAMAGED  (0.30f, 0.00f, 0.45f, 0.50f, 0.15f, 0.22f, 0.25f, 0.08f, 0.03f, 0.20f, 0.40f, 0.75f, 0.35f, 0.10f),
    RUINED   (0.50f, 0.35f, 0.70f, 0.70f, 0.20f, 0.35f, 0.15f, 0.16f, 0.08f, 0.32f, 0.60f, 1.00f, 0.55f, 0.18f),
    COLLAPSED(0.75f, 0.60f, 0.90f, 0.85f, 0.25f, 0.50f, 0.07f, 0.25f, 0.15f, 0.50f, 0.80f, 1.00f, 0.75f, 0.30f);

    public final float roofCollapseChance;
    public final float wallCollapseChance;
    public final float glassBreakChance;
    public final float brickWeatherChance;
    public final float doorBreakChance;
    public final float woodRotChance;
    public final float containerKeepFraction;
    public final float vineChance;
    public final float cobwebChance;
    public final float machineBreakChance;
    public final float envelopeBreakChance;
    public final float campfireExtinguishChance;
    public final float filmDamageChance;
    public final float unknownBreakChance;

    DegradeLevel(float roofCollapseChance, float wallCollapseChance, float glassBreakChance,
                 float brickWeatherChance, float doorBreakChance, float woodRotChance,
                 float containerKeepFraction, float vineChance, float cobwebChance,
                 float machineBreakChance, float envelopeBreakChance,
                 float campfireExtinguishChance, float filmDamageChance,
                 float unknownBreakChance) {
        this.roofCollapseChance = roofCollapseChance;
        this.wallCollapseChance = wallCollapseChance;
        this.glassBreakChance = glassBreakChance;
        this.brickWeatherChance = brickWeatherChance;
        this.doorBreakChance = doorBreakChance;
        this.woodRotChance = woodRotChance;
        this.containerKeepFraction = containerKeepFraction;
        this.vineChance = vineChance;
        this.cobwebChance = cobwebChance;
        this.machineBreakChance = machineBreakChance;
        this.envelopeBreakChance = envelopeBreakChance;
        this.campfireExtinguishChance = campfireExtinguishChance;
        this.filmDamageChance = filmDamageChance;
        this.unknownBreakChance = unknownBreakChance;
    }

    public int id() {
        return ordinal() + 1;
    }

    public static DegradeLevel byId(int id) {
        return values()[Mth.clamp(id, 1, 5) - 1];
    }
}
