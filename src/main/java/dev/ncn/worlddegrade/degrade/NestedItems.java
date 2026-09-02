package dev.ncn.worlddegrade.degrade;

import com.mojang.logging.LogUtils;
import dev.ncn.worlddegrade.compat.CompatManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class NestedItems {
    private static final Logger LOGGER = LogUtils.getLogger();

    @FunctionalInterface
    public interface StackVisitor {
        ItemStack visit(ItemStack stack);
    }

    @FunctionalInterface
    public interface Opener {
        boolean open(ItemStack stack, StackVisitor visitor, Budget budget);
    }

    public static final class Budget {
        private final int limit;
        private final String context;
        private int remaining;
        private boolean exhausted;
        private int changes;

        public Budget(int limit, String context) {
            this.limit = limit;
            this.remaining = limit;
            this.context = context;
        }

        boolean spend() {
            if (remaining <= 0) {
                if (!exhausted) {
                    exhausted = true;
                    LOGGER.warn("World Degrade: nested item walk at {} hit the {}-stack budget; "
                            + "deeper contents left untouched", context, limit);
                }
                return false;
            }
            remaining--;
            return true;
        }

        public void recordChange() {
            changes++;
        }

        public int changeCount() {
            return changes;
        }

        public boolean exhausted() {
            return exhausted;
        }
    }

    private NestedItems() {
    }

    public static boolean walk(IItemHandlerModifiable inventory, StackVisitor visitor, Budget budget) {
        int startingChanges = budget.changes;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (budget.exhausted()) {
                break;
            }
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int before = budget.changes;
            ItemStack result = visitStack(stack, visitor, budget);
            if (result != stack) {
                budget.recordChange();
            }
            if (budget.changes != before) {
                inventory.setStackInSlot(slot, result);
            }
        }
        return budget.changes != startingChanges;
    }

    public static ItemStack visitStack(ItemStack stack, StackVisitor visitor, Budget budget) {
        if (stack.isEmpty() || !budget.spend()) {
            return stack;
        }
        ItemStack result = visitor.visit(stack);
        if (!result.isEmpty()) {
            descend(result, visitor, budget);
        }
        return result;
    }

    private static void descend(ItemStack stack, StackVisitor visitor, Budget budget) {
        IItemHandler handler = stack.getCapability(Capabilities.ItemHandler.ITEM);
        if (handler instanceof IItemHandlerModifiable modifiable) {
            walk(modifiable, visitor, budget);
            return;
        }
        if (openBundle(stack, visitor, budget)) {
            return;
        }
        for (Opener opener : CompatManager.nestedOpeners()) {
            if (opener.open(stack, visitor, budget)) {
                return;
            }
        }
    }

    private static boolean openBundle(ItemStack stack, StackVisitor visitor, Budget budget) {
        BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (contents == null || contents.isEmpty()) {
            return false;
        }
        int before = budget.changes;
        List<ItemStack> updated = new ArrayList<>();
        for (ItemStack inner : contents.itemsCopy()) {
            ItemStack result = visitStack(inner, visitor, budget);
            if (result != inner) {
                budget.recordChange();
            }
            if (!result.isEmpty()) {
                updated.add(result);
            }
        }
        if (budget.changes != before) {
            stack.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(updated));
        }
        return true;
    }
}
