package dev.ncn.worlddegrade.degrade.effects;

import dev.ncn.worlddegrade.degrade.DegradeContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class LeafGrowthEffect implements DegradeEffect {
    private static final float[] GROWTH_CHANCE = {0.00f, 0.02f, 0.05f, 0.10f, 0.20f};

    @Override
    public void apply(DegradeContext ctx) {
        float chance = GROWTH_CHANCE[Mth.clamp(ctx.chances.levelId(), 1, 5) - 1];
        if (chance <= 0.0f) {
            return;
        }
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = ctx.state(pos);
            if (!state.is(BlockTags.LEAVES) || !ctx.roll(chance)) {
                continue;
            }
            grow(ctx, pos, state);
        }
    }

    @Override
    public boolean shipSafe() {
        return false;
    }

    private void grow(DegradeContext ctx, BlockPos pos, BlockState state) {
        Direction direction = pickDirection(ctx, pos);
        if (direction == null) {
            return;
        }
        BlockPos target = pos.relative(direction);
        if (!ctx.state(target).isAir()) {
            return;
        }
        BlockState leaf = state;
        if (leaf.hasProperty(BlockStateProperties.PERSISTENT)) {
            leaf = leaf.setValue(BlockStateProperties.PERSISTENT, true);
        }
        if (leaf.hasProperty(BlockStateProperties.DISTANCE)) {
            leaf = leaf.setValue(BlockStateProperties.DISTANCE, 1);
        }
        ctx.placeExtra(target, leaf);
    }

    private Direction pickDirection(DegradeContext ctx, BlockPos pos) {
        Direction[] options = againstWall(ctx, pos)
                ? new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH,
                        Direction.EAST, Direction.WEST, Direction.UP}
                : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH,
                        Direction.EAST, Direction.WEST};
        return options[ctx.random.nextInt(options.length)];
    }

    private boolean againstWall(DegradeContext ctx, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (ctx.state(pos.relative(direction)).isSolid()) {
                return true;
            }
        }
        return false;
    }
}
