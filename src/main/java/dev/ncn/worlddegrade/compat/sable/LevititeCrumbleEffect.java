package dev.ncn.worlddegrade.compat.sable;

import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.DegradeLevel;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

public class LevititeCrumbleEffect implements DegradeEffect {
    private static final List<String> LEVITITE_IDS =
            List.of("levitite", "levitite_blend", "pearlescent_levitite");

    private final List<Block> levititeBlocks = new ArrayList<>();

    public LevititeCrumbleEffect() {
        for (String id : LEVITITE_IDS) {
            BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("aeronautics", id))
                    .ifPresent(levititeBlocks::add);
        }
    }

    @Override
    public void apply(DegradeContext ctx) {
        if (levititeBlocks.isEmpty() || ctx.chances.levelId() < DegradeLevel.RUINED.id()) {
            return;
        }
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            if (!levititeBlocks.contains(ctx.state(pos).getBlock())) {
                continue;
            }
            ctx.claim(pos);
            if (ctx.roll(ctx.patchChance(pos, ctx.chances.woodRotChance()))) {
                ctx.removeBlock(pos);
            }
        }
    }
}
