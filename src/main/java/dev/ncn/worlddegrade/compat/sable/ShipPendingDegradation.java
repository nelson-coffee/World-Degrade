package dev.ncn.worlddegrade.compat.sable;

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

public class ShipPendingDegradation extends SavedData {
    private static final String NAME = "worlddegrade_ship_pending";

    private int pendingLevelId;
    private final Set<UUID> eligible = new HashSet<>();
    private final Set<UUID> knownShips = new HashSet<>();

    public static ShipPendingDegradation get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ShipPendingDegradation::new, ShipPendingDegradation::load, null), NAME);
    }

    public void markKnown(UUID shipId) {
        if (knownShips.add(shipId)) {
            setDirty();
        }
    }

    public Set<UUID> knownShipsSnapshot() {
        return new HashSet<>(knownShips);
    }

    public void setPending(int levelId, Collection<UUID> eligibleShips) {
        eligible.clear();
        eligible.addAll(eligibleShips);
        pendingLevelId = eligible.isEmpty() ? 0 : levelId;
        setDirty();
    }

    public void clearPending() {
        pendingLevelId = 0;
        eligible.clear();
        setDirty();
    }

    public int pendingLevelId() {
        return pendingLevelId;
    }

    public boolean claimOnAdd(UUID shipId) {
        if (pendingLevelId == 0 || !eligible.remove(shipId)) {
            return false;
        }
        if (eligible.isEmpty()) {
            pendingLevelId = 0;
        }
        setDirty();
        return true;
    }

    private static ShipPendingDegradation load(CompoundTag tag, HolderLookup.Provider registries) {
        ShipPendingDegradation data = new ShipPendingDegradation();
        data.pendingLevelId = tag.getInt("pendingLevel");
        readUuids(tag.getList("eligible", Tag.TAG_INT_ARRAY), data.eligible);
        readUuids(tag.getList("known", Tag.TAG_INT_ARRAY), data.knownShips);
        if (data.eligible.isEmpty()) {
            data.pendingLevelId = 0;
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("pendingLevel", pendingLevelId);
        tag.put("eligible", writeUuids(eligible));
        tag.put("known", writeUuids(knownShips));
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
