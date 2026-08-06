package dev.ncn.worlddegrade.compat.computercraft;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

final class ComputerStorage {
    private static final String NBT_COMPUTER_ID = "ComputerId";

    private static final ResourceLocation DISK_ID =
            ResourceLocation.fromNamespaceAndPath("computercraft", "disk_id");

    private ComputerStorage() {
    }

    static Path root(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("computercraft");
    }

    static Path computerDir(MinecraftServer server, int id) {
        return root(server).resolve("computer").resolve(Integer.toString(id));
    }

    static Path diskDir(MinecraftServer server, int id) {
        return root(server).resolve("disk").resolve(Integer.toString(id));
    }

    @Nullable
    static Integer computerId(MinecraftServer server, @Nullable BlockEntity blockEntity) {
        if (blockEntity == null) {
            return null;
        }
        ResourceLocation type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
        if (type == null || !type.getNamespace().equals("computercraft")) {
            return null;
        }
        var tag = blockEntity.saveWithoutMetadata(server.registryAccess());
        if (!tag.contains(NBT_COMPUTER_ID)) {
            return null;
        }
        int id = tag.getInt(NBT_COMPUTER_ID);
        return id >= 0 ? id : null;
    }

    @Nullable
    static Integer diskId(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(DISK_ID);
        if (type == null || type.codec() == null) {
            return null;
        }
        Object value = stack.get(type);
        if (value == null) {
            return null;
        }
        return encodeId(type, value);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static Integer encodeId(DataComponentType<?> type, Object value) {
        var codec = (com.mojang.serialization.Codec<Object>) type.codec();
        if (codec == null) {
            return null;
        }
        Tag encoded = codec.encodeStart(NbtOps.INSTANCE, value).result().orElse(null);
        return encoded instanceof IntTag intTag && intTag.getAsInt() >= 0 ? intTag.getAsInt() : null;
    }
}
