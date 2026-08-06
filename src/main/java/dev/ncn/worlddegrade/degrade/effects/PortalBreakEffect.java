package dev.ncn.worlddegrade.degrade.effects;

import dev.ncn.worlddegrade.degrade.DegradeContext;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;

public class PortalBreakEffect implements DegradeEffect {
    private static final float[] BREAK_CHANCE = {0.5f, 0.5f, 1.0f, 1.0f, 1.0f};

    private static final int MAX_PORTAL_BLOCKS = 1024;

    @Override
    public void apply(DegradeContext ctx) {
        float chance = BREAK_CHANCE[Mth.clamp(ctx.chances.levelId(), 1, 5) - 1];
        if (chance <= 0.0f) {
            return;
        }
        LongOpenHashSet handled = new LongOpenHashSet();
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (!ctx.state(neighbor).is(Blocks.NETHER_PORTAL)
                        || handled.contains(neighbor.asLong())) {
                    continue;
                }
                extinguish(ctx, neighbor, handled, chance);
            }
        }
    }

    private void extinguish(DegradeContext ctx, BlockPos start, LongOpenHashSet handled, float chance) {
        LongOpenHashSet sheet = new LongOpenHashSet();
        LongArrayFIFOQueue frontier = new LongArrayFIFOQueue();
        frontier.enqueue(start.asLong());
        sheet.add(start.asLong());

        while (!frontier.isEmpty() && sheet.size() < MAX_PORTAL_BLOCKS) {
            BlockPos pos = BlockPos.of(frontier.dequeueLong());
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (!ctx.state(neighbor).is(Blocks.NETHER_PORTAL)) {
                    continue;
                }
                if (sheet.add(neighbor.asLong())) {
                    frontier.enqueue(neighbor.asLong());
                }
            }
        }
        handled.addAll(sheet);
        if (!ctx.roll(chance)) {
            return;
        }
        for (long packed : sheet) {
            BlockPos pos = BlockPos.of(packed);
            ctx.claim(pos);
            ctx.removeBlock(pos);
        }
    }
}
