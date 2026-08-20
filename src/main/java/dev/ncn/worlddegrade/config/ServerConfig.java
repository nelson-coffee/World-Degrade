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
    final ModConfigSpec.BooleanValue protectFilledContainers;

    // Performance
    final ModConfigSpec.IntValue chunksPerTick;

    // Schedule (progressive multi-pass degradation, #5)
    final ModConfigSpec.BooleanValue enableSchedule;
    final ModConfigSpec.ConfigValue<List<? extends Integer>> passDelays;
    final ModConfigSpec.ConfigValue<List<? extends Integer>> passLevels;
    final ModConfigSpec.IntValue releaseBlockThreshold;
    final ModConfigSpec.BooleanValue schematicannonCountsAsInhabited;

    // Open Parties and Claims integration (#6)
    final ModConfigSpec.BooleanValue opacEnabled;
    final ModConfigSpec.BooleanValue opacUseCustomSchedule;
    final ModConfigSpec.ConfigValue<List<? extends Integer>> opacCustomPassDelays;
    final ModConfigSpec.ConfigValue<List<? extends Integer>> opacCustomPassLevels;
    final ModConfigSpec.EnumValue<ClaimRemovalTiming> opacRemoveClaimAfter;
    final ModConfigSpec.IntValue opacReleaseBlockThreshold;

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
        protectFilledContainers = builder.comment("Only destroy containers once they are empty.").define("protectFilledContainers", true);

        builder.pop();

        builder.comment("Performance tuning.").push("performance");

        chunksPerTick = builder
                .comment("How many chunks to process per server tick during a degradation run. Lower is gentler on the server.")
                .defineInRange("chunksPerTick", 4, 1, 64);

        builder.pop();

        builder.comment("Progressive multi-pass degradation.",
                        "A schedule runs several timed degradation passes over an area (e.g. lightly",
                        "weathered first, then damaged, then collapsed), triggered via the API (#5/#6) or",
                        "the /degrade schedule command. Scheduled passes never capture an undo snapshot.")
                .push("schedule");

        enableSchedule = builder
                .comment("Master switch for the schedule feature. Off by default because it changes world",
                        "state unattended over time. When off, /degrade schedule and the API are no-ops, the",
                        "per-placement inhabited check is skipped entirely, and existing schedules are frozen:",
                        "their clocks do not advance, so turning the feature back on resumes them where they",
                        "left off instead of finding every pass overdue at once.")
                .define("enabled", false);
        passDelays = builder
                .comment("Delay of each pass in real-life MINUTES after the schedule is triggered (one minute",
                        "= 1200 ticks of server runtime at the normal 20 tps). Timing uses server runtime, so",
                        "it does not advance while the server is offline and slows if the server is lagging.",
                        "Paired by index with passLevels; the shorter list wins. Non-positive delays are dropped",
                        "and pairs are sorted ascending, so order here does not matter.")
                .defineListAllowEmpty("passDelays", List.of(60, 720, 1440), () -> 60, o -> o instanceof Integer);
        passLevels = builder
                .comment("Severity level (1-5) of each pass, paired by index with passDelays.")
                .defineListAllowEmpty("passLevels", List.of(1, 3, 5), () -> 1, o -> o instanceof Integer);
        releaseBlockThreshold = builder
                .comment("How many blocks must be placed inside a scheduled area before the whole schedule is",
                        "cancelled, marking the area as inhabited again. Every placer counts: a player, a fake",
                        "player, a Create deployer, and each individual block fired by a Create schematicannon.",
                        "Breaking blocks never counts. The counter resets after each pass, so it measures",
                        "activity since the last pass. 1 means a single placed block spares the area.",
                        "0 turns the inhabited check off entirely — nothing built in a scheduled area will ever",
                        "cancel its schedule, so the area degrades on its passes regardless.")
                .defineInRange("releaseBlockThreshold", 1, 0, 4096);
        schematicannonCountsAsInhabited = builder
                .comment("Whether blocks fired by a Create schematicannon count toward releaseBlockThreshold.",
                        "Only relevant with Create installed. Turn this off to keep the cannon's blocks tracked",
                        "for degradation while leaving schedules alone: a cannon a player set up and walked away",
                        "from is a machine still running, not somebody moving back in.")
                .define("schematicannonCountsAsInhabited", true);

        builder.pop();

        builder.comment("Open Parties and Claims (OPAC) integration.",
                        "When a player's claim expires in OPAC, its chunks are fed into the schedule",
                        "feature above so the abandoned base degrades progressively into a lootable ruin.",
                        "Requires [schedule].enabled = true (this only feeds that system, it does not",
                        "degrade anything on its own) and OPAC's own playerClaimsConvertExpiredClaims =",
                        "false (with it on, expiration frees claims to wilderness/server instead of marking",
                        "them EXPIRED, so no signal reaches this integration and it stays inert).",
                        "Has no effect unless OPAC is installed.",
                        "OPAC-triggered runs never capture an undo snapshot.")
                .push("opac");

        opacEnabled = builder
                .comment("Master switch for the OPAC integration. When off, claim expirations are ignored.")
                .define("enabled", true);
        opacUseCustomSchedule = builder
                .comment("Use the OPAC-specific pass table below instead of the shared [schedule] table.",
                        "When false, OPAC expirations run the same passDelays/passLevels as everything else;",
                        "manual /degrade schedule entries always keep using the [schedule] table regardless.")
                .define("useCustomSchedule", false);
        opacCustomPassDelays = builder
                .comment("Delay of each OPAC pass in real-life MINUTES after the claim expired, same unit as",
                        "[schedule].passDelays. Only used when useCustomSchedule = true. Paired by index with",
                        "customPassLevels; non-positive delays are dropped and pairs are sorted ascending.")
                .defineListAllowEmpty("customPassDelays", List.of(7, 30, 60), () -> 7, o -> o instanceof Integer);
        opacCustomPassLevels = builder
                .comment("Severity level (1-5) of each OPAC pass, paired by index with customPassDelays.")
                .defineListAllowEmpty("customPassLevels", List.of(1, 3, 5), () -> 1, o -> o instanceof Integer);
        opacRemoveClaimAfter = builder
                .comment("When to drop the expired claim so other players can loot the ruin:",
                        "  FINAL_PASS - after the last degradation pass finishes (safest; loot only at the end)",
                        "  FIRST_PASS - after the first pass finishes, then it keeps crumbling while looted",
                        "  SCHEDULE   - immediately when the schedule is created, before any degradation",
                        "  NEVER      - leave the expired claim in place",
                        "The claim is only ever dropped if it is still owned by OPAC's expired-claim owner:",
                        "if someone re-claims a chunk during the schedule, that new claim is left untouched.",
                        "Note: with FIRST_PASS or SCHEDULE the claim is gone before the schedule could be",
                        "cancelled, and OPAC does not expose the old owner, so a later cancellation cannot",
                        "restore it — the area simply stays unclaimed.")
                .defineEnum("removeClaimAfter", ClaimRemovalTiming.FIRST_PASS);
        opacReleaseBlockThreshold = builder
                .comment("Overrides [schedule].releaseBlockThreshold for OPAC schedules only. 0 means looters",
                        "placing blocks never cancel the degradation, which is what you usually want once a",
                        "ruin has opened up for looting. Manual schedules keep using [schedule].releaseBlockThreshold.")
                .defineInRange("releaseBlockThreshold", 0, 0, 4096);

        builder.pop();

        builder.comment("Block behavior.").push("blocks");

        enableBurntBlockVariants = builder
                .comment("When true, snuffed torches/lanterns become custom burnt variants (requires the mod's assets on clients).",
                        "When false, light sources are simply removed instead, so the mod needs no client-side assets (server-only friendly).")
                .define("enableBurntBlockVariants", true);

        builder.pop();
    }
}
