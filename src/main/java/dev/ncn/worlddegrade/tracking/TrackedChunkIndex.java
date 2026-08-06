package dev.ncn.worlddegrade.tracking;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

public class TrackedChunkIndex extends SavedData {
    private static final String NAME = "worlddegrade_chunk_index";

    private final LongOpenHashSet chunks = new LongOpenHashSet();

    public static TrackedChunkIndex get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TrackedChunkIndex::new, TrackedChunkIndex::load, null), NAME);
    }

    private static TrackedChunkIndex load(CompoundTag tag, HolderLookup.Provider registries) {
        TrackedChunkIndex index = new TrackedChunkIndex();
        for (long packed : tag.getLongArray("chunks")) {
            index.chunks.add(packed);
        }
        return index;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray("chunks", chunks.toLongArray());
        return tag;
    }

    public void addChunk(ChunkPos pos) {
        if (chunks.add(pos.toLong())) {
            setDirty();
        }
    }

    public void removeChunk(ChunkPos pos) {
        if (chunks.remove(pos.toLong())) {
            setDirty();
        }
    }

    public long[] allChunks() {
        return chunks.toLongArray();
    }
}
