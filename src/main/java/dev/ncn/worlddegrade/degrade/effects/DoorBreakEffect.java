package dev.ncn.worlddegrade.degrade.effects;

import dev.ncn.worlddegrade.degrade.DegradeContext;
import net.minecraft.core.BlockPos;
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
            if (state.getBlock() instanceof DoorBlock) {
                ctx.claim(pos);
                if (!enabled || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
                    continue;
                }
                if (ctx.roll(ctx.chances.doorBreakChance())) {
                    BlockPos upper = pos.above();
                    if (ctx.state(upper).getBlock() instanceof DoorBlock) {
                        ctx.removeBlock(upper);
                    }
                    ctx.removeBlock(pos);
                }
            } else if (state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock) {
                ctx.claim(pos);
                if (enabled && ctx.roll(ctx.chances.doorBreakChance())) {
                    ctx.removeBlock(pos);
                }
            }
        }
    }
}
