package dev.ncn.worlddegrade.compat.exposure;

import dev.ncn.worlddegrade.degrade.DegradeContext;
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
            boolean recorded = false;
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack.isEmpty() || !stack.is(ExposurePhotographs.photograph())) {
                    continue;
                }
                if (!ctx.roll(ctx.chances.brickWeatherChance())) {
                    continue;
                }
                if (!recorded) {
                    ctx.recordForUndo(pos);
                    recorded = true;
                }
                inventory.setStackInSlot(slot,
                        stack.transmuteCopy(ExposurePhotographs.agedPhotograph(), stack.getCount()));
                ctx.markChanged();
            }
        }
    }
}
