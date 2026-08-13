package dev.ncn.worlddegrade.client;

import dev.ncn.worlddegrade.WorldDegrade;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = WorldDegrade.MOD_ID, value = Dist.CLIENT)
public final class ClientDisconnectHandler {

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MarkedRegionsClientCache.setRegions(java.util.List.of());
        MarkedRegionsClientCache.setSelection(null, null);
    }

    private ClientDisconnectHandler() {
    }
}
