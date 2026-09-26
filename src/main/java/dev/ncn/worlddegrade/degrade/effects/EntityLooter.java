package dev.ncn.worlddegrade.degrade.effects;

import dev.ncn.worlddegrade.degrade.DegradeContext;
import net.minecraft.world.entity.Entity;

public interface EntityLooter {

    boolean handles(Entity entity);

    default boolean isPlayerMade(DegradeContext ctx, Entity entity) {
        return EntityLootEffect.isTracked(ctx, entity.blockPosition().below());
    }

    boolean loot(DegradeContext ctx, Entity entity, Runnable recordUndo);
}
