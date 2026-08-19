package dev.ncn.worlddegrade.compat.create;

import com.simibubi.create.content.schematics.cannon.LaunchedItem;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlock;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import dev.ncn.worlddegrade.config.WorldDegradeConfig;
import dev.ncn.worlddegrade.schedule.ScheduleService;
import dev.ncn.worlddegrade.tracking.PlacementTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class SchematicannonTracker {
    private static final Map<ResourceKey<Level>, Set<BlockPos>> WATCHED = new HashMap<>();
    // Targets that were already in flight on the previous tick, keyed by cannon position. A
    // LaunchedItem sits in flyingBlocks for its whole flight (Create's LaunchedItem.update counts
    // ticksRemaining down and SchematicannonBlockEntity.tickFlyingBlocks removes it on landing), so it
    // is visible for many ticks; handling only targets that were not there last tick makes each block
    // the cannon places count exactly once, the same as a block placed by hand, and collapses the
    // repeated tracking of a still-flying block into a single call.
    private static final Map<ResourceKey<Level>, Map<BlockPos, Set<BlockPos>>> IN_FLIGHT = new HashMap<>();

    private SchematicannonTracker() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SchematicannonTracker::onRightClick);
        NeoForge.EVENT_BUS.addListener(SchematicannonTracker::onPlace);
        NeoForge.EVENT_BUS.addListener(SchematicannonTracker::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(SchematicannonTracker::onServerTick);
        NeoForge.EVENT_BUS.addListener(SchematicannonTracker::onServerStopping);
    }

    private static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level) {
            watchIfCannon(level, event.getPos(), level.getBlockEntity(event.getPos()));
        }
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            watchIfCannon(level, event.getPos(), level.getBlockEntity(event.getPos()));
        }
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        // getBlockEntities() only holds the block entities already instantiated; ones restored from
        // disk sit as raw NBT in the chunk's pending map until something asks for them. Discovery has
        // to see those too, or a cannon whose chunk cycled would stop being watched until someone
        // right-clicked it. getBlockEntitiesPos() covers both, and matching on the block state means
        // no block entity is instantiated just to answer the question.
        for (BlockPos pos : chunk.getBlockEntitiesPos()) {
            if (chunk.getBlockState(pos).getBlock() instanceof SchematicannonBlock) {
                watch(level, pos);
            }
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        WATCHED.clear();
        IN_FLIGHT.clear();
    }

    private static void watchIfCannon(ServerLevel level, BlockPos pos, BlockEntity blockEntity) {
        if (blockEntity instanceof SchematicannonBlockEntity) {
            watch(level, pos);
        }
    }

    private static void watch(ServerLevel level, BlockPos pos) {
        WATCHED.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(pos.immutable());
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        // Resolved once per tick, not per cannon.
        boolean countHabitation = ScheduleService.habitationCountingEnabled()
                && WorldDegradeConfig.schematicannonCountsAsInhabited();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            Set<BlockPos> positions = WATCHED.get(level.dimension());
            if (positions == null || positions.isEmpty()) {
                continue;
            }
            Map<BlockPos, Set<BlockPos>> inFlight =
                    IN_FLIGHT.computeIfAbsent(level.dimension(), key -> new HashMap<>());
            Iterator<BlockPos> iterator = positions.iterator();
            while (iterator.hasNext()) {
                BlockPos pos = iterator.next();
                // Unwatch rather than skip: chunk load re-discovers the cannon, so WATCHED stays
                // bounded by the loaded chunks instead of every cannon the server has ever seen.
                if (!level.isLoaded(pos)
                        || !(level.getBlockEntity(pos) instanceof SchematicannonBlockEntity cannon)) {
                    iterator.remove();
                    inFlight.remove(pos);
                    continue;
                }
                if (cannon.flyingBlocks.isEmpty()) {
                    inFlight.remove(pos);
                    continue;
                }
                // Use is keyed on the chunk being BUILT, not the cannon's own chunk: a cannon parked
                // outside a scheduled base but rebuilding inside it is exactly the "something is
                // building here" case, while a cannon standing inside a schedule but firing far away
                // must not spare it.
                Set<BlockPos> previous = inFlight.get(pos);
                Set<BlockPos> current = new HashSet<>();
                for (LaunchedItem item : cannon.flyingBlocks) {
                    if (item.target == null) {
                        continue;
                    }
                    BlockPos target = item.target.immutable();
                    current.add(target);
                    if (previous != null && previous.contains(target)) {
                        // Already handled on an earlier tick of this same flight.
                        continue;
                    }
                    PlacementTracker.track(level, target);
                    if (countHabitation) {
                        ScheduleService.markInUse(level, target);
                    }
                }
                inFlight.put(pos.immutable(), current);
            }
        }
    }
}
