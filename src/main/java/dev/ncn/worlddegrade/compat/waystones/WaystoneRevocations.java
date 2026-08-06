package dev.ncn.worlddegrade.compat.waystones;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class WaystoneRevocations extends SavedData {
    private static final String NAME = "worlddegrade_waystone_revocations";

    static final class Revocation {
        final UUID waystoneUid;
        final Set<UUID> appliedPlayers;

        Revocation(UUID waystoneUid, Set<UUID> appliedPlayers) {
            this.waystoneUid = waystoneUid;
            this.appliedPlayers = appliedPlayers;
        }
    }

    static final class Restoration {
        final UUID waystoneUid;
        final Set<UUID> players;

        Restoration(UUID waystoneUid, Set<UUID> players) {
            this.waystoneUid = waystoneUid;
            this.players = players;
        }
    }

    final List<Revocation> revocations = new ArrayList<>();
    final List<Restoration> restorations = new ArrayList<>();

    public static WaystoneRevocations get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WaystoneRevocations::new, WaystoneRevocations::load, null), NAME);
    }

    public void addRevocation(UUID waystoneUid, Set<UUID> alreadyApplied) {
        Revocation existing = findRevocation(waystoneUid);
        if (existing != null) {
            existing.appliedPlayers.addAll(alreadyApplied);
        } else {
            revocations.add(new Revocation(waystoneUid, new HashSet<>(alreadyApplied)));
        }
        setDirty();
    }

    @Nullable
    public Revocation removeRevocation(UUID waystoneUid) {
        Revocation revocation = findRevocation(waystoneUid);
        if (revocation != null) {
            revocations.remove(revocation);
            setDirty();
        }
        return revocation;
    }

    public void addRestoration(UUID waystoneUid, Set<UUID> players) {
        if (players.isEmpty()) {
            return;
        }
        for (Restoration restoration : restorations) {
            if (restoration.waystoneUid.equals(waystoneUid)) {
                restoration.players.addAll(players);
                setDirty();
                return;
            }
        }
        restorations.add(new Restoration(waystoneUid, new HashSet<>(players)));
        setDirty();
    }

    @Nullable
    private Revocation findRevocation(UUID waystoneUid) {
        for (Revocation revocation : revocations) {
            if (revocation.waystoneUid.equals(waystoneUid)) {
                return revocation;
            }
        }
        return null;
    }

    private static WaystoneRevocations load(CompoundTag tag, HolderLookup.Provider registries) {
        WaystoneRevocations data = new WaystoneRevocations();
        ListTag revocationList = tag.getList("revocations", Tag.TAG_COMPOUND);
        for (int i = 0; i < revocationList.size(); i++) {
            CompoundTag entry = revocationList.getCompound(i);
            data.revocations.add(new Revocation(
                    NbtUtils.loadUUID(entry.get("waystone")),
                    readUuidSet(entry.getList("applied", Tag.TAG_INT_ARRAY))));
        }
        ListTag restorationList = tag.getList("restorations", Tag.TAG_COMPOUND);
        for (int i = 0; i < restorationList.size(); i++) {
            CompoundTag entry = restorationList.getCompound(i);
            data.restorations.add(new Restoration(
                    NbtUtils.loadUUID(entry.get("waystone")),
                    readUuidSet(entry.getList("players", Tag.TAG_INT_ARRAY))));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag revocationList = new ListTag();
        for (Revocation revocation : revocations) {
            CompoundTag entry = new CompoundTag();
            entry.put("waystone", NbtUtils.createUUID(revocation.waystoneUid));
            entry.put("applied", writeUuidSet(revocation.appliedPlayers));
            revocationList.add(entry);
        }
        tag.put("revocations", revocationList);
        ListTag restorationList = new ListTag();
        for (Restoration restoration : restorations) {
            CompoundTag entry = new CompoundTag();
            entry.put("waystone", NbtUtils.createUUID(restoration.waystoneUid));
            entry.put("players", writeUuidSet(restoration.players));
            restorationList.add(entry);
        }
        tag.put("restorations", restorationList);
        return tag;
    }

    private static Set<UUID> readUuidSet(ListTag list) {
        Set<UUID> uuids = new HashSet<>();
        for (Tag entry : list) {
            uuids.add(NbtUtils.loadUUID(entry));
        }
        return uuids;
    }

    private static ListTag writeUuidSet(Set<UUID> uuids) {
        ListTag list = new ListTag();
        for (UUID uuid : uuids) {
            list.add(NbtUtils.createUUID(uuid));
        }
        return list;
    }
}
