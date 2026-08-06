package dev.ncn.worlddegrade.compat.computercraft;

import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

public class ComputerCorruptionEffect implements DegradeEffect {
    static final String UNDO_KEY = "computercraft";
    static final String UNDO_FILES = "files";

    private static final float SEVERITY_SCALE = 1.0f;

    @Override
    public void apply(DegradeContext ctx) {
        if (!ctx.chances.corruptComputers()) {
            return;
        }
        float severity = ctx.chances.machineBreakChance() * SEVERITY_SCALE;
        if (severity <= 0.0f) {
            return;
        }
        var server = ctx.level.getServer();
        if (server == null) {
            return;
        }
        IntOpenHashSet computers = new IntOpenHashSet();
        IntOpenHashSet disks = new IntOpenHashSet();

        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            BlockEntity blockEntity = ctx.blockEntity(pos);
            if (blockEntity == null) {
                continue;
            }
            Integer computerId = ComputerStorage.computerId(server, blockEntity);
            if (computerId != null) {
                ctx.claim(pos);
                computers.add(computerId.intValue());
            }
            collectDisks(ctx, pos, disks);
        }
        if (computers.isEmpty() && disks.isEmpty()) {
            return;
        }

        ListTag backup = existingBackup(ctx);
        int damaged = 0;
        for (int id : computers) {
            damaged += FileGarbler.garbleDirectory(
                    ComputerStorage.computerDir(server, id), severity, ctx.random, backup);
        }
        for (int id : disks) {
            damaged += FileGarbler.garbleDirectory(
                    ComputerStorage.diskDir(server, id), severity, ctx.random, backup);
        }
        if (damaged > 0) {
            ctx.undoCompatSection(UNDO_KEY).put(UNDO_FILES, backup);
        }
    }

    private void collectDisks(DegradeContext ctx, BlockPos pos, IntOpenHashSet disks) {
        IItemHandler handler = ctx.level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler == null) {
            return;
        }
        boolean found = false;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            Integer diskId = ComputerStorage.diskId(stack);
            if (diskId != null) {
                disks.add(diskId.intValue());
                found = true;
            }
        }
        if (found) {
            ctx.claim(pos);
        }
    }

    private ListTag existingBackup(DegradeContext ctx) {
        CompoundTag section = ctx.undoCompatSection(UNDO_KEY);
        return section.contains(UNDO_FILES) ? section.getList(UNDO_FILES, 10) : new ListTag();
    }

    @Override
    public boolean shipSafe() {
        return true;
    }
}
