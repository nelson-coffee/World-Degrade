package dev.ncn.worlddegrade.marking;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MarkedRegions extends SavedData {
    private static final String NAME = "worlddegrade_marked_regions";

    public record Region(UUID id, BlockPos min, BlockPos max) {
        public AABB bounds() {
            return new AABB(min.getX(), min.getY(), min.getZ(),
                    max.getX() + 1, max.getY() + 1, max.getZ() + 1);
        }

        public boolean intersectsChunk(ChunkPos chunkPos) {
            return chunkPos.getMaxBlockX() >= min.getX() && chunkPos.getMinBlockX() <= max.getX()
                    && chunkPos.getMaxBlockZ() >= min.getZ() && chunkPos.getMinBlockZ() <= max.getZ();
        }
    }

    private final List<Region> regions = new ArrayList<>();

    public static MarkedRegions get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MarkedRegions::new, MarkedRegions::load, null), NAME);
    }

    public List<Region> all() {
        return List.copyOf(regions);
    }

    public void add(Region region) {
        regions.add(region);
        setDirty();
    }

    @Nullable
    public Region remove(UUID id) {
        for (Region region : regions) {
            if (region.id().equals(id)) {
                regions.remove(region);
                setDirty();
                return region;
            }
        }
        return null;
    }

    public LongOpenHashSet regionChunks() {
        LongOpenHashSet chunks = new LongOpenHashSet();
        for (Region region : regions) {
            int minCX = SectionPos.blockToSectionCoord(region.min().getX());
            int maxCX = SectionPos.blockToSectionCoord(region.max().getX());
            int minCZ = SectionPos.blockToSectionCoord(region.min().getZ());
            int maxCZ = SectionPos.blockToSectionCoord(region.max().getZ());
            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    chunks.add(ChunkPos.asLong(cx, cz));
                }
            }
        }
        return chunks;
    }

    public LongOpenHashSet collectRegionPositions(ServerLevel level, LevelChunk chunk) {
        LongOpenHashSet positions = new LongOpenHashSet();
        ChunkPos chunkPos = chunk.getPos();
        for (Region region : regions) {
            if (!region.intersectsChunk(chunkPos)) {
                continue;
            }
            int minX = Math.max(region.min().getX(), chunkPos.getMinBlockX());
            int maxX = Math.min(region.max().getX(), chunkPos.getMaxBlockX());
            int minZ = Math.max(region.min().getZ(), chunkPos.getMinBlockZ());
            int maxZ = Math.min(region.max().getZ(), chunkPos.getMaxBlockZ());
            int minY = Math.max(region.min().getY(), level.getMinBuildHeight());
            int maxY = Math.min(region.max().getY(), level.getMaxBuildHeight() - 1);
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        cursor.set(x, y, z);
                        BlockState state = chunk.getBlockState(cursor);
                        if (!state.isAir() && state.getDestroySpeed(level, cursor) >= 0) {
                            positions.add(cursor.asLong());
                        }
                    }
                }
            }
        }
        return positions;
    }

    @Nullable
    public Region rayPick(Vec3 from, Vec3 direction, double maxDistance) {
        Vec3 to = from.add(direction.scale(maxDistance));
        Region closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Region region : regions) {
            var clip = region.bounds().clip(from, to);
            if (clip.isPresent()) {
                double distance = clip.get().distanceToSqr(from);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = region;
                }
            }
        }
        return closest;
    }

    private static MarkedRegions load(CompoundTag tag, HolderLookup.Provider registries) {
        MarkedRegions data = new MarkedRegions();
        ListTag list = tag.getList("regions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            data.regions.add(new Region(
                    NbtUtils.loadUUID(entry.get("id")),
                    NbtUtils.readBlockPos(entry, "min").orElse(BlockPos.ZERO),
                    NbtUtils.readBlockPos(entry, "max").orElse(BlockPos.ZERO)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Region region : regions) {
            CompoundTag entry = new CompoundTag();
            entry.put("id", NbtUtils.createUUID(region.id()));
            entry.put("min", NbtUtils.writeBlockPos(region.min()));
            entry.put("max", NbtUtils.writeBlockPos(region.max()));
            list.add(entry);
        }
        tag.put("regions", list);
        return tag;
    }
}
