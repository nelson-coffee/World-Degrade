package dev.ncn.worlddegrade.degrade.effects;

import dev.ncn.worlddegrade.data.BlockCategories;
import dev.ncn.worlddegrade.degrade.DecayExemptions;
import dev.ncn.worlddegrade.degrade.DegradeContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class DoorBreakEffect implements DegradeEffect {
    private final boolean enabled;

    public DoorBreakEffect(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void apply(DegradeContext ctx) {
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = ctx.state(pos);
            Block block = state.getBlock();
            boolean builtin = block instanceof DoorBlock || block instanceof TrapDoorBlock
                    || block instanceof FenceGateBlock;
            if (!BlockCategories.is(state, BlockCategories.Category.DOOR, builtin)) {
                continue;
            }
            ctx.claim(pos);
            if (!enabled) {
                continue;
            }
            if (DecayExemptions.isExempt(state)) {
                continue;
            }
            // The two-tall removal only makes sense for real doors; tag-added modded blocks and the
            // single-block trapdoor/fence-gate family fall through to a plain removal.
            if (block instanceof DoorBlock) {
                if (state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
                    continue;
                }
                if (ctx.roll(ctx.chances.doorBreakChance())) {
                    BlockPos upper = pos.above();
                    if (ctx.state(upper).getBlock() instanceof DoorBlock) {
                        ctx.removeBlock(upper);
                    }
                    ctx.removeBlock(pos);
                }
            } else {
                if (ctx.roll(ctx.chances.doorBreakChance())) {
                    ctx.removeBlock(pos);
                }
            }
        }
    }
}
