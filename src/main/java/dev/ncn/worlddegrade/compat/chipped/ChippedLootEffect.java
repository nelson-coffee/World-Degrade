package dev.ncn.worlddegrade.compat.chipped;

import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.effects.ContainerLootEffect;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ChippedLootEffect implements DegradeEffect {
    private static final TagKey<Block> CHIPPED_BARRELS =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("chipped", "barrel"));

    @Override
    public void apply(DegradeContext ctx) {
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = ctx.state(pos);
            if (!state.is(CHIPPED_BARRELS)
                    || !BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("chipped")) {
                continue;
            }
            if (ctx.blockEntity(pos) instanceof RandomizableContainerBlockEntity container) {
                ContainerLootEffect.lootContainer(ctx, pos, container);
            }
        }
    }
}
