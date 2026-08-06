package dev.ncn.worlddegrade.compat.create;

import com.simibubi.create.content.schematics.cannon.LaunchedItem;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
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
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class SchematicannonTracker {
    private static final Map<ResourceKey<Level>, Set<BlockPos>> WATCHED = new HashMap<>();

    private SchematicannonTracker() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SchematicannonTracker::onRightClick);
        NeoForge.EVENT_BUS.addListener(SchematicannonTracker::onPlace);
        NeoForge.EVENT_BUS.addListener(SchematicannonTracker::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(SchematicannonTracker::onServerTick);
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
        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            watchIfCannon(level, entry.getKey(), entry.getValue());
        }
    }

    private static void watchIfCannon(ServerLevel level, BlockPos pos, BlockEntity blockEntity) {
        if (blockEntity instanceof SchematicannonBlockEntity) {
            WATCHED.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(pos.immutable());
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            Set<BlockPos> positions = WATCHED.get(level.dimension());
            if (positions == null || positions.isEmpty()) {
                continue;
            }
            Iterator<BlockPos> iterator = positions.iterator();
            while (iterator.hasNext()) {
                BlockPos pos = iterator.next();
                if (!level.isLoaded(pos)) {
                    continue;
                }
                if (!(level.getBlockEntity(pos) instanceof SchematicannonBlockEntity cannon)) {
                    iterator.remove();
                    continue;
                }
                for (LaunchedItem item : cannon.flyingBlocks) {
                    if (item.target != null) {
                        PlacementTracker.track(level, item.target);
                    }
                }
            }
        }
    }
}
