package dev.ncn.worlddegrade.compat.waystones;

import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.DegradeLevel;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.block.ModBlocks;
import net.blay09.mods.waystones.block.WaystoneBlock;
import net.blay09.mods.waystones.core.PlayerWaystoneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class WaystoneDecayEffect implements DegradeEffect {
    private final Set<UUID> visitedWaystones = new HashSet<>();

    @Override
    public void apply(DegradeContext ctx) {
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = ctx.state(pos);
            if (!(state.getBlock() instanceof WaystoneBlock)) {
                continue;
            }
            ctx.claim(pos);
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                pos = pos.below();
                state = ctx.state(pos);
                if (!(state.getBlock() instanceof WaystoneBlock)) {
                    continue;
                }
            }
            Optional<Waystone> waystone = WaystonesAPI.getWaystoneAt(ctx.level, pos);
            if (waystone.isEmpty() || !visitedWaystones.add(waystone.get().getWaystoneUid())) {
                continue;
            }
            unDiscoverForEveryone(ctx, waystone.get());
            if (ctx.chances.levelId() >= DegradeLevel.DAMAGED.id() && state.getBlock() == ModBlocks.waystone) {
                weather(ctx, pos, state);
            }
        }
    }

    private void unDiscoverForEveryone(DegradeContext ctx, Waystone waystone) {
        Set<UUID> deactivatedNow = new HashSet<>();
        for (ServerPlayer player : ctx.level.getServer().getPlayerList().getPlayers()) {
            if (PlayerWaystoneManager.isWaystoneActivated(player, waystone)) {
                PlayerWaystoneManager.deactivateWaystone(player, waystone);
                deactivatedNow.add(player.getUUID());
            }
        }
        WaystoneRevocations.get(ctx.level.getServer()).addRevocation(waystone.getWaystoneUid(), deactivatedNow);
        ctx.markChanged();

        CompoundTag section = ctx.undoCompatSection("waystones");
        ListTag revoked = section.getList("revoked", Tag.TAG_COMPOUND);
        CompoundTag entry = new CompoundTag();
        entry.put("waystone", NbtUtils.createUUID(waystone.getWaystoneUid()));
        revoked.add(entry);
        section.put("revoked", revoked);
    }

    private void weather(DegradeContext ctx, BlockPos lowerPos, BlockState lowerState) {
        ctx.recordForUndo(lowerPos);
        BlockPos upperPos = lowerPos.above();
        BlockState upperState = ctx.state(upperPos);
        if (upperState.getBlock() == ModBlocks.waystone) {
            ctx.recordForUndo(upperPos);
        }
        ctx.replaceBlockPreservingEntity(lowerPos, ModBlocks.mossyWaystone.withPropertiesOf(lowerState));
        if (upperState.getBlock() == ModBlocks.waystone) {
            ctx.replaceBlockPreservingEntity(upperPos, ModBlocks.mossyWaystone.withPropertiesOf(upperState));
        }
    }
}
