package dev.ncn.worlddegrade.degrade.effects;

import dev.ncn.worlddegrade.degrade.DegradeContext;

public interface DegradeEffect {
    void apply(DegradeContext ctx);

    default boolean shipSafe() {
        return true;
    }
}
