package dev.ncn.worlddegrade.compat;

import dev.ncn.worlddegrade.degrade.DegradeArea;
import dev.ncn.worlddegrade.degrade.DegradeChances;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

public interface ModCompat {
    List<DegradeEffect> createEffects();

    default List<DegradeEffect> createWeatheringEffects() {
        return List.of();
    }

    default void init() {
    }

    default void registerWearSteps(BiConsumer<Block, Block> sink) {
    }

    default List<DegradeEffect> createShipOnlyEffects() {
        return List.of();
    }

    default List<RunWork> createRunWork(ServerLevel level, DegradeArea area, DegradeChances chances,
                                        @Nullable UUID operator) {
        return List.of();
    }

    default boolean shouldRestore(ServerLevel level, BlockPos pos) {
        return true;
    }

    default void onUndo(MinecraftServer server, CompoundTag compatSection) {
    }

    default void onServerStopping() {
    }

    default Boolean isFullyWorn(net.minecraft.world.level.block.state.BlockState state) {
        return null;
    }

    default Boolean isFullyWorn(dev.ncn.worlddegrade.degrade.DegradeContext ctx,
                                net.minecraft.core.BlockPos pos,
                                net.minecraft.world.level.block.state.BlockState state) {
        return isFullyWorn(state);
    }

    String modId();
}
