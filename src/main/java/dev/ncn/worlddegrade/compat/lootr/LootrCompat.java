package dev.ncn.worlddegrade.compat.lootr;

import dev.ncn.worlddegrade.compat.ModCompat;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;

import java.util.List;

public class LootrCompat implements ModCompat {

    @Override
    public String modId() {
        return "lootr";
    }

    @Override
    public List<DegradeEffect> createEffects() {
        return List.of();
    }

    @Override
    public List<DegradeEffect> createWeatheringEffects() {
        return List.of(new LootrContainerEffect());
    }
}
