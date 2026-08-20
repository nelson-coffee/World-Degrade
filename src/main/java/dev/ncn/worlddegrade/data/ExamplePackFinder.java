package dev.ncn.worlddegrade.data;

import dev.ncn.worlddegrade.WorldDegrade;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.AddPackFindersEvent;

public final class ExamplePackFinder {

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.addPackFinders(
                ResourceLocation.fromNamespaceAndPath(WorldDegrade.MOD_ID, "example_datapack"),
                PackType.SERVER_DATA,
                Component.translatable("pack.worlddegrade.examples"),
                PackSource.BUILT_IN,
                false,
                Pack.Position.TOP);
    }

    private ExamplePackFinder() {
    }
}
