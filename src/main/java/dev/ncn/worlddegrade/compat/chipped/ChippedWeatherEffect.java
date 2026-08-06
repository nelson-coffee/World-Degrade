package dev.ncn.worlddegrade.compat.chipped;

import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

public class ChippedWeatherEffect implements DegradeEffect {
    private static final String CHIPPED = "chipped";

    @Override
    public void apply(DegradeContext ctx) {
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = ctx.state(pos);
            if (state.isAir()
                    || !BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(CHIPPED)) {
                continue;
            }
            ctx.claim(pos);
            if (isWorkbench(state)) {
                if (ctx.roll(ctx.patchChance(pos, ctx.chances.woodRotChance()))) {
                    ctx.removeBlockAndWipeContents(pos);
                }
            } else if (isDecorativeTorch(state)) {
                if (ctx.roll(ctx.chances.campfireExtinguishChance())) {
                    ctx.removeBlock(pos);
                }
            }
        }
    }

    public static boolean handles(BlockState state) {
        return isWorkbench(state) || isDecorativeTorch(state);
    }

    private static final java.util.Set<String> WORKBENCHES = java.util.Set.of(
            "alchemy_bench", "botanist_workbench", "carpenters_table", "glassblower",
            "loom_table", "mason_table", "tinkering_table");

    private static boolean isWorkbench(BlockState state) {
        return WORKBENCHES.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
    }

    private static boolean isDecorativeTorch(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("torch");
    }
}
