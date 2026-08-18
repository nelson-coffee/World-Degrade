package dev.ncn.worlddegrade;

import dev.ncn.worlddegrade.compat.CompatManager;
import dev.ncn.worlddegrade.config.WorldDegradeConfig;
import dev.ncn.worlddegrade.tracking.ModAttachments;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(WorldDegrade.MOD_ID)
public class WorldDegrade {
    public static final String MOD_ID = "worlddegrade";

    public WorldDegrade(IEventBus modEventBus, ModContainer modContainer) {
        WorldDegradeConfig.register(modContainer);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        dev.ncn.worlddegrade.block.ModBlocks.BLOCKS.register(modEventBus);
        dev.ncn.worlddegrade.item.ModItems.ITEMS.register(modEventBus);
        modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(CompatManager::init));
        modEventBus.addListener(WorldDegradeConfig::onLoad);
        modEventBus.addListener(WorldDegradeConfig::onReload);
        modEventBus.addListener(WorldDegradeConfig::onUnload);
    }
}
