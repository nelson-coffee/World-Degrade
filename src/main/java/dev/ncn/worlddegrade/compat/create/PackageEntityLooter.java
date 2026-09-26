package dev.ncn.worlddegrade.compat.create;

import com.simibubi.create.content.logistics.box.PackageEntity;
import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.NestedItems;
import dev.ncn.worlddegrade.degrade.effects.ContainerLootEffect;
import dev.ncn.worlddegrade.degrade.effects.EntityLooter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public final class PackageEntityLooter implements EntityLooter {

    @Override
    public boolean handles(Entity entity) {
        return entity instanceof PackageEntity;
    }

    @Override
    public boolean isPlayerMade(DegradeContext ctx, Entity entity) {
        return true;
    }

    @Override
    public boolean loot(DegradeContext ctx, Entity entity, Runnable recordUndo) {
        if (!ctx.nested() || !(entity instanceof PackageEntity pkg)) {
            return false;
        }
        ItemStack box = pkg.getBox();
        if (box.isEmpty()) {
            return false;
        }
        ItemStack working = box.copy();
        NestedItems.Budget budget = ctx.newStackBudget(entity.blockPosition());
        NestedItems.descendInto(working, ContainerLootEffect.keepFractionVisitor(
                ctx, ctx.chances.containerKeepFraction()), budget);
        if (budget.changeCount() == 0) {
            return false;
        }
        recordUndo.run();
        pkg.box = working;
        return true;
    }
}
