package dev.ncn.worlddegrade.degrade.effects;

import dev.ncn.worlddegrade.degrade.DegradeContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

public class GlassBreakEffect implements DegradeEffect {
    private final boolean enabled;

    public GlassBreakEffect(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void apply(DegradeContext ctx) {
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = ctx.state(pos);
            if (!state.is(Tags.Blocks.GLASS_BLOCKS) && !state.is(Tags.Blocks.GLASS_PANES)) {
                continue;
            }
            ctx.claim(pos);
            if (!enabled) {
                continue;
            }
            if (!BrickWeatherEffect.isFullyWorn(ctx, pos, state)) {
                continue;
            }
            if (ctx.roll(ctx.chances.glassBreakChance())) {
                ctx.removeBlock(pos);
            }
        }
    }
}
