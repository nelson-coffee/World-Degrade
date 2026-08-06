package dev.ncn.worlddegrade.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ContraptionLootEffect implements DegradeEffect {
    private final Set<UUID> visited = new HashSet<>();

    @Override
    public boolean shipSafe() {
        return false;
    }

    @Override
    public void apply(DegradeContext ctx) {
        long[] positions = ctx.positions();
        if (positions.length == 0) {
            return;
        }
        BlockPos sample = BlockPos.of(positions[0]);
        int minX = SectionPos.blockToSectionCoord(sample.getX()) << 4;
        int minZ = SectionPos.blockToSectionCoord(sample.getZ()) << 4;
        AABB chunkBounds = new AABB(minX, ctx.level.getMinBuildHeight(), minZ,
                minX + 16, ctx.level.getMaxBuildHeight(), minZ + 16);

        for (AbstractContraptionEntity contraption : ctx.level.getEntitiesOfClass(
                AbstractContraptionEntity.class, chunkBounds)) {
            if (!visited.add(contraption.getUUID())) {
                continue;
            }
            lootContraption(ctx, contraption);
        }
    }

    private void lootContraption(DegradeContext ctx, AbstractContraptionEntity contraption) {
        IItemHandlerModifiable items = contraption.getContraption().getStorage().getAllItems();
        ListTag slotRecords = new ListTag();
        boolean changed = false;
        float keep = ctx.chances.containerKeepFraction();
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int surviving = CreateLootEffect.survivingCount(ctx, stack.getCount(), keep);
            if (surviving == stack.getCount()) {
                continue;
            }
            CompoundTag slotRecord = new CompoundTag();
            slotRecord.putInt("slot", slot);
            slotRecord.put("item", stack.save(ctx.level.registryAccess()));
            slotRecords.add(slotRecord);
            items.setStackInSlot(slot, surviving == 0 ? ItemStack.EMPTY : stack.copyWithCount(surviving));
            changed = true;
        }
        if (!changed) {
            return;
        }
        ctx.markChanged();
        CompoundTag entry = new CompoundTag();
        entry.put("uuid", NbtUtils.createUUID(contraption.getUUID()));
        entry.put("slots", slotRecords);
        CompoundTag section = ctx.undoCompatSection("create");
        ListTag list = section.getList("contraptions", Tag.TAG_COMPOUND);
        list.add(entry);
        section.put("contraptions", list);
    }

    public static void restore(MinecraftServer server, CompoundTag compatSection) {
        ListTag entries = compatSection.getList("contraptions", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            UUID uuid = NbtUtils.loadUUID(entry.get("uuid"));
            AbstractContraptionEntity contraption = findContraption(server, uuid);
            if (contraption == null) {
                continue;
            }
            IItemHandlerModifiable items = contraption.getContraption().getStorage().getAllItems();
            ListTag slots = entry.getList("slots", Tag.TAG_COMPOUND);
            for (int s = 0; s < slots.size(); s++) {
                CompoundTag slotRecord = slots.getCompound(s);
                int slot = slotRecord.getInt("slot");
                if (slot < items.getSlots()) {
                    ItemStack stack = ItemStack.parseOptional(server.registryAccess(), slotRecord.getCompound("item"));
                    items.setStackInSlot(slot, stack);
                }
            }
        }
    }

    private static AbstractContraptionEntity findContraption(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof AbstractContraptionEntity contraption) {
                return contraption;
            }
        }
        return null;
    }
}
