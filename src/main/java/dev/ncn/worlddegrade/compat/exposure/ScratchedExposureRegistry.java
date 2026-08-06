package dev.ncn.worlddegrade.compat.exposure;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScratchedExposureRegistry extends SavedData {
    private static final String NAME = "worlddegrade_scratched_exposures";

    public static final String SCRATCHED_PREFIX = "worlddegrade_scratched_";

    public static final int KEEP_RUNS = 10;

    private int runCounter;
    private final Map<String, Integer> supersededAt = new HashMap<>();

    public static ScratchedExposureRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ScratchedExposureRegistry::new, ScratchedExposureRegistry::load, null), NAME);
    }

    public int beginPass() {
        runCounter++;
        setDirty();
        return runCounter;
    }

    public void markSuperseded(String id, int run) {
        if (supersededAt.putIfAbsent(id, run) == null) {
            setDirty();
        }
    }

    public List<String> collectExpired() {
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : supersededAt.entrySet()) {
            if (runCounter - entry.getValue() >= KEEP_RUNS) {
                expired.add(entry.getKey());
            }
        }
        return expired;
    }

    public void forget(String id) {
        if (supersededAt.remove(id) != null) {
            setDirty();
        }
    }

    private static ScratchedExposureRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        ScratchedExposureRegistry data = new ScratchedExposureRegistry();
        data.runCounter = tag.getInt("run");
        CompoundTag superseded = tag.getCompound("superseded");
        for (String id : superseded.getAllKeys()) {
            data.supersededAt.put(id, superseded.getInt(id));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("run", runCounter);
        CompoundTag superseded = new CompoundTag();
        supersededAt.forEach(superseded::putInt);
        tag.put("superseded", superseded);
        return tag;
    }
}
