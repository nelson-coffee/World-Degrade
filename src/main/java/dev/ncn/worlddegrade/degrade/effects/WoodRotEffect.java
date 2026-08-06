package dev.ncn.worlddegrade.degrade.effects;

import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.StructureShape;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

public class WoodRotEffect implements DegradeEffect {

    @Override
    public void apply(DegradeContext ctx) {
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = ctx.state(pos);
            if (!isWood(state)) {
                continue;
            }
            ctx.claim(pos);
            if (!BrickWeatherEffect.isFullyWorn(ctx, pos, state)) {
                continue;
            }
            if (ctx.deferStructureToCollapse()
                    && StructureShape.classify(ctx, pos) != StructureShape.Part.NONE) {
                continue;
            }
            if (ctx.roll(ctx.patchChance(pos, ctx.chances.woodRotChance()))) {
                ctx.removeBlock(pos);
            }
        }
    }

    public static boolean isWood(BlockState state) {
        return state.is(BlockTags.PLANKS)
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.WOODEN_STAIRS)
                || state.is(BlockTags.WOODEN_SLABS)
                || state.is(BlockTags.WOODEN_FENCES)
                || state.is(BlockTags.BAMBOO_BLOCKS)
                || state.is(net.minecraft.world.level.block.Blocks.BAMBOO_MOSAIC)
                || state.is(net.minecraft.world.level.block.Blocks.MANGROVE_ROOTS)
                || state.is(net.minecraft.world.level.block.Blocks.MUDDY_MANGROVE_ROOTS);
    }
}
