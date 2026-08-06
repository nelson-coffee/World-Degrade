package dev.ncn.worlddegrade.degrade.effects;

import dev.ncn.worlddegrade.degrade.DegradeContext;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class SurvivalSweepEffect implements DegradeEffect {
    private static final int BUDGET_FACTOR = 2;

    @Override
    public void apply(DegradeContext ctx) {
        long[] positions = ctx.positions();
        LongOpenHashSet structure = new LongOpenHashSet(positions);
        LongOpenHashSet removed = new LongOpenHashSet();
        LongArrayFIFOQueue frontier = new LongArrayFIFOQueue();
        int budget = positions.length * BUDGET_FACTOR;

        for (long packed : positions) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = ctx.state(pos);
            if (state.isAir() || state.canSurvive(ctx.level, pos)) {
                continue;
            }
            ctx.removeBlock(pos);
            removed.add(packed);
            frontier.enqueue(packed);
            if (--budget <= 0) {
                return;
            }
        }

        while (!frontier.isEmpty() && budget > 0) {
            BlockPos pos = BlockPos.of(frontier.dequeueLong());
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                long key = neighbor.asLong();
                if (!structure.contains(key) || removed.contains(key)) {
                    continue;
                }
                BlockState state = ctx.state(neighbor);
                if (state.isAir() || state.canSurvive(ctx.level, neighbor)) {
                    continue;
                }
                ctx.removeBlock(neighbor);
                removed.add(key);
                frontier.enqueue(key);
                if (--budget <= 0) {
                    return;
                }
            }
        }
    }
}
