package dev.ncn.worlddegrade.degrade.effects;

import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.NestedItems;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

public class ContainerLootEffect implements DegradeEffect {
    public static final TagKey<Block> DISPLAY_CONTAINERS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("worlddegrade", "display_containers"));

    private final LongOpenHashSet lootedPositions = new LongOpenHashSet();
    private final boolean enabled;

    public ContainerLootEffect(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void apply(DegradeContext ctx) {
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            BlockEntity blockEntity = ctx.blockEntity(pos);
            if (blockEntity == null) {
                continue;
            }
            IItemHandler handler = inventoryAt(ctx, pos, blockEntity);
            if (handler == null) {
                continue;
            }
            if (handler instanceof IItemHandlerModifiable) {
                ctx.claim(pos);
            }
            if (!enabled) {
                continue;
            }
            if (!claimInventory(ctx, pos) || !ctx.claimLoot(pos)) {
                continue;
            }
            if (!(handler instanceof IItemHandlerModifiable modifiable)) {
                lootByExtraction(ctx, pos, handler);
            } else if (ctx.state(pos).is(DISPLAY_CONTAINERS)) {
                lootDisplay(ctx, pos, modifiable);
            } else {
                lootHandler(ctx, pos, modifiable);
            }
        }
    }

    private static IItemHandler inventoryAt(DegradeContext ctx, BlockPos pos, BlockEntity blockEntity) {
        IItemHandler handler = ctx.level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler != null) {
            return handler;
        }
        return blockEntity instanceof Container container ? new InvWrapper(container) : null;
    }

    private static void lootDisplay(DegradeContext ctx, BlockPos pos, IItemHandlerModifiable inventory) {
        ctx.recordForUndo(pos);
        NestedItems.StackVisitor base =
                keepFractionVisitor(ctx, ctx.chances.containerKeepFraction());
        NestedItems.StackVisitor visitor =
                stack -> NestedItems.isContainer(stack) ? ItemStack.EMPTY : base.visit(stack);
        if (lootTopLevelOnly(inventory, visitor)) {
            ctx.markChanged();
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
        float keep = ctx.chances.containerKeepFraction();
        // Contents of a surviving container item are rolled independently at the same fraction.
        NestedItems.StackVisitor visitor = keepFractionVisitor(ctx, keep);
        boolean changed = ctx.nested()
                ? NestedItems.walk(inventory, visitor, ctx.newStackBudget(pos))
                : lootTopLevelOnly(inventory, visitor);
        if (changed) {
            ctx.markChanged();
        }
    }

    public static NestedItems.StackVisitor keepFractionVisitor(DegradeContext ctx, float keep) {
        return stack -> {
            int surviving = survivingCount(ctx, stack.getCount(), keep);
            return surviving == stack.getCount() ? stack
                    : surviving == 0 ? ItemStack.EMPTY : stack.copyWithCount(surviving);
        };
    }

    private static void lootByExtraction(DegradeContext ctx, BlockPos pos, IItemHandler inventory) {
        ctx.recordForUndo(pos);
        float keep = ctx.chances.containerKeepFraction();
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int removed = stack.getCount() - survivingCount(ctx, stack.getCount(), keep);
            if (removed > 0 && !inventory.extractItem(slot, removed, false).isEmpty()) {
                changed = true;
            }
            if (ctx.nested() && lootNestedByRoundTrip(ctx, pos, inventory, slot, keep)) {
                changed = true;
            }
        }
        if (changed) {
            ctx.markChanged();
        }
    }

    private static boolean lootNestedByRoundTrip(DegradeContext ctx, BlockPos pos,
                                                 IItemHandler inventory, int slot, float keep) {
        ItemStack present = inventory.getStackInSlot(slot);
        if (present.isEmpty() || !NestedItems.isContainer(present)) {
            return false;
        }
        ItemStack extracted = inventory.extractItem(slot, present.getCount(), false);
        if (extracted.isEmpty()) {
            return false;
        }
        ItemStack original = extracted.copy();
        NestedItems.Budget budget = ctx.newStackBudget(pos);
        NestedItems.descendInto(extracted, keepFractionVisitor(ctx, keep), budget);
        ItemStack restored = budget.changeCount() > 0 ? extracted : original;
        if (!inventory.insertItem(slot, restored, true).isEmpty()) {
            restored = original;
            if (!inventory.insertItem(slot, restored, true).isEmpty()) {
                ctx.dropItem(pos, restored);
                return false;
            }
        }
        inventory.insertItem(slot, restored, false);
        return budget.changeCount() > 0;
    }

    private static boolean lootTopLevelOnly(IItemHandlerModifiable inventory,
                                            NestedItems.StackVisitor visitor) {
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack result = visitor.visit(stack);
            if (result != stack) {
                inventory.setStackInSlot(slot, result);
                changed = true;
            }
        }
        return changed;
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

}
