package dev.ncn.worlddegrade.compat.exposure;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

final class ExposurePhotographs {
    private static Item photograph;
    private static Item agedPhotograph;

    private ExposurePhotographs() {
    }

    static Item photograph() {
        if (photograph == null) {
            photograph = byId("photograph");
        }
        return photograph;
    }

    static Item agedPhotograph() {
        if (agedPhotograph == null) {
            agedPhotograph = byId("aged_photograph");
        }
        return agedPhotograph;
    }

    private static Item byId(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("exposure", path));
    }
}
