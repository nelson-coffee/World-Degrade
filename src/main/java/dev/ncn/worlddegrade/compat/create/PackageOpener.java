package dev.ncn.worlddegrade.compat.create;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.box.PackageItem;
import dev.ncn.worlddegrade.degrade.NestedItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.items.ComponentItemHandler;

import java.util.List;

public final class PackageOpener implements NestedItems.Opener {

    private static final Item GUARD = Items.COBWEB;

    public static boolean hasContents(ItemStack stack) {
        return PackageItem.isPackage(stack)
                && stack.getOrDefault(AllDataComponents.PACKAGE_CONTENTS, ItemContainerContents.EMPTY)
                        .nonEmptyStream().findAny().isPresent();
    }

    @Override
    public boolean recognises(ItemStack stack) {
        return hasContents(stack);
    }

    public static boolean isGuardOnly(ItemStack stack) {
        if (!PackageItem.isPackage(stack)) {
            return false;
        }
        List<ItemStack> items = stack
                .getOrDefault(AllDataComponents.PACKAGE_CONTENTS, ItemContainerContents.EMPTY)
                .nonEmptyStream().toList();
        return items.size() == 1 && items.get(0).is(GUARD);
    }

    @Override
    public boolean open(ItemStack stack, NestedItems.StackVisitor visitor, NestedItems.Budget budget) {
        if (!PackageItem.isPackage(stack)) {
            return false;
        }
        int before = budget.changeCount();
        NestedItems.walk(new ComponentItemHandler(stack, AllDataComponents.PACKAGE_CONTENTS,
                PackageItem.SLOTS), visitor, budget);
        if (budget.changeCount() != before && !hasContents(stack)) {
            stack.set(AllDataComponents.PACKAGE_CONTENTS,
                    ItemContainerContents.fromItems(List.of(new ItemStack(GUARD))));
        }
        return true;
    }
}
