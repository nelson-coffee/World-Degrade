package dev.ncn.worlddegrade.degrade.effects;

import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.EnderChestLootQueue;
import dev.ncn.worlddegrade.degrade.NestedItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import java.util.List;
import java.util.UUID;

public class EnderChestLootEffect implements DegradeEffect {
    public static final String UNDO_KEY = "enderchest";

    private final boolean enabled;

    public EnderChestLootEffect(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void apply(DegradeContext ctx) {
        MinecraftServer server = ctx.level.getServer();
        if (server == null) {
            return;
        }
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = ctx.state(pos);
            if (!state.is(Blocks.ENDER_CHEST)) {
                continue;
            }
            ctx.claim(pos);
            if (!enabled) {
                continue;
            }
            EnderChestLootQueue queue = EnderChestLootQueue.get(server);
            EnderChestLootQueue.Event event =
                    queue.beginLoot(packed, ctx.chances.containerKeepFraction());
            if (event == null) {
                continue;
            }
            recordEvent(ctx, packed, event.id());
            for (ServerPlayer player : EnderChestLootQueue.onlinePlayers(server)) {
                lootPlayer(ctx, queue, event, player);
            }
            ctx.markChanged();
        }
    }

    public static void lootPlayer(DegradeContext ctx, EnderChestLootQueue queue,
                                  EnderChestLootQueue.Event event, ServerPlayer player) {
        IItemHandlerModifiable inventory = new InvWrapper(player.getEnderChestInventory());
        ListTag removed = new ListTag();
        NestedItems.StackVisitor visitor = stack -> {
            int surviving = ContainerLootEffect.survivingCount(ctx, stack.getCount(), event.keepFraction());
            if (surviving == stack.getCount()) {
                return stack;
            }
            ItemStack lost = stack.copyWithCount(stack.getCount() - surviving);
            removed.add(lost.save(player.level().registryAccess()));
            return surviving == 0 ? ItemStack.EMPTY : stack.copyWithCount(surviving);
        };
        if (ctx.nested()) {
            NestedItems.walk(inventory, visitor, ctx.newStackBudget(player.blockPosition()));
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
        queue.markApplied(event, player.getUUID());
        if (!removed.isEmpty()) {
            recordRestore(ctx, player.getUUID(), removed);
        }
    }

    private static void recordEvent(DegradeContext ctx, long chestPos, long eventId) {
        CompoundTag section = ctx.undoCompatSection(UNDO_KEY);
        ListTag events = section.contains("events", Tag.TAG_LIST)
                ? section.getList("events", Tag.TAG_COMPOUND) : new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putLong("id", eventId);
        entry.putLong("chest", chestPos);
        events.add(entry);
        section.put("events", events);
    }

    private static void recordRestore(DegradeContext ctx, UUID player, ListTag removed) {
        CompoundTag section = ctx.undoCompatSection(UNDO_KEY);
        ListTag restores = section.contains("restore", Tag.TAG_LIST)
                ? section.getList("restore", Tag.TAG_COMPOUND) : new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.put("player", NbtUtils.createUUID(player));
        entry.put("items", removed);
        restores.add(entry);
        section.put("restore", restores);
    }

    public static void onUndo(MinecraftServer server, CompoundTag section) {
        if (section.isEmpty()) {
            return;
        }
        EnderChestLootQueue queue = EnderChestLootQueue.get(server);
        ListTag events = section.getList("events", Tag.TAG_COMPOUND);
        for (int i = 0; i < events.size(); i++) {
            CompoundTag entry = events.getCompound(i);
            queue.cancel(entry.getLong("id"));
            queue.forgetChest(entry.getLong("chest"));
        }
        ListTag restores = section.getList("restore", Tag.TAG_COMPOUND);
        for (int i = 0; i < restores.size(); i++) {
            CompoundTag entry = restores.getCompound(i);
            ServerPlayer player = server.getPlayerList().getPlayer(NbtUtils.loadUUID(entry.get("player")));
            if (player == null) {
                continue;
            }
            ListTag items = entry.getList("items", Tag.TAG_COMPOUND);
            for (int j = 0; j < items.size(); j++) {
                ItemStack.parse(server.registryAccess(), items.getCompound(j))
                        .ifPresent(stack -> giveBack(player, stack));
            }
        }
    }

    private static void giveBack(ServerPlayer player, ItemStack stack) {
        if (!player.getEnderChestInventory().canAddItem(stack)) {
            player.drop(stack, false);
            return;
        }
        player.getEnderChestInventory().addItem(stack);
    }

    public static void applyPending(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        EnderChestLootQueue queue = EnderChestLootQueue.get(server);
        List<EnderChestLootQueue.Event> owed = queue.pendingFor(player.getUUID());
        for (EnderChestLootQueue.Event event : owed) {
            lootOffline(player, event);
            queue.markApplied(event, player.getUUID());
        }
    }

    private static void lootOffline(ServerPlayer player, EnderChestLootQueue.Event event) {
        IItemHandlerModifiable inventory = new InvWrapper(player.getEnderChestInventory());
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int surviving = 0;
            for (int i = 0; i < stack.getCount(); i++) {
                if (player.level().getRandom().nextFloat() < event.keepFraction()) {
                    surviving++;
                }
            }
            if (surviving != stack.getCount()) {
                inventory.setStackInSlot(slot,
                        surviving == 0 ? ItemStack.EMPTY : stack.copyWithCount(surviving));
            }
        }
    }
}
