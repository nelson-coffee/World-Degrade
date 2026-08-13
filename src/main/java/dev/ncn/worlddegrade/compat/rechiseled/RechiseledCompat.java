package dev.ncn.worlddegrade.compat.rechiseled;

import dev.ncn.worlddegrade.compat.ModCompat;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.util.List;

public class RechiseledCompat implements ModCompat {

    @Override
    public String modId() {
        return "rechiseled";
    }

    @Override
    public void init() {
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) ->
                RechiseledWearTable.register(event.getServer()));
    }

    @Override
    public List<DegradeEffect> createEffects() {
        return List.of(new RechiseledMaskedMaterialEffect());
    }
}
