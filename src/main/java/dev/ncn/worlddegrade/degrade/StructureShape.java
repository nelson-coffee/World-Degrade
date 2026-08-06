package dev.ncn.worlddegrade.degrade;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public final class StructureShape {
    public static final int MAX_SPAN = 6;
    private static final int MIN_WALL_HEIGHT = 3;

    public enum Part {
        NONE,
        ROOF,
        WALL_CREST,
        SURFACE
    }

    private StructureShape() {
    }

    public static Part classify(DegradeContext ctx, BlockPos pos) {
        BlockState state = ctx.state(pos);
        if (state.isAir() || !state.isSolid()) {
            return Part.NONE;
        }
        if (ctx.state(pos.below()).isAir()) {
            return Part.ROOF;
        }
        if (ctx.state(pos.above()).isSolid()) {
            return Part.NONE;
        }
        if (exposedRunDown(ctx, pos, MIN_WALL_HEIGHT) >= MIN_WALL_HEIGHT) {
            return Part.WALL_CREST;
        }
        return ctx.level.canSeeSky(pos.above()) ? Part.SURFACE : Part.NONE;
    }

    public static int exposedRunDown(DegradeContext ctx, BlockPos pos, int max) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        int run = 0;
        while (run < max && ctx.state(cursor).isSolid() && hasOpenSide(ctx, cursor)) {
            run++;
            cursor.move(0, -1, 0);
        }
        return run;
    }

    public static int spanDistance(DegradeContext ctx, BlockPos pos, int max) {
        int nearest = max;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos.MutableBlockPos cursor = pos.mutable();
            for (int step = 1; step <= max; step++) {
                cursor.move(direction);
                if (!ctx.state(cursor).isSolid() || !ctx.state(cursor.below()).isAir()) {
                    nearest = Math.min(nearest, step);
                    break;
                }
            }
        }
        return nearest;
    }

    public static float supportWeight(int spanDistance) {
        return Mth.clamp((spanDistance - 1) / 2.0f, 0.15f, 1.0f);
    }

    static boolean hasOpenSide(DegradeContext ctx, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!ctx.state(pos.relative(direction)).isSolid()) {
                return true;
            }
        }
        return false;
    }
}
