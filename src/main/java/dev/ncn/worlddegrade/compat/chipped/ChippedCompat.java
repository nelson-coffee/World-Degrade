package dev.ncn.worlddegrade.compat.chipped;

import dev.ncn.worlddegrade.degrade.effects.BrickWeatherEffect;
import dev.ncn.worlddegrade.compat.ModCompat;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.util.List;

public class ChippedCompat implements ModCompat {

    @Override
    public String modId() {
        return "chipped";
    }

    @Override
    public void init() {
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) ->
                ChippedWearTable.register(BrickWeatherEffect::addWear));
    }

    @Override
    public List<DegradeEffect> createEffects() {
        return List.of(new ChippedLootEffect());
    }

    @Override
    public List<DegradeEffect> createWeatheringEffects() {
        return List.of(new ChippedWeatherEffect());
    }
}
