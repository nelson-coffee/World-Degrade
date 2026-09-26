package dev.ncn.worlddegrade.compat.lootr;

import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import dev.ncn.worlddegrade.tracking.PlacementTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

public class LootrContainerEffect implements DegradeEffect {

    @Override
    public void apply(DegradeContext ctx) {
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            BlockEntity blockEntity = ctx.blockEntity(pos);
            if (blockEntity == null || !isLootr(blockEntity)) {
                continue;
            }
            ctx.claimLoot(pos);
            if (!PlacementTracker.isTracked(ctx.level, pos)) {
                ctx.claim(pos);
            }
        }
    }

    @Override
    public boolean shipSafe() {
        return false;
    }

    private static boolean isLootr(BlockEntity blockEntity) {
        ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
        return id != null && id.getNamespace().equals("lootr");
    }
}
