package dev.ncn.worlddegrade.tracking;

import dev.ncn.worlddegrade.WorldDegrade;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.minecraft.world.level.chunk.LevelChunk;

@EventBusSubscriber(modid = WorldDegrade.MOD_ID)
public final class PlacementTracker {

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event instanceof BlockEvent.EntityMultiPlaceEvent) {
            return;
        }
        if (event.getEntity() instanceof Player && event.getLevel() instanceof ServerLevel level) {
            track(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (event.getEntity() instanceof Player && event.getLevel() instanceof ServerLevel level) {
            for (BlockSnapshot snapshot : event.getReplacedBlockSnapshots()) {
                track(level, snapshot.getPos());
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            untrack(level, event.getPos());
        }
    }

    public static void track(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        chunk.getData(ModAttachments.TRACKED_BLOCKS).add(pos.asLong());
        chunk.setUnsaved(true);
        TrackedChunkIndex.get(level).addChunk(chunk.getPos());
    }

    public static void untrack(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        if (chunk.hasData(ModAttachments.TRACKED_BLOCKS)) {
            chunk.getData(ModAttachments.TRACKED_BLOCKS).remove(pos.asLong());
            chunk.setUnsaved(true);
        }
    }

    public static long[] trackedPositions(LevelChunk chunk) {
        if (!chunk.hasData(ModAttachments.TRACKED_BLOCKS)) {
            return new long[0];
        }
        return chunk.getData(ModAttachments.TRACKED_BLOCKS).toLongArray();
    }

    private PlacementTracker() {
    }
}
