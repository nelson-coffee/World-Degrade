package dev.ncn.worlddegrade.degrade.effects;

import dev.ncn.worlddegrade.degrade.DegradeContext;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

public class ContainerLootEffect implements DegradeEffect {
    private final LongOpenHashSet lootedPositions = new LongOpenHashSet();

    @Override
    public void apply(DegradeContext ctx) {
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            if (ctx.blockEntity(pos) == null) {
                continue;
            }
            IItemHandler handler = ctx.level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (!(handler instanceof IItemHandlerModifiable modifiable)) {
                continue;
            }
            ctx.claim(pos);
            if (!claimInventory(ctx, pos) || !ctx.claimLoot(pos)) {
                continue;
            }
            lootHandler(ctx, pos, modifiable);
        }
    }

    // Both halves of a double chest hand back the same combined inventory, and the item-handler
    // capability builds a fresh wrapper on every lookup, so the halves can only be paired by
    // position. This set spans the whole run because a double chest can straddle a chunk border,
    // which puts each half in a different DegradeContext.
    private boolean claimInventory(DegradeContext ctx, BlockPos pos) {
        if (!lootedPositions.add(pos.asLong())) {
            return false;
        }
        BlockState state = ctx.state(pos);
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            lootedPositions.add(pos.relative(ChestBlock.getConnectedDirection(state)).asLong());
        }
        return true;
    }

    private static void lootHandler(DegradeContext ctx, BlockPos pos, IItemHandlerModifiable inventory) {
        ctx.recordForUndo(pos);
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
            ctx.markChanged();
        }
    }

    public static int survivingCount(DegradeContext ctx, int count, float keepFraction) {
        int surviving = 0;
        for (int i = 0; i < count; i++) {
            if (ctx.roll(keepFraction)) {
                surviving++;
            }
        }
        return surviving;
    }

    public static void lootContainer(DegradeContext ctx, BlockPos pos,
                                     RandomizableContainerBlockEntity container) {
        if (!ctx.claimLoot(pos)) {
            return;
        }
        ctx.recordForUndo(pos);
        boolean changed = false;
        float keep = ctx.chances.containerKeepFraction();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int surviving = survivingCount(ctx, stack.getCount(), keep);
            if (surviving != stack.getCount()) {
                container.setItem(slot, surviving == 0 ? ItemStack.EMPTY : stack.copyWithCount(surviving));
                changed = true;
            }
        }
        if (changed) {
            container.setChanged();
            ctx.markChanged();
        }
    }
}
