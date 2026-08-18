package dev.ncn.worlddegrade.compat;

import com.mojang.logging.LogUtils;
import dev.ncn.worlddegrade.config.WorldDegradeConfig;
import dev.ncn.worlddegrade.degrade.effects.BrickWeatherEffect;
import dev.ncn.worlddegrade.degrade.effects.ContainerLootEffect;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import dev.ncn.worlddegrade.degrade.effects.DoorBreakEffect;
import dev.ncn.worlddegrade.degrade.effects.GlassBreakEffect;
import dev.ncn.worlddegrade.degrade.effects.LightSnuffEffect;
import dev.ncn.worlddegrade.degrade.effects.OvergrowthEffect;
import dev.ncn.worlddegrade.degrade.effects.PortalBreakEffect;
import dev.ncn.worlddegrade.degrade.effects.StructuralCollapseEffect;
import dev.ncn.worlddegrade.degrade.effects.LeafGrowthEffect;
import dev.ncn.worlddegrade.degrade.effects.SurvivalSweepEffect;
import dev.ncn.worlddegrade.degrade.effects.UnknownBlockBreakEffect;
import dev.ncn.worlddegrade.degrade.effects.VanillaDecayEffect;
import dev.ncn.worlddegrade.degrade.effects.WoodRotEffect;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class CompatManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<ModCompat> ACTIVE = new ArrayList<>();
    private static boolean initialized;

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        dev.ncn.worlddegrade.degrade.VanillaWearTable.register();
        register("create", () -> new dev.ncn.worlddegrade.compat.create.CreateCompat());
        register("chipped", () -> new dev.ncn.worlddegrade.compat.chipped.ChippedCompat());
        register("waystones", () -> new dev.ncn.worlddegrade.compat.waystones.WaystonesCompat());
        register("sable", () -> new dev.ncn.worlddegrade.compat.sable.SableCompat());
        register("exposure", () -> new dev.ncn.worlddegrade.compat.exposure.ExposureCompat());
        register("supplementaries", () -> new dev.ncn.worlddegrade.compat.supplementaries.SupplementariesCompat());
        register("computercraft", () -> new dev.ncn.worlddegrade.compat.computercraft.ComputerCraftCompat());
        register("rechiseled", () -> new dev.ncn.worlddegrade.compat.rechiseled.RechiseledCompat());
    }

    private static void register(String modId, Supplier<ModCompat> factory) {
        if (!ModList.get().isLoaded(modId)) {
            return;
        }
        try {
            ModCompat compat = factory.get();
            compat.init();
            compat.registerWearSteps(BrickWeatherEffect::addWear);
            ACTIVE.add(compat);
            LOGGER.info("World Degrade: enabled {} compatibility", modId);
        } catch (Throwable t) {
            LOGGER.error("World Degrade: failed to enable {} compatibility; that mod's blocks will only degrade generically", modId, t);
        }
    }

    public static List<DegradeEffect> createEffects() {
        List<DegradeEffect> effects = new ArrayList<>();
        // Claim-based effects stay in the list even when disabled: their apply() still marks
        // matching blocks as handled (ctx.claim) before skipping mutation, so a disabled effect
        // leaves its blocks alone instead of handing them to UnknownBlockBreakEffect for deletion.
        effects.add(new GlassBreakEffect(WorldDegradeConfig.glassBreakEnabled()));
        effects.add(new BrickWeatherEffect(WorldDegradeConfig.brickWeatherEnabled()));
        effects.add(new WoodRotEffect(WorldDegradeConfig.woodRotEnabled()));
        effects.add(new VanillaDecayEffect(WorldDegradeConfig.vanillaDecayEnabled()));
        for (ModCompat compat : ACTIVE) {
            effects.addAll(compat.createWeatheringEffects());
        }
        // Effects below neither claim blocks nor risk unknown-break deletion when absent, so
        // disabling them simply omits them from the run.
        if (WorldDegradeConfig.structuralCollapseEnabled()) {
            effects.add(new StructuralCollapseEffect());
        }
        effects.add(new DoorBreakEffect(WorldDegradeConfig.doorBreakEnabled()));
        effects.add(new ContainerLootEffect(WorldDegradeConfig.containerLootEnabled()));
        effects.add(new LightSnuffEffect(WorldDegradeConfig.lightSnuffEnabled(),
                WorldDegradeConfig.burntBlockVariantsEnabled()));
        if (WorldDegradeConfig.portalBreakEnabled()) {
            effects.add(new PortalBreakEffect());
        }
        if (WorldDegradeConfig.overgrowthEnabled()) {
            effects.add(new OvergrowthEffect());
        }
        if (WorldDegradeConfig.leafGrowthEnabled()) {
            effects.add(new LeafGrowthEffect());
        }
        for (ModCompat compat : ACTIVE) {
            effects.addAll(compat.createEffects());
        }
        if (WorldDegradeConfig.unknownBreakEnabled()) {
            effects.add(new UnknownBlockBreakEffect());
        }
        effects.add(new SurvivalSweepEffect());
        return effects;
    }

    public static List<DegradeEffect> createShipEffects() {
        List<DegradeEffect> effects = new ArrayList<>();
        for (DegradeEffect effect : createEffects()) {
            if (effect.shipSafe()) {
                effects.add(effect);
            }
        }
        for (ModCompat compat : ACTIVE) {
            effects.addAll(compat.createShipOnlyEffects());
        }
        return effects;
    }

    public static List<RunWork> collectRunWork(net.minecraft.server.level.ServerPlayer operator,
                                               dev.ncn.worlddegrade.degrade.DegradeChances chances,
                                               boolean wholeWorld, int radius) {
        List<RunWork> work = new ArrayList<>();
        for (ModCompat compat : ACTIVE) {
            try {
                work.addAll(compat.createRunWork(operator, chances, wholeWorld, radius));
            } catch (Throwable t) {
                LOGGER.error("World Degrade: {} compat failed to contribute run work", compat.modId(), t);
            }
        }
        return work;
    }

    public static boolean shouldRestore(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
        for (ModCompat compat : ACTIVE) {
            try {
                if (!compat.shouldRestore(level, pos)) {
                    return false;
                }
            } catch (Throwable t) {
                LOGGER.error("World Degrade: {} compat failed during undo restore check", compat.modId(), t);
            }
        }
        return true;
    }

    public static void onUndo(MinecraftServer server, UndoCompatView compatView) {
        for (ModCompat compat : ACTIVE) {
            try {
                compat.onUndo(server, compatView.section(compat.modId()));
            } catch (Throwable t) {
                LOGGER.error("World Degrade: {} compat failed during undo", compat.modId(), t);
            }
        }
    }

    public static void onServerStopping() {
        for (ModCompat compat : ACTIVE) {
            try {
                compat.onServerStopping();
            } catch (Throwable t) {
                LOGGER.error("World Degrade: {} compat failed during shutdown cleanup", compat.modId(), t);
            }
        }
    }

    public interface UndoCompatView {
        CompoundTag section(String key);
    }

    public static boolean isFullyWorn(net.minecraft.world.level.block.state.BlockState state) {
        for (ModCompat compat : ACTIVE) {
            Boolean worn = compat.isFullyWorn(state);
            if (worn != null) {
                return worn;
            }
        }
        return BrickWeatherEffect.weather(state) == null;
    }

    public static boolean isFullyWorn(dev.ncn.worlddegrade.degrade.DegradeContext ctx,
                                      net.minecraft.core.BlockPos pos,
                                      net.minecraft.world.level.block.state.BlockState state) {
        for (ModCompat compat : ACTIVE) {
            Boolean worn = compat.isFullyWorn(ctx, pos, state);
            if (worn != null) {
                return worn;
            }
        }
        return BrickWeatherEffect.weather(state) == null;
    }

    private CompatManager() {
    }
}
