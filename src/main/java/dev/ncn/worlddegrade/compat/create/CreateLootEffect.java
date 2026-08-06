package dev.ncn.worlddegrade.compat.create;

import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public class CreateLootEffect implements DegradeEffect {

    @Override
    public void apply(DegradeContext ctx) {
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            if (!(ctx.blockEntity(pos) instanceof ItemVaultBlockEntity vault)) {
                continue;
            }
            if (!ctx.claimLoot(pos)) {
                continue;
            }
            ctx.recordForUndo(pos);
            ItemStackHandler inventory = vault.getInventoryOfBlock();
            boolean changed = false;
            float keep = ctx.chances.containerKeepFraction();
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                int surviving = survivingCount(ctx, stack.getCount(), keep);
                if (surviving != stack.getCount()) {
                    inventory.setStackInSlot(slot, surviving == 0 ? ItemStack.EMPTY : stack.copyWithCount(surviving));
                    changed = true;
                }
            }
            if (changed) {
                vault.setChanged();
                ctx.markChanged();
            }
        }
    }

    static int survivingCount(DegradeContext ctx, int count, float keepFraction) {
        int surviving = 0;
        for (int i = 0; i < count; i++) {
            if (ctx.roll(keepFraction)) {
                surviving++;
            }
        }
        return surviving;
    }
}
