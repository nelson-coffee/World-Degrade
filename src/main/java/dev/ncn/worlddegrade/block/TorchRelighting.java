package dev.ncn.worlddegrade.block;

import dev.ncn.worlddegrade.tracking.PlacementTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;

final class TorchRelighting {

    static ItemInteractionResult tryRelight(ItemStack stack, Level level, BlockPos pos,
                                            Player player, InteractionHand hand, BlockState litState) {
        boolean consumable = stack.is(Items.COAL) || stack.is(Items.CHARCOAL) || stack.is(Items.FIRE_CHARGE);
        boolean igniter = stack.is(Items.FLINT_AND_STEEL);
        if (!consumable && !igniter) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            if (consumable) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            } else {
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            }
            level.setBlock(pos, litState, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
            if (level instanceof ServerLevel serverLevel) {
                PlacementTracker.track(serverLevel, pos);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private TorchRelighting() {
    }
}
