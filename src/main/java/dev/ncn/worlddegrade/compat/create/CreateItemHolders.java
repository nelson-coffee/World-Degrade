package dev.ncn.worlddegrade.compat.create;

import com.simibubi.create.content.decoration.placard.PlacardBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.NestedItems;
import dev.ncn.worlddegrade.degrade.effects.ContainerLootEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public final class CreateItemHolders {

    private CreateItemHolders() {
    }

    public static boolean handles(BlockEntity blockEntity) {
        return blockEntity instanceof PlacardBlockEntity || blockEntity instanceof ChuteBlockEntity
                || blockEntity instanceof ChainConveyorBlockEntity;
    }

    public static boolean holdsItems(BlockEntity blockEntity) {
        return blockEntity instanceof PlacardBlockEntity placard && !placard.getHeldItem().isEmpty();
    }

    public static boolean loot(DegradeContext ctx, BlockPos pos, BlockEntity blockEntity) {
        if (blockEntity instanceof PlacardBlockEntity placard) {
            return lootDisplay(ctx, pos, placard.getHeldItem(), placard::setHeldItem);
        }
        if (blockEntity instanceof ChuteBlockEntity chute) {
            return lootHeld(ctx, pos, chute.getItem(), chute::setItem);
        }
        if (blockEntity instanceof ChainConveyorBlockEntity conveyor) {
            return lootConveyor(ctx, pos, conveyor);
        }
        return false;
    }

    private static boolean lootConveyor(DegradeContext ctx, BlockPos pos,
                                        ChainConveyorBlockEntity conveyor) {
        if (!ctx.nested()) {
            return false;
        }
        NestedItems.StackVisitor visitor =
                ContainerLootEffect.keepFractionVisitor(ctx, ctx.chances.containerKeepFraction());
        NestedItems.Budget budget = ctx.newStackBudget(pos);
        boolean changed = lootPackages(conveyor.getLoopingPackages(), visitor, budget);
        for (List<ChainConveyorPackage> travelling : conveyor.getTravellingPackages().values()) {
            changed |= lootPackages(travelling, visitor, budget);
        }
        if (!changed) {
            return false;
        }
        ctx.recordForUndo(pos);
        conveyor.notifyUpdate();
        ctx.markChanged();
        return true;
    }

    private static boolean lootPackages(List<ChainConveyorPackage> packages,
                                        NestedItems.StackVisitor visitor, NestedItems.Budget budget) {
        boolean changed = false;
        Iterator<ChainConveyorPackage> iterator = packages.iterator();
        while (iterator.hasNext()) {
            ChainConveyorPackage carried = iterator.next();
            if (carried.item.isEmpty()) {
                continue;
            }
            int before = budget.changeCount();
            ItemStack working = carried.item.copy();
            NestedItems.descendInto(working, visitor, budget);
            if (budget.changeCount() == before) {
                continue;
            }
            if (PackageOpener.hasContents(working) && !PackageOpener.isGuardOnly(working)) {
                carried.item = working;
            } else {
                iterator.remove();
            }
            changed = true;
        }
        return changed;
    }

    private static boolean lootDisplay(DegradeContext ctx, BlockPos pos, ItemStack held,
                                       Consumer<ItemStack> sink) {
        if (held.isEmpty()) {
            return false;
        }
        if (NestedItems.isContainer(held)) {
            ctx.recordForUndo(pos);
            sink.accept(ItemStack.EMPTY);
            ctx.markChanged();
            return true;
        }
        return lootHeld(ctx, pos, held, sink);
    }

    private static boolean lootHeld(DegradeContext ctx, BlockPos pos, ItemStack held,
                                    Consumer<ItemStack> sink) {
        if (held.isEmpty()) {
            return false;
        }
        NestedItems.StackVisitor visitor =
                ContainerLootEffect.keepFractionVisitor(ctx, ctx.chances.containerKeepFraction());
        ItemStack working = held.copy();
        NestedItems.Budget budget = ctx.newStackBudget(pos);
        ItemStack result = ctx.nested()
                ? NestedItems.visitStack(working, visitor, budget)
                : visitor.visit(working);
        if (result == working && budget.changeCount() == 0) {
            return false;
        }
        ctx.recordForUndo(pos);
        sink.accept(result);
        ctx.markChanged();
        return true;
    }
}
