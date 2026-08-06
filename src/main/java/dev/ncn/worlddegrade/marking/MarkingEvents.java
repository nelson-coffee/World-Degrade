package dev.ncn.worlddegrade.marking;

import dev.ncn.worlddegrade.WorldDegrade;
import dev.ncn.worlddegrade.item.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = WorldDegrade.MOD_ID)
public final class MarkingEvents {

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.isShiftKeyDown() || !event.getItemStack().is(ModItems.MARKER_WAND.get())) {
            return;
        }
        event.setCanceled(true);
        MarkingService.deleteAimedRegion(player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MarkingService.syncRegionsTo(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MarkingService.syncRegionsTo(player);
        }
    }

    private MarkingEvents() {
    }
}
