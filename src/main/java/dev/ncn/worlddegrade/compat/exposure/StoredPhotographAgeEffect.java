package dev.ncn.worlddegrade.compat.exposure;

import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.NestedItems;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

public class StoredPhotographAgeEffect implements DegradeEffect {

    @Override
    public void apply(DegradeContext ctx) {
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            if (ctx.blockEntity(pos) == null) {
                continue;
            }
            IItemHandler handler = ctx.level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (!(handler instanceof IItemHandlerModifiable inventory)) {
                continue;
            }
            boolean[] recorded = {false};
            NestedItems.StackVisitor visitor = stack -> {
                if (!stack.is(ExposurePhotographs.photograph())
                        || !ctx.roll(ctx.chances.brickWeatherChance())) {
                    return stack;
                }
                if (!recorded[0]) {
                    ctx.recordForUndo(pos);
                    recorded[0] = true;
                }
                ctx.markChanged();
                return stack.transmuteCopy(ExposurePhotographs.agedPhotograph(), stack.getCount());
            };
            if (ctx.nested()) {
                NestedItems.walk(inventory, visitor, ctx.newStackBudget(pos));
            } else {
                for (int slot = 0; slot < inventory.getSlots(); slot++) {
                    ItemStack stack = inventory.getStackInSlot(slot);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    ItemStack result = visitor.visit(stack);
                    if (result != stack) {
                        inventory.setStackInSlot(slot, result);
                    }
                }
            }
        }
    }
}
