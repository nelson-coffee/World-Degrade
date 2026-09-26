package dev.ncn.worlddegrade.compat.create;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.box.PackageItem;
import dev.ncn.worlddegrade.degrade.NestedItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.items.ComponentItemHandler;

public final class PackageOpener implements NestedItems.Opener {

    public static boolean hasContents(ItemStack stack) {
        return PackageItem.isPackage(stack)
                && stack.getOrDefault(AllDataComponents.PACKAGE_CONTENTS, ItemContainerContents.EMPTY)
                        .nonEmptyStream().findAny().isPresent();
    }

    @Override
    public boolean recognises(ItemStack stack) {
        return hasContents(stack);
    }

    @Override
    public boolean open(ItemStack stack, NestedItems.StackVisitor visitor, NestedItems.Budget budget) {
        if (!PackageItem.isPackage(stack)) {
            return false;
        }
        NestedItems.walk(new ComponentItemHandler(stack, AllDataComponents.PACKAGE_CONTENTS,
                PackageItem.SLOTS), visitor, budget);
        return true;
    }
}
