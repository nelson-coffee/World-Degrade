package dev.ncn.worlddegrade.compat.exposure;

import dev.ncn.worlddegrade.degrade.NestedItems;
import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.world.item.component.StoredItemStack;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class CameraOpener implements NestedItems.Opener {

    private static final List<DataComponentType<StoredItemStack>> SLOTS =
            List.of(Exposure.DataComponents.FILM, Exposure.DataComponents.FLASH, Exposure.DataComponents.LENS);

    @Override
    public boolean open(ItemStack stack, NestedItems.StackVisitor visitor, NestedItems.Budget budget) {
        boolean recognised = false;
        for (DataComponentType<StoredItemStack> slot : SLOTS) {
            StoredItemStack stored = stack.get(slot);
            if (stored == null || stored.isEmpty()) {
                continue;
            }
            recognised = true;
            ItemStack inner = stored.getCopy();
            int before = budget.changeCount();
            ItemStack result = NestedItems.visitStack(inner, visitor, budget);
            if (result != inner || budget.changeCount() != before) {
                stack.set(slot, new StoredItemStack(result));
                budget.recordChange();
            }
        }
        return recognised;
    }
}
