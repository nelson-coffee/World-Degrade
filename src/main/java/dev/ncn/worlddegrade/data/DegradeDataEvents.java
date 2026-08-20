package dev.ncn.worlddegrade.data;

import dev.ncn.worlddegrade.WorldDegrade;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

@EventBusSubscriber(modid = WorldDegrade.MOD_ID)
public final class DegradeDataEvents {

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new DegradeDataReloadListener());
    }

    private DegradeDataEvents() {
    }
}
