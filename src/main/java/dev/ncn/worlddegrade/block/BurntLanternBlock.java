package dev.ncn.worlddegrade.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

public class BurntLanternBlock extends LanternBlock {
    private final Supplier<Block> litCounterpart;

    public BurntLanternBlock(Supplier<Block> litCounterpart, Properties properties) {
        super(properties);
        this.litCounterpart = litCounterpart;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        return TorchRelighting.tryRelight(stack, level, pos, player, hand,
                litCounterpart.get().withPropertiesOf(state));
    }
}
