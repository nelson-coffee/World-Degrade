package dev.ncn.worlddegrade.degrade.effects;

import dev.ncn.worlddegrade.degrade.DegradeContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;

public class OvergrowthEffect implements DegradeEffect {
    private static final int EXTERIOR_SKYLIGHT = 9;
    private static final TagKey<Biome> GROWS_VINES = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("worlddegrade", "grows_vines"));

    @Override
    public void apply(DegradeContext ctx) {
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = ctx.state(pos);
            if (state.is(Blocks.VINE) || state.is(Blocks.COBWEB)) {
                ctx.claim(pos);
            }
            if (state.isAir() || !state.isSolid()) {
                continue;
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos neighbor = pos.relative(direction);
                if (!ctx.state(neighbor).isAir()) {
                    continue;
                }
                if (ctx.level.getBrightness(LightLayer.SKY, neighbor) >= EXTERIOR_SKYLIGHT) {
                    if (ctx.level.getBiome(neighbor).is(GROWS_VINES)
                            && ctx.roll(ctx.chances.vineChance())
                            && state.isFaceSturdy(ctx.level, pos, direction)) {
                        ctx.placeExtra(neighbor, Blocks.VINE.defaultBlockState()
                                .setValue(VineBlock.PROPERTY_BY_DIRECTION.get(direction.getOpposite()), true));
                    }
                } else if (ctx.chances.cobwebChance() > 0
                        && ctx.roll(ctx.chances.cobwebChance())
                        && countSolidNeighbors(ctx, neighbor) >= 2) {
                    ctx.placeExtra(neighbor, Blocks.COBWEB.defaultBlockState());
                }
            }
        }
    }

    @Override
    public boolean shipSafe() {
        return false;
    }

    private int countSolidNeighbors(DegradeContext ctx, BlockPos pos) {
        int solid = 0;
        for (Direction direction : Direction.values()) {
            if (ctx.state(pos.relative(direction)).isSolid()) {
                solid++;
            }
        }
        return solid;
    }
}
