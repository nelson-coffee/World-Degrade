package dev.ncn.worlddegrade.undo;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class UndoSnapshot {
    public record BlockRecord(long pos, CompoundTag stateTag, @Nullable CompoundTag blockEntityTag) {
    }

    private final ResourceKey<Level> dimension;
    private final Long2ObjectMap<BlockRecord> records = new Long2ObjectOpenHashMap<>();
    private final CompoundTag compatData = new CompoundTag();

    public UndoSnapshot(ResourceKey<Level> dimension) {
        this.dimension = dimension;
    }

    public CompoundTag compatSection(String key) {
        if (!compatData.contains(key, Tag.TAG_COMPOUND)) {
            compatData.put(key, new CompoundTag());
        }
        return compatData.getCompound(key);
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public void record(ServerLevel level, BlockPos pos) {
        long key = pos.asLong();
        if (records.containsKey(key)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        CompoundTag blockEntityTag = null;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            blockEntityTag = blockEntity.saveWithoutMetadata(level.registryAccess());
        }
        records.put(key, new BlockRecord(key, NbtUtils.writeBlockState(state), blockEntityTag));
    }

    public boolean isEmpty() {
        return records.isEmpty();
    }

    public int size() {
        return records.size();
    }

    public List<BlockRecord> allRecords() {
        return new ArrayList<>(records.values());
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", dimension.location().toString());
        ListTag list = new ListTag();
        for (BlockRecord record : records.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", record.pos());
            entry.put("state", record.stateTag());
            if (record.blockEntityTag() != null) {
                entry.put("be", record.blockEntityTag());
            }
            list.add(entry);
        }
        tag.put("blocks", list);
        tag.put("compat", compatData);
        return tag;
    }

    public static UndoSnapshot fromNbt(CompoundTag tag) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(tag.getString("dimension")));
        UndoSnapshot snapshot = new UndoSnapshot(dimension);
        ListTag list = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            long pos = entry.getLong("pos");
            CompoundTag beTag = entry.contains("be", Tag.TAG_COMPOUND) ? entry.getCompound("be") : null;
            snapshot.records.put(pos, new BlockRecord(pos, entry.getCompound("state"), beTag));
        }
        snapshot.compatData.merge(tag.getCompound("compat"));
        return snapshot;
    }
}
