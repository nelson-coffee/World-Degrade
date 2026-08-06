package dev.ncn.worlddegrade.item;

import dev.ncn.worlddegrade.marking.MarkingService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class MarkerWandItem extends Item {

    public MarkerWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("chat.worlddegrade.no_permission"), true);
            return InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) {
            MarkingService.clearSelection(player);
        } else {
            MarkingService.handleBlockClick(player, context.getClickedPos());
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(2)) {
            if (player.isShiftKeyDown()) {
                MarkingService.clearSelection(serverPlayer);
            } else {
                MarkingService.handleAirClick(serverPlayer);
            }
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
