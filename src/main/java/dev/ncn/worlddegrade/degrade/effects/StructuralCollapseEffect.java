package dev.ncn.worlddegrade.degrade.effects;

import dev.ncn.worlddegrade.degrade.DecayExemptions;
import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.StructureShape;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public class StructuralCollapseEffect implements DegradeEffect {
    private static final float DEBRIS_CHANCE = 0.4f;
    private static final float CAVE_IN_DEBRIS_CHANCE = 0.8f;
    private static final float[] LAYER_CHANCE = {1.0f, 0.5f, 0.2f};
    private static final float RUBBLE_CHANCE = 0.35f;
    private static final float FLOOR_FACTOR = 0.33f;

    private static final int FLOOR_SEARCH_DEPTH = 32;
    private static final int RUBBLE_FALL_DEPTH = 24;
    private static final int MAX_WALL_HEIGHT = 24;
    private static final int CREST_NOISE_CELL = 4;
    private static final int ROOF_NOISE_CELL = 6;
    private static final int ROOF_DETAIL_CELL = 2;
    private static final float ROOF_DETAIL_WEIGHT = 0.4f;
    private static final float ROOF_SPREAD = 1.5f;
    private static final float ROOF_SOFTNESS = 0.3f;
    private static final int SWEEP_BUDGET_FACTOR = 4;

    @Override
    public void apply(DegradeContext ctx) {
        float roof = ctx.chances.roofCollapseChance();
        float wall = ctx.chances.wallCollapseChance();
        if (roof <= 0.0f && wall <= 0.0f) {
            return;
        }
        LongOpenHashSet structure = new LongOpenHashSet(ctx.positions());
        LongOpenHashSet removed = new LongOpenHashSet();
        LongOpenHashSet shavedColumns = new LongOpenHashSet();

        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            if (StructureShape.classify(ctx, pos) == StructureShape.Part.ROOF) {
                collapseRoof(ctx, pos, roof, removed);
            }
        }
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            if (StructureShape.classify(ctx, pos) == StructureShape.Part.WALL_CREST) {
                shaveWall(ctx, pos, wall, removed, shavedColumns);
            }
        }
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            if (StructureShape.classify(ctx, pos) == StructureShape.Part.SURFACE) {
                pitSurface(ctx, pos, wall * FLOOR_FACTOR, removed);
            }
        }
        sweepOrphans(ctx, structure, removed);
    }

    @Override
    public boolean shipSafe() {
        return false;
    }

    private void collapseRoof(DegradeContext ctx, BlockPos pos, float roof, LongOpenHashSet removed) {
        if (roof <= 0.0f) {
            return;
        }
        BlockState state = ctx.state(pos);
        int allowance = ctx.excavationAllowance(pos);
        boolean dug = allowance > 0;
        if (!dug && !BrickWeatherEffect.isFullyWorn(ctx, pos, state)) {
            return;
        }
        if (dug) {
            caveIn(ctx, pos, allowance, roof, removed);
            return;
        }
        float support = StructureShape.supportWeight(
                StructureShape.spanDistance(ctx, pos, StructureShape.MAX_SPAN));
        float rim = Mth.lerp(1.0f - roof, 1.0f, support);

        float field = ctx.noise2d(pos.getX(), pos.getZ(), ROOF_NOISE_CELL) * (1.0f - ROOF_DETAIL_WEIGHT)
                + ctx.noise2d(pos.getX(), pos.getZ(), ROOF_DETAIL_CELL) * ROOF_DETAIL_WEIGHT;
        float threshold = roof * rim * ROOF_SPREAD;
        if (!ctx.roll(Mth.clamp((threshold - field) / ROOF_SOFTNESS + 0.5f, 0.0f, 1.0f))) {
            return;
        }
        if (!ctx.removeBlock(pos)) {
            return;
        }
        removed.add(pos.asLong());
        if (ctx.roll(DEBRIS_CHANCE)) {
            ctx.scatterDebris(pos, state, FLOOR_SEARCH_DEPTH);
        }
    }

    private void caveIn(DegradeContext ctx, BlockPos ceiling, int allowance, float roof,
                        LongOpenHashSet removed) {
        if (!ctx.roll(ctx.patchChance(ceiling, roof))) {
            return;
        }
        float support = StructureShape.supportWeight(
                StructureShape.spanDistance(ctx, ceiling, StructureShape.MAX_SPAN));
        int depth = Mth.clamp(Math.round(allowance * support), 1, allowance);

        BlockPos.MutableBlockPos cursor = ceiling.mutable();
        for (int layer = 0; layer < depth; layer++) {
            float layerChance = LAYER_CHANCE[Math.min(layer, LAYER_CHANCE.length - 1)];
            if (layer > 0 && !ctx.roll(ctx.patchChance(cursor, layerChance))) {
                break;
            }
            BlockState state = ctx.state(cursor);
            if (state.isAir() || !state.isSolid()) {
                break;
            }
            BlockPos at = cursor.immutable();
            if (DecayExemptions.isOre(state)) {
                if (!ctx.scatterDebrisOrKeep(at, state, FLOOR_SEARCH_DEPTH)) {
                    break;
                }
                ctx.removeBlock(at);
                removed.add(at.asLong());
                cursor.move(0, 1, 0);
                continue;
            }
            if (!ctx.removeBlock(at)) {
                break;
            }
            removed.add(at.asLong());
            if (ctx.roll(CAVE_IN_DEBRIS_CHANCE)) {
                ctx.scatterDebris(at, state, FLOOR_SEARCH_DEPTH);
            }
            cursor.move(0, 1, 0);
        }
    }

    private void shaveWall(DegradeContext ctx, BlockPos pos, float wall,
                           LongOpenHashSet removed, LongOpenHashSet shavedColumns) {
        if (wall <= 0.0f) {
            return;
        }
        long column = (long) pos.getX() << 32 | (pos.getZ() & 0xFFFFFFFFL);
        if (!shavedColumns.add(column)) {
            return;
        }
        if (!ctx.roll(ctx.patchChance(pos, wall))) {
            return;
        }
        int height = StructureShape.exposedRunDown(ctx, pos, MAX_WALL_HEIGHT);
        int maxDepth = Math.min(height - 1, 1 + Math.round(wall * 5.0f));
        if (maxDepth <= 0) {
            return;
        }
        float noise = ctx.noise2d(pos.getX(), pos.getZ(), CREST_NOISE_CELL);
        int depth = Math.round(noise * maxDepth);

        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int i = 0; i < depth; i++) {
            BlockState state = ctx.state(cursor);
            if (!BrickWeatherEffect.isFullyWorn(ctx, pos, state)) {
                break;
            }
            BlockPos at = cursor.immutable();
            if (!ctx.removeBlock(at)) {
                break;
            }
            removed.add(at.asLong());
            if (ctx.roll(RUBBLE_CHANCE)) {
                pileRubble(ctx, at, state);
            }
            cursor.move(0, -1, 0);
        }
    }

    private void pitSurface(DegradeContext ctx, BlockPos pos, float chance, LongOpenHashSet removed) {
        if (chance <= 0.0f) {
            return;
        }
        BlockState state = ctx.state(pos);
        if (!BrickWeatherEffect.isFullyWorn(ctx, pos, state)) {
            return;
        }
        if (!ctx.roll(ctx.patchChance(pos, chance))) {
            return;
        }
        if (ctx.removeBlock(pos)) {
            removed.add(pos.asLong());
        }
    }

    private void pileRubble(DegradeContext ctx, BlockPos from, BlockState state) {
        Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(ctx.random);
        BlockPos.MutableBlockPos cursor = from.relative(side).mutable();
        if (!ctx.state(cursor).isAir()) {
            return;
        }
        for (int i = 0; i < RUBBLE_FALL_DEPTH && cursor.getY() > ctx.level.getMinBuildHeight(); i++) {
            if (!ctx.state(cursor.below()).isAir()) {
                ctx.placeExtra(cursor.immutable(), state.getBlock().defaultBlockState());
                return;
            }
            cursor.move(0, -1, 0);
            if (!ctx.state(cursor).isAir()) {
                return;
            }
        }
    }

    private void sweepOrphans(DegradeContext ctx, LongOpenHashSet structure, LongOpenHashSet removed) {
        if (removed.isEmpty()) {
            return;
        }
        int budget = removed.size() * SWEEP_BUDGET_FACTOR;
        LongArrayFIFOQueue frontier = new LongArrayFIFOQueue();
        for (long seed : removed.toLongArray()) {
            frontier.enqueue(seed);
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
                if (state.isAir() || !isOrphaned(ctx, neighbor, state)) {
                    continue;
                }
                if (!ctx.removeBlock(neighbor)) {
                    continue;
                }
                removed.add(key);
                frontier.enqueue(key);
                if (--budget == 0) {
                    return;
                }
            }
        }
    }

    private boolean isOrphaned(DegradeContext ctx, BlockPos pos, BlockState state) {
        if (!state.canSurvive(ctx.level, pos)) {
            return true;
        }
        if (!state.isSolid()) {
            return false;
        }
        if (!ctx.state(pos.below()).isAir() || !ctx.state(pos.above()).isAir()) {
            return false;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (ctx.state(pos.relative(direction)).isSolid()) {
                return false;
            }
        }
        return true;
    }
}
