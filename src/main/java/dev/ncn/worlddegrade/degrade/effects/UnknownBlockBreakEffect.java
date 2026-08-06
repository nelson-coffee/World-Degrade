package dev.ncn.worlddegrade.degrade.effects;

import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.StructureShape;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class UnknownBlockBreakEffect implements DegradeEffect {

    @Override
    public void apply(DegradeContext ctx) {
        float chance = ctx.chances.unknownBreakChance();
        if (chance <= 0.0f) {
            return;
        }
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            if (ctx.isClaimed(pos)) {
                continue;
            }
            BlockState state = ctx.state(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            if (state.getDestroySpeed(ctx.level, pos) < 0) {
                continue;
            }
            if (!BrickWeatherEffect.isFullyWorn(ctx, pos, state)) {
                continue;
            }
            if (ctx.deferStructureToCollapse()
                    && StructureShape.classify(ctx, pos) != StructureShape.Part.NONE) {
                continue;
            }
            if (!ctx.roll(ctx.patchChance(pos, chance))) {
                continue;
            }
            if (ctx.blockEntity(pos) != null) {
                ctx.removeBlockAndWipeContents(pos);
            } else {
                ctx.removeBlock(pos);
            }
        }
    }
}
