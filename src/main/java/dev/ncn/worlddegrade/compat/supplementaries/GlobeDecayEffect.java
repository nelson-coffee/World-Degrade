package dev.ncn.worlddegrade.compat.supplementaries;

import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class GlobeDecayEffect implements DegradeEffect {
    private final Block globe = byId("globe");
    private final Block sepiaGlobe = byId("globe_sepia");

    private static Block byId(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("supplementaries", path));
    }

    @Override
    public void apply(DegradeContext ctx) {
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = ctx.state(pos);
            if (state.isAir()) {
                continue;
            }
            Block block = state.getBlock();
            if (block == globe || block == sepiaGlobe) {
                ctx.claim(pos);
            }
            if (block == globe) {
                if (ctx.roll(ctx.patchChance(pos, ctx.chances.brickWeatherChance()))) {
                    ctx.replaceBlockPreservingEntity(pos, sepiaGlobe.withPropertiesOf(state));
                }
            } else if (block == sepiaGlobe) {
                if (ctx.roll(ctx.patchChance(pos, ctx.chances.woodRotChance()))) {
                    ctx.removeBlock(pos);
                }
            }
        }
    }
}
