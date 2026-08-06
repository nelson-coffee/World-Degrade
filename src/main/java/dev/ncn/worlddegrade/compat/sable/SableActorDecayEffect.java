package dev.ncn.worlddegrade.compat.sable;

import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

public class SableActorDecayEffect implements DegradeEffect {
    private final boolean createLoaded = ModList.get().isLoaded("create");

    @Override
    public void apply(DegradeContext ctx) {
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            BlockEntity blockEntity = ctx.blockEntity(pos);
            if (!(blockEntity instanceof BlockEntitySubLevelActor)) {
                continue;
            }
            ctx.claim(pos);
            if (createLoaded && CreateOverlapCheck.isCreateMachine(blockEntity)) {
                continue;
            }
            if (ctx.roll(ctx.chances.machineBreakChance())) {
                ctx.removeBlockAndWipeContents(pos);
            }
        }
    }

    private static final class CreateOverlapCheck {
        static boolean isCreateMachine(BlockEntity blockEntity) {
            return blockEntity instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
        }
    }
}
