package dev.ncn.worlddegrade.compat.rechiseled;

import dev.ncn.worlddegrade.degrade.DecayExemptions;
import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.StructureShape;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class RechiseledMaskedMaterialEffect implements DegradeEffect {
    private static final float BREAK_SCALE = 0.33f;

    @Override
    public void apply(DegradeContext ctx) {
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = ctx.state(pos);
            if (state.isAir() || !RechiseledWearTable.maskedVariants().containsKey(state.getBlock())) {
                continue;
            }
            ctx.claim(pos);
            if (DecayExemptions.isExempt(state)) {
                continue;
            }
            if (ctx.deferStructureToCollapse()
                    && StructureShape.classify(ctx, pos) != StructureShape.Part.NONE) {
                continue;
            }
            if (!ctx.roll(ctx.patchChance(pos, ctx.chances.brickWeatherChance() * BREAK_SCALE))) {
                continue;
            }
            if (ctx.blockEntity(pos) != null) {
                ctx.removeBlockAndWipeContents(pos);
            } else {
                ctx.removeBlock(pos);
            }
        }
    }

    public static boolean handles(BlockState state) {
        return !state.isAir() && RechiseledWearTable.maskedVariants().containsKey(state.getBlock());
    }
}
