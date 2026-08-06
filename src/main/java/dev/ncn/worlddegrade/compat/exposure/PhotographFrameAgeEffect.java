package dev.ncn.worlddegrade.compat.exposure;

import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import io.github.mortuusars.exposure.world.entity.PhotographFrameEntity;
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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PhotographFrameAgeEffect implements DegradeEffect {
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

        for (PhotographFrameEntity frame : ctx.level.getEntitiesOfClass(
                PhotographFrameEntity.class, chunkBounds)) {
            if (!visited.add(frame.getUUID())) {
                continue;
            }
            age(ctx, frame);
        }
    }

    private void age(DegradeContext ctx, PhotographFrameEntity frame) {
        ItemStack held = frame.getItem();
        if (held.isEmpty()) {
            return;
        }
        boolean isPhotograph = held.is(ExposurePhotographs.photograph());
        boolean isAged = held.is(ExposurePhotographs.agedPhotograph());
        if (!isPhotograph && !isAged) {
            return;
        }
        if (!ctx.roll(ctx.patchChance(frame.blockPosition(), ctx.chances.brickWeatherChance()))) {
            return;
        }
        record(ctx, frame, held);
        if (isPhotograph) {
            frame.setItem(held.transmuteCopy(ExposurePhotographs.agedPhotograph(), held.getCount()));
        } else {
            frame.setItem(ItemStack.EMPTY);
        }
        ctx.markChanged();
    }

    private void record(DegradeContext ctx, PhotographFrameEntity frame, ItemStack previous) {
        CompoundTag entry = new CompoundTag();
        entry.put("uuid", NbtUtils.createUUID(frame.getUUID()));
        entry.put("item", previous.save(ctx.level.registryAccess()));
        CompoundTag section = ctx.undoCompatSection("exposure");
        ListTag list = section.getList("frames", Tag.TAG_COMPOUND);
        list.add(entry);
        section.put("frames", list);
    }

    public static void restore(MinecraftServer server, CompoundTag compatSection) {
        ListTag entries = compatSection.getList("frames", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            UUID uuid = NbtUtils.loadUUID(entry.get("uuid"));
            PhotographFrameEntity frame = findFrame(server, uuid);
            if (frame == null) {
                continue;
            }
            frame.setItem(ItemStack.parseOptional(server.registryAccess(), entry.getCompound("item")));
        }
    }

    private static PhotographFrameEntity findFrame(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof PhotographFrameEntity frame) {
                return frame;
            }
        }
        return null;
    }
}
