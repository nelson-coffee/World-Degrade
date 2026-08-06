package dev.ncn.worlddegrade.item;

import dev.ncn.worlddegrade.WorldDegrade;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WorldDegrade.MOD_ID);

    public static final DeferredItem<Item> MARKER_WAND = ITEMS.register("marker_wand",
            () -> new MarkerWandItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> BURNT_TORCH = ITEMS.register("burnt_torch",
            () -> new net.minecraft.world.item.StandingAndWallBlockItem(
                    dev.ncn.worlddegrade.block.ModBlocks.BURNT_TORCH.get(),
                    dev.ncn.worlddegrade.block.ModBlocks.BURNT_WALL_TORCH.get(),
                    new Item.Properties(), net.minecraft.core.Direction.DOWN));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> BURNT_LANTERN =
            ITEMS.registerSimpleBlockItem(dev.ncn.worlddegrade.block.ModBlocks.BURNT_LANTERN);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> BURNT_SOUL_LANTERN =
            ITEMS.registerSimpleBlockItem(dev.ncn.worlddegrade.block.ModBlocks.BURNT_SOUL_LANTERN);

    public static final DeferredItem<Item> BURNT_SOUL_TORCH = ITEMS.register("burnt_soul_torch",
            () -> new net.minecraft.world.item.StandingAndWallBlockItem(
                    dev.ncn.worlddegrade.block.ModBlocks.BURNT_SOUL_TORCH.get(),
                    dev.ncn.worlddegrade.block.ModBlocks.BURNT_SOUL_WALL_TORCH.get(),
                    new Item.Properties(), net.minecraft.core.Direction.DOWN));

    private ModItems() {
    }
}
