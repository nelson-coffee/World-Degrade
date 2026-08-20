package dev.ncn.worlddegrade.degrade.effects;

import dev.ncn.worlddegrade.data.BlockCategories;
import dev.ncn.worlddegrade.degrade.DecayExemptions;
import dev.ncn.worlddegrade.degrade.DegradeContext;
import net.minecraft.core.BlockPos;
import dev.ncn.worlddegrade.block.BurntLanternBlock;
import dev.ncn.worlddegrade.block.BurntTorchBlock;
import dev.ncn.worlddegrade.block.BurntWallTorchBlock;
import dev.ncn.worlddegrade.block.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class LightSnuffEffect implements DegradeEffect {
    private final boolean enabled;
    private final boolean burntVariants;

    public LightSnuffEffect(boolean enabled, boolean burntVariants) {
        this.enabled = enabled;
        this.burntVariants = burntVariants;
    }

    @Override
    public void apply(DegradeContext ctx) {
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = ctx.state(pos);
            Block block = state.getBlock();
            boolean builtin = block instanceof CampfireBlock || block instanceof TorchBlock
                    || block instanceof WallTorchBlock || block instanceof LanternBlock
                    || block instanceof BurntTorchBlock || block instanceof BurntWallTorchBlock
                    || block instanceof BurntLanternBlock;
            if (BlockCategories.is(state, BlockCategories.Category.LIGHT, builtin)) {
                ctx.claim(pos);
            }
            if (!enabled) {
                continue;
            }
            if (DecayExemptions.isExempt(state)) {
                continue;
            }
            if (block instanceof CampfireBlock) {
                if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)
                        && ctx.roll(ctx.chances.campfireExtinguishChance())) {
                    ctx.replaceBlock(pos, state.setValue(BlockStateProperties.LIT, false));
                }
                continue;
            }
            if (block instanceof BurntTorchBlock || block instanceof BurntWallTorchBlock
                    || block instanceof BurntLanternBlock) {
                continue;
            }
            if (block instanceof WallTorchBlock) {
                if (ctx.roll(ctx.chances.campfireExtinguishChance())) {
                    if (burntVariants) {
                        Block burnt = isSoulVariant(block)
                                ? ModBlocks.BURNT_SOUL_WALL_TORCH.get()
                                : ModBlocks.BURNT_WALL_TORCH.get();
                        ctx.replaceBlock(pos, burnt.defaultBlockState().setValue(
                                BlockStateProperties.HORIZONTAL_FACING,
                                state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
                    } else {
                        ctx.removeBlock(pos);
                    }
                }
                continue;
            }
            if (block instanceof TorchBlock) {
                if (ctx.roll(ctx.chances.campfireExtinguishChance())) {
                    if (burntVariants) {
                        Block burnt = isSoulVariant(block)
                                ? ModBlocks.BURNT_SOUL_TORCH.get()
                                : ModBlocks.BURNT_TORCH.get();
                        ctx.replaceBlock(pos, burnt.defaultBlockState());
                    } else {
                        ctx.removeBlock(pos);
                    }
                }
                continue;
            }
            if (block instanceof LanternBlock && ctx.roll(ctx.chances.campfireExtinguishChance())) {
                if (burntVariants) {
                    Block burnt = isSoulVariant(block)
                            ? ModBlocks.BURNT_SOUL_LANTERN.get()
                            : ModBlocks.BURNT_LANTERN.get();
                    ctx.replaceBlock(pos, burnt.withPropertiesOf(state));
                } else {
                    ctx.removeBlock(pos);
                }
            }
        }
    }

    private static boolean isSoulVariant(Block block) {
        return block == Blocks.SOUL_TORCH || block == Blocks.SOUL_WALL_TORCH || block == Blocks.SOUL_LANTERN
                || net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath().contains("soul");
    }
}
