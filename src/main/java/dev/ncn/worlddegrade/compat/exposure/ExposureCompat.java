package dev.ncn.worlddegrade.compat.exposure;

import dev.ncn.worlddegrade.compat.ModCompat;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import java.util.List;

public class ExposureCompat implements ModCompat {

    @Override
    public String modId() {
        return "exposure";
    }

    @Override
    public List<DegradeEffect> createEffects() {
        return List.of(new PhotographFrameAgeEffect(), new StoredPhotographAgeEffect(),
                new FilmScratchEffect());
    }

    @Override
    public void onUndo(MinecraftServer server, CompoundTag compatSection) {
        PhotographFrameAgeEffect.restore(server, compatSection);
    }
}
