package dev.ncn.worlddegrade.degrade;

import dev.ncn.worlddegrade.WorldDegrade;
import dev.ncn.worlddegrade.config.WorldDegradeConfig;
import dev.ncn.worlddegrade.degrade.effects.EnderChestLootEffect;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = WorldDegrade.MOD_ID)
public final class EnderChestLootEvents {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!WorldDegradeConfig.lootEnderChestsEnabled()
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        EnderChestLootEffect.applyPending(player);
    }

    private EnderChestLootEvents() {
    }
}
