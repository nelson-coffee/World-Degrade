package dev.ncn.worlddegrade.compat.supplementaries;

import dev.ncn.worlddegrade.compat.ModCompat;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;

import java.util.List;

public class SupplementariesCompat implements ModCompat {

    @Override
    public String modId() {
        return "supplementaries";
    }

    @Override
    public List<DegradeEffect> createEffects() {
        return List.of(new GlobeDecayEffect());
    }
}
