package dev.ncn.worlddegrade.compat.create;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ContraptionPendingDegradation extends SavedData {
    private static final String NAME = "worlddegrade_contraption_pending";

    private int pendingLevelId;
    private final Set<UUID> eligibleContraptions = new HashSet<>();
    private final Set<UUID> eligibleTrains = new HashSet<>();
    private final Set<UUID> knownContraptions = new HashSet<>();

    public static ContraptionPendingDegradation get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ContraptionPendingDegradation::new,
                        ContraptionPendingDegradation::load, null), NAME);
    }

    public void markKnownContraption(UUID contraptionId) {
        if (knownContraptions.add(contraptionId)) {
            setDirty();
        }
    }

    public Set<UUID> knownContraptionsSnapshot() {
        return new HashSet<>(knownContraptions);
    }

    public void setPending(int levelId, Collection<UUID> eligibleContraptions, Collection<UUID> eligibleTrains) {
        this.eligibleContraptions.clear();
        this.eligibleContraptions.addAll(eligibleContraptions);
        this.eligibleTrains.clear();
        this.eligibleTrains.addAll(eligibleTrains);
        pendingLevelId = (this.eligibleContraptions.isEmpty() && this.eligibleTrains.isEmpty()) ? 0 : levelId;
        setDirty();
    }

    public void clearPending() {
        pendingLevelId = 0;
        eligibleContraptions.clear();
        eligibleTrains.clear();
        setDirty();
    }

    public int pendingLevelId() {
        return pendingLevelId;
    }

    public boolean claimContraption(UUID contraptionId) {
        if (pendingLevelId == 0 || !eligibleContraptions.remove(contraptionId)) {
            return false;
        }
        deactivateIfDrained();
        setDirty();
        return true;
    }

    public boolean claimTrain(UUID trainId) {
        if (pendingLevelId == 0 || !eligibleTrains.remove(trainId)) {
            return false;
        }
        deactivateIfDrained();
        setDirty();
        return true;
    }

    private void deactivateIfDrained() {
        if (eligibleContraptions.isEmpty() && eligibleTrains.isEmpty()) {
            pendingLevelId = 0;
        }
    }

    private static ContraptionPendingDegradation load(CompoundTag tag, HolderLookup.Provider registries) {
        ContraptionPendingDegradation data = new ContraptionPendingDegradation();
        data.pendingLevelId = tag.getInt("pendingLevel");
        readUuids(tag.getList("eligible", Tag.TAG_INT_ARRAY), data.eligibleContraptions);
        readUuids(tag.getList("eligibleTrains", Tag.TAG_INT_ARRAY), data.eligibleTrains);
        readUuids(tag.getList("known", Tag.TAG_INT_ARRAY), data.knownContraptions);
        if (data.eligibleContraptions.isEmpty() && data.eligibleTrains.isEmpty()) {
            data.pendingLevelId = 0;
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("pendingLevel", pendingLevelId);
        tag.put("eligible", writeUuids(eligibleContraptions));
        tag.put("eligibleTrains", writeUuids(eligibleTrains));
        tag.put("known", writeUuids(knownContraptions));
        return tag;
    }

    private static void readUuids(ListTag list, Set<UUID> into) {
        for (Tag entry : list) {
            into.add(NbtUtils.loadUUID(entry));
        }
    }

    private static ListTag writeUuids(Set<UUID> uuids) {
        ListTag list = new ListTag();
        for (UUID uuid : uuids) {
            list.add(NbtUtils.createUUID(uuid));
        }
        return list;
    }
}
