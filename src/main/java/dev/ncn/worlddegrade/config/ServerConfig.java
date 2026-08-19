package dev.ncn.worlddegrade.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public final class ServerConfig {
    static final ServerConfig CONFIG;
    public static final ModConfigSpec SPEC;

    static {
        Pair<ServerConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(ServerConfig::new);
        CONFIG = pair.getLeft();
        SPEC = pair.getRight();
    }

    // Tracking
    final ModConfigSpec.BooleanValue enablePlacementTracking;
    final ModConfigSpec.BooleanValue enableExcavationTracking;
    final ModConfigSpec.ConfigValue<List<? extends String>> disabledDimensions;

    // Degradation defaults
    final ModConfigSpec.IntValue defaultLevel;

    // Per-effect toggles
    final ModConfigSpec.BooleanValue enableWoodRot;
    final ModConfigSpec.BooleanValue enableGlassBreak;
    final ModConfigSpec.BooleanValue enableBrickWeather;
    final ModConfigSpec.BooleanValue enableStructuralCollapse;
    final ModConfigSpec.BooleanValue enableDoorBreak;
    final ModConfigSpec.BooleanValue enableContainerLoot;
    final ModConfigSpec.BooleanValue enableLightSnuff;
    final ModConfigSpec.BooleanValue enablePortalBreak;
    final ModConfigSpec.BooleanValue enableOvergrowth;
    final ModConfigSpec.BooleanValue enableLeafGrowth;
    final ModConfigSpec.BooleanValue enableVanillaDecay;
    final ModConfigSpec.BooleanValue enableUnknownBreak;

    // Performance
    final ModConfigSpec.IntValue chunksPerTick;

    // Blocks
    final ModConfigSpec.BooleanValue enableBurntBlockVariants;

    private ServerConfig(ModConfigSpec.Builder builder) {
        builder.comment("Server-side settings for World Degrade.",
                        "This file is synced to clients when they join.")
                .push("tracking");

        enablePlacementTracking = builder
                .comment("Track blocks placed by players so they can be degraded later. Disable to only degrade marked/excavated areas.")
                .define("enablePlacementTracking", true);
        enableExcavationTracking = builder
                .comment("Track ceilings above player-dug tunnels so they can collapse during degradation.")
                .define("enableExcavationTracking", true);
        disabledDimensions = builder
                .comment("Dimension IDs (e.g. \"minecraft:the_nether\") excluded from World Degrade.",
                        "Tracking is skipped in these dimensions and /degrade refuses to run there.",
                        "Leave empty to cover every dimension. Unqualified names assume the \"minecraft\" namespace.",
                        "Unparseable entries are skipped with a warning; entries that name no loaded dimension are warned about on server start.")
                .defineListAllowEmpty("disabledDimensions", List.of(), () -> "minecraft:the_nether", o -> o instanceof String);

        builder.pop();

        builder.comment("Degradation defaults.").push("degradation");

        defaultLevel = builder
                .comment("Initial severity (1-5) pre-selected on the degrade GUI slider.",
                        "The server does not enforce this; it only seeds the GUI, and the client sends whichever level it chooses.")
                .defineInRange("defaultLevel", 3, 1, 5);

        builder.pop();

        builder.comment("Toggle individual degradation effects.",
                        "A disabled effect leaves its blocks untouched; it does not hand them to enableUnknownBreak.")
                .push("effects");

        enableWoodRot = builder.comment("Rot wooden blocks (planks, logs, fences, etc.).").define("enableWoodRot", true);
        enableGlassBreak = builder.comment("Break glass blocks and panes.").define("enableGlassBreak", true);
        enableBrickWeather = builder.comment("Weather bricks, stone and similar masonry into worn variants.").define("enableBrickWeather", true);
        enableStructuralCollapse = builder.comment("Collapse roofs, walls and excavated ceilings.").define("enableStructuralCollapse", true);
        enableDoorBreak = builder.comment("Break doors, trapdoors and gates.").define("enableDoorBreak", true);
        enableContainerLoot = builder.comment("Empty or partially empty containers.").define("enableContainerLoot", true);
        enableLightSnuff = builder.comment("Snuff out torches, lanterns and campfires.").define("enableLightSnuff", true);
        enablePortalBreak = builder.comment("Break nether portals.").define("enablePortalBreak", true);
        enableOvergrowth = builder.comment("Spread vines, cobwebs and other overgrowth.").define("enableOvergrowth", true);
        enableLeafGrowth = builder.comment("Grow leaves and foliage over structures.").define("enableLeafGrowth", true);
        enableVanillaDecay = builder.comment("Apply vanilla block wear transitions.").define("enableVanillaDecay", true);
        enableUnknownBreak = builder.comment("Break otherwise-unhandled blocks that have no specific effect.").define("enableUnknownBreak", true);

        builder.pop();

        builder.comment("Performance tuning.").push("performance");

        chunksPerTick = builder
                .comment("How many chunks to process per server tick during a degradation run. Lower is gentler on the server.")
                .defineInRange("chunksPerTick", 4, 1, 64);

        builder.pop();

        builder.comment("Block behavior.").push("blocks");

        enableBurntBlockVariants = builder
                .comment("When true, snuffed torches/lanterns become custom burnt variants (requires the mod's assets on clients).",
                        "When false, light sources are simply removed instead, so the mod needs no client-side assets (server-only friendly).")
                .define("enableBurntBlockVariants", true);

        builder.pop();
    }
}
