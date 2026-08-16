package dev.ncn.worlddegrade.tracking;

import dev.ncn.worlddegrade.WorldDegrade;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

@EventBusSubscriber(modid = WorldDegrade.MOD_ID)
public final class ExcavationTracker {
    private static final int MAX_CEILING_SEARCH = 12;

    private ExcavationTracker() {
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getPlayer() != null) {
            record(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isDeliberateBlast(event)) {
            return;
        }
        for (BlockPos pos : event.getAffectedBlocks()) {
            record(level, pos);
        }
    }

    private static boolean isDeliberateBlast(ExplosionEvent.Detonate event) {
        Entity source = event.getExplosion().getDirectSourceEntity();
        return source instanceof PrimedTnt || source instanceof MinecartTNT
                || source instanceof Player;
    }

    public static void record(ServerLevel level, BlockPos pos) {
        BlockPos ceiling = ceilingAbove(level, pos);
        if (ceiling == null) {
            return;
        }
        LevelChunk chunk = level.getChunkAt(ceiling);
        chunk.getData(ModAttachments.EXCAVATED_CEILINGS).add(ceiling.asLong());
        chunk.setUnsaved(true);
        TrackedChunkIndex.get(level).addChunk(chunk.getPos());
    }

    private static BlockPos ceilingAbove(ServerLevel level, BlockPos pos) {
        BlockPos above = pos.above();
        if (level.canSeeSky(above)) {
            return null;
        }
        BlockPos.MutableBlockPos cursor = above.mutable();
        for (int step = 0; step < MAX_CEILING_SEARCH; step++) {
            if (cursor.getY() > level.getMaxBuildHeight()) {
                return null;
            }
            if (level.getBlockState(cursor).isSolid()) {
                return cursor.immutable();
            }
            cursor.move(0, 1, 0);
        }
        return null;
    }

    public static long[] excavatedCeilings(LevelChunk chunk) {
        if (!chunk.hasData(ModAttachments.EXCAVATED_CEILINGS)) {
            return new long[0];
        }
        return chunk.getData(ModAttachments.EXCAVATED_CEILINGS).toLongArray();
    }

    public static boolean isExcavatedCeiling(LevelChunk chunk, BlockPos pos) {
        return chunk.hasData(ModAttachments.EXCAVATED_CEILINGS)
                && chunk.getData(ModAttachments.EXCAVATED_CEILINGS).contains(pos.asLong());
    }
}
